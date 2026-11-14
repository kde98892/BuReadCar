package ai.bureadcar.app

import ai.bureadcar.app.briefing.BriefingPlan
import ai.bureadcar.app.briefing.PlaybackQueue
import ai.bureadcar.app.briefing.Speech
import ai.bureadcar.app.data.Scripts
import ai.bureadcar.app.data.parseRegion
import ai.bureadcar.app.data.parseRtmsItems
import ai.bureadcar.app.model.Category
import ai.bureadcar.app.model.Region
import ai.bureadcar.app.model.Segment
import ai.bureadcar.app.schedule.WeeklySchedule
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {
    private val bundang = Region("41135", "경기도", "성남시 분당구")
    private val gangnam = Region("11680", "서울특별시", "강남구")
    private val seocho = Region("11650", "서울특별시", "서초구")

    @Test fun `won reads naturally`() {
        assertEquals("23억 원", Speech.won(230_000))
        assertEquals("15억 3천만 원", Speech.won(153_000))
        assertEquals("2억 원", Speech.won(20_000))
        assertEquals("320만 원", Speech.won(320))
        assertEquals("1억 2천500만 원", Speech.won(12_500))
    }

    @Test fun `change states direction`() {
        assertEquals("0.07퍼센트 상승했습니다", Speech.change(0.0712))
        assertEquals("0.03퍼센트 하락했습니다", Speech.change(-0.03))
        assertEquals("변동이 없었습니다", Speech.change(0.001))
    }

    @Test fun `schedule refuses a duplicate in one day`() {
        val schedule = WeeklySchedule.DEFAULT
        assertNull(schedule.place(DayOfWeek.MONDAY, 1, Category.SUBSCRIPTION))
        val placed = schedule.place(DayOfWeek.MONDAY, 1, Category.RATE)!!
        assertEquals(listOf(Category.SUBSCRIPTION, Category.RATE), placed.categories(DayOfWeek.MONDAY))
    }

    @Test fun `same category is allowed on another day`() {
        val placed = WeeklySchedule.DEFAULT.place(DayOfWeek.TUESDAY, 0, Category.SUBSCRIPTION)
        assertEquals(Category.SUBSCRIPTION, placed!!.slots(DayOfWeek.TUESDAY)[0])
    }

    @Test fun `schedule survives json with an empty slot`() {
        val schedule = WeeklySchedule.DEFAULT.clear(DayOfWeek.FRIDAY, 0)
        assertEquals(schedule, WeeklySchedule.fromJson(schedule.toJson()))
    }

    @Test fun `moving a box swaps with the target`() {
        val moved = WeeklySchedule.DEFAULT.move(DayOfWeek.MONDAY, 0, DayOfWeek.TUESDAY, 0)!!
        assertEquals(Category.TRADE, moved.slots(DayOfWeek.MONDAY)[0])
        assertEquals(Category.SUBSCRIPTION, moved.slots(DayOfWeek.TUESDAY)[0])
    }

    @Test fun `moving within a day swaps slots`() {
        val moved = WeeklySchedule.DEFAULT.move(DayOfWeek.MONDAY, 0, DayOfWeek.MONDAY, 1)!!
        assertEquals(listOf(Category.POLICY, Category.SUBSCRIPTION), moved.categories(DayOfWeek.MONDAY))
    }

    @Test fun `move refuses a duplicate in the target day`() {
        val schedule = WeeklySchedule.DEFAULT.place(DayOfWeek.TUESDAY, 1, Category.SUBSCRIPTION)!!
        assertNull(schedule.move(DayOfWeek.MONDAY, 0, DayOfWeek.TUESDAY, 0))
    }

    @Test fun `weekend has no schedule`() {
        assertTrue(WeeklySchedule.DEFAULT.categories(DayOfWeek.SUNDAY).isEmpty())
    }

    @Test fun `corrupt schedule falls back to the default`() {
        assertEquals(WeeklySchedule.DEFAULT, WeeklySchedule.fromJson("not json"))
    }

    @Test fun `queue replays the interrupted sentence and inserts after the current segment`() {
        val queue = PlaybackQueue()
        queue.load(listOf(Segment("a", "첫째 문장. 둘째 문장."), Segment("b", "다음.")))
        assertEquals("첫째 문장.", queue.currentSentence)
        assertTrue(queue.advanceSentence())
        assertEquals("둘째 문장.", queue.currentSentence)
        queue.insertNext(listOf(Segment("c", "끼어든 소식.")))
        assertTrue(queue.advanceSentence())
        assertEquals("끼어든 소식.", queue.currentSentence)
        assertTrue(queue.advanceSentence())
        assertEquals("다음.", queue.currentSentence)
        assertFalse(queue.advanceSentence())
    }

    @Test fun `decimals do not split sentences`() {
        val queue = PlaybackQueue()
        queue.load(listOf(Segment("a", "연 4.88퍼센트입니다. 끝.")))
        assertEquals("연 4.88퍼센트입니다.", queue.currentSentence)
    }

    @Test fun `priority puts destination first then origin`() {
        val priority = BriefingPlan.regionPriority(listOf(bundang, seocho, gangnam))
        assertEquals(0, priority["11680"])
        assertEquals(1, priority["41135"])
        assertEquals(2, priority["11650"])
    }

    @Test fun `fit drops least important news and keeps framing`() {
        val text = "가".repeat((10 * BriefingPlan.CHARS_PER_SECOND).toInt()) // 10 seconds
        val segments = listOf(
            Segment("intro", text),
            Segment("dest", text, priority = 0),
            Segment("mid", text, priority = 2),
        )
        val kept = BriefingPlan.fit(segments, 50)
        assertEquals(listOf("intro", "dest"), kept.map { it.title })
    }

    @Test fun `no budget keeps everything`() {
        val segments = listOf(Segment("a", "x", priority = 1))
        assertEquals(segments, BriefingPlan.fit(segments, null))
    }

    @Test fun `parses a geocoder response with a combined city and district`() {
        val json = """{"response":{"status":"OK","result":[{"structure":
            {"level1":"경기도","level2":"성남시 분당구","level3":"","level4LC":"4113510300"}}]}}"""
        assertEquals(bundang, parseRegion(json))
    }

    @Test fun `geocoder miss returns null`() {
        assertNull(parseRegion("""{"response":{"status":"NOT_FOUND"}}"""))
    }

    @Test fun `parses transactions and rejects api errors`() {
        val xml = "<response><header><resultCode>000</resultCode></header><body><items>" +
            "<item><aptNm>래미안</aptNm><dealAmount> 230,000</dealAmount></item></items></body></response>"
        assertEquals("230,000", parseRtmsItems(xml).single()["dealAmount"])
        val bad = "<response><header><resultCode>30</resultCode></header></response>"
        assertTrue(runCatching { parseRtmsItems(bad) }.isFailure)
    }

    @Test fun `trade excludes cancelled deals`() {
        val rows = listOf(
            mapOf("dealAmount" to "900,000", "aptNm" to "취소단지", "umdNm" to "정자동", "cdealType" to "O", "excluUseAr" to "84"),
            mapOf("dealAmount" to "230,000", "aptNm" to "상록마을(라이프1차)", "umdNm" to "정자동", "cdealType" to "", "excluUseAr" to "128.7"),
        )
        assertEquals(
            "성남시 분당구의 9월 아파트 매매는 지금까지 1건 신고됐습니다. 계약을 해제했다는 신고도 1건 있었습니다. " +
                "가장 높은 거래는 정자동 상록마을 라이프1차 전용 128제곱미터로, 23억 원입니다.",
            Scripts.trade(bundang, 9, rows),
        )
    }

    @Test fun `rent separates jeonse from wolse`() {
        val rows = listOf(
            mapOf("deposit" to "50,000", "monthlyRent" to "0", "aptNm" to "A", "umdNm" to "정자동", "excluUseAr" to "84"),
            mapOf("deposit" to "20,000", "monthlyRent" to "320", "aptNm" to "B", "umdNm" to "운중동"),
        )
        val script = Scripts.rent(bundang, 9, rows)!!
        assertTrue(script, "전세 1건, 월세 1건" in script)
        assertTrue(script, "보증금 5억 원" in script)
    }

    @Test fun `notice matching respects districts`() {
        assertTrue(Scripts.inRegion("경기도 성남시 분당구 정자동 1", bundang))
        assertFalse(Scripts.inRegion("경기도 성남시 수정구 신흥동 1", bundang))
        assertTrue(Scripts.inRegion("경기도 성남시 금토동 일원", bundang))
        assertFalse(Scripts.inRegion("경기도 시흥시 거모지구", bundang))
    }

    private fun notice(name: String, start: String, end: String) =
        Scripts.Notice(name, name, "경기도 성남시 분당구", "경기", 100, LocalDate.parse(start), LocalDate.parse(end))

    @Test fun `notices split into this week and next week`() {
        val wednesday = LocalDate.of(2026, 11, 11)
        val (thisWeek, nextWeek) = Scripts.noticeWeeks(
            listOf(
                notice("closed", "2026-11-05", "2026-11-10"),
                notice("open", "2026-11-09", "2026-11-12"),
                notice("saturday", "2026-11-14", "2026-11-16"),
                notice("next", "2026-11-17", "2026-11-19"),
                notice("later", "2026-11-24", "2026-11-26"),
            ),
            wednesday,
        )
        assertEquals(listOf("open", "saturday"), thisWeek.map { it.name })
        assertEquals(listOf("next"), nextWeek.map { it.name })
    }

    @Test fun `notice news reads this week then next week`() {
        val text = Scripts.noticeNews(
            "성남시 분당구 소식입니다.",
            listOf(notice("A단지", "2026-11-09", "2026-11-12")),
            listOf(notice("B단지", "2026-11-17", "2026-11-19")),
        )!!
        assertTrue(text, text.indexOf("이번 주 청약 접수 소식입니다. A단지") < text.indexOf("다음 주에 접수를 시작하는 공고도 있습니다. B단지"))
        assertEquals(
            "분당구 B단지 청약 접수가 다음 주 11월 17일에 시작됩니다.",
            Scripts.noticeHighlight("분당구", emptyList(), listOf(notice("B단지", "2026-11-17", "2026-11-19"))),
        )
        assertNull(Scripts.noticeNews("x", emptyList(), emptyList()))
    }

    private fun deal(amount: Int, area: Double) =
        mapOf("dealAmount" to "%,d".format(amount), "aptNm" to "단지", "umdNm" to "정자동", "cdealType" to "", "excluUseAr" to area.toString())

    @Test fun `trade compares price per square meter with the previous month`() {
        val october = List(5) { deal(110_000, 100.0) }
        val september = List(5) { deal(100_000, 100.0) }
        val text = Scripts.trade(bundang, 10, october, september)!!
        assertTrue(text, text.endsWith("제곱미터당 평균 거래가는 9월보다 10퍼센트 상승했습니다."))
        assertEquals(
            "성남시 분당구 아파트의 10월 제곱미터당 평균 거래가는 전월보다 10퍼센트 상승했습니다.",
            Scripts.tradeHighlight(bundang, 10, october, september),
        )
    }

    @Test fun `too few deals skip the comparison`() {
        val text = Scripts.trade(bundang, 10, List(5) { deal(110_000, 100.0) }, List(2) { deal(100_000, 100.0) })!!
        assertTrue(text, "평균" !in text)
        assertTrue(Scripts.tradeHighlight(bundang, 10, List(1) { deal(110_000, 100.0) })!!.startsWith("성남시 분당구의 10월 최고 거래는"))
    }

    @Test fun `rent compares jeonse deposits with the previous month`() {
        fun jeonse(deposit: Int) = mapOf("deposit" to "%,d".format(deposit), "monthlyRent" to "0", "aptNm" to "A", "umdNm" to "정자동", "excluUseAr" to "84")
        val text = Scripts.rent(bundang, 1, List(5) { jeonse(48_000) }, List(5) { jeonse(50_000) })!!
        assertTrue(text, text.endsWith("전세 보증금은 제곱미터당 평균으로 12월보다 4퍼센트 하락했습니다."))
    }

    @Test fun `index highlight states the weekly move and streak`() {
        fun p(day: String, v: Double) = Scripts.IndexPoint(LocalDate.parse(day), v)
        assertEquals(
            "분당구 아파트값은 전주보다 0.5퍼센트 상승했습니다. 2주 연속 상승입니다.",
            Scripts.priceIndexHighlight("분당구", listOf(p("2026-10-19", 100.0), p("2026-10-26", 100.5), p("2026-11-02", 101.0025))),
        )
    }

    @Test fun `closing sums up one point per category, destination first`() {
        val segments = listOf(
            Segment("안내", "intro"),
            Segment("분양·청약", "a", priority = 1, highlight = "출발지 소식."),
            Segment("분양·청약", "b", priority = 0, highlight = "도착지 소식."),
            Segment("금리·대출", "c", highlight = "금리 소식."),
        )
        assertEquals(
            "끝으로 오늘의 핵심을 정리해 드립니다. 첫째, 분양·청약 소식입니다. 도착지 소식. " +
                "둘째, 금리·대출 소식입니다. 금리 소식. 오늘 방송은 여기까지입니다. 안전 운전하시기 바랍니다.",
            BriefingPlan.closing(segments).text,
        )
        assertEquals("오늘 방송은 여기까지입니다. 안전 운전하시기 바랍니다.", BriefingPlan.closing(listOf(Segment("안내", "x"))).text)
    }

    @Test fun `closing time is reserved before trimming news`() {
        val ten = "가".repeat((10 * BriefingPlan.CHARS_PER_SECOND).toInt()) // 10 seconds
        val segments = listOf(
            Segment("intro", ten),
            Segment("dest", ten, priority = 0, highlight = "짧음."),
            Segment("mid", ten, priority = 2, highlight = "짧음."),
        )
        val kept = BriefingPlan.withClosing(segments, 70)
        assertEquals(listOf("intro", "dest", BriefingPlan.CLOSING_TITLE), kept.map { it.title })
        assertTrue(kept.last().text, "dest 소식입니다" in kept.last().text)
    }

    @Test fun `highest rate uses first rank local rows and skips unpublished`() {
        val rows = listOf(
            mapOf("RESIDE_SENM" to "해당지역", "SUBSCRPT_RANK_CODE" to 1, "CMPET_RATE" to "2.05"),
            mapOf("RESIDE_SENM" to "기타지역", "SUBSCRPT_RANK_CODE" to 1, "CMPET_RATE" to "99"),
            mapOf("RESIDE_SENM" to "해당지역", "SUBSCRPT_RANK_CODE" to 1, "CMPET_RATE" to "-"),
        )
        assertEquals(2.05, Scripts.highestRate(rows)!!, 0.001)
    }

    private val classes = listOf(
        50077 to "경기>경부1권>성남시",
        50080 to "경기>경부1권>성남시>분당구",
        50068 to "서울>강남지역>동남권>강남구",
        50033 to "세종",
        50224 to "경북>포항시",
    )

    @Test fun `r-one class prefers the district and falls back to the city`() {
        assertEquals(50080 to "분당구", Scripts.rOneClass(bundang, classes))
        assertEquals(50068 to "강남구", Scripts.rOneClass(gangnam, classes))
        val unknownDistrict = Region("41131", "경기도", "성남시 수정구")
        assertEquals(50077 to "성남시", Scripts.rOneClass(unknownDistrict, classes))
        assertEquals(50033 to "세종", Scripts.rOneClass(Region("36110", "세종특별자치시", "세종특별자치시"), classes))
    }

    @Test fun `index compares the last two weeks and counts the streak`() {
        fun p(day: String, v: Double) = Scripts.IndexPoint(LocalDate.parse(day), v)
        val script = Scripts.priceIndex(
            "성남시 분당구",
            listOf(p("2026-11-02", 101.0), p("2026-10-19", 100.0), p("2026-10-26", 100.5)),
        )!!
        assertTrue(script, "11월 2일 주간" in script)
        assertTrue(script, "상승했습니다" in script)
        assertTrue(script, "2주 연속 상승" in script)
    }

    @Test fun `rate reports the month over month move`() {
        assertEquals(
            "주담대 금리는 9월 기준 연 4.88퍼센트로, 전월보다 0.22퍼센트포인트 올랐습니다.",
            Scripts.rate("주담대 금리는", "9월", listOf(4.66, 4.88)),
        )
        assertEquals(
            "기준금리는 10월 기준 연 3퍼센트로, 전월과 같습니다.",
            Scripts.rate("기준금리는", "10월", listOf(3.0, 3.0)),
        )
    }

    @Test fun `trade adds direct deals, the floor, the busiest complex and the 84 average`() {
        fun row(amount: Int, apt: String, area: String, floor: String, way: String = "중개거래") = mapOf(
            "dealAmount" to "%,d".format(amount), "aptNm" to apt, "umdNm" to "정자동", "cdealType" to "",
            "excluUseAr" to area, "floor" to floor, "dealingGbn" to way,
        )
        val rows = listOf(
            row(150_000, "느티마을", "84.9", "7"),
            row(152_000, "느티마을", "84.5", "12", "직거래"),
            row(148_000, "느티마을", "84.9", "3"),
            row(250_000, "파크뷰", "139.6", "21"),
        )
        val text = Scripts.trade(bundang, 10, rows)!!
        assertTrue(text, "이 가운데 중개를 거치지 않은 직거래는 1건입니다." in text)
        assertTrue(text, "가장 높은 거래는 정자동 파크뷰 전용 139제곱미터 21층으로, 25억 원입니다." in text)
        assertTrue(text, "거래가 가장 많은 단지는 정자동 느티마을로, 3건입니다." in text)
        assertTrue(text, "전용 84제곱미터 안팎 거래 3건의 평균 가격은 15억 원입니다." in text)
    }

    @Test fun `rent adds renewals and the average wolse`() {
        fun row(deposit: Int, monthly: Int, type: String = "신규", right: String = "") = mapOf(
            "deposit" to "%,d".format(deposit), "monthlyRent" to monthly.toString(), "aptNm" to "A", "umdNm" to "정자동",
            "excluUseAr" to "84", "contractType" to type, "useRRRight" to right,
        )
        val rows = listOf(
            row(60_000, 0, "갱신", "사용"), row(62_000, 0, "갱신"), row(64_000, 0),
            row(20_000, 150), row(30_000, 120), row(10_000, 180),
        )
        val text = Scripts.rent(bundang, 10, rows)!!
        assertTrue(text, "기존 계약을 갱신한 경우는 2건이고, 이 중 1건은 계약갱신요구권을 썼습니다." in text)
        assertTrue(text, "전용 84제곱미터 안팎 전세 3건의 평균 보증금은 6억 2천만 원입니다." in text)
        assertTrue(text, "월세는 평균 보증금 2억 원에 월 150만 원입니다." in text)
    }

    @Test fun `notice reads place, builder, each step, prices and move-in`() {
        val notice = Scripts.Notice(
            "1", "아르뮤제자이", "경기도 안산시 상록구 성포동 586번지 일원", "경기", 984,
            LocalDate.parse("2026-11-23"), LocalDate.parse("2026-11-25"),
            kind = "민영", builder = "지에스건설(주)",
            specialDay = LocalDate.parse("2026-11-23"), firstRankDay = LocalDate.parse("2026-11-24"),
            secondRankDay = LocalDate.parse("2026-11-25"), winnersDay = LocalDate.parse("2026-12-01"),
            moveIn = java.time.YearMonth.of(2031, 3), priceCap = true,
            models = listOf(
                Scripts.HouseModel(84.8861, 180, 110_500),
                Scripts.HouseModel(84.958, 88, 110_500),
                Scripts.HouseModel(99.9001, 180, 127_200),
            ),
        )
        assertEquals(
            "아르뮤제자이 공고입니다. 안산시 상록구 성포동에 들어서는 민영 아파트로, 모두 984세대를 공급합니다. " +
                "시공사는 지에스건설입니다. 청약 접수는 특별공급 11월 23일, 1순위 11월 24일, 2순위 11월 25일입니다. " +
                "당첨자는 12월 1일에 발표합니다. 분양가는 주택형별 최고가 기준으로 전용 84제곱미터 11억 500만 원, " +
                "99제곱미터 12억 7천200만 원입니다. 분양가상한제가 적용됩니다. 입주는 2031년 3월 예정입니다.",
            Scripts.notice(notice),
        )
        assertEquals("남광토건 등", Scripts.builderName("남광토건(주), 중흥토건(주)"))
        assertEquals("부천시 오정구 대장동", Scripts.neighborhood("경기도 부천시 오정구 대장동, 오정동, 원종동 일원"))
        assertNull(Scripts.neighborhood("경기도 성남시 분당구"))
    }

    @Test fun `competition sums applications and counts short house types`() {
        fun row(type: String, rank: Int, reside: Int, supply: Int, req: Int, rate: String) = mapOf<String, Any?>(
            "HOUSE_TY" to type, "SUBSCRPT_RANK_CODE" to rank, "RESIDE_SECD" to "0$reside",
            "RESIDE_SENM" to if (reside == 1) "해당지역" else "기타지역",
            "SUPLY_HSHLDCO" to supply, "REQ_CNT" to req.toString(), "CMPET_RATE" to rate,
        )
        val rows = listOf(
            row("059A", 1, 1, 14, 43, "3.07"), row("059A", 1, 2, 14, 4, "-"), row("059A", 2, 1, 14, 8, "-"), row("059A", 2, 2, 14, 0, "-"),
            row("084B", 1, 1, 32, 16, "(△16)"), row("084B", 1, 2, 32, 0, "(△16)"), row("084B", 2, 1, 32, 3, "(△13)"), row("084B", 2, 2, 32, 1, "(△12)"),
        )
        val result = Scripts.competitionOf(rows)!!
        assertEquals(Scripts.Competition(3.07, 75, 46, 2, 1), result)
        val notice = notice("성포자이", "2026-10-20", "2026-10-22")
        assertEquals(
            "최근 마감한 성포자이의 청약 결과입니다. 일반공급 46세대에 1순위와 2순위를 합쳐 75건이 접수됐습니다. " +
                "1순위 해당지역 최고 경쟁률은 3.07대 1입니다. 2개 주택형 가운데 1개는 신청이 공급 세대보다 적었습니다.",
            Scripts.competition(notice, result),
        )
        assertNull(Scripts.competitionOf(emptyList()))
    }

    @Test fun `index adds the four week change and the province`() {
        fun p(day: String, v: Double) = Scripts.IndexPoint(LocalDate.parse(day), v)
        val points = listOf(
            p("2026-10-05", 100.0), p("2026-10-12", 100.2), p("2026-10-19", 100.4), p("2026-10-26", 100.6), p("2026-11-02", 101.0),
        )
        val seoul = listOf(p("2026-10-26", 100.0), p("2026-11-02", 100.1))
        val text = Scripts.priceIndex("성남시 분당구", points, "경기" to seoul)!!
        assertTrue(text, "최근 4주 동안은 누적으로 1퍼센트 상승했습니다." in text)
        assertTrue(text, text.endsWith("같은 주 경기 전체는 0.1퍼센트 상승했습니다."))
        val otherWeek = listOf(p("2026-10-19", 100.0), p("2026-10-26", 100.1))
        assertTrue("전체" !in Scripts.priceIndex("성남시 분당구", points, "경기" to otherWeek)!!)
        assertEquals(50033 to "세종", Scripts.rOneProvince(Region("36110", "세종특별자치시", "세종특별자치시"), classes))
    }

    @Test fun `rate compares with a few months back`() {
        assertEquals(
            "주담대 금리는 9월 기준 연 4.88퍼센트로, 전월보다 0.22퍼센트포인트 올랐습니다. 2026년 4월보다는 0.3퍼센트포인트 낮습니다.",
            Scripts.rate("주담대 금리는", "9월", listOf(5.18, 4.9, 4.66, 4.88), "2026년 4월" to 5.18),
        )
        assertTrue(Scripts.rate("기준금리는", "10월", listOf(3.0, 3.0), "2026년 4월" to 3.0)!!.endsWith("2026년 4월과 같은 수준입니다."))
    }

    @Test fun `pauses slow the broadcast to the set pace`() {
        val sentence = "가".repeat(65) // 10 seconds at the engine's speed
        assertEquals(1111L, BriefingPlan.pauseAfter(sentence))
        assertEquals(300L, BriefingPlan.pauseAfter("이어서 전월세 시세입니다."))
        assertEquals(1200L, BriefingPlan.pauseAfter("가".repeat(300)))
        assertEquals(10 / 0.9, BriefingPlan.seconds(Segment("x", sentence)), 0.01)
    }

    @Test fun `the demo drive steps along the route about 3 km apart`() {
        val bundang = ai.bureadcar.app.model.Place("정자동", 37.3825, 127.1086)
        val seocho = ai.bureadcar.app.model.Place("서초동", 37.4979, 127.0276)
        val path = ai.bureadcar.app.route.simulatedDrive(listOf(bundang, seocho))
        assertEquals(seocho, path.last())
        assertTrue(path.size.toString(), path.size in 4..6)
        path.zipWithNext().forEach { (a, b) ->
            val gap = ai.bureadcar.app.route.distanceMeters(a, b)
            assertTrue(gap.toString(), gap in 2_000.0..4_500.0)
        }
        val far = ai.bureadcar.app.model.Place("부산", 35.1796, 129.0756)
        assertEquals(12, ai.bureadcar.app.route.simulatedDrive(listOf(bundang, far)).size)
    }
}
