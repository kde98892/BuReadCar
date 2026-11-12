package ai.bureadcar.app.ui

import ai.bureadcar.app.model.Category
import ai.bureadcar.app.player.PlayerState
import ai.bureadcar.app.route.UxLimits
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Soft = Color.White.copy(alpha = 0.82f)
private val Faint = Color.White.copy(alpha = 0.28f)
private val Veil = Color.White.copy(alpha = 0.18f)

/** The whole window takes the program's color, so the topic reads at a glance. */
@Composable
internal fun NowPlaying(
    ui: RadioState,
    player: PlayerState,
    limits: UxLimits,
    wide: Boolean,
    viewModel: RadioViewModel,
    openSchedule: () -> Unit,
) {
    val category = Category.entries.firstOrNull { it.label == player.title }
    val stage = category?.color ?: LocalColors.current.ink
    val onChange = { viewModel.player.toggle() }

    Column(Modifier.fillMaxSize().background(stage).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ON AIR", Modifier.weight(1f), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!limits.setupBlocked) {
                CircleIconButton(Icons.Rounded.CalendarViewWeek, "편성표", 72.dp, Veil, Color.White, onClick = openSchedule)
                Spacer(Modifier.width(12.dp))
            }
            CircleIconButton(Icons.Rounded.Close, "끝내기", 72.dp, Veil, Color.White) { viewModel.stop() }
        }

        player.message?.let {
            Text(it, color = Color.White, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        val headline: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                category?.let { Icon(it.icon, null, Modifier.size(88.dp), tint = Color.White) }
                Text(
                    player.title.ifEmpty { "브리핑" },
                    color = Color.White,
                    fontSize = 46.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                player.region?.let { Text("지금 $it 소식", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold) }
                if (player.isSample) {
                    Text(
                        "예시 데이터",
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Veil).padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                // Reading is by ear; the script appears only where the platform allows text.
                if (!limits.limitText) {
                    Text(player.text, color = Soft, fontSize = 22.sp, lineHeight = 32.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (wide) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                headline(Modifier.weight(1.4f).fillMaxHeight())
                RouteTimeline(ui.regions, player.region, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            headline(Modifier.weight(1f).fillMaxWidth())
            RouteLine(ui.regions, player.region)
        }

        Progress(player.position, player.total)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.Rounded.Replay, "처음부터", 92.dp, Veil, Color.White) { viewModel.player.restart() }
            CircleIconButton(
                if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (player.playing) "일시정지" else "재생",
                128.dp,
                Color.White,
                stage,
                onClick = onChange,
            )
            CircleIconButton(Icons.Rounded.SkipNext, "다음", 92.dp, Veil, Color.White) { viewModel.player.next() }
        }
    }
}

@Composable
private fun Progress(position: Int, total: Int) {
    Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total.coerceAtLeast(1)) { index ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .background(if (index <= position) Color.White else Faint),
            )
        }
    }
}

/** Districts in route order, with the one being read picked out. */
@Composable
private fun RouteTimeline(regions: List<String>, current: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text("내 경로", color = Soft, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        val visible = regions.map(::shortName).distinct().take(MAX_STOPS)
        visible.forEachIndexed { index, name ->
            val active = name == current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(if (active) 28.dp else 16.dp).clip(CircleShape).background(if (active) Color.White else Faint))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    name,
                    color = if (active) Color.White else Soft,
                    fontSize = if (active) 34.sp else 26.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
            if (index < visible.lastIndex) {
                Box(Modifier.width(36.dp).height(26.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Faint))
                }
            }
        }
    }
}

@Composable
private fun RouteLine(regions: List<String>, current: String?) {
    val line = buildAnnotatedString {
        regions.map(::shortName).distinct().forEachIndexed { index, name ->
            if (index > 0) append("  ›  ")
            if (name == current) {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(name) }
            } else {
                append(name)
            }
        }
    }
    Text(line, color = Soft, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

internal fun shortName(sigungu: String) = sigungu.substringAfterLast(" ")

private const val MAX_STOPS = 6
