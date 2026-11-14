package ai.bureadcar.app.ui

import ai.bureadcar.app.R
import ai.bureadcar.app.model.Category
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Pleos Connect design tokens, mirrored from the Figma file (Pleos · Color Light / Dark, Pleos · Dimension).
 * Names follow the system tokens: text_dark_* is ink on light surfaces, text_light_* is ink on filled surfaces.
 * Values are Pleos px at 160dpi, which equal dp on the car display.
 */

private val GRAY = longArrayOf(
    0xFFFFFFFF, 0xFFF7F8FA, 0xFFEDEEF2, 0xFFE0E1E6, 0xFFC7C9CE, 0xFFA4A8AE,
    0xFF686A72, 0xFF44464E, 0xFF313236, 0xFF1D1E21, 0xFF131417,
)
private val INK = Color(0xFF131417)
private val WHITE = Color.White
private val STATIC_ALPHA = floatArrayOf(1f, 0.84f, 0.64f, 0.32f)

data class PleosColors(
    val dark: Boolean,
    val background: Color,
    val surfaceLow: Color,
    val surfaceBasic: Color,
    val surfaceHigh: Color,
    val popup: Color,
    val basicEnabled: Color,
    val basicPressed: Color,
    val basicDisabled: Color,
    val filledEnabled: Color,
    val filledPressed: Color,
    val filledDisabled: Color,
    val switchEnabled: Color,
    val switchPressed: Color,
    val tabBackground: Color,
    val dropdown: Color,
    val dropdownPressed: Color,
    val fieldFocused: Color,
    val line: Color,
    val lineStrong: Color,
    /** text_dark_primary … quaternary (also icon_dark_*). */
    val text: List<Color>,
    /** text_light_primary … quaternary: ink on filled surfaces. */
    val onFilled: List<Color>,
    val active: Color,
    val positive: Color,
    val negative: Color,
    val switchOn: Color,
    val switchOff: Color,
    val switchDisabled: Color,
    val knobOn: Color,
    val knobOff: Color,
    val overlay: Color,
    val radio: Color,
    val toast: Color,
    val onToast: Color,
) {
    val ink get() = text[0]
    val sub get() = text[1]
    val faint get() = text[2]
    val off get() = text[3]
}

/** basic_00, 50, 100 … 900 map to steps 0…10 and flip between light and dark. */
fun pleosColors(dark: Boolean): PleosColors {
    fun basic(step: Int) = Color(GRAY[if (dark) 10 - step else step])
    fun alphaDark(a: Float) = (if (dark) WHITE else INK).copy(alpha = a)
    fun alphaLight(a: Float) = (if (dark) INK else WHITE).copy(alpha = a)
    val staticDark = STATIC_ALPHA.map { INK.copy(alpha = it) }
    val staticLight = STATIC_ALPHA.map { WHITE.copy(alpha = it) }
    val active = if (dark) Color(0xFF00E074) else Color(0xFF02C265)
    return PleosColors(
        dark = dark,
        background = basic(1),
        surfaceLow = if (dark) basic(0) else basic(1),
        surfaceBasic = if (dark) basic(1) else basic(0),
        surfaceHigh = basic(2),
        popup = if (dark) basic(2) else basic(0),
        basicEnabled = alphaDark(0.05f),
        basicPressed = alphaDark(0.10f),
        basicDisabled = alphaLight(0.20f),
        filledEnabled = basic(7),
        filledPressed = if (dark) basic(6) else basic(8),
        filledDisabled = alphaDark(0.05f),
        switchEnabled = if (dark) alphaDark(0.20f) else basic(0),
        switchPressed = if (dark) alphaDark(0.20f) else alphaLight(0.50f),
        tabBackground = alphaDark(0.05f),
        dropdown = if (dark) alphaDark(0.10f) else basic(0),
        dropdownPressed = if (dark) alphaDark(0.30f) else basic(2),
        fieldFocused = basic(8),
        line = alphaDark(0.10f),
        lineStrong = alphaDark(0.20f),
        text = if (dark) staticLight else staticDark,
        onFilled = if (dark) staticDark else staticLight,
        active = active,
        positive = if (dark) Color(0xFF0082FF) else Color(0xFF0064FF),
        negative = if (dark) Color(0xFFFF4C28) else Color(0xFFFE3D16),
        switchOn = active,
        switchOff = alphaDark(if (dark) 0.10f else 0.30f),
        switchDisabled = alphaDark(0.10f),
        knobOn = WHITE,
        knobOff = if (dark) basic(5) else basic(0),
        overlay = if (dark) INK.copy(alpha = 0.5f) else INK.copy(alpha = 0.2f),
        radio = Color(0xFFC911E7),
        toast = staticDark[1],
        onToast = WHITE,
    )
}

val LightColors = pleosColors(dark = false)
val DarkColors = pleosColors(dark = true)
val LocalColors = staticCompositionLocalOf { LightColors }

@OptIn(ExperimentalTextApi::class)
private fun asta(weight: FontWeight) =
    Font(R.font.asta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Asta Sans, the Pleos system typeface (variable font, SIL OFL 1.1). */
val AstaSans = FontFamily(
    asta(FontWeight.Normal),
    asta(FontWeight.SemiBold),
    asta(FontWeight.Bold),
    asta(FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = AstaSans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/** The 25 Pleos text styles (Strong = SemiBold, Normal = Regular). */
object PleosType {
    val HeadlineL = style(60, 80, FontWeight.Bold)
    val HeadlineM = style(56, 72, FontWeight.Bold)
    val HeadlineS = style(48, 64, FontWeight.Bold)
    val TitleL = style(40, 52, FontWeight.Bold)
    val TitleM = style(36, 44, FontWeight.Bold)
    val TitleS = style(32, 40, FontWeight.Bold)
    val BodyLStrong = style(30, 38, FontWeight.SemiBold)
    val BodyL = style(30, 38, FontWeight.Normal)
    val BodyMStrong = style(28, 36, FontWeight.SemiBold)
    val BodyM = style(28, 36, FontWeight.Normal)
    val BodySStrong = style(26, 34, FontWeight.SemiBold)
    val BodyS = style(26, 34, FontWeight.Normal)
    val LabelMStrong = style(24, 28, FontWeight.SemiBold)
    val LabelM = style(24, 28, FontWeight.Normal)
    val LabelSStrong = style(20, 24, FontWeight.SemiBold)
    val LabelS = style(20, 24, FontWeight.Normal)
}

/** Categories are told apart by shape and name, not color (Pleos: avoid excessive color). */
val Category.icon: ImageVector
    get() = when (this) {
        Category.SUBSCRIPTION -> PleosIcons.catSubscription
        Category.PRICE_INDEX -> PleosIcons.catPriceIndex
        Category.TRADE -> PleosIcons.catTrade
        Category.RENT -> PleosIcons.catRent
        Category.TRANSPORT -> PleosIcons.catTransport
        Category.DEVELOPMENT -> PleosIcons.catDevelopment
        Category.REDEVELOPMENT -> PleosIcons.catRedevelopment
        Category.POLICY -> PleosIcons.catPolicy
        Category.RATE -> PleosIcons.catRate
        Category.UNSOLD -> PleosIcons.catUnsold
    }

/** One-line description used on program cards. */
val Category.blurb: String
    get() = when (this) {
        Category.SUBSCRIPTION -> "청약 일정과 경쟁률"
        Category.PRICE_INDEX -> "주간 매매가격지수 변화"
        Category.TRADE -> "이번 달 실거래와 신고가"
        Category.RENT -> "전세·월세 거래 흐름"
        Category.TRANSPORT -> "철도·도로 사업 소식"
        Category.DEVELOPMENT -> "신도시·산업단지 소식"
        Category.REDEVELOPMENT -> "정비사업 진행 단계"
        Category.POLICY -> "대출·세금·규제 지역"
        Category.RATE -> "기준금리와 주택담보대출 금리"
        Category.UNSOLD -> "미분양과 입주 예정 물량"
    }
