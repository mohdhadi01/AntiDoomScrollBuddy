# Asset licenses

The entire shipped visual experience is **procedural**: characters, props, particles, app icon
and status icon are vector/code (Apache-2.0-compatible, authored in this repository).
There are no third-party fonts, images, sounds, or models bundled.

| Asset | Source | License | Commercial use |
|---|---|---|---|
| Character art (3 launch buddies, rig) | authored in-repo (`CharacterRenderer.kt`) | project license | yes |
| Launcher / status icons | authored in-repo (`res/drawable`) | project license | yes |
| Typefaces | system Android fonts (Roboto/Sans) | Apache-2.0 (bundled with Android) | yes |
| Libraries (Compose, Room, DataStore, coroutines) | Google/JetBrains Maven | Apache-2.0 | yes |

## External research reference

The Kenney **Mini Characters** pack was reviewed during the redesign because it provides
3D/GLB/FBX character meshes under CC0. It is not bundled in the shipped APK: the pack is
static source art rather than an Android-ready animation/overlay system, and adding a
full 3D renderer for a small always-on-top widget would increase startup, memory, APK
size, and clipping risk. It remains a documented option for a future authored sprite or
Rive/3D pipeline, not a hidden runtime dependency.

- Source: https://kenney.nl/assets/mini-characters
- License: CC0 / public domain dedication

If sound effects are added later (banks exist in the product spec), each file must be
added here with creator/source/license/attribution + a replacement plan before shipping.
