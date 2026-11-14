package ai.bureadcar.app.ui

import ai.bureadcar.app.briefing.BriefingPlan
import ai.bureadcar.app.data.BriefingBuilder
import ai.bureadcar.app.data.VWorldGeocoder
import ai.bureadcar.app.data.apiResult
import ai.bureadcar.app.data.allProviders
import ai.bureadcar.app.model.Category
import ai.bureadcar.app.model.ListenMode
import ai.bureadcar.app.model.Place
import ai.bureadcar.app.model.Region
import ai.bureadcar.app.model.RouteBasis
import ai.bureadcar.app.model.SavedRoute
import ai.bureadcar.app.player.BriefingPlayer
import ai.bureadcar.app.player.PleosSpeaker
import ai.bureadcar.app.route.AudioPowerSource
import ai.bureadcar.app.route.DEMO_ROUTE
import ai.bureadcar.app.route.NaviSource
import ai.bureadcar.app.route.UxRestrictionSource
import ai.bureadcar.app.route.distanceMeters
import ai.bureadcar.app.route.simulatedDrive
import ai.bureadcar.app.schedule.SettingsStore
import ai.bureadcar.app.schedule.WeeklySchedule
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RadioState(
    val schedule: WeeklySchedule,
    val mode: ListenMode,
    val autoStart: Boolean,
    val routes: List<SavedRoute>,
    val selectedRouteId: String,
    val consent: Boolean,
    val today: DayOfWeek,
    val status: String? = null,
    val regions: List<String> = emptyList(),
    val preparing: Boolean = false,
    val tripActive: Boolean = false,
)

private data class Trip(val points: List<Place>, val remainSeconds: Int?, val basis: RouteBasis)

/** The demo's own commute, for when there is no route to follow. */
private val SCRIPTED_DEMO = Trip(listOf(DEMO_ROUTE.first(), DEMO_ROUTE.last()), null, RouteBasis.Demo)

private fun dayName(day: DayOfWeek) = when (day) {
    DayOfWeek.MONDAY -> "월요일"
    DayOfWeek.TUESDAY -> "화요일"
    DayOfWeek.WEDNESDAY -> "수요일"
    DayOfWeek.THURSDAY -> "목요일"
    DayOfWeek.FRIDAY -> "금요일"
    DayOfWeek.SATURDAY -> "토요일"
    DayOfWeek.SUNDAY -> "일요일"
}

class RadioViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val navi = NaviSource(app)
    private val ux = UxRestrictionSource(app)
    private val geocoder = VWorldGeocoder()
    private val builder = BriefingBuilder(allProviders())
    private val speaker = PleosSpeaker(app)
    val player = BriefingPlayer(app, speaker)
    private val audioPower = AudioPowerSource(app, player::setAudioPowerEnabled)
    val ttsReady: StateFlow<Boolean?> = speaker.ready
    val limits = ux.limits

    private val _ui = MutableStateFlow(
        RadioState(
            schedule = store.schedule,
            mode = store.mode,
            autoStart = store.autoStart,
            // A route saved from unset bookmarks (0,0) can lead nowhere, so it is not offered.
            routes = store.routes.filter { it.origin.isKnown && it.destination.isKnown }
                .ifEmpty { listOf(SettingsStore.EXAMPLE_ROUTE) },
            selectedRouteId = store.selectedRouteId,
            consent = store.locationConsent,
            today = LocalDate.now().dayOfWeek,
        ),
    )
    val radio: StateFlow<RadioState> = _ui

    private var briefingJob: Job? = null
    private var trackingJob: Job? = null

    init {
        viewModelScope.launch {
            navi.events.collect { event ->
                when (event) {
                    NaviSource.Event.ROUTE_STARTED ->
                        if (_ui.value.autoStart && _ui.value.consent && !_ui.value.tripActive) start()
                    NaviSource.Event.ROUTE_ENDED -> if (_ui.value.tripActive) endTrip()
                }
            }
        }
        viewModelScope.launch {
            navi.bookmarks.collect { marks ->
                val home = marks.home?.takeIf { it.isKnown } ?: return@collect
                val office = marks.office?.takeIf { it.isKnown } ?: return@collect
                val route = SavedRoute(SettingsStore.HOME_OFFICE_ID, "집 → 회사", home, office)
                val updated = _ui.value.routes.filterNot { it.id == route.id } + route
                store.routes = updated
                _ui.update { it.copy(routes = updated) }
            }
        }
    }

    fun grantConsent() {
        store.locationConsent = true
        _ui.update { it.copy(consent = true) }
    }

    /** Plays today's briefing for the selected mode, or [only] that category. */
    fun start(only: Category? = null) = launchBriefing(only, demoDay = null)

    /** Plays the demo drive with [day]'s programs, so any weekday can be shown on any day. */
    fun startDemo(day: DayOfWeek) = launchBriefing(null, demoDay = day)

    fun stop() {
        briefingJob?.cancel()
        trackingJob?.cancel()
        player.pause()
        player.load(emptyList())
        _ui.update { it.copy(tripActive = false, preparing = false, regions = emptyList(), status = null) }
    }

    private fun launchBriefing(only: Category?, demoDay: DayOfWeek?) {
        val demo = demoDay != null
        briefingJob?.cancel()
        trackingJob?.cancel()
        player.pause()
        briefingJob = viewModelScope.launch {
            _ui.update { it.copy(preparing = true, status = "경로 지역과 소식을 준비하고 있어요", regions = emptyList()) }
            if (!_ui.value.consent) return@launch fail("위치 정보 이용에 동의한 후 시작해 주세요")
            val today = LocalDate.now()
            val day = demoDay ?: today.dayOfWeek
            val categories = if (only != null) listOf(only) else _ui.value.schedule.categories(day)
            var trip = if (demo) demoTrip() else resolveTrip()
            if (categories.isEmpty()) return@launch fail("오늘은 편성된 소식이 없어요")
            if (trip == null) return@launch fail("현재 위치를 받지 못했어요")

            var regions = BriefingPlan.distinctRegions(trip.points.mapNotNull { regionOrNull(it) })
            if (regions.isEmpty() && demo && trip.basis != RouteBasis.Demo) {
                // The route's places fall in no known district (e.g. an emulator's default location),
                // so the demo still runs, on the scripted commute.
                Log.i(TAG, "Demo route had no districts; using the scripted commute")
                trip = SCRIPTED_DEMO
                regions = BriefingPlan.distinctRegions(trip.points.mapNotNull { regionOrNull(it) })
            }
            if (regions.isEmpty()) return@launch fail("지역을 확인하지 못했어요. 네트워크를 확인해 주세요")

            val segments = builder.build(
                categories,
                regions,
                BriefingPlan.regionPriority(regions),
                today,
                dayName(day),
                trip.basis,
            )
            player.load(BriefingPlan.withClosing(segments, trip.remainSeconds))
            player.play()
            _ui.update {
                it.copy(preparing = false, status = null, tripActive = true, regions = regions.map(Region::sigungu))
            }
            val tracksNavi = store.mode == ListenMode.ON_ROUTE && !demo
            val demoPath = if (trip.basis == RouteBasis.Demo) DEMO_ROUTE.drop(1) else simulatedDrive(trip.points)
            if (demo || tracksNavi) track(demo, categories, regions.map { it.lawdCode }.toSet(), today, demoPath)
        }
    }

    /**
     * The demo follows the navigation while it guides, whatever the listen mode; otherwise the saved route
     * in that mode. Only with neither does it take the scripted commute.
     */
    private suspend fun demoTrip(): Trip {
        naviTrip()?.takeIf { it.basis == RouteBasis.Navigation && it.points.size >= 2 }?.let { return it }
        if (store.mode == ListenMode.SAVED_ROUTE) {
            savedTrip()?.takeIf { it.points.size >= 2 }?.let { trip ->
                return trip.copy(basis = (trip.basis as RouteBasis.Saved).copy(followsDrive = true))
            }
        }
        return SCRIPTED_DEMO
    }

    private suspend fun resolveTrip(): Trip? =
        if (store.mode == ListenMode.SAVED_ROUTE) savedTrip() else naviTrip()

    /** The selected saved route; null when it has no usable places. */
    private fun savedTrip(): Trip? {
        val route = _ui.value.routes.firstOrNull { it.id == _ui.value.selectedRouteId } ?: _ui.value.routes.firstOrNull()
            ?: return null
        val points = listOf(route.origin, route.destination).filter { it.isKnown }
        return Trip(points, null, RouteBasis.Saved(route.name)).takeIf { points.isNotEmpty() }
    }

    /** Where the car is and, while guiding, the navigation's waypoints and destination. */
    private suspend fun naviTrip(): Trip? {
        val trip = navi.snapshot()
        val points = (listOfNotNull(trip.origin) + trip.waypoints + listOfNotNull(trip.destination)).filter { it.isKnown }
        val basis = if (trip.destination?.isKnown == true) RouteBasis.Navigation else RouteBasis.CurrentLocation
        return points.takeIf { it.isNotEmpty() }?.let { Trip(it, trip.remainSeconds, basis) }
    }

    private suspend fun regionOrNull(place: Place): Region? =
        apiResult { geocoder.region(place) }.getOrNull()

    /** [demoPath] is where the demo drive passes, one point per step. */
    private fun track(demo: Boolean, categories: List<Category>, known: Set<String>, today: LocalDate, demoPath: List<Place>) {
        trackingJob = viewModelScope.launch {
            val seen = known.toMutableSet()
            var lastCheck: Place? = null

            suspend fun visit(place: Place) {
                if (lastCheck?.let { distanceMeters(it, place) < CHECK_DISTANCE_M } == true) return
                lastCheck = place
                val region = regionOrNull(place) ?: return
                if (!seen.add(region.lawdCode)) return
                _ui.update { it.copy(regions = it.regions + region.sigungu) }
                player.insertNext(builder.forPassing(categories, region, today))
            }

            if (demo) {
                demoPath.forEach {
                    delay(DEMO_STEP_MS)
                    visit(it)
                }
                _ui.update { it.copy(tripActive = false) }
            } else {
                while (isActive) {
                    navi.requestLocation()
                    delay(POLL_MS)
                    navi.location.value?.let { visit(it) }
                }
            }
        }
    }

    private fun endTrip() {
        trackingJob?.cancel()
        player.finishAfterSentence()
        _ui.update { it.copy(tripActive = false) }
    }

    private fun fail(message: String) {
        _ui.update { it.copy(preparing = false, status = message) }
    }

    fun place(day: DayOfWeek, slot: Int, category: Category): Boolean {
        val updated = _ui.value.schedule.place(day, slot, category) ?: return false
        saveSchedule(updated)
        return true
    }

    fun clear(day: DayOfWeek, slot: Int) = saveSchedule(_ui.value.schedule.clear(day, slot))

    fun move(fromDay: DayOfWeek, fromSlot: Int, toDay: DayOfWeek, toSlot: Int): Boolean {
        val updated = _ui.value.schedule.move(fromDay, fromSlot, toDay, toSlot) ?: return false
        saveSchedule(updated)
        return true
    }

    private fun saveSchedule(schedule: WeeklySchedule) {
        store.schedule = schedule
        _ui.update { it.copy(schedule = schedule) }
    }

    fun setMode(mode: ListenMode) {
        store.mode = mode
        _ui.update { it.copy(mode = mode) }
    }

    fun setAutoStart(enabled: Boolean) {
        store.autoStart = enabled
        _ui.update { it.copy(autoStart = enabled) }
    }

    fun selectRoute(id: String) {
        store.selectedRouteId = id
        _ui.update { it.copy(selectedRouteId = id) }
    }

    override fun onCleared() {
        audioPower.release()
        player.release()
        navi.release()
        ux.release()
    }

    private companion object {
        const val TAG = "BuReadCar"
        const val CHECK_DISTANCE_M = 2_000.0
        const val POLL_MS = 15_000L
        const val DEMO_STEP_MS = 18_000L
    }
}
