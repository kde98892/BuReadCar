package ai.bureadcar.app.ui

import ai.bureadcar.app.model.Category
import ai.bureadcar.app.model.ListenMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

private const val CAR_SCALE = 1.5f

private enum class Screen { HOME, SCHEDULE, SETTINGS }

internal fun dayLabel(day: DayOfWeek) = when (day) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

@Composable
fun App(viewModel: RadioViewModel) {
    val ui by viewModel.radio.collectAsState()
    val player by viewModel.player.state.collectAsState()
    val ttsReady by viewModel.ttsReady.collectAsState()
    val limits by viewModel.limits.collectAsState()
    var screen by remember { mutableStateOf(Screen.HOME) }

    LaunchedEffect(limits.setupBlocked) {
        if (limits.setupBlocked) screen = Screen.HOME
    }

    // The car display reports density 1.0; sizes are scaled for arm's-length touch and reading.
    val base = LocalDensity.current
    val density = Density(base.density * if (base.density < 1.5f) CAR_SCALE else 1f, base.fontScale)
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors

    CompositionLocalProvider(LocalDensity provides density, LocalColors provides colors) {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.bg)) {
            val wide = maxWidth > maxHeight
            when {
                screen == Screen.SCHEDULE -> ScheduleBoard(ui, viewModel) { screen = Screen.HOME }
                screen == Screen.SETTINGS -> SettingsScreen(ui, viewModel) { screen = Screen.HOME }
                player.loaded && !player.finished -> NowPlaying(ui, player, limits, wide, viewModel) {
                    if (!limits.setupBlocked) screen = Screen.SCHEDULE
                }
                else -> Home(
                    ui = ui,
                    finished = player.finished,
                    ttsFailed = ttsReady == false,
                    setupBlocked = limits.setupBlocked,
                    wide = wide,
                    viewModel = viewModel,
                    openSchedule = { screen = Screen.SCHEDULE },
                    openSettings = { screen = Screen.SETTINGS },
                )
            }
            if (!ui.consent) ConsentDialog(viewModel::grantConsent)
        }
    }
}

@Composable
private fun Home(
    ui: RadioState,
    finished: Boolean,
    ttsFailed: Boolean,
    setupBlocked: Boolean,
    wide: Boolean,
    viewModel: RadioViewModel,
    openSchedule: () -> Unit,
    openSettings: () -> Unit,
) {
    val c = LocalColors.current
    val today = ui.schedule.slots(ui.today)
    val onAir = ui.schedule.categories(ui.today)
    val mode = if (ui.mode == ListenMode.ON_ROUTE) "내 경로 지역의 소식" else "저장된 경로의 소식"

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${dayLabel(ui.today)}요일 부동산 라디오", color = c.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(mode, color = c.sub, fontSize = 20.sp)
            }
            CircleIconButton(Icons.Rounded.CalendarViewWeek, "편성표", 72.dp, c.surface, c.ink, enabled = !setupBlocked, onClick = openSchedule)
            Spacer(Modifier.width(12.dp))
            CircleIconButton(Icons.Rounded.Settings, "설정", 72.dp, c.surface, c.ink, enabled = !setupBlocked, onClick = openSettings)
        }

        val notice = when {
            ttsFailed -> "음성 엔진을 시작하지 못했어요"
            ui.status != null && !ui.preparing -> ui.status
            finished -> "오늘 방송을 모두 들었어요"
            else -> null
        }
        notice?.let { Text(it, color = c.accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }

        if (onAir.isEmpty()) {
            Text("오늘은 쉬는 날이에요. 듣고 싶은 소식을 골라 보세요.", color = c.ink, fontSize = 24.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Category.entries.chunked(5).forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { category ->
                            CategoryTile(category, Modifier.weight(1f).fillMaxHeight().clickable(enabled = !ui.preparing) { viewModel.start(category) })
                        }
                    }
                }
            }
        } else {
            val cards: @Composable (Modifier) -> Unit = { modifier ->
                today.forEachIndexed { index, category ->
                    if (category != null) ProgramCard(index + 1, category, modifier)
                }
            }
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    cards(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    cards(Modifier.weight(1f).fillMaxWidth())
                }
            }
            ListenBar(ui.preparing, enabled = ui.consent && !ui.preparing) { viewModel.start() }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WeekStrip(ui, Modifier.weight(1f).clickable(enabled = !setupBlocked, onClick = openSchedule))
            Box(
                Modifier
                    .height(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .clickable(enabled = ui.consent && !ui.preparing) { viewModel.startDemo() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("시연 주행", color = c.sub, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProgramCard(part: Int, category: Category, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(category.color).padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${part}부", Modifier.weight(1f), color = Color.White.copy(alpha = 0.9f), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Icon(category.icon, null, Modifier.size(64.dp), tint = Color.White)
        }
        Column {
            Text(category.label, color = Color.White, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!category.live) Text("예시 데이터", color = Color.White.copy(alpha = 0.9f), fontSize = 20.sp)
        }
    }
}

@Composable
private fun ListenBar(preparing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(c.ink)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!preparing) Icon(Icons.Rounded.PlayArrow, null, Modifier.size(52.dp), tint = c.onInk)
        Spacer(Modifier.width(8.dp))
        Text(if (preparing) "경로의 소식을 모으는 중이에요" else "오늘 방송 듣기", color = c.onInk, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeekStrip(ui: RadioState, modifier: Modifier) {
    val c = LocalColors.current
    Row(modifier.height(72.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        weekdays.forEach { day ->
            val isToday = day == ui.today
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(dayLabel(day), color = if (isToday) c.accent else c.sub, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                ui.schedule.slots(day).forEach { category ->
                    Box(Modifier.size(16.dp).clip(CircleShape).background(category?.color ?: c.line))
                }
            }
        }
    }
}

@Composable
private fun ConsentDialog(onAgree: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            // Swallows touches so nothing behind the dialog can be pressed.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 640.dp).padding(24.dp).clip(RoundedCornerShape(28.dp)).background(c.surface).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("내 경로의 소식을 들으려면", color = c.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(
                "차량 위치를 V-World로 보내 지금 어느 시군구인지 확인해요. 결과는 저장하지 않고 주행 기록도 남기지 않아요.",
                color = c.sub,
                fontSize = 22.sp,
                lineHeight = 32.sp,
            )
            Box(
                Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(44.dp)).background(c.ink).clickable(onClick = onAgree),
                contentAlignment = Alignment.Center,
            ) {
                Text("동의하고 시작", color = c.onInk, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

internal val weekdays get() = ai.bureadcar.app.schedule.WEEKDAYS
