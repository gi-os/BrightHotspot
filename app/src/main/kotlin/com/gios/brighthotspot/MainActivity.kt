package com.gios.brighthotspot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gios.brighthotspot.ble.BleScanner
import com.gios.brighthotspot.ble.Bt
import com.gios.brighthotspot.hotspot.Privilege
import com.gios.brighthotspot.report.CrashLog
import com.gios.brighthotspot.report.ReportContext
import com.gios.brighthotspot.report.ReportOverlay
import com.gios.brighthotspot.service.WatchService
import com.gios.brighthotspot.service.WatchState
import com.gios.brighthotspot.ui.BarItem
import com.gios.brighthotspot.ui.DiagnosticScreen
import com.gios.brighthotspot.ui.HomeScreen
import com.gios.brighthotspot.ui.HotspotViewModel
import com.gios.brighthotspot.ui.LightTopBar
import com.gios.brighthotspot.ui.Rule
import com.gios.brighthotspot.ui.ScanRow
import com.gios.brighthotspot.ui.SetupScreen
import com.gios.brighthotspot.ui.theme.BrightHotspotTheme
import rikka.shizuku.Shizuku

/**
 * One activity, three screens. There is very little here on purpose -- the interesting work
 * is in the watcher and the engine, and this is the thin surface that lets a person point
 * them at a device and see what is happening.
 */
class MainActivity : ComponentActivity() {

    private val askPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    private val shizukuResult = Shizuku.OnRequestPermissionResultListener { _, _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        WatchService.ensureChannel(this)
        requestRuntimePermissions()
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuResult) }

        setContent {
            BrightHotspotTheme {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    App()
                    ReportOverlay()
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuResult) }
        super.onDestroy()
    }

    private fun requestRuntimePermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isNotEmpty()) askPerms.launch(wanted.toTypedArray())
    }
}

private const val SCREEN_HOME = 0
private const val SCREEN_SETUP = 1
private const val SCREEN_DIAG = 2

@Composable
private fun App() {
    val vm: HotspotViewModel = viewModel()
    var screen by remember { mutableStateOf(SCREEN_HOME) }

    // Refresh the snapshot whenever the app comes forward -- pairings, the current SSID and
    // Shizuku's state all change out from under us while we are in the background.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refresh() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val running by WatchState.running.collectAsState()
    val phase by WatchState.phase.collectAsState()
    val lastEvent by WatchState.lastEvent.collectAsState()
    val clients by WatchState.clients.collectAsState()
    val bonded by vm.bonded.collectAsState()
    val triggers by vm.triggers.collectAsState()
    val trusted by vm.trusted.collectAsState()
    val autoOn by vm.autoOn.collectAsState()
    val shizukuReady by vm.shizukuReady.collectAsState()
    val apOn by vm.apOn.collectAsState()
    val currentSsid by vm.currentSsid.collectAsState()

    BackHandler(enabled = screen != SCREEN_HOME) { screen = SCREEN_HOME }

    Column(Modifier.fillMaxSize()) {
        when (screen) {
            SCREEN_HOME -> {
                ReportContext.screen = "home"
                LightTopBar(title = "HOTSPOT")
            }
            SCREEN_SETUP -> {
                ReportContext.screen = "setup"
                LightTopBar(
                    left = BarItem.Icon(R.drawable.ic_back_white, { screen = SCREEN_HOME }, "Back"),
                    title = "SETUP",
                )
            }
            else -> {
                ReportContext.screen = "diagnostic"
                LightTopBar(
                    left = BarItem.Icon(R.drawable.ic_back_white, { screen = SCREEN_HOME }, "Back"),
                    title = "DIAGNOSTIC",
                )
            }
        }
        Rule()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (screen) {
                SCREEN_HOME -> HomeScreen(
                    phase = phase,
                    lastEvent = if (running) lastEvent else "",
                    clients = clients,
                    autoOn = autoOn,
                    apOn = apOn,
                    shizukuReady = shizukuReady,
                    triggerCount = triggers.size,
                    onStartNow = { vm.startNow { } },
                    onStopNow = { vm.stopNow() },
                    onToggleAuto = { vm.setAuto(it) },
                    onOpenSetup = { screen = SCREEN_SETUP },
                    onOpenDiagnostic = { screen = SCREEN_DIAG },
                )
                SCREEN_SETUP -> SetupScreen(
                    bonded = bonded,
                    triggers = triggers,
                    trusted = trusted,
                    currentSsid = currentSsid,
                    onToggleTrigger = { vm.toggleTrigger(it) },
                    onToggleTrusted = { vm.toggleTrusted(it) },
                )
                else -> DiagnosticHost(vm, shizukuReady, apOn)
            }
        }
    }
}

/**
 * The diagnostic runs its own short-lived scan while the screen is up, separate from the
 * watcher's, so a person can hold the iPad close and watch it resolve in real time. This is
 * the screen that answers the one open hardware question: does this phone resolve the iPad's
 * rotating address back to the paired identity.
 */
@Composable
private fun DiagnosticHost(vm: HotspotViewModel, shizukuReady: Boolean, apOn: Boolean) {
    val ctx = LocalLifecycleOwner.current
    var rows by remember { mutableStateOf<List<ScanRow>>(emptyList()) }
    val activity = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        val bondedAddrs = Bt.bonded(activity).map { it.address }.toSet()
        val heard = linkedMapOf<String, ScanRow>()
        val scanner = BleScanner(activity)
        scanner.onResult = { address, name, rssi ->
            heard[address] = ScanRow(address, name, rssi, bonded = address in bondedAddrs)
            rows = heard.values.sortedByDescending { it.rssi }
        }
        scanner.start()
        onDispose { scanner.stop() }
    }

    val resolved = rows.count { it.bonded }
    DiagnosticScreen(
        shizukuReady = shizukuReady,
        apState = if (apOn) "On" else "Off",
        resolvedBondedCount = resolved,
        rows = rows,
        onRequestShizuku = { Privilege(activity).requestPermission(1001) },
    )
}
