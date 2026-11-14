package ai.bureadcar.app.route

import ai.bureadcar.app.model.Place
import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class Bookmarks(val home: Place?, val office: Place?)

data class NaviTrip(
    val origin: Place?,
    val waypoints: List<Place>,
    val destination: Place?,
    val remainSeconds: Int?,
)

/** The car's navigation app. When its service is absent, every value stays empty. */
class NaviSource(context: Context) {
    enum class Event { ROUTE_STARTED, ROUTE_ENDED }

    private val _location = MutableStateFlow<Place?>(null)
    val location: StateFlow<Place?> = _location
    private val _destination = MutableStateFlow<DestinationInfo?>(null)
    private val _waypoints = MutableStateFlow<List<WaypointInfo>>(emptyList())
    private val recentDestinations = MutableStateFlow<List<RecentDestinationInfo>?>(null)
    private val _bookmarks = MutableStateFlow(Bookmarks(null, null))
    val bookmarks: StateFlow<Bookmarks> = _bookmarks
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            _location.value = Place("현재 위치", info.latitude, info.longitude)
        }

        override fun onDestinationInfo(info: DestinationInfo) {
            _destination.value = info
        }

        override fun onWayPointInfo(info: List<WaypointInfo>) {
            _waypoints.value = info
        }

        override fun onRecentDestinationInfo(info: List<RecentDestinationInfo>) {
            recentDestinations.value = info.toList()
        }

        override fun onBookmarkInfo(info: List<BookmarkInfo>) {
            fun pick(type: BookmarkType) = info.firstOrNull { it.category == type }?.let {
                Place(it.name, it.latitude, it.longitude)
            }
            _bookmarks.value = Bookmarks(pick(BookmarkType.HOME), pick(BookmarkType.OFFICE))
        }

        override fun onRouteStarted(info: RouteStartInfo) {
            _events.tryEmit(Event.ROUTE_STARTED)
        }

        override fun onRouteEnded() {
            _events.tryEmit(Event.ROUTE_ENDED)
        }

        override fun onRouteCancelled() {
            _events.tryEmit(Event.ROUTE_ENDED)
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            _events.tryEmit(Event.ROUTE_ENDED)
        }
    }

    private val helper: NaviHelper? = runCatching { NaviHelper(context.applicationContext) }.getOrNull()

    init {
        runCatching {
            helper?.initialize({
                helper.addListener(listener)
                helper.getBookmarkInfo()
            }, { })
        }
    }

    fun requestLocation() {
        runCatching { helper?.getCurrentLocationInfo() }
    }

    /** Asks the navigation for the trip and gives its callbacks a moment to answer. */
    suspend fun snapshot(): NaviTrip {
        _destination.value = null
        _waypoints.value = emptyList()
        runCatching {
            helper?.getCurrentLocationInfo()
            helper?.getDestinationInfo()
            helper?.getWaypointInfo()
        }
        delay(SNAPSHOT_WAIT_MS)
        val capturedWaypoints = _waypoints.value
        val resolvedWaypoints = recoverWaypoints(capturedWaypoints)
        val destination = _destination.value?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
        return NaviTrip(
            origin = _location.value,
            waypoints = resolvedWaypoints,
            destination = destination?.let { Place(it.name, it.latitude, it.longitude) },
            remainSeconds = destination?.remainTime?.takeIf { it > 0 },
        )
    }

    /** Restore missing coordinates only from a unique, exact SDK place identity. */
    private suspend fun recoverWaypoints(points: List<WaypointInfo>): List<Place> {
        val missing = points.filterNot { validCoordinates(it.latitude, it.longitude) }
        if (missing.isEmpty()) return points.map { Place(it.name, it.latitude, it.longitude) }
        val candidates = missing.filter { point ->
            point.poiId.isNotBlank() && points.count { it.poiId == point.poiId } == 1
        }
        if (candidates.isEmpty()) {
            return points.filter { validCoordinates(it.latitude, it.longitude) }
                .map { Place(it.name, it.latitude, it.longitude) }
        }
        recentDestinations.value = null
        val connectedHelper = helper
        val requested = connectedHelper != null && runCatching {
            connectedHelper.getRecentDestinationInfo()
        }.isSuccess
        val recent = if (requested) withTimeoutOrNull(RECENT_WAIT_MS) {
            recentDestinations.filterNotNull().first()
        } else null
        return points.mapNotNull { point ->
            if (validCoordinates(point.latitude, point.longitude)) {
                return@mapNotNull Place(point.name, point.latitude, point.longitude)
            }
            if (point !in candidates) return@mapNotNull null
            // Do not match by name, list order, or a guessed address.
            val match = recent.orEmpty().filter {
                it.poiId == point.poiId && it.poiProvider == point.poiProvider && it.poiSubId == point.poiSubId
            }.singleOrNull()?.takeIf { validCoordinates(it.latitude, it.longitude) }
            match?.let { Place(point.name, it.latitude, it.longitude) }
        }

    }

    private fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 && (latitude != 0.0 || longitude != 0.0)

    fun release() {
        runCatching {
            helper?.removeListener(listener)
            helper?.release()
        }
    }

    private companion object {
        const val SNAPSHOT_WAIT_MS = 1_200L
        const val RECENT_WAIT_MS = 8_000L
    }
}

fun distanceMeters(a: Place, b: Place): Double {
    val earth = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * earth * asin(sqrt(h))
}

/**
 * Points along straight lines between [stops], about 3 km apart and at most 12, ending at the last stop.
 * The demo drives through them so a real route's districts are passed while the car stands still.
 */
fun simulatedDrive(stops: List<Place>): List<Place> {
    val legs = stops.zipWithNext()
    val total = legs.sumOf { (a, b) -> distanceMeters(a, b) }
    if (total == 0.0) return stops.drop(1)
    val spacing = maxOf(DEMO_SPACING_M, total / DEMO_MAX_POINTS)
    return legs.flatMap { (a, b) ->
        val steps = maxOf(1, Math.round(distanceMeters(a, b) / spacing).toInt())
        (1..steps).map { i ->
            val t = i.toDouble() / steps
            if (i == steps) b else Place(a.name, a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
        }
    }
}

private const val DEMO_SPACING_M = 3_000.0
private const val DEMO_MAX_POINTS = 12

/**
 * Scripted commute for the emulator, whose location never moves: Hanam to Gangnam through Gangdong and
 * Songpa. Prices rise in Hanam while Songpa and Gangnam fall, and Hanam has its own transport news.
 */
val DEMO_ROUTE = listOf(
    Place("하남시 미사역", 37.5631, 127.1928),
    Place("강동구 고덕역", 37.5550, 127.1540),
    Place("송파구 잠실역", 37.5133, 127.1001),
    Place("강남구 삼성역", 37.5089, 127.0631),
)
