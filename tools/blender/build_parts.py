"""
Baut alle Kreisel-Teile als GLB-Modelle in Blender (headless).

    blender -b --factory-startup -P tools/blender/build_parts.py -- --out src/frontend/public/models
    blender -b --factory-startup -P tools/blender/build_parts.py -- --out ... --preview tools/blender/preview.png
    (ohne GPU zusätzlich --engine CYCLES)

Quelle der Designs (Form, Farben, Klingenzahl) ist src/frontend/src/game/partDesigns.ts, die Dateinamen
kommen aus src/backend/src/main/resources/parts/*.json. Das Hells-Scythe-Set bleibt unverändert.

Aufbau je Kategorie in wheels.py, tracks.py und tips.py, gemeinsame Helfer in geometry.py, Logos in logos.py.

Maße wie beim Hells-Scythe-Set (Blender-Einheiten, Z nach oben, Achse im Ursprung). Die Teile
überlappen bewusst, damit jede Kombination ineinandergreift:
    Tip           r <= 0.08   z 0.00 - 0.30 (Schaft ab 0.14)
    Spin Track    r <= 0.18   z 0.12 - 0.25
    Fusion Wheel  r <= 0.25   z 0.21 - 0.36 (Hörner und Türme bis 0.39)
"""
import json
import os
import re
import sys

import bpy

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from tips import build_tip  # noqa: E402
from tracks import build_track  # noqa: E402
from wheels import build_wheel  # noqa: E402

BUILDERS = {"fusion-wheels": build_wheel, "spin-tracks": build_track, "tips": build_tip}
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DESIGNS_TS = os.path.join(ROOT, "src", "frontend", "src", "game", "partDesigns.ts")
PARTS_DIR = os.path.join(ROOT, "src", "backend", "src", "main", "resources", "parts")
KEEP = {"hells-scythe-blade", "hells-scythe-ratchet", "hells-scythe-bit"}


# ---------------------------------------------------------------- Designs aus TypeScript lesen

def ts_object(source, const_name):
    m = re.search(r"export const " + const_name + r"[^=]*=\s*\{", source)
    if not m:
        raise ValueError(const_name + " nicht gefunden")
    depth, i = 1, m.end()
    while depth:
        depth += {"{": 1, "}": -1}.get(source[i], 0)
        i += 1
    body = "{" + source[m.end():i]
    body = re.sub(r"'([^']*)'", r'"\1"', body)
    body = re.sub(r"([{,]\s*)([A-Za-z_][\w-]*)\s*:", r'\1"\2":', body)
    body = re.sub(r",(\s*[}\]])", r"\1", body)
    return json.loads(body)


def load_designs():
    with open(DESIGNS_TS, encoding="utf-8") as f:
        src = f.read()
    return {
        "fusion-wheels": ts_object(src, "WHEEL_DESIGNS"),
        "spin-tracks": ts_object(src, "TRACK_DESIGNS"),
        "tips": ts_object(src, "TIP_DESIGNS"),
    }


def load_catalog():
    out = []
    for kind in ("fusion-wheels", "spin-tracks", "tips"):
        with open(os.path.join(PARTS_DIR, kind + ".json"), encoding="utf-8") as f:
            for part in json.load(f):
                path = part["modelPath"]
                key = os.path.splitext(os.path.basename(path))[0]
                out.append((kind, key, path, part["name"]))
    return out


# ---------------------------------------------------------------- Ablauf

def export(col, path):
    bpy.ops.object.select_all(action="DESELECT")
    for o in col.objects:
        if not o.hide_render:
            o.select_set(True)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", use_selection=True, export_apply=True,
                              export_yup=True, export_materials="EXPORT")


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    out = argv[argv.index("--out") + 1] if "--out" in argv else os.path.join(ROOT, "src", "frontend", "public", "models")
    preview = argv[argv.index("--preview") + 1] if "--preview" in argv else None
    engine = argv[argv.index("--engine") + 1] if "--engine" in argv else None

    bpy.ops.wm.read_factory_settings(use_empty=True)
    designs = load_designs()
    built = []
    for kind, key, model_path, name in load_catalog():
        if key in KEEP:
            continue
        design = designs[kind].get(key)
        if design is None:
            print("WARNUNG: kein Design für", key)
            continue
        col = bpy.data.collections.new(key)
        bpy.context.scene.collection.children.link(col)
        BUILDERS[kind](design, col, key)
        path = os.path.join(out, model_path)
        export(col, path)
        built.append((kind, key, col))
        print("OK", model_path, round(os.path.getsize(path) / 1024), "KB")

    if preview:
        import render_preview
        render_preview.render(built, os.path.join(ROOT, "src", "frontend", "public", "models"), preview, engine)


if __name__ == "__main__":
    main()
