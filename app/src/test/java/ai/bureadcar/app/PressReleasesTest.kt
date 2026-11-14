package ai.bureadcar.app

import ai.bureadcar.app.data.PressRelease
import ai.bureadcar.app.data.PressReleases
import ai.bureadcar.app.model.Category
import ai.bureadcar.app.model.Region
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PressReleasesTest {
    private val goyang = Region("41281", "경기도", "고양시 덕양구")
    private val bundang = Region("41135", "경기도", "성남시 분당구")
    private val gangnam = Region("11680", "서울특별시", "강남구")

    private fun release(title: String, field: String = "", day: String = "2026-11-12", id: String = title) =
        PressRelease(id, title, field, LocalDate.parse(day))

    @Test fun `parses the board list`() {
        val html = """
            <tr>
                <td class="bd_num">826</td>
                    <td class="bd_title">
                        <a href="dtl.jsp?lcmspage=1&amp;id=95092593" class="">
                            김용석 대광위원장,고양창릉 광역교통개선대책 추진상황 점검
                        </a>
                    </td>
                    <td class="bd_field">교통물류</td>
                <td class="bd_date">2026-11-12</td>
                <td class="bd_inquiry">1037</td>
            </tr>
            <tr>
                <td class="bd_num">825</td>
                    <td class="bd_title">
                        <a href="dtl.jsp?lcmspage=1&amp;id=95092589" class="">공공재개발 사업수수료 3%→1% 한시 인하</a>
                    </td>
                    <td class="bd_field">주택토지</td>
                <td class="bd_date">2026-11-12</td>
            </tr>
        """.trimIndent()
        val rows = PressReleases.parseList(html)
        assertEquals(2, rows.size)
        assertEquals("95092593", rows[0].id)
        assertEquals("김용석 대광위원장,고양창릉 광역교통개선대책 추진상황 점검", rows[0].title)
        assertEquals("교통물류", rows[0].field)
        assertEquals(LocalDate.parse("2026-11-12"), rows[1].date)
    }

    @Test fun `parses the rss fallback`() {
        val xml = """
            <item>
                <category><![CDATA[보도자료]]></category>
                <title><![CDATA[[참고] 백령도 소형공항 건설사업,  타당성재조사 통과]]></title>
                <link>https://www.molit.go.kr/USR/NEWS/m_71/dtl.jsp?id=95092600&#38;src=text&#38;kw=000004</link>
                <pubDate>Fri, 13 Nov 2026 16:34:04 +0900</pubDate>
                <dc:date>2026-11-13 16:34:04</dc:date>
            </item>
        """.trimIndent()
        val rows = PressReleases.parseRss(xml)
        assertEquals(1, rows.size)
        assertEquals("95092600", rows[0].id)
        assertEquals("[참고] 백령도 소형공항 건설사업, 타당성재조사 통과", rows[0].title)
    }

    @Test fun `classifies real titles`() {
        fun cat(title: String, field: String = "") = PressReleases.classify(release(title, field))
        assertEquals(Category.TRANSPORT, cat("김용석 대광위원장,고양창릉 광역교통개선대책 추진상황 점검"))
        assertEquals(Category.TRANSPORT, cat("3기 신도시 교통대책 ‘직접 인·허가’로 속도 높인다"))
        assertEquals(Category.REDEVELOPMENT, cat("공공재개발 사업수수료 3%→1% 한시 인하"))
        assertEquals(Category.UNSOLD, cat("서울 가격상승폭 둔화·매물 증가…수도권 내년 1분기까지 4만 호 이상 입주"))
        assertEquals(Category.UNSOLD, cat("‘26년 9월 주택통계"))
        assertEquals(Category.DEVELOPMENT, cat("고흥 ‘우주발사체’ 국가산업단지 조성 속도"))
        assertEquals(Category.POLICY, cat("전세사기피해자등 480건 추가 결정"))
        assertEquals(Category.POLICY, cat("연체 걱정 없이 세 놓으세요. 전월세 안심신탁 첫 임대인 모집 공고", "주택토지"))
    }

    @Test fun `skips schedules ceremonies and off-topic posts`() {
        assertNull(PressReleases.classify(release("[장관동정] 홍지선 장관, “8·13 대책 신속 추진, 서울 연내 3.1만호 착공 지원”")))
        assertNull(PressReleases.classify(release("「제11회 부동산산업의 날」 “국민 신뢰와 산업 혁신 다짐”")))
        assertNull(PressReleases.classify(release("대학생이 만든 자율주행차, 실험실을 벗어나 도로 위를 달린다")))
        assertNull(PressReleases.classify(release("철도안전법 위반 철도운영기관 및 철도차량 정비조직 등에 과징금 부과 의결")))
    }

    @Test fun `titles are rewritten for speech`() {
        val text = PressReleases.spoken(release("[참고] 공공재개발 사업수수료 3%→1% 한시 인하"))
        assertEquals("11월 12일, 공공재개발 사업수수료 3퍼센트에서 1퍼센트 한시 인하 소식입니다.", text)
        val dots = PressReleases.spoken(release("서울 가격상승폭 둔화·매물 증가…수도권 4만 호 이상 입주"))
        assertTrue(dots, "둔화, 매물 증가, 수도권" in dots)
        val verb = PressReleases.spoken(release("3기 신도시 교통대책 직접 인허가로 속도 높인다"))
        assertEquals("11월 12일, 3기 신도시 교통대책 직접 인허가로 속도 높인다는 소식입니다.", verb)
    }

    @Test fun `district names include the city and stems`() {
        assertEquals(listOf("고양시", "고양", "덕양구", "덕양"), PressReleases.names(goyang))
        assertEquals(listOf("강남구", "강남"), PressReleases.names(gangnam))
        assertEquals(listOf("중구"), PressReleases.names(Region("11140", "서울특별시", "중구")))
    }

    @Test fun `route districts come first`() {
        val news = PressReleases.news(
            listOf(release("김용석 대광위원장,고양창릉 광역교통개선대책 추진상황 점검"), release("서울-양평 고속도로 신규 타당성조사 착수")),
            listOf(bundang, goyang),
        )
        assertEquals(1, news.size)
        assertEquals(goyang, news[0].region)
        assertTrue(news[0].text, news[0].text.startsWith("11월 12일, 김용석"))
        assertEquals("고양시 덕양구에서는 김용석 대광위원장, 고양창릉 광역교통개선대책 추진상황 점검 소식이 있었습니다.", news[0].highlight)
    }

    @Test fun `province mentions count as route news`() {
        val news = PressReleases.news(listOf(release("서울-양평 고속도로 신규 타당성조사 착수")), listOf(gangnam))
        assertEquals(1, news.size)
        assertNull(news[0].region)
        assertTrue(news[0].text, news[0].text.startsWith("경로가 지나는 서울 관련 소식을 전합니다."))
    }

    @Test fun `no route news falls back to nationwide and says so`() {
        val news = PressReleases.news(
            listOf(
                release("대구경북통합신공항 민간공항 기본계획 변경 고시", day = "2026-10-30", id = "1"),
                release("새로운 대한민국의 내일을 잇는 철도 대전환, 국민과 함께 그립니다", day = "2026-11-08", id = "2"),
                release("고속철도 통합 60일, 하루 32.2만 명 이용", day = "2026-11-05", id = "3"),
            ),
            listOf(bundang),
        )
        assertEquals(1, news.size)
        assertNull(news[0].region)
        val text = news[0].text
        assertTrue(text, text.startsWith("경로 지역 소식이 없어 전국 소식으로 대신 전해 드립니다. 11월 8일,"))
        assertTrue(text, "11월 5일" in text)
        assertTrue(text, "신공항" !in text)
    }

    @Test fun `nothing at all stays empty`() {
        assertTrue(PressReleases.news(emptyList(), listOf(bundang)).isEmpty())
    }

    private val body = listOf(
        "보도자료",
        "보도시점 : 2026. 11. 12.(목) 11:00 이후(11. 13.(금) 조간) / 배포 : 2026. 11. 12.(목)",
        "<주택 신속공급 방안(8.13) 후속조치>",
        "공공재개발 사업수수료 3%→1% 한시 인하",
        "- '27년 말까지 한국토지주택공사(LH) 신규 후보지 신청 사업에 적용",
        "-",
        "- 구역당 평균 약 130억원 절감, 수수료의 최대 15% 국비 지원은 유지",
        "□ 국토교통부(장관 홍지선)와 한국토지주택공사(사장 이성훈, 이하 LH)는 ’27년 12월 31일까지 공공재개발 후보지 선정을 새로 신청하는 구역에 대해 사업수수료율을 총 사업비의 3%에서 1%로 한시 인하한다.",
        "ㅇ 이번 조치는 ‘주택 신속공급 방안(’26.8.13)’에서 발표한 공공재개발 활성화 방안의 하나다.",
        "□ 홍 장관은 “공공재개발이 빨라질 것”이라고 말했다.",
        "□ 국토교통부는 ｢전세사기피해지원위원회｣ 전체회의 3회(116~118회,",
        "□ 이번 인하로 공공재개발 수수료 부담은 구역당 평균 약 130억원 줄어든다. 예시는 다음과 같다.",
        "□ 적발된 주요 사례는 다음과 같다.",
        "□ 아울러 수수료의 최대 15%를 국비로 지원하는 기존 제도는 계속 적용된다.",
    )

    @Test fun `body details are opening sentences in the formal style`() {
        assertEquals(
            listOf(
                "국토교통부와 한국토지주택공사는 2027년 12월 31일까지 공공재개발 후보지 선정을 새로 신청하는 구역에 대해 " +
                    "사업수수료율을 총 사업비의 3퍼센트에서 1퍼센트로 한시 인하합니다.",
                "이번 인하로 공공재개발 수수료 부담은 구역당 평균 약 130억원 줄어듭니다.",
                "아울러 수수료의 최대 15퍼센트를 국비로 지원하는 기존 제도는 계속 적용됩니다.",
            ),
            PressReleases.details(body),
        )
        assertTrue(PressReleases.details(listOf("제목", "- 부제")).isEmpty())
    }

    @Test fun `body text is rewritten for speech`() {
        assertEquals("2026년 8월 13일 대책에 따라", PressReleases.speakable("’26.8.13 대책에 따라"))
        assertEquals("8월 13일 주택 신속공급 대책", PressReleases.speakable("8.13 주택 신속공급 대책"))
        assertEquals("지난 8월 13일 대책을 통해", PressReleases.speakable("지난 8.13대책을 통해"))
        assertEquals("기본계획 수립 등", PressReleases.speakable("기본계획 수립(’27.상) 등"))
        assertEquals("2027년 상반기 착수", PressReleases.speakable("’27.상 착수"))
        assertEquals("최대 2퍼센트포인트, 화정동에서 도내동", PressReleases.speakable("최대 2%p, 화정동~도내동"))
        assertEquals("경쟁률 3.07대 1", PressReleases.speakable("경쟁률 3.07대 1"))
        assertEquals("2025년 3월에서 2026년 3월 직거래", PressReleases.speakable("’25.3월~’26.3월 직거래"))
        assertEquals("10월 19일부터 10월 30일까지, 11월 9일부터 11월 20일까지 점검", PressReleases.speakable("10.19(월)~10.30(금), 11.9~11.20 점검"))
        assertEquals("도심 속 2,150호, 10.4만호", PressReleases.speakable("도심 속 2,150호, 10.4만호"))
        assertEquals("브리핑룸에서 제3차 정례브리핑을 서울 5킬로미터", PressReleases.speakable("브리핑룸에서「제3차 정례브리핑」을 서울 5km"))
    }

    @Test fun `reads paragraphs from hwpx section xml`() {
        val xml = "<hs:sec><hp:p id=\"1\"><hp:run><hp:t>공공재개발 </hp:t></hp:run><hp:run><hp:t>사업수수료</hp:t></hp:run></hp:p>" +
            "<hp:p><hp:run><hp:t>&lt;후속조치&gt;</hp:t></hp:run></hp:p><hp:p><hp:run><hp:pic/></hp:run></hp:p></hs:sec>"
        assertEquals(listOf("공공재개발 사업수수료", "<후속조치>"), PressReleases.paragraphs(xml))
        val bytes = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("BinData/image1.png"))
                zip.write(ByteArray(1000))
                zip.putNextEntry(java.util.zip.ZipEntry("Contents/section0.xml"))
                zip.write(xml.toByteArray())
            }
        }.toByteArray()
        assertEquals(xml, PressReleases.section(bytes.inputStream()))
    }

    @Test fun `picks the press release document among attachments`() {
        val html = """
            <a href="/portal/common/download/DownloadMltm2.jsp?FilePath=/upload/portal/DextUpload/202611/b.hwpx&amp;FileName=별첨1(브리핑문).hwpx">
            <a href="/portal/common/download/DownloadMltm2.jsp?FilePath=/upload/portal/DextUpload/202611/a.hwpx&amp;FileName=(보도자료)_제3차.hwpx">
            <a href="/portal/common/download/DownloadMltm2.jsp?FilePath=/upload/portal/DextUpload/202611/c.pdf&amp;FileName=인포그래픽.pdf">
        """.trimIndent()
        assertEquals("/upload/portal/DextUpload/202611/a.hwpx", PressReleases.documentPath(html))
        assertNull(PressReleases.documentPath("<a href=\"x.pdf\">"))
    }

    @Test fun `details follow each title and releases are joined`() {
        val first = release("고양창릉 광역교통개선대책 점검", day = "2026-11-12", id = "1")
        val second = release("고양 은평선 광역철도 착공", day = "2026-11-10", id = "2")
        val news = PressReleases.news(listOf(first, second), listOf(goyang), mapOf("1" to listOf("세부 내용입니다.")))
        assertEquals(
            "11월 12일, 고양창릉 광역교통개선대책 점검 소식입니다. 세부 내용입니다. 다음은 11월 10일, 고양 은평선 광역철도 착공 소식입니다.",
            news.single().text,
        )
        assertEquals(listOf(first, second), PressReleases.chosen(listOf(first, second), listOf(goyang)))
    }
}
