# Release

## Builds

- Debug: `./gradlew :app:assembleDebug` (applicationId `com.doombuddy.debug`).
- Release: `./gradlew :app:assembleRelease` — R8 minify + resource shrink ON;
  unsigned until `signingConfig` is provided (add keystore + env-based credentials;
  never commit them).
- Version: `versionCode`/`versionName` in `app/build.gradle.kts` (currently 1 / 0.1.0).

## Store readiness checklist

- [ ] Signing config + Play App Signing enrollment
- [ ] Privacy policy URL + Data safety form (local-only, see PRIVACY.md)
- [ ] Usage-access prominent disclosure screenshot for review
- [ ] FGS specialUse subtype justification text (in manifest; Play reviews it)
- [ ] targetSdk 36 (meets Aug 2026 Play requirement), minSdk 26
- [ ] Battery-optimization guidance page for OEM devices (user-initiated)
- [ ] Store listing: icon (in-repo), 2.5D feature graphic, screen recordings of the
      buddy acting over real apps — the character IS the marketing
- [ ] Pre-launch report on physical mid/low-tier devices (see TESTING.md matrix)

## Known limitations (honest list)

- Scroll-gesture detection (dizzy-while-scrolling) would require accessibility APIs —
  deliberately not used; reactions are time/app-based instead.
- Usage access on some aggressive OEMs can be silently reset by battery managers;
  a health-check row on Home (permission re-check + deep link) is the mitigation path.
- No arbitrary-package tracking in V1 (curated list) — by design for Play safety.
