package com.doombuddy.character

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/**
 * What the buddy is currently DOING — the core of "show, don't tell".
 * Actions are picked from a context-weighted pool by [CharacterDirector] as the
 * session progresses, so elapsed time is communicated through acting, not text.
 */
enum class Acting { IDLE, PEEK, FIDGET_PHONE, POPCORN, CLOCK_TAP, NEST, NAP, YAWN, STRETCH, FACEPALM, CELEBRATE, DIZZY, WAVE }

/** Particle overlays rendered procedurally (bounded counts, no allocations per frame). */
data class Effects(
    val zzz: Boolean = false,
    val confetti: Boolean = false,
    val dizzy: Boolean = false,
    val sparkle: Boolean = false,
    val snackCrumbs: Boolean = false,
)

/** Props drawn around/behind the buddy. */
enum class Prop { NONE, COUCH, HOURGLASS, BLANKET, POCKET_CLOCK, POPCORN, TINY_PHONE, PILLOW }

/**
 * Full render input for one frame. Immutable snapshot; swapped atomically by the director.
 * All fields are normalized (unit space) so the renderer is resolution-independent.
 */
data class CharacterPose(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val offsetY: Float = 0f,      // fraction of height; negative = up
    val offsetX: Float = 0f,      // for peek slide
    val rotation: Float = 0f,     // degrees
    val lean: Float = 0f,         // extra body tilt in degrees (idle drift)
    val alpha: Float = 1f,
    val lookX: Float = 0f,        // -1..1 pupil offset
    val lookY: Float = 0f,
    val blink: Float = 0f,        // 0 open, 1 closed
    val eyeLid: Float = 0f,       // 0..1 droopy eyelids (sleepy/judgmental)
    val mouth: MouthShape = MouthShape.SMALL_SMILE,
    val mouthOpen: Float = 0f,
    val browRaise: Float = 0f,    // -1 furrowed .. 1 raised
    val armLeft: Float = 0f,      // -1 down .. 1 raised
    val armRight: Float = 0f,
    val tailWag: Float = 0f,      // phase for wagging
    val headTilt: Float = 0f,     // degrees
    val acting: Acting = Acting.IDLE,
    val prop: Prop = Prop.NONE,
    val effects: Effects = Effects(),
    val accessory: Accessory = Accessory.NONE,
    val sleepBreath: Float = 0f,  // slow breath cycle 0..1
    val peekFraction: Float = 0f, // 0 fully visible .. 1 fully hidden (edge clip)
) {
    companion object {
        val hidden = CharacterPose(alpha = 0f)
    }
}

enum class Entrance { BOUNCE, FALL, SLIDE, POP, PORTAL, PEEK_IN }
enum class Exit { HOP_AWAY, SINK, PUFF, SLIDE_OUT, TUCK_AND_RUN }
