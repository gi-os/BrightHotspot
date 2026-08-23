package com.gios.brighthotspot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gios.brighthotspot.ble.Bonded
import com.gios.brighthotspot.core.Phase
import com.gios.brighthotspot.hotspot.AdbSetup
import com.gios.brighthotspot.ui.theme.Dim
import com.gios.brighthotspot.ui.theme.Faint

/**
 * A big tappable slab. The house apps use inverted fills for the primary action on the
 * matte greyscale panel -- a tint would read as a smudge -- so the main button is white
 * on black or, when active, black on white.
 */
@Composable
fun BigButton(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(if (active) Color.White else Color.Black)
            .clickable(onClick = onClick)
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Color.Black else Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

// -------------------------------------------------------------------------- Home

@Composable
fun HomeScreen(
    phase: Phase,
    lastEvent: String,
    clients: Int,
    autoOn: Boolean,
    apOn: Boolean,
    shizukuReady: Boolean,
    triggerCount: Int,
    onStartNow: () -> Unit,
    onStopNow: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenDiagnostic: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), Alignment.Center) {
            Text(
                when {
                    apOn && clients > 0 -> "Sharing with $clients"
                    apOn -> "Hotspot on"
                    autoOn -> "Watching"
                    else -> "Off"
                },
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
        if (lastEvent.isNotBlank()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), Alignment.Center) {
                Text(lastEvent, style = MaterialTheme.typography.bodyMedium, color = Dim,
                    textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(20.dp))

        if (apOn) {
            BigButton("Stop hotspot", active = true, onClick = onStopNow)
        } else {
            BigButton("Start hotspot now", active = false, onClick = onStartNow)
        }

        Rule(Modifier.padding(vertical = 12.dp))
        MenuRow(
            label = "Auto mode",
            sub = if (triggerCount == 0) "Pick a device in Setup first"
                  else "Wakes the hotspot when your iPad is near",
            detail = if (autoOn) "On" else "Off",
            onClick = { onToggleAuto(!autoOn) },
        )
        MenuRow(label = "Setup", sub = "Trigger devices and home Wi-Fi", onClick = onOpenSetup)
        MenuRow(
            label = "Diagnostic",
            sub = if (shizukuReady) "Shizuku ready" else "Shizuku not ready",
            onClick = onOpenDiagnostic,
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------------- Setup

@Composable
fun SetupScreen(
    bonded: List<Bonded>,
    triggers: Set<String>,
    trusted: Set<String>,
    currentSsid: String?,
    onToggleTrigger: (String) -> Unit,
    onToggleTrusted: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader("Trigger devices")
        if (bonded.isEmpty()) {
            EmptyState("No paired devices. Pair your iPad in the phone's Bluetooth settings, then come back.")
        } else {
            bonded.forEach { d ->
                MenuRow(
                    label = d.name,
                    sub = d.address,
                    detail = if (d.address in triggers) "On" else "",
                    onClick = { onToggleTrigger(d.address) },
                )
            }
        }

        SectionHeader("Home Wi-Fi")
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                "Near these networks the hotspot stays off -- you already have Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium, color = Faint,
            )
        }
        if (currentSsid != null) {
            MenuRow(
                label = currentSsid,
                sub = "Current network",
                detail = if (currentSsid in trusted) "Trusted" else "Add",
                onClick = { onToggleTrusted(currentSsid) },
            )
        }
        trusted.filter { it != currentSsid }.forEach { ssid ->
            MenuRow(label = ssid, detail = "Trusted", onClick = { onToggleTrusted(ssid) })
        }
        Spacer(Modifier.height(24.dp))
    }
}

// -------------------------------------------------------------------- Diagnostic

/** One line of the live scan: what was heard, and whether it is a device we can trigger on. */
data class ScanRow(val address: String, val name: String?, val rssi: Int, val bonded: Boolean)

@Composable
fun DiagnosticScreen(
    shizukuReady: Boolean,
    apState: String,
    resolvedBondedCount: Int,
    rows: List<ScanRow>,
    onRequestShizuku: () -> Unit,
) {
    val context = LocalContext.current
    val hasControl = remember { AdbSetup.controlInstalled(context) }
    var handoff by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader("Privilege")
        MenuRow(
            label = "Shizuku",
            sub = if (shizukuReady) "Ready -- the hotspot can be driven"
                  else "Not ready -- start Shizuku and grant this app",
            detail = if (shizukuReady) "OK" else "Fix",
            onClick = { if (!shizukuReady) onRequestShizuku() },
        )
        // **The step that made this app look broken.** Shizuku's own way in is the
        // wireless-debugging pairing flow, and Android tears it down on every reboot -- so it is
        // a dance you repeat rather than a setup you finish, and repeating it is where people
        // stop. BrightControl already holds an adb shell to this phone's own daemon, which is the
        // same privilege by a route that survives. It runs the one line; Shizuku still asks, app
        // by app, in its own screen afterwards.
        if (!shizukuReady) {
            MenuRow(
                label = "Start Shizuku with BrightControl",
                sub = if (hasControl) {
                    "opens BrightControl, which shows the command and runs it over its adb shell"
                } else {
                    "BrightControl is not installed. Get it from BrightMarket and set up its " +
                        "adb connection first -- then this is one tap after every reboot."
                },
                detail = if (hasControl) ">" else "--",
                onClick = {
                    if (hasControl && !AdbSetup.open(context)) {
                        handoff = "BrightControl would not open."
                    }
                },
            )
            handoff?.let {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Faint)
                }
            }
        }
        MenuRow(label = "Access point", detail = apState)

        SectionHeader("Address resolution")
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                if (resolvedBondedCount > 0)
                    "Good: $resolvedBondedCount paired device(s) resolved to their real address. Presence triggering will work."
                else
                    "No paired device has resolved yet. Wake the device and keep it close. If it never resolves, this phone did not keep the pairing keys and app-free triggering will not work.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (resolvedBondedCount > 0) Dim else Faint,
            )
        }

        SectionHeader("Heard just now (${rows.size})")
        if (rows.isEmpty()) {
            EmptyState("Nothing yet. Scanning...")
        } else {
            rows.forEach { r ->
                MenuRow(
                    label = r.name ?: r.address,
                    sub = "${r.address}   ${r.rssi} dBm",
                    detail = if (r.bonded) "paired" else "",
                    dim = !r.bonded,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
