package com.yabah.app.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.yabah.app.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SheetValue { Collapsed, Half, Expanded }

/**
 * State of the three-anchor trip panel. [offset] is the distance (px) from the top of the container to the top of
 * the sheet; smaller offset = taller sheet. Dragging writes [offset] directly; releasing settles to an anchor using
 * position and fling velocity.
 */
@Stable
class YabahSheetState(initialValue: SheetValue) {
    var currentValue by mutableStateOf(initialValue)
        private set
    var offset by mutableFloatStateOf(Float.NaN)
        private set
    private var anchors: Map<SheetValue, Float> = emptyMap()
    private val mutex = MutatorMutex()
    private var dragging = false

    val isReady: Boolean get() = !offset.isNaN()
    private val minOffset get() = anchors.values.minOrNull() ?: 0f
    private val maxOffset get() = anchors.values.maxOrNull() ?: 0f

    fun anchorOf(value: SheetValue): Float? = anchors[value]

    internal fun updateAnchors(new: Map<SheetValue, Float>) {
        if (new == anchors) return
        anchors = new
        if (!dragging) new[currentValue]?.let { offset = it }
    }

    /** Applies a drag delta; returns the amount actually consumed. */
    fun dispatchRawDelta(delta: Float): Float {
        if (offset.isNaN()) return 0f
        val target = (offset + delta).coerceIn(minOffset, maxOffset)
        val consumed = target - offset
        offset = target
        return consumed
    }

    internal suspend fun startDrag() {
        mutex.mutate(MutatePriority.UserInput) { dragging = true }
    }

    suspend fun settle(velocity: Float) {
        dragging = false
        animateTo(targetFor(offset, velocity), velocity)
    }

    suspend fun animateTo(value: SheetValue, velocity: Float = 0f) {
        val to = anchors[value] ?: run { currentValue = value; return }
        mutex.mutate {
            if (!offset.isNaN() && offset != to) {
                animate(offset, to, velocity, spring(dampingRatio = 0.92f, stiffness = 420f)) { v, _ -> offset = v }
            }
            offset = to
            currentValue = value
        }
    }

    internal fun targetFor(position: Float, velocity: Float, velocityThreshold: Float = 900f): SheetValue {
        val sorted = anchors.entries.sortedBy { it.value } // Expanded (top) first
        if (sorted.isEmpty()) return currentValue
        return when {
            velocity < -velocityThreshold -> (sorted.lastOrNull { it.value < position - 1f } ?: sorted.first()).key
            velocity > velocityThreshold -> (sorted.firstOrNull { it.value > position + 1f } ?: sorted.last()).key
            else -> sorted.minBy { abs(it.value - position) }.key
        }
    }

    internal val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            // Finger moving up: grow the sheet before the content scrolls.
            return if (delta < 0 && source == NestedScrollSource.UserInput) Offset(0f, dispatchRawDelta(delta)) else Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            // Content is at its top and the finger keeps moving down: shrink the sheet.
            if (source == NestedScrollSource.UserInput) Offset(0f, dispatchRawDelta(available.y)) else Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity {
            val v = available.y
            return if (v < 0 && offset > minOffset) { settle(v); available } else Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(available.y)
            return available
        }
    }

    companion object {
        val Saver = Saver<YabahSheetState, String>(save = { it.currentValue.name }, restore = { YabahSheetState(SheetValue.valueOf(it)) })
    }
}

@Composable
fun rememberYabahSheetState(initial: SheetValue = SheetValue.Half): YabahSheetState =
    rememberSaveable(saver = YabahSheetState.Saver) { YabahSheetState(initial) }

/**
 * Map-behind-sheet layout. [background] receives the visible sheet height so the map can pad its camera.
 */
@Composable
fun YabahBottomSheetLayout(
    state: YabahSheetState,
    peekHeight: Dp,
    modifier: Modifier = Modifier,
    halfFraction: Float = 0.52f,
    expandedTopInset: Dp = 72.dp,
    background: @Composable BoxWithConstraintsScope.(visibleSheetHeight: Dp) -> Unit,
    sheetContent: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val full = constraints.maxHeight.toFloat()
        val topPx = with(density) { expandedTopInset.toPx() }
        val peekPx = with(density) { peekHeight.toPx() }.coerceAtMost(full - topPx)
        val collapsed = full - peekPx
        val half = (full * (1f - halfFraction)).coerceIn(topPx, collapsed)
        SideEffect { state.updateAnchors(mapOf(SheetValue.Expanded to topPx, SheetValue.Half to half, SheetValue.Collapsed to collapsed)) }

        val offset = if (state.isReady) state.offset else (state.anchorOf(state.currentValue) ?: half)
        val visiblePx = (full - offset).coerceAtLeast(0f)
        background(with(density) { visiblePx.toDp() })

        val handleDesc = stringResource(R.string.sheet_handle_desc)
        val expandLabel = stringResource(R.string.sheet_expand)
        val collapseLabel = stringResource(R.string.sheet_collapse)
        val dragState = rememberDraggableState { delta -> state.dispatchRawDelta(delta) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { (full - topPx).toDp() })
                .offset { IntOffset(0, offset.roundToInt()) }
                .testTag("trip_sheet")
                .semantics { stateDescription = state.currentValue.name }
                .nestedScroll(state.nestedScrollConnection)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { state.startDrag() },
                    onDragStopped = { velocity -> state.settle(velocity) },
                ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                // The grabber is a real control: drag anywhere on the panel, or tap/activate it to cycle sizes.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sheet_handle")
                        .clickable(role = Role.Button) {
                            scope.launch {
                                state.animateTo(
                                    when (state.currentValue) {
                                        SheetValue.Collapsed -> SheetValue.Half
                                        SheetValue.Half -> SheetValue.Expanded
                                        SheetValue.Expanded -> SheetValue.Collapsed
                                    },
                                )
                            }
                        }
                        .semantics {
                            contentDescription = handleDesc
                            stateDescription = state.currentValue.name
                            customActions = listOf(
                                CustomAccessibilityAction(expandLabel) { scope.launch { state.animateTo(SheetValue.Expanded) }; true },
                                CustomAccessibilityAction(collapseLabel) { scope.launch { state.animateTo(SheetValue.Collapsed) }; true },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.outline))
                }
                sheetContent()
            }
        }
    }
}
