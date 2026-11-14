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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek

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

/** Sizes are Pleos px at 160dpi, which is 1dp on the Connect display, so no extra scaling. */
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

    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    CompositionLocalProvider(LocalColors provides colors) {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.surfaceLow)) {
            val wide = maxWidth > maxHeight
            when {
                screen == Screen.SCHEDULE -> ScheduleBoard(ui, viewModel, wide) { screen = Screen.HOME }
                screen == Screen.SETTINGS -> SettingsScreen(ui, viewModel, wide) { screen = Screen.HOME }
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
            if (!ui.consent) {
                SystemPopup(
                    title = "내 경로의 소식을 들으려면",
                    description = "차량 위치를 V-World로 보내 지금 어느 시군구인지 확인해요. 결과는 저장하지 않고 주행 기록도 남기지 않아요.",
                    confirm = "동의하고 시작",
                    onConfirm = viewModel::grantConsent,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    val weekend = onAir.isEmpty()
    val mode = if (ui.mode == ListenMode.ON_ROUTE) "내 경로 지역의 소식" else "저장된 경로의 소식"

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageNavigation(
                title = "${dayLabel(ui.today)}요일 부동산 라디오",
                subtitle = if (weekend) "오늘은 쉬는 날이에요" else mode,
            ) {
                PleosIconButton(PleosIcons.schedule, "편성표", openSchedule, enabled = !setupBlocked)
                PleosIconButton(PleosIcons.settings, "설정", openSettings, enabled = !setupBlocked)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                if (weekend) {
                    Text("듣고 싶은 소식을 골라 보세요", style = PleosType.TitleS, color = c.ink)
                    val perRow = if (wide) 5 else 2
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Category.entries.chunked(perRow).forEach { row ->
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                row.forEach { category ->
                                    CategoryTile(
                                        category,
                                        Modifier.weight(1f).fillMaxHeight().clickable(enabled = ui.consent && !ui.preparing) { viewModel.start(category) },
                                        label = category.label,
                                    )
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
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            cards(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            cards(Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                    if (ui.regions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("오늘 경로", Modifier.align(Alignment.CenterVertically), style = PleosType.LabelMStrong, color = c.faint)
                            ui.regions.map(::shortName).distinct().forEach { RegionChip(it) }
                        }
                    }
                    PleosButton(
                        text = if (ui.preparing) "경로의 소식을 모으는 중이에요" else "오늘 방송 듣기",
                        onClick = { viewModel.start() },
                        modifier = Modifier.fillMaxWidth(),
                        hierarchy = Hierarchy.Secondary,
                        icon = PleosIcons.play,
                        enabled = ui.consent,
                        loading = ui.preparing,
                    )
                }
                WeekStrip(ui, wide, Modifier.clickable(remember { MutableInteractionSource() }, null, enabled = !setupBlocked, onClick = openSchedule)) {
                    PleosTextButton("시연 주행", { viewModel.startDemo() }, enabled = ui.consent && !ui.preparing)
                }
            }
        }

        val notice = when {
            ttsFailed -> "음성 엔진을 시작하지 못했어요"
            ui.status != null && !ui.preparing -> ui.status
            finished -> "오늘 방송을 모두 들었어요"
            setupBlocked && ui.consent -> "주행 중에는 편성표와 설정을 열 수 없어요"
            else -> null
        }
        notice?.let { PleosToast(it, if (setupBlocked && it.startsWith("주행")) PleosIcons.lock else PleosIcons.info) }
    }
}

@Composable
private fun WeekStrip(ui: RadioState, wide: Boolean, modifier: Modifier, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 4.dp)) {
            weekdays.forEach { day ->
                WeekDay(
                    day = dayLabel(day),
                    programs = ui.schedule.slots(day).filterNotNull().joinToString(" · ") { it.short },
                    today = day == ui.today,
                    modifier = if (wide) Modifier.width(200.dp) else Modifier.weight(1f),
                )
            }
        }
        if (wide) {
            Spacer(Modifier.padding(8.dp))
            trailing()
        }
    }
}

internal val weekdays get() = ai.bureadcar.app.schedule.WEEKDAYS
