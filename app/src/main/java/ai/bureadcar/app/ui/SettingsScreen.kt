package ai.bureadcar.app.ui

import ai.bureadcar.app.model.ListenMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsScreen(ui: RadioState, viewModel: RadioViewModel, wide: Boolean, onBack: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxSize()) {
        PageNavigation("설정", onBack = onBack)
        val where: @Composable ColumnScope.() -> Unit = {
            SectionLabel("어디의 소식을 들을까요")
            ListRow(
                "내 경로 지역",
                { viewModel.setMode(ListenMode.ON_ROUTE) },
                icon = PleosIcons.route,
                description = "내비 안내 중인 경로의 소식",
            ) { PleosRadio(ui.mode == ListenMode.ON_ROUTE) }
            ListRow(
                "저장된 경로",
                { viewModel.setMode(ListenMode.SAVED_ROUTE) },
                icon = PleosIcons.bookmark,
                description = "집과 회사처럼 정해 둔 경로의 소식",
            ) { PleosRadio(ui.mode == ListenMode.SAVED_ROUTE) }
            if (ui.mode == ListenMode.SAVED_ROUTE && ui.routes.isNotEmpty()) {
                RouteDropdown(ui, viewModel)
            }
        }
        val whenAndAbout: @Composable ColumnScope.() -> Unit = {
            SectionLabel("언제 시작할까요")
            ListRow(
                "안내가 시작되면 자동으로 듣기",
                { viewModel.setAutoStart(!ui.autoStart) },
                icon = PleosIcons.play,
                description = "내비 길 안내를 시작하면 바로 틀어요",
            ) { PleosSwitch(ui.autoStart) }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceBasic).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("데이터 출처", style = PleosType.BodyLStrong, color = c.ink)
                listOf(
                    "국토교통부 실거래가 · 전월세 (공공데이터포털)",
                    "한국부동산원 R-ONE 주택가격지수",
                    "한국은행 ECOS 금리 · 청약홈 분양정보",
                ).forEach { Text(it, style = PleosType.LabelM, color = c.sub) }
                Text("참고용 정보이며 투자 권유가 아닙니다.", style = PleosType.LabelMStrong, color = c.faint)
            }
        }
        val body = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 48.dp)
        if (wide) {
            Row(body, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = where)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = whenAndAbout)
            }
        } else {
            Column(body, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                where()
                whenAndAbout()
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(top = 8.dp), style = PleosType.BodyMStrong, color = LocalColors.current.faint)
}

/** Pleos dropdown (Medium, 100 high) that opens its list in place. */
@Composable
private fun RouteDropdown(ui: RadioState, viewModel: RadioViewModel) {
    val c = LocalColors.current
    var open by remember { mutableStateOf(false) }
    val selected = ui.routes.firstOrNull { it.id == ui.selectedRouteId } ?: ui.routes.first()
    val shape = RoundedCornerShape(24.dp)
    Pressable(
        onClick = { open = !open },
        modifier = Modifier.fillMaxWidth().height(100.dp).border(2.dp, if (open) c.fieldFocused else c.line, shape),
        shape = shape,
        background = { if (it) c.dropdownPressed else c.dropdown },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(PleosIcons.bookmark, null, Modifier.size(40.dp), tint = c.ink)
            Text(selected.name, Modifier.weight(1f), style = PleosType.BodyLStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(PleosIcons.chevronDown, null, Modifier.size(40.dp).rotate(if (open) 180f else 0f), tint = c.ink)
        }
    }
    if (open) {
        val menu = RoundedCornerShape(32.dp)
        Column(Modifier.fillMaxWidth().elevation(2, menu).clip(menu).background(c.popup).padding(16.dp)) {
            ui.routes.forEach { route ->
                val isSelected = route.id == selected.id
                Pressable(
                    onClick = {
                        viewModel.selectRoute(route.id)
                        open = false
                    },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    background = { if (it) c.basicEnabled else Color.Transparent },
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(PleosIcons.route, null, Modifier.size(48.dp), tint = c.ink)
                        Text(route.name, Modifier.weight(1f), style = if (isSelected) PleosType.BodyMStrong else PleosType.BodyM, color = c.ink)
                        if (isSelected) Icon(PleosIcons.check, "선택됨", Modifier.size(40.dp), tint = c.positive)
                    }
                }
            }
        }
    }
}
