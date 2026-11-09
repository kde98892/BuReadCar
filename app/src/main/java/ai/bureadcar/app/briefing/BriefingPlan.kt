package ai.bureadcar.app.briefing

import ai.bureadcar.app.model.Region
import ai.bureadcar.app.model.Segment

object BriefingPlan {
    /** Estimated Korean TTS speed; calibrate against the car's engine. */
    const val CHARS_PER_SECOND = 6.5

    /** Spare time kept before arrival. */
    const val MARGIN_SECONDS = 30

    fun seconds(segment: Segment): Double = segment.text.length / CHARS_PER_SECOND

    /** Regions in route order without repeats. */
    fun distinctRegions(regions: List<Region>): List<Region> = regions.distinctBy { it.lawdCode }

    /** Destination first, then origin, then the rest along the route. Smaller is more important. */
    fun regionPriority(regions: List<Region>): Map<String, Int> {
        val order = buildList {
            regions.lastOrNull()?.let(::add)
            regions.firstOrNull()?.let(::add)
            addAll(regions.drop(1).dropLast(1))
        }.distinctBy { it.lawdCode }
        return order.withIndex().associate { (index, region) -> region.lawdCode to index }
    }

    /** Drops the least important segments until the rest fit, keeping order. */
    fun fit(segments: List<Segment>, budgetSeconds: Int?): List<Segment> {
        if (budgetSeconds == null) return segments
        val budget = (budgetSeconds - MARGIN_SECONDS).coerceAtLeast(0)
        val kept = segments.toMutableList()
        while (kept.sumOf(::seconds) > budget) {
            val drop = kept.withIndex()
                .filter { it.value.priority != null }
                .maxByOrNull { it.value.priority!! }
                ?: break
            kept.removeAt(drop.index)
        }
        return kept
    }
}
