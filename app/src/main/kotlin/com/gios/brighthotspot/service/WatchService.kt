package com.gios.brighthotspot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.gios.brighthotspot.R
import com.gios.brighthotspot.core.Action
import com.gios.brighthotspot.core.Snapshot
import com.gios.brighthotspot.core.TriggerEngine
import com.gios.brighthotspot.data.Prefs
import com.gios.brighthotspot.ble.BleScanner
import com.gios.brighthotspot.hotspot.Clients
import com.gios.brighthotspot.hotspot.Privilege
import com.gios.brighthotspot.net.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The presence loop, as a foreground service because that is the only way LightOS will let
 * a sideloaded app keep a BLE scan alive with the screen off -- which is the entire point,
 * since the hotspot is meant to come up while the phone sits in a pocket.
 *
 * Each tick it takes one honest reading of the world and hands it to the [TriggerEngine],
 * then does exactly what the engine says. The service holds no policy of its own; if the
 * behaviour is wrong, the fix is in the engine, where a test can pin it.
 */
class WatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    private lateinit var prefs: Prefs
    private lateinit var connectivity: Connectivity
    private lateinit var privilege: Privilege
    private lateinit var scanner: BleScanner
    private val engine = TriggerEngine()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        connectivity = Connectivity(this)
        privilege = Privilege(this)
        scanner = BleScanner(this)
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Watching for your iPad"))
        if (loop == null) loop = scope.launch { run() }
        WatchState.running.value = true
        return START_STICKY
    }

    private suspend fun run() {
        val started = scanner.start()
        if (!started) {
            note("Bluetooth scan unavailable -- is Bluetooth on?")
        }
        while (scope.isActive) {
            val nearby = scanner.recentlySeen(SEEN_WINDOW_MS)
            val triggers = prefs.triggerAddresses
            val snap = Snapshot(
                triggerNearby = triggers.isNotEmpty() && nearby.any { it in triggers },
                onTrustedWifi = connectivity.onTrustedWifi(prefs.trustedSsids),
                apActive = privilege.apEnabled(),
                clientCount = Clients.count(),
            )
            WatchState.clients.value = snap.clientCount
            WatchState.phase.value = engine.phase

            when (engine.evaluate(System.currentTimeMillis(), snap)) {
                Action.START_AP -> {
                    val ok = privilege.startTethering()
                    note(if (ok) "iPad nearby -- hotspot on" else "Wanted to start, but Shizuku is not ready")
                }
                Action.STOP_AP -> {
                    privilege.stopTethering()
                    note(if (engine.inBackoff(System.currentTimeMillis()))
                        "No one joined -- standing down for a while" else "Idle -- hotspot off")
                }
                Action.NONE -> Unit
            }
            delay(TICK_MS)
        }
    }

    private fun note(msg: String) {
        Log.d(TAG, msg)
        WatchState.lastEvent.value = msg
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ID, buildNotification(msg))
        }
    }

    private fun buildNotification(text: String): Notification {
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return b.setContentTitle("Bright Hotspot")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_hotspot)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scanner.stop()
        engine.reset()
        scope.cancel()
        WatchState.running.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BrightWatch"
        private const val CHANNEL = "watch"
        private const val NOTIF_ID = 1
        /** How often the loop reconsiders the world. */
        private const val TICK_MS = 15_000L
        /** A device counts as "nearby" if it was heard within this long. Two rotating-address
         *  windows would be minutes; this is short so "nearby" tracks the present, not the past. */
        private const val SEEN_WINDOW_MS = 45_000L

        fun start(context: Context) {
            val i = Intent(context, WatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Hotspot watcher", NotificationManager.IMPORTANCE_LOW)
                        .apply { description = "Runs while auto mode watches for your iPad" },
                )
            }
        }
    }
}
