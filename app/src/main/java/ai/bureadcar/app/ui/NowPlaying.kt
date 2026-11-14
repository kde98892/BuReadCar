package ai.bureadcar.app.ui

import ai.bureadcar.app.model.Category
import ai.bureadcar.app.player.PlayerState
import ai.bureadcar.app.route.UxLimits
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Monochrome player; the only accent is the radio color on what is being read right now. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NowPlaying(
    ui: RadioState,
    player: PlayerState,
    limits: UxLimits,
    wide: Boolean,
    viewModel: RadioViewModel,
    openSchedule: () -> Unit,
) {
    val c = LocalColors.current
    val category = Category.entries.firstOrNull { it.label == player.title }
    val part = category?.let { ui.schedule.categories(ui.today).indexOf(it) }?.takeIf { it >= 0 }?.let { "${it + 1}부" }
    val stops = ui.regions.map(::shortName).distinct().take(MAX_STOPS)
    val current = stops.indexOf(player.region)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageNavigation(
                title = "방송 중",
                subtitle = listOfNotNull(part, player.title.ifEmpty { "브리핑" }).joinToString(" · "),
            ) {
                PleosIconButton(PleosIcons.schedule, "편성표", openSchedule, enabled = !limits.setupBlocked)
                PleosIconButton(PleosIcons.close, "끝내기", { viewModel.stop() })
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                val headline: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Box(
                                Modifier.size(if (wide) 120.dp else 96.dp).clip(CircleShape).background(c.basicEnabled),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(category?.icon ?: PleosIcons.broadcast, null, Modifier.size(if (wide) 64.dp else 56.dp), tint = c.ink)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                part?.let { Text(it, style = PleosType.LabelMStrong, color = c.faint) }
                                Text(
                                    player.title.ifEmpty { "브리핑" },
                                    style = if (wide) PleosType.HeadlineL else PleosType.HeadlineS,
                                    color = c.ink,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        player.region?.let { region ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(c.radio))
                                Text("지금 $region 소식", style = PleosType.TitleL, color = c.ink)
                            }
                        }
                        // Reading is by ear; the script appears only where the platform allows text.
                        if (limits.limitText) {
                            Row(
                                Modifier.clip(RoundedCornerShape(16.dp)).background(c.basicEnabled).padding(horizontal = 24.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(PleosIcons.lock, null, Modifier.size(32.dp), tint = c.sub)
                                Text("주행 중에는 소리로만 들려드려요", style = PleosType.BodyMStrong, color = c.sub)
                            }
                        } else if (player.sentence.isNotEmpty()) {
                            // One sentence at a time follows the voice and stays within three lines, so nothing is cut.
                            Text(player.sentence, style = PleosType.BodyL, color = c.sub)
                        }
                        DataBadge(!player.isSample)
                    }
                }

                if (wide) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                        headline(Modifier.weight(1f).fillMaxHeight())
                        if (stops.isNotEmpty()) RouteTimeline(stops, current, Modifier.width(520.dp).fillMaxHeight())
                    }
                } else {
                    headline(Modifier.weight(1f).fillMaxWidth())
                    if (stops.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            stops.forEachIndexed { i, name ->
                                RegionChip(name, if (i == current) ChipState.Current else if (current >= 0 && i < current) ChipState.Passed else ChipState.Default)
                            }
                        }
                    }
                }

                SegmentProgress(player.position, player.total)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PleosIconButton(PleosIcons.replay, "처음부터", { viewModel.player.restart() }, style = IconStyle.Circle)
                    Pressable(
                        onClick = { viewModel.player.toggle() },
                        modifier = Modifier.size(128.dp),
                        shape = CircleShape,
                        background = { if (it) c.filledPressed else c.filledEnabled },
                        label = if (player.playing) "일시정지" else "재생",
                    ) {
                        Icon(
                            if (player.playing) PleosIcons.pause else PleosIcons.play,
                            if (player.playing) "일시정지" else "재생",
                            Modifier.size(64.dp),
                            tint = c.onFilled[0],
                        )
                    }
                    PleosIconButton(PleosIcons.skipNext, "다음", { viewModel.player.next() }, style = IconStyle.Circle)
                }
            }
        }
        player.message?.let { PleosToast(it, PleosIcons.info) }
    }
}

/** Districts in route order: passed ones dim, the one being read large with the radio dot. */
@Composable
private fun RouteTimeline(stops: List<String>, current: Int, modifier: Modifier) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(32.dp)
    Column(modifier.elevation(1, shape).clip(shape).background(c.surfaceBasic).padding(40.dp)) {
        Text("내 경로", style = PleosType.LabelMStrong, color = c.faint)
        Spacer(Modifier.height(16.dp))
        stops.forEachIndexed { i, name ->
            val state = when {
                i == current -> 1
                current >= 0 && i < current -> 0
                else -> 2
            }
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.width(40.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(4.dp).weight(1f).background(if (i > 0) c.line else Color.Transparent))
                    when (state) {
                        1 -> Box(Modifier.size(28.dp).clip(CircleShape).background(c.radio))
                        0 -> Box(Modifier.size(16.dp).clip(CircleShape).background(c.faint))
                        else -> Box(Modifier.size(16.dp).clip(CircleShape).border(3.dp, c.off, CircleShape))
                    }
                    Box(Modifier.width(4.dp).weight(1f).background(if (i < stops.lastIndex) c.line else Color.Transparent))
                }
                Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        name,
                        style = when (state) {
                            1 -> PleosType.TitleL
                            0 -> PleosType.BodyM
                            else -> PleosType.BodyL
                        },
                        color = when (state) {
                            1 -> c.ink
                            0 -> c.faint
                            else -> c.sub
                        },
                        maxLines = 1,
                    )
                    if (state == 1) Text("지금 듣는 중", style = PleosType.LabelMStrong, color = c.radio)
                }
            }
        }
    }
}

internal fun shortName(sigungu: String) = sigungu.substringAfterLast(" ")

private const val MAX_STOPS = 6
