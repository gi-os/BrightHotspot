# BrightHotspot

An Instant-Hotspot clone for the **Light Phone III**, for the case Apple never covered:
waking the phone's own hotspot for a device that is *not* an iPhone. Launcher label:
**Hotspot**, package `com.gios.brighthotspot`.

The target is a Wi-Fi-only **iPad mini**. Open the iPad, and the Light Phone -- sitting in a
pocket -- notices it is near, brings its hotspot up, and the iPad joins a network it already
knows. No tap on the iPad, nothing installed on the iPad. A MacBook works the same way if you
add it; the iPad is the priority.

## How it works

Apple's Instant Hotspot is a private handshake between Apple devices over Bluetooth LE, with
no third-party API on either side. So this app does not try to imitate the protocol. It
imitates the *behaviour*, from the phone's side, in three moves:

1. **Presence.** The app scans BLE for a device you paired through the phone's Bluetooth
   settings. Android resolves that device's rotating advertising address back to its real
   one *only because the pairing exchanged the keys* -- which is why setup is "pair the iPad
   once," and why the Diagnostic screen exists to prove the resolution works on your phone.
2. **Guess.** The phone cannot see whether the iPad has internet. So when the paired device
   is near and the phone is **not** on a home Wi-Fi network, it guesses the iPad needs a
   connection and raises the hotspot.
3. **Verify.** The iPad answers the guess by joining or not. A join confirms it; three
   minutes of silence refutes it, the hotspot goes back down, and the trigger is ignored for
   a while so a cafe with good Wi-Fi does not make the phone flap. When clients are actually
   connected the hotspot stays up until ten idle minutes pass.

All of that decision logic lives in `core/TriggerEngine.kt`, with no Android imports, and is
pinned by `TriggerEngineTest`.

## The one privileged piece

Raising the hotspot is the only thing an ordinary app cannot do -- since Android 11 the
tethering API is system-only. BrightHotspot borrows a shell UID through **[Shizuku]** to make
that single call. You start Shizuku once (wireless debugging, no PC needed afterward) and
grant this app; the Diagnostic screen shows whether it is ready. Without Shizuku the app still
detects presence and shows what it *would* do -- it just cannot flip the switch.

[Shizuku]: https://shizuku.rikka.app/

## Setup

1. Pair your iPad with the Light Phone in the phone's **Bluetooth settings**.
2. Join the iPad to the phone's hotspot once, so it is a known network on the iPad.
3. In BrightHotspot: **Setup -> Trigger devices**, turn on the iPad. Add your home Wi-Fi under
   **Home Wi-Fi** so the hotspot stays off there.
4. Start Shizuku and grant BrightHotspot (**Diagnostic -> Shizuku -> Fix**).
5. Turn on **Auto mode**.

### Starting Shizuku without the pairing dance

Shizuku's own route in is the wireless-debugging pairing flow, and Android tears it down on
**every reboot** -- so it is a dance you repeat rather than a setup you finish, and repeating it
is where people give up on this app.

If you have **BrightControl** with its adb connection set up, you do not have to. It already
holds a shell to this phone's own daemon, which is the same privilege by a route that survives.
Two ways:

- **Diagnostic -> Start Shizuku with BrightControl** in this app, which hands the request over
  and lets BrightControl show you the command before it runs it.
- **BrightControl -> ADB -> START SHIZUKU**, if you are already in there.

Either way Shizuku still asks you, app by app, in its own screen afterwards. Starting it grants
nothing on its own.

You can also just tap **Start hotspot now** any time; the manual button skips the whole guess.

## Install via BrightMarket

Scan the code from [gi-os.github.io/brightmarket-index/browse.html](https://gi-os.github.io/brightmarket-index/browse.html)
with BrightMarket installed. Every push to `main` cuts a signed APK that Obtainium picks up.

## Build

```
./gradlew :app:assembleRelease
```

The LP3 is arm64 only, so the APK ships one ABI. Java 17. No annotation processors --
state is `SharedPreferences`, so there is no KSP in the build.

## Reporting bugs

Shake the phone three times, or answer the crash prompt on next launch. Reports file into
the private `gi-os/light-reports` repo, same as the rest of the fleet.
