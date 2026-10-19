package com.doombuddy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class TimeDataTest {

    private val zone = ZoneId.of("Asia/Kolkata") // fixed offset, no DST noise

    private fun epoch(iso: String): Long =
        java.time.ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun `same-day session stays one row`() {
        val start = epoch("2026-09-29T10:00:00+05:30[Asia/Kolkata]")
        val end = epoch("2026-09-29T10:04:00+05:30[Asia/Kolkata]")
        val rows = SessionSplitter.split(start, end, zone)
        assertEquals(1, rows.size)
        assertEquals(4 * 60_000L, rows[0].second)
    }

    @Test
    fun `midnight crossing splits duration across two days`() {
        val start = epoch("2026-09-29T23:59:30+05:30[Asia/Kolkata]")
        val end = epoch("2026-09-30T00:00:30+05:30[Asia/Kolkata]")
        val rows = SessionSplitter.split(start, end, zone)
        assertEquals(2, rows.size)
        assertEquals(30_000L, rows[0].second)
        assertEquals(30_000L, rows[1].second)
        // Row day-starts differ and each is the local midnight.
        assertTrue(rows[0].first != rows[1].first)
    }

    @Test
    fun `multi-day session splits per day without losing time`() {
        val start = epoch("2026-09-29T22:00:00+05:30[Asia/Kolkata]")
        val end = epoch("2026-10-01T02:00:00+05:30[Asia/Kolkata]")
        val rows = SessionSplitter.split(start, end, zone)
        assertEquals(3, rows.size) // Sep29: 2h, Sep30: 24h, Oct1: 2h
        assertEquals(28 * 60 * 60_000L, rows.sumOf { it.second })
        assertEquals(2 * 60 * 60_000L, rows[0].second)
        assertEquals(24 * 60 * 60_000L, rows[1].second)
    }

    @Test
    fun `empty and inverted ranges produce nothing`() {
        val a = epoch("2026-09-29T10:00:00+05:30[Asia/Kolkata]")
        assertEquals(0, SessionSplitter.split(a, a, zone).size)
        assertEquals(0, SessionSplitter.split(a + 1, a, zone).size)
    }

    @Test
    fun `live today adds running session to persisted total`() {
        assertEquals(37 * 60_000L + 4 * 60_000L + 12_000L, ReclaimedCalculator.liveTodayMs(persistedTodayMs = 37 * 60_000L, currentSessionElapsedMs = 4 * 60_000L + 12_000L))
        assertEquals(0L, ReclaimedCalculator.liveTodayMs(persistedTodayMs = 0, currentSessionElapsedMs = 0))
    }

    @Test
    fun `milestone fires once per threshold`() {
        val m30 = TodayMilestones.milestonesMs[0]
        assertNull(TodayMilestones.crossed(previousMs = m30, currentMs = m30 + 1_000))
        assertEquals(m30, TodayMilestones.crossed(previousMs = m30 - 1, currentMs = m30 + 500))
        assertNull(TodayMilestones.crossed(previousMs = m30, currentMs = m30 - 1))
    }
}
