# DoomBuddy original toy cast

Loui, Buffer, and Goop are original characters created for DoomBuddy. Their
character designs, meshes, material palettes, rigs, props, and keyframed
performances are authored in `tools/generate_characters.py`. No third-party model,
texture, animation, font, reference asset, or downloaded geometry is incorporated.
The neighboring `fox.glb` and `LICENSE.txt` are separate pre-existing assets and
are not covered by this statement.

## Original asset license

Copyright (c) 2026 DoomBuddy contributors.

Permission is hereby granted, free of charge, to any person obtaining a copy of
these original character assets and their generator, to use, copy, modify, merge,
publish, distribute, sublicense, and/or sell copies, and to permit persons to whom
the assets or generator are furnished to do so, subject to the following condition:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the assets or generator.

THE ASSETS AND GENERATOR ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE ASSETS OR GENERATOR OR THE USE OR OTHER
DEALINGS IN THEM.

## Reproduction and integration

From the project root, using Python 3.9 or newer and no installed packages:

```sh
python3 tools/generate_characters.py
python3 tools/generate_characters.py --check
```

The check is read-only, rebuilds in memory, validates GLB headers, buffers,
accessors, outward triangle winding, unit normals, animation timestamps and
quaternions, complete shared channel sets, seamless loop endpoints, articulated
acting, and byte-compares every bundled model. Floating-point values are rounded
before serialization to discard platform math-library noise.
It returns nonzero for missing, changed, or stale assets. No network is used.

All models face **+Z**, with **+Y up**. A fixed, fully transparent root-level
rounded box establishes rest-space bounds **X [-1.4, 1.4], Y [-1.55, 1.55],
Z [-0.8, 0.8]**. Keep it when computing bounds or calling `transformToUnitCube`;
it makes framing independent of initially collapsed prop nodes. Do not recenter
or recompute bounds per animation frame. The actors intentionally have breathing
room, and Goop is shorter. Jumps, raised arms, and exits may exceed rest bounds;
camera framing should leave a small animation margin. The invisible framing
material uses standard glTF alpha blending and never needs to be made visible.
All visible parts are opaque PBR with smooth vertex normals; Buffer adds core
glTF emissive colors. No textures, skinning, morphs, extensions, or decoders are
required. These are rigid articulated toy rigs, with scale-based jelly deformation.

Each file has exactly these case-sensitive clips, in this order:

| Clip | Seconds | Performance |
| --- | ---: | --- |
| Idle | 3.6 | Breathing or hover, blinks, antenna/curl settling |
| Entrance | 1.0 | Overshooting pop with head or jelly follow-through |
| Notice | 1.6 | Articulated wave and looking toward the viewer |
| Waiting | 2.8 | Loui coffee break/foot tap; Buffer loading eyes/dots; Goop impatient bounce |
| Escalate | 2.4 | Loui scrolling report; Buffer extending receipt; Goop fluttering white flag |
| Meltdown | 2.5 | Loui slumps; Buffer flickers/reboots; Goop melts into a puddle |
| Poke | 0.8 | Startled hop, enlarged eyes, hands/antenna/curl recoil |
| Hold | 1.4 | Nervous hands, brows, head and secondary wiggle |
| Dismiss | 0.9 | Loui walks out; Buffer scoots and waves; Goop sinks and waves |
| Celebrate | 2.0 | Joyful repeated jumps, raised hands, smiling squinted eyes |

`Idle`, `Waiting`, and `Hold` are designed to loop; other clips play once. Every
clip keys the exact same union of animated node TRS channels, including constant
rest keys for unused channels. Switching clips therefore resets props and face
state without depending on previous playback. Apply one active clip at a time
(or use the renderer's normal replacement crossfade). A one-shot `Dismiss` ends
offstage at near-zero scale intentionally; starting any other clip restores it.
Animation `extras.loop` is advisory metadata; the host controls looping.
