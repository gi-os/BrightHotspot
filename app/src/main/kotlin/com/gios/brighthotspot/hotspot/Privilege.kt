package com.gios.brighthotspot.hotspot

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.Executor

/**
 * The one piece that cannot be done with ordinary app permissions.
 *
 * Since Android 11 the tethering API is `signature|privileged`, which a sideloaded APK can
 * never hold. Shizuku is the standard way around that without root: the user starts a
 * shell-privileged Shizuku service once (over wireless debugging, no PC needed after
 * setup), and this app borrows that shell UID to make the one call the platform will not
 * let it make directly.
 *
 * Everything here is reflection on purpose. The classes involved -- `TetheringManager`, its
 * callback, `getWifiApState` -- are all hidden, so touching them by name would not compile
 * and, worse, would break silently across LightOS versions. Reflected and wrapped in
 * try/catch, a change in the platform turns into a caught failure and a report, not a crash.
 *
 * The approach mirrors the maintained SoftAp-via-Shizuku apps (EasySpot, telegram-rc): wrap
 * the system "tethering" binder with Shizuku's UID, hand `TetheringManager` a context that
 * claims to be the shell package so its internal permission check passes, then call
 * `startTethering(mode, executor, callback)`.
 */
class Privilege(context: Context) {

    private val app = context.applicationContext
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /** Shizuku is installed, its service is running, and it has bound to us. */
    fun binderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        if (Shizuku.isPreV11()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    /** Ready to actually drive tethering: bound and permitted. */
    fun ready(): Boolean = binderAlive() && hasPermission()

    // ------------------------------------------------------------------ AP state

    /**
     * The AP state constants from `WifiManager`: 10 disabling, 11 disabled, 12 enabling,
     * 13 enabled, 14 failed. `getWifiApState` is hidden but callable on the ordinary
     * manager with `ACCESS_WIFI_STATE`, no Shizuku needed just to read it.
     */
    fun apState(): Int = runCatching {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/net/wifi/WifiManager;")
        wifi.javaClass.getMethod("getWifiApState").invoke(wifi) as Int
    }.getOrDefault(AP_STATE_UNKNOWN)

    fun apEnabled(): Boolean = apState() == AP_STATE_ENABLED

    // ------------------------------------------------------------------ start / stop

    fun startTethering(): Boolean = callTethering(start = true)

    fun stopTethering(): Boolean = callTethering(start = false)

    private fun callTethering(start: Boolean): Boolean {
        if (!ready()) return false
        return runCatching {
            HiddenApiBypass.addHiddenApiExemptions("")
            val tm = tetheringManager() ?: return false
            val executor = Executor { it.run() }

            if (start) {
                val cbClass = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
                val cb = java.lang.reflect.Proxy.newProxyInstance(
                    cbClass.classLoader, arrayOf(cbClass),
                ) { _, method, args ->
                    when (method.name) {
                        "onTetheringStarted" -> Log.d(TAG, "AP up")
                        "onTetheringFailed" -> Log.w(TAG, "AP failed: ${args?.getOrNull(0)}")
                    }
                    null
                }
                tm.javaClass.getMethod(
                    "startTethering",
                    Int::class.javaPrimitiveType, Executor::class.java, cbClass,
                ).invoke(tm, TETHERING_WIFI, executor, cb)
            } else {
                tm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
                    .invoke(tm, TETHERING_WIFI)
            }
            true
        }.getOrElse {
            Log.w(TAG, "tethering call failed", it)
            false
        }
    }

    /**
     * Build a `TetheringManager` that runs with Shizuku's shell UID. The manager checks the
     * calling package name internally, so the context we hand it lies and says it is
     * `com.android.shell` (UID 2000) -- which is exactly who the Shizuku binder is.
     */
    private fun tetheringManager(): Any? = runCatching {
        val binder: IBinder = SystemServiceHelper.getSystemService("tethering")
            ?: return null
        val wrapped = ShizukuBinderWrapper(binder)

        val shellContext = object : ContextWrapper(app) {
            override fun getPackageName() = SHELL_PKG
            override fun getOpPackageName() = SHELL_PKG
            override fun getAttributionTag(): String? = null
        }

        val tmClass = Class.forName("android.net.TetheringManager")

        // Newer platforms: (Context, Supplier<IBinder>).
        runCatching {
            val supplierClass = Class.forName("java.util.function.Supplier")
            val ctor = tmClass.getDeclaredConstructor(Context::class.java, supplierClass)
            ctor.isAccessible = true
            val supplier = java.lang.reflect.Proxy.newProxyInstance(
                supplierClass.classLoader, arrayOf(supplierClass),
            ) { _, method, _ -> if (method.name == "get") wrapped else null }
            return ctor.newInstance(shellContext, supplier)
        }

        // Older platforms: (Context, IBinder).
        runCatching {
            val ctor = tmClass.getDeclaredConstructor(Context::class.java, IBinder::class.java)
            ctor.isAccessible = true
            return ctor.newInstance(shellContext, wrapped)
        }
        null
    }.getOrNull()

    companion object {
        private const val TAG = "BrightPrivilege"
        private const val SHELL_PKG = "com.android.shell"
        const val TETHERING_WIFI = 0
        const val AP_STATE_ENABLED = 13
        const val AP_STATE_UNKNOWN = -1
    }
}
