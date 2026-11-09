package ai.bureadcar.app.ui

import ai.bureadcar.app.model.Category
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Colors(
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val ink: Color,
    val onInk: Color,
    val sub: Color,
    val line: Color,
    val accent: Color,
)

val LightColors = Colors(
    bg = Color(0xFFEEEEEE),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFE2E4E8),
    ink = Color(0xFF131417),
    onInk = Color(0xFFFFFFFF),
    sub = Color(0xFF5B616B),
    line = Color(0xFFCBCFD6),
    accent = Color(0xFFE8710A),
)

val DarkColors = Colors(
    bg = Color(0xFF131417),
    surface = Color(0xFF22252B),
    raised = Color(0xFF30343B),
    ink = Color(0xFFF5F6F8),
    onInk = Color(0xFF131417),
    sub = Color(0xFFB0B6C0),
    line = Color(0xFF444952),
    accent = Color(0xFFFF9A3D),
)

val LocalColors = staticCompositionLocalOf { LightColors }

/** Every program color carries white text at 4.5:1 or better. */
val Category.color: Color
    get() = when (this) {
        Category.SUBSCRIPTION -> Color(0xFFC2410C)
        Category.PRICE_INDEX -> Color(0xFF1864AB)
        Category.TRADE -> Color(0xFF2B7A3B)
        Category.RENT -> Color(0xFF0B7285)
        Category.TRANSPORT -> Color(0xFF5F3DC4)
        Category.DEVELOPMENT -> Color(0xFFC2255C)
        Category.REDEVELOPMENT -> Color(0xFF862E9C)
        Category.POLICY -> Color(0xFF364FC7)
        Category.RATE -> Color(0xFFC92A2A)
        Category.UNSOLD -> Color(0xFF495057)
    }

val Category.icon: ImageVector
    get() = when (this) {
        Category.SUBSCRIPTION -> Icons.Rounded.EditNote
        Category.PRICE_INDEX -> Icons.Rounded.ShowChart
        Category.TRADE -> Icons.Rounded.Handshake
        Category.RENT -> Icons.Rounded.Key
        Category.TRANSPORT -> Icons.Rounded.Train
        Category.DEVELOPMENT -> Icons.Rounded.LocationCity
        Category.REDEVELOPMENT -> Icons.Rounded.Construction
        Category.POLICY -> Icons.Rounded.Gavel
        Category.RATE -> Icons.Rounded.Percent
        Category.UNSOLD -> Icons.Rounded.Apartment
    }

@Composable
fun CircleIconButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    background: Color,
    tint: Color,
    border: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var modifier = Modifier.size(size).clip(CircleShape).background(background)
    if (border != null) modifier = modifier.border(1.dp, border, CircleShape)
    Box(modifier.clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(size * 0.5f), tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}
