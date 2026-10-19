package com.doombuddy.character

import androidx.compose.ui.graphics.Color

/**
 * Ten real rigged 3D characters from Kenney's Mini Characters pack.
 *
 * The models are local GLB assets with embedded materials and animation clips. They are
 * intentionally presented as a crew with original names/copy; the art is not represented
 * as Minions or any other copyrighted franchise.
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

    val MELT = crew("melt", "Marlow", "male_a.glb", Personality.LAZY, "Professional pause button.", Color(0xFFB9A8D6), Color(0xFF5E4D7C), Color(0xFFF1D3C2), Color(0xFFF0B365), "mini_male_a", lines("We are really doing this?", "Five minutes became a lifestyle.", "I have become one with the couch.", "Fine. I saw that."))
    val ZOBO = crew("zobo", "Orbit", "male_b.glb", Personality.CONFUSED, "Mission control is concerned.", Color(0xFF8ED1E4), Color(0xFF315C88), Color(0xFFD9EDF0), Color(0xFFF4CF71), "mini_male_b", lines("What is the mission here?", "Elapsed time is suspicious.", "My report has become a novel.", "Acknowledged. Temporarily."))
    val QUACKLES = crew("quackles", "Riff", "male_c.glb", Personality.CHAOTIC, "No brakes. Many opinions.", Color(0xFFF4B36C), Color(0xFFB54B46), Color(0xFFFFE2B1), Color(0xFF52D1C3), "mini_male_c", lines("One more, then we go.", "We missed the exit ramp.", "My knees filed a complaint.", "Okay okay, I am going."))
    val NOVA = crew("nova", "Nova", "male_d.glb", Personality.DRAMATIC, "Makes every minute cinematic.", Color(0xFFE18CA7), Color(0xFF6D3B64), Color(0xFFFFD4CF), Color(0xFFFFD166), "mini_male_d", lines("Roll the opening credits.", "The plot is getting long.", "This episode needs an ending.", "Cut! We are done here."))
    val PIXEL = crew("pixel", "Pixel", "male_e.glb", Personality.PLAYFUL, "One more level, allegedly.", Color(0xFF8EC6A4), Color(0xFF3A756A), Color(0xFFE7F1D5), Color(0xFF83D8F4), "mini_male_e", lines("Was that a side quest?", "Achievement unlocked: still here.", "I have lost the main storyline.", "Saving progress. Bye."))
    val LUMA = crew("luma", "Luma", "female_a.glb", Personality.SWEET, "Gentle nudge, excellent timing.", Color(0xFFE9A1B7), Color(0xFF834B71), Color(0xFFFFDFD2), Color(0xFFBDA6FF), "mini_female_a", lines("Want to take a tiny break?", "That timer is getting brave.", "We have had a very full day.", "Okay. I will miss you."))
    val JET = crew("jet", "Jet", "female_b.glb", Personality.CHAOTIC, "Runs on momentum.", Color(0xFFE6BE75), Color(0xFF735D40), Color(0xFFFFE6BE), Color(0xFF65D5B6), "mini_female_b", lines("I was only checking one thing!", "The scroll has acceleration.", "I may need a pit crew.", "That was a clean getaway."))
    val SAGE = crew("sage", "Sage", "female_c.glb", Personality.DEADPAN, "Quietly taking notes.", Color(0xFFB6C6D8), Color(0xFF496478), Color(0xFFE4EFF3), Color(0xFFE5B96D), "mini_female_c", lines("Interesting choice.", "The data is becoming personal.", "I have several notes.", "Noted. See you soon."))
    val ECHO = crew("echo", "Echo", "female_d.glb", Personality.MISCHIEVOUS, "Has seen this loop before.", Color(0xFFB9A2DF), Color(0xFF55417E), Color(0xFFF3D9D4), Color(0xFF79D4E0), "mini_female_d", lines("Was that the same clip?", "The loop is looping.", "I have memorized the feed.", "I will pretend not to notice."))
    val MOSS = crew("moss", "Moss", "female_e.glb", Personality.CHILL, "A tiny calm interruption.", Color(0xFFA8D6A5), Color(0xFF4D7556), Color(0xFFE0EACB), Color(0xFFF3C874), "mini_female_e", lines("Soft reset?", "Time is doing its thing.", "Even my patience is buffering.", "Go on. I will be here."))

    val ALL: List<CharacterDefinition> = listOf(MELT, ZOBO, QUACKLES, NOVA, PIXEL, LUMA, JET, SAGE, ECHO, MOSS)

    fun byId(id: String): CharacterDefinition = ALL.firstOrNull { it.id == id } ?: MELT
}
