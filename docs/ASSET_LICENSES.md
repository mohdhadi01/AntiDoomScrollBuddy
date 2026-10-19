# Asset licenses

The entire visual experience is **procedural**: characters, props, particles, app icon
and status icon are vector/code (Apache-2.0-compatible, authored in this repository).
There are no third-party fonts, images, sounds, or models bundled.

| Asset | Source | License | Commercial use |
|---|---|---|---|
| Character art (6 buddies, rig) | authored in-repo (`CharacterRenderer.kt`) | project license | yes |
| Launcher / status icons | authored in-repo (`res/drawable`) | project license | yes |
| Typefaces | system Android fonts (Roboto/Sans) | Apache-2.0 (bundled with Android) | yes |
| Libraries (Compose, Room, DataStore, coroutines) | Google/JetBrains Maven | Apache-2.0 | yes |

If sound effects are added later (banks exist in the product spec), each file must be
added here with creator/source/license/attribution + a replacement plan before shipping.
