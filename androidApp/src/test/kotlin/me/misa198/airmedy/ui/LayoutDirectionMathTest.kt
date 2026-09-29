package me.misa198.airmedy.ui

import androidx.compose.ui.unit.LayoutDirection
import me.misa198.airmedy.ui.components.towardsEnd
import me.misa198.airmedy.ui.navigation.navigationPillLeftPx
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutDirectionMathTest {
    @Test
    fun physicalDistancesPointTowardsTheEndInLtrAndAwayFromItInRtl() {
        assertEquals(12f, 12f.towardsEnd(LayoutDirection.Ltr))
        assertEquals(-12f, 12f.towardsEnd(LayoutDirection.Rtl))
    }

    @Test
    fun navigationPillIsMeasuredFromTheStartEdge() {
        // Four 100px destinations in a 400px bar; the second one is selected.
        assertEquals(100f, navigationPillLeftPx(startOffsetPx = 100f, pillWidthPx = 100f, containerWidthPx = 400f, LayoutDirection.Ltr))
        // In RTL the second destination is the second from the right.
        assertEquals(200f, navigationPillLeftPx(startOffsetPx = 100f, pillWidthPx = 100f, containerWidthPx = 400f, LayoutDirection.Rtl))
        assertEquals(300f, navigationPillLeftPx(startOffsetPx = 0f, pillWidthPx = 100f, containerWidthPx = 400f, LayoutDirection.Rtl))
    }
}
