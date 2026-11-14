package ai.bureadcar.app.data

import ai.bureadcar.app.BuildConfig
import ai.bureadcar.app.briefing.Speech
import ai.bureadcar.app.model.Category
import ai.bureadcar.app.model.Region
import ai.bureadcar.app.model.RouteBasis
import ai.bureadcar.app.model.Segment
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async

/** Fetches one category's news. Regional providers return one segment per region with news. */
interface NewsProvider {
    val category: Category
    val source: String
    /** False for nationwide news, which is read once per briefing. */
    val regional: Boolean
    suspend fun fetch(regions: List<Region>, today: LocalDate): List<RegionNews>

    /** Loads what does not depend on the route ahead of time, so the first briefing starts sooner. */
    suspend fun warmUp(today: LocalDate) {}
}

/**
 * [highlight] is one sentence for the closing summary: the point worth remembering.
 * [area] places news with no district: a province ("서울"), "수도권", or "전국"; null reads as nationwide.
 */
data class RegionNews(
    val region: Region?,
    val text: String,
    val isSample: Boolean = false,
    val highlight: String? = null,
    val area: String? = null,
)

private const val RTMS = "https://apis.data.go.kr/1613000"
private val YM: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")

private fun rtmsUrl(service: String, operation: String, region: Region, month: YearMonth, page: Int) =
    "$RTMS/$service/$operation?serviceKey=${BuildConfig.DATA_GO_KR_API_KEY}" +
        "&LAWD_CD=${region.lawdCode}&DEAL_YMD=${month.format(YM)}&pageNo=$page&numOfRows=1000"

/** Rows that failed to load are treated as missing, so the comparison is skipped instead of the news. */
private suspend fun rowsOrEmpty(load: suspend () -> List<Map<String, String>>): List<Map<String, String>> = try {
    load()
} catch (e: java.io.IOException) {
    emptyList()
}

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
        val load: suspend (YearMonth) -> List<Map<String, String>> = { m ->
            collectApiPages(1000) { page ->
                parseRtmsPage(httpGet(rtmsUrl("RTMSDataSvcAptTradeDev", "getRTMSDataSvcAptTradeDev", region, m, page)))
            }
        }
        val (month, rows) = latestRows(today, load) ?: return@mapNotNull null
        val previous = rowsOrEmpty { load(month.minusMonths(1)) }
        val text = Scripts.trade(region, month.monthValue, rows, previous) ?: return@mapNotNull null
        RegionNews(region, text, highlight = Scripts.tradeHighlight(region, month.monthValue, rows, previous))
    }
}

class RentProvider : NewsProvider {
    override val category = Category.RENT
    override val source = "국토교통부 실거래가 공개시스템"
    override val regional = true

    override suspend fun fetch(regions: List<Region>, today: LocalDate) = regions.mapNotNull { region ->
        val load: suspend (YearMonth) -> List<Map<String, String>> = { m ->
            collectApiPages(1000) { page ->
                parseRtmsPage(httpGet(rtmsUrl("RTMSDataSvcAptRent", "getRTMSDataSvcAptRent", region, m, page)))
            }
        }
        val (month, rows) = latestRows(today, load) ?: return@mapNotNull null
        val previous = rowsOrEmpty { load(month.minusMonths(1)) }
        val text = Scripts.rent(region, month.monthValue, rows, previous) ?: return@mapNotNull null
        RegionNews(region, text, highlight = Scripts.rentHighlight(region, month.monthValue, rows, previous))
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
            val (thisWeek, nextWeek) = Scripts.noticeWeeks(local, today).let { (a, b) -> withModels(a) to withModels(b) }
            Scripts.noticeNews("", thisWeek, nextWeek)?.let {
                return@mapNotNull RegionNews(region, it, highlight = Scripts.noticeHighlight(region.sigungu, thisWeek, nextWeek))
            }
            competition(region, local, today)
        }
        if (news.isNotEmpty()) return news
        // Nothing on the route: widen to the province, and say so.
        val region = regions.lastOrNull() ?: return emptyList()
        val (thisWeek, nextWeek) = Scripts.noticeWeeks(bySido[region.sidoShort].orEmpty(), today)
            .let { (a, b) -> withModels(a) to withModels(b) }
        val text = Scripts.noticeNews(
            "경로 지역에는 이번 주와 다음 주 공고가 없어 ${region.sidoShort} 소식을 전합니다.",
            thisWeek,
            nextWeek,
        ) ?: return emptyList()
        // Province news is not one district's, so it is read after the route.
        return listOf(
            RegionNews(null, text, highlight = Scripts.noticeHighlight(region.sidoShort, thisWeek, nextWeek), area = region.sidoShort),
        )
    }

    /** The latest notice that closed in the last month, with its applications and shortfalls. */
    private suspend fun competition(region: Region, local: List<Scripts.Notice>, today: LocalDate): RegionNews? {
        val closed = local.filter {
            val end = it.receiptEnd ?: return@filter false
            end.isBefore(today) && !end.isBefore(today.minusDays(30))
        }.maxByOrNull { it.receiptEnd!! } ?: return null
        val url = "$base/ApplyhomeInfoCmpetRtSvc/v1/getAPTLttotPblancCmpet?perPage=100" +
            "&${encode("cond[HOUSE_MANAGE_NO::EQ]")}=${closed.manageNo}&serviceKey=${BuildConfig.DATA_GO_KR_API_KEY}"
        val rows = collectApiPages(100) { page -> parseOdcloudPage(httpGet("$url&page=$page")) }
        val result = Scripts.competitionOf(rows) ?: return null
        return RegionNews(
            region,
            Scripts.competition(closed, result),
            highlight = Scripts.competitionHighlight(closed, result),
        )
    }

    private val models = mutableMapOf<String, List<Scripts.HouseModel>>()

    /** Adds house types and prices to the notices that will be read. A failed lookup leaves them out. */
    private suspend fun withModels(notices: List<Scripts.Notice>): List<Scripts.Notice> =
        notices.take(Scripts.MAX_NOTICES).map { notice ->
            val known = models[notice.manageNo] ?: try {
                loadModels(notice.manageNo).also { models[notice.manageNo] = it }
            } catch (e: java.io.IOException) {
                emptyList()
            }
            notice.copy(models = known)
        } + notices.drop(Scripts.MAX_NOTICES)

    private suspend fun loadModels(manageNo: String): List<Scripts.HouseModel> {
        val url = "$base/ApplyhomeInfoDetailSvc/v1/getAPTLttotPblancMdl?perPage=100" +
            "&${encode("cond[HOUSE_MANAGE_NO::EQ]")}=$manageNo&serviceKey=${BuildConfig.DATA_GO_KR_API_KEY}"
        return collectApiPages(100) { page -> parseOdcloudPage(httpGet("$url&page=$page")) }.mapNotNull { row ->
            val area = row["HOUSE_TY"]?.toString()?.let { Regex("^\\d+(\\.\\d+)?").find(it)?.value }?.toDoubleOrNull()
                ?: return@mapNotNull null
            fun count(key: String) = row[key]?.toString()?.replace(",", "")?.toIntOrNull() ?: 0
            Scripts.HouseModel(
                area = area,
                households = count("SUPLY_HSHLDCO") + count("SPSPLY_HSHLDCO"),
                topPrice = row["LTTOT_TOP_AMOUNT"]?.toString()?.let(Speech::parseAmount),
            )
        }
    }

    private suspend fun loadNotices(sido: String): List<Scripts.Notice> {
        val url = "$base/ApplyhomeInfoDetailSvc/v1/getAPTLttotPblancDetail?perPage=200" +
            "&${encode("cond[SUBSCRPT_AREA_CODE_NM::EQ]")}=${encode(sido)}&serviceKey=${BuildConfig.DATA_GO_KR_API_KEY}"
        return collectApiPages(200) { page -> parseOdcloudPage(httpGet("$url&page=$page")) }.map { row ->
            fun text(key: String) = row[key]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            fun date(key: String) = text(key)?.let(::dateOrNull)
            Scripts.Notice(
                manageNo = text("HOUSE_MANAGE_NO").orEmpty(),
                name = text("HOUSE_NM").orEmpty(),
                address = text("HSSPLY_ADRES").orEmpty(),
                areaName = text("SUBSCRPT_AREA_CODE_NM").orEmpty(),
                households = text("TOT_SUPLY_HSHLDCO")?.toIntOrNull(),
                receiptStart = date("RCEPT_BGNDE"),
                receiptEnd = date("RCEPT_ENDDE"),
                kind = text("HOUSE_DTL_SECD_NM"),
                builder = text("CNSTRCT_ENTRPS_NM"),
                specialDay = date("SPSPLY_RCEPT_BGNDE"),
                firstRankDay = date("GNRL_RNK1_CRSPAREA_RCPTDE"),
                secondRankDay = date("GNRL_RNK2_CRSPAREA_RCPTDE"),
                winnersDay = date("PRZWNER_PRESNATN_DE"),
                moveIn = text("MVN_PREARNGE_YM")?.let { runCatching { YearMonth.parse(it, YM) }.getOrNull() },
                priceCap = text("PARCPRC_ULS_AT") == "Y",
            )
        }
    }

    private fun dateOrNull(text: String) = runCatching { LocalDate.parse(text) }.getOrNull()


}

class PriceIndexProvider : NewsProvider {
    override val category = Category.PRICE_INDEX
    override val source = "한국부동산원 주간아파트동향"
    override val regional = true

    private val base = "https://www.reb.or.kr/r-one/openapi"
    private val table = "T244183132827305"
    private val classMutex = Mutex()
    private var classes: List<Pair<Int, String>>? = null

    /** Weekly data, so each class's points are kept for an hour (passing districts, replays). */
    private val pointCache = java.util.concurrent.ConcurrentHashMap<Int, Pair<Long, List<Scripts.IndexPoint>>>()

    override suspend fun warmUp(today: LocalDate) {
        classList()
    }

    /**
     * R-ONE takes seconds per request, so the districts' and their provinces' points are requested at once.
     * A district's own failure fails the category; a province's only drops the comparison.
     */
    override suspend fun fetch(regions: List<Region>, today: LocalDate): List<RegionNews> = coroutineScope {
        val known = classList()
        val wanted = regions.mapNotNull { region ->
            val own = Scripts.rOneClass(region, known)?.first ?: return@mapNotNull null
            // The province's own move for the same week, unless the district is the province (세종).
            Triple(region, own, Scripts.rOneProvince(region, known)?.takeIf { it.first != own })
        }
        val own = wanted.map { it.second }.distinct().associateWith { id -> async { points(id, today) } }
        val provinces = wanted.mapNotNull { it.third?.first }.distinct().filterNot { it in own }
            .associateWith { id -> async { runCatching { points(id, today) }.getOrNull() } }
        wanted.mapNotNull { (region, id, province) ->
            val points = own.getValue(id).await()
            val wider = province?.let { (provinceId, name) ->
                val other = own[provinceId]?.await() ?: provinces[provinceId]?.await()
                other?.let { name to it }
            }
            Scripts.priceIndex(region.sigungu, points, wider)?.let {
                RegionNews(region, it, highlight = Scripts.priceIndexHighlight(region.sigungu, points))
            }
        }
    }

    private suspend fun classList(): List<Pair<Int, String>> = classMutex.withLock {
        classes ?: loadClasses().also { classes = it }
    }

    private suspend fun points(classId: Int, today: LocalDate): List<Scripts.IndexPoint> {
        pointCache[classId]?.let { (at, cached) -> if (System.currentTimeMillis() - at < POINTS_TTL_MS) return cached }
        return loadPoints(classId, today).also { pointCache[classId] = System.currentTimeMillis() to it }
    }

    private suspend fun loadClasses(): List<Pair<Int, String>> {
        val url = "$base/SttsApiTblItm.do?KEY=${BuildConfig.RONE_API_KEY}&Type=json&pIndex=1&pSize=1000&STATBL_ID=$table"
        return parseRoneRows(httpGet(url), "SttsApiTblItm")
            .filter { it.optString("ITM_TAG") == "분류" }
            .map { it.getInt("ITM_ID") to it.getString("ITM_FULLNM") }
    }

    private suspend fun loadPoints(classId: Int, today: LocalDate): List<Scripts.IndexPoint> {
        val url = "$base/SttsApiTblData.do?KEY=${BuildConfig.RONE_API_KEY}&Type=json&pIndex=1&pSize=20" +
            "&STATBL_ID=$table&DTACYCLE_CD=WK&CLS_ID=$classId&ITM_ID=10001" +
            "&START_WRTTIME=${weekId(today.minusWeeks(8))}&END_WRTTIME=${weekId(today)}"
        return parseRoneRows(httpGet(url), "SttsApiTblData").map {
            Scripts.IndexPoint(LocalDate.parse(it.getString("WRTTIME_DESC")), it.getDouble("DTA_VAL"))
        }
    }

    private fun weekId(date: LocalDate): String {
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "%d%02d".format(year, week)
    }

    private companion object {
        const val POINTS_TTL_MS = 60 * 60 * 1000L
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
            val rows = parseEcosRows(httpGet("$base/$table/$range/$item")).sortedBy { it.first }
            val month = rows.lastOrNull()?.first ?: return@mapNotNull null
            val values = rows.map { it.second }
            // A few months back as well, so a one-month move is seen against the trend.
            val since = rows.takeIf { it.size >= 4 }?.first()?.let { (m, v) -> spokenMonth(m) to v }
            val text = Scripts.rate(label, spokenMonth(month), values, since) ?: return@mapNotNull null
            text to Scripts.rate(label, spokenMonth(month), values)
        }
        // The mortgage rate is what drivers act on; the base rate is the fallback point. The closing keeps it short.
        return if (parts.isEmpty()) {
            emptyList()
        } else {
            listOf(RegionNews(null, parts.joinToString(" ") { it.first }, highlight = parts.last().second))
        }
    }

    private fun spokenMonth(yyyymm: String) = "${yyyymm.take(4)}년 ${yyyymm.drop(4).toInt()}월"


}

fun allProviders(): Map<Category, NewsProvider> {
    val releases = PressReleaseFeed()
    return Category.entries.associateWith { category ->
        when (category) {
            Category.TRADE -> TradeProvider()
            Category.RENT -> RentProvider()
            Category.SUBSCRIPTION -> SubscriptionProvider()
            Category.PRICE_INDEX -> PriceIndexProvider()
            Category.RATE -> RateProvider()
            Category.TRANSPORT, Category.DEVELOPMENT, Category.REDEVELOPMENT, Category.POLICY, Category.UNSOLD ->
                PressReleaseProvider(category, releases)
        }
    }
}

/** Turns provider output into playback segments. */
class BriefingBuilder(private val providers: Map<Category, NewsProvider>) {

    /**
     * Reads the route district by district, each district's 1부 and 2부 together, in driving order.
     * News that belongs to no single district (rates, province or nationwide stand-ins) follows.
     */
    suspend fun build(
        categories: List<Category>,
        regions: List<Region>,
        priorities: Map<String, Int>,
        today: LocalDate,
        dayName: String,
        basis: RouteBasis? = null,
    ): List<Segment> = buildList {
        val intro = "부.읽.카입니다. 참고용 정보이며 투자 권유가 아닙니다. 오늘은 $dayName ${Speech.list(categories.map { it.label })} 소식입니다."
        add(Segment("안내", listOfNotNull(intro, basis?.let { routeLine(it, regions) }).joinToString(" ")))
        // Providers wait on slow public APIs, so every category is fetched at the same time.
        val results = coroutineScope {
            categories.map { category -> async { category to apiResult { providers.getValue(category).fetch(regions, today) } } }
                .awaitAll().toMap()
        }
        val news = results.mapValues { it.value.getOrDefault(emptyList()) }

        // News for a province or the whole country is read as that program's part in the first district
        // of the route it covers, so every district keeps its 1부 then 2부.
        val shared = categories.flatMap { category ->
            news.getValue(category).filter { it.region == null }.map { item -> Triple(home(item.area, regions), category, item) }
        }
        regions.forEach { region ->
            val parts = categories.map { category ->
                val local = news.getValue(category).filter { it.region?.lawdCode == region.lawdCode } +
                    shared.filter { it.first == region && it.second == category }.map { it.third }
                category to local
            }
            if (parts.all { it.second.isEmpty() }) return@forEach
            // A program with news elsewhere on the route says it has none here; nationwide ones stay quiet.
            val here = parts.flatMap { (category, items) ->
                when {
                    items.isNotEmpty() -> items.map { category to it as RegionNews? }
                    news.getValue(category).isNotEmpty() && providers.getValue(category).regional -> listOf(category to null)
                    else -> emptyList()
                }
            }
            here.forEachIndexed { index, (category, item) ->
                val lead = when {
                    here.size == 1 && item?.region == null -> "${category.label} 소식입니다."
                    here.size == 1 -> "${region.sigungu} ${category.label} 소식입니다."
                    index == 0 -> "${region.sigungu} 소식입니다. 먼저 ${category.label}입니다."
                    else -> "이어서 ${category.label}입니다."
                }
                if (item != null) {
                    add(toSegment(category, item, priorities, lead, region))
                } else {
                    add(
                        Segment(
                            category.label,
                            "$lead ${region.sigungu}에는 이번 주 새 소식이 없습니다.",
                            priority = priorities[region.lawdCode],
                            region = region.sigungu.substringAfterLast(" "),
                        ),
                    )
                }
            }
        }

        categories.forEach { category ->
            val result = results.getValue(category)
            when {
                result.isFailure -> add(Segment(category.label, "${Speech.topic(category.label)} 소식을 불러오지 못해 건너뜁니다."))
                news.getValue(category).isEmpty() -> add(Segment(category.label, "${Speech.topic(category.label)} 이번 주 새 소식이 없습니다."))
            }
        }
        val sources = categories.filter { news.getValue(it).isNotEmpty() }.map { providers.getValue(it).source }.distinct()
        if (sources.isNotEmpty()) add(Segment("출처", "출처는 ${sources.joinToString(", ")}입니다."))
    }

    /** News for a district entered mid-drive; regional categories only. */
    suspend fun forPassing(categories: List<Category>, region: Region, today: LocalDate): List<Segment> = coroutineScope {
        data class Part(val category: Category, val news: RegionNews?, val failed: Boolean = false)

        categories.mapNotNull { providers[it]?.takeIf { p -> p.regional } }
            .map { provider -> async { provider to apiResult { provider.fetch(listOf(region), today) } } }
            .awaitAll()
            .flatMap { (provider, result) ->
                // Keep lookup failures distinct from successful lookups with no local news.
                if (result.isFailure) {
                    listOf(Part(provider.category, null, failed = true))
                } else {
                    // Province/nationwide stand-ins were already read at the start.
                    val local = result.getOrThrow().filter { it.region != null }
                    if (local.isEmpty()) listOf(Part(provider.category, null))
                    else local.map { Part(provider.category, it) }
                }
            }
            .let { here ->
                // Keep each district's programs together, including unavailable programs.
                here.mapIndexed { index, (category, news, failed) ->
                    val lead = when {
                        here.size == 1 -> "지금 지나는 ${region.sigungu}의 ${category.label} 소식입니다."
                        index == 0 -> "지금 지나는 ${region.sigungu} 소식입니다. 먼저 ${category.label}입니다."
                        else -> "이어서 ${category.label}입니다."
                    }
                    val text = when {
                        failed -> "${region.sigungu}의 ${category.label} 소식을 불러오지 못해 건너뜁니다."
                        news != null -> news.text
                        else -> "${region.sigungu}에는 이번 주 새 소식이 없습니다."
                    }
                    Segment(
                        category.label,
                        "$lead $text",
                        priority = Int.MAX_VALUE,
                        isSample = news?.isSample ?: false,
                        region = region.sigungu.substringAfterLast(" "),
                        passing = true,
                    )
                }
            }
    }

    /** Loads route-independent data for every category in the background, ignoring failures. */
    suspend fun warmUp(today: LocalDate) = coroutineScope {
        providers.values.distinct().forEach { provider -> launch { apiResult { provider.warmUp(today) } } }
    }

    companion object {
        /**
         * "내비게이션 경로를 따라 성남시 분당구와 강남구 순서로 전해 드립니다." Districts are read in route order,
         * so they are named in that order; a long route names the first three and the count.
         */
        fun routeLine(basis: RouteBasis, regions: List<Region>): String {
            val names = regions.map { it.sigungu }
            val order = when {
                names.size > MAX_NAMED -> "${names.take(MAX_NAMED - 1).joinToString(", ")} 등 ${names.size}개 지역을 차례로"
                else -> "${Speech.list(names)} 순서로"
            }
            val one = names.singleOrNull()
            val line = when (basis) {
                RouteBasis.Navigation ->
                    if (one != null) "내비게이션 경로에 있는 $one 소식을 전해 드립니다." else "내비게이션 경로를 따라 $order 전해 드립니다."
                RouteBasis.CurrentLocation ->
                    "내비게이션에 목적지가 없어 지금 계신 ${one ?: Speech.list(names)} 소식을 전해 드립니다."
                is RouteBasis.Saved -> {
                    val name = basis.name.split("→").map { it.trim() }.filter { it.isNotEmpty() }
                        .let { if (it.size == 2) "${it[0]}에서 ${it[1]}까지" else basis.name }
                    if (one != null) "저장된 $name 경로에 있는 $one 소식을 전해 드립니다." else "저장된 $name 경로를 따라 $order 전해 드립니다."
                }
                RouteBasis.Demo -> "시연 주행 경로를 따라 $order 전해 드립니다."
            }
            // Saved routes are read as saved; the others follow the car and add districts on the way.
            return if (basis is RouteBasis.Saved) line else "$line 가는 길에 다른 지역에 들어서면 그 지역 소식도 이어서 전해 드립니다."
        }

        private const val MAX_NAMED = 4
        private val CAPITAL_AREA = setOf("서울", "경기", "인천")
    }

    /** The first district on the route that [area] covers; the first district for nationwide news. */
    private fun home(area: String?, regions: List<Region>): Region =
        regions.firstOrNull { region ->
            when (area) {
                null, "전국" -> true
                "수도권" -> region.sidoShort in CAPITAL_AREA
                else -> region.sidoShort == area
            }
        } ?: regions.first()

    /** [district] is where the news is read; shared news keeps no priority so it is never cut. */
    private fun toSegment(category: Category, news: RegionNews, priorities: Map<String, Int>, lead: String, district: Region) = Segment(
        title = category.label,
        text = "$lead ${news.text}",
        priority = news.region?.let { priorities[it.lawdCode] },
        isSample = news.isSample,
        region = district.sigungu.substringAfterLast(" "),
        highlight = news.highlight,
    )
}
