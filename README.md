# Foqos for Android

An unofficial Android fork of [Foqos](https://github.com/awaseem/foqos), the free, open-source
iPhone app blocker with NFC and QR unlocking by Ali Waseem. Same idea, rebuilt for Android:
Kotlin, Jetpack Compose, Room, no account, no analytics, no subscription.

> Not affiliated with the original Foqos project or with foqos.app. MIT licensed, like the
> original.

## What it does

Create a profile for a routine — work, study, bedtime — pick the apps and websites it blocks,
then choose how that profile starts and stops: manually, with an NFC tag, with a QR code, on a
timer, or with a limited number of temporary openings.

- App blocking through an accessibility shield, with a usage-stats fallback
- Optional system-level app suspension when Foqos is provisioned as device owner
- Website blocking through a local DNS filter (a VPN that only routes DNS)
- Twelve blocking strategies, the same ids and behaviour as the iOS app
- NFC tag read **and write** — Android can do both without a system sheet
- QR code scanning and generation
- Breaks, pauses, temporary access, strict mode, emergency unblocks
- Session history, four-week heatmap, focus streak
- Everything local: the only network traffic is the DNS queries the filter forwards

## How blocking actually works on Android

This is the part that does not port directly, and it is worth being blunt about.

iOS has `FamilyControls` / `ManagedSettings`: Apple gives an app the right to shield other apps at
the system level, and the user cannot walk around it. **Android has no public equivalent for a
normal app.** So Foqos for Android stacks three mechanisms, strongest last:

| Layer | How it blocks | Can the user get around it? |
| --- | --- | --- |
| Accessibility shield | Watches window transitions, covers a blocked app with the shield screen | Yes — turn the service off in Settings, or force-stop Foqos |
| Usage-stats poller | Reads the foreground app once a second when accessibility is off | Yes, and it reacts a second or two later |
| Device owner suspension | `DevicePolicyManager.setPackagesSuspended` — the app will not launch at all | No, short of a factory reset |

Device owner is the only unbypassable option, and it cannot be granted from inside the app. On a
device with no accounts added:

```bash
adb shell dpm set-device-owner app.foqos.android/.blocking.FoqosDeviceAdminReceiver
```

Settings carries a step-by-step guide for this, written for someone who has never opened a
terminal, and it says up front that the setup needs a factory reset and suits a spare phone
rather than a main one. Once device owner is set, a running session also blocks uninstalling
Foqos and booting into safe mode; factory reset is deliberately left available.

Website blocking answers DNS from a local tunnel. Two limits come with that: only one VPN can be
active at a time, so it conflicts with a real VPN; and an app with its own DNS-over-HTTPS resolver
(Chrome's secure DNS, for example) never asks the system resolver and is not filtered.

### Google Play

Play's [Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491)
requires that the API be used for accessibility purposes, and app blockers using it are regularly
rejected or removed. Plan on distributing this through F-Droid, GitHub releases, or a sideloaded
APK rather than Play. That is a policy problem, not a technical one — the app itself works.

## NFC-only unlock (the default)

A new profile is created with **Unlock only with an NFC tag** on. While it is on, a running
session ends one way and one way only: hold the linked tag to the phone. The in-app Stop button
refuses, breaks and emergency unblocks are off, a QR code with the same value refuses, and a
`foqos.app/profile/…` link opened from a browser refuses. Turning the switch off restores the
normal strategies, breaks and emergency unblocks.

Two details make this hold rather than merely look strict:

- **A key is a tag's hardware UID, not the link written on it.** A profile link is text — anyone
  who scans the tag once can reprint it as a QR code. Linking a tag on its Tag / QR screen stores
  the UID, which cannot be reproduced from a photograph. Writing a link to a tag is still offered
  for compatibility with the iOS app, and the screen says plainly that a written link starts
  sessions but never ends one.
- **A profile cannot be edited while its session runs.** Otherwise deleting the key mid-session
  would be the escape hatch. For the same reason a profile refuses to start at all when NFC-only
  is on and no tag is linked, and emergency unblock stays available in that one state so a
  keyless session can never strand the device.

What this does not do is change the table above: a user who goes into Android's settings can
still switch the accessibility service off or force-stop Foqos. NFC-only unlocking closes every
route through the app; only device owner closes the routes through the OS.

## Blocking strategies

Ids match the iOS app, so a profile means the same thing on both platforms.

| Strategy | How it works |
| --- | --- |
| `ManualBlockingStrategy` | Start and stop in the app |
| `NFCBlockingStrategy` | Scan a tag to start, scan the same tag to stop |
| `QRCodeBlockingStrategy` | Scan a code to start, scan the same code to stop |
| `NFCManualBlockingStrategy` | Start in the app, stop with a tag |
| `QRManualBlockingStrategy` | Start in the app, stop with a code |
| `ShortcutTimerBlockingStrategy` | Pick a duration, stop early in the app |
| `NFCTimerBlockingStrategy` | Pick a duration, stop early with a tag |
| `QRTimerBlockingStrategy` | Pick a duration, stop early with a code |
| `NFCPauseTimerBlockingStrategy` | Scan to pause, scan during the pause to stop |
| `QRPauseTimerBlockingStrategy` | Same, with a code |
| `NFCSoftUnblockBlockingStrategy` | Limited short openings, tag stops the session |
| `QRSoftUnblockBlockingStrategy` | Limited short openings, code stops the session |

A profile with saved physical unlock items only ever answers to those items — everything else is
refused, which is what strict mode means here.

## Tags and links

Tags and QR codes carry `https://foqos.app/profile/<uuid>`, exactly what the iOS app writes, so a
tag written on either platform toggles the matching profile on the other. `foqos://profile/<uuid>`
works too.

## Build

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug          # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
./gradlew installDebug           # to a connected device
```

CI builds the debug APK and runs the tests on every push; the APK is uploaded as a build artifact.

## Project layout

```
app/src/main/java/app/foqos/android/
├── blocking/          shield service, device-owner blocker, shared blocking state
│   └── vpn/           DNS-filtering VpnService, IPv4/UDP and DNS parsing
├── data/              Room entities, DAOs, repositories, DataStore settings
├── nfc/               NDEF read and write
├── qr/                ML Kit scanning, ZXing generation
├── session/           session controller, time maths, foreground service, alarms
├── strategy/          the twelve strategies as data
├── ui/                Compose screens, theme, view model
└── util/              deep links, permissions, foreground-app watcher
```

## Credits

Original iOS app: [awaseem/foqos](https://github.com/awaseem/foqos) by Ali Waseem.
