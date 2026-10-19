package com.doombuddy.character

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChoreographyTest {

    @Test
    fun `early session pool is curious not invasive`() {
        val pool = Choreography.poolFor(0.1f)
        assertTrue(Acting.PEEK in pool)
        assertTrue(Acting.NAP !in pool)
        assertTrue(Acting.POPCORN !in pool)
    }

    @Test
    fun `mid session pool escalates to snacking and nesting`() {
        val pool = Choreography.poolFor(0.6f)
        assertTrue(Acting.POPCORN in pool || Acting.NEST in pool)
        assertTrue(Acting.PEEK !in pool)
    }

    @Test
    fun `late session pool is fully moved in`() {
        val pool = Choreography.poolFor(0.95f)
        assertTrue(Acting.NAP in pool)
        assertTrue(Acting.FACEPALM in pool)
        assertTrue(Acting.WAVE !in pool)
    }

    @Test
    fun `pick avoids immediate repetition when alternatives exist`() {
        val random = Random(42)
        val pool = listOf(Acting.PEEK, Acting.WAVE, Acting.YAWN)
        repeat(50) {
            val picked = Choreography.pick(pool, Acting.PEEK, random)
            assertNotEquals(Acting.PEEK, picked)
        }
    }

    @Test
    fun `gap shortens as session progresses`() {
        val random = Random(7)
        val early = List(20) { Choreography.gapMs(0.1f, random) }.average()
        val late = List(20) { Choreography.gapMs(0.9f, random) }.average()
        assertTrue("early=$early late=$late", late < early)
    }

    @Test
    fun `hold times stay in comedy-sized bounds`() {
        val random = Random(1)
        Acting.entries.forEach { action ->
            val ms = Choreography.holdMs(action, random)
            assertTrue("$action hold=$ms", ms in 500..9_000)
        }
    }
}
