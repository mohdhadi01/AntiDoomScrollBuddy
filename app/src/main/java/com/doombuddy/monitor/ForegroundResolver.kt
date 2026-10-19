package com.doombuddy.monitor

/**
 * Pure foreground-state resolver — the heart of reliable detection.
 *
 * Model: track the set of packages with a live RESUMED, keyed by their resume
 * timestamp. PAUSED/STOPPED removes the package. The current foreground is the
 * most-recently-resumed live package (null when none).
 *
 * This survives real-world event interleavings that break "newest event wins"
 * logic — e.g. an old activity's STOPPED landing *after* the next app's RESUMED
 * must not clear the foreground (that class of bug made sessions end spuriously
 * and interventions stop firing).
 *
 * Thread confinement: owned by the poller's single loop coroutine.
 */
class ForegroundResolver {

    private val liveResumes = HashMap<String, Long>()

    fun onResume(packageName: String, timestamp: Long) {
        liveResumes[packageName] = timestamp
    }

    fun onLeave(packageName: String, timestamp: Long) {
        // Ignore a leave that predates the package's own resume (out-of-order logs).
        val resumeTs = liveResumes[packageName] ?: return
        if (timestamp >= resumeTs) liveResumes.remove(packageName)
    }

    /** Most-recently-resumed live package, or null for launcher/home. */
    fun current(): String? =
        liveResumes.entries.maxByOrNull { it.value }?.key

    fun reset() = liveResumes.clear()

    /** Number of packages believed foreground — diagnostics/multi-window sanity. */
    fun liveCount(): Int = liveResumes.size
}
