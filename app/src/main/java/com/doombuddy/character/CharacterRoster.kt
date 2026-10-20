package com.doombuddy.character

import androidx.compose.ui.graphics.Color

/** Original comedy cast plus the licensed fox. Stable IDs preserve existing selections. */
object CharacterRoster {
    const val DEFAULT_ID = "loui"

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
        portrait: String? = null,
        dialogue: DialogueBank,
        species: Species = Species.HUMANOID,
    ) = CharacterDefinition(
        id = id,
        displayName = name,
        species = species,
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

    val LOUI = crew(
        id = DEFAULT_ID, name = "Loui", asset = "loui.glb",
        personality = Personality.DEADPAN,
        tagline = "This scroll could have been an email.",
        top = Color(0xFFA998E8), bottom = Color(0xFF65568D),
        belly = Color(0xFFDCA47D), accent = Color(0xFFFFC978),
        dialogue = DialogueBank(
            peek = listOf("Quick meeting?", "Clocking in."),
            speak = listOf("I brought coffee.", "This scroll could have been an email."),
            clocking = listOf("I've started taking minutes.", "The report is getting longer."),
            ridiculous = listOf("Please find attached: your afternoon.", "This is a full-time scroll."),
            fedUp = listOf("My coffee has retired.", "The meeting has become a lifestyle."),
            gaveUp = listOf("I'm on my third imaginary break.", "Out of office. Mentally."),
            rapidReopen = listOf("Another meeting already?"),
            longSession = listOf("We could have left at the first slide."),
            celebration = listOf("Meeting adjourned.", "Finally. Lunch."),
            dismissed = listOf("I'll send the minutes.", "Taking this offline."),
            lateNight = listOf("Even the spreadsheet is asleep."),
            todayMilestone = listOf("The timesheet has questions."),
        ),
    )

    val BUFFER = crew(
        id = "buffer", name = "Buffer", asset = "buffer.glb", species = Species.ROBOT,
        personality = Personality.CONFUSED,
        tagline = "Loading a reason we're still here…",
        top = Color(0xFF68CEBC), bottom = Color(0xFF326E70),
        belly = Color(0xFFD9EEE3), accent = Color(0xFFB9FF74),
        dialogue = DialogueBank(
            peek = listOf("Connection established.", "Oh. More scrolling."),
            speak = listOf("Loading a reason we're still here…", "Still buffering."),
            clocking = listOf("Printing your receipt.", "One more? Adding it to the bill."),
            ridiculous = listOf("Receipt longer than my legs.", "Paper jam. Emotionally."),
            fedUp = listOf("Have we tried turning the scroll off?", "Rebooting my expectations."),
            gaveUp = listOf("404: ending not found.", "Entering potato mode."),
            rapidReopen = listOf("That reboot was quick."),
            longSession = listOf("My warranty doesn't cover this."),
            celebration = listOf("Task failed successfully. We left!", "Connection to outside restored."),
            dismissed = listOf("Going into standby.", "Receipt saved. In my feelings."),
            lateNight = listOf("Sleep mode is a feature."),
            todayMilestone = listOf("Storage full of scrolling."),
        ),
    )

    val GOOP = crew(
        id = "goop", name = "Goop", asset = "goop.glb", species = Species.BLOB,
        personality = Personality.DRAMATIC,
        tagline = "Not built for this much plot.",
        top = Color(0xFFC3F465), bottom = Color(0xFF69AF4D),
        belly = Color(0xFFEAFFAD), accent = Color(0xFFFF9DB0),
        dialogue = DialogueBank(
            peek = listOf("I have arrived.", "What's the plot?"),
            speak = listOf("I'm invested. Unfortunately.", "Not built for this much plot."),
            clocking = listOf("Raising a tiny concern.", "It's a white flag kind of day."),
            ridiculous = listOf("My bones would hurt. If I had any.", "I surrender to the algorithm."),
            fedUp = listOf("Becoming floor.", "Please respect my puddle era."),
            gaveUp = listOf("I am soup now.", "Nothing left but vibes."),
            rapidReopen = listOf("I JUST reassembled."),
            longSession = listOf("I've changed states of matter."),
            celebration = listOf("We have a shape again!", "Solid decision."),
            dismissed = listOf("Sliding out of this situation.", "Don't step in me."),
            lateNight = listOf("Even slime needs sleep."),
            todayMilestone = listOf("A whole day of character development."),
        ),
    )

    val ALL: List<CharacterDefinition> = listOf(LOUI, BUFFER, GOOP, MELT)

    fun byId(id: String): CharacterDefinition = ALL.firstOrNull { it.id == id } ?: LOUI
}
