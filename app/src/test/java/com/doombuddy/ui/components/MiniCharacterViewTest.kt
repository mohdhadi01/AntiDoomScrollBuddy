package com.doombuddy.ui.components

import android.view.TextureView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MiniCharacterViewTest {
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
    }
}
