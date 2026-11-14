package ai.bureadcar.app.data

import ai.bureadcar.app.briefing.Speech
import ai.bureadcar.app.model.Category
import ai.bureadcar.app.model.Region
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One MOLIT press release as listed on its board: title, field and posting date. */
data class PressRelease(val id: String, val title: String, val field: String, val date: LocalDate)

/**
 * Reads MOLIT press releases for the news-style categories (transport, development, redevelopment,
 * policy, supply). Releases are public under KOGL type 1, so titles and body lines can be read aloud.
 */
object PressReleases {
    const val SOURCE = "국토교통부 보도자료"

    /** Posts that are not news about places or rules: minister schedules, rebuttals, ceremonies. */
    private val skipTag = Regex("^\\[(장관동정|차관동정|1차관동정|2차관동정|인사|해명|설명)]")
    private val noise = Regex("기념식|박람회|세미나|의 날|시상|개막|공모전|자격시험|합격|안전|사고|과징금|ICAO|유엔|채용")

    /** Checked in order; a release belongs to the first category whose words it contains. */
    private val rules: List<Triple<Category, Regex, Regex?>> = listOf(
        Triple(
            Category.REDEVELOPMENT,
            Regex("재건축|재개발|정비사업|정비구역|노후계획도시|리모델링|도시재생|모아타운|가로주택|소규모주택"),
            null,
        ),
        Triple(
            Category.UNSOLD,
            Regex("미분양|입주\\s*(물량|예정)|호 이상 입주|주택통계|준공 후|인허가 실적|착공 실적"),
            null,
        ),
        Triple(
            Category.TRANSPORT,
            Regex("철도|광역교통|교통대책|교통시설|지하철|전철|GTX|고속도로|국도|도로사업|KTX|SRT|노선|개통|신설역|역세권|트램|광역버스|BRT|공항"),
            Regex("교통비|요금|환급|운임|항공사|합병|물류|자율주행"),
        ),
        Triple(
            Category.DEVELOPMENT,
            Regex("신도시|공공주택지구|택지|산업단지|산단|도시개발|혁신도시|기업도시|개발제한구역|그린벨트|(?<!재)개발사업|복합개발|지구 지정|클러스터|지가"),
            null,
        ),
        Triple(
            Category.POLICY,
            Regex("대출|DSR|LTV|규제지역|조정대상|투기과열|토지거래허가|세제|취득세|양도세|종부세|보유세|대책|전세사기|임대차|청약|공시가격|공급|공공주택|공공임대|임대주택|주택|아파트|다세대|다가구|오피스텔|적발|기획조사|부동산"),
            null,
        ),
    )

    private val row = Regex(
        "<td class=\"bd_title\">\\s*<a href=\"dtl\\.jsp\\?[^\"]*?id=(\\d+)\"[^>]*>(.*?)</a>.*?" +
            "<td class=\"bd_field\">(.*?)</td>.*?<td class=\"bd_date\">(\\d{4}-\\d{2}-\\d{2})</td>",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** Rows of the press release board page. Empty when the page layout no longer matches. */
    fun parseList(html: String): List<PressRelease> = row.findAll(html).map { m ->
        PressRelease(
            id = m.groupValues[1],
            title = clean(m.groupValues[2]),
            field = clean(m.groupValues[3]),
            date = LocalDate.parse(m.groupValues[4]),
        )
    }.toList()

    private val item = Regex(
        "<item>.*?<title>(.*?)</title>.*?<link>.*?id=(\\d+).*?</link>.*?<dc:date>(\\d{4}-\\d{2}-\\d{2})",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** The RSS feed carries only the latest ten posts and no field, so it is the fallback. */
    fun parseRss(xml: String): List<PressRelease> = item.findAll(xml).map { m ->
        PressRelease(
            id = m.groupValues[2],
            title = clean(m.groupValues[1].removePrefix("<![CDATA[").removeSuffix("]]>")),
            field = "",
            date = LocalDate.parse(m.groupValues[3]),
        )
    }.toList()

    fun classify(release: PressRelease): Category? {
        val title = release.title
        if (skipTag.containsMatchIn(title) || noise.containsMatchIn(title)) return null
        rules.firstOrNull { (_, words, except) ->
            words.containsMatchIn(title) && except?.containsMatchIn(title) != true
        }?.let { return it.first }
        return if (release.field == "주택토지") Category.POLICY else null
    }

    /**
     * "11월 12일, 공공재개발 사업수수료 3퍼센트에서 1퍼센트 한시 인하 소식입니다." followed by [details]
     * from the release's body when they could be read. Titles are phrases, so they are framed as news.
     */
    fun spoken(release: PressRelease, details: List<String> = emptyList()): String =
        (listOf("${Speech.date(release.date)}, ${news(headline(release))}입니다.") + details).joinToString(" ")

    /** "속도 높인다" → "속도 높인다는 소식", "한시 인하" → "한시 인하 소식". */
    private fun news(headline: String) = if (headline.endsWith("다")) "${headline}는 소식" else "$headline 소식"

    /** "고양시 덕양구에서는 … 점검 소식이 있었습니다." for the closing summary. */
    private fun highlight(place: String, release: PressRelease) = "${place}에서는 ${news(headline(release))}이 있었습니다."

    /** The title rewritten for speech, without date or final period. */
    fun headline(release: PressRelease): String =
        symbols(release.title.replace(Regex("^\\[[^\\]]*]\\s*"), "")).trimEnd(',', '.', '!', ' ')

    /** Symbols and brackets a TTS engine reads badly, rewritten as words or dropped. */
    private fun symbols(text: String): String = text
        .replace("%p", "퍼센트포인트")
        .replace("%", "퍼센트")
        .replace(Regex("\\s*[→~∼]\\s*"), "에서 ")
        .replace("↑", " 상승").replace("↓", " 하락")
        .replace("社", "사")
        .replace("㎡", "제곱미터")
        .replace(Regex("…|‥|···|·{2,}"), ", ")
        .replace(Regex("[·‧･․ㆍ]"), ", ")
        .replace(Regex("[「『｢《【‘“]"), " ")
        .replace(Regex("[」』｣》】’”\"'*※☞]"), "")
        .replace(Regex("(?<=\\d)\\s?(km|㎞)"), "킬로미터")
        // A comma before exactly three digits is a thousands separator ("2,150호") and stays.
        .replace(Regex("\\s*,(?!\\d{3}(?!\\d))\\s*"), ", ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Body text for speech: two-digit years spelled out ("’27년" → "2027년"), dates such as "8.13 대책"
     * read as dates, and asides in brackets dropped.
     */
    fun speakable(text: String): String = symbols(
        text
            .replace(Regex("\\([월화수목금토일]\\)"), "")
            .replace(Regex("[’‘'`](\\d{2})\\.\\s?(\\d{1,2})\\.\\s?(\\d{1,2})\\.?")) {
                "20${it.groupValues[1]}년 ${it.groupValues[2].toInt()}월 ${it.groupValues[3].toInt()}일"
            }
            .replace(Regex("[’‘'`](\\d{2})\\.\\s?([상하])(반기)?")) { "20${it.groupValues[1]}년 ${it.groupValues[2]}반기" }
            .replace(Regex("[’‘'`](\\d{2})\\.\\s?(\\d{1,2})(?=월)")) { "20${it.groupValues[1]}년 ${it.groupValues[2].toInt()}" }
            .replace(Regex("(?<![\\d.])(\\d{1,2})\\.(\\d{1,2})\\s*[~∼]\\s*(\\d{1,2})\\.(\\d{1,2})(?![\\d.])")) { m ->
                val (a, b, c, d) = m.destructured
                "${a.toInt()}월 ${b.toInt()}일부터 ${c.toInt()}월 ${d.toInt()}일까지"
            }
            .replace(Regex("[’‘'`](\\d{2})(?=년)")) { "20${it.groupValues[1]}" }
            .replace(Regex("(?<![\\d.])(\\d{1,2})\\.(\\d{1,2})\\s*(?=대책|방안|후속|주택|부동산)")) {
                "${it.groupValues[1]}월 ${it.groupValues[2]}일 "
            }
            .replace(Regex("\\([^()]*\\)"), "")
            .replace(Regex("\\([^()]*\\)"), ""),
    ).replace(" ,", ",").replace(" .", ".")

    private val paragraph = Regex("<hp:p\\b.*?</hp:p>", RegexOption.DOT_MATCHES_ALL)
    private val textRun = Regex("<hp:t(?:\\s[^>]*)?>(.*?)</hp:t>", RegexOption.DOT_MATCHES_ALL)

    /** Text paragraphs of an hwpx section (Contents/section0.xml), in order. */
    fun paragraphs(sectionXml: String): List<String> = paragraph.findAll(sectionXml).map { p ->
        clean(textRun.findAll(p.value).joinToString("") { it.groupValues[1] })
    }.filter { it.isNotEmpty() }.toList()

    /**
     * What to read after the title: the opening sentence of the body's first paragraphs ("□"), in the
     * broadcast's 합니다 style. The subtitle points under the title are phrases, not sentences, so they are
     * left out; quotes and unfinished sentences are skipped.
     */
    fun details(paragraphs: List<String>): List<String> =
        paragraphs.filter { it.startsWith("□") }
            .mapNotNull { firstSentence(it.removePrefix("□").trim()) }
            // "…사례는 다음과 같다." introduces a table that is not read.
            .filter { s -> s.none { it in "“”\"" } && s.length <= MAX_SENTENCE && "다음과 같" !in s }
            .map { Speech.polite(speakable(it)) }
            .take(MAX_LEAD)

    /** Up to the first "…다." of [text]; null when the paragraph never finishes a sentence. */
    private fun firstSentence(text: String): String? {
        val end = Regex("다\\.(?=\\s|$)").find(text) ?: return null
        return text.substring(0, end.range.last + 1).trim()
    }

    /** Download link of a release's own document on its detail page: the hwpx named 보도자료, else the first. */
    fun documentPath(detailHtml: String): String? {
        val links = attachment.findAll(detailHtml).map { it.groupValues[1] to clean(it.groupValues[2]) }.toList()
        return (links.firstOrNull { "보도자료" in it.second } ?: links.firstOrNull())?.first
    }

    private val attachment = Regex("DownloadMltm2\\.jsp\\?FilePath=([^\"&]+?\\.hwpx)&(?:amp;)?FileName=([^\"]*)\"")

    /** Contents/section0.xml of an hwpx document. Stops reading once found; null when absent. */
    fun section(document: InputStream): String? =
        ZipInputStream(Capped(document, MAX_DOCUMENT_BYTES)).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == "Contents/section0.xml" }
                ?.let { zip.readBytes().toString(Charsets.UTF_8) }
        }

    /** Words a title may use for this district: "성남시 분당구" → 성남시, 성남, 분당구, 분당. */
    fun names(region: Region): List<String> = region.sigungu.split(" ").flatMap { part ->
        val stem = part.dropLast(1)
        if (stem.length >= 2 && part.last() in "시군구") listOf(part, stem) else listOf(part)
    }

    private fun wider(region: Region): List<String> = buildList {
        add(region.sidoShort)
        if (region.sidoShort in setOf("서울", "경기", "인천")) add("수도권")
    }

    /** Releases read together: one district's, one province's, or the nationwide stand-in. */
    private class Group(val region: Region?, val intro: String, val place: String, val releases: List<PressRelease>)

    /**
     * Route news first: releases naming a district, then ones naming its province.
     * With none, the latest nationwide releases, introduced as a stand-in.
     */
    private fun groups(releases: List<PressRelease>, regions: List<Region>): List<Group> {
        val recent = releases.sortedByDescending { it.date }
        val used = mutableSetOf<String>()
        val local = regions.mapNotNull { region ->
            val words = names(region)
            val hits = recent.filter { r -> r.id !in used && words.any { it in r.title } }.take(PER_REGION)
            if (hits.isEmpty()) return@mapNotNull null
            used += hits.map { it.id }
            Group(region, "", region.sigungu, hits)
        }
        val provinces = regions.flatMap(::wider).distinct().mapNotNull { area ->
            val hits = recent.filter { r -> r.id !in used && area in r.title }.take(PER_REGION)
            if (hits.isEmpty()) return@mapNotNull null
            used += hits.map { it.id }
            Group(null, "경로가 지나는 $area 관련 소식을 전합니다. ", area, hits)
        }
        val routeNews = (local + provinces).take(MAX_ITEMS)
        if (routeNews.isNotEmpty()) return routeNews
        val national = recent.take(NATIONAL_ITEMS)
        if (national.isEmpty()) return emptyList()
        return listOf(Group(null, "경로 지역 소식이 없어 전국 소식으로 대신 전해 드립니다. ", "전국", national))
    }

    /** The releases [news] reads for this route, so their bodies can be loaded first. */
    fun chosen(releases: List<PressRelease>, regions: List<Region>): List<PressRelease> =
        groups(releases, regions).flatMap { it.releases }

    /** [details] holds each release's body lines by id; a release without them is read by title. */
    fun news(
        releases: List<PressRelease>,
        regions: List<Region>,
        details: Map<String, List<String>> = emptyMap(),
    ): List<RegionNews> = groups(releases, regions).map { group ->
        RegionNews(
            group.region,
            group.intro + group.releases.joinToString(" 다음은 ") { spoken(it, details[it.id].orEmpty()) },
            highlight = highlight(group.place, group.releases.first()),
        )
    }

    private const val PER_REGION = 2
    private const val MAX_ITEMS = 3
    private const val NATIONAL_ITEMS = 2
    private const val MAX_LEAD = 3
    private const val MAX_SENTENCE = 220
    private const val MAX_DOCUMENT_BYTES = 20L * 1024 * 1024

    private val tag = Regex("<[^>]+>")

    private fun clean(html: String): String = tag.replace(html, " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&#38;", "&").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** Loads the last month of MOLIT press releases once and shares them across the five categories. */
class PressReleaseFeed {
    private val mutex = Mutex()
    private var loadedAt = 0L
    private var cached: List<PressRelease> = emptyList()

    suspend fun recent(today: LocalDate): List<PressRelease> = mutex.withLock {
        if (cached.isNotEmpty() && System.currentTimeMillis() - loadedAt < CACHE_MS) return cached
        cached = load(today.minusDays(WINDOW_DAYS))
        loadedAt = System.currentTimeMillis()
        cached
    }

    private val detailMutex = Mutex()
    private val detailCache = mutableMapOf<String, List<String>>()

    /** Body lines for each release by id. A body that fails to load is left empty and tried again next time. */
    suspend fun details(releases: List<PressRelease>): Map<String, List<String>> = coroutineScope {
        releases.distinctBy { it.id }.map { release -> async { release.id to detailsOf(release) } }.awaitAll().toMap()
    }

    private suspend fun detailsOf(release: PressRelease): List<String> {
        detailMutex.withLock { detailCache[release.id] }?.let { return it }
        val lines = try {
            val path = PressReleases.documentPath(httpGetWithCookies("$DETAIL?id=${release.id}")) ?: return emptyList()
            val xml = httpWithCookies("$DOWNLOAD?FilePath=${path.replace(" ", "%20")}&FileName=release.hwpx") {
                PressReleases.section(it)
            } ?: return emptyList()
            PressReleases.details(PressReleases.paragraphs(xml))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        detailMutex.withLock { detailCache[release.id] = lines }
        return lines
    }

    private suspend fun load(since: LocalDate): List<PressRelease> {
        val board = try {
            boardRows(since)
        } catch (e: IOException) {
            emptyList()
        }
        // The board layout or site may change; the RSS feed still gives the latest ten.
        val rows = board.ifEmpty { PressReleases.parseRss(httpGetWithCookies(RSS)) }
        return rows.filterNot { it.date.isBefore(since) }.distinctBy { it.id }
    }

    private suspend fun boardRows(since: LocalDate): List<PressRelease> {
        val rows = mutableListOf<PressRelease>()
        for (page in 1..MAX_PAGES) {
            val pageRows = try {
                PressReleases.parseList(httpGetWithCookies("$BOARD?lcmspage=$page"))
            } catch (e: IOException) {
                if (rows.isEmpty()) throw e else break
            }
            if (pageRows.isEmpty()) break
            rows += pageRows
            if (pageRows.last().date.isBefore(since)) break
        }
        return rows
    }

    private companion object {
        const val BOARD = "https://www.molit.go.kr/USR/NEWS/m_71/lst.jsp"
        const val RSS = "https://www.molit.go.kr/dev/board/board_rss.jsp?rss_id=NEWS"
        const val DETAIL = "https://www.molit.go.kr/USR/NEWS/m_71/dtl.jsp"
        const val DOWNLOAD = "https://www.molit.go.kr/portal/common/download/DownloadMltm2.jsp"
        const val MAX_PAGES = 10
        const val WINDOW_DAYS = 31L
        const val CACHE_MS = 30 * 60 * 1000L
    }
}

class PressReleaseProvider(override val category: Category, private val feed: PressReleaseFeed) : NewsProvider {
    override val source = PressReleases.SOURCE
    override val regional = true

    override suspend fun fetch(regions: List<Region>, today: LocalDate): List<RegionNews> {
        val mine = feed.recent(today).filter { PressReleases.classify(it) == category }
        val details = feed.details(PressReleases.chosen(mine, regions))
        return PressReleases.news(mine, regions, details)
    }
}

/** Stops a download that grows past [limit] bytes; press releases with many images can be large. */
private class Capped(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) add(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) add(it.toLong()) }

    override fun skip(n: Long): Long = super.skip(n).also(::add)

    private fun add(n: Long) {
        count += n
        if (count > limit) throw IOException("문서가 너무 큽니다")
    }
}
