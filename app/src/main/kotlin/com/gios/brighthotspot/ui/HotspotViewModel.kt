package com.gios.brighthotspot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.brighthotspot.ble.Bonded
import com.gios.brighthotspot.ble.Bt
import com.gios.brighthotspot.data.Prefs
import com.gios.brighthotspot.hotspot.Privilege
import com.gios.brighthotspot.net.Connectivity
import com.gios.brighthotspot.service.WatchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the screen's copy of what the app knows and the few actions a tap can take. The
 * privileged and Bluetooth calls all hop off the main thread here so the UI never blocks
 * on a binder round-trip.
 */
class HotspotViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    private val privilege = Privilege(app)
    private val connectivity = Connectivity(app)

    val bonded = MutableStateFlow<List<Bonded>>(emptyList())
    val triggers = MutableStateFlow(prefs.triggerAddresses)
    val trusted = MutableStateFlow(prefs.trustedSsids)
    val autoOn = MutableStateFlow(prefs.autoEnabled)

    val shizukuReady = MutableStateFlow(false)
    val apOn = MutableStateFlow(false)
    val currentSsid = MutableStateFlow<String?>(null)

    fun refresh() {
        bonded.value = Bt.bonded(getApplication())
        triggers.value = prefs.triggerAddresses
        trusted.value = prefs.trustedSsids
        autoOn.value = prefs.autoEnabled
        currentSsid.value = connectivity.currentSsid()
        viewModelScope.launch(Dispatchers.IO) {
            shizukuReady.value = privilege.ready()
            apOn.value = privilege.apEnabled()
        }
    }

    fun toggleTrigger(address: String) {
        prefs.toggleTrigger(address)
        triggers.value = prefs.triggerAddresses
    }

    fun toggleTrusted(ssid: String) {
        prefs.toggleTrusted(ssid)
        trusted.value = prefs.trustedSsids
    }

    fun setAuto(on: Boolean) {
        prefs.autoEnabled = on
        autoOn.value = on
        if (on) WatchService.start(getApplication()) else WatchService.stop(getApplication())
    }

    /** The manual button. Bypasses the engine on purpose: the user asked, so just do it. */
    fun startNow(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { privilege.startTethering() }
        apOn.value = withContext(Dispatchers.IO) { privilege.apEnabled() }
        onResult(ok)
    }

    fun stopNow() = viewModelScope.launch {
        withContext(Dispatchers.IO) { privilege.stopTethering() }
        apOn.value = withContext(Dispatchers.IO) { privilege.apEnabled() }
    }
}
