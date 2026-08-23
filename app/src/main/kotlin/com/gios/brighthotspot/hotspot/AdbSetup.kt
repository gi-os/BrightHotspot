package com.gios.brighthotspot.hotspot

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Asking BrightControl to start Shizuku.
 *
 * ### The problem this removes
 *
 * Everything this app needs, it can ask for itself — Bluetooth, location, notifications are all
 * ordinary runtime prompts. The exception is the one that matters: raising the hotspot is
 * `signature|privileged` since Android 11, so it goes through Shizuku, and Shizuku's own way in
 * is the wireless-debugging pairing flow. Android tears that down on every reboot. So the setup
 * step is not something you complete, it is something you repeat, and it is the reason this app
 * reads as "I cannot figure out how to start it".
 *
 * BrightControl already holds an adb shell to this phone's own daemon — the same privilege, by a
 * route the user set up once and which survives on its own terms. It can run the one line that
 * starts Shizuku.
 *
 * ### What crosses the gap
 *
 * The words `start shizuku`, and nothing else. This side carries a request; BrightControl parses
 * it, rebuilds the actual command from its own source, shows it, and runs it only after the user
 * says so. Deliberately no validation here: the far side is the one holding the shell, and
 * checking on this side would only invite the assumption that it can relax.
 *
 * The same contract BrightMarket uses to hand an app's ADB grants over. This app is the second
 * caller of it, and the first with something to ask that is not about itself.
 */
object AdbSetup {

    const val CONTROL_PKG = "com.gios.lightcontrol"

    private const val ACTION = "com.gios.lightcontrol.action.RUN_GRANTS"
    private const val EXTRA_PACKAGE = "com.gios.lightcontrol.extra.PACKAGE"
    private const val EXTRA_LABEL = "com.gios.lightcontrol.extra.LABEL"
    private const val EXTRA_COMMANDS = "com.gios.lightcontrol.extra.COMMANDS"

    /** The one thing to ask for. A verb, not a command — see BrightControl's GrantRequest. */
    private val SETUP = arrayListOf("start shizuku")

    /** Whether BrightControl is on the phone at all. */
    fun controlInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(CONTROL_PKG, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Open BrightControl on this app's setup. False when it could not be opened.
     *
     * Explicit package rather than a bare action: the receiver is one known app, and an implicit
     * intent for something that runs shell commands is an invitation for anything else to answer
     * it.
     */
    fun open(ctx: Context): Boolean {
        val intent = Intent(ACTION).apply {
            setPackage(CONTROL_PKG)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_PACKAGE, ctx.packageName)
            putExtra(EXTRA_LABEL, "BrightHotspot")
            putStringArrayListExtra(EXTRA_COMMANDS, SETUP)
        }
        return runCatching { ctx.startActivity(intent); true }.getOrDefault(false)
    }
}
