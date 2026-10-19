# DoomBuddy (AntiDoomScrollBuddy)

A character-driven digital-wellbeing companion for Android. You pick one of ten
animated 3D crew members — **Marlow**, **Orbit**, **Riff**, **Nova**, **Pixel**, **Luma**, **Jet**, **Sage**, **Echo**, or **Moss**; when a tracked app has been open too
long, the buddy appears **over** that app, *stays*, and acts out your elapsed time
(peeking, fidgeting, popcorn, napping) beside a live timer that cycles between
**THIS SESSION** and **TODAY**. It never quietly disappears: you escape with a
deliberate **hold-to-dismiss** (the session keeps counting), and it comes back later.
Leaving the app ends the session, celebrates your escape, and banks the time.

> The character is the product. The timer is part of the character.

Native-first, offline-first, local GLB characters rendered by Filament, no analytics, no
network at runtime. Character models are from Kenney's CC0 Mini Characters pack; the
license is preserved in `app/src/main/assets/characters/LICENSE.txt`.

## Try it

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.doombuddy.debug GET_USAGE_STATS allow
adb shell appops set com.doombuddy.debug SYSTEM_ALERT_WINDOW allow
```

For a signed release APK: `./gradlew :app:assembleRelease` (signing config reads
`keystore.properties`).

Onboarding walks through: buddy picker → tracked apps → intensity → permissions →
a live test overlay. A developer mode in Settings unlocks simulation tools
(any escalation level, celebration, character/entrance preview studio).

## Reproducible project commands

The project is pinned to Gradle 9.7.1, JDK 21 (`.java-version`), Android API 37, and
keeps the Gradle wrapper and version catalog in the repository. No network is needed for
the normal local checks after dependencies have been provisioned once:

```bash
make test       # offline unit tests
make build      # offline debug APK
make lint       # Android lint
```

For a clean Linux build with the Android SDK installed by the project image:

```bash
docker build -t doombuddy-android .
docker run --rm -v "$PWD:/workspace" doombuddy-android
```

`keystore.properties`, local signing material, and machine-specific Gradle/Android files
are intentionally ignored. Release signing is optional and must be supplied locally.

## Architecture in one paragraph

A `specialUse` foreground service polls `UsageStatsManager.queryEvents()` every 2s to
track the foreground app. A pure, unit-tested `SessionEngine` turns those events into
session/intervention outputs against a configurable `InterventionPolicy`. Outputs are
dispatched on the main thread to a `CharacterDirector` (choreography + spring
animation) and an `OverlayController` (a `TYPE_APPLICATION_OVERLAY` window hosting a
Compose canvas). Sessions land in Room; settings live in DataStore; "time reclaimed"
is today's usage vs the trailing 7-day average.

Docs index: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) ·
[ANDROID_PLATFORM.md](docs/ANDROID_PLATFORM.md) · [CHARACTER_SYSTEM.md](docs/CHARACTER_SYSTEM.md) ·
[CHARACTER_ART_PIPELINE.md](docs/CHARACTER_ART_PIPELINE.md) ·
[ANIMATION_SYSTEM.md](docs/ANIMATION_SYSTEM.md) · [PERFORMANCE.md](docs/PERFORMANCE.md) ·
[PERMISSIONS.md](docs/PERMISSIONS.md) · [PRIVACY.md](docs/PRIVACY.md) ·
[PRODUCT_BEHAVIOR.md](docs/PRODUCT_BEHAVIOR.md) · [TESTING.md](docs/TESTING.md) ·
[RELEASE.md](docs/RELEASE.md) · [ASSET_LICENSES.md](docs/ASSET_LICENSES.md)

## Build stack

Kotlin 2.4.20 (AGP 9 built-in Kotlin) · AGP 9.4.1 · Gradle 9.7.1 · JDK 21 ·
compileSdk 37 / targetSdk 36 / minSdk 26 · Compose BOM 2026.09.00 · Room (KSP 2.3.12) ·
DataStore · single `:app` module.
