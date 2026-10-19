package com.doombuddy.character

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Native 2.5D toy-character renderer.
 *
 * This deliberately does not draw blob bodies. Each launch buddy is a small staged
 * performer with a head, clothing, articulated arms/legs, footwear, material lighting,
 * and a prop. The same draw path is used in the picker and the real overlay.
 *
 * The renderer clamps theatrical transforms to the measured stage. Android overlay
 * windows are wrap-content surfaces; allowing an animation to translate outside its
 * measured bounds is the cause of the old half-rendered entrance/exit bugs.
 */
private data class RenderCache(
    val body: Brush,
    val bodyShadow: Color,
    val skin: Brush,
    val skinShadow: Color,
    val stageShadow: Brush,
    val outline: Color,
    val highlight: Color,
)

fun Modifier.characterDraw(definition: CharacterDefinition, pose: () -> CharacterPose): Modifier =
    drawWithCache {
        val cache = RenderCache(
            body = Brush.verticalGradient(
                listOf(definition.palette.bodyTop.lighten(0.18f), definition.palette.bodyTop, definition.palette.bodyBottom.darken(0.12f)),
            ),
            bodyShadow = definition.palette.bodyBottom.darken(0.38f),
            skin = Brush.radialGradient(
                listOf(definition.palette.belly.lighten(0.08f), definition.palette.belly, definition.palette.belly.darken(0.18f)),
            ),
            skinShadow = definition.palette.belly.darken(0.32f),
            stageShadow = Brush.radialGradient(listOf(definition.palette.shadow, Color.Transparent)),
            outline = definition.palette.pupil.copy(alpha = 0.48f),
            highlight = Color.White.copy(alpha = 0.52f),
        )
        onDrawBehind { drawToyCharacter(definition, pose(), cache) }
    }

private fun DrawScope.drawToyCharacter(definition: CharacterDefinition, pose: CharacterPose, cache: RenderCache) {
    val s = min(size.width, size.height)
    val base = Offset(size.width / 2f, size.height * 0.88f)
    val safeScaleX = pose.scaleX.coerceIn(0.84f, 1.12f)
    val safeScaleY = pose.scaleY.coerceIn(0.84f, 1.12f)
    val safeRotation = pose.rotation.coerceIn(-12f, 12f)
    val safeX = (pose.offsetX - pose.peekFraction * 0.12f).coerceIn(-0.16f, 0.16f) * s
    val safeY = pose.offsetY.coerceIn(-0.16f, 0.12f) * s
    val alpha = pose.alpha.coerceIn(0f, 1f)
    if (alpha <= 0.01f) return

    drawOval(
        brush = cache.stageShadow,
        topLeft = Offset(base.x - s * 0.30f, base.y - s * 0.025f),
        size = Size(s * 0.60f, s * 0.09f),
        alpha = alpha * (1f - (-safeY / s).coerceIn(0f, 0.5f)),
    )

    translate(safeX, safeY) {
        scale(safeScaleX, safeScaleY, pivot = base) {
            rotate(safeRotation + pose.lean.coerceIn(-5f, 5f), pivot = base) {
                drawBackActivity(definition, pose, base, s, alpha)
                drawLegs(definition, pose, base, s, cache, alpha)
                drawTorso(definition, base, s, cache, alpha)
                drawArms(definition, pose, base, s, cache, alpha)
                drawHead(definition, pose, base, s, cache, alpha)
                drawFrontActivity(definition, pose, base, s, alpha)
                drawEffects(pose, base, s, alpha)
            }
        }
    }
}

private fun DrawScope.drawBackActivity(definition: CharacterDefinition, pose: CharacterPose, base: Offset, s: Float, alpha: Float) {
    when (definition.species) {
        Species.HUMANOID -> {
            drawRoundRect(
                definition.palette.bodyBottom.copy(alpha = alpha),
                topLeft = Offset(base.x - s * 0.27f, base.y - s * 0.69f),
                size = Size(s * 0.54f, s * 0.30f),
                cornerRadius = CornerRadius(s * 0.12f, s * 0.12f),
            )
        }
        Species.ASTRONAUT -> {
            drawCircle(Color(0xFF183E4A).copy(alpha = alpha), s * 0.205f, Offset(base.x, base.y - s * 0.70f))
            drawCircle(Color(0xFF77D6D6).copy(alpha = 0.26f * alpha), s * 0.165f, Offset(base.x - s * 0.035f, base.y - s * 0.735f))
            drawLine(definition.palette.accent.copy(alpha = alpha), Offset(base.x, base.y - s * 0.91f), Offset(base.x + s * 0.07f, base.y - s * 1.02f), s * 0.015f)
            drawCircle(definition.palette.accent.copy(alpha = alpha), s * 0.028f, Offset(base.x + s * 0.07f, base.y - s * 1.02f))
        }
        Species.SKATER -> {
            val boardY = base.y - s * 0.055f
            drawRoundRect(Color(0xFF1B2734).copy(alpha = alpha), Offset(base.x - s * 0.34f, boardY), Size(s * 0.68f, s * 0.035f), CornerRadius(s * 0.018f, s * 0.018f))
            drawCircle(Color(0xFFEDE8D8).copy(alpha = alpha), s * 0.035f, Offset(base.x - s * 0.23f, boardY + s * 0.045f))
            drawCircle(Color(0xFFEDE8D8).copy(alpha = alpha), s * 0.23f, Offset(base.x + s * 0.23f, boardY + s * 0.045f))
        }
        else -> Unit
    }
}

private fun DrawScope.drawLegs(definition: CharacterDefinition, pose: CharacterPose, base: Offset, s: Float, cache: RenderCache, alpha: Float) {
    val pants = when (definition.species) {
        Species.HUMANOID -> Color(0xFF302948)
        Species.ASTRONAUT -> Color(0xFFE7EEF0)
        Species.SKATER -> Color(0xFF274A68)
        else -> definition.palette.bodyBottom
    }
    val hipY = base.y - s * 0.22f
    val kneeY = base.y - s * 0.045f
    drawRoundRect(pants.copy(alpha = alpha), Offset(base.x - s * 0.16f, hipY), Size(s * 0.115f, s * 0.22f), CornerRadius(s * 0.055f, s * 0.055f))
    drawRoundRect(pants.copy(alpha = alpha), Offset(base.x + s * 0.045f, hipY), Size(s * 0.115f, s * 0.22f), CornerRadius(s * 0.055f, s * 0.055f))
    val shoe = when (definition.species) {
        Species.SKATER -> definition.palette.accent
        Species.ASTRONAUT -> Color(0xFF293942)
        else -> Color(0xFF252233)
    }
    drawRoundRect(shoe.copy(alpha = alpha), Offset(base.x - s * 0.19f, kneeY - s * 0.01f), Size(s * 0.19f, s * 0.075f), CornerRadius(s * 0.035f, s * 0.035f))
    drawRoundRect(shoe.copy(alpha = alpha), Offset(base.x + s * 0.01f, kneeY - s * 0.01f), Size(s * 0.19f, s * 0.075f), CornerRadius(s * 0.035f, s * 0.035f))
    drawLine(cache.highlight.copy(alpha = 0.22f * alpha), Offset(base.x - s * 0.16f, kneeY + s * 0.02f), Offset(base.x - s * 0.04f, kneeY + s * 0.02f), s * 0.012f)
}

private fun DrawScope.drawTorso(definition: CharacterDefinition, base: Offset, s: Float, cache: RenderCache, alpha: Float) {
    val torso = Rect(base.x - s * 0.285f, base.y - s * 0.56f, base.x + s * 0.285f, base.y - s * 0.19f)
    drawRoundRect(cache.body, torso.topLeft, torso.size, CornerRadius(s * 0.12f, s * 0.12f), alpha = alpha)
    drawRoundRect(cache.bodyShadow.copy(alpha = 0.28f * alpha), Offset(torso.left, torso.bottom - s * 0.08f), Size(torso.width, s * 0.08f), CornerRadius(s * 0.04f, s * 0.04f))
    drawRoundRect(cache.highlight.copy(alpha = 0.12f * alpha), Offset(torso.left + s * 0.045f, torso.top + s * 0.04f), Size(s * 0.07f, torso.height * 0.62f), CornerRadius(s * 0.03f, s * 0.03f))
    if (definition.species == Species.ASTRONAUT) {
        drawRoundRect(Color(0xFFB9D5D8).copy(alpha = alpha), Offset(base.x - s * 0.12f, base.y - s * 0.45f), Size(s * 0.24f, s * 0.14f), CornerRadius(s * 0.025f, s * 0.025f))
        drawCircle(definition.palette.accent.copy(alpha = alpha), s * 0.018f, Offset(base.x - s * 0.07f, base.y - s * 0.39f))
        drawCircle(Color(0xFFE16F6F).copy(alpha = alpha), s * 0.018f, Offset(base.x, base.y - s * 0.39f))
        drawCircle(Color(0xFF5DD4A5).copy(alpha = alpha), s * 0.018f, Offset(base.x + s * 0.07f, base.y - s * 0.39f))
    } else if (definition.species == Species.SKATER) {
        drawLine(definition.palette.accent.copy(alpha = 0.75f * alpha), Offset(base.x, base.y - s * 0.53f), Offset(base.x, base.y - s * 0.20f), s * 0.018f)
        drawRoundRect(Color(0xFFEBF4EC).copy(alpha = 0.9f * alpha), Offset(base.x - s * 0.09f, base.y - s * 0.39f), Size(s * 0.18f, s * 0.07f), CornerRadius(s * 0.02f, s * 0.02f))
    }
}

private fun DrawScope.drawArms(definition: CharacterDefinition, pose: CharacterPose, base: Offset, s: Float, cache: RenderCache, alpha: Float) {
    val shoulderY = base.y - s * 0.47f
    val leftShoulder = Offset(base.x - s * 0.25f, shoulderY)
    val rightShoulder = Offset(base.x + s * 0.25f, shoulderY)
    val leftHand = Offset(base.x - s * (0.40f - pose.armLeft.coerceIn(-1f, 1f) * 0.045f), shoulderY + s * (0.20f - pose.armLeft.coerceIn(-1f, 1f) * 0.28f))
    val rightHand = Offset(base.x + s * (0.40f - pose.armRight.coerceIn(-1f, 1f) * 0.045f), shoulderY + s * (0.20f - pose.armRight.coerceIn(-1f, 1f) * 0.28f))
    val sleeve = definition.palette.bodyBottom.darken(0.12f).copy(alpha = alpha)
    drawLine(cache.outline.copy(alpha = 0.28f * alpha), leftShoulder, leftHand, s * 0.105f)
    drawLine(cache.outline.copy(alpha = 0.28f * alpha), rightShoulder, rightHand, s * 0.105f)
    drawLine(sleeve, leftShoulder, leftHand, s * 0.075f)
    drawLine(sleeve, rightShoulder, rightHand, s * 0.075f)
    drawCircle(definition.palette.belly.copy(alpha = alpha), s * 0.055f, leftHand)
    drawCircle(definition.palette.belly.copy(alpha = alpha), s * 0.055f, rightHand)
}

private fun DrawScope.drawHead(definition: CharacterDefinition, pose: CharacterPose, base: Offset, s: Float, cache: RenderCache, alpha: Float) {
    val head = Offset(base.x, base.y - s * 0.70f)
    val radius = s * 0.175f
    drawCircle(cache.skin, radius, head, alpha = alpha)
    drawArc(cache.skinShadow.copy(alpha = 0.25f * alpha), 20f, 150f, false, Offset(head.x - radius, head.y - radius), Size(radius * 2f, radius * 2f), style = Stroke(s * 0.025f))

    when (definition.species) {
        Species.HUMANOID -> {
            drawRoundRect(Color(0xFF38304D).copy(alpha = alpha), Offset(head.x - radius * 1.03f, head.y - radius * 0.99f), Size(radius * 2.06f, radius * 0.42f), CornerRadius(radius * 0.18f, radius * 0.18f))
            drawRoundRect(Color(0xFF65588A).copy(alpha = alpha), Offset(head.x - radius * 0.70f, head.y - radius * 1.17f), Size(radius * 1.40f, radius * 0.35f), CornerRadius(radius * 0.16f, radius * 0.16f))
        }
        Species.ASTRONAUT -> {
            drawCircle(Color(0xFF1A4854).copy(alpha = alpha), radius * 1.15f, head)
            drawOval(Color(0xFF9FE3E1).copy(alpha = 0.46f * alpha), Offset(head.x - radius * 0.79f, head.y - radius * 0.62f), Size(radius * 1.58f, radius * 1.12f))
            drawArc(Color.White.copy(alpha = 0.42f * alpha), 204f, 88f, false, Offset(head.x - radius * 0.78f, head.y - radius * 0.70f), Size(radius * 1.45f, radius * 1.20f), style = Stroke(s * 0.018f))
        }
        Species.SKATER -> {
            val hair = Path().apply {
                moveTo(head.x - radius * 1.10f, head.y - radius * 0.18f)
                cubicTo(head.x - radius * 0.90f, head.y - radius * 1.18f, head.x + radius * 0.55f, head.y - radius * 1.27f, head.x + radius * 1.14f, head.y - radius * 0.32f)
                lineTo(head.x + radius * 0.75f, head.y - radius * 0.12f)
                cubicTo(head.x + radius * 0.20f, head.y - radius * 0.52f, head.x - radius * 0.32f, head.y - radius * 0.55f, head.x - radius * 1.10f, head.y - radius * 0.18f)
                close()
            }
            drawPath(hair, Color(0xFF263447).copy(alpha = alpha))
            drawLine(definition.palette.accent.copy(alpha = alpha), Offset(head.x - radius * 0.78f, head.y - radius * 0.60f), Offset(head.x + radius * 0.72f, head.y - radius * 0.72f), s * 0.026f)
        }
        else -> Unit
    }

    val eyeY = head.y - radius * 0.02f + pose.lookY.coerceIn(-1f, 1f) * radius * 0.05f
    val eyeDX = radius * 0.43f
    val blinkScale = (1f - pose.blink.coerceIn(0f, 1f) * 0.94f).coerceIn(0.12f, 1f)
    for (dir in listOf(-1f, 1f)) {
        val eye = Offset(head.x + dir * eyeDX, eyeY)
        drawOval(Color.White.copy(alpha = alpha), Offset(eye.x - radius * 0.22f, eye.y - radius * 0.18f * blinkScale), Size(radius * 0.44f, radius * 0.36f * blinkScale))
        drawCircle(definition.palette.pupil.copy(alpha = alpha), radius * 0.105f, Offset(eye.x + pose.lookX.coerceIn(-1f, 1f) * radius * 0.07f, eye.y + pose.lookY.coerceIn(-1f, 1f) * radius * 0.04f))
        drawCircle(Color.White.copy(alpha = 0.75f * alpha), radius * 0.035f, Offset(eye.x - radius * 0.04f, eye.y - radius * 0.045f))
    }
    val brow = definition.palette.pupil.copy(alpha = 0.72f * alpha)
    drawLine(brow, Offset(head.x - eyeDX - radius * 0.17f, eyeY - radius * 0.25f - pose.browRaise * radius * 0.05f), Offset(head.x - eyeDX + radius * 0.14f, eyeY - radius * 0.29f + pose.browRaise * radius * 0.05f), s * 0.014f)
    drawLine(brow, Offset(head.x + eyeDX - radius * 0.14f, eyeY - radius * 0.29f + pose.browRaise * radius * 0.05f), Offset(head.x + eyeDX + radius * 0.17f, eyeY - radius * 0.25f - pose.browRaise * radius * 0.05f), s * 0.014f)
    drawLine(cache.skinShadow.copy(alpha = 0.48f * alpha), Offset(head.x, eyeY + radius * 0.04f), Offset(head.x - radius * 0.03f, eyeY + radius * 0.16f), s * 0.012f)
    drawMouth(pose.mouth, pose.mouthOpen, Offset(head.x, head.y + radius * 0.38f), s, definition.palette.pupil.copy(alpha = alpha))
    drawOval(definition.palette.blush.copy(alpha = 0.25f * alpha), Offset(head.x - radius * 0.90f, head.y + radius * 0.25f), Size(radius * 0.32f, radius * 0.14f))
    drawOval(definition.palette.blush.copy(alpha = 0.25f * alpha), Offset(head.x + radius * 0.58f, head.y + radius * 0.25f), Size(radius * 0.32f, radius * 0.14f))
}

private fun DrawScope.drawMouth(mouth: MouthShape, open: Float, center: Offset, s: Float, color: Color) {
    when (mouth) {
        MouthShape.OPEN_O -> drawOval(color, Offset(center.x - s * (0.025f + open * 0.02f), center.y - s * 0.028f), Size(s * (0.05f + open * 0.04f), s * (0.07f + open * 0.035f)))
        MouthShape.FROWN -> drawArc(color, 20f, 140f, false, Offset(center.x - s * 0.08f, center.y - s * 0.015f), Size(s * 0.16f, s * 0.08f), style = Stroke(s * 0.014f))
        MouthShape.FLAT -> drawLine(color, Offset(center.x - s * 0.06f, center.y), Offset(center.x + s * 0.06f, center.y), s * 0.014f)
        else -> drawArc(color, 20f, 140f, false, Offset(center.x - s * 0.075f, center.y - s * 0.025f), Size(s * 0.15f, s * 0.075f), style = Stroke(s * 0.014f))
    }
}

private fun DrawScope.drawFrontActivity(definition: CharacterDefinition, pose: CharacterPose, base: Offset, s: Float, alpha: Float) {
    when (pose.prop) {
        Prop.TINY_PHONE -> {
            val hand = Offset(base.x + s * 0.35f, base.y - s * 0.45f)
            drawRoundRect(Color(0xFF19222C).copy(alpha = alpha), Offset(hand.x - s * 0.055f, hand.y - s * 0.10f), Size(s * 0.11f, s * 0.18f), CornerRadius(s * 0.018f, s * 0.018f))
            drawRoundRect(Color(0xFF68D6DF).copy(alpha = 0.72f * alpha), Offset(hand.x - s * 0.043f, hand.y - s * 0.075f), Size(s * 0.086f, s * 0.125f), CornerRadius(s * 0.012f, s * 0.012f))
        }
        Prop.POCKET_CLOCK -> {
            val clock = Offset(base.x + s * 0.34f, base.y - s * 0.43f)
            drawCircle(Color(0xFFF7E8B7).copy(alpha = alpha), s * 0.085f, clock)
            drawCircle(Color(0xFF51422A).copy(alpha = alpha), s * 0.085f, clock, style = Stroke(s * 0.012f))
            drawLine(Color(0xFF51422A).copy(alpha = alpha), clock, Offset(clock.x + s * 0.035f, clock.y - s * 0.043f), s * 0.012f)
            drawLine(Color(0xFF51422A).copy(alpha = alpha), clock, Offset(clock.x - s * 0.045f, clock.y + s * 0.015f), s * 0.010f)
        }
        Prop.POPCORN -> {
            val box = Offset(base.x - s * 0.39f, base.y - s * 0.42f)
            drawRoundRect(Color(0xFFD45449).copy(alpha = alpha), Offset(box.x - s * 0.07f, box.y - s * 0.04f), Size(s * 0.14f, s * 0.15f), CornerRadius(s * 0.02f, s * 0.02f))
            for (i in 0..2) drawCircle(Color(0xFFFFE4A6).copy(alpha = alpha), s * 0.035f, Offset(box.x - s * 0.05f + i * s * 0.05f, box.y - s * 0.09f - (i % 2) * s * 0.025f))
        }
        else -> Unit
    }
    if (definition.species == Species.SKATER) {
        drawLine(definition.palette.accent.copy(alpha = 0.7f * alpha), Offset(base.x - s * 0.18f, base.y - s * 0.05f), Offset(base.x + s * 0.12f, base.y - s * 0.02f), s * 0.018f)
    }
}

private fun DrawScope.drawEffects(pose: CharacterPose, base: Offset, s: Float, alpha: Float) {
    if (pose.effects.zzz) {
        for (i in 0..2) {
            val x = base.x + s * (0.28f + i * 0.07f)
            val y = base.y - s * (0.94f + i * 0.10f)
            drawZ(x, y, s * (0.04f + i * 0.012f), Color(0xFFB9D9E8).copy(alpha = alpha * (0.9f - i * 0.2f)))
        }
    }
    if (pose.effects.confetti || pose.effects.sparkle) {
        val colors = listOf(Color(0xFFFFD166), Color(0xFF58D6C7), Color(0xFFFF7B72), Color(0xFFBDA6FF))
        for (i in 0..7) {
            val a = i * 0.78f
            val x = base.x + cos(a) * s * 0.40f
            val y = base.y - s * 0.52f + sin(a) * s * 0.34f
            drawCircle(colors[i % colors.size].copy(alpha = alpha * 0.8f), s * 0.014f, Offset(x, y))
        }
    }
    if (pose.effects.dizzy) {
        for (i in 0..2) {
            val a = i * 2.1f
            drawCircle(Color(0xFFFFD166).copy(alpha = alpha), s * 0.025f, Offset(base.x + cos(a) * s * 0.25f, base.y - s * 0.92f + sin(a) * s * 0.04f))
        }
    }
}

private fun DrawScope.drawZ(x: Float, y: Float, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(x - r, y - r * 0.5f); lineTo(x + r, y - r * 0.5f); lineTo(x - r, y + r * 0.5f); lineTo(x + r, y + r * 0.5f)
    }
    drawPath(path, color, style = Stroke(r * 0.18f))
}

private fun Color.lighten(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

private fun Color.darken(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)
