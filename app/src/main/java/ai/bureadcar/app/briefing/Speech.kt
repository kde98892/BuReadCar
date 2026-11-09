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
