package ai.bureadcar.app.data

import ai.bureadcar.app.briefing.Speech
import ai.bureadcar.app.model.Region
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Builds spoken text from API rows. Facts only: no opinions or buy/sell advice. */
object Scripts {

    private fun active(rows: List<Map<String, String>>) =
        rows.filter { it["cdealType"].isNullOrBlank() }

    private fun Map<String, String>.amount() = Speech.parseAmount(get("dealAmount").orEmpty())

    private fun Map<String, String>.areaValue() = get("excluUseAr")?.toDoubleOrNull()

    /** Average price per square meter, or null with too few deals to compare. */
    private fun perSquareMeter(rows: List<Map<String, String>>, value: (Map<String, String>) -> Long?): Double? =
        rows.mapNotNull { row ->
            val amount = value(row) ?: return@mapNotNull null
            val area = row.areaValue()?.takeIf { it > 0 } ?: return@mapNotNull null
            amount / area
        }.takeIf { it.size >= MIN_DEALS }?.average()

    private fun previousMonth(month: Int) = if (month == 1) 12 else month - 1

    private fun percent(current: Double, previous: Double) = (current / previous - 1) * 100

    private fun tradeChange(rows: List<Map<String, String>>, previous: List<Map<String, String>>): Double? {
        val now = perSquareMeter(active(rows)) { it.amount() } ?: return null
        val before = perSquareMeter(active(previous)) { it.amount() } ?: return null
        return percent(now, before)
    }

    private fun Map<String, String>.floor() = get("floor")?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    private fun Map<String, String>.place() = "${get("umdNm").orEmpty()} ${Speech.name(get("aptNm").orEmpty())}".trim()

    /** Rows near 84 m², the size most buyers compare. */
    private fun nearFamilySize(rows: List<Map<String, String>>) =
        rows.filter { row -> row.areaValue()?.let { it >= 80 && it < 90 } == true }

    /** Average in 10,000 won, rounded to a million won so it reads short. */
    private fun roundedAverage(values: List<Long>) = Math.round(values.average() / 100) * 100

    /** The complex with the most deals, when it stands out with at least three. */
    private fun busiest(rows: List<Map<String, String>>): Pair<String, Int>? =
        rows.groupingBy { it.place() }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value >= MIN_BUSIEST }?.toPair()

    /**
     * Deal count, direct and cancelled deals, the top deal, the busiest complex, the average near 84 m²,
     * and, with enough deals in both months, the price per m² against [previous].
     */
    fun trade(
        region: Region,
        month: Int,
        rows: List<Map<String, String>>,
        previous: List<Map<String, String>> = emptyList(),
    ): String? {
        val deals = active(rows).filter { it.amount() != null }
        val top = deals.maxByOrNull { it.amount()!! } ?: return null
        val cancelled = rows.size - active(rows).size
        val direct = deals.count { it["dealingGbn"]?.trim() == "직거래" }
        return buildString {
            append("${region.sigungu}의 ${month}월 아파트 매매는 지금까지 ${deals.size}건 신고됐습니다.")
            if (direct > 0) append(" 이 가운데 중개를 거치지 않은 직거래는 ${direct}건입니다.")
            if (cancelled > 0) append(" 계약을 해제했다는 신고도 ${cancelled}건 있었습니다.")
            append(" 가장 높은 거래는 ${top.place()}")
            top.areaValue()?.let { append(" ${Speech.area(it)}") }
            val where = top.floor()?.let { "${it}층" }
            append(if (where != null) " ${Speech.ro(where)}" else "로")
            append(", ${Speech.won(top.amount()!!)}입니다.")
            busiest(deals)?.let { (name, count) -> append(" 거래가 가장 많은 단지는 ${Speech.ro(name)}, ${count}건입니다.") }
            nearFamilySize(deals).takeIf { it.size >= MIN_SIZE_DEALS }?.let { family ->
                append(" 전용 84제곱미터 안팎 거래 ${family.size}건의 평균 가격은 ${Speech.won(roundedAverage(family.map { it.amount()!! }))}입니다.")
            }
            tradeChange(rows, previous)?.let {
                append(" 제곱미터당 평균 거래가는 ${previousMonth(month)}월보다 ${Speech.change(it)}.")
            }
        }
    }

    fun tradeHighlight(
        region: Region,
        month: Int,
        rows: List<Map<String, String>>,
        previous: List<Map<String, String>> = emptyList(),
    ): String? {
        tradeChange(rows, previous)?.let {
            return "${region.sigungu} 아파트의 ${month}월 제곱미터당 평균 거래가는 전월보다 ${Speech.change(it)}."
        }
        val top = active(rows).filter { it.amount() != null }.maxByOrNull { it.amount()!! } ?: return null
        return "${region.sigungu}의 ${month}월 최고 거래는 ${Speech.name(top["aptNm"].orEmpty())} ${Speech.won(top.amount()!!)}입니다."
    }

    private fun Map<String, String>.deposit() = Speech.parseAmount(get("deposit").orEmpty())

    private fun Map<String, String>.monthly() = Speech.parseAmount(get("monthlyRent").orEmpty()) ?: 0L

    private fun jeonse(rows: List<Map<String, String>>) = rows.filter { it.monthly() == 0L && it.deposit() != null }

    private fun jeonseChange(rows: List<Map<String, String>>, previous: List<Map<String, String>>): Double? {
        val now = perSquareMeter(jeonse(rows)) { it.deposit() } ?: return null
        val before = perSquareMeter(jeonse(previous)) { it.deposit() } ?: return null
        return percent(now, before)
    }

    /**
     * Contract count, jeonse and wolse split, renewals, the average jeonse near 84 m², the top jeonse,
     * the average wolse, and the jeonse deposit per m² against [previous].
     */
    fun rent(
        region: Region,
        month: Int,
        rows: List<Map<String, String>>,
        previous: List<Map<String, String>> = emptyList(),
    ): String? {
        if (rows.isEmpty()) return null
        val jeonse = jeonse(rows)
        val wolse = rows.filter { it.monthly() > 0 && it.deposit() != null }
        return buildString {
            append("${region.sigungu}의 ${month}월 아파트 전월세 계약은 지금까지 ${rows.size}건입니다. ")
            append("전세 ${jeonse.size}건, 월세 ${rows.size - jeonse.size}건입니다.")
            val renewed = rows.filter { it["contractType"]?.trim() == "갱신" }
            if (renewed.isNotEmpty()) {
                val right = renewed.count { it["useRRRight"]?.trim() == "사용" }
                append(" 기존 계약을 갱신한 경우는 ${renewed.size}건이고")
                append(if (right > 0) ", 이 중 ${right}건은 계약갱신요구권을 썼습니다." else "입니다.")
            }
            nearFamilySize(jeonse).takeIf { it.size >= MIN_SIZE_DEALS }?.let { family ->
                append(" 전용 84제곱미터 안팎 전세 ${family.size}건의 평균 보증금은 ${Speech.won(roundedAverage(family.map { it.deposit()!! }))}입니다.")
            }
            jeonse.maxByOrNull { it.deposit()!! }?.let { top ->
                append(" 가장 높은 전세는 ${top.place()}")
                top.areaValue()?.let { append(" ${Speech.area(it)}") }
                append("로, 보증금 ${Speech.won(top.deposit()!!)}입니다.")
            }
            if (wolse.size >= MIN_SIZE_DEALS) {
                val deposit = roundedAverage(wolse.map { it.deposit()!! })
                val monthly = Math.round(wolse.map { it.monthly() }.average())
                append(" 월세는 평균 보증금 ${Speech.won(deposit)}에 월 ${monthly}만 원입니다.")
            }
            jeonseChange(rows, previous)?.let {
                append(" 전세 보증금은 제곱미터당 평균으로 ${previousMonth(month)}월보다 ${Speech.change(it)}.")
            }
        }
    }

    fun rentHighlight(
        region: Region,
        month: Int,
        rows: List<Map<String, String>>,
        previous: List<Map<String, String>> = emptyList(),
    ): String? {
        if (rows.isEmpty()) return null
        jeonseChange(rows, previous)?.let {
            return "${region.sigungu} 아파트의 ${month}월 전세 보증금은 제곱미터당 평균으로 전월보다 ${Speech.change(it)}."
        }
        val jeonse = jeonse(rows).size
        return "${region.sigungu}의 ${month}월 아파트 전월세 계약은 전세 ${jeonse}건, 월세 ${rows.size - jeonse}건입니다."
    }

    /** One subscription notice. Schedule fields beyond the receipt window are read when present. */
    data class Notice(
        val manageNo: String,
        val name: String,
        val address: String,
        val areaName: String,
        val households: Int?,
        val receiptStart: LocalDate?,
        val receiptEnd: LocalDate?,
        /** "민영" or "국민". */
        val kind: String? = null,
        val builder: String? = null,
        val specialDay: LocalDate? = null,
        val firstRankDay: LocalDate? = null,
        val secondRankDay: LocalDate? = null,
        val winnersDay: LocalDate? = null,
        val moveIn: YearMonth? = null,
        val priceCap: Boolean = false,
        val models: List<HouseModel> = emptyList(),
    )

    /** One house type of a notice: exclusive area, households and top sale price in 10,000 won. */
    data class HouseModel(val area: Double, val households: Int, val topPrice: Long?)

    /** True when a notice's address lies in [region]. */
    fun inRegion(address: String, region: Region): Boolean {
        val tokens = region.sigungu.split(" ")
        val last = tokens.last()
        if (address.contains(last)) return true
        // "성남시 ..." with no district named still counts for any of that city's districts.
        return tokens.size > 1 && address.contains(tokens.first()) &&
            !Regex("${tokens.first()}\\s*\\S+구").containsMatchIn(address)
    }

    /** Sunday that ends [today]'s week; weeks run Monday to Sunday. */
    fun weekEnd(today: LocalDate): LocalDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    /** Notices taking applications this week, and those opening next week. Later ones are left out. */
    fun noticeWeeks(notices: List<Notice>, today: LocalDate): Pair<List<Notice>, List<Notice>> {
        val end = weekEnd(today)
        val thisWeek = notices.filter {
            it.receiptEnd != null && !it.receiptEnd.isBefore(today) && (it.receiptStart == null || !it.receiptStart.isAfter(end))
        }.sortedBy { it.receiptStart }
        val nextWeek = notices.filter {
            it.receiptStart != null && it.receiptStart.isAfter(end) && !it.receiptStart.isAfter(end.plusWeeks(1))
        }.sortedBy { it.receiptStart }
        return thisWeek to nextWeek
    }

    /** "성남시 분당구 정자동" from "경기도 성남시 분당구 정자동 1 일원": the address down to its neighborhood. */
    fun neighborhood(address: String): String? {
        val tokens = address.split(" ").map { it.trim(',', ' ') }.filter { it.isNotEmpty() }
        val end = tokens.indexOfFirst { NEIGHBORHOOD.matches(it) }
        if (end < 1) return null
        return tokens.subList(1, end + 1).joinToString(" ")
    }

    /** "남광토건(주), 중흥토건(주)" → "남광토건 등". */
    fun builderName(text: String?): String? {
        val names = text.orEmpty().split(",")
            .map { it.replace(Regex("\\(주\\)|㈜|주식회사"), "").trim() }
            .filter { it.isNotEmpty() }
        val first = names.firstOrNull() ?: return null
        return if (names.size > 1) "$first 등" else first
    }

    /** Top prices of the most supplied sizes, smallest first. */
    private fun prices(models: List<HouseModel>): String? {
        val bySize = models.filter { (it.topPrice ?: 0) > 0 }.groupBy { it.area.toInt() }
        if (bySize.isEmpty()) return null
        val sizes = bySize.entries.sortedByDescending { e -> e.value.sumOf { it.households } }
            .take(MAX_SIZES).sortedBy { it.key }
        val parts = sizes.map { (size, group) -> "${size}제곱미터 ${Speech.won(group.maxOf { it.topPrice!! })}" }
        return "분양가는 주택형별 최고가 기준으로 전용 ${parts.joinToString(", ")}입니다."
    }

    /** Where and what, the builder, each receipt day, the winners' day, prices and move-in month. */
    fun notice(notice: Notice): String = buildString {
        append("${Speech.name(notice.name)} 공고입니다.")
        val where = neighborhood(notice.address)
        val what = when (notice.kind) {
            "민영" -> "민영 아파트"
            "국민" -> "국민주택"
            else -> "아파트"
        }
        when {
            where != null && notice.households != null ->
                append(" ${where}에 들어서는 ${Speech.ro(what)}, 모두 ${notice.households}세대를 공급합니다.")
            where != null -> append(" ${where}에 들어서는 ${what}입니다.")
            notice.households != null -> append(" 공급 규모는 ${notice.households}세대입니다.")
        }
        builderName(notice.builder)?.let { append(" 시공사는 ${it}입니다.") }
        val steps = listOfNotNull(
            notice.specialDay?.let { "특별공급 ${Speech.date(it)}" },
            notice.firstRankDay?.let { "1순위 ${Speech.date(it)}" },
            notice.secondRankDay?.let { "2순위 ${Speech.date(it)}" },
        )
        val start = notice.receiptStart
        val end = notice.receiptEnd
        when {
            steps.size >= 2 -> append(" 청약 접수는 ${steps.joinToString(", ")}입니다.")
            start != null && end != null -> append(" 청약 접수는 ${Speech.date(start)}부터 ${Speech.date(end)}까지입니다.")
        }
        notice.winnersDay?.let { append(" 당첨자는 ${Speech.date(it)}에 발표합니다.") }
        prices(notice.models)?.let { append(" $it") }
        if (notice.priceCap) append(" 분양가상한제가 적용됩니다.")
        notice.moveIn?.let { append(" 입주는 ${it.year}년 ${it.monthValue}월 예정입니다.") }
    }

    /** This week's notices, then next week's, after [intro]. Null when both are empty. */
    fun noticeNews(intro: String, thisWeek: List<Notice>, nextWeek: List<Notice>): String? {
        if (thisWeek.isEmpty() && nextWeek.isEmpty()) return null
        return buildString {
            append(intro)
            if (thisWeek.isNotEmpty()) {
                append(" 이번 주 청약 접수 소식입니다. ")
                append(thisWeek.take(MAX_NOTICES).joinToString(" ", transform = ::notice))
            }
            if (nextWeek.isNotEmpty()) {
                append(if (thisWeek.isEmpty()) " 다음 주에 접수를 시작하는 공고가 있습니다. " else " 다음 주에 접수를 시작하는 공고도 있습니다. ")
                append(nextWeek.take(MAX_NOTICES).joinToString(" ", transform = ::notice))
            }
        }.trim()
    }

    fun noticeHighlight(place: String, thisWeek: List<Notice>, nextWeek: List<Notice>): String? {
        thisWeek.firstOrNull { it.receiptEnd != null }?.let {
            return "$place ${Speech.name(it.name)} 청약 접수가 ${Speech.date(it.receiptEnd!!)}에 마감됩니다."
        }
        return nextWeek.firstOrNull()?.let {
            "$place ${Speech.name(it.name)} 청약 접수가 다음 주 ${Speech.date(it.receiptStart!!)}에 시작됩니다."
        }
    }

    /** Highest first-rank local rate, or null before results are published. */
    fun highestRate(rows: List<Map<String, Any?>>): Double? =
        rows.filter { it["RESIDE_SENM"] == "해당지역" && it["SUBSCRPT_RANK_CODE"]?.toString() == "1" }
            .mapNotNull { it["CMPET_RATE"]?.toString()?.replace(",", "")?.toDoubleOrNull() }
            .maxOrNull()

    /** Result of a closed notice: applications against general supply, and house types left short. */
    data class Competition(val topRate: Double?, val applicants: Int, val supply: Int, val types: Int, val shortTypes: Int)

    /**
     * Sums a closed notice's competition rows. A type is short when its last round still shows
     * a shortfall ("(△12)"). Null before any result is published.
     */
    fun competitionOf(rows: List<Map<String, Any?>>): Competition? {
        if (rows.isEmpty()) return null
        fun Any?.int() = this?.toString()?.replace(",", "")?.trim()?.toIntOrNull()
        val byType = rows.groupBy { it["HOUSE_TY"]?.toString().orEmpty() }
        val supply = byType.values.sumOf { it.first()["SUPLY_HSHLDCO"].int() ?: 0 }
        val applicants = rows.sumOf { it["REQ_CNT"].int() ?: 0 }
        val short = byType.values.count { type ->
            val last = type.maxBy { (it["SUBSCRPT_RANK_CODE"].int() ?: 0) * 10 + (it["RESIDE_SECD"].int() ?: 0) }
            "△" in last["CMPET_RATE"]?.toString().orEmpty()
        }
        val rate = highestRate(rows)
        if (rate == null && applicants == 0) return null
        return Competition(rate, applicants, supply, byType.size, short)
    }

    fun competition(notice: Notice, result: Competition): String = buildString {
        append("최근 마감한 ${Speech.name(notice.name)}의 청약 결과입니다.")
        if (result.supply > 0) {
            append(" 일반공급 ${result.supply}세대에 1순위와 2순위를 합쳐 ${result.applicants}건이 접수됐습니다.")
        }
        result.topRate?.let { append(" 1순위 해당지역 최고 경쟁률은 ${Speech.number(it)}대 1입니다.") }
        when {
            result.types == 0 -> Unit
            result.shortTypes == 0 -> append(" ${result.types}개 주택형 모두 신청이 공급 세대를 넘었습니다.")
            else -> append(" ${result.types}개 주택형 가운데 ${result.shortTypes}개는 신청이 공급 세대보다 적었습니다.")
        }
    }

    fun competitionHighlight(notice: Notice, result: Competition): String {
        val name = Speech.name(notice.name)
        result.topRate?.let { return "$name 1순위 해당지역 최고 경쟁률은 ${Speech.number(it)}대 1입니다." }
        return "$name ${result.types}개 주택형 가운데 ${result.shortTypes}개는 신청이 공급보다 적었습니다."
    }

    data class IndexPoint(val weekStart: LocalDate, val value: Double)

    private fun weeklyChange(sorted: List<IndexPoint>): Double? =
        if (sorted.size < 2) null else (sorted.last().value / sorted[sorted.size - 2].value - 1) * 100

    /**
     * The latest week against the one before, the streak, the change over four weeks, and
     * [wider] (the province, as name and points) for the same week.
     */
    fun priceIndex(name: String, points: List<IndexPoint>, wider: Pair<String, List<IndexPoint>>? = null): String? {
        val sorted = points.sortedBy { it.weekStart }
        val change = weeklyChange(sorted) ?: return null
        val last = sorted.last()
        return buildString {
            append("$name 아파트 매매가격지수는 ${Speech.date(last.weekStart)} 주간 기준 ${Speech.number(last.value)}로, ")
            append("전주보다 ${Speech.change(change)}.")
            val streak = streak(sorted)
            if (streak >= 2) append(" ${streak}주 연속 ${if (change > 0) "상승" else "하락"}입니다.")
            if (sorted.size > MONTH_WEEKS) {
                val month = (last.value / sorted[sorted.size - 1 - MONTH_WEEKS].value - 1) * 100
                append(" 최근 ${MONTH_WEEKS}주 동안은 누적으로 ${Speech.change(month)}.")
            }
            wider?.let { (area, widerPoints) ->
                val other = widerPoints.sortedBy { it.weekStart }
                if (other.lastOrNull()?.weekStart == last.weekStart) {
                    weeklyChange(other)?.let { append(" 같은 주 $area 전체는 ${Speech.change(it)}.") }
                }
            }
        }
    }

    /** "분당구 아파트값은 전주보다 0.12퍼센트 상승했습니다. 3주 연속 상승입니다." */
    fun priceIndexHighlight(name: String, points: List<IndexPoint>): String? {
        val sorted = points.sortedBy { it.weekStart }
        if (sorted.size < 2) return null
        val change = (sorted.last().value / sorted[sorted.size - 2].value - 1) * 100
        val streak = streak(sorted)
        return buildString {
            append("$name 아파트값은 전주보다 ${Speech.change(change)}.")
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

    /** R-ONE class of the region's province as a whole, for comparison. */
    fun rOneProvince(region: Region, classes: List<Pair<Int, String>>): Pair<Int, String>? {
        val sido = rOneSido(region.sidoShort)
        return classes.firstOrNull { it.second == sido }?.let { it.first to region.sidoShort }
    }

    /** [since] is an earlier month (spoken name and value) to compare with, a few months back. */
    fun rate(label: String, month: String, values: List<Double>, since: Pair<String, Double>? = null): String? {
        val current = values.lastOrNull() ?: return null
        val base = "$label ${month} 기준 연 ${Speech.number(current)}퍼센트"
        val previous = values.getOrNull(values.size - 2) ?: return "$base 입니다.".replace(" 입니다.", "입니다.")
        val delta = Math.round((current - previous) * 100) / 100.0
        val text = when {
            delta > 0 -> "$base 로, 전월보다 ${Speech.number(delta)}퍼센트포인트 올랐습니다."
            delta < 0 -> "$base 로, 전월보다 ${Speech.number(-delta)}퍼센트포인트 내렸습니다."
            else -> "$base 로, 전월과 같습니다."
        }.replace(" 로,", "로,")
        val (earlier, value) = since ?: return text
        val gap = Math.round((current - value) * 100) / 100.0
        return text + when {
            gap > 0 -> " ${earlier}보다는 ${Speech.number(gap)}퍼센트포인트 높습니다."
            gap < 0 -> " ${earlier}보다는 ${Speech.number(-gap)}퍼센트포인트 낮습니다."
            else -> " ${Speech.with(earlier)} 같은 수준입니다."
        }
    }

    private const val MIN_DEALS = 5
    private const val MIN_SIZE_DEALS = 3
    private const val MIN_BUSIEST = 3
    private const val MAX_SIZES = 3
    private const val MONTH_WEEKS = 4
    const val MAX_NOTICES = 2
    private val NEIGHBORHOOD = Regex(".+(동|읍|면|리|\\d가)")
}
