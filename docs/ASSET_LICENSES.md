# Asset licenses

The shipped character experience uses one local animated fox GLB from Bake3D, plus original
app UI, overlay composition, and copy authored in this repository.

| Asset | Source | License | Commercial use |
|---|---|---|---|
| Fenn fox GLB + fallback portrait | “Fox” by Bake3D | CC BY 4.0 | yes, with attribution |
| Launcher / status icons | authored in-repo (`res/drawable`) | project license | yes |
| Typefaces | system Android fonts (Roboto/Sans) | Apache-2.0 (bundled with Android) | yes |
| Libraries (Compose, Room, DataStore, coroutines) | Google/JetBrains Maven | Apache-2.0 | yes |

## Character source and provenance

The Bake3D **Fox** sample is bundled as `app/src/main/assets/characters/fox.glb` with its
Walk, Tail wag, and Look around clips. The fallback portrait is bundled locally as
`app/src/main/res/drawable-nodpi/fenn_portrait.png`. The Android runtime uses Google Filament
to render the model locally; no network access is required at runtime.

- Source: https://bake3d.ai/free-3d-models
- Asset: “Fox” by Bake3D
- License: CC BY 4.0, https://creativecommons.org/licenses/by/4.0/
- Required attribution: “Fox” by Bake3D (https://bake3d.ai), licensed under CC BY 4.0.

The source license and required attribution are preserved at
`app/src/main/assets/characters/LICENSE.txt`.

If sound effects are added later (banks exist in the product spec), each file must be
added here with creator/source/license/attribution + a replacement plan before shipping.
