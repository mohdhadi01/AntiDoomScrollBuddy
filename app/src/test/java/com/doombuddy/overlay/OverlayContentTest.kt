package com.doombuddy.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayContentTest {
    @Test
    fun timerTextStaysCompactAndReadable() {
        assertEquals("00:04", formatSession(4_000))
        assertEquals("59:59", formatSession(3_599_000))
        assertEquals("1:00:00", formatSession(3_600_000))
        assertEquals("1h 47m", formatToday((107 * 60_000).toLong()))
        assertEquals("59m", formatToday((59 * 60_000).toLong()))
    }
}
