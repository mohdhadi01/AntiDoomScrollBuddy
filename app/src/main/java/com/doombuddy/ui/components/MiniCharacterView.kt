package com.doombuddy.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.doombuddy.R
import com.doombuddy.character.CharacterDefinition
import com.doombuddy.character.CharacterPose
import com.doombuddy.character.MouthShape
import com.doombuddy.character.characterDraw
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
    private val texture = TextureView(context).apply {
        // TextureView rejects background drawables, including transparent colours.
        isOpaque = false
    }
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
                                actionIndex = (actionIndex + 1) % animator.animationCount
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
        actionIndex = (actionIndex + 1 + level.coerceIn(0, 2)) % animationCount
        animationStartNanos = 0L
    }

    private fun actionClip(animationCount: Int): Int {
        return actionIndex % animationCount
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
    val modelAsset = definition.modelAsset
    if (modelAsset == null) {
        MiniCharacterPortrait(definition, modifier)
        return
    }
    var failed by remember(modelAsset) { mutableStateOf(false) }
    if (!useLive3d) {
        MiniCharacterPortrait(definition, modifier)
    } else {
        androidx.compose.foundation.layout.Box(modifier) {
            // Keep the authored portrait behind the GL surface. It remains visible while the
            // GPU surface is warming up and becomes the guaranteed fallback if Filament fails.
            MiniCharacterPortrait(definition, Modifier.fillMaxSize())
            if (!failed) {
                key(modelAsset) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { context ->
                            MiniCharacterView(context, modelAsset) { failed = true }
                        },
                        update = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
fun MiniCharacterPortrait(definition: CharacterDefinition, modifier: Modifier = Modifier) {
    val name = definition.portraitName
    // Keep direct resource references here. Dynamic getIdentifier() lookups are invisible to
    // release resource shrinking and can leave every portrait blank in the production APK.
    val resId = remember(name) { portraitResourceId(name) }

    // Resource names are metadata and may be stale after a resource split or an asset
    // rename. Never leave a roster slot blank just because the optional portrait is absent.
    if (resId != 0) {
        Image(
            painter = painterResource(resId),
            contentDescription = definition.displayName,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
        return
    }

    ProceduralCharacterFallback(definition, modifier)
}

private fun portraitResourceId(name: String?): Int = when (name) {
    "fenn_portrait" -> R.drawable.fenn_portrait
    else -> 0
}

@Composable
private fun ProceduralCharacterFallback(
    definition: CharacterDefinition,
    modifier: Modifier,
) {
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
}
