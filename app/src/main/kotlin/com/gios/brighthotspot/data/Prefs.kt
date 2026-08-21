package com.gios.brighthotspot.data

import android.content.Context
import android.content.SharedPreferences

/**
 * All of the app's state, which is small: whether auto mode is on, which paired devices
 * are triggers, and which Wi-Fi networks count as "home" so the phone stays quiet there.
 * SharedPreferences rather than anything heavier -- this is a handful of strings and one
 * flag, and it keeps an annotation processor out of the build entirely.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("brighthotspot", Context.MODE_PRIVATE)

    /** Auto mode: run the presence watcher and bring the AP up on its own. */
    var autoEnabled: Boolean
        get() = sp.getBoolean(KEY_AUTO, false)
        set(v) = sp.edit().putBoolean(KEY_AUTO, v).apply()

    /**
     * Bluetooth addresses of the devices whose presence should wake the hotspot -- the
     * iPad first, the MacBook if the user adds it. Stored as the bond's identity address;
     * the OS resolves the rotating advertising address back to this for bonded devices.
     */
    var triggerAddresses: Set<String>
        get() = sp.getStringSet(KEY_TRIGGERS, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_TRIGGERS, v).apply()

    fun toggleTrigger(address: String): Boolean {
        val next = triggerAddresses.toMutableSet()
        val added = next.add(address)
        if (!added) next.remove(address)
        triggerAddresses = next
        return added
    }

    /**
     * SSIDs the phone treats as home. Near any of these the trigger is ignored, because at
     * home the iPad and the phone are always near each other and always have Wi-Fi, so a
     * presence trigger there would fire constantly and mean nothing.
     */
    var trustedSsids: Set<String>
        get() = sp.getStringSet(KEY_TRUSTED, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_TRUSTED, v).apply()

    fun toggleTrusted(ssid: String): Boolean {
        val next = trustedSsids.toMutableSet()
        val added = next.add(ssid)
        if (!added) next.remove(ssid)
        trustedSsids = next
        return added
    }

    private companion object {
        const val KEY_AUTO = "auto_enabled"
        const val KEY_TRIGGERS = "trigger_addresses"
        const val KEY_TRUSTED = "trusted_ssids"
    }
}
