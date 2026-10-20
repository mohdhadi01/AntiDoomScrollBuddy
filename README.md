# 🐾 DoomBuddy

<p align="center">
  <strong>Your day, reclaimed.</strong><br />
  A character-driven digital-wellbeing companion for Android that turns endless scrolling into a small, funny moment of awareness.
</p>

<p align="center">
  <a href="demo/doombuddy-demo.mp4">▶ Watch the running app demo</a> ·
  <a href="#-quick-start">Quick start</a> ·
  <a href="docs/ARCHITECTURE.md">Architecture</a> ·
  <a href="docs/PRIVACY.md">Privacy</a>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-26%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Offline first" src="https://img.shields.io/badge/runtime-offline--first-25283B" />
  <img alt="Tests" src="https://img.shields.io/badge/tests-65%20passing-6B8E23" />
</p>

> **The character is the product. The timer is part of the character.**

## 🎬 See it in action

The repository includes a screen recording of the release app running on an Android emulator:

**[▶ Open the 17-second quick Instagram-style demo](demo/doombuddy-instagram-quick.mp4)** ·
**[Open the full 72-second product demo](demo/doombuddy-demo.mp4)**

The quick demo makes the core action obvious in under a minute: **scroll the feed → the buddy appears above it → hold to escape**. The longer demo shows the complete product flow: onboarding, the cast picker, named performances, selected apps, transparent permission explanations, a live over-app intervention, hold-to-escape, and the daily dashboard.

The feed in the quick demo is a local Instagram-style page at [`demo/instagram-feed.html`](demo/instagram-feed.html). It is intentionally self-contained, so the recording does not depend on a login, an external account, or network access.

## ✨ What DoomBuddy does

DoomBuddy watches only the apps you choose. When a scrolling session crosses your personal threshold, a small companion appears above the current app with a live timer and a performance that matches how long you have been there.

- **Not a blocker:** the overlay is deliberately small and does not steal touch input.
- **Not a guilt machine:** there are no streaks, shame messages, ads, or social rankings.
- **Not a disappearing notification:** the buddy stays until you deliberately hold to escape or leave the tracked app.
- **Not a cloud tracker:** usage history and preferences stay on the device.

The goal is simple: interrupt autopilot long enough for you to make a choice.

## 🐾 Meet the cast

Choose a buddy during onboarding or later from **Settings → Your buddy**. Every original character has a complete set of named GLB performances, not a single looping idle animation.

| Buddy | Personality | Signature acts |
| --- | --- | --- |
| **Loui** | The deadpan intern | coffee break, scrolling report, exhausted slump |
| **Buffer** | A receipt-printing CRT robot | loading, paper receipt, dramatic reboot |
| **Goop** | A melodramatic lime blob | white flag, puddle meltdown, spring-back |
| **Fenn** | A suspicious fox | walk, tail wag, look around |

The original trio — Loui, Buffer, and Goop — ships with ten clips each:

`Idle` · `Entrance` · `Notice` · `Waiting` · `Escalate` · `Meltdown` · `Poke` · `Hold` · `Dismiss` · `Celebrate`

Existing Fenn selections are preserved during upgrades. New installs start with Loui.

## 🎭 The intervention loop

1. **Choose the apps** that tend to swallow your time: Instagram, YouTube, TikTok, Reddit, X, Facebook, Snapchat, Chrome, or your own additions.
2. **Choose the energy**: Chill, Playful, Persistent, or Chaos.
3. **Set the first check-in** from 1–15 minutes.
4. **Keep scrolling**. DoomBuddy quietly measures the selected app session.
5. **Meet the buddy** when the threshold is crossed. The timer cycles between the current session and today’s total.
6. **Hold to escape**. A deliberate hold dismisses the overlay; the session keeps counting, so the interaction is honest.
7. **Leave the app** to end the session and celebrate reclaimed time.

## 🧩 Product highlights

| Area | Behaviour |
| --- | --- |
| **Live overlay** | A `TYPE_APPLICATION_OVERLAY` Compose card stays visible above the tracked app without blocking the app’s controls. |
| **Performance director** | Maps elapsed time and events to named entrance, escalation, poke, hold, dismissal, and celebration clips. |
| **Character studio** | Preview all four buddies and replay Hello, Still here?, Too long, Meltdown, and We escaped! from onboarding or Settings. |
| **Session engine** | Turns `UsageStatsManager` foreground events into deterministic, unit-tested session state. |
| **Dashboard** | Shows today’s scroll time, reclaimed time, app breakdown, live session state, and recent sessions. |
| **Privacy** | No analytics, no account, no runtime network calls, and no collected content from the tracked apps. |

## 🚀 Quick start

### Requirements

- Android SDK Platform 37 and build-tools 35.0.0
- JDK 21
- Python 3.10+ (the asset tool uses only the standard library)
- An Android emulator or device for the manual demo

The project pins Gradle 9.7.1 and the reference Python runtime in `.python-version`. No global package installation is required.

### Clone and build

```bash
git clone https://github.com/mohdhadi01/AntiDoomScrollBuddy.git
cd AntiDoomScrollBuddy

# First build: provision the Android/Gradle caches if needed.
./gradlew :app:assembleDebug :app:testDebugUnitTest

# Repeatable offline checks after provisioning.
make test
make build
make lint
```

### Install a debug build

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Complete onboarding in the app and grant the two required Android permissions:

1. **Usage access** — identifies when a selected app is in the foreground.
2. **Display over other apps** — lets the buddy appear above that app.

Notifications and battery-exemption permission are explained separately and are optional for the core interaction.

### Build and install the release APK

```bash
./gradlew :app:assembleRelease --offline
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build reads optional local signing values from `keystore.properties`. Signing material is intentionally ignored and is never part of the repository.

## 🧪 Testing and reproducibility

There is one offline test command for the whole project:

```bash
make test
```

It performs both deterministic asset validation and the Android unit-test suite. The tests cover:

- session start/end and foreground-app resolution;
- intervention thresholds and escalation policy;
- character roster IDs and persisted selection behaviour;
- named clip resolution and performance priority;
- hold-to-dismiss, poke replay, and celebration transitions;
- the TextureView lifecycle and non-zero Filament viewport regression.

The project also includes a Dockerfile that provisions a Linux Android build image and runs the same test command:

```bash
docker build -t doombuddy-android .
docker run --rm --network none doombuddy-android
```

To copy the debug APK out of the image:

```bash
docker create --name doombuddy-apk doombuddy-android
docker cp doombuddy-apk:/workspace/app/build/outputs/apk/debug/app-debug.apk ./app-debug.apk
docker rm doombuddy-apk
adb install -r ./app-debug.apk
```

## 🏗️ How it is built

```text
UsageStatsManager ──▶ ForegroundAppPoller ──▶ SessionEngine
                                              │
                                              ├──▶ CharacterDirector ──▶ GLB / Filament performer
                                              └──▶ OverlayController ──▶ Compose timer card

Room ── session history       DataStore ── preferences and thresholds
```

- **Kotlin + Jetpack Compose** for the Android UI.
- **Filament** for local GLB character rendering.
- **Room** for session history.
- **DataStore** for preferences and intervention policy.
- **Foreground service** for reliable background monitoring.
- **Pure session and playback policies** so the important behaviour is testable without a device.

See the full docs in [`docs/`](docs/), especially:

- [`ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`PRODUCT_BEHAVIOR.md`](docs/PRODUCT_BEHAVIOR.md)
- [`CHARACTER_SYSTEM.md`](docs/CHARACTER_SYSTEM.md)
- [`TESTING.md`](docs/TESTING.md)
- [`PRIVACY.md`](docs/PRIVACY.md)
- [`RELEASE.md`](docs/RELEASE.md)

## 🔒 Privacy model

DoomBuddy stores only what it needs to make the intervention useful:

- which selected package is in the foreground;
- when a session starts and ends;
- local preferences such as the selected buddy, threshold, intensity, and overlay position.

It does **not** read message content, page content, screenshots, keystrokes, contacts, or browser history. It has no analytics SDK and makes no runtime network request. Read [`docs/PRIVACY.md`](docs/PRIVACY.md) for the full model.

## 🎨 Assets and licensing

Loui, Buffer, and Goop are original deterministic GLB assets generated by `tools/generate_characters.py`; no Blender installation or asset download is required. Fenn is the existing Bake3D “Fox” asset used under **CC BY 4.0**, with attribution preserved in [`app/src/main/assets/characters/LICENSE.txt`](app/src/main/assets/characters/LICENSE.txt).

Asset details and the regeneration workflow are documented in:

- [`docs/ASSET_LICENSES.md`](docs/ASSET_LICENSES.md)
- [`docs/CHARACTER_ART_PIPELINE.md`](docs/CHARACTER_ART_PIPELINE.md)

## 📦 Repository layout

```text
app/src/main/java/com/doombuddy/   Android application and runtime
app/src/main/assets/characters/    Local GLB performers and attribution
app/src/test/                      Offline unit and regression tests
docs/                               Product, architecture, privacy, and release notes
tools/generate_characters.py       Deterministic original asset generator
demo/doombuddy-demo.mp4            Screen recording of the running release app
demo/doombuddy-instagram-quick.mp4 Quick feed → intervention → hold demo
demo/instagram-feed.html            Offline Instagram-style demo feed
Dockerfile                         Clean Linux build/test environment
Makefile                           Single reproducible test/build entry point
```

## ⚠️ Current platform note

The pinned Filament 1.51 native libraries trigger Android’s 16 KB page-size compatibility warning on the 16 KB emulator. The app runs in compatibility mode and the warning is documented in [`docs/TESTING.md`](docs/TESTING.md). Full 16 KB compliance requires a future native-library upgrade.

## 🗺️ Buildathon submission

- **Project:** AntiDoomScrollBuddy / DoomBuddy
- **Repository:** [github.com/mohdhadi01/AntiDoomScrollBuddy](https://github.com/mohdhadi01/AntiDoomScrollBuddy)
- **Quick demo:** [`demo/doombuddy-instagram-quick.mp4`](demo/doombuddy-instagram-quick.mp4)
- **Full demo:** [`demo/doombuddy-demo.mp4`](demo/doombuddy-demo.mp4)
- **Build status:** reproducible Android build, pinned toolchain, lockfiles, Dockerfile, and offline test command included

Built during the OmniRush Frontier Buildathon 2026 with OmniRush as the project’s generative AI tool.

## 📄 License

Project source is provided for the buildathon submission. Third-party asset attribution is listed separately in [`docs/ASSET_LICENSES.md`](docs/ASSET_LICENSES.md) and the bundled asset license file.
