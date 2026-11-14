package ai.bureadcar.app.player

import ai.bureadcar.app.briefing.BriefingPlan
import ai.bureadcar.app.briefing.PlaybackQueue
import ai.bureadcar.app.model.Segment
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "BuReadCar"

interface Speaker {
    val ready: StateFlow<Boolean?>
    fun speak(text: String, onDone: () -> Unit, onError: () -> Unit)
    fun stop()
    fun release()
}

/** The car's on-device engine; needs no network or app registration. */
class PleosSpeaker(context: Context) : Speaker {
    private val main = Handler(Looper.getMainLooper())
    private val _ready = MutableStateFlow<Boolean?>(null)
    override val ready: StateFlow<Boolean?> = _ready
    private var onDone: (() -> Unit)? = null
    private var onError: (() -> Unit)? = null

    private val listener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = finish()
        override fun onStop() = Unit

        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "TTS error: $errorType")
            val failed = onError
            onDone = null
            onError = null
            main.post { failed?.invoke() }
        }
    }

    private val engine = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    init {
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                engine.addEventListener(listener)
                engine.setLocale(Locale.KOREA)
                // Pleos currently plays this driver-facing app in the primary audio zone.
                // Without an explicit zone, the remote TTS service can miss the car output bus.
                engine.setAudioZoneId(0)
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                _ready.value = true
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "TTS init failed: $errorType")
                _ready.value = false
            }
        })
    }

    private fun finish() {
        val completed = onDone
        onDone = null
        onError = null
        main.post { completed?.invoke() }
    }

    override fun speak(text: String, onDone: () -> Unit, onError: () -> Unit) {
        this.onDone = onDone
        this.onError = onError
        engine.speak(text)
    }

    override fun stop() {
        onDone = null
        onError = null
        engine.stop()
    }

    override fun release() {
        engine.removeEventListener(listener)
        engine.release()
    }
}

data class PlayerState(
    val playing: Boolean = false,
    val loaded: Boolean = false,
    val title: String = "",
    val text: String = "",
    /** The sentence being spoken now; shown on screen instead of the whole segment. */
    val sentence: String = "",
    val isSample: Boolean = false,
    val position: Int = 0,
    val total: Int = 0,
    val next: String? = null,
    val finished: Boolean = false,
    val region: String? = null,
    val message: String? = null,
)

/** Android focus is kept behind an interface so loss/denial can be tested without a car. */
interface AudioFocus {
    var onLost: () -> Unit
    var onTransientLoss: () -> Unit
    var onGain: () -> Unit
    val otherAudioActive: Boolean
    fun request(): Boolean
    fun abandon()
}

class AndroidAudioFocus(context: Context) : AudioFocus {
    private val audio = context.getSystemService(AudioManager::class.java)
    override var onLost: () -> Unit = {}
    override var onTransientLoss: () -> Unit = {}
    override var onGain: () -> Unit = {}
    override val otherAudioActive: Boolean get() = audio.isMusicActive
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // A real-estate briefing is media, never navigation or a safety announcement.
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> onGain()
                AudioManager.AUDIOFOCUS_LOSS -> onLost()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onTransientLoss()
            }
        }, Handler(Looper.getMainLooper()))
        .build()

    override fun request(): Boolean =
        audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun abandon() { audio.abandonAudioFocusRequest(request) }
}

/** Temporary interruptions resume on gain; power restoration requests fresh focus. */
/** [later] runs a step after a delay; the pause between sentences goes through it. */
class BriefingPlayer(
    private val engine: Speaker,
    private val focus: AudioFocus,
    private val later: (Long, () -> Unit) -> Unit = { ms, step -> Handler(Looper.getMainLooper()).postDelayed(step, ms) },
) {
    constructor(context: Context, engine: Speaker) : this(engine, AndroidAudioFocus(context))

    private val queue = PlaybackQueue()
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state
    private var playing = false
    private var finishing = false
    private var hasFocus = false
    private var generation = 0L
    private var message: String? = null
    private var resumeOnGain = false
    private var resumeAfterPower = false
    private var audioPowerEnabled = true
    private var released = false

    init {
        focus.onLost = {
            val powerResume = !audioPowerEnabled && resumeAfterPower
            pause()
            resumeAfterPower = powerResume
            message = "다른 소리가 우선 재생되어 멈췄어요. 끝나면 재생을 눌러 주세요"
            publish()
        }
        focus.onTransientLoss = {
            if (playing || resumeOnGain) {
                resumeOnGain = true
                hasFocus = false
                stopSpeech()
                message = "다른 소리 안내가 끝나면 이어 읽을게요"
                publish()
            }
        }
        focus.onGain = {
            if (resumeOnGain && audioPowerEnabled && !released) {
                resumeOnGain = false
                hasFocus = true
                // The system returned the existing focus; do not request it again.
                if (engine.ready.value == true) {
                    message = null
                    playing = true
                    speakCurrent()
                } else {
                    pause()
                    message = "음성 엔진이 준비되지 않았어요. 재생을 눌러 다시 시도해 주세요"
                    publish()
                }
            }
        }
    }

    /** Audio-off policy or screen-off is a power interruption, not a navigation prompt. */
    fun setAudioPowerEnabled(enabled: Boolean) {
        if (released || audioPowerEnabled == enabled) return
        audioPowerEnabled = enabled
        if (!enabled) {
            val shouldResume = playing || resumeOnGain
            pause()
            resumeAfterPower = shouldResume
            message = "절전 중에는 재생을 멈춰요"
            publish()
        } else {
            val shouldResume = resumeAfterPower
            resumeAfterPower = false
            message = null
            publish()
            // play() reevaluates engine readiness, other audio, and a new focus request.
            if (shouldResume) play()
        }
    }

    fun load(segments: List<Segment>) {
        finishing = false
        pause()
        message = null
        queue.load(segments)
        publish()
    }

    fun play() {
        if (released || playing || queue.currentSegment == null) return
        if (resumeOnGain) return // Wait for the system; never steal focus back from a prompt.
        message = when {
            !audioPowerEnabled -> "절전 중에는 재생할 수 없어요"
            engine.ready.value != true -> "음성 엔진이 준비되지 않았어요. 잠시 후 다시 시도해 주세요"
            !hasFocus && runCatching { focus.otherAudioActive }.getOrDefault(true) -> "다른 오디오가 재생 중이에요. 일시정지한 뒤 재생해 주세요"
            else -> null
        }
        if (message != null) { publish(); return }
        val granted = hasFocus || runCatching { focus.request() }.getOrDefault(false)
        if (!granted) {
            message = "지금은 오디오를 사용할 수 없어요. 잠시 후 재생해 주세요"
            publish()
            return
        }
        hasFocus = true
        playing = true
        speakCurrent()
    }

    fun pause() {
        resumeOnGain = false
        resumeAfterPower = false
        stopSpeech()
        hasFocus = false
        runCatching { focus.abandon() }
    }

    fun toggle() {
        if (playing) pause() else play()
    }

    fun next() {
        val wasPlaying = playing
        stopSpeech()
        if (queue.skipSegment()) {
            publish()
            if (wasPlaying) play()
        } else {
            end()
        }
    }

    fun restart() {
        pause()
        queue.restart()
        publish()
        play()
    }

    /** Adds a district entered on the way; see [PlaybackQueue.insertPassing]. */
    fun insertPassing(segments: List<Segment>) {
        queue.insertPassing(segments)
        publish()
    }

    /** Stops at the end of the current sentence, e.g. on arrival. */
    fun finishAfterSentence() {
        if (playing) finishing = true else end()
    }

    fun release() {
        released = true
        resumeOnGain = false
        resumeAfterPower = false
        stopSpeech()
        hasFocus = false
        focus.onLost = {}
        focus.onTransientLoss = {}
        focus.onGain = {}
        runCatching { focus.abandon() }
        engine.release()
    }

    private fun speakCurrent() {
        if (!playing || !hasFocus) return
        val sentence = queue.currentSentence ?: return end()
        val token = ++generation
        publish()
        val completed: () -> Unit = {
            if (token == generation && playing && hasFocus) {
                if (finishing) end()
                else if (queue.advanceSentence()) {
                    // A short pause slows the broadcast to BriefingPlan.PACE; pausing or skipping meanwhile cancels it.
                    later(BriefingPlan.pauseAfter(sentence)) {
                        if (token == generation && playing) {
                            if (finishing) end() else speakCurrent()
                        }
                    }
                }
                else end()
            }
        }
        val failed: () -> Unit = {
            if (token == generation) {
                pause()
                message = "음성 재생에 실패했어요. 재생을 눌러 다시 시도해 주세요"
                publish()
            }
        }
        try { engine.speak(sentence, completed, failed) }
        catch (_: Exception) { failed() }
    }

    private fun stopSpeech() {
        playing = false
        generation++
        engine.stop()
        publish()
    }

    private fun end() {
        finishing = false
        playing = false
        resumeOnGain = false
        resumeAfterPower = false
        generation++
        hasFocus = false
        engine.stop()
        runCatching { focus.abandon() }
        _state.value = _state.value.copy(playing = false, finished = true, next = null)
    }

    private fun publish() {
        val segment = queue.currentSegment
        _state.value = PlayerState(
            playing = playing,
            loaded = queue.size > 0,
            title = segment?.title.orEmpty(),
            text = segment?.text.orEmpty(),
            sentence = queue.currentSentence.orEmpty(),
            isSample = segment?.isSample == true,
            position = queue.position,
            total = queue.size,
            next = queue.nextSegment?.title,
            region = segment?.region,
            message = message,
        )
    }
}
