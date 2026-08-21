## BrightHotspot v1.0 -- waking the hotspot for something that isn't an iPhone

**First release.** Apple's Instant Hotspot only ever worked between Apple devices, which left
a Wi-Fi-only iPad mini with no way to wake the Light Phone III's hotspot on its own. This app
fills that gap from the phone's side.

The phone watches for a paired device over Bluetooth LE. When that device is near and the
phone is not on a home Wi-Fi network, it raises its hotspot on a guess -- it cannot see whether
the iPad has internet, so it lets the iPad answer by either joining or not. A join keeps the
hotspot up until ten idle minutes pass; no join within three minutes takes it back down and
holds off for half an hour, so a place with its own Wi-Fi does not make the phone flap.

Raising the hotspot is the one thing a sideloaded app cannot do alone -- the tethering API has
been system-only since Android 11 -- so the privileged call goes through Shizuku. Without it
the app still detects presence and shows its decision; it just cannot flip the switch.

This build ships the trigger engine (pure Kotlin, unit-tested), the BLE presence watcher as a
foreground service, the Shizuku-driven tethering, a manual start button, and a Diagnostic
screen that proves whether this phone resolves a paired device's rotating address -- the one
thing that has to be true for app-free triggering to work at all.
