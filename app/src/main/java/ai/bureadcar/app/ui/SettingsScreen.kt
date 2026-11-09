package ai.bureadcar.app.ui

import ai.bureadcar.app.model.ListenMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsScreen(ui: RadioState, viewModel: RadioViewModel, onBack: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", 72.dp, c.surface, c.ink, onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Text("설정", color = c.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }

        SectionLabel("어디의 소식을 들을까요")
        ChoiceRow(Icons.Rounded.Route, "내 경로 지역", "내비 안내 중인 경로의 소식", ui.mode == ListenMode.ON_ROUTE) {
            viewModel.setMode(ListenMode.ON_ROUTE)
        }
        ChoiceRow(Icons.Rounded.Bookmark, "저장된 경로", "집과 회사처럼 정해 둔 경로의 소식", ui.mode == ListenMode.SAVED_ROUTE) {
            viewModel.setMode(ListenMode.SAVED_ROUTE)
        }
        if (ui.mode == ListenMode.SAVED_ROUTE) {
            ui.routes.forEach { route ->
                ChoiceRow(null, route.name, null, route.id == ui.selectedRouteId) { viewModel.selectRoute(route.id) }
            }
        }

        SectionLabel("언제 시작할까요")
        ChoiceRow(null, "내비 안내가 시작되면 자동으로", null, ui.autoStart) { viewModel.setAutoStart(true) }
        ChoiceRow(null, "직접 눌러서", null, !ui.autoStart) { viewModel.setAutoStart(false) }

        Text("참고용 정보이며 투자 권유가 아닙니다.", color = c.sub, fontSize = 20.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(top = 10.dp), color = LocalColors.current.sub, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ChoiceRow(icon: ImageVector?, title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(if (selected) 3.dp else 0.dp, if (selected) c.ink else c.surface, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(40.dp), tint = c.ink) }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            detail?.let { Text(it, color = c.sub, fontSize = 20.sp) }
        }
        if (selected) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "선택됨", Modifier.size(26.dp), tint = c.onInk)
            }
        }
    }
}
