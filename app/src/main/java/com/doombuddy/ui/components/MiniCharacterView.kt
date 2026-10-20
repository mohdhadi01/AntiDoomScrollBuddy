package com.doombuddy.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.Choreographer
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.doombuddy.R
import com.doombuddy.character.CharacterDefinition
import com.doombuddy.character.CharacterClip
import com.doombuddy.character.CharacterPerformance
import com.doombuddy.character.CharacterPlaybackInput
import com.doombuddy.character.CharacterPlaybackPolicy
import com.doombuddy.character.CharacterPose
import com.doombuddy.character.MouthShape
import com.doombuddy.character.characterDraw
import com.google.android.filament.Renderer
import com.google.android.filament.View
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import kotlin.math.sin

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
    private val onReady: () -> Unit = {},
    private val onFailure: (Throwable) -> Unit,
) : FrameLayout(context) {
    private var texture: TextureView? = null
    private var viewer: ModelViewer? = null
    private var surfaceHelper: UiHelper? = null
    private var callbackPosted = false
    private var activeNanos = 0L
    private var lastFrameNanos = 0L
    private var playback = CharacterPlaybackPolicy()
    private var playbackInput = CharacterPlaybackInput()
    private var failureReported = false
    private var readyReported = false
    private var loggedGeneration = -1L
    internal val isFrameScheduled: Boolean get() = callbackPosted

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        callbackPosted = false
        if (canRender()) {
            try {
                // At most 30fps, including on high refresh rate displays. Active time pauses
                // while the window is hidden, rather than skipping an entrance on resume.
                if (lastFrameNanos == 0L || frameTimeNanos - lastFrameNanos >= 32_000_000L) {
                    if (lastFrameNanos != 0L) activeNanos += frameTimeNanos - lastFrameNanos
                    lastFrameNanos = frameTimeNanos
                    val activeViewer = viewer
                    val frame = playback.frame(activeNanos / 1_000_000L)
                    if (com.doombuddy.BuildConfig.DEBUG && loggedGeneration != frame.generation) {
                        loggedGeneration = frame.generation
                        Log.d(TAG, "$modelAsset ${frame.performance} at ${activeNanos / 1_000_000L}ms viewport=${activeViewer?.view?.viewport?.width}x${activeViewer?.view?.viewport?.height}")
                    }
                    activeViewer?.animator?.let { animator ->
                        frame.clipIndex?.let { animator.applyAnimation(it, frame.seconds) }
                        animator.updateBoneMatrices()
                    }
                    activeViewer?.render(frameTimeNanos)
                    val expectedRenderables = activeViewer?.asset?.renderableEntities?.size ?: 0
                    if (!readyReported && activeViewer != null && activeViewer.progress >= 1f && texture?.isAvailable == true &&
                        expectedRenderables > 0 && activeViewer.scene.renderableCount >= expectedRenderables) {
                        readyReported = true
                        onReady()
                    }
                }
                scheduleFrame()
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    init {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        addTexture()
    }

    private fun addTexture(): TextureView = TextureView(context).apply {
        // NEVER set a TextureView background (even a transparent color throws).
        isOpaque = false
        isClickable = false
        isFocusable = false
        texture = this
        addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (texture == null && !failureReported) {
            // Old children already received detach (and Filament destroyed its engine).
            // Removing them normally while this parent is attached would dispatch a
            // SECOND detach. Unlink without dispatch, then use a fresh texture/listener.
            detachAllViewsFromParent()
            addTexture()
        }
        post { startViewer() }
    }

    private fun startViewer() {
        if (failureReported || !isAttachedToWindow || windowVisibility != VISIBLE || !isShown) return
        if (width <= 0 || height <= 0) return // onSizeChanged retries after layout
        if (viewer != null) { scheduleFrame(); return }
        var engine: Engine? = null
        try {
            // Native library loading and the ModelViewer constructor can themselves fail.
            Utils.init()
            Gltfio.init()
            engine = Engine.create()
            val surface = texture ?: addTexture()
            // UiHelper otherwise resets TextureView to opaque when attaching its surface.
            val uiHelper = characterSurfaceHelper(width, height)
            surfaceHelper = uiHelper
            val next = ModelViewer(surface, engine, uiHelper)
            viewer = next
            next.view.blendMode = View.BlendMode.TRANSLUCENT
            // Invisible framing meshes must not cast a box-shaped shadow over the actor.
            engine.lightManager.setShadowCaster(engine.lightManager.getInstance(next.light), false)
            next.cameraFocalLength = 44f
            next.renderer.clearOptions = Renderer.ClearOptions().apply {
                clear = true
                discard = true
                clearColor = floatArrayOf(0f, 0f, 0f, 0f)
            }
            // A front-facing key light keeps expressions legible without an external HDRI.
            engine.lightManager.setDirection(engine.lightManager.getInstance(next.light), -0.4f, -0.6f, -1f)
            context.assets.open("characters/$modelAsset").use { stream ->
                next.loadModelGlb(ByteBuffer.wrap(stream.readBytes()))
            }
            check(next.asset != null) { "No renderable GLB asset: $modelAsset" }
            next.transformToUnitCube()
            val animator = next.animator
            val clips = if (animator == null) emptyList() else List(animator.animationCount) {
                CharacterClip(animator.getAnimationName(it), animator.getAnimationDuration(it))
            }
            check(clips.isNotEmpty()) { "No animation clips in $modelAsset" }
            Log.d(TAG, "Loaded $modelAsset with ${clips.size} performance clips")
            playback = CharacterPlaybackPolicy(clips).also { it.update(playbackInput, activeNanos / 1_000_000L) }
            scheduleFrame()
        } catch (t: Throwable) {
            // Once constructed, ModelViewer owns the engine and releases it on texture
            // detach. Before that ownership transfer, release our engine here.
            if (viewer == null) runCatching { engine?.destroy() }.onFailure {
                Log.w(TAG, "Failed to release partially initialized renderer", it)
            }
            fail(t)
        }
    }

    private fun fail(t: Throwable) {
        if (failureReported) return
        failureReported = true
        Log.w(TAG, "3D character '$modelAsset' failed; using animated procedural fallback", t)
        stopFrames()
        // Filament 1.51 ModelViewer installs a detach listener which destroys ALL native
        // resources, not just the model. Remove the texture exactly once to trigger it.
        texture?.let { surface ->
            runCatching { removeView(surface) }.onFailure { Log.w(TAG, "Renderer detach failed", it) }
        }
        texture = null
        viewer = null
        surfaceHelper = null
        post { onFailure(t) }
    }

    fun updatePlayback(input: CharacterPlaybackInput) {
        playbackInput = input.copy(moodLevel = input.moodLevel.coerceIn(0, 6))
        playback.update(playbackInput, activeNanos / 1_000_000L)
    }

    /** Retained imperative event API; repeated calls always restart a finite poke. */
    fun nudge(level: Int = playbackInput.moodLevel) {
        updatePlayback(playbackInput.copy(moodLevel = level, pokeToken = playbackInput.pokeToken + 1))
    }

    private fun canRender() = viewer != null && !failureReported && isAttachedToWindow && windowVisibility == VISIBLE && isShown

    private fun scheduleFrame() {
        if (canRender() && !callbackPosted) {
            callbackPosted = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    private fun stopFrames() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        callbackPosted = false
        lastFrameNanos = 0L
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) post { startViewer() } else stopFrames()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            surfaceHelper?.setDesiredSize(w, h)
            post { startViewer() }
        }
    }

    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // The framework may call this from the superclass constructor, before fields
        // exist; posting defers inspection until construction has completed.
        post { if (isShown) startViewer() else stopFrames() }
    }

    override fun onDetachedFromWindow() {
        stopFrames()
        // The texture's ModelViewer detach listener releases its engine. Never call
        // destroyModel here: child detach may already have destroyed the native engine.
        viewer = null
        texture = null
        surfaceHelper = null
        readyReported = false
        super.onDetachedFromWindow()
    }

    companion object { private const val TAG = "MiniCharacterView" }
}

/** Filament 1.51 uses desired dimensions when the TextureView is already available. */
internal fun characterSurfaceHelper(width: Int, height: Int): UiHelper {
    require(width > 0 && height > 0) { "Character surface must be measured before attachment" }
    return UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
        isOpaque = false
        setDesiredSize(width, height)
    }
}

@Composable
fun MiniCharacter3D(
    definition: CharacterDefinition,
    modifier: Modifier = Modifier,
    moodLevel: Int = 0,
    onPoke: () -> Unit = {},
    useLive3d: Boolean = false,
    performance: CharacterPerformance? = null,
    pokeToken: Long = 0,
    holding: Boolean = false,
    dismissing: Boolean = false,
    celebration: Boolean = false,
    interactive: Boolean = true,
    previewToken: Long = 0,
) {
    val modelAsset = definition.modelAsset
    var localPokes by remember(definition.id) { mutableStateOf(0L) }
    val latestPoke by rememberUpdatedState(onPoke)
    val stageModifier = if (interactive) modifier.pointerInput(definition.id) {
        detectTapGestures { localPokes++; latestPoke() }
    } else modifier
    val input = CharacterPlaybackInput(moodLevel, performance, pokeToken + localPokes, holding, dismissing, celebration, previewToken)
    var failed by remember(modelAsset) { mutableStateOf(false) }
    var ready by remember(modelAsset) { mutableStateOf(false) }
    if (!useLive3d || modelAsset == null || failed) {
        ProceduralCharacterFallback(definition, stageModifier, input)
    } else {
        Box(stageModifier) {
            if (!ready) ProceduralCharacterFallback(definition, Modifier.fillMaxSize(), input)
            key(modelAsset) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        try {
                            MiniCharacterView(context, modelAsset, onFailure = { failed = true }, onReady = { ready = true })
                        } catch (t: Throwable) {
                            Log.w("MiniCharacterView", "Cannot construct '$modelAsset'; using animated fallback", t)
                            failed = true
                            android.view.View(context)
                        }
                    },
                    update = { (it as? MiniCharacterView)?.updatePlayback(input) },
                    modifier = Modifier.fillMaxSize(),
                )
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

    ProceduralCharacterFallback(definition, modifier, animated = false)
}

private fun portraitResourceId(name: String?): Int = when (name) {
    "fenn_portrait" -> R.drawable.fenn_portrait
    else -> 0
}

@Composable
internal fun ProceduralCharacterFallback(
    definition: CharacterDefinition,
    modifier: Modifier,
    input: CharacterPlaybackInput = CharacterPlaybackInput(),
    animated: Boolean = true,
) {
    if (!animated) {
        Box(modifier.characterDraw(definition) {
            CharacterPose(alpha = 1f, accessory = definition.accessory, mouth = MouthShape.SMILE, browRaise = 0.3f)
        })
        return
    }
    val policy = remember(definition.id) { CharacterPlaybackPolicy() }
    val latestInput by rememberUpdatedState(input)
    var pose by remember(definition.id) { mutableStateOf(CharacterPose(alpha = 1f, accessory = definition.accessory)) }
    LaunchedEffect(policy) {
        var start = 0L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                if (last == 0L || now - last >= 32_000_000L) {
                    last = now
                    val time = (now - start) / 1_000_000L
                    policy.update(latestInput, time)
                    val frame = policy.frame(time)
                    val wave = sin(frame.seconds * 5f)
                    val hop = sin(frame.progress * Math.PI).toFloat().coerceAtLeast(0f)
                    val act = frame.performance
                    pose = CharacterPose(
                        alpha = if (act == CharacterPerformance.DISMISS) 1f - frame.progress else 1f,
                        accessory = definition.accessory,
                        mouth = when (act) {
                            CharacterPerformance.MELTDOWN, CharacterPerformance.HOLD -> MouthShape.OPEN_O
                            CharacterPerformance.CELEBRATE -> MouthShape.GRIN
                            else -> MouthShape.SMILE
                        },
                        browRaise = if (act == CharacterPerformance.HOLD) 1f else 0.3f,
                        scaleY = 1f + wave * 0.025f,
                        offsetY = when (act) {
                            CharacterPerformance.POKE, CharacterPerformance.ENTRANCE, CharacterPerformance.CELEBRATE -> -0.22f * hop
                            CharacterPerformance.DISMISS -> frame.progress * 0.25f
                            else -> 0f
                        },
                        rotation = when (act) {
                            CharacterPerformance.HOLD, CharacterPerformance.MELTDOWN -> wave * 7f
                            CharacterPerformance.ESCALATE -> wave * 5f
                            CharacterPerformance.NOTICE, CharacterPerformance.WAITING -> wave * 3f
                            else -> 0f
                        },
                    )
                }
            }
        }
    }
    Box(modifier = modifier.characterDraw(definition) { pose })
}
