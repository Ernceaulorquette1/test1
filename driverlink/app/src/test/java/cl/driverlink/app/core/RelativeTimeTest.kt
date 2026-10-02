package cl.driverlink.app.core

import cl.driverlink.app.core.time.RelativeTime
import cl.driverlink.app.core.time.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val min = 60_000L

    @Test fun `intervalos legibles`() {
        assertEquals(RelativeTime.JustNow, RelativeTime.between(0, 30_000))
        assertEquals(RelativeTime.Minutes(5), RelativeTime.between(0, 5 * min))
        assertEquals(RelativeTime.Hours(2), RelativeTime.between(0, 125 * min))
        assertEquals(RelativeTime.Days(3), RelativeTime.between(0, 3 * 24 * 60 * min))
        assertEquals(RelativeTime.JustNow, RelativeTime.between(10 * min, 0))
    }

    @Test fun `duracion de audio`() {
        assertEquals("0:05", formatDuration(5_400))
        assertEquals("1:30", formatDuration(90_000))
    }
}
