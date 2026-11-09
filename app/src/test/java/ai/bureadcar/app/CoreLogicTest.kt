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
        assertEquals(Category.PRICE_INDEX, moved.slots(DayOfWeek.MONDAY)[0])
        assertEquals(Category.SUBSCRIPTION, moved.slots(DayOfWeek.TUESDAY)[0])
    }

    @Test fun `moving within a day swaps slots`() {
        val moved = WeeklySchedule.DEFAULT.move(DayOfWeek.MONDAY, 0, DayOfWeek.MONDAY, 1)!!
        assertEquals(listOf(Category.UNSOLD, Category.SUBSCRIPTION), moved.categories(DayOfWeek.MONDAY))
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
        val text = "가".repeat(65) // 10 seconds
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
            "성남시 분당구의 9월 아파트 매매는 지금까지 1건 신고됐습니다. " +
                "이 중 가장 높은 거래는 정자동 상록마을 라이프1차 전용 128제곱미터로, 23억 원입니다.",
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

    @Test fun `closed notices are not read as open`() {
        val today = LocalDate.of(2026, 11, 9)
        fun notice(end: String) = Scripts.Notice("1", "n", "a", "경기", 10, LocalDate.parse(end), LocalDate.parse(end))
        assertEquals(1, Scripts.openNotices(listOf(notice("2026-11-08"), notice("2026-11-09")), today).size)
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
}
