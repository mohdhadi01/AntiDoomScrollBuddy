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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Procedural 2.5D character renderer, v2.
 *
 * Dimensional recipe per docs/ANIMATION_SYSTEM.md:
 *  - 3-stop body gradient (lit top → core → shaded bottom) + top rim light
 *  - contact shadow + soft ground shadow (jumps lift and fade it)
 *  - bounce light from the ground on the body's underside
 *  - species life: cat tail wag, frog throat bubble, ghost hover trail, duck wing flap,
 *    robot status LED, blob jiggle
 *  - acting props (popcorn, pocket clock, blanket, pillow, tiny phone) + particles
 *    (Zzz, confetti, dizzy stars, crumbs)
 *  - peek mode: buddy clipped behind the screen edge, eyes only
 *
 * All drawing is normalized to canvas size; paths/brushes are cached per size, pose
 * updates only re-issue draw commands (no recomposition, no per-frame allocation).
 */

private class Cache(
    val bodyPath: Path,
    val bodyBrush: Brush,
    val rimBrush: Brush,
    val bellyRect: Rect,
    val bellyBrush: Brush,
    val aoBrush: Brush,
    val shadowBrush: Brush,
    val highlightBrush: Brush,
    val glossBrush: Brush,
    val outlineColor: Color,
    val coreSpecular: Offset,
    val isGhost: Boolean,
    val floats: Boolean,
    val species: Species,
)

fun Modifier.characterDraw(definition: CharacterDefinition, pose: () -> CharacterPose): Modifier =
    drawWithCache {
        val cache = buildCache(definition, size)
        onDrawBehind {
            drawBuddy(definition, pose(), cache)
        }
    }

private fun buildCache(definition: CharacterDefinition, size: Size): Cache {
    val s = size.minDimension
    val p = definition.palette
    val prop = definition.proportions
    val anchorY = size.height * 0.94f
    val bodyRect = Rect(
        left = size.width / 2f - prop.bodyWidth * s / 2f,
        top = anchorY - prop.bodyHeight * s,
        right = size.width / 2f + prop.bodyWidth * s / 2f,
        bottom = anchorY,
    )
    return Cache(
        bodyPath = buildBodyPath(definition.species, bodyRect, prop.cornerRadius),
        // 3-stop dimensional gradient: light top, saturated core, deep shaded base.
        bodyBrush = Brush.verticalGradient(
            listOf(
                p.bodyTop.lighten(0.22f),
                p.bodyTop,
                p.bodyBottom.darken(0.18f),
            ),
            startY = bodyRect.top,
            endY = bodyRect.bottom,
        ),
        rimBrush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
            startY = bodyRect.top,
            endY = bodyRect.top + bodyRect.height * 0.3f,
        ),
        bellyRect = Rect(
            left = bodyRect.center.x - bodyRect.width * 0.27f,
            top = bodyRect.bottom - bodyRect.height * 0.46f,
            right = bodyRect.center.x + bodyRect.width * 0.27f,
            bottom = bodyRect.bottom - bodyRect.height * 0.04f,
        ),
        bellyBrush = Brush.verticalGradient(
            listOf(p.belly.copy(alpha = 0.98f), p.belly.copy(alpha = 0.5f)),
        ),
        // Contact shadow where the body meets the ground (bounce-light occluder).
        aoBrush = Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f)),
            startY = bodyRect.bottom - bodyRect.height * 0.3f,
            endY = bodyRect.bottom,
        ),
        shadowBrush = Brush.radialGradient(
            listOf(p.shadow, Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.985f),
            radius = s * 0.4f,
        ),
        highlightBrush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
        ),
        // Spherical light: offset radial highlight = the "premium 3D" read on every body.
        glossBrush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.06f), Color.Transparent),
            center = Offset(bodyRect.left + bodyRect.width * 0.3f, bodyRect.top + bodyRect.height * 0.22f),
            radius = bodyRect.width * 0.85f,
        ),
        // Soft (not pure-black) edge definition — the "premium vinyl toy" line weight
        // instead of a flat cartoon/sticker outline.
        outlineColor = p.bodyBottom.darken(0.42f).copy(alpha = 0.5f),
        // A tight, bright secondary specular dot layered over the broad gloss — this is
        // what reads as "glossy/3D" rather than "flat" at a glance (same trick used on
        // glossy app icons/toy renders: one broad soft highlight + one crisp hot spot).
        coreSpecular = Offset(bodyRect.left + bodyRect.width * 0.28f, bodyRect.top + bodyRect.height * 0.18f),
        isGhost = definition.species == Species.GHOST,
        floats = definition.species == Species.GHOST || definition.species == Species.ALIEN ||
            definition.species == Species.ORB || definition.species == Species.SHADOW,
        species = definition.species,
    )
}

private fun Color.lighten(f: Float) = Color(
    red = ((red * (1 - f) + f)).coerceIn(0f, 1f),
    green = ((green * (1 - f) + f)).coerceIn(0f, 1f),
    blue = ((blue * (1 - f) + f)).coerceIn(0f, 1f),
    alpha = alpha,
)

private fun Color.darken(f: Float) = Color(
    red = (red * (1 - f)).coerceIn(0f, 1f),
    green = (green * (1 - f)).coerceIn(0f, 1f),
    blue = (blue * (1 - f)).coerceIn(0f, 1f),
    alpha = alpha,
)

private fun DrawScope.drawBuddy(definition: CharacterDefinition, pose: CharacterPose, cache: Cache) {
    val s = size.minDimension
    val p = definition.palette
    val anchor = Offset(size.width / 2f, size.height * 0.93f)

    // Ground shadow — lighter and smaller the higher the buddy is.
    val lift = (-pose.offsetY).coerceIn(0f, 1f)
    val shadowScale = 1f - lift * 0.4f
    scale(shadowScale, shadowScale, pivot = anchor) {
        drawCircle(
            brush = cache.shadowBrush,
            radius = s * 0.32f,
            center = Offset(anchor.x, anchor.y + s * 0.01f),
            alpha = (0.95f - lift * 0.55f) * pose.alpha,
        )
    }

    if (pose.alpha <= 0.01f) return

    // PEEK: the buddy slides toward the window's outer edge — at peekFraction=1 only
    // a sliver (eyes) remains visible; at 0 it is fully in view.
    if (pose.peekFraction > 0.01f) {
        clipRect(
            left = size.width * pose.peekFraction * 0.62f,
            top = 0f,
            right = size.width,
            bottom = size.height,
        ) {
            drawBodyAndFriends(definition, pose, cache, anchor, s, p)
        }
    } else {
        drawBodyAndFriends(definition, pose, cache, anchor, s, p)
    }
}

private fun DrawScope.drawBodyAndFriends(
    definition: CharacterDefinition,
    pose: CharacterPose,
    cache: Cache,
    anchor: Offset,
    s: Float,
    p: Palette,
) {
    translate(left = pose.offsetX * s, top = pose.offsetY * s) {
        drawProps(pose, anchor, s, behind = true)

        scale(pose.scaleX, pose.scaleY, pivot = anchor) {
            rotate(pose.rotation + pose.lean, pivot = anchor) {
                val alpha = if (cache.isGhost) 0.94f else 1f

                // Back-of-body species bits (ears, wings) before the body fill.
                drawSpeciesBack(definition.species, anchor, s, p, pose, alpha)

                drawPath(cache.bodyPath, brush = cache.bodyBrush, alpha = alpha)

                // Contact shading + belly + rim light.
                clipPath(cache.bodyPath) {
                    drawRect(brush = cache.aoBrush, topLeft = Offset(0f, 0f), size = size)
                    drawOval(
                        cache.bellyBrush,
                        topLeft = Offset(cache.bellyRect.left, cache.bellyRect.top),
                        size = cache.bellyRect.size,
                        alpha = alpha * 0.92f,
                    )
                    // Spherical 3D gloss layer (skip for the flat-dark shadow body).
                    if (definition.species != Species.SHADOW) {
                        val b = cache.bodyPath.getBounds()
                        drawRect(
                            brush = cache.glossBrush,
                            topLeft = Offset(b.left, b.top),
                            size = Size(b.width, b.height),
                            alpha = alpha,
                        )
                    }
                    val bounds = cache.bodyPath.getBounds()
                    drawRect(
                        brush = cache.rimBrush,
                        topLeft = Offset(bounds.left, bounds.top),
                        size = Size(bounds.width, bounds.height * 0.34f),
                        alpha = alpha * 0.6f,
                    )
                    // Crisp secondary specular hot-spot — small and bright, layered over the
                    // broad soft gloss above. This single addition is most of what separates a
                    // "flat sticker" read from a "glossy premium toy" read.
                    if (definition.species != Species.SHADOW) {
                        drawCircle(Color.White.copy(alpha = 0.75f * alpha), s * 0.034f, cache.coreSpecular)
                    }
                }

                // Soft outline along the silhouette — drawn outside the clip so it traces the
                // exact exterior edge, UNDER face/limb details added next.
                drawPath(cache.bodyPath, color = cache.outlineColor, style = Stroke(width = s * 0.01f), alpha = alpha)

                drawSpeciesFront(definition, pose, cache, anchor, s, p, alpha)
                drawFace(definition, pose, cache, anchor, s, p)
                drawAccessoryLayer(definition.accessory, pose.accessory, anchor, s, p, pose)
                drawEffects(pose, anchor, s)
                drawProps(pose, anchor, s, behind = false)
            }
        }
    }
}

private fun buildBodyPath(species: Species, r: Rect, cornerRadius: Float): Path = Path().apply {
    when (species) {
        Species.ORB -> addOval(
            Rect(
                left = r.center.x - min(r.width, r.height) / 2f,
                top = r.bottom - min(r.width, r.height),
                right = r.center.x + min(r.width, r.height) / 2f,
                bottom = r.bottom,
            )
        )
        Species.SHADOW -> {
            // Soft shadow blob: dome + three-bump wavy base + a side drip.
            val top = r.top + r.height * 0.12f
            moveTo(r.center.x, top)
            cubicTo(r.left, top - r.height * 0.1f, r.left, r.bottom - r.height * 0.2f, r.left, r.bottom - r.height * 0.14f)
            val bumpW = r.width / 3f
            for (i in 0..2) {
                val x0 = r.left + bumpW * i
                val dir = if (i % 2 == 0) 1f else -1f
                cubicTo(
                    x0 + bumpW * 0.25f, r.bottom + dir * r.height * 0.05f,
                    x0 + bumpW * 0.75f, r.bottom - dir * r.height * 0.05f,
                    x0 + bumpW, r.bottom - r.height * 0.04f,
                )
            }
            cubicTo(r.right, r.bottom - r.height * 0.2f, r.right, top - r.height * 0.1f, r.center.x, top)
            close()
        }
        Species.FROG -> addOval(r)
        Species.BLOB -> {
            // The Melt: asymmetric soft blob with a drip on the lower-left.
            val w = r.width; val h = r.height; val l = r.left; val t = r.top
            moveTo(r.center.x, t)
            cubicTo(l + w * 0.94f, t - h * 0.06f, l + w * 1.06f, t + h * 0.52f, r.center.x + w * 0.46f, t + h * 0.78f)
            cubicTo(l + w * 0.42f, t + h * 0.98f, l + w * 0.22f, t + h * 1.04f, l + w * 0.10f, t + h * 0.94f)
            // the drip
            cubicTo(l + w * 0.02f, t + h * 1.02f, l + w * 0.01f, t + h * 1.16f, l + w * 0.05f, t + h * 1.18f)
            cubicTo(l + w * 0.09f, t + h * 1.20f, l + w * 0.06f, t + h * 1.06f, l - w * 0.02f, t + h * 0.94f)
            cubicTo(l - w * 0.08f, t + h * 0.78f, l - w * 0.02f, t + h * 0.28f, r.center.x, t)
            close()
        }
        Species.ALIEN -> {
            // Zobo: egg silhouette, wide base, narrow dome.
            val insetTop = r.width * 0.22f
            moveTo(r.center.x, r.top)
            cubicTo(r.right - insetTop, r.top, r.right, r.top + r.height * 0.42f, r.right, r.top + r.height * 0.62f)
            cubicTo(r.right, r.top + r.height * 0.86f, r.center.x + r.width * 0.5f, r.bottom, r.center.x, r.bottom)
            cubicTo(r.center.x - r.width * 0.5f, r.bottom, r.left, r.top + r.height * 0.86f, r.left, r.top + r.height * 0.62f)
            cubicTo(r.left, r.top + r.height * 0.42f, r.left + insetTop, r.top, r.center.x, r.top)
            close()
        }
        Species.PANDA -> {
            val radius = r.width * 0.42f
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = r,
                    topLeft = CornerRadius(radius, radius),
                    topRight = CornerRadius(radius, radius),
                    bottomRight = CornerRadius(radius * 0.88f, radius * 0.88f),
                    bottomLeft = CornerRadius(radius * 0.88f, radius * 0.88f),
                )
            )
        }
        Species.CAT, Species.DUCK -> {
            val radius = r.width * cornerRadius
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = r,
                    topLeft = CornerRadius(radius, radius),
                    topRight = CornerRadius(radius, radius),
                    bottomRight = CornerRadius(radius * 0.9f, radius * 0.9f),
                    bottomLeft = CornerRadius(radius * 0.9f, radius * 0.9f),
                )
            )
        }
        Species.ROBOT -> {
            val radius = r.width * 0.2f
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = r,
                    topLeft = CornerRadius(radius, radius),
                    topRight = CornerRadius(radius, radius),
                    bottomRight = CornerRadius(radius, radius),
                    bottomLeft = CornerRadius(radius, radius),
                )
            )
        }
        Species.GHOST -> {
            val top = r.top + r.height * 0.16f
            moveTo(r.center.x, top)
            cubicTo(r.left, top - r.height * 0.1f, r.left, r.bottom - r.height * 0.22f, r.left, r.bottom - r.height * 0.14f)
            val bumpW = r.width / 3f
            for (i in 0..2) {
                val x0 = r.left + bumpW * i
                val dir = if (i % 2 == 0) 1f else -1f
                cubicTo(
                    x0 + bumpW * 0.25f, r.bottom + dir * r.height * 0.05f,
                    x0 + bumpW * 0.75f, r.bottom - dir * r.height * 0.05f,
                    x0 + bumpW, r.bottom - r.height * 0.04f,
                )
            }
            cubicTo(r.right, r.bottom - r.height * 0.22f, r.right, top - r.height * 0.1f, r.center.x, top)
            close()
        }
    }
}

private fun DrawScope.drawSpeciesBack(species: Species, anchor: Offset, s: Float, p: Palette, pose: CharacterPose, alpha: Float) {
    when (species) {
        Species.ALIEN -> {
            // Two antennae; bulbs pulse with breath and tilt with head motion.
            val topY = anchor.y - s * 0.88f
            val sway = sin(pose.tailWag * 0.9f) * s * 0.02f
            for (dir in listOf(-1f, 1f)) {
                val baseX = anchor.x + dir * s * 0.07f
                val tipX = baseX + dir * s * 0.045f + sway * dir
                val stalk = Path().apply {
                    moveTo(baseX, topY + s * 0.14f)
                    quadraticTo(baseX + dir * s * 0.02f, topY + s * 0.02f, tipX, topY)
                }
                drawPath(stalk, p.bodyBottom, style = Stroke(width = s * 0.014f))
                drawCircle(
                    p.accent.copy(alpha = 0.7f + pose.sleepBreath * 0.3f),
                    s * 0.024f,
                    Offset(tipX, topY - s * 0.008f),
                )
            }
        }
        Species.PANDA -> {
            // Black ear caps.
            val earY = anchor.y - s * 0.68f
            drawCircle(Color(0xFF23262E), s * 0.105f, Offset(anchor.x - s * 0.24f, earY))
            drawCircle(Color(0xFF23262E), s * 0.105f, Offset(anchor.x + s * 0.24f, earY))
            drawCircle(Color(0xFF3A3E48), s * 0.05f, Offset(anchor.x - s * 0.26f, earY - s * 0.015f))
            drawCircle(Color(0xFF3A3E48), s * 0.05f, Offset(anchor.x + s * 0.26f, earY - s * 0.015f))
        }
        Species.CAT -> {
            drawCatEars(anchor, s, p)
            // Tail: animated curve, wags with pose.tailWag.
            val wag = sin(pose.tailWag) * s * 0.05f
            val baseX = anchor.x + s * 0.3f
            val baseY = anchor.y - s * 0.16f
            val tail = Path().apply {
                moveTo(baseX, baseY)
                cubicTo(
                    baseX + s * 0.16f, baseY + wag * 0.4f,
                    baseX + s * 0.2f, baseY - s * 0.2f + wag,
                    baseX + s * 0.1f + wag, baseY - s * 0.34f,
                )
            }
            drawPath(tail, p.bodyBottom.copy(alpha = alpha), style = Stroke(width = s * 0.045f))
        }
        Species.DUCK -> {
            // Wing stubs, flap with mouthOpen-ish phase.
            val flap = sin(pose.tailWag * 0.7f) * 0.5f + 0.5f
            val wingY = anchor.y - s * 0.36f + flap * s * 0.02f
            drawOval(
                p.bodyBottom.copy(alpha = alpha),
                topLeft = Offset(anchor.x - s * 0.34f, wingY),
                size = Size(s * 0.13f, s * 0.2f - flap * s * 0.02f),
            )
            drawOval(
                p.bodyBottom.copy(alpha = alpha),
                topLeft = Offset(anchor.x + s * 0.21f, wingY),
                size = Size(s * 0.13f, s * 0.2f - flap * s * 0.02f),
            )
        }
        Species.BLOB -> {
            // Jiggle: tiny side lobes lag the bob phase.
            val jig = sin(pose.tailWag * 0.8f) * s * 0.02f
            drawOval(
                p.bodyTop.copy(alpha = 0.7f * alpha),
                topLeft = Offset(anchor.x - s * 0.46f, anchor.y - s * 0.4f + jig),
                size = Size(s * 0.1f, s * 0.16f),
            )
            drawOval(
                p.bodyTop.copy(alpha = 0.7f * alpha),
                topLeft = Offset(anchor.x + s * 0.36f, anchor.y - s * 0.4f - jig),
                size = Size(s * 0.1f, s * 0.16f),
            )
        }
        else -> Unit
    }
}

private fun DrawScope.drawSpeciesFront(
    definition: CharacterDefinition,
    pose: CharacterPose,
    cache: Cache,
    anchor: Offset,
    s: Float,
    p: Palette,
    alpha: Float,
) {
    when (definition.species) {
        Species.ORB -> {
            // Secondary crisp specular dot on top of the broad gloss.
            drawCircle(Color.White.copy(alpha = 0.55f), s * 0.026f, Offset(anchor.x - s * 0.15f, anchor.y - s * 0.58f))
        }
        Species.SHADOW -> {
            // Glowing eye halos behind the face + drifting edge wisps.
            val eyeY = anchor.y - s * 0.5f
            val eyeDX = s * 0.15f
            for (dir in listOf(-1f, 1f)) {
                drawCircle(p.accent.copy(alpha = 0.16f), s * 0.135f, Offset(anchor.x + dir * eyeDX, eyeY))
            }
            val wisp = sin(pose.tailWag * 1.1f) * s * 0.02f
            drawOval(
                p.accent.copy(alpha = 0.08f),
                topLeft = Offset(anchor.x - s * 0.3f, anchor.y - s * 0.12f + wisp),
                size = Size(s * 0.6f, s * 0.06f),
            )
        }
        Species.ALIEN -> {
            // Third eye: a small glowing dot between/below the main eyes.
            drawCircle(
                p.accent.copy(alpha = 0.55f + pose.sleepBreath * 0.25f),
                s * 0.016f,
                Offset(anchor.x, anchor.y - s * 0.40f),
            )
        }
        Species.FROG -> {
            // Eye bumps + throat bubble that pulses with breath.
            val eyeR = s * 0.105f
            val eyeY = anchor.y - s * 0.64f
            drawCircle(p.bodyTop.copy(alpha = alpha), eyeR, Offset(anchor.x - s * 0.185f, eyeY))
            drawCircle(p.bodyTop.copy(alpha = alpha), eyeR, Offset(anchor.x + s * 0.185f, eyeY))
            val throat = 1f + pose.sleepBreath * 0.25f
            drawOval(
                p.belly.copy(alpha = 0.85f * alpha),
                topLeft = Offset(anchor.x - s * 0.09f, anchor.y - s * 0.3f - (throat - 1f) * s * 0.04f),
                size = Size(s * 0.18f, s * 0.075f * throat),
            )
            drawMouthArc(anchor.x, anchor.y - s * 0.31f, s * 0.15f, s * 0.05f, p.pupil.copy(alpha = alpha), Stroke(width = s * 0.015f))
        }
        Species.ROBOT -> {
            // Face screen + blinking status LED.
            drawRoundRect(
                color = p.belly,
                topLeft = Offset(anchor.x - s * 0.26f, anchor.y - s * 0.58f),
                size = Size(s * 0.52f, s * 0.38f),
                cornerRadius = CornerRadius(s * 0.055f, s * 0.055f),
            )
            val ledOn = (pose.tailWag % (2 * PI)).let { it < PI } // slow blink
            drawCircle(
                p.accent.copy(alpha = if (ledOn) 0.95f else 0.35f),
                s * 0.018f,
                Offset(anchor.x + s * 0.2f, anchor.y - s * 0.55f),
            )
        }
        Species.GHOST -> {
            // Hover wisps under the tail.
            val wisp = sin(pose.tailWag * 1.3f) * s * 0.02f
            drawOval(
                p.bodyTop.copy(alpha = 0.4f * alpha),
                topLeft = Offset(anchor.x - s * 0.14f, anchor.y - s * 0.05f + wisp),
                size = Size(s * 0.28f, s * 0.045f),
            )
        }
        Species.DUCK -> {
            // Beak (mouthOpen parts it).
            val beakY = anchor.y - s * 0.36f
            val open = pose.mouthOpen * s * 0.05f
            drawRoundRect(
                color = p.accent,
                topLeft = Offset(anchor.x - s * 0.115f, beakY - s * 0.032f - open * 0.5f),
                size = Size(s * 0.23f, s * 0.045f + open * 0.5f),
                cornerRadius = CornerRadius(s * 0.024f, s * 0.024f),
            )
            drawRoundRect(
                color = p.accent.darken(0.2f),
                topLeft = Offset(anchor.x - s * 0.1f, beakY + s * 0.012f + open * 0.5f),
                size = Size(s * 0.2f, s * 0.032f),
                cornerRadius = CornerRadius(s * 0.02f, s * 0.02f),
            )
        }
        else -> Unit
    }

    // Feet + arms (shared rig; ghost & alien float). Panda wears black mitts.
    val limbColor = if (definition.species == Species.PANDA) Color(0xFF23262E) else p.bodyBottom
    if (!cache.floats) {
        val footY = anchor.y - s * 0.012f
        val footW = s * 0.125f
        val footH = s * 0.05f
        drawRoundRect(limbColor.copy(alpha = alpha), topLeft = Offset(anchor.x - footW - s * 0.09f, footY - footH / 2), size = Size(footW, footH), cornerRadius = CornerRadius(footH / 2, footH / 2))
        drawRoundRect(limbColor.copy(alpha = alpha), topLeft = Offset(anchor.x + s * 0.09f, footY - footH / 2), size = Size(footW, footH), cornerRadius = CornerRadius(footH / 2, footH / 2))
    }

    // Arms rotate up/down by pose targets; popcorn/phone/wave/celebrate drive them.
    val armW = s * 0.048f
    val armH = s * 0.14f
    val pivotL = Offset(anchor.x - s * 0.36f, anchor.y - s * 0.36f)
    val pivotR = Offset(anchor.x + s * 0.36f, anchor.y - s * 0.36f)
    rotate(pose.armLeft * 70f, pivot = pivotL) {
        drawRoundRect(
            limbColor.copy(alpha = alpha),
            topLeft = Offset(pivotL.x - armW / 2, pivotL.y - armH * 0.15f),
            size = Size(armW, armH),
            cornerRadius = CornerRadius(armW / 2, armW / 2),
        )
    }
    rotate(-pose.armRight * 70f, pivot = pivotR) {
        drawRoundRect(
            limbColor.copy(alpha = alpha),
            topLeft = Offset(pivotR.x - armW / 2, pivotR.y - armH * 0.15f),
            size = Size(armW, armH),
            cornerRadius = CornerRadius(armW / 2, armW / 2),
        )
    }
}

private fun DrawScope.drawCatEars(anchor: Offset, s: Float, p: Palette) {
    val bodyTopY = anchor.y - s * 0.72f
    val ear = { cx: Float, dir: Float ->
        val path = Path().apply {
            moveTo(cx - s * 0.095f, bodyTopY + s * 0.08f)
            lineTo(cx + dir * s * 0.02f, bodyTopY - s * 0.1f)
            lineTo(cx + s * 0.095f, bodyTopY + s * 0.08f)
            close()
        }
        drawPath(path, p.bodyTop)
        val inner = Path().apply {
            moveTo(cx - s * 0.05f, bodyTopY + s * 0.06f)
            lineTo(cx + dir * s * 0.014f, bodyTopY - s * 0.042f)
            lineTo(cx + s * 0.05f, bodyTopY + s * 0.06f)
            close()
        }
        drawPath(inner, p.blush.copy(alpha = 0.85f))
    }
    ear(anchor.x - s * 0.19f, -1f)
    ear(anchor.x + s * 0.19f, 1f)
}

private fun DrawScope.drawFace(
    definition: CharacterDefinition,
    pose: CharacterPose,
    cache: Cache,
    anchor: Offset,
    s: Float,
    p: Palette,
) {
    val isRobot = definition.species == Species.ROBOT
    val eyeY = anchor.y - s * 0.5f + pose.headTilt * s * 0.0008f
    val eyeDX = s * 0.15f
    val eyeR = s * 0.072f
    val sclera = if (isRobot) Color(0xFF111920) else Color.White
    val pupilColor = if (isRobot) p.accent else p.pupil
    val blinkScale = (1f - pose.blink * 0.94f).coerceIn(0.06f, 1f)
    val lid = pose.eyeLid.coerceIn(0f, 1f)

    rotate(pose.headTilt, pivot = Offset(anchor.x, eyeY)) {
        for (dir in listOf(-1f, 1f)) {
            val cx = anchor.x + dir * eyeDX
            scale(1f, blinkScale, pivot = Offset(cx, eyeY)) {
                drawCircle(sclera.copy(alpha = if (cache.isGhost) 0.95f else 1f), eyeR, Offset(cx, eyeY))
                if (blinkScale > 0.25f) {
                    val pr = if (isRobot) eyeR * 0.36f else eyeR * 0.52f
                    drawCircle(pupilColor, pr, Offset(cx + pose.lookX * eyeR * 0.3f, eyeY + pose.lookY * eyeR * 0.28f))
                    if (!isRobot) {
                        val pupilCx = cx + pose.lookX * eyeR * 0.3f
                        val pupilCy = eyeY + pose.lookY * eyeR * 0.28f
                        drawCircle(
                            Color.White.copy(alpha = 0.92f),
                            pr * 0.3f,
                            Offset(pupilCx - pr * 0.32f, pupilCy - pr * 0.34f),
                        )
                        // Secondary, smaller catchlight on the opposite side — the two-highlight
                        // "glass marble" eye is what reads as glossy/premium instead of flat.
                        drawCircle(
                            Color.White.copy(alpha = 0.55f),
                            pr * 0.14f,
                            Offset(pupilCx + pr * 0.4f, pupilCy + pr * 0.38f),
                        )
                    }
                }
            }
            // Droopy eyelid (sleepy / over-it): skin-colored lid easing down.
            if (lid > 0.02f && blinkScale > 0.25f) {
                clipRect(left = cx - eyeR, top = eyeY - eyeR, right = cx + eyeR, bottom = eyeY + eyeR) {
                    drawRect(
                        p.bodyTop.copy(alpha = lid),
                        topLeft = Offset(cx - eyeR, eyeY - eyeR),
                        size = Size(eyeR * 2f, eyeR * 2f * lid * 0.72f),
                    )
                }
            }
        }

        // Brows (panda: thick black slashes, always stern).
        val browY = eyeY - eyeR * 1.5f - pose.browRaise * s * 0.014f
        val browColor = if (definition.species == Species.PANDA) Color(0xFF23262E) else p.pupil.copy(alpha = 0.7f)
        val browWidth = if (definition.species == Species.PANDA) s * 0.022f else s * 0.013f
        drawLine(
            browColor,
            Offset(anchor.x - eyeDX - eyeR * 0.66f, browY + (1f - pose.browRaise) * s * 0.011f),
            Offset(anchor.x - eyeDX + eyeR * 0.66f, browY - (1f - pose.browRaise) * s * 0.011f),
            browWidth,
        )
        drawLine(
            browColor,
            Offset(anchor.x + eyeDX - eyeR * 0.66f, browY - (1f - pose.browRaise) * s * 0.011f),
            Offset(anchor.x + eyeDX + eyeR * 0.66f, browY + (1f - pose.browRaise) * s * 0.011f),
            browWidth,
        )

        // Blush.
        if (!isRobot) {
            drawOval(
                p.blush.copy(alpha = 0.55f),
                topLeft = Offset(anchor.x - eyeDX - eyeR * 1.75f, eyeY + eyeR * 1.05f),
                size = Size(eyeR * 1.4f, eyeR * 0.75f),
            )
            drawOval(
                p.blush.copy(alpha = 0.55f),
                topLeft = Offset(anchor.x + eyeDX + eyeR * 0.35f, eyeY + eyeR * 1.05f),
                size = Size(eyeR * 1.4f, eyeR * 0.75f),
            )
        }

        // Mouth (species with dedicated hardware skip it).
        if (definition.species != Species.FROG && definition.species != Species.DUCK &&
            definition.species != Species.CAT && definition.species != Species.PANDA
        ) {
            drawMouthShape(pose.mouth, pose.mouthOpen, anchor.x, anchor.y - s * 0.345f, s, pupilColor)
        }
    }
}

private fun DrawScope.drawMouthShape(mouth: MouthShape, open: Float, cx: Float, cy: Float, s: Float, color: Color) {
    when (mouth) {
        MouthShape.SMALL_SMILE -> drawMouthArc(cx, cy, s * 0.085f, s * 0.04f, color, Stroke(width = s * 0.015f))
        MouthShape.SMILE -> drawMouthArc(cx, cy, s * 0.125f, s * 0.065f, color, Stroke(width = s * 0.017f))
        MouthShape.GRIN -> {
            val path = Path().apply {
                moveTo(cx - s * 0.125f, cy - s * 0.008f)
                quadraticTo(cx, cy + s * 0.105f, cx + s * 0.125f, cy - s * 0.008f)
                close()
            }
            drawPath(path, color)
        }
        MouthShape.OPEN_O -> {
            val r = s * (0.032f + open * 0.04f)
            drawOval(color, topLeft = Offset(cx - r, cy - r * 1.2f), size = Size(r * 2, r * 2.4f))
        }
        MouthShape.FLAT -> drawLine(color, Offset(cx - s * 0.085f, cy), Offset(cx + s * 0.085f, cy), s * 0.015f)
        MouthShape.WAVE -> {
            val path = Path().apply {
                moveTo(cx - s * 0.105f, cy)
                var i = 0
                while (i < 3) {
                    val x0 = cx - s * 0.105f + (s * 0.07f) * i
                    cubicTo(
                        x0 + s * 0.023f, cy + (if (i % 2 == 0) -1 else 1) * s * 0.026f,
                        x0 + s * 0.048f, cy + (if (i % 2 == 0) -1 else 1) * s * 0.026f,
                        x0 + s * 0.07f, cy,
                    )
                    i++
                }
            }
            drawPath(path, color, style = Stroke(width = s * 0.015f))
        }
        MouthShape.SMIRK -> {
            rotate(-8f, pivot = Offset(cx, cy)) {
                drawMouthArc(cx, cy, s * 0.105f, s * 0.055f, color, Stroke(width = s * 0.015f))
            }
        }
        MouthShape.FROWN -> drawMouthArc(cx, cy + s * 0.032f, s * 0.095f, -s * 0.048f, color, Stroke(width = s * 0.015f))
    }
}

private fun DrawScope.drawMouthArc(cx: Float, cy: Float, halfWidth: Float, depth: Float, color: Color, style: Stroke) {
    val path = Path().apply {
        moveTo(cx - halfWidth, cy)
        quadraticTo(cx, cy + depth * 2f, cx + halfWidth, cy)
    }
    drawPath(path, color, style = style)
}

private fun DrawScope.drawAccessoryLayer(builtIn: Accessory, unlocked: Accessory, anchor: Offset, s: Float, p: Palette, pose: CharacterPose) {
    val top = anchor.y - s * 0.8f
    when (builtIn) {
        Accessory.LEAF -> {
            val sway = sin(pose.tailWag * 0.6f) * s * 0.012f
            val stem = Path().apply {
                moveTo(anchor.x + sway * 0.3f, top + s * 0.055f)
                quadraticTo(anchor.x + s * 0.02f + sway, top - s * 0.012f, anchor.x + s * 0.05f + sway, top - s * 0.03f)
            }
            drawPath(stem, Color(0xFF3E7C36), style = Stroke(width = s * 0.012f))
            val leaf = Path().apply {
                moveTo(anchor.x + s * 0.05f + sway, top - s * 0.03f)
                quadraticTo(anchor.x + s * 0.13f + sway, top - s * 0.09f, anchor.x + s * 0.165f + sway, top - s * 0.02f)
                quadraticTo(anchor.x + s * 0.1f + sway, top + s * 0.012f, anchor.x + s * 0.05f + sway, top - s * 0.03f)
            }
            drawPath(leaf, Color(0xFF57A84E))
        }
        Accessory.BOW -> {
            val bowColor = p.accent
            val bx = anchor.x + s * 0.17f
            val by = top + s * 0.03f
            val loop = { dir: Float ->
                Path().apply {
                    moveTo(bx, by)
                    quadraticTo(bx + dir * s * 0.085f, by - s * 0.05f, bx + dir * s * 0.085f, by + s * 0.014f)
                    quadraticTo(bx + dir * s * 0.05f, by + s * 0.02f, bx, by)
                }
            }
            drawPath(loop(-1f), bowColor)
            drawPath(loop(1f), bowColor)
            drawCircle(bowColor, s * 0.02f, Offset(bx, by))
        }
        Accessory.ANTENNAE -> Unit // drawn in drawSpeciesBack (needs breath phase)
        Accessory.ANTENNA -> {
            val wobble = sin(pose.tailWag * 0.5f) * s * 0.014f
            drawLine(p.pupil.copy(alpha = 0.6f), Offset(anchor.x, top + s * 0.035f), Offset(anchor.x + s * 0.03f + wobble, top - s * 0.06f), s * 0.012f)
            drawCircle(p.accent, s * 0.026f, Offset(anchor.x + s * 0.03f + wobble, top - s * 0.075f))
        }
        Accessory.HALO -> {
            val bob = sin(pose.tailWag * 0.4f) * s * 0.01f
            drawOval(
                Color(0xFFFFE58A),
                topLeft = Offset(anchor.x - s * 0.115f, top - s * 0.05f + bob),
                size = Size(s * 0.23f, s * 0.05f),
                style = Stroke(width = s * 0.017f),
            )
        }
        Accessory.HAIR_SWIRL -> {
            val sway = sin(pose.tailWag * 0.8f) * s * 0.02f
            val path = Path().apply {
                moveTo(anchor.x + sway * 0.2f, top + s * 0.02f)
                cubicTo(anchor.x - s * 0.06f + sway, top - s * 0.08f, anchor.x + s * 0.09f + sway, top - s * 0.1f, anchor.x + s * 0.05f + sway, top - s * 0.02f)
            }
            drawPath(path, p.accent, style = Stroke(width = s * 0.015f))
        }
        Accessory.CROWN -> {
            val path = Path().apply {
                moveTo(anchor.x - s * 0.1f, top + s * 0.04f)
                lineTo(anchor.x - s * 0.1f, top - s * 0.028f)
                lineTo(anchor.x - s * 0.05f, top + s * 0.005f)
                lineTo(anchor.x, top - s * 0.045f)
                lineTo(anchor.x + s * 0.05f, top + s * 0.005f)
                lineTo(anchor.x + s * 0.1f, top - s * 0.028f)
                lineTo(anchor.x + s * 0.1f, top + s * 0.04f)
                close()
            }
            drawPath(path, Color(0xFFFFD54A))
        }
        else -> Unit
    }

    when (unlocked) {
        Accessory.HAT -> {
            drawRoundRect(Color(0xFF3A3F58), topLeft = Offset(anchor.x - s * 0.155f, top - s * 0.045f), size = Size(s * 0.31f, s * 0.085f), cornerRadius = CornerRadius(s * 0.02f, s * 0.02f))
            drawRoundRect(Color(0xFF3A3F58), topLeft = Offset(anchor.x - s * 0.215f, top + s * 0.033f), size = Size(s * 0.43f, s * 0.024f), cornerRadius = CornerRadius(s * 0.012f, s * 0.012f))
        }
        Accessory.SUNGLASSES -> {
            val y = anchor.y - s * 0.5f
            drawRoundRect(Color(0xFF23272E), topLeft = Offset(anchor.x - s * 0.2f, y - s * 0.034f), size = Size(s * 0.4f, s * 0.07f), cornerRadius = CornerRadius(s * 0.024f, s * 0.024f))
        }
        Accessory.SCARF -> {
            drawRoundRect(Color(0xFFE5654F), topLeft = Offset(anchor.x - s * 0.23f, anchor.y - s * 0.315f), size = Size(s * 0.46f, s * 0.065f), cornerRadius = CornerRadius(s * 0.03f, s * 0.03f))
            drawRoundRect(Color(0xFFE5654F), topLeft = Offset(anchor.x + s * 0.08f, anchor.y - s * 0.265f), size = Size(s * 0.075f, s * 0.15f), cornerRadius = CornerRadius(s * 0.024f, s * 0.024f))
        }
        Accessory.GLASSES -> {
            val y = anchor.y - s * 0.5f
            drawCircle(Color(0x33526B), s * 0.08f, Offset(anchor.x - s * 0.15f, y), style = Stroke(s * 0.013f))
            drawCircle(Color(0x33526B), s * 0.08f, Offset(anchor.x + s * 0.15f, y), style = Stroke(s * 0.013f))
            drawLine(Color(0xFF33526B), Offset(anchor.x - s * 0.07f, y), Offset(anchor.x + s * 0.07f, y), s * 0.01f)
        }
        else -> Unit
    }
}

// ---------------------------------------------------------------- particles & props

private fun DrawScope.drawEffects(pose: CharacterPose, anchor: Offset, s: Float) {
    val t = (System.nanoTime() / 1_000_000L) % 4_000L / 4_000f // 0..1 looping phase
    if (pose.effects.zzz) {
        for (i in 0..2) {
            val phase = (t + i / 3f) % 1f
            val x = anchor.x + s * 0.18f + i * s * 0.07f + phase * s * 0.05f
            val y = anchor.y - s * 0.72f - phase * s * 0.22f
            drawZzz(x, y, s * (0.05f + i * 0.015f), Color(0xFFB9C4D0), alpha = (1f - phase))
        }
    }
    if (pose.effects.confetti) {
        val colors = listOf(Color(0xFFA3E635), Color(0xFFFB7185), Color(0xFFFBBF24), Color(0xFF60A5FA))
        for (i in 0..9) {
            val phase = (t * 2f + i / 10f) % 1f
            val ang = i * 2.399f + t * 4f
            val x = anchor.x + cos(ang).toFloat() * s * 0.4f * (0.4f + phase)
            val y = anchor.y - s * 0.4f - phase * s * 0.5f + sin(ang * 2).toFloat() * s * 0.05f
            drawRect(colors[i % 4], topLeft = Offset(x, y), size = Size(s * 0.025f, s * 0.01f))
        }
    }
    if (pose.effects.dizzy) {
        for (i in 0..2) {
            val a = t * 2f * PI.toFloat() + i * 2f * PI.toFloat() / 3f
            val x = anchor.x + cos(a) * s * 0.2f
            val y = anchor.y - s * 0.8f + sin(a) * s * 0.03f
            drawStar(x, y, s * 0.028f, Color(0xFFFBBF24))
        }
    }
    if (pose.effects.sparkle) {
        for (i in 0..3) {
            val phase = (t + i / 4f) % 1f
            val ang = i * 1.7f
            val x = anchor.x + cos(ang).toFloat() * s * 0.33f
            val y = anchor.y - s * 0.55f + sin(ang * 3).toFloat() * s * 0.25f
            drawStar(x, y, s * 0.02f * (1f - phase), Color.White.copy(alpha = 1f - phase))
        }
    }
    if (pose.effects.snackCrumbs) {
        for (i in 0..4) {
            val phase = (t * 1.5f + i / 5f) % 1f
            val x = anchor.x + s * (0.05f + i * 0.02f)
            val y = anchor.y - s * 0.35f + phase * s * 0.2f
            drawCircle(Color(0xFFF5DEB3), s * 0.011f, Offset(x, y), alpha = 1f - phase)
        }
    }
}

/** Tiny "Z" glyphs drawn as three strokes (no text stack in the overlay pipeline). */
private fun DrawScope.drawZzz(x: Float, y: Float, size: Float, color: Color, alpha: Float) {
    val stroke = size * 0.22f
    drawLine(color.copy(alpha = alpha), Offset(x, y), Offset(x + size, y), stroke)
    drawLine(color.copy(alpha = alpha), Offset(x + size, y), Offset(x, y + size), stroke)
    drawLine(color.copy(alpha = alpha), Offset(x, y + size), Offset(x + size, y + size), stroke)
}

private fun DrawScope.drawStar(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    for (i in 0..9) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val a = PI / 2 + i * PI / 5
        val x = cx + cos(a).toFloat() * rad
        val y = cy - sin(a).toFloat() * rad
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawProps(pose: CharacterPose, anchor: Offset, s: Float, behind: Boolean) {
    val t = (System.nanoTime() / 1_000_000L) % 2_000L / 2_000f
    when (pose.prop) {
        Prop.COUCH -> if (behind) {
            val cy = anchor.y - s * 0.06f
            drawRoundRect(Color(0xFF8B6FB8), topLeft = Offset(anchor.x - s * 0.44f, cy - s * 0.13f), size = Size(s * 0.88f, s * 0.19f), cornerRadius = CornerRadius(s * 0.055f, s * 0.055f))
            drawRoundRect(Color(0xFF9F83CC), topLeft = Offset(anchor.x - s * 0.44f, cy - s * 0.25f), size = Size(s * 0.11f, s * 0.29f), cornerRadius = CornerRadius(s * 0.05f, s * 0.05f))
            drawRoundRect(Color(0xFF9F83CC), topLeft = Offset(anchor.x + s * 0.33f, cy - s * 0.25f), size = Size(s * 0.11f, s * 0.29f), cornerRadius = CornerRadius(s * 0.05f, s * 0.05f))
        }
        Prop.BLANKET -> if (behind) {
            drawRoundRect(
                Color(0xFF7FB3D9),
                topLeft = Offset(anchor.x - s * 0.34f, anchor.y - s * 0.3f),
                size = Size(s * 0.68f, s * 0.26f),
                cornerRadius = CornerRadius(s * 0.04f, s * 0.04f),
            )
            // stitch pattern
            for (i in 0..3) {
                drawCircle(
                    Color.White.copy(alpha = 0.25f),
                    s * 0.012f,
                    Offset(anchor.x - s * 0.24f + i * s * 0.16f, anchor.y - s * 0.19f),
                )
            }
        }
        Prop.PILLOW -> if (behind) {
            drawRoundRect(
                Color(0xFFEDE7DC),
                topLeft = Offset(anchor.x - s * 0.4f, anchor.y - s * 0.17f),
                size = Size(s * 0.26f, s * 0.16f),
                cornerRadius = CornerRadius(s * 0.05f, s * 0.05f),
            )
        }
        Prop.POCKET_CLOCK -> if (!behind) {
            val cx = anchor.x + s * 0.32f
            val cy = anchor.y - s * 0.42f
            drawCircle(Color(0xFFF7F4EC), s * 0.1f, Offset(cx, cy))
            drawCircle(Color(0xFF2A2E37), s * 0.1f, Offset(cx, cy), style = Stroke(s * 0.014f))
            // hand spins faster the longer the session; pose.prop set during CLOCK_TAP
            val ang = t * 2f * PI.toFloat()
            drawLine(Color(0xFF2A2E37), Offset(cx, cy), Offset(cx + sin(ang) * s * 0.06f, cy - cos(ang) * s * 0.06f), s * 0.012f)
        }
        Prop.POPCORN -> if (!behind) {
            val bx = anchor.x - s * 0.4f
            val by = anchor.y - s * 0.42f
            // box
            val path = Path().apply {
                moveTo(bx - s * 0.07f, by - s * 0.12f)
                lineTo(bx + s * 0.07f, by - s * 0.12f)
                lineTo(bx + s * 0.055f, by + s * 0.1f)
                lineTo(bx - s * 0.055f, by + s * 0.1f)
                close()
            }
            drawPath(path, Color(0xFFE5654F))
            drawRect(Color.White.copy(alpha = 0.85f), topLeft = Offset(bx - s * 0.035f, by - s * 0.08f), size = Size(s * 0.07f, s * 0.03f))
            // kernels
            drawCircle(Color(0xFFFFF3D6), s * 0.02f, Offset(bx - s * 0.03f, by - s * 0.13f))
            drawCircle(Color(0xFFFFF3D6), s * 0.024f, Offset(bx + s * 0.02f, by - s * 0.135f))
            drawCircle(Color(0xFFFFF3D6), s * 0.018f, Offset(bx - s * 0.005f, by - s * 0.15f))
        }
        Prop.TINY_PHONE -> if (!behind) {
            val px = anchor.x + s * 0.3f
            val py = anchor.y - s * 0.4f
            drawRoundRect(Color(0xFF2A2E37), topLeft = Offset(px, py), size = Size(s * 0.09f, s * 0.15f), cornerRadius = CornerRadius(s * 0.02f, s * 0.02f))
            drawRoundRect(Color(0xFF60A5FA).copy(alpha = 0.8f), topLeft = Offset(px + s * 0.008f, py + s * 0.012f), size = Size(s * 0.074f, s * 0.11f), cornerRadius = CornerRadius(s * 0.012f, s * 0.012f))
        }
        Prop.HOURGLASS -> if (behind) {
            val cy = anchor.y - s * 0.16f
            val path = Path().apply {
                moveTo(anchor.x - s * 0.08f, cy - s * 0.13f)
                lineTo(anchor.x + s * 0.08f, cy - s * 0.13f)
                lineTo(anchor.x + s * 0.018f, cy)
                lineTo(anchor.x + s * 0.08f, cy + s * 0.13f)
                lineTo(anchor.x - s * 0.08f, cy + s * 0.13f)
                lineTo(anchor.x - s * 0.018f, cy)
                close()
            }
            drawPath(path, Color(0x66BFD8E8))
            drawPath(path, Color(0xFF7FA8C9), style = Stroke(width = s * 0.012f))
            val sand = 0.4f + 0.6f * t
            drawPath(
                Path().apply {
                    moveTo(anchor.x - s * 0.05f * (1.4f - sand), cy + s * 0.11f)
                    lineTo(anchor.x + s * 0.05f * (1.4f - sand), cy + s * 0.11f)
                    lineTo(anchor.x, cy + s * 0.02f)
                    close()
                },
                Color(0xFFE8C97A),
            )
        }
        else -> Unit
    }
}
