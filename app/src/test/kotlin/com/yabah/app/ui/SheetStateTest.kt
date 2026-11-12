package com.yabah.app.ui

import com.yabah.app.ui.components.SheetValue
import com.yabah.app.ui.components.YabahSheetState
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetStateTest {
    private fun state() = YabahSheetState(SheetValue.Half).also {
        it.updateAnchors(mapOf(SheetValue.Expanded to 100f, SheetValue.Half to 900f, SheetValue.Collapsed to 1500f))
    }

    @Test fun dragIsClampedBetweenAnchors() {
        val s = state()
        assertEquals(900f, s.offset)
        assertEquals(-800f, s.dispatchRawDelta(-5000f))
        assertEquals(100f, s.offset)
        s.dispatchRawDelta(99999f)
        assertEquals(1500f, s.offset)
    }

    @Test fun settleTargetUsesPositionAndVelocity() {
        val s = state()
        assertEquals(SheetValue.Half, s.targetFor(950f, 0f))           // nearest anchor
        assertEquals(SheetValue.Collapsed, s.targetFor(1300f, 0f))
        assertEquals(SheetValue.Expanded, s.targetFor(850f, -2000f))   // fast fling up from just above Half
        assertEquals(SheetValue.Half, s.targetFor(1400f, -2000f))      // fling up from near Collapsed → next anchor only
        assertEquals(SheetValue.Collapsed, s.targetFor(950f, 2000f))   // fling down
    }
}
