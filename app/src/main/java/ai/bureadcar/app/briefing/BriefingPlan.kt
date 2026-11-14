package ai.bureadcar.app.briefing

import ai.bureadcar.app.model.Region
import ai.bureadcar.app.model.Segment

object BriefingPlan {
    /** Estimated speed of the car's Korean TTS engine itself; calibrate against the car. */
    const val ENGINE_CHARS_PER_SECOND = 6.5

    /**
     * Broadcast pace against the engine. The Pleos TTS SDK has no speech-rate setting, so the player
     * pauses after each sentence for the time that makes the whole read at this share of engine speed.
     */
    const val PACE = 0.9

    /** Overall reading speed including the pauses, for fitting the news to the trip. */
    const val CHARS_PER_SECOND = ENGINE_CHARS_PER_SECOND * PACE

    /** Pause after [sentence]: its speaking time × (1 / PACE − 1), kept between a beat and a breath. */
    fun pauseAfter(sentence: String): Long =
        (sentence.length / ENGINE_CHARS_PER_SECOND * (1 / PACE - 1) * 1000).toLong().coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)

    private const val MIN_PAUSE_MS = 300L
    private const val MAX_PAUSE_MS = 1200L

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

    /**
     * Fits the news to the time left, then closes with one key point per category still in.
     * Time for the closing is set aside first so it is never cut.
     */
    fun withClosing(segments: List<Segment>, budgetSeconds: Int?): List<Segment> {
        val reserve = seconds(closing(segments)).toInt() + 1
        val kept = fit(segments, budgetSeconds?.let { it - reserve })
        return kept + closing(kept)
    }

    /** "끝으로 오늘의 핵심을 정리해 드립니다. 첫째, …" built from the segments' highlights. */
    fun closing(segments: List<Segment>): Segment {
        val points = segments.filter { it.highlight != null }
            .groupBy { it.title }
            .map { (label, news) -> label to news.minBy { it.priority ?: Int.MAX_VALUE }.highlight!! }
            .take(ORDINALS.size)
        val text = buildString {
            if (points.isNotEmpty()) {
                append("끝으로 오늘의 핵심을 정리해 드립니다. ")
                points.forEachIndexed { i, (label, point) -> append("${ORDINALS[i]}, $label 소식입니다. $point ") }
            }
            append("오늘 방송은 여기까지입니다. 안전 운전하시기 바랍니다.")
        }
        return Segment(CLOSING_TITLE, text)
    }

    const val CLOSING_TITLE = "오늘의 핵심"
    private val ORDINALS = listOf("첫째", "둘째", "셋째", "넷째", "다섯째")

    /** Drops the least important segments until the rest fit, keeping order. */
    fun fit(segments: List<Segment>, budgetSeconds: Int?): List<Segment> {
        if (budgetSeconds == null) return segments
        val budget = (budgetSeconds - MARGIN_SECONDS).coerceAtLeast(0)
        val kept = segments.toMutableList()
        while (kept.sumOf(::seconds) > budget) {
            // Among equals, the later segment goes first so a district keeps its opening line.
            val drop = kept.withIndex()
                .filter { it.value.priority != null }
                .maxWithOrNull(compareBy({ it.value.priority!! }, { it.index }))
                ?: break
            kept.removeAt(drop.index)
        }
        return kept
    }
}
