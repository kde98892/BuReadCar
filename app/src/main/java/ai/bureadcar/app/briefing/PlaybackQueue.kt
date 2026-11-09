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

    /** Queues [items] right after what is playing now. */
    fun insertNext(items: List<Segment>) {
        val at = (segmentIndex + 1).coerceAtMost(segments.size)
        segments.addAll(at, items)
    }

    private fun sentences(segment: Segment): List<String> =
        segment.text.split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        val SENTENCE_END = Regex("(?<=[.?!])\\s+")
    }
}
