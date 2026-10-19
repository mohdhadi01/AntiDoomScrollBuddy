package com.doombuddy.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundResolverTest {

    @Test
    fun `resume makes package foreground`() {
        val r = ForegroundResolver()
        r.onResume("com.instagram.android", 100)
        assertEquals("com.instagram.android", r.current())
    }

    @Test
    fun `leave clears package - the works-once-then-never bug`() {
        val r = ForegroundResolver()
        r.onResume("com.instagram.android", 100)
        r.onLeave("com.instagram.android", 200)
        assertNull("leaving the app MUST clear foreground", r.current())
    }

    @Test
    fun `late stopped after another apps resume does not clear foreground`() {
        val r = ForegroundResolver()
        r.onResume("com.instagram.android", 100)
        r.onResume("com.android.chrome", 200)
        // Old activity's STOPPED lands AFTER the new app's RESUMED (real-world ordering).
        r.onLeave("com.instagram.android", 300)
        assertEquals("com.android.chrome", r.current())
    }

    @Test
    fun `newest resume wins among live packages`() {
        val r = ForegroundResolver()
        r.onResume("com.instagram.android", 100)
        r.onResume("com.google.android.youtube", 500)
        assertEquals("com.google.android.youtube", r.current())
        r.onLeave("com.google.android.youtube", 600)
        assertEquals("back to the still-live app", "com.instagram.android", r.current())
    }

    @Test
    fun `leave older than own resume is ignored - out-of-order log entries`() {
        val r = ForegroundResolver()
        r.onResume("com.instagram.android", 500)
        r.onLeave("com.instagram.android", 300) // stale event from a previous cycle
        assertEquals("com.instagram.android", r.current())
    }

    @Test
    fun `leave for unknown package is a no-op`() {
        val r = ForegroundResolver()
        r.onResume("com.instagram.android", 100)
        r.onLeave("com.android.chrome", 200)
        assertEquals("com.instagram.android", r.current())
    }

    @Test
    fun `reset clears everything - screen off path`() {
        val r = ForegroundResolver()
        r.onResume("com.instagram.android", 100)
        r.reset()
        assertNull(r.current())
    }

    @Test
    fun `rapid close and reopen cycle re-arms cleanly`() {
        val r = ForegroundResolver()
        // open -> leave -> reopen, ten times (the reported failure pattern)
        repeat(10) { i ->
            val t = i * 1_000L
            r.onResume("com.instagram.android", t)
            assertEquals("com.instagram.android", r.current())
            r.onLeave("com.instagram.android", t + 400)
            assertNull("cycle $i: must clear on leave", r.current())
        }
    }
}
