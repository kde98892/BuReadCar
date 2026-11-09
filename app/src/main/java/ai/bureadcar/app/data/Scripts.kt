package ai.bureadcar.app.data

import ai.bureadcar.app.briefing.Speech
import ai.bureadcar.app.model.Region
import java.time.LocalDate

/** Builds spoken text from API rows. Facts only: no opinions or buy/sell advice. */
object Scripts {

    private fun active(rows: List<Map<String, String>>) =
        rows.filter { it["cdealType"].isNullOrBlank() }

    private fun Map<String, String>.amount() = Speech.parseAmount(get("dealAmount").orEmpty())

    private fun Map<String, String>.areaValue() = get("excluUseAr")?.toDoubleOrNull()

    fun trade(region: Region, month: Int, rows: List<Map<String, String>>): String? {
        val deals = active(rows).filter { it.amount() != null }
        val top = deals.maxByOrNull { it.amount()!! } ?: return null
        return buildString {
            append("${region.sigungu}의 ${month}월 아파트 매매는 지금까지 ${deals.size}건 신고됐습니다. ")
            append("이 중 가장 높은 거래는 ${top["umdNm"].orEmpty()} ${Speech.name(top["aptNm"].orEmpty())}")
            top.areaValue()?.let { append(" ${Speech.area(it)}") }
            append("로, ${Speech.won(top.amount()!!)}입니다.")
        }
    }

    fun rent(region: Region, month: Int, rows: List<Map<String, String>>): String? {
        if (rows.isEmpty()) return null
        fun Map<String, String>.deposit() = Speech.parseAmount(get("deposit").orEmpty())
        fun Map<String, String>.monthly() = Speech.parseAmount(get("monthlyRent").orEmpty()) ?: 0L
        val jeonse = rows.filter { it.monthly() == 0L && it.deposit() != null }
        val wolse = rows.size - jeonse.size
        return buildString {
            append("${region.sigungu}의 ${month}월 아파트 전월세 계약은 지금까지 ${rows.size}건입니다. ")
            append("전세 ${jeonse.size}건, 월세 ${wolse}건입니다.")
            jeonse.maxByOrNull { it.deposit()!! }?.let { top ->
                append(" 가장 높은 전세는 ${top["umdNm"].orEmpty()} ${Speech.name(top["aptNm"].orEmpty())}")
                top["excluUseAr"]?.toDoubleOrNull()?.let { append(" ${Speech.area(it)}") }
                append("로, 보증금 ${Speech.won(top.deposit()!!)}입니다.")
            }
        }
    }

    data class Notice(
        val manageNo: String,
        val name: String,
        val address: String,
        val areaName: String,
        val households: Int?,
        val receiptStart: LocalDate?,
        val receiptEnd: LocalDate?,
    )

    /** True when a notice's address lies in [region]. */
    fun inRegion(address: String, region: Region): Boolean {
        val tokens = region.sigungu.split(" ")
        val last = tokens.last()
        if (address.contains(last)) return true
        // "성남시 ..." with no district named still counts for any of that city's districts.
        return tokens.size > 1 && address.contains(tokens.first()) &&
            !Regex("${tokens.first()}\\s*\\S+구").containsMatchIn(address)
    }

    fun openNotices(notices: List<Notice>, today: LocalDate): List<Notice> =
        notices.filter { it.receiptEnd != null && !it.receiptEnd.isBefore(today) }
            .sortedBy { it.receiptStart }

    fun notice(notice: Notice): String = buildString {
        append("${Speech.name(notice.name)} 공고입니다. ")
        val start = notice.receiptStart
        val end = notice.receiptEnd
        if (start != null && end != null) {
            append("청약 접수는 ${Speech.date(start)}부터 ${Speech.date(end)}까지입니다.")
        }
        notice.households?.let { append(" 공급 규모는 ${it}세대입니다.") }
    }

    /** Highest first-rank local rate, or null before results are published. */
    fun highestRate(rows: List<Map<String, Any?>>): Double? =
        rows.filter { it["RESIDE_SENM"] == "해당지역" && it["SUBSCRPT_RANK_CODE"]?.toString() == "1" }
            .mapNotNull { it["CMPET_RATE"]?.toString()?.replace(",", "")?.toDoubleOrNull() }
            .maxOrNull()

    fun competition(notice: Notice, rate: Double): String =
        "최근 마감한 ${Speech.name(notice.name)}의 1순위 해당지역 최고 경쟁률은 ${Speech.number(rate)}대 1입니다."

    data class IndexPoint(val weekStart: LocalDate, val value: Double)

    fun priceIndex(name: String, points: List<IndexPoint>): String? {
        val sorted = points.sortedBy { it.weekStart }
        if (sorted.size < 2) return null
        val last = sorted.last()
        val change = (last.value / sorted[sorted.size - 2].value - 1) * 100
        return buildString {
            append("$name 아파트 매매가격지수는 ${Speech.date(last.weekStart)} 주간 기준 ${Speech.number(last.value)}로, ")
            append("전주보다 ${Speech.change(change)}.")
            val streak = streak(sorted)
            if (streak >= 2) append(" ${streak}주 연속 ${if (change > 0) "상승" else "하락"}입니다.")
        }
    }

    private fun streak(sorted: List<IndexPoint>): Int {
        val changes = sorted.zipWithNext { a, b -> b.value - a.value }
        val direction = changes.lastOrNull()?.let { if (it > 0) 1 else if (it < 0) -1 else 0 } ?: 0
        if (direction == 0) return 0
        return changes.reversed().takeWhile { (it > 0 && direction > 0) || (it < 0 && direction < 0) }.size
    }

    /** R-ONE class ID for a region: the district if listed, otherwise its city. */
    fun rOneClass(region: Region, classes: List<Pair<Int, String>>): Pair<Int, String>? {
        val sido = rOneSido(region.sidoShort)
        val inSido = classes.map { (id, full) -> Triple(id, full, full.split(">")) }
            .filter { it.third.first() == sido }
        if (region.sigungu == region.sido) return inSido.firstOrNull { it.third.size == 1 }?.toResult()
        val tokens = region.sigungu.split(" ")
        return (inSido.firstOrNull { c -> tokens.all { it in c.third } && c.third.last() == tokens.last() }
            ?: inSido.firstOrNull { it.third.last() == tokens.first() })?.toResult()
    }

    private fun Triple<Int, String, List<String>>.toResult() = first to third.last()

    private fun rOneSido(short: String) = if (short == "광주" || short == "전남") "전남광주" else short

    fun rate(label: String, month: String, values: List<Double>): String? {
        val current = values.lastOrNull() ?: return null
        val base = "$label ${month} 기준 연 ${Speech.number(current)}퍼센트"
        val previous = values.getOrNull(values.size - 2) ?: return "$base 입니다."
        val delta = Math.round((current - previous) * 100) / 100.0
        return when {
            delta > 0 -> "$base 로, 전월보다 ${Speech.number(delta)}퍼센트포인트 올랐습니다."
            delta < 0 -> "$base 로, 전월보다 ${Speech.number(-delta)}퍼센트포인트 내렸습니다."
            else -> "$base 로, 전월과 같습니다."
        }.replace(" 로,", "로,").replace(" 입니다.", "입니다.")
    }
}
