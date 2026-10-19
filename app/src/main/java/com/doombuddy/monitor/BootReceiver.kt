package com.doombuddy.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // specialUse is not on Android 15's boot-start ban list (docs/ANDROID_PLATFORM.md).
            MonitorService.start(context)
            WatchdogReceiver.scheduleWatchdog(context)
        }
    }
}
