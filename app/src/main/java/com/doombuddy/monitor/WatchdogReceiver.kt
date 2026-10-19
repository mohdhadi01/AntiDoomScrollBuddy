package com.doombuddy.monitor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import com.doombuddy.data.PrefsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Keeps the companion alive across the realities of Android process management:
 *  - swiped-away task (onTaskRemoved → one-shot restart alarm),
 *  - silent process death (START_STICKY + a periodic inexact watchdog),
 *  - device reboot (BootReceiver).
 *
 * Uses inexact alarms only — no SCHEDULE_EXACT_ALARM permission needed.
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val permissionsGranted = UsageAccess.isGranted(context) &&
            Settings.canDrawOverlays(context)
        val onboarded = runBlocking {
            runCatching { PrefsRepository(context).preferences.first().onboardingDone }
                .getOrDefault(true) // if prefs are unreadable, err toward restarting
        }
        if (MonitorService.instance == null && permissionsGranted && onboarded) {
            MonitorService.start(context)
        }
    }

    companion object {
        private const val REQUEST_RESTART = 1001
        private const val REQUEST_PERIODIC = 1002

        /** One-shot: bring the service back shortly after swipe-away / process death. */
        fun scheduleRestart(context: Context, delayMs: Long) {
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_RESTART,
                Intent(context, WatchdogReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMs, pi)
        }

        /** Every 10 minutes while everything works: heal silently if the process died. */
        fun scheduleWatchdog(context: Context) {
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_PERIODIC,
                Intent(context, WatchdogReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 10 * 60_000,
                10 * 60_000,
                pi,
            )
        }
    }
}
