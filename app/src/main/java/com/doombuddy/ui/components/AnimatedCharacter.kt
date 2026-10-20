package com.doombuddy.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.doombuddy.character.CharacterDefinition
import com.doombuddy.character.CharacterPerformance
import com.doombuddy.character.CharacterDirector
import com.doombuddy.character.Entrance
import com.doombuddy.character.MouthShape
import com.doombuddy.character.CharacterPose
import com.doombuddy.character.characterDraw
import kotlinx.coroutines.launch

/**
 * In-app animated character (onboarding picker, home screen, preview studio).
 * Owns a [CharacterDirector] lifecycle-tied to this composable: idle loops run only
 * while composed; taps produce a comedic reaction. Pose updates drive only the draw
 * phase — recomposition happens only on definition/mood changes.
 */
@Composable
fun AnimatedCharacter(
    definition: CharacterDefinition,
    modifier: Modifier = Modifier,
    entrance: Entrance = Entrance.BOUNCE,
    moodLevel: Int = 0,
    interactive: Boolean = true,
    animated: Boolean = true,
    useLive3d: Boolean = false,
    onPoke: () -> Unit = {},
    performance: CharacterPerformance? = null,
    celebration: Boolean = false,
) {
    if (definition.modelAsset != null) {
        if (!animated) {
            MiniCharacterPortrait(definition, modifier)
        } else {
            MiniCharacter3D(
                definition, modifier, moodLevel, onPoke,
                useLive3d = useLive3d, performance = performance,
                celebration = celebration, interactive = interactive,
            )
        }
        return
    }
    // Explicit previews use the same finite performance policy even without a GLB.
    if (animated && (performance != null || celebration)) {
        MiniCharacter3D(
            definition, modifier, moodLevel, onPoke,
            performance = performance, celebration = celebration, interactive = interactive,
        )
        return
    }
    // Static mode: fixed cute pose, ZERO animation loops. Pickers/rosters render many
    // buddies at once — one frame loop per card is real battery/jank cost.
    if (!animated) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.characterDraw(definition) {
                CharacterPose(
                    alpha = 1f,
                    accessory = definition.accessory,
                    mouth = MouthShape.SMILE,
                    browRaise = 0.3f,
                    scaleY = 1f,
                )
            },
        )
        return
    }
    val scope = rememberCoroutineScope()
    val director = remember(definition.id) { CharacterDirector(scope) }

    LaunchedEffect(definition.id, entrance) {
        director.enter(entrance, accessory = definition.accessory)
        director.setMood(moodLevel.coerceIn(0, 6))
    }
    LaunchedEffect(moodLevel) {
        director.setMood(moodLevel.coerceIn(0, 6))
    }

    val drawModifier = modifier.characterDraw(definition) { director.pose }

    androidx.compose.foundation.layout.Box(
        modifier = drawModifier.pointerInput(definition.id, interactive) {
            if (!interactive) return@pointerInput
            detectTapGestures { offset ->
                onPoke()
                scope.launch {
                    director.setLook(
                        x = (offset.x / size.width - 0.5f) * 2f,
                        y = (offset.y / size.height - 0.5f) * 2f,
                    )
                    director.react(CharacterDirector.Reaction.JUMP)
                }
            }
        },
    )
}
