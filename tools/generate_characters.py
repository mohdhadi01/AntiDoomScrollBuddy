#!/usr/bin/env python3
"""Build DoomBuddy's original, texture-free animated toy cast using only stdlib.

Run: python3 tools/generate_characters.py [--check]
The GLBs use glTF 2.0 core features; no decoder, extension, image or network needed.
Coordinates: metres-ish, Y up, character fronts +Z. Euler inputs are degrees.
"""

import argparse
import json
import math
from pathlib import Path
import struct
import sys


ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "app/src/main/assets/characters"
CLIPS = ("Idle", "Entrance", "Notice", "Waiting", "Escalate", "Meltdown",
         "Poke", "Hold", "Dismiss", "Celebrate")
DURATIONS = dict(zip(CLIPS, (3.6, 1.0, 1.6, 2.8, 2.4, 2.5, 0.8, 1.4, 0.9, 2.0)))


def canonical(value):
    """Discard platform libm noise before JSON or float32 serialization."""
    if isinstance(value, float):
        return round(value, 7) or 0.0
    if isinstance(value, (list, tuple)):
        return [canonical(v) for v in value]
    if isinstance(value, dict):
        return {key: canonical(v) for key, v in value.items()}
    return value


def quaternion(euler):
    x, y, z = [math.radians(a) / 2 for a in euler]
    sx, sy, sz = math.sin(x), math.sin(y), math.sin(z)
    cx, cy, cz = math.cos(x), math.cos(y), math.cos(z)
    return [sx * cy * cz - cx * sy * sz, cx * sy * cz + sx * cy * sz,
            cx * cy * sz - sx * sy * cz, cx * cy * cz + sx * sy * sz]


def sphere():
    """Smooth unit UV sphere; shared by all rounded organic pieces."""
    positions, normals, indices = [], [], []
    rings, segments = 12, 20
    for j in range(rings + 1):
        v = math.pi * j / rings
        for i in range(segments + 1):
            u = math.tau * i / segments
            p = (math.sin(v) * math.cos(u), math.cos(v), math.sin(v) * math.sin(u))
            positions.extend(p)
            normals.extend(p)
    for j in range(rings):
        for i in range(segments):
            a, b = j * (segments + 1) + i, (j + 1) * (segments + 1) + i
            # Outward winding, viewed from the outside of the sphere.
            if j != 0:
                indices.extend((a, a + 1, b))
            if j != rings - 1:
                indices.extend((a + 1, b + 1, b))
    return positions, normals, indices


def rounded_box():
    """Six gridded faces projected around a .22-radius rounded cube core."""
    positions, normals, indices = [], [], []
    grid = (-1, -.94, -.84, -.78, 0, .78, .84, .94, 1)
    # normal, U, V chosen so U cross V is the outward normal.
    faces = (((1, 0, 0), (0, 1, 0), (0, 0, 1)),
             ((-1, 0, 0), (0, 0, 1), (0, 1, 0)),
             ((0, 1, 0), (0, 0, 1), (1, 0, 0)),
             ((0, -1, 0), (1, 0, 0), (0, 0, 1)),
             ((0, 0, 1), (1, 0, 0), (0, 1, 0)),
             ((0, 0, -1), (0, 1, 0), (1, 0, 0)))
    for n, u, v in faces:
        start = len(positions) // 3
        for y in grid:
            for x in grid:
                raw = [n[k] + x * u[k] + y * v[k] for k in range(3)]
                core = [max(-.78, min(.78, a)) for a in raw]
                delta = [a - b for a, b in zip(raw, core)]
                length = math.sqrt(sum(a * a for a in delta))
                normal = [a / length for a in delta]
                positions.extend(c + .22 * d for c, d in zip(core, normal))
                normals.extend(normal)
        for y in range(8):
            for x in range(8):
                a = start + y * 9 + x
                indices.extend((a, a + 1, a + 9, a + 1, a + 10, a + 9))
    return positions, normals, indices


class Toy:
    def __init__(self, name):
        self.name = name
        self.data = bytearray()
        self.doc = {
            "asset": {"version": "2.0", "generator": "DoomBuddy original toy workshop 1.0",
                      "copyright": "Original DoomBuddy characters; see ORIGINAL_CHARACTERS.md"},
            "scene": 0, "scenes": [{"name": name, "nodes": [0]}],
            "nodes": [{"name": "Root", "children": []}],
            "meshes": [], "materials": [], "accessors": [], "bufferViews": [],
            "animations": [],
            "extras": {"front": "+Z", "up": "+Y", "restBounds": [-1.4, -1.55, -.8, 1.4, 1.55, .8],
                       "animationContract": "Every clip keys the complete shared animated channel set; replace, do not add clips."},
        }
        self.ids = {"Root": 0}
        self.materials = {}
        self.meshes = {}
        self.accessor_cache = {}
        self.geometry = {}
        self.tracks = {clip: {} for clip in CLIPS}
        for shape, generator in (("sphere", sphere), ("box", rounded_box)):
            positions, normals, indices = generator()
            self.geometry[shape] = {
                "attributes": {"POSITION": self.accessor(positions, "VEC3", bounds=True, target=34962),
                               "NORMAL": self.accessor(normals, "VEC3", target=34962)},
                "indices": self.accessor(indices, "SCALAR", component=5123, target=34963),
            }
        self.material("framing", (0, 0, 0, 0), roughness=1)
        # Fully transparent, non-animated bounds mesh. Props can safely start hidden
        # without changing transformToUnitCube's reference framing.
        self.part("Rest framing (invisible)", "Root", (0, 0, 0), (1.4, 1.55, .8), "framing", "box")
        self.joint("Actor", "Root")

    def accessor(self, values, kind, component=5126, bounds=False, target=None):
        count = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[kind]
        values = canonical(list(values))
        packed = struct.pack("<" + ("f" if component == 5126 else "H") * len(values), *values)
        key = (kind, component, bounds, target, packed)
        if key in self.accessor_cache:
            return self.accessor_cache[key]
        self.data.extend(b"\0" * (-len(self.data) % 4))
        view = {"buffer": 0, "byteOffset": len(self.data), "byteLength": len(packed)}
        if target:
            view["target"] = target
        self.doc["bufferViews"].append(view)
        self.data.extend(packed)
        acc = {"bufferView": len(self.doc["bufferViews"]) - 1, "componentType": component,
               "count": len(values) // count, "type": kind}
        if bounds:
            acc["min"] = [min(values[i::count]) for i in range(count)]
            acc["max"] = [max(values[i::count]) for i in range(count)]
        self.doc["accessors"].append(acc)
        result = len(self.doc["accessors"]) - 1
        self.accessor_cache[key] = result
        return result

    def material(self, name, color, roughness=.52, metallic=0, emission=None):
        if len(color) == 3:
            color = (*color, 1)
        mat = {"name": name, "pbrMetallicRoughness": {"baseColorFactor": list(color),
               "metallicFactor": metallic, "roughnessFactor": roughness}}
        if color[3] < 1:
            mat["alphaMode"] = "BLEND"
        if emission:
            mat["emissiveFactor"] = list(emission)
        self.materials[name] = len(self.doc["materials"])
        self.doc["materials"].append(mat)

    def joint(self, name, parent="Actor", t=(0, 0, 0), r=(0, 0, 0), s=(1, 1, 1)):
        if name in self.ids:
            raise ValueError("Duplicate node: " + name)
        idx = len(self.doc["nodes"])
        self.ids[name] = idx
        self.doc["nodes"].append({"name": name, "translation": list(t),
                                 "rotation": quaternion(r), "scale": list(s)})
        self.doc["nodes"][self.ids[parent]].setdefault("children", []).append(idx)
        return name

    def part(self, name, parent, t, s, material, shape="sphere", r=(0, 0, 0)):
        self.joint(name, parent, t, r, s)
        key = (shape, material)
        if key not in self.meshes:
            self.meshes[key] = len(self.doc["meshes"])
            self.doc["meshes"].append({"name": f"{shape} / {material}",
                "primitives": [{**self.geometry[shape], "material": self.materials[material]}]})
        self.doc["nodes"][self.ids[name]]["mesh"] = self.meshes[key]
        return name

    def track(self, clip, node, path, values, times=None):
        if times is None:
            times = [i / (len(values) - 1) for i in range(len(values))]
        if path == "rotation":
            values = [quaternion(v) for v in values]
        self.tracks[clip][(self.ids[node], path)] = (times, values)

    def rotate(self, clip, node, values, times=None):
        self.track(clip, node, "rotation", values, times)

    def move(self, clip, node, values, times=None):
        self.track(clip, node, "translation", values, times)

    def scale(self, clip, node, values, times=None):
        self.track(clip, node, "scale", values, times)

    def finish(self):
        channels = sorted({key for tracks in self.tracks.values() for key in tracks})
        for clip in CLIPS:
            animation = {"name": clip, "samplers": [], "channels": [],
                         "extras": {"loop": clip in ("Idle", "Waiting", "Hold")}}
            for node, path in channels:
                times, values = self.tracks[clip].get((node, path),
                    ([0, 1], [self.doc["nodes"][node][path]] * 2))
                input_acc = self.accessor([t * DURATIONS[clip] for t in times], "SCALAR", bounds=True)
                output_acc = self.accessor([v for point in values for v in point],
                                           "VEC4" if path == "rotation" else "VEC3")
                animation["channels"].append({"sampler": len(animation["samplers"]),
                                              "target": {"node": node, "path": path}})
                animation["samplers"].append({"input": input_acc, "output": output_acc,
                                               "interpolation": "LINEAR"})
            self.doc["animations"].append(animation)
        self.data.extend(b"\0" * (-len(self.data) % 4))
        self.doc["buffers"] = [{"byteLength": len(self.data)}]
        js = json.dumps(canonical(self.doc), separators=(",", ":"), ensure_ascii=True).encode("ascii")
        js += b" " * (-len(js) % 4)
        size = 12 + 8 + len(js) + 8 + len(self.data)
        return (struct.pack("<III", 0x46546C67, 2, size) + struct.pack("<I4s", len(js), b"JSON")
                + js + struct.pack("<I4s", len(self.data), b"BIN\0") + self.data)


def palette(toy):
    toy.material("ink", (.032, .042, .075), .5)
    toy.material("cream", (.97, .91, .76), .63)
    toy.material("white", (.98, .99, 1), .38)
    toy.material("pink", (1, .39, .47), .58)
    toy.material("sole", (.16, .18, .25), .8)


def eyes(toy, parent, x, y, z, size, robot=False):
    for side, sign in (("L", -1), ("R", 1)):
        eye = toy.joint("Eye" + side, parent, (sign * x, y, z))
        toy.part("Eye " + side + " white", eye, (0, 0, 0), (size, size * 1.25, .035),
                 "glow" if robot else "white", "box" if robot else "sphere")
        if not robot:
            toy.part("Pupil " + side, eye, (.012, 0, .035), (size * .48, size * .66, .018), "ink")
            toy.part("Eye " + side + " sparkle", eye, (0, .023, .05), (.013, .018, .006), "white")
        brow = toy.joint("Brow" + side, parent, (sign * x, y + size * 1.7, z - .006))
        toy.part("Brow " + side + " form", brow, (0, 0, 0), (size * .8, .018, .022),
                 "glow" if robot else "ink")
    mouth = toy.joint("Mouth", parent, (0, y - size * 1.9, z + .003))
    toy.part("Smile cavity", mouth, (0, 0, 0), (size * .72, size * .36, .022), "ink")
    toy.part("Smile tongue", mouth, (0, -size * .12, .019), (size * .45, size * .12, .006), "pink")


def face_animation(toy):
    for eye in ("EyeL", "EyeR"):
        toy.scale("Idle", eye, [(1, 1, 1), (1, 1, 1), (1, .08, 1), (1, 1, 1), (1, 1, 1)],
                  [0, .66, .69, .73, 1])
        toy.scale("Poke", eye, [(1, 1, 1), (1.2, 1.25, 1), (1.1, 1.2, 1), (1, 1, 1)])
        toy.scale("Meltdown", eye, [(1, 1, 1), (1, .2, 1), (1, .12, 1), (1, 1, 1)])
        toy.scale("Celebrate", eye, [(1, 1, 1), (1.2, .28, 1), (1.2, .28, 1), (1, 1, 1)])
    toy.scale("Poke", "Mouth", [(1, 1, 1), (.65, 3, 1), (1, 1, 1)])
    toy.scale("Celebrate", "Mouth", [(1, 1, 1), (1.35, 2.3, 1), (1.35, 2.3, 1), (1, 1, 1)])
    toy.rotate("Hold", "BrowL", [(0, 0, 0), (0, 0, -22), (0, 0, -10), (0, 0, 0)])
    toy.rotate("Hold", "BrowR", [(0, 0, 0), (0, 0, 22), (0, 0, 10), (0, 0, 0)])


def common_motion(toy):
    toy.scale("Entrance", "Actor", [(.001, .001, .001), (.75, 1.14, .8), (1.08, .94, 1), (1, 1, 1)],
              [0, .5, .75, 1])
    toy.move("Entrance", "Actor", [(0, -.7, 0), (0, .13, 0), (0, 0, 0)], [0, .6, 1])
    toy.move("Poke", "Actor", [(0, 0, 0), (0, -.08, 0), (0, .25, 0), (0, 0, 0)], [0, .15, .4, 1])
    toy.scale("Poke", "Actor", [(1, 1, 1), (1.08, .88, 1), (.94, 1.07, 1), (1, 1, 1)])
    toy.move("Dismiss", "Actor", [(0, 0, 0), (-.1, .06, 0), (2.9, -.3, 0)], [0, .25, 1])
    toy.scale("Dismiss", "Actor", [(1, 1, 1), (1, 1, 1), (.001, .001, .001)], [0, .35, 1])
    face_animation(toy)


def loui():
    t = Toy("Loui / chronically online intern")
    palette(t)
    t.material("lavender", (.49, .34, .82), .72)
    t.material("hood trim", (.67, .53, .96), .65)
    t.material("skin", (.78, .46, .30), .62)
    t.material("hair", (.055, .037, .073), .57)
    t.material("tie", (.19, .12, .34), .63)
    t.material("coffee", (.23, .10, .045), .36)
    t.joint("Torso", t=(0, -.05, 0))
    t.part("Oversized hoodie", "Torso", (0, -.13, 0), (.57, .59, .34), "lavender", "box")
    t.part("Hood behind neck", "Torso", (0, .39, -.11), (.46, .29, .32), "hood trim")
    t.part("Kangaroo pocket", "Torso", (0, -.34, .325), (.33, .17, .045), "hood trim", "box")
    t.part("Hoodie hem", "Torso", (0, -.66, 0), (.51, .07, .31), "hood trim", "box")
    for sign in (-1, 1):
        t.part("Drawstring " + str(sign), "Torso", (sign * .16, .14, .345), (.012, .13, .014), "cream")
    t.joint("Tie", "Torso", (0, .32, .38), (0, 0, -13))
    t.part("Loose tie knot", "Tie", (0, 0, 0), (.075, .067, .028), "tie", "box")
    t.part("Loose tie blade", "Tie", (0, -.20, .008), (.065, .18, .022), "tie", "box", (0, 0, 7))
    t.joint("Head", "Torso", (0, .78, .035))
    t.part("Head sculpt", "Head", (0, 0, 0), (.41, .43, .32), "skin")
    t.part("Dark hair cap", "Head", (0, .25, -.045), (.435, .27, .325), "hair")
    for i, (x, y, angle) in enumerate(((-.29, .23, -28), (-.14, .31, -20), (.03, .34, 0), (.20, .31, 20))):
        t.part("Sculpted fringe " + str(i), "Head", (x, y, .235), (.15, .15, .10), "hair", r=(0, 0, angle))
    for sign in (-1, 1):
        t.part("Ear " + str(sign), "Head", (sign * .41, -.03, 0), (.075, .105, .065), "skin")
        t.part("Cheek " + str(sign), "Head", (sign * .25, -.13, .258), (.066, .032, .012), "pink")
    t.part("Button nose", "Head", (0, -.055, .326), (.062, .059, .05), "skin")
    eyes(t, "Head", .155, .035, .298, .075)
    for side, sign in (("L", -1), ("R", 1)):
        t.joint("Arm" + side, "Torso", (sign * .48, .24, 0), (0, 0, sign * 12))
        t.part("Sleeve " + side, "Arm" + side, (sign * .10, -.22, 0), (.19, .31, .22), "lavender")
        t.joint("Forearm" + side, "Arm" + side, (sign * .15, -.40, .03))
        t.part("Cuff " + side, "Forearm" + side, (0, -.06, 0), (.16, .16, .17), "hood trim", "box")
        t.part("Mitten " + side, "Forearm" + side, (0, -.24, .04), (.135, .14, .13), "skin")
        t.joint("Leg" + side, t=(sign * .25, -.73, 0))
        t.part("Trouser " + side, "Leg" + side, (0, -.21, 0), (.16, .28, .18), "ink", "box")
        t.part("Sneaker " + side, "Leg" + side, (0, -.48, .10), (.215, .14, .31), "cream", "box")
        t.part("Sole " + side, "Leg" + side, (0, -.58, .10), (.218, .045, .31), "sole", "box")
        for j in range(3):
            t.part(f"Lace {side}{j}", "Leg" + side, (0, -.37, .08 + j * .055), (.10, .009, .012), "white", "box")
    t.joint("Coffee", "ForearmL", (0, -.22, .18), s=(.001, .001, .001))
    t.part("Coffee cup", "Coffee", (0, .08, 0), (.13, .19, .13), "cream", "box")
    t.part("Coffee lid", "Coffee", (0, .28, 0), (.145, .033, .145), "coffee", "box")
    t.part("Coffee sleeve", "Coffee", (0, .06, .125), (.10, .065, .013), "lavender", "box")
    t.joint("Report", "ForearmR", (0, -.22, .18), s=(.001, .001, .001))
    t.part("Report clipboard", "Report", (0, -.03, 0), (.22, .30, .025), "tie", "box")
    t.part("Scrolling report paper", "Report", (0, -.16, .03), (.19, .40, .009), "cream", "box")
    t.part("Paper curl", "Report", (0, -.55, .055), (.19, .04, .04), "cream")
    t.joint("ReportInk", "Report", (0, -.12, .044))
    for j, width in enumerate((.13, .10, .145, .08, .12)):
        t.part("Report line " + str(j), "ReportInk", (0, .22 - j * .11, 0), (width, .013, .004), "lavender", "box")
    common_motion(t)
    t.scale("Idle", "Torso", [(1, 1, 1), (1.025, 1.025, 1.025), (1, 1, 1)])
    t.rotate("Idle", "Head", [(0, 0, 0), (-2, -4, -3), (0, 0, 0)])
    t.rotate("Entrance", "Head", [(12, -18, -18), (0, 8, 8), (0, 0, 0)])
    t.rotate("Entrance", "Tie", [(0, 0, -13), (25, 0, 25), (0, 0, -13)])
    t.rotate("Notice", "ArmR", [(0, 0, 12), (0, 0, 140), (0, 0, 130), (0, 0, 12)])
    t.rotate("Notice", "ForearmR", [(0, 0, 0), (0, 0, 28), (0, 0, -25), (0, 0, 30), (0, 0, 0)])
    t.rotate("Notice", "Head", [(0, 0, 0), (0, -10, -8), (0, 0, 0)])
    t.scale("Waiting", "Coffee", [(.001, .001, .001), (1, 1, 1), (1, 1, 1), (.001, .001, .001)], [0, .15, .85, 1])
    t.rotate("Waiting", "ArmL", [(0, 0, -12), (-40, 0, -35), (-40, 0, -35), (0, 0, -12)])
    t.rotate("Waiting", "ForearmL", [(0, 0, 0), (-88, 0, -10), (-100, 0, -10), (0, 0, 0)])
    t.rotate("Waiting", "Head", [(0, 0, 0), (8, -12, 5), (8, -12, 5), (0, 0, 0)])
    t.rotate("Waiting", "LegR", [(0, 0, 0), (-8, 0, 0), (0, 0, 0), (-8, 0, 0), (0, 0, 0)])
    t.scale("Escalate", "Report", [(.001, .001, .001), (1, 1, 1), (1, 1, 1), (.001, .001, .001)], [0, .16, .87, 1])
    t.rotate("Escalate", "ArmR", [(0, 0, 12), (-35, 0, 65), (-35, 0, 65), (0, 0, 12)])
    t.rotate("Escalate", "ForearmR", [(0, 0, 0), (-25, 0, -55), (-25, 0, -55), (0, 0, 0)])
    t.move("Escalate", "ReportInk", [(0, -.12, .044), (0, -.03, .044), (0, -.12, .044)])
    t.rotate("Escalate", "Head", [(0, 0, 0), (8, 18, -10), (0, -15, 4), (0, 0, 0)])
    t.rotate("Escalate", "ArmL", [(0, 0, -12), (0, 0, -70), (0, 0, -55), (0, 0, -12)])
    t.rotate("Meltdown", "Torso", [(0, 0, 0), (20, 0, -8), (24, 0, -9), (0, 0, 0)])
    t.rotate("Meltdown", "Head", [(0, 0, 0), (32, 0, 12), (30, -12, 15), (0, 0, 0)])
    t.rotate("Meltdown", "ArmL", [(0, 0, -12), (-18, 0, -3), (-18, 0, -3), (0, 0, -12)])
    t.rotate("Meltdown", "Tie", [(0, 0, -13), (-20, 0, 10), (-24, 0, 4), (0, 0, -13)])
    t.rotate("Poke", "ArmL", [(0, 0, -12), (0, 0, -80), (0, 0, -12)])
    t.rotate("Poke", "ArmR", [(0, 0, 12), (0, 0, 80), (0, 0, 12)])
    t.rotate("Hold", "Head", [(0, 0, 0), (0, -8, -5), (0, 8, 5), (0, -8, -5), (0, 0, 0)])
    t.rotate("Hold", "ForearmL", [(0, 0, 0), (-22, 0, -12), (-15, 0, 12), (0, 0, 0)])
    t.rotate("Hold", "Tie", [(0, 0, -13), (0, 0, -23), (0, 0, 0), (0, 0, -13)])
    t.rotate("Dismiss", "Actor", [(0, 0, 0), (0, 35, -8), (0, 85, -13)])
    t.rotate("Dismiss", "LegL", [(0, 0, 0), (-35, 0, 0), (35, 0, 0), (0, 0, 0)])
    t.rotate("Dismiss", "LegR", [(0, 0, 0), (35, 0, 0), (-35, 0, 0), (0, 0, 0)])
    t.move("Celebrate", "Actor", [(0, 0, 0), (0, .24, 0), (0, 0, 0), (0, .32, 0), (0, 0, 0)])
    for side, sign in (("L", -1), ("R", 1)):
        t.rotate("Celebrate", "Arm" + side, [(0, 0, sign * 12), (0, 0, sign * 155),
                 (0, 0, sign * 130), (0, 0, sign * 160), (0, 0, sign * 12)])
        t.rotate("Celebrate", "Forearm" + side, [(0, 0, 0), (0, 0, sign * 20), (0, 0, 0)])
    t.rotate("Celebrate", "Tie", [(0, 0, -13), (30, 0, 20), (0, 0, -25), (30, 0, 10), (0, 0, -13)])
    return t


def buffer():
    t = Toy("Buffer / receipt-printing CRT companion")
    palette(t)
    t.material("teal", (.035, .48, .46), .42, .08)
    t.material("deep teal", (.025, .21, .23), .52)
    t.material("screen", (.012, .055, .065), .22)
    t.material("glow", (.28, 1, .80), .25, emission=(.23, .9, .62))
    t.material("amber", (1, .53, .10), .35, emission=(.5, .18, .015))
    t.joint("Torso", t=(0, -.35, 0))
    t.part("Teal chassis", "Torso", (0, 0, 0), (.43, .34, .28), "teal", "box")
    t.part("Chest panel", "Torso", (0, .02, .27), (.31, .20, .025), "cream", "box")
    t.part("Receipt slot", "Torso", (0, -.13, .30), (.24, .035, .014), "ink", "box")
    for j in range(3):
        t.part("Status lamp " + str(j), "Torso", (-.16 + j * .16, .10, .305), (.035, .035, .012), "amber" if j == 0 else "glow")
    t.joint("Head", "Torso", (0, .91, 0))
    t.part("CRT cream shell", "Head", (0, 0, -.06), (.68, .49, .37), "cream", "box")
    t.part("Teal CRT bezel", "Head", (0, .015, .279), (.61, .415, .075), "teal", "box")
    t.part("Convex dark glass", "Head", (0, .035, .35), (.53, .33, .045), "screen", "box")
    t.part("Glass highlight", "Head", (-.35, .26, .395), (.09, .012, .004), "cream")
    t.part("Power button", "Head", (.49, -.365, .335), (.037, .026, .018), "amber")
    for j in range(3):
        t.part("Speaker slit " + str(j), "Head", (-.40 + j * .06, -.36, .34), (.015, .023, .008), "deep teal", "box")
    eyes(t, "Head", .205, .08, .399, .091, robot=True)
    t.joint("Antenna", "Head", (.25, .45, -.08))
    t.part("Antenna stalk", "Antenna", (0, .14, 0), (.022, .17, .022), "deep teal")
    t.part("Antenna luminous bead", "Antenna", (0, .32, 0), (.073, .073, .073), "amber")
    t.joint("Loading", "Head", (0, -.19, .405), s=(.001, .001, .001))
    for j in range(3):
        t.part("Loading dot " + str(j), "Loading", ((j - 1) * .14, 0, 0), (.03, .03, .009), "glow")
    for side, sign in (("L", -1), ("R", 1)):
        t.joint("Arm" + side, "Torso", (sign * .44, .12, 0))
        t.part("Shoulder socket " + side, "Arm" + side, (sign * .06, 0, 0), (.13, .14, .14), "deep teal")
        t.part("Stubby upper arm " + side, "Arm" + side, (sign * .13, -.13, 0), (.13, .22, .13), "teal", "box")
        t.joint("Forearm" + side, "Arm" + side, (sign * .16, -.28, .01))
        t.part("Cream gripper " + side, "Forearm" + side, (0, -.11, .02), (.17, .15, .15), "cream", "box")
        t.part("Gripper seam " + side, "Forearm" + side, (0, -.13, .166), (.012, .10, .006), "deep teal", "box")
        t.joint("Leg" + side, t=(sign * .25, -.74, 0))
        t.part("Accordion leg " + side, "Leg" + side, (0, -.13, 0), (.105, .21, .12), "deep teal", "box")
        for j in range(3):
            t.part(f"Leg ridge {side}{j}", "Leg" + side, (0, -.04 - j * .085, .005), (.13, .018, .135), "teal", "box")
        t.part("Robot boot " + side, "Leg" + side, (0, -.36, .10), (.23, .15, .29), "teal", "box")
        t.part("Robot boot sole " + side, "Leg" + side, (0, -.47, .10), (.23, .045, .29), "deep teal", "box")
    t.joint("Receipt", "Torso", (0, -.15, .32), s=(1, .001, 1))
    t.part("Extending receipt", "Receipt", (0, -.33, 0), (.21, .33, .013), "cream", "box")
    t.part("Receipt roll curl", "Receipt", (0, -.66, .035), (.21, .04, .055), "cream")
    for j, width in enumerate((.14, .10, .15, .09, .13, .12)):
        t.part("Printed receipt line " + str(j), "Receipt", (0, -.08 - j * .09, .016), (width, .012, .003), "deep teal", "box")
    common_motion(t)
    t.move("Idle", "Head", [(0, .91, 0), (0, .935, 0), (0, .91, 0)])
    t.rotate("Idle", "Antenna", [(0, 0, 0), (0, 0, -6), (0, 0, 0)])
    t.rotate("Entrance", "Head", [(0, -25, -15), (0, 8, 7), (0, 0, 0)])
    t.rotate("Entrance", "Antenna", [(0, 0, 25), (0, 0, -20), (0, 0, 8), (0, 0, 0)])
    t.rotate("Notice", "Head", [(0, 0, 0), (0, -22, -5), (0, 22, 5), (0, 0, 0)])
    t.rotate("Notice", "ArmL", [(0, 0, 0), (0, 0, -145), (0, 0, -125), (0, 0, -145), (0, 0, 0)])
    t.rotate("Notice", "ForearmL", [(0, 0, 0), (0, 20, 0), (0, -20, 0), (0, 0, 0)])
    t.scale("Waiting", "Loading", [(.001, .001, .001), (1, 1, 1), (1, 1, 1), (.001, .001, .001)], [0, .1, .9, 1])
    for side, delay in (("L", 0), ("R", 1)):
        values = [(1, 1, 1), (1, .16 if delay == 0 else .65, 1), (1, .65 if delay == 0 else .16, 1),
                  (1, .16 if delay == 0 else .65, 1), (1, 1, 1)]
        t.scale("Waiting", "Eye" + side, values)
    for j in range(3):
        t.scale("Waiting", "Loading dot " + str(j),
                [(.03, .03, .009), (.03, .055 if j == 0 else .015, .009),
                 (.03, .055 if j == 1 else .015, .009), (.03, .055 if j == 2 else .015, .009), (.03, .03, .009)])
    t.rotate("Waiting", "Antenna", [(0, 0, 0), (0, 0, 14), (0, 0, -14), (0, 0, 0)])
    t.scale("Escalate", "Receipt", [(1, .001, 1), (1, .35, 1), (1, .7, 1), (1, 1, 1), (1, 1, 1), (1, .001, 1)],
            [0, .18, .35, .55, .86, 1])
    t.rotate("Escalate", "Head", [(0, 0, 0), (12, 0, 0), (10, 0, -5), (12, 0, 5), (0, 0, 0)])
    t.rotate("Escalate", "ArmR", [(0, 0, 0), (-30, 0, 65), (-30, 0, 70), (0, 0, 0)])
    t.rotate("Escalate", "ForearmR", [(0, 0, 0), (-45, 0, -40), (-45, 0, -40), (0, 0, 0)])
    t.rotate("Meltdown", "Head", [(0, 0, 0), (15, 0, 16), (22, 0, -13), (25, 0, 18), (0, 0, 0)])
    t.rotate("Meltdown", "Antenna", [(0, 0, 0), (0, 0, 45), (0, 0, -30), (0, 0, 25), (0, 0, 0)])
    for side in ("L", "R"):
        t.scale("Meltdown", "Eye" + side, [(1, 1, 1), (1, .07, 1), (.001, .001, .001),
                (1, .07, 1), (.001, .001, .001), (1, 1, 1)], [0, .25, .4, .65, .7, 1])
    t.scale("Meltdown", "Mouth", [(1, 1, 1), (.001, .001, .001), (.001, .001, .001), (1, 1, 1)])
    t.move("Meltdown", "Torso", [(0, -.35, 0), (0, -.52, 0), (0, -.52, 0), (0, -.35, 0)])
    t.rotate("Poke", "Antenna", [(0, 0, 0), (0, 0, -30), (0, 0, 22), (0, 0, 0)])
    t.move("Poke", "Head", [(0, .91, 0), (0, 1.07, 0), (0, .91, 0)])
    t.rotate("Poke", "ArmR", [(0, 0, 0), (0, 0, 80), (0, 0, 0)])
    t.rotate("Hold", "Head", [(0, 0, 0), (0, 0, -5), (0, 0, 5), (0, 0, -5), (0, 0, 0)])
    t.rotate("Hold", "Antenna", [(0, 0, 0), (0, 0, 12), (0, 0, -12), (0, 0, 12), (0, 0, 0)])
    t.rotate("Hold", "ForearmL", [(0, 0, 0), (-22, 0, -12), (-15, 0, 12), (0, 0, 0)])
    t.rotate("Dismiss", "Actor", [(0, 0, 0), (0, 0, -16), (0, 0, -22)])
    t.rotate("Dismiss", "ArmR", [(0, 0, 0), (0, 0, 145), (0, 0, 120)])
    t.rotate("Dismiss", "LegL", [(0, 0, 0), (-20, 0, 0), (20, 0, 0), (-20, 0, 0)])
    t.rotate("Dismiss", "LegR", [(0, 0, 0), (20, 0, 0), (-20, 0, 0), (20, 0, 0)])
    t.move("Celebrate", "Actor", [(0, 0, 0), (0, .19, 0), (0, 0, 0), (0, .26, 0), (0, 0, 0)])
    t.rotate("Celebrate", "Head", [(0, 0, 0), (0, -12, -10), (0, 12, 10), (0, -12, -10), (0, 0, 0)])
    for side, sign in (("L", -1), ("R", 1)):
        t.rotate("Celebrate", "Arm" + side, [(0, 0, 0), (0, 0, sign * 155), (0, 0, sign * 120),
                 (0, 0, sign * 155), (0, 0, 0)])
    t.rotate("Celebrate", "Antenna", [(0, 0, 0), (0, 0, 18), (0, 0, -18), (0, 0, 18), (0, 0, 0)])
    return t


def goop():
    t = Toy("Goop / impatient lime jelly")
    palette(t)
    # Opaque glossy jelly avoids transparency sorting artifacts on mobile GPUs.
    t.material("lime", (.55, .84, .065), .26)
    t.material("jelly light", (.76, 1, .22), .3)
    t.material("jelly shade", (.30, .54, .045), .43)
    t.joint("Body", t=(0, -.20, 0))
    t.part("Jelly pear body", "Body", (0, -.05, 0), (.73, .82, .45), "lime")
    t.part("Jelly crown", "Body", (-.10, .48, -.01), (.46, .44, .37), "lime")
    t.part("Sculpted forehead glint", "Body", (-.30, .53, .31), (.12, .21, .023), "jelly light", r=(0, 0, -25))
    t.part("Little glint", "Body", (-.43, .31, .385), (.042, .065, .015), "cream")
    for j, x in enumerate((-.46, -.15, .19, .47)):
        t.joint("Lobe" + str(j), "Body", (x, -.69, .03))
        t.part("Squishy foot " + str(j), "Lobe" + str(j), (0, -.03, .04), (.25, .20, .32), "lime")
    t.joint("Tuft", "Body", (.16, .77, -.03))
    t.part("Jelly curl", "Tuft", (.02, .05, 0), (.105, .20, .09), "jelly light", r=(0, 0, -28))
    t.joint("Face", "Body", (0, .18, .014))
    eyes(t, "Face", .20, .06, .423, .115)
    for sign in (-1, 1):
        t.part("Jelly cheek " + str(sign), "Face", (sign * .37, -.13, .36), (.09, .045, .017), "pink")
        t.part("Jelly freckle " + str(sign), "Face", (sign * .43, -.03, .33), (.018, .018, .009), "jelly shade")
    for side, sign in (("L", -1), ("R", 1)):
        t.joint("Hand" + side, t=(sign * .91, -.10, .04))
        t.part("Floating mitten " + side, "Hand" + side, (0, 0, 0), (.19, .20, .16), "lime")
        t.part("Floating thumb " + side, "Hand" + side, (-sign * .13, .04, .075), (.08, .10, .09), "jelly light")
    t.joint("Flag", "HandR", (.02, .02, .06), s=(.001, .001, .001))
    t.part("White flag pole", "Flag", (0, .25, 0), (.017, .41, .017), "cream")
    t.part("Flag pole cap", "Flag", (0, .68, 0), (.03, .03, .03), "cream")
    t.joint("FlagCloth", "Flag", (0, .50, 0))
    t.part("White surrender flag near", "FlagCloth", (-.14, 0, 0), (.14, .14, .014), "white", "box")
    t.joint("FlagTip", "FlagCloth", (-.27, 0, 0))
    t.part("White surrender flag tip", "FlagTip", (-.12, .018, 0), (.13, .14, .014), "white", "box", (0, 0, -7))
    common_motion(t)
    t.scale("Idle", "Body", [(1, 1, 1), (1.035, .97, 1.025), (1, 1, 1)])
    t.rotate("Idle", "Tuft", [(0, 0, 0), (0, 0, 12), (0, 0, 0)])
    for side, sign in (("L", -1), ("R", 1)):
        t.move("Idle", "Hand" + side, [(sign * .91, -.1, .04), (sign * .94, -.04, .04), (sign * .91, -.1, .04)])
    t.scale("Entrance", "Body", [(1.4, .45, 1.2), (.8, 1.16, .9), (1.1, .9, 1), (1, 1, 1)])
    t.rotate("Entrance", "Tuft", [(0, 0, 35), (0, 0, -20), (0, 0, 0)])
    t.move("Notice", "HandL", [(-.91, -.1, .04), (-.90, .54, .04), (-.82, .60, .04), (-.91, -.1, .04)])
    t.rotate("Notice", "HandL", [(0, 0, 0), (0, 0, -25), (0, 0, 25), (0, 0, -25), (0, 0, 0)])
    t.rotate("Notice", "Face", [(0, 0, 0), (0, -8, -6), (0, 0, 0)])
    t.move("Waiting", "Body", [(0, -.2, 0), (0, -.28, 0), (0, -.06, 0), (0, -.28, 0), (0, -.2, 0)])
    t.scale("Waiting", "Body", [(1, 1, 1), (1.08, .9, 1), (.94, 1.08, 1), (1.08, .9, 1), (1, 1, 1)])
    t.rotate("Waiting", "BrowL", [(0, 0, 0), (0, 0, -18), (0, 0, -18), (0, 0, 0)])
    t.move("Waiting", "HandR", [(.91, -.1, .04), (.86, -.27, .10), (.90, -.06, .10), (.86, -.27, .10), (.91, -.1, .04)])
    t.rotate("Waiting", "Lobe3", [(0, 0, 0), (-15, 0, 0), (0, 0, 0), (-15, 0, 0), (0, 0, 0)])
    t.scale("Escalate", "Flag", [(.001, .001, .001), (1, 1, 1), (1, 1, 1), (.001, .001, .001)], [0, .12, .88, 1])
    t.move("Escalate", "HandR", [(.91, -.1, .04), (.84, .18, .04), (.91, .25, .04), (.84, .18, .04), (.91, -.1, .04)])
    t.rotate("Escalate", "HandR", [(0, 0, 0), (0, 0, 18), (0, 0, -18), (0, 0, 18), (0, 0, 0)])
    t.rotate("Escalate", "FlagCloth", [(0, 0, 0), (0, 22, 0), (0, -22, 0), (0, 22, 0), (0, 0, 0)])
    t.rotate("Escalate", "FlagTip", [(0, 0, 0), (0, -35, 0), (0, 35, 0), (0, -35, 0), (0, 0, 0)])
    t.scale("Escalate", "Mouth", [(1, 1, 1), (.75, 2.3, 1), (.75, 2.3, 1), (1, 1, 1)])
    t.scale("Meltdown", "Body", [(1, 1, 1), (1.55, .22, 1.25), (1.55, .22, 1.25), (1, 1, 1)])
    t.move("Meltdown", "Body", [(0, -.2, 0), (0, -.84, 0), (0, -.84, 0), (0, -.2, 0)])
    for side, sign in (("L", -1), ("R", 1)):
        t.move("Meltdown", "Hand" + side, [(sign * .91, -.1, .04), (sign * 1.08, -.93, .07),
                 (sign * 1.08, -.93, .07), (sign * .91, -.1, .04)])
        t.scale("Meltdown", "Hand" + side, [(1, 1, 1), (1.2, .4, 1), (1.2, .4, 1), (1, 1, 1)])
        t.move("Poke", "Hand" + side, [(sign * .91, -.1, .04), (sign * 1.1, .32, .04), (sign * .91, -.1, .04)])
    t.rotate("Meltdown", "Tuft", [(0, 0, 0), (0, 0, 65), (0, 0, 65), (0, 0, 0)])
    t.scale("Poke", "Body", [(1, 1, 1), (.85, 1.18, .9), (1.1, .9, 1), (1, 1, 1)])
    t.rotate("Poke", "Tuft", [(0, 0, 0), (0, 0, -35), (0, 0, 20), (0, 0, 0)])
    t.scale("Hold", "Body", [(1, 1, 1), (1.07, .95, 1), (.95, 1.05, 1), (1.07, .95, 1), (1, 1, 1)])
    t.rotate("Hold", "Tuft", [(0, 0, 0), (0, 0, 18), (0, 0, -18), (0, 0, 18), (0, 0, 0)])
    t.rotate("Hold", "HandL", [(0, 0, 0), (0, 0, -20), (0, 0, 20), (0, 0, -20), (0, 0, 0)])
    t.move("Dismiss", "Actor", [(0, 0, 0), (0, -.12, 0), (0, -2.7, 0)], [0, .3, 1])
    t.scale("Dismiss", "Body", [(1, 1, 1), (1.25, .6, 1.1), (.7, 1.4, .8)])
    t.move("Dismiss", "HandL", [(-.91, -.1, .04), (-.85, .3, .04), (-.60, -.4, .04)])
    t.rotate("Dismiss", "HandL", [(0, 0, 0), (0, 0, 25), (0, 0, -20), (0, 0, 10)])
    t.move("Celebrate", "Actor", [(0, 0, 0), (0, .27, 0), (0, 0, 0), (0, .35, 0), (0, 0, 0)])
    t.scale("Celebrate", "Body", [(1, 1, 1), (.9, 1.1, 1), (1.1, .87, 1), (.9, 1.13, 1), (1, 1, 1)])
    for side, sign in (("L", -1), ("R", 1)):
        t.move("Celebrate", "Hand" + side, [(sign * .91, -.1, .04), (sign * .77, .78, .04),
                 (sign * .91, .44, .04), (sign * .77, .85, .04), (sign * .91, -.1, .04)])
        t.rotate("Celebrate", "Hand" + side, [(0, 0, 0), (0, 0, sign * 30), (0, 0, -sign * 25), (0, 0, 0)])
    t.rotate("Celebrate", "Tuft", [(0, 0, 0), (0, 0, -25), (0, 0, 25), (0, 0, -25), (0, 0, 0)])
    return t


def validate(blob):
    """Validate local GLB structure, mesh data, and complete reset-safe clip sets."""
    magic, version, length = struct.unpack_from("<III", blob)
    assert (magic, version, length) == (0x46546C67, 2, len(blob))
    js_length, js_type = struct.unpack_from("<I4s", blob, 12)
    assert js_type == b"JSON" and js_length % 4 == 0
    doc = json.loads(blob[20:20 + js_length])
    bin_length, bin_type = struct.unpack_from("<I4s", blob, 20 + js_length)
    binary = blob[28 + js_length:]
    assert bin_type == b"BIN\0" and bin_length == len(binary) == doc["buffers"][0]["byteLength"]
    assert not any(key in doc for key in ("images", "textures", "extensionsRequired"))
    assert all("uri" not in buffer for buffer in doc["buffers"])
    visited = set()

    def visit(index):
        assert 0 <= index < len(doc["nodes"]) and index not in visited, "Invalid node tree"
        visited.add(index)
        for child in doc["nodes"][index].get("children", []):
            visit(child)

    visit(0)
    assert len(visited) == len(doc["nodes"])

    def values(index):
        acc = doc["accessors"][index]
        view = doc["bufferViews"][acc["bufferView"]]
        n = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[acc["type"]]
        size, char = (4, "f") if acc["componentType"] == 5126 else (2, "H")
        assert view["byteOffset"] % 4 == 0
        assert view["byteLength"] == acc["count"] * n * size
        assert view["byteOffset"] + view["byteLength"] <= len(binary)
        data = struct.unpack_from("<" + char * (acc["count"] * n), binary, view["byteOffset"])
        assert all(math.isfinite(v) for v in data)
        if "min" in acc:
            for axis in range(n):
                assert abs(min(data[axis::n]) - acc["min"][axis]) < 1e-5
                assert abs(max(data[axis::n]) - acc["max"][axis]) < 1e-5
        return [data[i:i + n] for i in range(0, len(data), n)]

    for i in range(len(doc["accessors"])):
        values(i)
    for mesh in doc["meshes"]:
        for primitive in mesh["primitives"]:
            vertices = values(primitive["attributes"]["POSITION"])
            normals = values(primitive["attributes"]["NORMAL"])
            indices = [v[0] for v in values(primitive["indices"])]
            assert len(vertices) == len(normals) and len(indices) % 3 == 0
            assert max(indices) < len(vertices)
            assert all(abs(sum(v * v for v in n) - 1) < 1e-5 for n in normals)
            for i in range(0, len(indices), 3):
                a, b, c = (vertices[j] for j in indices[i:i + 3])
                u, v = [b[k] - a[k] for k in range(3)], [c[k] - a[k] for k in range(3)]
                cross = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
                assert sum(cross[k] * normals[indices[i]][k] for k in range(3)) > 0, "Inward or degenerate triangle"
    assert [a["name"] for a in doc["animations"]] == list(CLIPS)
    expected_targets = None
    for animation in doc["animations"]:
        targets = {(c["target"]["node"], c["target"]["path"]) for c in animation["channels"]}
        assert len(targets) == len(animation["channels"])
        assert expected_targets is None or targets == expected_targets, "Clip could leave stale transforms"
        expected_targets = targets
        moving = set()
        for channel in animation["channels"]:
            sampler = animation["samplers"][channel["sampler"]]
            times, output = values(sampler["input"]), values(sampler["output"])
            assert len(times) == len(output) and times[0][0] == 0
            assert abs(times[-1][0] - DURATIONS[animation["name"]]) < 1e-5
            assert all(a[0] < b[0] for a, b in zip(times, times[1:]))
            if len(set(output)) > 1:
                moving.add(doc["nodes"][channel["target"]["node"]]["name"])
            if animation["extras"]["loop"]:
                assert output[0] == output[-1], "Loop seam is not at rest"
            if channel["target"]["path"] == "rotation":
                assert all(abs(sum(x * x for x in q) - 1) < 1e-5 for q in output)
        assert len(moving - {"Actor"}) >= 2, "Clip needs articulated acting"
    return doc


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Validate and compare bundled GLBs without modifying files")
    args = parser.parse_args()
    total = 0
    for name, make in (("loui", loui), ("buffer", buffer), ("goop", goop)):
        asset = bytes(make().finish())
        doc = validate(asset)
        path = DESTINATION / (name + ".glb")
        if args.check:
            if not path.is_file() or path.read_bytes() != asset:
                print(f"OUT OF DATE: {path.relative_to(ROOT)}", file=sys.stderr)
                return 1
            validate(path.read_bytes())
        else:
            DESTINATION.mkdir(parents=True, exist_ok=True)
            path.write_bytes(asset)
        total += len(asset)
        print(f"{'OK' if args.check else 'WROTE'} {path.relative_to(ROOT)}: {len(asset):,} bytes; "
              f"{len(doc['nodes'])} nodes; {len(doc['animations'])} clips; "
              f"{len(doc['animations'][0]['channels'])} reset-safe channels/clip")
    print(f"Total: {total:,} bytes. Clips: {', '.join(CLIPS)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
