package com.doombuddy.core.time

/** Injectable time source so the session engine and animators are testable. */
fun interface MonotonicClock {
    /** Milliseconds since an arbitrary but stable origin; immune to wall-clock changes. */
    fun nowMs(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowMs(): Long = android.os.SystemClock.elapsedRealtime()
}

fun interface WallClock {
    fun nowEpochMs(): Long
}

object SystemWallClock : WallClock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}
