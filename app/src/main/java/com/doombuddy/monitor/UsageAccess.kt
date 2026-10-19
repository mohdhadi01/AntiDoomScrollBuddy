package com.doombuddy.monitor

import android.app.AppOpsManager
import android.content.Context
import android.os.Process

/**
 * Usage access = the GET_USAGE_STATS appop; there is no runtime dialog, the user grants
 * it through Settings (docs/ANDROID_PLATFORM.md).
 */
object UsageAccess {
    fun openSettings(context: Context) {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
