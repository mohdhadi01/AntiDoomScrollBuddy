package com.doombuddy.ui.components

import android.view.TextureView
import android.app.Activity
import android.os.Looper
import android.view.View
import com.doombuddy.character.CharacterPlaybackInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MiniCharacterViewTest {
    @Test
    fun `surface helper preserves transparency and nonzero initial viewport`() {
        val helper = characterSurfaceHelper(551, 400)
        assertFalse(helper.isOpaque)
        assertEquals(551, helper.desiredWidth)
        assertEquals(400, helper.desiredHeight)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero sized surface is rejected before native initialization`() {
        characterSurfaceHelper(0, 400)
    }

    @Test
    fun `construction creates a transparent texture without a background drawable`() {
        // Do not attach the view: this tests startup construction without native rendering.
        val view = MiniCharacterView(ApplicationProvider.getApplicationContext(), "unused.glb") {
            throw AssertionError("Construction should not invoke the rendering fallback", it)
        }

        assertEquals(1, view.childCount)
        val texture = view.getChildAt(0) as TextureView
        assertFalse(texture.isOpaque)
        assertNull(texture.background)
        assertFalse(texture.isClickable)
        assertFalse(view.isFrameScheduled)
    }

    @Test
    fun `state updates before attach are safe and do not schedule rendering`() {
        val view = MiniCharacterView(ApplicationProvider.getApplicationContext(), "unused.glb") {}
        view.updatePlayback(CharacterPlaybackInput(moodLevel = Int.MAX_VALUE, holding = true))
        view.nudge()
        view.nudge(-99)
        view.visibility = View.INVISIBLE
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(view.isFrameScheduled)
        assertEquals(1, view.childCount)
    }

    @Test
    fun `native initialization failure reports once and removes the texture safely`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        var failures = 0
        val view = MiniCharacterView(activity.get(), "missing.glb") { failures++ }
        activity.get().setContentView(view)
        activity.visible()
        // Robolectric has no WindowManager visibility dispatch; make its attached
        // window visible before exercising the production lifecycle callback.
        ReflectionHelpers.setField(ReflectionHelpers.getField<Any>(view, "mAttachInfo"), "mWindowVisibility", View.VISIBLE)
        view.dispatchWindowVisibilityChanged(View.VISIBLE)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(view.isAttachedToWindow)
        assertTrue("Attached stage should be shown", view.isShown)
        assertEquals("Attached window should be visible", View.VISIBLE, view.windowVisibility)
        // Robolectric cannot load Android Filament JNI; this exercises the real catch
        // around Utils.init/Engine/ModelViewer rather than pretending GPU rendering works.
        assertEquals(1, failures)
        assertEquals(0, view.childCount)
        assertFalse(view.isFrameScheduled)
        view.visibility = View.INVISIBLE
        view.visibility = View.VISIBLE
        view.nudge()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, failures)
        activity.pause().stop().destroy()
        assertFalse(view.isFrameScheduled)
    }

    @Test
    fun `reattach replaces the texture without dispatching its cleanup twice`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val view = MiniCharacterView(activity.get(), "unused.glb") {}
        // Keep rendering stopped: this checks Android lifecycle, not GPU emulation.
        view.visibility = View.INVISIBLE
        val oldTexture = view.getChildAt(0)
        var detaches = 0
        oldTexture.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) { detaches++ }
        })
        activity.get().setContentView(view)
        activity.visible()
        activity.get().setContentView(View(activity.get()))
        assertEquals(1, detaches)
        activity.get().setContentView(view)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, detaches)
        assertEquals(1, view.childCount)
        assertTrue(oldTexture !== view.getChildAt(0))
        assertFalse(view.isFrameScheduled)
        activity.pause().stop().destroy()
        assertEquals(1, detaches)
    }
}
