# DoomBuddy (AntiDoomScrollBuddy)

A character-driven digital-wellbeing companion for Android. Pick **Loui** (deadpan intern),
**Buffer** (receipt-printing robot), **Goop** (dramatic slime), or **Fenn** (fox).
When a tracked app has been open too long, the buddy appears **over** that
app, *stays*, and acts out your elapsed time
(peeking, fidgeting, popcorn, napping) beside a live timer that cycles between
**THIS SESSION** and **TODAY**. It never quietly disappears: you escape with a
deliberate **hold-to-dismiss** (the session keeps counting), and it comes back later.
Leaving the app ends the session, celebrates your escape, and banks the time.

> The character is the product. The timer is part of the character.

Native-first, offline-first, local GLB performers rendered by Filament, no analytics, no
network at runtime. The original trio has ten named clips each: entrances, waiting acts,
prop comedy, meltdowns, pokes, holds, exits and celebrations. Fenn retains the three clips
from “Fox” by Bake3D under CC BY 4.0; attribution is preserved in
`app/src/main/assets/characters/LICENSE.txt`.

**Try the cast:** Settings → Your buddy. All four choices are visible in a two-column
selector. Tap a performance chip to replay an act; swipe the chips for Meltdown and
We escaped! Tap the actor for a poke, or hold for 1.2 seconds to practise dismissal.
Previews return to automatic animation; they do not change your intervention thresholds.
Existing Fenn selections are preserved when updating. New installs start with Loui.

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
keeps Gradle dependency lockfiles and the version catalog in the repository. The asset
generator uses only the Python standard library (reference Python 3.10.12 in
`.python-version`, also verified locally on Python 3.9.6). No pip dependencies or asset
downloads are needed. Install JDK 21, Python 3 and Android SDK API 37/build-tools 35.0.0,
set `JAVA_HOME` and `ANDROID_HOME`, then provision dependencies once:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

That first online test also populates Robolectric's Android runtime cache. Subsequent
normal checks work with the provisioned caches offline:

```bash
make test       # deterministic GLB validation + offline unit tests
make build      # offline debug APK
make lint       # Android lint
make assets     # regenerate the original trio deterministically, no Blender required
```

For a clean Linux build with the Android SDK installed by the project image:

```bash
docker build -t doombuddy-android .
docker run --rm --network none doombuddy-android
docker create --name doombuddy-apk doombuddy-android
docker cp doombuddy-apk:/workspace/app/build/outputs/apk/debug/app-debug.apk ./app-debug.apk
docker rm doombuddy-apk
adb install -r ./app-debug.apk
```

The container builds Android APKs and runs tests; the app itself runs on a phone/emulator.
Do not mount a host `local.properties` over the container SDK configuration. Docker is not
installed in the current development environment; these image changes have not been
executed here. Native build/test commands have been run locally.

`keystore.properties`, local signing material, and machine-specific Gradle/Android files
are intentionally excluded from Git and the Docker context. Release signing is optional
and must be supplied locally. To update an existing signed installation:

```bash
./gradlew :app:assembleRelease --offline
adb devices -l
adb -s PHONE_SERIAL install -r app/build/outputs/apk/release/app-release.apk
```

Use the phone serial shown by ADB when an emulator is also connected; run the APK command
from this project root. The signing key must match the installed app. Do not uninstall
to bypass a signature mismatch, because that would erase saved data.

**Known platform gap:** the pinned Filament 1.51 native libraries trigger Android's 16 KB
page-size compatibility warning on the 16 KB emulator. The app runs there in compatibility
mode. Full 16 KB compliance and Play distribution require a separate native-library upgrade.

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
