package ai.bureadcar.app.briefing

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/** Turns raw values into text a TTS engine reads naturally. */
object Speech {

    /** 153000 (in 10,000 won) → "15억 3천만 원". */
    fun won(manWon: Long): String {
        val eok = manWon / 10_000
        val rest = manWon % 10_000
        val parts = buildList {
            if (eok > 0) add("${eok}억")
            if (rest > 0) add(manPart(rest))
        }
        return if (parts.isEmpty()) "0원" else parts.joinToString(" ") + " 원"
    }

    private fun manPart(value: Long): String {
        val thousands = value / 1000
        val remainder = value % 1000
        return when {
            remainder == 0L && thousands > 0 -> "${thousands}천만"
            thousands > 0 -> "${thousands}천${remainder}만"
            else -> "${remainder}만"
        }
    }

    /** "230,000" → 230000. */
    fun parseAmount(text: String): Long? = text.replace(",", "").trim().toLongOrNull()

    fun area(squareMeters: Double): String = "전용 ${squareMeters.toInt()}제곱미터"

    fun date(date: LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일"

    /** Removes brackets a TTS engine reads badly: "상록마을(라이프1차)" → "상록마을 라이프1차". */
    fun name(text: String): String =
        text.replace(Regex("[()\\[\\]]"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * Final consonant of how [word] is read: 0 for none, 8 for ㄹ. A trailing digit counts as read aloud
     * ("2" is 이, "3" is 삼); otherwise the last Hangul syllable decides.
     */
    private fun finalOf(word: String): Int {
        val end = word.trimEnd()
        end.lastOrNull()?.takeIf { it.isDigit() }?.let { return DIGIT_FINALS[it - '0'] }
        val last = end.lastOrNull { it in '가'..'힣' } ?: return 0
        return (last - '가') % 28
    }

    /** 영 일 이 삼 사 오 육 칠 팔 구. */
    private val DIGIT_FINALS = intArrayOf(21, 8, 0, 16, 0, 0, 1, 8, 8, 0)

    /** True when [word] is read ending in a consonant, which picks 은/는 and 과/와. */
    private fun hasFinal(word: String): Boolean = finalOf(word) != 0

    /** "12층" → "12층으로", "단지2" → "단지2로"; ㄹ endings take 로 as well. */
    fun ro(word: String): String = word + if (finalOf(word) in setOf(0, 8)) "로" else "으로"

    /**
     * A written-style sentence in the broadcast's 합니다 style: "인하한다." → "인하합니다.",
     * "개최하였다." → "개최하였습니다.", "지역이다." → "지역입니다.", "받는다." → "받습니다.".
     * Sentences not ending in "다" are returned as they are.
     */
    fun polite(sentence: String): String {
        val body = sentence.trimEnd().removeSuffix(".")
        if (body.length < 2 || !body.endsWith("다")) return sentence
        val stem = body.dropLast(1)
        val last = stem.last()
        if (last !in '가'..'힣') return sentence
        val head = stem.dropLast(1)
        // Already formal: "입니다", "습니다", "합니다" ("아니다" still needs changing).
        val before = head.lastOrNull()
        if (last == '니' && before != null && before in '가'..'힣' && (before - '가') % 28 == BIEUP) return sentence
        // 받는다 → 받습니다: 는 follows a stem ending in a consonant.
        if (last == '는') return "${head}습니다."
        val final = (last - '가') % 28
        return when (final) {
            // 하다 → 합니다, 인한다 → 인합니다, 길다 → 깁니다: the ending's ㄴ or ㄹ gives way to ㅂ.
            0, NIEUN, RIEUL -> "$head${last - final + BIEUP}니다."
            else -> "${stem}습니다."
        }
    }

    private const val NIEUN = 4
    private const val RIEUL = 8
    private const val BIEUP = 17

    /** "4월" → "4월과" for "과/와". */
    fun with(word: String): String = word + if (hasFinal(word)) "과" else "와"

    /** "분양·청약" → "분양·청약은", "전월세 시세" → "전월세 시세는". */
    fun topic(word: String): String = word + if (hasFinal(word)) "은" else "는"

    /** "A과 B", "A와 B", or "A, B, C". */
    fun list(words: List<String>): String = when (words.size) {
        0 -> ""
        1 -> words[0]
        2 -> words[0] + (if (hasFinal(words[0])) "과 " else "와 ") + words[1]
        else -> words.joinToString(", ")
    }

    fun number(value: Double, decimals: Int = 2): String {
        val text = String.format(Locale.US, "%.${decimals}f", value)
        return if ('.' in text) text.trimEnd('0').trimEnd('.') else text
    }

    /** "0.07퍼센트 상승했습니다" style clause; rounds to two decimals first. */
    fun change(percent: Double): String {
        val rounded = Math.round(percent * 100) / 100.0
        return when {
            rounded > 0 -> "${number(abs(rounded))}퍼센트 상승했습니다"
            rounded < 0 -> "${number(abs(rounded))}퍼센트 하락했습니다"
            else -> "변동이 없었습니다"
        }
    }
}
