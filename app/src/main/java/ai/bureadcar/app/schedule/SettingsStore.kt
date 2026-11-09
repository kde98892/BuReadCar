package ai.bureadcar.app.schedule

import ai.bureadcar.app.model.ListenMode
import ai.bureadcar.app.model.Place
import ai.bureadcar.app.model.SavedRoute
import ai.bureadcar.app.route.DEMO_ROUTE
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-local settings. Only the origin and destination of saved routes are kept. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("bureadcar", Context.MODE_PRIVATE)

    var schedule: WeeklySchedule
        get() = prefs.getString(KEY_SCHEDULE, null)?.let(WeeklySchedule::fromJson) ?: WeeklySchedule.DEFAULT
        set(value) = prefs.edit().putString(KEY_SCHEDULE, value.toJson()).apply()

    var mode: ListenMode
        get() = ListenMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: ListenMode.ON_ROUTE
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    var locationConsent: Boolean
        get() = prefs.getBoolean(KEY_CONSENT, false)
        set(value) = prefs.edit().putBoolean(KEY_CONSENT, value).apply()

    var selectedRouteId: String
        get() = prefs.getString(KEY_SELECTED, EXAMPLE_ROUTE.id) ?: EXAMPLE_ROUTE.id
        set(value) = prefs.edit().putString(KEY_SELECTED, value).apply()

    var routes: List<SavedRoute>
        get() = prefs.getString(KEY_ROUTES, null)?.let(::parseRoutes) ?: listOf(EXAMPLE_ROUTE)
        set(value) = prefs.edit().putString(KEY_ROUTES, toJson(value)).apply()

    private fun toJson(routes: List<SavedRoute>) = JSONArray(
        routes.map { route ->
            JSONObject()
                .put("id", route.id)
                .put("name", route.name)
                .put("origin", placeJson(route.origin))
                .put("destination", placeJson(route.destination))
        },
    ).toString()

    private fun placeJson(place: Place) =
        JSONObject().put("name", place.name).put("lat", place.lat).put("lng", place.lng)

    private fun parseRoutes(text: String): List<SavedRoute> = runCatching {
        val array = JSONArray(text)
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            SavedRoute(
                item.getString("id"),
                item.getString("name"),
                parsePlace(item.getJSONObject("origin")),
                parsePlace(item.getJSONObject("destination")),
            )
        }
    }.getOrDefault(listOf(EXAMPLE_ROUTE))

    private fun parsePlace(json: JSONObject) =
        Place(json.getString("name"), json.getDouble("lat"), json.getDouble("lng"))

    companion object {
        private const val KEY_SCHEDULE = "schedule"
        private const val KEY_MODE = "mode"
        private const val KEY_AUTO = "auto_start"
        private const val KEY_CONSENT = "location_consent"
        private const val KEY_SELECTED = "selected_route"
        private const val KEY_ROUTES = "routes"
        const val HOME_OFFICE_ID = "home-office"

        val EXAMPLE_ROUTE = SavedRoute("example", "예시: 정자동 → 역삼동", DEMO_ROUTE.first(), DEMO_ROUTE.last())
    }
}
