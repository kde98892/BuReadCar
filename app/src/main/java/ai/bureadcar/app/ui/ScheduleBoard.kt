package ai.bureadcar.app.ui

import ai.bureadcar.app.model.Category
import ai.bureadcar.app.schedule.SLOTS_PER_DAY
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import kotlin.math.roundToInt

private typealias SlotId = Pair<DayOfWeek, Int>

private val DragSize = 120.dp

/** A colored program box with its icon and short name. */
@Composable
internal fun CategoryTile(category: Category, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(category.color).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(category.icon, null, Modifier.size(40.dp), tint = Color.White)
        Text(category.short, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A TV-style grid: weekdays across, 1부 and 2부 down, program boxes dragged into place. */
@Composable
internal fun ScheduleBoard(ui: RadioState, viewModel: RadioViewModel, onBack: () -> Unit) {
    val c = LocalColors.current
    val slotBounds = remember { mutableStateMapOf<SlotId, Rect>() }
    var dragging by remember { mutableStateOf<Category?>(null) }
    var source by remember { mutableStateOf<SlotId?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var message by remember { mutableStateOf<String?>(null) }

    fun begin(category: Category, from: SlotId?, at: Offset) {
        dragging = category
        source = from
        pointer = at
        message = null
    }

    fun drop() {
        val category = dragging ?: return
        val from = source
        dragging = null
        source = null
        val target = slotBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
        val placed = when {
            target == null -> {
                from?.let { viewModel.clear(it.first, it.second) }
                true
            }
            from != null -> viewModel.move(from.first, from.second, target.first, target.second)
            else -> viewModel.place(target.first, target.second, category)
        }
        if (!placed) message = "같은 요일에 같은 소식은 넣을 수 없어요"
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", 72.dp, c.surface, c.ink, onClick = onBack)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("이번 주 편성표", color = c.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(message ?: "박스를 끌어서 놓으세요. 빼려면 칸 밖으로 끌어내세요", color = if (message != null) c.accent else c.sub, fontSize = 20.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.width(52.dp))
                weekdays.forEach { day ->
                    Text(
                        dayLabel(day),
                        Modifier.weight(1f).padding(vertical = 2.dp),
                        color = if (day == ui.today) c.accent else c.ink,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            repeat(SLOTS_PER_DAY) { slot ->
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${slot + 1}부", Modifier.width(52.dp), color = c.sub, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    weekdays.forEach { day ->
                        val id = day to slot
                        Slot(
                            category = ui.schedule.slots(day)[slot],
                            highlighted = dragging != null && slotBounds[id]?.contains(pointer) == true,
                            hidden = source == id,
                            modifier = Modifier.weight(1f).fillMaxHeight().onGloballyPositioned {
                                slotBounds[id] = it.boundsInRoot()
                            },
                            onDragStart = { category, at -> begin(category, id, at) },
                            onMove = { pointer += it },
                            onDrop = ::drop,
                            onCancel = {
                                dragging = null
                                source = null
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            Category.entries.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { category ->
                        var tileOrigin by remember { mutableStateOf(Offset.Zero) }
                        CategoryTile(
                            category,
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { tileOrigin = it.positionInRoot() }
                                .pointerInput(category) {
                                    detectDragGestures(
                                        onDragStart = { begin(category, null, tileOrigin + it) },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            pointer += delta
                                        },
                                        onDragEnd = ::drop,
                                        onDragCancel = {
                                            dragging = null
                                            source = null
                                        },
                                    )
                                },
                        )
                    }
                }
            }
        }

        dragging?.let { category ->
            val half = with(LocalDensity.current) { (DragSize / 2).roundToPx() }
            CategoryTile(
                category,
                Modifier
                    .offset { IntOffset((pointer.x - origin.x).roundToInt() - half, (pointer.y - origin.y).roundToInt() - half) }
                    .size(DragSize),
            )
        }
    }
}

@Composable
private fun Slot(
    category: Category?,
    highlighted: Boolean,
    hidden: Boolean,
    modifier: Modifier,
    onDragStart: (Category, Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = LocalColors.current
    var slotOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { slotOrigin = it.positionInRoot() }
            .clip(RoundedCornerShape(18.dp))
            .background(c.raised)
            .border(if (highlighted) 4.dp else 0.dp, if (highlighted) c.accent else Color.Transparent, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (category == null || hidden) {
            Icon(Icons.Rounded.Add, "비어 있음", Modifier.size(36.dp), tint = c.sub)
        } else {
            CategoryTile(
                category,
                Modifier.fillMaxSize().pointerInput(category) {
                    detectDragGestures(
                        onDragStart = { onDragStart(category, slotOrigin + it) },
                        onDrag = { change, delta ->
                            change.consume()
                            onMove(delta)
                        },
                        onDragEnd = onDrop,
                        onDragCancel = onCancel,
                    )
                },
            )
        }
    }
}
