package com.doombuddy.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Dynamic receiver: pauses sessions when the screen turns off. */
class ScreenStateReceiver(private val onScreenStateChanged: (Boolean) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> onScreenStateChanged(true)
            Intent.ACTION_SCREEN_OFF -> onScreenStateChanged(false)
        }
    }
}
