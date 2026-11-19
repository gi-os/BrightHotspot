<a href="https://github.com/sponsors/gi-os"><img src="https://raw.githubusercontent.com/gi-os/gi-os/main/images/bright-sponsors.svg" width="100%" alt="Thank you to my sponsors"></a>

<img src="docs/icon.png" alt="" width="72" align="left" />

# BrightHotspot

[**⬇ Download the latest APK**](https://github.com/gi-os/BrightHotspot/releases/latest) · free, open source.

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

Scan the code from [brightmarket.gzl.dev](https://brightmarket.gzl.dev)
with BrightMarket installed. Every push to `main` cuts a signed APK that Obtainium picks up.

## Build

```
./gradlew :app:assembleRelease
```

The LP3 is arm64 only, so the APK ships one ABI. Java 17. No annotation processors --
state is `SharedPreferences`, so there is no KSP in the build.

Without signing credentials, the release APK is unsigned and debug builds use the standard Android
debug key. Neither can update an installed production APK.

### Release signing

These instructions are for Giovanni, for package `com.gios.brighthotspot`.

Before merging the signing change, disable **Build & Release APK** so the merge does
not publish. In a private directory outside the checkout, use Android Build Tools
35.0.0 to generate the replacement key and create a proof of rotation from the original
release key. Use fresh passwords when prompted.

```sh
gh workflow disable build.yml --repo gi-os/BrightHotspot

keytool -genkeypair -storetype JKS -keystore brighthotspot-new.jks -alias brighthotspot-release \
  -keyalg RSA -keysize 3072 -validity 10000

apksigner rotate --out brighthotspot.lineage \
  --old-signer --ks /path/to/original/brighthotspot.jks --ks-key-alias brighthotspot \
  --set-installed-data true --set-rollback false \
  --new-signer --ks brighthotspot-new.jks --ks-key-alias brighthotspot-release

apksigner lineage --in brighthotspot.lineage --print-certs
```

Verify that signer #1's SHA-256 digest matches `signing-lineage-root-fingerprint.txt`,
then replace `signing-fingerprint.txt` in the checkout with signer #2's SHA-256 digest:

```sh
apksigner lineage --in brighthotspot.lineage --print-certs \
  | sed -n 's/^Signer #2 in lineage certificate SHA-256 digest: //p' \
  > /path/to/BrightHotspot/signing-fingerprint.txt
```

Commit that fingerprint change on the PR branch before merging. Do not change
the original fingerprint. Keep the original private key out of CI; it is only needed
offline to create the lineage.

Until the new fingerprint is committed, the identical original/current pins deliberately
make release signing fail closed.

In [**Settings → Secrets and variables → Actions**](https://github.com/gi-os/BrightHotspot/settings/secrets/actions), set these five secrets:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `brighthotspot-new.jks` |
| `SIGNING_LINEAGE_BASE64` | Base64-encoded `brighthotspot.lineage` |
| `KEYSTORE_PASSWORD` | New keystore password |
| `KEY_ALIAS` | `brighthotspot-release` |
| `KEY_PASSWORD` | New key password |

On Linux, upload the files without printing their contents and enter the other values when prompted:

```sh
base64 -w 0 brighthotspot-new.jks | gh secret set RELEASE_KEYSTORE_BASE64 --repo gi-os/BrightHotspot
base64 -w 0 brighthotspot.lineage | gh secret set SIGNING_LINEAGE_BASE64 --repo gi-os/BrightHotspot
gh secret set KEYSTORE_PASSWORD --repo gi-os/BrightHotspot
gh secret set KEY_ALIAS --repo gi-os/BrightHotspot
gh secret set KEY_PASSWORD --repo gi-os/BrightHotspot
```

CI signs with the new key using APK Signature Scheme v3 and verifies the proof of rotation
from the original pin to the new pin. BrightHotspot requires Android API 29, so no supported
device needs a v1/v2 signature or the old private key. Android can accept an in-place update,
but a catalog that compares only the current certificate can still hide it from users on
the old signer. Coordinate the BrightMarket catalog/updater transition before re-enabling
publishing, and only re-enable it for an authorized release.

A push to `main` (other than ignored paths) or a manual run of `build.yml` builds,
signs, tags, and publishes. Tag pushes do not trigger it. Branch and PR checks
in `check.yml` publish nothing.

## Reporting bugs

Shake the phone three times, or answer the crash prompt on next launch. Reports file into
the private `gi-os/light-reports` repo, same as the rest of the fleet.

<!-- bright-footer:begin -->
---

## Bright\*

**It's not Light, it's Bright.**

26 open-source apps for the **Light Phone III** — camera, music, maps, messages,
reading, transit, games. The phone has no app store, so they install by sideload: scan one
code from **[brightmarket.gzl.dev](https://brightmarket.gzl.dev)** and BrightMarket keeps them updated.

[Roll](https://github.com/gi-os/Roll) · [BrightNotebook](https://github.com/gi-os/BrightNotebook) · [BrightControl](https://github.com/gi-os/BrightControl) · [BrightWay](https://github.com/gi-os/BrightWay) · [BrightChat](https://github.com/gi-os/BrightChat) · [browse all 26 →](https://brightmarket.gzl.dev)

The Light Phone does not sponsor or endorse any of these. Built by
[Giovanni Lupo](https://github.com/gi-os) — if this one is useful to you, a ⭐ helps the next
person find it.
<!-- bright-footer:end -->

## Support

These apps are free, open, and built on my own time. Sponsorship pays the bills that don't go away: build servers, test hardware, and the crash reporter that keeps them shipping. Donation or not my code is always free for the world to use.

[Sponsor on GitHub](https://github.com/sponsors/gi-os)
