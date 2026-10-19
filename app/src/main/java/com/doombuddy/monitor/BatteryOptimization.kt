package com.doombuddy.monitor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Aggressive OEM/system battery managers kill long-running monitors. This is the
 * user-initiated exemption flow (Play-policy-acceptable for screen-time apps where
 * uninterrupted monitoring IS the core feature — see docs/PERMISSIONS.md).
 */
object BatteryOptimization {
    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Always ends in a system dialog the user can deny — we degrade gracefully. */
    fun requestExemption(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } catch (_: Exception) {
            // Fallback: the general optimization list.
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}
