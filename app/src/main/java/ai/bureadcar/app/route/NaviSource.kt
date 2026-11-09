package ai.bureadcar.app.route

import ai.bureadcar.app.model.Place
import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
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
        val destination = _destination.value?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
        return NaviTrip(
            origin = _location.value,
            waypoints = _waypoints.value.map { Place(it.name, it.latitude, it.longitude) },
            destination = destination?.let { Place(it.name, it.latitude, it.longitude) },
            remainSeconds = destination?.remainTime?.takeIf { it > 0 },
        )
    }

    fun release() {
        runCatching {
            helper?.removeListener(listener)
            helper?.release()
        }
    }

    private companion object {
        const val SNAPSHOT_WAIT_MS = 1_200L
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

/** Scripted commute for the emulator, whose location never moves. */
val DEMO_ROUTE = listOf(
    Place("분당구 정자동", 37.3825, 127.1086),
    Place("수정구 시흥동", 37.4127, 127.0947),
    Place("강남구 세곡동", 37.4640, 127.1040),
    Place("서초구 서초동", 37.4979, 127.0276),
    Place("강남구 역삼동", 37.5006, 127.0366),
)
