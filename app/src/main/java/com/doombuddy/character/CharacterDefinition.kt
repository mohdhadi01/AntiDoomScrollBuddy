package com.doombuddy.character

import androidx.compose.ui.graphics.Color

/**
 * A character is DATA, not code. Adding a buddy means adding a definition (and a species
 * draw routine); the intervention engine, animator and renderer stay untouched.
 */
data class CharacterDefinition(
    val id: String,
    val displayName: String,
    val species: Species,
    /** Asset path under app/src/main/assets/characters, when this is a real 3D buddy. */
    val modelAsset: String? = null,
    /** Drawable portrait used for lightweight roster thumbnails. */
    val portraitName: String? = null,
    val personality: Personality,
    val tagline: String,
    val palette: Palette,
    val proportions: Proportions,
    val accessory: Accessory,
    val dialogue: DialogueBank,
)

enum class Personality { SWEET, ANGRY, PLAYFUL, MISCHIEVOUS, LAZY, CONFUSED, CHAOTIC, CHILL, JUDGMENTAL, DEADPAN, DRAMATIC, WHOLESOME }

enum class Species {
    HUMANOID,
    ASTRONAUT,
    SKATER,
    ORB,
    SHADOW,
    BLOB,
    ALIEN,
    DUCK,
    CAT,
    PANDA,
    FROG,
    ROBOT,
    GHOST,
}

/** How "escalated" the buddy currently looks. Maps 1:1 to intervention levels. */
enum class Mood {
    CALM,        // level 1: subtle peek
    TALKATIVE,   // level 2: speaking
    CLOCKING,    // level 3: interacting with the timer
    RIDICULOUS,  // level 4: prop comedy
    FED_UP,      // level 5: major intervention
    DRAMATIC,    // level 6: gives up / exhausted
}

enum class MouthShape { SMALL_SMILE, SMILE, GRIN, OPEN_O, FLAT, WAVE, SMIRK, FROWN }

enum class Accessory { NONE, LEAF, BOW, ANTENNA, ANTENNAE, HALO, HAIR_SWIRL, CROWN, SCARF, SUNGLASSES, HAT, GLASSES }

data class Palette(
    val bodyTop: Color,
    val bodyBottom: Color,
    val belly: Color,
    val pupil: Color,
    val blush: Color,
    val accent: Color,
    val shadow: Color,
)

/**
 * Normalized rig dimensions (unit space: body fits roughly in 1.0 x 1.0).
 * The renderer scales these into pixels.
 */
data class Proportions(
    val bodyWidth: Float = 0.86f,
    val bodyHeight: Float = 0.74f,
    val eyeSize: Float = 0.11f,
    val eyeSpacing: Float = 0.155f,
    val eyeVertical: Float = 0.36f,
    val mouthVertical: Float = 0.56f,
    val cornerRadius: Float = 0.5f,
)

/** Line banks per product event. Keep every line SHORT — the character performs, not recites. */
data class DialogueBank(
    val peek: List<String>,          // level 1
    val speak: List<String>,         // level 2
    val clocking: List<String>,      // level 3
    val ridiculous: List<String>,    // level 4
    val fedUp: List<String>,         // level 5
    val gaveUp: List<String>,        // level 6
    val rapidReopen: List<String>,
    val longSession: List<String>,
    val celebration: List<String>,
    val dismissed: List<String>,
    val lateNight: List<String> = emptyList(),
    /** Rare reactions when the user's DAY total crosses a milestone (spec §56). */
    val todayMilestone: List<String> = emptyList(),
) {
    fun forLevel(level: Int): List<String> = when (level) {
        1 -> peek
        2 -> speak
        3 -> clocking
        4 -> ridiculous
        5 -> fedUp
        else -> gaveUp
    }
}
