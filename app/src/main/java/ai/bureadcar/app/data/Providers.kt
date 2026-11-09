package ai.bureadcar.app.data

import ai.bureadcar.app.BuildConfig
import ai.bureadcar.app.model.Category
import ai.bureadcar.app.model.Region
import ai.bureadcar.app.model.Segment
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import org.json.JSONArray
import org.json.JSONObject

/** Fetches one category's news. Regional providers return one segment per region with news. */
interface NewsProvider {
    val category: Category
    val source: String
    /** False for nationwide news, which is read once per briefing. */
    val regional: Boolean
    suspend fun fetch(regions: List<Region>, today: LocalDate): List<RegionNews>
}

data class RegionNews(val region: Region?, val text: String, val isSample: Boolean = false)

private const val RTMS = "https://apis.data.go.kr/1613000"
private val YM: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")

private fun rtmsUrl(service: String, operation: String, region: Region, month: YearMonth) =
    "$RTMS/$service/$operation?serviceKey=${BuildConfig.DATA_GO_KR_API_KEY}" +
        "&LAWD_CD=${region.lawdCode}&DEAL_YMD=${month.format(YM)}&pageNo=1&numOfRows=1000"

/** Latest of the last two complete months that has rows; this month is still being reported. */
private suspend fun latestRows(
    today: LocalDate,
    load: suspend (YearMonth) -> List<Map<String, String>>,
): Pair<YearMonth, List<Map<String, String>>>? {
    val current = YearMonth.from(today)
    for (back in 1L..2L) {
        val month = current.minusMonths(back)
        val rows = load(month)
        if (rows.isNotEmpty()) return month to rows
    }
    return null
}

class TradeProvider : NewsProvider {
    override val category = Category.TRADE
    override val source = "국토교통부 실거래가 공개시스템"
    override val regional = true

    override suspend fun fetch(regions: List<Region>, today: LocalDate) = regions.mapNotNull { region ->
        val (month, rows) = latestRows(today) { m ->
            parseRtmsItems(httpGet(rtmsUrl("RTMSDataSvcAptTradeDev", "getRTMSDataSvcAptTradeDev", region, m)))
        } ?: return@mapNotNull null
        Scripts.trade(region, month.monthValue, rows)?.let { RegionNews(region, it) }
    }
}

class RentProvider : NewsProvider {
    override val category = Category.RENT
    override val source = "국토교통부 실거래가 공개시스템"
    override val regional = true

    override suspend fun fetch(regions: List<Region>, today: LocalDate) = regions.mapNotNull { region ->
        val (month, rows) = latestRows(today) { m ->
            parseRtmsItems(httpGet(rtmsUrl("RTMSDataSvcAptRent", "getRTMSDataSvcAptRent", region, m)))
        } ?: return@mapNotNull null
        Scripts.rent(region, month.monthValue, rows)?.let { RegionNews(region, it) }
    }
}

class SubscriptionProvider : NewsProvider {
    override val category = Category.SUBSCRIPTION
    override val source = "한국부동산원 청약홈"
    override val regional = true

    private val base = "https://api.odcloud.kr/api"

    override suspend fun fetch(regions: List<Region>, today: LocalDate): List<RegionNews> {
        val bySido = mutableMapOf<String, List<Scripts.Notice>>()
        val news = regions.mapNotNull { region ->
            val notices = bySido.getOrPut(region.sidoShort) { loadNotices(region.sidoShort) }
            val local = notices.filter { Scripts.inRegion(it.address, region) }
            val open = Scripts.openNotices(local, today).take(3)
            if (open.isNotEmpty()) {
                return@mapNotNull RegionNews(region, "${region.sigungu} 소식입니다. " + open.joinToString(" ", transform = Scripts::notice))
            }
            competition(local, today)?.let { RegionNews(region, "${region.sigungu} 소식입니다. $it") }
        }
        if (news.isNotEmpty()) return news
        // Nothing on the route: widen to the province, and say so.
        val region = regions.lastOrNull() ?: return emptyList()
        val open = Scripts.openNotices(bySido[region.sidoShort].orEmpty(), today).take(2)
        if (open.isEmpty()) return emptyList()
        return listOf(
            RegionNews(
                region,
                "경로 지역에는 새 공고가 없어 ${region.sidoShort} 소식을 전합니다. " +
                    open.joinToString(" ", transform = Scripts::notice),
            ),
        )
    }

    private suspend fun competition(local: List<Scripts.Notice>, today: LocalDate): String? {
        val closed = local.filter {
            val end = it.receiptEnd ?: return@filter false
            end.isBefore(today) && !end.isBefore(today.minusDays(30))
        }.maxByOrNull { it.receiptEnd!! } ?: return null
        val url = "$base/ApplyhomeInfoCmpetRtSvc/v1/getAPTLttotPblancCmpet?page=1&perPage=100" +
            "&${encode("cond[HOUSE_MANAGE_NO::EQ]")}=${closed.manageNo}&serviceKey=${BuildConfig.DATA_GO_KR_API_KEY}"
        val rate = Scripts.highestRate(jsonRows(httpGet(url))) ?: return null
        return Scripts.competition(closed, rate)
    }

    private suspend fun loadNotices(sido: String): List<Scripts.Notice> {
        val url = "$base/ApplyhomeInfoDetailSvc/v1/getAPTLttotPblancDetail?page=1&perPage=200" +
            "&${encode("cond[SUBSCRPT_AREA_CODE_NM::EQ]")}=${encode(sido)}&serviceKey=${BuildConfig.DATA_GO_KR_API_KEY}"
        return jsonRows(httpGet(url)).map { row ->
            Scripts.Notice(
                manageNo = row["HOUSE_MANAGE_NO"]?.toString().orEmpty(),
                name = row["HOUSE_NM"]?.toString().orEmpty(),
                address = row["HSSPLY_ADRES"]?.toString().orEmpty(),
                areaName = row["SUBSCRPT_AREA_CODE_NM"]?.toString().orEmpty(),
                households = row["TOT_SUPLY_HSHLDCO"]?.toString()?.toIntOrNull(),
                receiptStart = row["RCEPT_BGNDE"]?.toString()?.let(::dateOrNull),
                receiptEnd = row["RCEPT_ENDDE"]?.toString()?.let(::dateOrNull),
            )
        }
    }

    private fun dateOrNull(text: String) = runCatching { LocalDate.parse(text) }.getOrNull()

    private fun jsonRows(json: String): List<Map<String, Any?>> {
        val data = JSONObject(json).optJSONArray("data") ?: JSONArray()
        return List(data.length()) { i ->
            val row = data.getJSONObject(i)
            row.keys().asSequence().associateWith { key -> row.opt(key).takeUnless { it == JSONObject.NULL } }
        }
    }
}

class PriceIndexProvider : NewsProvider {
    override val category = Category.PRICE_INDEX
    override val source = "한국부동산원 주간아파트동향"
    override val regional = true

    private val base = "https://www.reb.or.kr/r-one/openapi"
    private val table = "T244183132827305"
    private var classes: List<Pair<Int, String>>? = null

    override suspend fun fetch(regions: List<Region>, today: LocalDate): List<RegionNews> {
        val known = classes ?: loadClasses().also { classes = it }
        return regions.mapNotNull { region ->
            val (id, _) = Scripts.rOneClass(region, known) ?: return@mapNotNull null
            val points = loadPoints(id, today)
            Scripts.priceIndex(region.sigungu, points)?.let { RegionNews(region, it) }
        }
    }

    private suspend fun loadClasses(): List<Pair<Int, String>> {
        val url = "$base/SttsApiTblItm.do?KEY=${BuildConfig.RONE_API_KEY}&Type=json&pIndex=1&pSize=1000&STATBL_ID=$table"
        return rows(httpGet(url), "SttsApiTblItm")
            .filter { it.optString("ITM_TAG") == "분류" }
            .map { it.getInt("ITM_ID") to it.getString("ITM_FULLNM") }
    }

    private suspend fun loadPoints(classId: Int, today: LocalDate): List<Scripts.IndexPoint> {
        val url = "$base/SttsApiTblData.do?KEY=${BuildConfig.RONE_API_KEY}&Type=json&pIndex=1&pSize=20" +
            "&STATBL_ID=$table&DTACYCLE_CD=WK&CLS_ID=$classId&ITM_ID=10001" +
            "&START_WRTTIME=${weekId(today.minusWeeks(8))}&END_WRTTIME=${weekId(today)}"
        return rows(httpGet(url), "SttsApiTblData").map {
            Scripts.IndexPoint(LocalDate.parse(it.getString("WRTTIME_DESC")), it.getDouble("DTA_VAL"))
        }
    }

    private fun weekId(date: LocalDate): String {
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "%d%02d".format(year, week)
    }

    private fun rows(json: String, name: String): List<JSONObject> {
        val root = JSONObject(json).optJSONArray(name) ?: return emptyList()
        val array = root.optJSONObject(1)?.optJSONArray("row") ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it) }
    }
}

class RateProvider : NewsProvider {
    override val category = Category.RATE
    override val source = "한국은행 경제통계시스템"
    override val regional = false

    private val base = "https://ecos.bok.or.kr/api/StatisticSearch/${BuildConfig.ECOS_API_KEY}/json/kr/1/24"

    override suspend fun fetch(regions: List<Region>, today: LocalDate): List<RegionNews> {
        val end = YearMonth.from(today)
        val start = end.minusMonths(6)
        val range = "M/${start.format(YM)}/${end.format(YM)}"
        val parts = listOf(
            Triple("722Y001", "0101000", "한국은행 기준금리는"),
            Triple("121Y006", "BECBLA0302", "은행 주택담보대출 신규 금리는"),
        ).mapNotNull { (table, item, label) ->
            val rows = rows(httpGet("$base/$table/$range/$item")).sortedBy { it.first }
            val month = rows.lastOrNull()?.first ?: return@mapNotNull null
            Scripts.rate(label, spokenMonth(month), rows.map { it.second })
        }
        return if (parts.isEmpty()) emptyList() else listOf(RegionNews(null, parts.joinToString(" ")))
    }

    private fun spokenMonth(yyyymm: String) = "${yyyymm.take(4)}년 ${yyyymm.drop(4).toInt()}월"

    private fun rows(json: String): List<Pair<String, Double>> {
        val array = JSONObject(json).optJSONObject("StatisticSearch")?.optJSONArray("row") ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it) }.mapNotNull { row ->
            row.optString("DATA_VALUE").toDoubleOrNull()?.let { row.getString("TIME") to it }
        }
    }
}

/** Placeholder for categories without a live source yet. Never sounds like real news. */
class SampleProvider(override val category: Category) : NewsProvider {
    override val source = "예시 데이터"
    override val regional = true

    override suspend fun fetch(regions: List<Region>, today: LocalDate) = regions.map { region ->
        RegionNews(
            region,
            "예시 데이터입니다. 실제 소식이 아닙니다. 이 자리에서 ${region.sigungu}의 ${category.label} 소식을 읽어 드릴 예정입니다.",
            isSample = true,
        )
    }
}

fun allProviders(): Map<Category, NewsProvider> = Category.entries.associateWith { category ->
    when (category) {
        Category.TRADE -> TradeProvider()
        Category.RENT -> RentProvider()
        Category.SUBSCRIPTION -> SubscriptionProvider()
        Category.PRICE_INDEX -> PriceIndexProvider()
        Category.RATE -> RateProvider()
        else -> SampleProvider(category)
    }
}

/** Turns provider output into playback segments. */
class BriefingBuilder(private val providers: Map<Category, NewsProvider>) {

    suspend fun build(
        categories: List<Category>,
        regions: List<Region>,
        priorities: Map<String, Int>,
        today: LocalDate,
        dayName: String,
    ): List<Segment> = buildList {
        add(Segment("안내", "부.읽.카입니다. 참고용 정보이며 투자 권유가 아닙니다."))
        categories.forEachIndexed { index, category ->
            val provider = providers.getValue(category)
            add(Segment(category.label, "오늘은 $dayName ${category.label} 소식입니다."))
            val result = runCatching { provider.fetch(regions, today) }
            result.onSuccess { news ->
                if (news.isEmpty()) add(Segment(category.label, "이번 주 새 소식이 없습니다."))
                news.forEach { add(toSegment(category, it, priorities)) }
                if (news.isNotEmpty()) add(Segment(category.label, "출처는 ${provider.source}입니다."))
            }
            result.onFailure {
                add(Segment(category.label, "소식을 불러오지 못해 ${category.label}은 건너뜁니다."))
            }
            categories.getOrNull(index + 1)?.let {
                add(Segment(category.label, "다음은 ${it.label} 소식입니다."))
            }
        }
    }

    /** News for a district entered mid-drive; regional categories only. */
    suspend fun forPassing(categories: List<Category>, region: Region, today: LocalDate): List<Segment> =
        categories.mapNotNull { providers[it]?.takeIf { p -> p.regional } }.flatMap { provider ->
            runCatching { provider.fetch(listOf(region), today) }.getOrDefault(emptyList()).map { news ->
                Segment(
                    provider.category.label,
                    "지금 지나는 ${region.sigungu} 소식입니다. ${news.text}",
                    priority = Int.MAX_VALUE,
                    isSample = news.isSample,
                    region = region.sigungu.substringAfterLast(" "),
                )
            }
        }

    private fun toSegment(category: Category, news: RegionNews, priorities: Map<String, Int>) = Segment(
        title = category.label,
        text = news.text,
        priority = news.region?.let { priorities[it.lawdCode] },
        isSample = news.isSample,
        region = news.region?.sigungu?.substringAfterLast(" "),
    )
}
