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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private typealias SlotId = Pair<DayOfWeek, Int>

private enum class SlotState { Empty, Filled, Target, Invalid }

/** Weekdays across, 1부 and 2부 down; program boxes are dragged in, between, and out. */
@Composable
internal fun ScheduleBoard(ui: RadioState, viewModel: RadioViewModel, wide: Boolean, onBack: () -> Unit) {
    val c = LocalColors.current
    val slotBounds = remember { mutableStateMapOf<SlotId, Rect>() }
    var dragging by remember { mutableStateOf<Category?>(null) }
    var source by remember { mutableStateOf<SlotId?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_000)
            message = null
        }
    }

    /** Placing [category] at [id] would repeat it on that day. */
    fun repeats(category: Category, id: SlotId): Boolean =
        ui.schedule.slots(id.first).withIndex().any { (i, other) -> i != id.second && other == category && (id.first to i) != source }

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

    fun cancel() {
        dragging = null
        source = null
    }

    val labelWidth = if (wide) 96.dp else 64.dp
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            PageNavigation("이번 주 편성표", "박스를 끌어서 놓으세요. 빼려면 칸 밖으로 끌어내세요", onBack = onBack)
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(labelWidth))
                        weekdays.forEach { day ->
                            val today = day == ui.today
                            Box(
                                Modifier.weight(1f).height(64.dp).clip(CircleShape).background(if (today) c.filledEnabled else c.surfaceLow),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(dayLabel(day), style = PleosType.BodyLStrong, color = if (today) c.onFilled[0] else c.ink, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    repeat(SLOTS_PER_DAY) { slot ->
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${slot + 1}부", Modifier.width(labelWidth), style = PleosType.BodyMStrong, color = c.sub)
                            weekdays.forEach { day ->
                                val id = day to slot
                                val hovered = dragging != null && slotBounds[id]?.contains(pointer) == true
                                val category = ui.schedule.slots(day)[slot]
                                val state = when {
                                    hovered && repeats(dragging!!, id) -> SlotState.Invalid
                                    hovered -> SlotState.Target
                                    category == null || source == id -> SlotState.Empty
                                    else -> SlotState.Filled
                                }
                                Slot(
                                    category = category,
                                    state = state,
                                    modifier = Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { slotBounds[id] = it.boundsInRoot() },
                                    onDragStart = { cat, at -> begin(cat, id, at) },
                                    onMove = { pointer += it },
                                    onDrop = ::drop,
                                    onCancel = ::cancel,
                                )
                            }
                        }
                    }
                }

                Text("소식 박스", style = PleosType.LabelMStrong, color = c.faint)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Category.entries.chunked(if (wide) 10 else 5).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(if (wide) 112.dp else 104.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                                onDragCancel = ::cancel,
                                            )
                                        },
                                )
                            }
                        }
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
                    .size(DragSize)
                    .rotate(-4f),
                state = TileState.Dragging,
            )
        }

        message?.let { PleosToast(it, PleosIcons.info) }
    }
}

private val DragSize = 160.dp

@Composable
private fun Slot(
    category: Category?,
    state: SlotState,
    modifier: Modifier,
    onDragStart: (Category, Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = LocalColors.current
    var slotOrigin by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(24.dp)
    val framed = when (state) {
        SlotState.Empty -> modifier.dashedBorder(c.lineStrong, 24.dp)
        SlotState.Target -> modifier.clip(shape).background(c.basicEnabled).border(4.dp, c.positive, shape)
        SlotState.Invalid -> modifier.clip(shape).background(c.basicEnabled).border(4.dp, c.negative, shape)
        SlotState.Filled -> modifier
    }
    Box(framed.onGloballyPositioned { slotOrigin = it.positionInRoot() }, contentAlignment = Alignment.Center) {
        when (state) {
            SlotState.Filled -> CategoryTile(
                category!!,
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
            SlotState.Invalid -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(PleosIcons.close, null, Modifier.size(40.dp), tint = c.negative)
                Text("이미 있어요", style = PleosType.LabelMStrong, color = c.negative)
            }
            else -> Icon(PleosIcons.add, "비어 있음", Modifier.size(40.dp), tint = if (state == SlotState.Target) c.positive else c.faint)
        }
    }
}
