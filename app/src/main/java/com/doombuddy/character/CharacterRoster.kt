package com.doombuddy.character

import androidx.compose.ui.graphics.Color

/**
 * V1 launch cast. These are deliberately humanoid/toy-like rather than blobs:
 * every buddy has a head, clothing, limbs, footwear, a readable silhouette, and a
 * prop that makes the acting legible at overlay size.
 *
 * The IDs remain stable for existing installs; changing a buddy is therefore a
 * visual/content replacement, not a preference migration.
 */
object CharacterRoster {
    val MELT = CharacterDefinition(
        id = "melt",
        displayName = "Marlow",
        species = Species.HUMANOID,
        personality = Personality.LAZY,
        tagline = "The couch has a point.",
        palette = Palette(
            bodyTop = Color(0xFFB8A4F5),
            bodyBottom = Color(0xFF5D4A9C),
            belly = Color(0xFFFFD7C5),
            pupil = Color(0xFF242133),
            blush = Color(0xFFFF9FAD),
            accent = Color(0xFFF3C86B),
            shadow = Color(0x55251A3A),
        ),
        proportions = Proportions(bodyWidth = 0.72f, bodyHeight = 0.9f, eyeSize = 0.07f),
        accessory = Accessory.HAT,
        dialogue = DialogueBank(
            peek = listOf("Oh. You.", "The couch misses you."),
            speak = listOf("Still scrolling?", "That was one video."),
            clocking = listOf("The clock is judging us.", "Five minutes became a lifestyle."),
            ridiculous = listOf("I brought a blanket. Obviously.", "I have fully moved in."),
            fedUp = listOf("I am sitting down harder.", "Marlow requests a plot twist."),
            gaveUp = listOf("Wake me when the scroll ends.", "I will be over here. Existing."),
            rapidReopen = listOf("That was a quick escape.", "Back already? Bold."),
            longSession = listOf("I have become one with the upholstery."),
            celebration = listOf("We are free. Briefly.", "A beautiful exit."),
            dismissed = listOf("Fine. Five more minutes.", "I saw that."),
            lateNight = listOf("The blanket says sleep."),
            todayMilestone = listOf("That is a lot of today.", "The couch is concerned."),
        ),
    )

    val ZOBO = CharacterDefinition(
        id = "zobo",
        displayName = "Orbit",
        species = Species.ASTRONAUT,
        personality = Personality.CONFUSED,
        tagline = "Mission control is concerned.",
        palette = Palette(
            bodyTop = Color(0xFF8EE4D0),
            bodyBottom = Color(0xFF237A83),
            belly = Color(0xFFD8F7F2),
            pupil = Color(0xFF102B39),
            blush = Color(0xFF84D8E9),
            accent = Color(0xFFFFD36B),
            shadow = Color(0x55306E77),
        ),
        proportions = Proportions(bodyWidth = 0.68f, bodyHeight = 0.96f, eyeSize = 0.075f),
        accessory = Accessory.ANTENNAE,
        dialogue = DialogueBank(
            peek = listOf("Signal acquired.", "Hello, Earth person."),
            speak = listOf("What is the mission here?", "Is this still the same video?"),
            clocking = listOf("Elapsed time is becoming suspicious.", "Mission timer says: too much."),
            ridiculous = listOf("I am documenting this for science.", "The screen has gravity."),
            fedUp = listOf("Mission control says log off.", "This is not in the flight plan."),
            gaveUp = listOf("Returning to orbit.", "I will float here quietly."),
            rapidReopen = listOf("The human returned.", "A fascinating pattern."),
            longSession = listOf("My report has become a novel."),
            celebration = listOf("Successful re-entry!", "Earth mission complete."),
            dismissed = listOf("Acknowledged. Temporarily.", "I will be watching the stars."),
            lateNight = listOf("Night cycle detected. Please power down."),
            todayMilestone = listOf("Today total exceeds flight protocol.", "I have questions about this planet."),
        ),
    )

    val QUACKLES = CharacterDefinition(
        id = "quackles",
        displayName = "Riff",
        species = Species.SKATER,
        personality = Personality.CHAOTIC,
        tagline = "No brakes. Many opinions.",
        palette = Palette(
            bodyTop = Color(0xFFFFC56B),
            bodyBottom = Color(0xFFE05A3F),
            belly = Color(0xFFFFE7B6),
            pupil = Color(0xFF362018),
            blush = Color(0xFFFF8B6B),
            accent = Color(0xFF4BD4C6),
            shadow = Color(0x554C241B),
        ),
        proportions = Proportions(bodyWidth = 0.76f, bodyHeight = 0.92f, eyeSize = 0.073f),
        accessory = Accessory.HAIR_SWIRL,
        dialogue = DialogueBank(
            peek = listOf("Wait. We are still here?", "I have a theory."),
            speak = listOf("One more, then we go.", "This scroll has momentum."),
            clocking = listOf("The timer is doing numbers!", "We missed the exit ramp."),
            ridiculous = listOf("I brought wheels.", "I am solving this with speed."),
            fedUp = listOf("Emergency dramatic reaction!", "I have fallen and blamed the app."),
            gaveUp = listOf("I am horizontal now.", "No thoughts. Just floor."),
            rapidReopen = listOf("WE WERE JUST HERE!", "The comeback is immediate!"),
            longSession = listOf("My knees have filed a complaint."),
            celebration = listOf("WE ESCAPED!", "Kickflip out!"),
            dismissed = listOf("Okay okay, I am going.", "That was almost a trick."),
            lateNight = listOf("The skatepark is closed. Go sleep."),
            todayMilestone = listOf("That total needs a helmet.", "Today just did a backflip."),
        ),
    )

    val ALL: List<CharacterDefinition> = listOf(MELT, ZOBO, QUACKLES)

    fun byId(id: String): CharacterDefinition = ALL.firstOrNull { it.id == id } ?: MELT
}
