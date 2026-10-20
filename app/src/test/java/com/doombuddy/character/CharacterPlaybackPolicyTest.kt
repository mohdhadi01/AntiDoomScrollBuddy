package com.doombuddy.character

import org.junit.Assert.*
import org.junit.Test

class CharacterPlaybackPolicyTest {
    private val clips = CharacterPerformance.entries.map { CharacterClip(it.clipName, 1f) }

    @Test fun `mood levels clamp and select coherent acts`() {
        val expected = listOf(CharacterPerformance.IDLE, CharacterPerformance.NOTICE, CharacterPerformance.WAITING,
            CharacterPerformance.ESCALATE, CharacterPerformance.ESCALATE, CharacterPerformance.MELTDOWN, CharacterPerformance.MELTDOWN)
        assertEquals(expected, (0..6).map(CharacterPerformance::forMood))
        assertEquals(CharacterPerformance.IDLE, CharacterPerformance.forMood(Int.MIN_VALUE))
        assertEquals(CharacterPerformance.MELTDOWN, CharacterPerformance.forMood(Int.MAX_VALUE))
    }

    @Test fun `exact names win with shuffled clips and fox has safe semantic fallbacks`() {
        val shuffled = clips.reversed()
        for (performance in CharacterPerformance.entries) {
            assertEquals(performance.clipName, shuffled[resolveCharacterClip(performance, shuffled)!!].name)
            assertNull(resolveCharacterClip(performance, emptyList()))
            assertEquals(0, resolveCharacterClip(performance, listOf(CharacterClip("unknown", 0f))))
        }
        val fox = listOf(CharacterClip("Walk", 2f), CharacterClip("Tail wag", 1f), CharacterClip("Look around", 3f))
        assertEquals(2, resolveCharacterClip(CharacterPerformance.IDLE, fox))
        assertEquals(1, resolveCharacterClip(CharacterPerformance.POKE, fox))
        assertEquals(0, resolveCharacterClip(CharacterPerformance.ESCALATE, fox))
        assertEquals(0, resolveCharacterClip(CharacterPerformance.NOTICE, listOf(CharacterClip(" notice ", 1f))))
    }

    @Test fun `entrance completes then timed mood acts have idle gaps`() {
        val policy = CharacterPlaybackPolicy(clips)
        policy.update(CharacterPlaybackInput(moodLevel = 4), 0)
        assertEquals(CharacterPerformance.ENTRANCE, policy.frame(0).performance)
        assertEquals(CharacterPerformance.IDLE, policy.frame(1000).performance)
        assertEquals(CharacterPerformance.IDLE, policy.frame(4999).performance)
        assertEquals(CharacterPerformance.ESCALATE, policy.frame(5000).performance)
        assertEquals(CharacterPerformance.IDLE, policy.frame(6000).performance)
    }

    @Test fun `unchanged updates and equivalent clamped levels never restart playback`() {
        val policy = CharacterPlaybackPolicy(clips)
        policy.update(CharacterPlaybackInput(moodLevel = 6), 0)
        val before = policy.frame(200)
        policy.update(CharacterPlaybackInput(moodLevel = 900), 400)
        val after = policy.frame(600)
        assertEquals(before.generation, after.generation)
        assertEquals(0.6f, after.seconds, 0.001f)
        policy.update(CharacterPlaybackInput(moodLevel = 2), 700)
        assertEquals(CharacterPerformance.WAITING, policy.frame(700).performance)
    }

    @Test fun `every poke restarts a finite reaction including while already poking`() {
        val policy = CharacterPlaybackPolicy(clips)
        policy.update(CharacterPlaybackInput(), 0)
        policy.update(CharacterPlaybackInput(pokeToken = 1), 100)
        val first = policy.frame(500)
        assertEquals(CharacterPerformance.POKE, first.performance)
        policy.update(CharacterPlaybackInput(pokeToken = 2), 500)
        val repeated = policy.frame(500)
        assertTrue(repeated.generation > first.generation)
        assertEquals(0f, repeated.seconds, 0f)
        assertEquals(CharacterPerformance.IDLE, policy.frame(1500).performance)
    }

    @Test fun `hold suspends acts and dismissal wins over hold poke and celebration`() {
        val policy = CharacterPlaybackPolicy(clips)
        policy.update(CharacterPlaybackInput(moodLevel = 6, holding = true), 0)
        assertEquals(CharacterPerformance.HOLD, policy.frame(120000).performance)
        policy.update(CharacterPlaybackInput(moodLevel = 6, holding = true, pokeToken = 1), 120000)
        assertEquals(CharacterPerformance.HOLD, policy.frame(120500).performance)
        policy.update(CharacterPlaybackInput(moodLevel = 6, pokeToken = 1), 121000)
        assertEquals(CharacterPerformance.IDLE, policy.frame(121000).performance)
        policy.update(CharacterPlaybackInput(holding = true, dismissing = true, celebration = true, pokeToken = 2), 121100)
        assertEquals(CharacterPerformance.DISMISS, policy.frame(130000).performance)
        assertEquals(1f, policy.frame(130000).progress, 0f)
    }

    @Test fun `celebration is edge triggered and resumes ordinary acts`() {
        val policy = CharacterPlaybackPolicy(clips)
        val input = CharacterPlaybackInput(celebration = true)
        policy.update(input, 0)
        assertEquals(CharacterPerformance.CELEBRATE, policy.frame(0).performance)
        policy.update(input, 500)
        assertEquals(0.5f, policy.frame(500).seconds, 0f)
        assertEquals(CharacterPerformance.IDLE, policy.frame(1000).performance)
        policy.update(input, 1100)
        assertEquals(CharacterPerformance.IDLE, policy.frame(1100).performance)
        policy.update(input.copy(celebration = false), 1200)
        policy.update(input, 1300)
        assertEquals(CharacterPerformance.CELEBRATE, policy.frame(1300).performance)
    }

    @Test fun `preview freezes final pose without restarting and poke temporarily interrupts it`() {
        val policy = CharacterPlaybackPolicy(clips)
        val preview = CharacterPlaybackInput(performance = CharacterPerformance.WAITING)
        policy.update(preview, 0)
        policy.update(preview, 100)
        assertEquals(0.2f, policy.frame(200).seconds, 0.001f)
        assertEquals(CharacterPerformance.WAITING, policy.frame(5000).performance)
        assertEquals(1f, policy.frame(5000).progress, 0f)
        policy.update(preview.copy(pokeToken = 1), 6000)
        policy.update(preview.copy(pokeToken = 1), 6500)
        assertEquals(CharacterPerformance.POKE, policy.frame(6500).performance)
        assertEquals(CharacterPerformance.WAITING, policy.frame(7000).performance)
        policy.update(CharacterPlaybackInput(pokeToken = 1), 8000)
        assertEquals(CharacterPerformance.IDLE, policy.frame(8000).performance)
    }

    @Test fun `missing zero and nonfinite clip durations always yield finite samples`() {
        for (duration in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val policy = CharacterPlaybackPolicy(listOf(CharacterClip("Idle", duration)))
            policy.update(CharacterPlaybackInput(performance = CharacterPerformance.IDLE), 0)
            val frame = policy.frame(100000)
            assertTrue(frame.seconds.isFinite())
            assertTrue(frame.progress in 0f..1f)
        }
        val empty = CharacterPlaybackPolicy()
        assertNull(empty.frame(0).clipIndex)
        assertEquals(CharacterPerformance.IDLE, empty.frame(1200).performance)
    }

    @Test fun `replaying the same preview starts at zero without recreating the renderer`() {
        val policy = CharacterPlaybackPolicy(clips)
        val input = CharacterPlaybackInput(performance = CharacterPerformance.MELTDOWN)
        policy.update(input, 0)
        val first = policy.frame(600)
        policy.update(input.copy(previewToken = 1), 600)
        val replay = policy.frame(600)
        assertEquals(CharacterPerformance.MELTDOWN, replay.performance)
        assertEquals(0f, replay.seconds, 0f)
        assertTrue(replay.generation > first.generation)
    }
}
