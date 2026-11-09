package ai.bureadcar.app.player

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
    fun speak(text: String, onDone: () -> Unit)
    fun stop()
    fun release()
}

/** The car's on-device engine; needs no network or app registration. */
class PleosSpeaker(context: Context) : Speaker {
    private val main = Handler(Looper.getMainLooper())
    private val _ready = MutableStateFlow<Boolean?>(null)
    override val ready: StateFlow<Boolean?> = _ready
    private var onDone: (() -> Unit)? = null

    private val listener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = finish()
        override fun onStop() = Unit

        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "TTS error: $errorType")
            finish()
        }
    }

    private val engine = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    init {
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                engine.addEventListener(listener)
                engine.setLocale(Locale.KOREA)
                _ready.value = true
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "TTS init failed: $errorType")
                _ready.value = false
            }
        })
    }

    private fun finish() {
        main.post { onDone?.also { onDone = null }?.invoke() }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        this.onDone = onDone
        engine.speak(text)
    }

    override fun stop() {
        onDone = null
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
    val isSample: Boolean = false,
    val position: Int = 0,
    val total: Int = 0,
    val next: String? = null,
    val finished: Boolean = false,
    val region: String? = null,
)

/**
 * Plays a briefing sentence by sentence. Pauses for navigation prompts rather than ducking,
 * because a missed number makes a news item useless; resumes from the interrupted sentence.
 */
class BriefingPlayer(context: Context, private val engine: Speaker) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val queue = PlaybackQueue()
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var playing = false
    private var finishing = false
    private var resumeOnGain = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            Log.d(TAG, "audio focus: $change")
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnGain) {
                    resumeOnGain = false
                    play()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (playing) {
                    resumeOnGain = true
                    stopSpeech()
                }
                AudioManager.AUDIOFOCUS_LOSS -> pause()
            }
        }, main)
        .build()

    fun load(segments: List<Segment>) {
        finishing = false
        stopSpeech()
        queue.load(segments)
        publish()
    }

    fun play() {
        if (queue.currentSegment == null) return
        if (audio.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        playing = true
        speakCurrent()
    }

    fun pause() {
        resumeOnGain = false
        stopSpeech()
        audio.abandonAudioFocusRequest(focusRequest)
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
        stopSpeech()
        queue.restart()
        publish()
        play()
    }

    fun insertNext(segments: List<Segment>) {
        queue.insertNext(segments)
        publish()
    }

    /** Stops at the end of the current sentence, e.g. on arrival. */
    fun finishAfterSentence() {
        if (playing) finishing = true else end()
    }

    fun release() {
        stopSpeech()
        audio.abandonAudioFocusRequest(focusRequest)
        engine.release()
    }

    private fun speakCurrent() {
        val sentence = queue.currentSentence ?: return end()
        publish()
        engine.speak(sentence) {
            if (finishing) {
                end()
            } else if (queue.advanceSentence()) {
                speakCurrent()
            } else {
                end()
            }
        }
    }

    private fun stopSpeech() {
        playing = false
        engine.stop()
        publish()
    }

    private fun end() {
        finishing = false
        playing = false
        resumeOnGain = false
        engine.stop()
        audio.abandonAudioFocusRequest(focusRequest)
        _state.value = _state.value.copy(playing = false, finished = true, next = null)
    }

    private fun publish() {
        val segment = queue.currentSegment
        _state.value = PlayerState(
            playing = playing,
            loaded = queue.size > 0,
            title = segment?.title.orEmpty(),
            text = segment?.text.orEmpty(),
            isSample = segment?.isSample == true,
            position = queue.position,
            total = queue.size,
            next = queue.nextSegment?.title,
            region = segment?.region,
        )
    }
}
