package ai.bureadcar.app.ui

import ai.bureadcar.app.R
import ai.bureadcar.app.model.Category
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pleos shadow levels: 1 cards, 2 menus, 3 popups and dragged items. */
fun Modifier.elevation(level: Int, shape: Shape): Modifier = when (level) {
    1 -> shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
    2 -> shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.16f))
    else -> shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.24f), spotColor = Color.Black.copy(alpha = 0.24f))
}

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 2.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 8.dp.toPx()))),
    )
}

/**
 * Every touchable element shrinks to 95% while pressed (Pleos button interaction) and swaps its
 * background through [background]; moving off the element cancels the click.
 */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(24.dp),
    background: (pressed: Boolean) -> Color = { Color.Transparent },
    label: String? = null,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.95f else 1f, label = "press")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(background(pressed && enabled))
            .clickable(interaction, indication = null, enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(pressed && enabled) }
}

enum class Hierarchy { Primary, Secondary }

enum class ButtonSize(val height: Dp, val radius: Dp, val padding: Dp, val gap: Dp, val icon: Dp) {
    Large(100.dp, 32.dp, 40.dp, 12.dp, 40.dp),
    Medium(80.dp, 24.dp, 32.dp, 8.dp, 36.dp),
    Small(64.dp, 16.dp, 24.dp, 8.dp, 28.dp),
    ;

    val text: TextStyle
        get() = when (this) {
            Large -> PleosType.BodyMStrong
            Medium -> PleosType.BodySStrong
            Small -> PleosType.LabelMStrong
        }
}

/** Pleos Basic button. Primary = basic (5% fill), Secondary = filled. */
@Composable
fun PleosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hierarchy: Hierarchy = Hierarchy.Primary,
    size: ButtonSize = ButtonSize.Large,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = LocalColors.current
    val filled = hierarchy == Hierarchy.Secondary
    val ink = when {
        !enabled -> c.off
        filled -> c.onFilled[0]
        else -> c.ink
    }
    Pressable(
        onClick = onClick,
        modifier = modifier.height(size.height),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(size.radius),
        background = { pressed ->
            when {
                !enabled -> if (filled) c.filledDisabled else c.basicDisabled
                filled -> if (pressed) c.filledPressed else c.filledEnabled
                else -> if (pressed) c.basicPressed else c.basicEnabled
            }
        },
    ) {
        Row(
            Modifier.padding(horizontal = size.padding),
            horizontalArrangement = Arrangement.spacedBy(size.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(size.icon), color = ink, strokeWidth = 4.dp, trackColor = ink.copy(alpha = 0.24f))
            } else {
                icon?.let { Icon(it, null, Modifier.size(size.icon), tint = ink) }
            }
            Text(text, style = size.text, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

enum class IconStyle { Basic, Plain, Circle }

enum class IconSize(val box: Dp, val icon: Dp, val radius: Dp) {
    Large(80.dp, 48.dp, 24.dp), Medium(64.dp, 40.dp, 16.dp), Small(48.dp, 32.dp, 12.dp),
}

@Composable
fun PleosIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IconStyle = IconStyle.Basic,
    size: IconSize = IconSize.Large,
    enabled: Boolean = true,
) {
    val c = LocalColors.current
    Pressable(
        onClick = onClick,
        modifier = modifier.size(size.box),
        enabled = enabled,
        shape = if (style == IconStyle.Circle) CircleShape else RoundedCornerShape(size.radius),
        background = { pressed ->
            when {
                pressed -> c.basicPressed
                style == IconStyle.Plain -> Color.Transparent
                !enabled -> c.basicDisabled
                else -> c.basicEnabled
            }
        },
        label = description,
    ) {
        Icon(icon, description, Modifier.size(size.icon), tint = if (enabled) c.ink else c.off)
    }
}

/** Link-like button in informative_positive. */
@Composable
fun PleosTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalColors.current
    val ink = if (enabled) c.positive else c.off
    Pressable(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        background = { if (it) c.basicEnabled else Color.Transparent },
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = PleosType.BodySStrong, color = ink)
            Icon(PleosIcons.chevronRight, null, Modifier.size(32.dp), tint = ink)
        }
    }
}

/** Adaptive app icon: #131417 background with the house-and-sound foreground. */
@Composable
fun AppIcon(size: Dp = 80.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color(0xFF131417)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
    }
}

/**
 * Pleos page navigation, 128 high. Main shows the app icon; Depth shows a back button.
 * [actions] go in the suffix slot on the right.
 */
@Composable
fun PageNavigation(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().height(128.dp).padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (onBack == null) 32.dp else 24.dp),
    ) {
        if (onBack == null) AppIcon() else PleosIconButton(PleosIcons.back, "뒤로", onBack, style = IconStyle.Plain)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = PleosType.TitleM, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = PleosType.LabelM, color = c.faint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Pleos toast: 80 high, #131417 84%, at most 792 wide, 24 from the panel edge. */
@Composable
fun BoxScope.PleosToast(message: String, icon: ImageVector? = null) {
    val c = LocalColors.current
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(24.dp)
            .widthIn(max = 792.dp)
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.toast)
            .padding(horizontal = 40.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(40.dp), tint = c.onToast) }
        Text(message, Modifier.widthIn(max = 712.dp), style = PleosType.BodyM, color = c.onToast, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Pleos switch: track 80×48 radius 16, handle 32 radius 8 inset 8. */
@Composable
fun PleosSwitch(checked: Boolean, enabled: Boolean = true) {
    val c = LocalColors.current
    val x by animateDpAsState(if (checked) 40.dp else 8.dp, label = "knob")
    Box(
        Modifier.size(80.dp, 48.dp).clip(RoundedCornerShape(16.dp))
            .background(if (!enabled) c.switchDisabled else if (checked) c.switchOn else c.switchOff),
    ) {
        Box(Modifier.offset(x = x, y = 8.dp).size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (checked) c.knobOn else c.knobOff))
    }
}

/** Pleos radio: 48 circle, 4px outline at 20%, selected #02C265 with a white dot. */
@Composable
fun PleosRadio(selected: Boolean, enabled: Boolean = true) {
    val c = LocalColors.current
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .then(
                if (selected) Modifier.background(if (enabled) c.switchOn else c.switchDisabled)
                else Modifier.border(4.dp, c.lineStrong, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) 1f else 0.64f)))
    }
}

/** A settings row: leading icon, title and description, and a trailing control. */
@Composable
fun ListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    val c = LocalColors.current
    Pressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 112.dp),
        enabled = enabled,
        background = { if (it) c.surfaceHigh else c.surfaceBasic },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            icon?.let { Icon(it, null, Modifier.size(48.dp), tint = c.ink) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = PleosType.BodyLStrong, color = c.ink)
                description?.let { Text(it, style = PleosType.LabelM, color = c.faint) }
            }
            trailing()
        }
    }
}

/** Pleos system popup (Medium 808): centered text, confirm (filled) on the left. */
@Composable
fun SystemPopup(
    title: String,
    description: String,
    confirm: String,
    onConfirm: () -> Unit,
    cancel: String? = null,
    onCancel: () -> Unit = {},
) {
    PopupFrame(title, description) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            PleosButton(confirm, onConfirm, Modifier.weight(1f), Hierarchy.Secondary)
            cancel?.let { PleosButton(it, onCancel, Modifier.weight(1f)) }
        }
    }
}

/** Dimmed backdrop and the popup card with a centered title and description above [content]. */
@Composable
fun PopupFrame(
    title: String,
    description: String,
    maxWidth: Dp = 808.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalColors.current
    Box(
        Modifier.fillMaxSize().background(c.overlay)
            // Swallows touches so nothing behind the popup can be pressed.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(48.dp)
        Column(
            Modifier.padding(24.dp).widthIn(max = maxWidth).elevation(3, shape).clip(shape)
                .background(c.popup).border(2.dp, c.basicEnabled, shape).padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = PleosType.TitleM, color = c.ink, textAlign = TextAlign.Center)
                Text(description, style = PleosType.BodyL, color = c.sub, textAlign = TextAlign.Center, maxLines = 3)
            }
            content()
        }
    }
}

/** Today's program: the category's photo with its name over a fade of the card color. */
@Composable
fun ProgramCard(part: Int, category: Category, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(32.dp)
    Box(modifier.elevation(1, shape).clip(shape).background(c.surfaceBasic)) {
        // Each photo keeps its subject on the right, so cropping starts from the right edge.
        Image(
            painterResource(category.backdrop),
            null,
            Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
        )
        if (c.dark) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
        // The card color fades in toward the text so it keeps 4.5:1 contrast on any photo.
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(0f to c.surfaceBasic.copy(alpha = 0.92f), 0.6f to c.surfaceBasic.copy(alpha = 0f)),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.4f to c.surfaceBasic.copy(alpha = 0f), 1f to c.surfaceBasic.copy(alpha = 0.88f)),
            ),
        )
        Column(Modifier.matchParentSize().padding(40.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${part}부",
                Modifier.clip(RoundedCornerShape(16.dp)).background(c.surfaceBasic.copy(alpha = 0.88f)).padding(horizontal = 16.dp, vertical = 8.dp),
                style = PleosType.LabelMStrong,
                color = c.sub,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(category.label, style = PleosType.HeadlineS, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(category.blurb, style = PleosType.BodyM, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                DataBadge(live = true)
            }
        }
    }
}

@Composable
fun DataBadge(live: Boolean, source: String? = null) {
    val c = LocalColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (live) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(c.active))
        } else {
            Icon(PleosIcons.info, null, Modifier.size(28.dp), tint = c.faint)
        }
        Text(
            if (live) listOfNotNull("실시간 데이터", source).joinToString(" · ") else "예시 데이터",
            style = PleosType.LabelMStrong,
            color = if (live) c.sub else c.faint,
        )
    }
}

enum class TileState { Default, Dragging, Disabled }

/**
 * A program tile: clay art (or the icon when there is none) and its name, over the program's hue.
 * [picture] false leaves only the name, as on the schedule's small news boxes.
 */
@Composable
fun CategoryTile(
    category: Category,
    modifier: Modifier = Modifier,
    label: String = category.short,
    state: TileState = TileState.Default,
    @DrawableRes artwork: Int? = category.artwork,
    picture: Boolean = true,
) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(24.dp)
    val off = state == TileState.Disabled
    val base = when (state) {
        TileState.Dragging -> modifier.elevation(3, shape).clip(shape).background(c.surfaceBasic).border(3.dp, c.ink, shape)
        TileState.Disabled -> modifier.clip(shape).background(c.basicEnabled)
        TileState.Default -> modifier.clip(shape).background(c.surfaceBasic).border(2.dp, c.basicEnabled, shape)
    }
    // A faint wash of the program's own hue, so a box looks the same on the weekend grid, the schedule
    // and while dragged. Disabled boxes stay grey.
    val tinted = if (state != TileState.Disabled) {
        base.background(category.hue.copy(alpha = if (c.dark) TILE_WASH_DARK else TILE_WASH_LIGHT))
    } else {
        base
    }
    Column(
        tinted.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        when {
            !picture -> Unit
            // 200 dp on the big tiles; on the schedule's cells it shrinks to the room left above the label.
            artwork != null -> Image(
                painterResource(artwork),
                null,
                Modifier
                    .weight(1f, fill = false)
                    .sizeIn(maxWidth = 200.dp, maxHeight = 200.dp)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                alpha = if (off) 0.4f else 1f,
            )
            else -> Icon(category.icon, null, Modifier.size(40.dp), tint = if (off) c.off else c.ink)
        }
        Text(label, style = PleosType.LabelMStrong, color = if (off) c.off else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private const val TILE_WASH_LIGHT = 0.10f
private const val TILE_WASH_DARK = 0.16f

enum class ChipState { Default, Current, Passed }

@Composable
fun RegionChip(label: String, state: ChipState = ChipState.Default) {
    val c = LocalColors.current
    val base = Modifier.height(56.dp).clip(CircleShape)
    val styled = when (state) {
        ChipState.Default -> base.background(c.basicEnabled)
        ChipState.Current -> base.background(c.filledEnabled)
        ChipState.Passed -> base.border(2.dp, c.line, CircleShape)
    }
    Row(styled.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state == ChipState.Current) Box(Modifier.size(12.dp).clip(CircleShape).background(c.radio))
        Text(
            label,
            style = PleosType.BodySStrong,
            color = when (state) {
                ChipState.Current -> c.onFilled[0]
                ChipState.Passed -> c.faint
                ChipState.Default -> c.sub
            },
        )
    }
}

/** Broadcast progress: done segments in ink, the current one in Pleos radio color. */
@Composable
fun SegmentProgress(position: Int, total: Int, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Row(modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total.coerceAtLeast(1)) { i ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(
                    when {
                        i < position -> c.ink
                        i == position -> c.radio
                        else -> c.line
                    },
                ),
            )
        }
    }
}

@Composable
fun WeekDay(day: String, programs: String, today: Boolean, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(if (today) c.filledEnabled else Color.Transparent)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(day, style = PleosType.BodyLStrong, color = if (today) c.onFilled[0] else c.ink)
        Text(programs, style = PleosType.LabelM, color = if (today) c.onFilled[1] else c.faint, textAlign = TextAlign.Center)
    }
}
