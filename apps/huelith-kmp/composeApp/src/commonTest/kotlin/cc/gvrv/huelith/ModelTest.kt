package cc.gvrv.huelith

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelTest {
    @Test fun groups_preserve_order() {
        val a = EditControl("a", "A", "Light", -1.0, 1.0, 0.0, 0.1, 1, 0.0)
        assertEquals(listOf("Light", "Color"), controlGroups(listOf(a, a.copy(section = "Color"), a)))
    }

    @Test fun controls_display_precise_rounded_values() {
        val ctl = EditControl("light.exposure", "Exposure", "Light", -5.0, 5.0, 0.0, 0.01, 2, 0.0)
        assertEquals("+0.29", formatValue(ctl, 0.29))
        assertEquals("-0.29", formatValue(ctl, -0.29))
        assertEquals("+0.30", formatValue(ctl, 0.299))
        assertEquals("0.00", formatValue(ctl, 0.0))
        assertEquals("+2", formatValue(ctl.copy(decimals = 0), 1.9))
    }
}
