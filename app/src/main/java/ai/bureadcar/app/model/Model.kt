package ai.bureadcar.app.model

enum class Category(val label: String, val short: String, val live: Boolean) {
    SUBSCRIPTION("분양·청약", "분양", true),
    PRICE_INDEX("주택가격지수·증감률", "지수", true),
    TRADE("실거래가·신고가", "실거래", true),
    RENT("전월세 시세", "전월세", true),
    TRANSPORT("교통 호재", "교통", false),
    DEVELOPMENT("개발 호재", "개발", false),
    REDEVELOPMENT("재건축·재개발", "재건축", false),
    POLICY("정책·규제", "정책", false),
    RATE("금리·대출", "금리", true),
    UNSOLD("미분양·입주물량", "미분양", false),
}

enum class ListenMode { ON_ROUTE, SAVED_ROUTE }

data class Place(val name: String, val lat: Double, val lng: Double)

data class SavedRoute(val id: String, val name: String, val origin: Place, val destination: Place)

/** A si/gun/gu. [lawdCode] is the 5-digit code the transaction APIs take. */
data class Region(val lawdCode: String, val sido: String, val sigungu: String) {
    /** Name used for the subscription API's area filter. */
    val sidoShort: String get() = shortSido(sido)
}

/** One unit of playback: an intro, a region's news, or a closing line. */
data class Segment(
    val title: String,
    val text: String,
    /** Lower survives longer when the time budget is short; null is never dropped. */
    val priority: Int? = null,
    val isSample: Boolean = false,
    /** Short district name being read, e.g. "분당구". */
    val region: String? = null,
)

private val SIDO_PREFIXES = listOf(
    "전남광주" to "전남광주", "서울" to "서울", "부산" to "부산", "대구" to "대구", "인천" to "인천",
    "광주" to "광주", "대전" to "대전", "울산" to "울산", "세종" to "세종", "경기" to "경기",
    "강원" to "강원", "충청북" to "충북", "충북" to "충북", "충청남" to "충남", "충남" to "충남",
    "전라북" to "전북", "전북" to "전북", "전라남" to "전남", "전남" to "전남",
    "경상북" to "경북", "경북" to "경북", "경상남" to "경남", "경남" to "경남", "제주" to "제주",
)

fun shortSido(full: String): String =
    SIDO_PREFIXES.firstOrNull { full.startsWith(it.first) }?.second ?: full
