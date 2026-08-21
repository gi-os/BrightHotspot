package com.gios.brighthotspot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gios.brighthotspot.data.Prefs

/**
 * A reboot cancels the foreground service, so if the user left auto mode on, put the
 * watcher back. Guarded on the pref so a phone that was never set up stays silent.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Prefs(context).autoEnabled) WatchService.start(context)
    }
}
