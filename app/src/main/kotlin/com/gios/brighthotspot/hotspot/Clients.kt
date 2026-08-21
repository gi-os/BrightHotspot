package com.gios.brighthotspot.hotspot

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader

/**
 * How many devices have actually joined the hotspot.
 *
 * This is the fact the whole guess-and-verify design turns on: a client appearing means
 * the iPad had no internet and took ours, which confirms the guess; none appearing means
 * it did not need us. There is no unprivileged Android API that answers it, so this reads
 * the kernel neighbour table through the Shizuku shell -- the same `ip neigh` a person
 * would run over adb -- and counts reachable entries on a tethering interface.
 *
 * It is deliberately forgiving. If Shizuku is not up, or the command shape differs on this
 * LightOS build, it returns [UNKNOWN] rather than guessing zero, because a false zero would
 * tear down a working hotspot. The engine treats unknown as "keep waiting", which fails
 * safe: an AP nobody joined still times out on its own.
 */
object Clients {

    const val UNKNOWN = -1

    // The soft-AP interface is named differently across vendors; these are the ones seen
    // on Android tethering. Matching on the interface keeps the phone's own Wi-Fi and
    // Bluetooth neighbours out of the count.
    private val AP_IFACES = listOf("wlan1", "ap0", "swlan0", "softap0")

    fun count(): Int {
        val out = runShell("ip neigh show") ?: return UNKNOWN
        var n = 0
        for (line in out.lineSequence()) {
            if (line.isBlank()) continue
            val onAp = AP_IFACES.any { line.contains(" dev $it ") || line.endsWith(" dev $it") ||
                line.contains(" $it ") }
            // REACHABLE, STALE and DELAY all mean the lease is live; FAILED/INCOMPLETE do not.
            val live = line.contains("REACHABLE") || line.contains("STALE") || line.contains("DELAY")
            if (onAp && live) n++
        }
        return n
    }

    /** Run a command as the Shizuku shell UID and return stdout, or null if it could not run. */
    private fun runShell(cmd: String): String? = runCatching {
        if (!Shizuku.pingBinder()) return null
        // newProcess is hidden; reflect it so a signature change is a caught failure, not a
        // link error at install time.
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        )
        m.isAccessible = true
        val proc = m.invoke(null, arrayOf("sh", "-c", cmd), null, null)
        val stream = proc.javaClass.getMethod("getInputStream").invoke(proc) as java.io.InputStream
        val text = stream.bufferedReader().use(BufferedReader::readText)
        proc.javaClass.getMethod("waitFor").invoke(proc)
        text
    }.getOrElse {
        Log.d("BrightClients", "neigh read failed: ${it.message}")
        null
    }
}
