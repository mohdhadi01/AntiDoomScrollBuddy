package com.doombuddy.character

/** Stable authored GLB clip contract, also used by the preview selector. */
enum class CharacterPerformance(val clipName: String) {
    IDLE("Idle"), ENTRANCE("Entrance"), NOTICE("Notice"), WAITING("Waiting"),
    ESCALATE("Escalate"), MELTDOWN("Meltdown"), POKE("Poke"), HOLD("Hold"),
    DISMISS("Dismiss"), CELEBRATE("Celebrate");

    companion object {
        fun forMood(level: Int): CharacterPerformance = when (level.coerceIn(0, 6)) {
            0 -> IDLE
            1 -> NOTICE
            2 -> WAITING
            3, 4 -> ESCALATE
            else -> MELTDOWN
        }
    }
}

data class CharacterPlaybackInput(
    val moodLevel: Int = 0,
    val performance: CharacterPerformance? = null,
    /** Increment for EVERY tap, including a tap during a previous reaction. */
    val pokeToken: Long = 0,
    val holding: Boolean = false,
    val dismissing: Boolean = false,
    val celebration: Boolean = false,
    val previewToken: Long = 0,
)

data class CharacterClip(val name: String, val durationSeconds: Float)

/** Missing clips never produce an invalid animator index. Legacy Fenn remains expressive. */
fun resolveCharacterClip(performance: CharacterPerformance, clips: List<CharacterClip>): Int? {
    val legacy = when (performance) {
        CharacterPerformance.IDLE, CharacterPerformance.WAITING, CharacterPerformance.HOLD -> listOf("Look around", "Tail wag", "Walk")
        CharacterPerformance.ENTRANCE, CharacterPerformance.ESCALATE, CharacterPerformance.DISMISS -> listOf("Walk", "Look around", "Tail wag")
        else -> listOf("Tail wag", "Look around", "Walk")
    }
    for (name in listOf(performance.clipName, "Idle") + legacy) {
        val index = clips.indexOfFirst { it.name.trim().equals(name, ignoreCase = true) }
        if (index >= 0) return index
    }
    return clips.indices.firstOrNull()
}

data class CharacterPlaybackFrame(
    val performance: CharacterPerformance,
    val clipIndex: Int?,
    val seconds: Float,
    val progress: Float,
    /** Changes even if repeated pokes resolve to the same clip. */
    val generation: Long,
)

/**
 * Deterministic, clock-injected performance director. The caller supplies active (not wall)
 * time, so backgrounding pauses a performance. Identical state updates never restart it.
 * Explicit previews play once and hold their last pose (Idle/Hold loop). Automatic acts
 * finish within three seconds and are separated by four seconds of idle breathing.
 */
class CharacterPlaybackPolicy(private val clips: List<CharacterClip> = emptyList()) {
    private var input = CharacterPlaybackInput()
    private var initialized = false
    private var startedAt = 0L
    private var generation = 0L
    private var current = CharacterPerformance.ENTRANCE

    fun update(next: CharacterPlaybackInput, nowMs: Long) {
        val normalized = next.copy(moodLevel = next.moodLevel.coerceIn(0, 6))
        val previous = input
        input = normalized
        val first = !initialized
        val previewChanged = normalized.performance != previous.performance || normalized.previewToken != previous.previewToken
        initialized = true
        val selected = when {
            normalized.dismissing -> CharacterPerformance.DISMISS
            normalized.holding -> CharacterPerformance.HOLD
            normalized.pokeToken != previous.pokeToken -> CharacterPerformance.POKE
            normalized.performance != null && (first || previewChanged || previous.holding || previous.dismissing) -> normalized.performance
            normalized.celebration && (first || !previous.celebration) -> CharacterPerformance.CELEBRATE
            first -> CharacterPerformance.ENTRANCE
            previous.dismissing || previous.holding || (previous.performance != null && normalized.performance == null) -> CharacterPerformance.IDLE
            normalized.performance == null && CharacterPerformance.forMood(normalized.moodLevel) != CharacterPerformance.forMood(previous.moodLevel) -> CharacterPerformance.forMood(normalized.moodLevel)
            else -> null
        }
        val repeatedPoke = selected == CharacterPerformance.POKE && normalized.pokeToken != previous.pokeToken
        if (selected != null && (first || selected != current || repeatedPoke || previewChanged)) {
            start(selected, nowMs)
        }
    }

    fun frame(nowMs: Long): CharacterPlaybackFrame {
        if (!initialized) update(input, nowMs)
        var elapsed = (nowMs - startedAt).coerceAtLeast(0L) / 1000f
        val persistent = input.dismissing || input.holding || (input.performance != null && current == input.performance)
        if (!persistent) {
            if (current != CharacterPerformance.IDLE && elapsed >= actDuration()) {
                start(input.performance ?: CharacterPerformance.IDLE, nowMs)
                elapsed = 0f
            } else if (current == CharacterPerformance.IDLE && elapsed >= IDLE_GAP_SECONDS) {
                val mood = CharacterPerformance.forMood(input.moodLevel)
                if (mood != CharacterPerformance.IDLE) {
                    start(mood, nowMs)
                    elapsed = 0f
                }
            }
        }
        val index = resolveCharacterClip(current, clips)
        val duration = clipDuration(index)
        val loop = current == CharacterPerformance.IDLE || current == CharacterPerformance.HOLD
        val seconds = if (loop) elapsed % duration else elapsed.coerceAtMost(duration)
        return CharacterPlaybackFrame(current, index, seconds, (seconds / duration).coerceIn(0f, 1f), generation)
    }

    private fun start(performance: CharacterPerformance, nowMs: Long) {
        current = performance
        startedAt = nowMs
        generation++
    }

    private fun clipDuration(index: Int?): Float = clips.getOrNull(index ?: -1)?.durationSeconds
        ?.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(0.1f) ?: 1.2f

    private fun actDuration(): Float = clipDuration(resolveCharacterClip(current, clips)).coerceIn(0.25f, 3f)

    companion object { private const val IDLE_GAP_SECONDS = 4f }
}
