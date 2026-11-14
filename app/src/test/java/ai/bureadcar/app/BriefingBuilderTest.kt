package ai.bureadcar.app

import ai.bureadcar.app.briefing.BriefingPlan
import ai.bureadcar.app.briefing.Speech
import ai.bureadcar.app.data.BriefingBuilder
import ai.bureadcar.app.data.NewsProvider
import ai.bureadcar.app.data.RegionNews
import ai.bureadcar.app.model.Category
import ai.bureadcar.app.model.Region
import ai.bureadcar.app.model.RouteBasis
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingBuilderTest {
    private val bundang = Region("41135", "경기도", "성남시 분당구")
    private val gangnam = Region("11680", "서울특별시", "강남구")
    private val today = LocalDate.of(2026, 11, 16)

    private class Fake(
        override val category: Category,
        private val news: (List<Region>) -> List<RegionNews>,
    ) : NewsProvider {
        override val source = "${category.short} 출처"
        override val regional = true
        override suspend fun fetch(regions: List<Region>, today: LocalDate) = news(regions)
    }

    private fun build(vararg providers: Fake, basis: RouteBasis? = null) = runBlocking {
        val regions = listOf(bundang, gangnam)
        BriefingBuilder(providers.associateBy { it.category })
            .build(providers.map { it.category }, regions, BriefingPlan.regionPriority(regions), today, "월요일", basis)
    }

    @Test fun `each district reads both programs before the next district`() {
        val segments = build(
            Fake(Category.SUBSCRIPTION) { regions -> regions.map { RegionNews(it, "${it.sigungu} 분양.") } },
            Fake(Category.UNSOLD) { regions -> regions.map { RegionNews(it, "${it.sigungu} 미분양.") } },
        )
        val texts = segments.map { it.text }
        assertEquals("부.읽.카입니다. 참고용 정보이며 투자 권유가 아닙니다. 오늘은 월요일 분양·청약과 미분양·입주물량 소식입니다.", texts[0])
        assertEquals(
            listOf(
                "성남시 분당구 소식입니다. 먼저 분양·청약입니다. 성남시 분당구 분양.",
                "이어서 미분양·입주물량입니다. 성남시 분당구 미분양.",
                "강남구 소식입니다. 먼저 분양·청약입니다. 강남구 분양.",
                "이어서 미분양·입주물량입니다. 강남구 미분양.",
            ),
            texts.subList(1, 5),
        )
        assertEquals("출처는 분양 출처, 미분양 출처입니다.", texts.last())
    }

    @Test fun `news without a district follows the route and empty programs are noted`() {
        val segments = build(
            Fake(Category.RATE) { listOf(RegionNews(null, "금리 소식.")) },
            Fake(Category.TRANSPORT) { emptyList() },
        )
        val texts = segments.map { it.text }
        assertTrue(texts.toString(), "금리·대출 소식입니다. 금리 소식." in texts)
        assertTrue(texts.toString(), "교통 호재는 이번 주 새 소식이 없습니다." in texts)
        assertEquals("출처는 금리 출처입니다.", texts.last())
    }

    @Test fun `one program in a district gets a single opening line`() {
        val segments = build(
            Fake(Category.SUBSCRIPTION) { listOf(RegionNews(gangnam, "강남 공고.")) },
            Fake(Category.TRADE) { throw IOException("down") },
        )
        val texts = segments.map { it.text }
        assertTrue(texts.toString(), "강남구 분양·청약 소식입니다. 강남 공고." in texts)
        assertTrue(texts.toString(), "실거래가·신고가는 소식을 불러오지 못해 건너뜁니다." in texts)
    }

    @Test fun `trimming keeps a district's opening line`() {
        val ten = "가".repeat((10 * BriefingPlan.CHARS_PER_SECOND).toInt()) // 10 seconds
        val segments = listOf(
            ai.bureadcar.app.model.Segment("분양", "분당 소식입니다. 먼저 $ten", priority = 1),
            ai.bureadcar.app.model.Segment("미분양", "이어서 $ten", priority = 1),
        )
        assertEquals(listOf("분양"), BriefingPlan.fit(segments, 50).map { it.title })
    }

    @Test fun `particles follow the final consonant`() {
        assertEquals("분양·청약은", Speech.topic("분양·청약"))
        assertEquals("전월세 시세는", Speech.topic("전월세 시세"))
        assertEquals("실거래가·신고가와 금리·대출", Speech.list(listOf("실거래가·신고가", "금리·대출")))
        assertEquals("금리·대출과 정책·규제", Speech.list(listOf("금리·대출", "정책·규제")))
        assertEquals("매화마을공무원2로", Speech.ro("매화마을공무원2"))
        assertEquals("현대3으로", Speech.ro("현대3"))
        assertEquals("12층으로", Speech.ro("12층"))
        assertEquals("은마로", Speech.ro("은마"))
        assertEquals("서울로", Speech.ro("서울"))
    }

    @Test fun `written endings become the formal style`() {
        val cases = mapOf(
            "사업수수료율을 한시 인하한다." to "사업수수료율을 한시 인하합니다.",
            "브리핑을 개최하였다." to "브리핑을 개최하였습니다.",
            "약 130억원 줄어든다." to "약 130억원 줄어듭니다.",
            "시급한 지역이다." to "시급한 지역입니다.",
            "확대되고 있다." to "확대되고 있습니다.",
            "수수료로 받아 왔다." to "수수료로 받아 왔습니다.",
            "사업시행자를 맡는다." to "사업시행자를 맡습니다.",
            "도심 접근성이 뛰어나다." to "도심 접근성이 뛰어납니다.",
            "협력이 필요하다." to "협력이 필요합니다.",
            "물량이 많다." to "물량이 많습니다.",
            "기간이 길다." to "기간이 깁니다.",
            "이미 공고입니다." to "이미 공고입니다.",
            "대상이 아니다." to "대상이 아닙니다.",
            "추진하고 있습니다." to "추진하고 있습니다.",
        )
        cases.forEach { (from, to) -> assertEquals(to, Speech.polite(from)) }
    }

    @Test fun `the opening says which route the districts come from`() {
        val segments = build(Fake(Category.RATE) { listOf(RegionNews(null, "금리 소식.")) }, basis = RouteBasis.Navigation)
        assertEquals(
            "부.읽.카입니다. 참고용 정보이며 투자 권유가 아닙니다. 오늘은 월요일 금리·대출 소식입니다. " +
                "내비게이션 경로를 따라 성남시 분당구와 강남구 순서로 전해 드립니다. " +
                "가는 길에 다른 지역에 들어서면 그 지역 소식도 이어서 전해 드립니다.",
            segments.first().text,
        )
    }

    @Test fun `route lines name the basis and the districts in order`() {
        val two = listOf(bundang, gangnam)
        assertEquals(
            "저장된 집에서 회사까지 경로를 따라 성남시 분당구와 강남구 순서로 전해 드립니다.",
            BriefingBuilder.routeLine(RouteBasis.Saved("집 → 회사"), two),
        )
        assertTrue(
            BriefingBuilder.routeLine(RouteBasis.Saved("집 → 회사", followsDrive = true), two)
                .endsWith("가는 길에 다른 지역에 들어서면 그 지역 소식도 이어서 전해 드립니다."),
        )
        assertEquals(
            "저장된 주말 본가 경로에 있는 강남구 소식을 전해 드립니다.",
            BriefingBuilder.routeLine(RouteBasis.Saved("주말 본가"), listOf(gangnam)),
        )
        assertTrue(
            BriefingBuilder.routeLine(RouteBasis.CurrentLocation, listOf(bundang))
                .startsWith("내비게이션에 목적지가 없어 지금 계신 성남시 분당구 소식을 전해 드립니다."),
        )
        val many = listOf(
            bundang, Region("41131", "경기도", "성남시 수정구"), gangnam,
            Region("11650", "서울특별시", "서초구"), Region("11560", "서울특별시", "영등포구"),
        )
        assertTrue(
            BriefingBuilder.routeLine(RouteBasis.Demo, many)
                .startsWith("시연 주행 경로를 따라 성남시 분당구, 성남시 수정구, 강남구 등 5개 지역을 차례로 전해 드립니다."),
        )
    }
}
