package ai.bureadcar.app.schedule

import ai.bureadcar.app.model.Category
import ai.bureadcar.app.model.Category.*
import java.time.DayOfWeek
import org.json.JSONArray
import org.json.JSONObject

val WEEKDAYS = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)

const val SLOTS_PER_DAY = 2

/** Two slots for each weekday. An empty slot is null. */
data class WeeklySchedule(val days: Map<DayOfWeek, List<Category?>>) {

    fun slots(day: DayOfWeek): List<Category?> = days[day] ?: List(SLOTS_PER_DAY) { null }

    /** Categories to play on [day], in slot order. Weekends have none. */
    fun categories(day: DayOfWeek): List<Category> = slots(day).filterNotNull()

    /** Puts [category] in a slot, or returns null when the other slot of that day already has it. */
    fun place(day: DayOfWeek, slot: Int, category: Category): WeeklySchedule? {
        val current = slots(day)
        if (current.withIndex().any { (i, c) -> i != slot && c == category }) return null
        return copy(days = days + (day to current.toMutableList().also { it[slot] = category }))
    }

    fun clear(day: DayOfWeek, slot: Int): WeeklySchedule =
        copy(days = days + (day to slots(day).toMutableList().also { it[slot] = null }))

    /** Moves a box, swapping with whatever is in the target; null if that repeats a day's category. */
    fun move(fromDay: DayOfWeek, fromSlot: Int, toDay: DayOfWeek, toSlot: Int): WeeklySchedule? {
        val moving = slots(fromDay)[fromSlot] ?: return this
        val displaced = slots(toDay)[toSlot]
        val cleared = clear(fromDay, fromSlot).clear(toDay, toSlot)
        val placed = cleared.place(toDay, toSlot, moving) ?: return null
        return if (displaced == null) placed else placed.place(fromDay, fromSlot, displaced)
    }

    fun toJson(): String = JSONObject().apply {
        WEEKDAYS.forEach { day ->
            put(day.name, JSONArray(slots(day).map { it?.name ?: "" }))
        }
    }.toString()

    companion object {
        /**
         * 1부 holds the five categories with the most news (district data or weekly releases);
         * 2부 pairs each with a sparser one so no day runs short. Friday gets the price index,
         * which R-ONE publishes on Thursdays.
         */
        val DEFAULT = WeeklySchedule(
            mapOf(
                DayOfWeek.MONDAY to listOf(SUBSCRIPTION, POLICY),
                DayOfWeek.TUESDAY to listOf(TRADE, UNSOLD),
                DayOfWeek.WEDNESDAY to listOf(RENT, REDEVELOPMENT),
                DayOfWeek.THURSDAY to listOf(TRANSPORT, RATE),
                DayOfWeek.FRIDAY to listOf(PRICE_INDEX, DEVELOPMENT),
            ),
        )

        fun fromJson(text: String): WeeklySchedule = runCatching {
            val json = JSONObject(text)
            WeeklySchedule(
                WEEKDAYS.associateWith { day ->
                    val slots = json.getJSONArray(day.name)
                    List(SLOTS_PER_DAY) { i ->
                        slots.optString(i).takeIf { it.isNotEmpty() }
                            ?.let { name -> Category.entries.firstOrNull { it.name == name } }
                    }
                },
            )
        }.getOrDefault(DEFAULT)
    }
}
