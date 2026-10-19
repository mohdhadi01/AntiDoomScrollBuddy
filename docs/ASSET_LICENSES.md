# Asset licenses

The shipped character experience uses local GLB models from Kenney's Mini Characters pack,
plus original app UI, overlay composition, and copy authored in this repository.

| Asset | Source | License | Commercial use |
|---|---|---|---|
| Character art (10 GLB buddies) | Kenney Mini Characters | CC0 | yes |
| Launcher / status icons | authored in-repo (`res/drawable`) | project license | yes |
| Typefaces | system Android fonts (Roboto/Sans) | Apache-2.0 (bundled with Android) | yes |
| Libraries (Compose, Room, DataStore, coroutines) | Google/JetBrains Maven | Apache-2.0 | yes |

## Character source and provenance

The Kenney **Mini Characters** pack is bundled in `app/src/main/assets/characters/` as
optimized GLB files. The pack provides rigged 3D characters and animation clips under CC0.
The Android runtime uses Google Filament to render the models locally; no network access is
required at runtime.

- Source: https://kenney.nl/assets/mini-characters
- License: CC0 / public domain dedication

The original source license is preserved at `app/src/main/assets/characters/LICENSE.txt`.

If sound effects are added later (banks exist in the product spec), each file must be
added here with creator/source/license/attribution + a replacement plan before shipping.
