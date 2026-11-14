package ai.bureadcar.app.briefing

import ai.bureadcar.app.model.Segment

/** Where playback is. Interrupted speech restarts from the current sentence. */
class PlaybackQueue {
    private val segments = mutableListOf<Segment>()
    private var segmentIndex = 0
    private var sentenceIndex = 0

    val size: Int get() = segments.size
    val position: Int get() = segmentIndex
    val currentSegment: Segment? get() = segments.getOrNull(segmentIndex)
    val nextSegment: Segment? get() = segments.getOrNull(segmentIndex + 1)
    val currentSentence: String? get() = currentSegment?.let { sentences(it).getOrNull(sentenceIndex) }

    fun load(items: List<Segment>) {
        segments.clear()
        segments += items
        restart()
    }

    fun restart() {
        segmentIndex = 0
        sentenceIndex = 0
    }

    /** Moves past the sentence just spoken; false when nothing is left. */
    fun advanceSentence(): Boolean {
        val segment = currentSegment ?: return false
        sentenceIndex++
        if (sentenceIndex < sentences(segment).size) return true
        return skipSegment()
    }

    fun skipSegment(): Boolean {
        sentenceIndex = 0
        if (segmentIndex < segments.size) segmentIndex++
        return segmentIndex < segments.size
    }

    /**
     * Queues the news of a district entered on the way. It goes after the rest of the district being read
     * (or about to be read, during the opening) and after districts already added this way, so each
     * district's 1부 and 2부 stay together and districts keep route order.
     */
    fun insertPassing(items: List<Segment>) {
        if (items.isEmpty()) return
        val here = segments.drop(segmentIndex).firstOrNull { it.region != null }?.region
        var at = (segmentIndex + 1).coerceAtMost(segments.size)
        if (here != null) {
            while (at < segments.size && (segments[at].region == here || segments[at].passing)) at++
        }
        segments.addAll(at, items)
    }

    private fun sentences(segment: Segment): List<String> =
        segment.text.split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        val SENTENCE_END = Regex("(?<=[.?!])\\s+")
    }
}
