package com.doombuddy.character

import kotlin.random.Random

/** Product-level dialogue triggers (orthogonal to escalation level). */
enum class DialogueEvent {
    THRESHOLD,      // level provided separately
    RAPID_REOPEN,
    LONG_SESSION,
    CELEBRATION,
    DISMISSED,
    TODAY_MILESTONE,
}

/**
 * Picks short lines with anti-repetition guarantees:
 *  1. no immediate repeat for the same (character, event, level) bucket
 *  2. app-specific quips injected with bounded probability so they stay fresh
 *
 * Deterministic given the same [Random] seed — unit-testable.
 */
class DialogueEngine(private val random: Random = Random.Default) {

    private val recent = mutableMapOf<String, String>()

    data class Choice(val line: String, val showAppQuip: Boolean)

    fun select(
        character: CharacterDefinition,
        event: DialogueEvent,
        level: Int = 0,
        foregroundAppName: String? = null,
        minuteOfDay: Int = 600,
    ): Choice {
        // Late-night override: only for early interventions, nothing more specific happening.
        if (event == DialogueEvent.THRESHOLD && level <= 2 &&
            character.dialogue.lateNight.isNotEmpty() &&
            (minuteOfDay >= 23 * 60 + 30 || minuteOfDay < 5 * 60)
        ) {
            return Choice(pick(character.id, "late", character.dialogue.lateNight), showAppQuip = false)
        }

        val bank: List<String> = when (event) {
            DialogueEvent.THRESHOLD -> character.dialogue.forLevel(level)
            DialogueEvent.RAPID_REOPEN -> character.dialogue.rapidReopen
            DialogueEvent.LONG_SESSION -> character.dialogue.longSession
            DialogueEvent.CELEBRATION -> character.dialogue.celebration
            DialogueEvent.DISMISSED -> character.dialogue.dismissed
            DialogueEvent.TODAY_MILESTONE -> character.dialogue.todayMilestone
        }
        val main = pick(character.id, event.name + level, bank)

        val appQuip = foregroundAppName?.let { AppQuips.forApp(it, character.personality) }
        val useQuip = appQuip != null &&
            event == DialogueEvent.THRESHOLD &&
            level in 1..3 &&
            random.nextFloat() < 0.4f

        return Choice(line = if (useQuip) appQuip!! else main, showAppQuip = useQuip)
    }

    private fun pick(key: String, bucket: String, options: List<String>): String {
        if (options.isEmpty()) return ""
        if (options.size == 1) return options[0]
        val last = recent["$key/$bucket"]
        var choice = options[random.nextInt(options.size)]
        var guard = 0
        while (choice == last && guard++ < 4) {
            choice = options[random.nextInt(options.size)]
        }
        recent["$key/$bucket"] = choice
        return choice
    }
}

/** Original one-liners keyed by doomscroll destination, flavored by roster personality. */
object AppQuips {

    private data class AppLines(
        val lazy: List<String>,      // The Melt
        val confused: List<String>,  // Zobo
        val chaotic: List<String>,   // Quackles
        val playful: List<String> = emptyList(),     // Pip
        val mischievous: List<String> = emptyList(), // Shade
    )

    private val byApp: Map<String, AppLines> = mapOf(
        "instagram" to AppLines(
            lazy = listOf("Another reel. Shocking.", "Reels… why are they like this."),
            confused = listOf("Human watches tiny humans. Why?", "Is this… important?"),
            chaotic = listOf("REEL NUMBER 47! QUACK!"),
            playful = listOf("Another reel! Ooh! Wait—no!"),
            mischievous = listOf("*flickers over your feed*"),
        ),
        "youtube" to AppLines(
            lazy = listOf("Shorts queue: infinite.", "'One video.' Sure."),
            confused = listOf("Why watch a stranger open a box?", "Video #12 of 'just one'."),
            chaotic = listOf("AUTOPLAY! IT'S A TRAP! QUACK!"),
            playful = listOf("Shorts? AGAIN? ...okay one more."),
            mischievous = listOf("The autoplay abyss. Classic."),
        ),
        "tiktok" to AppLines(
            lazy = listOf("'Last one' — a saga.", "Swipe. Swipe. Yawn."),
            confused = listOf("What IS this dance? Why?", "Tiny humans. Tiny videos. Why."),
            chaotic = listOf("QUACK SWIPE QUACK SWIPE!"),
            playful = listOf("SWIPE RACE! GO GO GO!"),
            mischievous = listOf("Infinite tiny videos. Delicious chaos."),
        ),
        "reddit" to AppLines(
            lazy = listOf("The hole is bottomless. Carry on.", "r/doomscrolling."),
            confused = listOf("Why argue with strangers?", "The hole. It keeps going."),
            chaotic = listOf("RABBIT HOLE! BUNNY! QUACK!"),
            playful = listOf("Rabbit hole! I'll dig!"),
            mischievous = listOf("The hole is bottomless. I checked."),
        ),
        "twitter" to AppLines(
            lazy = listOf("'Checking one thing' since forever.", "The discourse. Riveting."),
            confused = listOf("Humans yell into void. Strange.", "Who is 'they'? Why?"),
            chaotic = listOf("HOT TAKE! QUACK! ANOTHER ONE!"),
            playful = listOf("Hot takes! Catch! TOO MANY!"),
            mischievous = listOf("The discourse feeds me."),
        ),
        "facebook" to AppLines(
            lazy = listOf("Uncle status: unhinged.", "Marketplace misses you."),
            confused = listOf("Who are all these relatives?", "Why wish strangers 'happy'?"),
            chaotic = listOf("POKE! POKE! QUACK!"),
            playful = listOf("Your uncle posted AGAIN."),
            mischievous = listOf("Family drama. My favorite."),
        ),
        "snapchat" to AppLines(
            lazy = listOf("Streaks > sleep, apparently.", "Dog filter. Classic."),
            confused = listOf("Why send face with dog ears?", "The streak. It means nothing."),
            chaotic = listOf("FILTER! QUACK! DOG EARS!"),
            playful = listOf("STREAKS! DOG EARS! GO!"),
            mischievous = listOf("Streaks are a social construct. So am I."),
        ),
        "chrome" to AppLines(
            lazy = listOf("Wikipedia rabbit holes count too.", "One tab became forty-one."),
            confused = listOf("What is 'just researching'?", "47 tabs. All 'important'."),
            chaotic = listOf("TABS! TABS EVERYWHERE! QUACK!"),
            playful = listOf("47 tabs! Let's open one more!"),
            mischievous = listOf("The dark between tabs. Cozy."),
        ),
    )

    /** Match by package substring, e.g. com.instagram.android -> "instagram". */
    fun forApp(packageName: String, personality: Personality): String? {
        val entry = byApp.entries.firstOrNull { packageName.contains(it.key, ignoreCase = true) }
            ?: return null
        return when (personality) {
            Personality.LAZY -> entry.value.lazy.randomOrNull()
            Personality.CONFUSED -> entry.value.confused.randomOrNull()
            Personality.CHAOTIC -> entry.value.chaotic.randomOrNull()
            Personality.PLAYFUL -> entry.value.playful.randomOrNull()
            Personality.MISCHIEVOUS -> entry.value.mischievous.randomOrNull()
            else -> null
        }
    }
}
