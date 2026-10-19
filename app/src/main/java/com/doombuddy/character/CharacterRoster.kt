package com.doombuddy.character

import androidx.compose.ui.graphics.Color

/**
 * One expressive rigged 3D fox companion.
 *
 * The model is a local GLB with embedded materials and authored animation clips. It is an
 * original licensed asset, not a recreation of an Ice Age character or any other franchise.
 */
object CharacterRoster {
    private fun crew(
        id: String,
        name: String,
        asset: String,
        personality: Personality,
        tagline: String,
        top: Color,
        bottom: Color,
        belly: Color,
        accent: Color,
        portrait: String,
        dialogue: DialogueBank,
    ) = CharacterDefinition(
        id = id,
        displayName = name,
        species = Species.HUMANOID,
        modelAsset = asset,
        portraitName = portrait,
        personality = personality,
        tagline = tagline,
        palette = Palette(
            bodyTop = top,
            bodyBottom = bottom,
            belly = belly,
            pupil = Color(0xFF1E2430),
            blush = Color(0xFFFF9C9C),
            accent = accent,
            shadow = Color(0x55323A48),
        ),
        proportions = Proportions(bodyWidth = 0.72f, bodyHeight = 0.92f),
        accessory = Accessory.NONE,
        dialogue = dialogue,
    )

    private fun lines(first: String, clock: String, long: String, dismiss: String) = DialogueBank(
        peek = listOf("Oh. Hi.", "You again."),
        speak = listOf(first, "Still scrolling?"),
        clocking = listOf(clock, "The timer has entered the chat."),
        ridiculous = listOf("I have a plan. It is mostly standing here.", "This is now a team activity."),
        fedUp = listOf("We need a meeting about this.", "I am escalating artistically."),
        gaveUp = listOf("I live here now.", "Wake me when the scroll ends."),
        rapidReopen = listOf("That was a short escape.", "Back already?"),
        longSession = listOf(long),
        celebration = listOf("We escaped!", "A beautiful exit."),
        dismissed = listOf(dismiss, "Fine. I will be around."),
        lateNight = listOf("Night mode says sleep."),
        todayMilestone = listOf("That is a lot of today.", "The total has opinions."),
    )

    // Keep the stable "melt" id so existing preferences migrate without a database change.
    val MELT = crew(
        id = "melt",
        name = "Fenn",
        asset = "fox.glb",
        personality = Personality.PLAYFUL,
        tagline = "A tiny fox with zero chill.",
        top = Color(0xFFF2A05A),
        bottom = Color(0xFF9A4D2F),
        belly = Color(0xFFFFE3C1),
        accent = Color(0xFF6B3325),
        portrait = "fenn_portrait",
        dialogue = lines(
            "One more scroll? I support bad decisions.",
            "My tail says we have been here too long.",
            "I chased a thought and it got away.",
            "Fine. I will stop being adorable over here.",
        ),
    )

    val ALL: List<CharacterDefinition> = listOf(MELT)

    fun byId(id: String): CharacterDefinition = ALL.firstOrNull { it.id == id } ?: MELT
}
