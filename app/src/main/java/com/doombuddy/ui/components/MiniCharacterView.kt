package com.doombuddy.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.doombuddy.character.CharacterDefinition
import com.google.android.filament.Renderer
import com.google.android.filament.View
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer

/**
 * Lightweight 3D character stage backed by Filament and a local GLB.
 *
 * The GLB files are real rigged models, not screenshots. Filament owns the mesh, skinning,
 * materials, camera, and animation playback; the Compose overlay only owns the bounded stage
 * and interaction surface around it.
 */
class MiniCharacterView(
    context: Context,
    private val modelAsset: String,
    private val onFailure: (Throwable) -> Unit,
) : FrameLayout(context) {
    private val texture = TextureView(context).apply { isOpaque = false; setBackgroundColor(AndroidColor.TRANSPARENT) }
    private var viewer: ModelViewer? = null
    private var frameCallback: android.view.Choreographer.FrameCallback? = null
    private var actionIndex = 0
    private var animationStartNanos = 0L
    private var failureReported = false

    init {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { startViewer() }
    }

    private fun startViewer() {
        if (viewer != null || !isAttachedToWindow) return
        var next: ModelViewer? = null
        try {
            next = ModelViewer(texture)
            next.view.blendMode = View.BlendMode.TRANSLUCENT
            next.renderer.clearOptions = Renderer.ClearOptions().apply { clear = true; discard = true }
            context.assets.open("characters/$modelAsset").use { stream ->
                next.loadModelGlb(ByteBuffer.wrap(stream.readBytes()))
            }
            next.transformToUnitCube()
            viewer = next
            frameCallback = android.view.Choreographer.FrameCallback { frameTimeNanos ->
                try {
                    val activeViewer = viewer
                    activeViewer?.animator?.let { animator ->
                        if (animator.animationCount > 0) {
                            if (animationStartNanos == 0L) animationStartNanos = frameTimeNanos
                            if (frameTimeNanos - animationStartNanos > 4_500_000_000L) {
                                actionIndex = (actionIndex + 1) % 8
                                animationStartNanos = frameTimeNanos
                            }
                            val duration = animator.getAnimationDuration(actionClip(animator.animationCount)).coerceAtLeast(0.1f)
                            val elapsed = ((frameTimeNanos - animationStartNanos) / 1_000_000_000f) % duration
                            animator.applyAnimation(actionClip(animator.animationCount), elapsed)
                            animator.updateBoneMatrices()
                        }
                    }
                    activeViewer?.render(frameTimeNanos)
                    android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
                } catch (t: Throwable) {
                    fail(t)
                }
            }
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
        } catch (t: Throwable) {
            runCatching { next?.destroyModel() }
            fail(t)
        }
    }

    private fun fail(t: Throwable) {
        if (failureReported) return
        failureReported = true
        frameCallback?.let { android.view.Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
        runCatching { viewer?.destroyModel() }
        viewer = null
        post { onFailure(t) }
    }

    /** Move to a different real animation clip for a poke/hold or escalation event. */
    fun nudge(level: Int = 0) {
        val model = viewer ?: return
        val animationCount = model.animator?.animationCount ?: return
        if (animationCount == 0) return
        val plan = listOf(1, 11, 12, 7, 4, 2, 15, 22)
        actionIndex = (actionIndex + 1 + level.coerceIn(0, 2)) % plan.size
        animationStartNanos = 0L
    }

    private fun actionClip(animationCount: Int): Int {
        val plan = listOf(1, 11, 12, 7, 4, 2, 15, 22)
        return plan[actionIndex % plan.size].coerceIn(0, animationCount - 1)
    }

    override fun onDetachedFromWindow() {
        frameCallback?.let { android.view.Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
        runCatching { viewer?.destroyModel() }
        viewer = null
        super.onDetachedFromWindow()
    }
}

@Composable
fun MiniCharacter3D(
    definition: CharacterDefinition,
    modifier: Modifier = Modifier,
    moodLevel: Int = 0,
    onPoke: () -> Unit = {},
    useLive3d: Boolean = false,
) {
    val modelAsset = definition.modelAsset ?: return
    var failed by remember(modelAsset) { mutableStateOf(false) }
    if (failed || !useLive3d) {
        MiniCharacterPortrait(definition, modifier)
    } else {
        key(modelAsset) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { context ->
                    MiniCharacterView(context, modelAsset) { failed = true }
                },
                update = {},
                modifier = modifier,
            )
        }
    }
}

@Composable
fun MiniCharacterPortrait(definition: CharacterDefinition, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val name = definition.portraitName ?: return
    val resId = remember(name) {
        context.resources.getIdentifier(name, "drawable", context.packageName)
    }
    if (resId != 0) {
        Image(
            painter = painterResource(resId),
            contentDescription = definition.displayName,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}
