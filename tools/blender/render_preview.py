"""Kontaktblatt aller Teile (plus Hells-Scythe zum Vergleich und ein paar fertige Kombinationen)."""
import math
import os

import bpy
from mathutils import Vector

ROWS = ("fusion-wheels", "spin-tracks", "tips")
ORIGINALS = {
    "fusion-wheels": "hells-scythe-blade",
    "spin-tracks": "hells-scythe-ratchet",
    "tips": "hells-scythe-bit",
}
STEP = 0.62
ROW_GAP = 0.95


def import_original(models_dir, kind, key):
    col = bpy.data.collections.new("orig-" + key)
    bpy.context.scene.collection.children.link(col)
    layer = bpy.context.view_layer.layer_collection.children[col.name]
    bpy.context.view_layer.active_layer_collection = layer
    bpy.ops.import_scene.gltf(filepath=os.path.join(models_dir, kind, key + ".glb"))
    for o in col.objects:
        if "Icosphere" in o.name:
            o.hide_render = True
    bpy.context.view_layer.active_layer_collection = bpy.context.view_layer.layer_collection
    return col


def instance(col, location, name):
    e = bpy.data.objects.new(name, None)
    e.instance_type = "COLLECTION"
    e.instance_collection = col
    e.location = location
    bpy.context.scene.collection.objects.link(e)
    return e


def label(text, location):
    curve = bpy.data.curves.new("Label", "FONT")
    curve.body = text
    curve.size = 0.055
    curve.align_x = "CENTER"
    obj = bpy.data.objects.new("Label", curve)
    obj.location = location
    obj.rotation_euler = (math.radians(50), 0, 0)
    mat = bpy.data.materials.new("Schrift")
    mat.use_nodes = True
    b = mat.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (0.8, 0.8, 0.82, 1)
    b.inputs["Emission Color"].default_value = (0.8, 0.8, 0.82, 1)
    b.inputs["Emission Strength"].default_value = 1.0
    curve.materials.append(mat)
    bpy.context.scene.collection.objects.link(obj)


def area(name, location, rotation, energy, size):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = energy
    data.size = size
    obj = bpy.data.objects.new(name, data)
    obj.location = location
    obj.rotation_euler = rotation
    bpy.context.scene.collection.objects.link(obj)


def set_engine(scene, engine):
    """Eevee braucht eine GPU; ohne (z. B. bei gesperrtem Bildschirm) mit --engine CYCLES auf der CPU rendern."""
    if engine == "CYCLES":
        scene.render.engine = "CYCLES"
        scene.cycles.device = "CPU"
        scene.cycles.samples = 48
        scene.cycles.use_denoising = True
        return
    for name in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT"):
        try:
            scene.render.engine = name
            return
        except TypeError:
            continue


def render(built, models_dir, out_path, engine=None):
    scene = bpy.context.scene
    by_kind = {k: [] for k in ROWS}
    for kind, key, col in built:
        by_kind[kind].append((key, col))
    for kind in ROWS:
        by_kind[kind].insert(0, (ORIGINALS[kind], import_original(models_dir, kind, ORIGINALS[kind])))

    # Sammlungen aus der Szene nehmen, nur noch als Instanzen zeigen
    for kind in ROWS:
        for _, col in by_kind[kind]:
            if col.name in scene.collection.children:
                scene.collection.children.unlink(col)
    for c in list(scene.collection.children):
        if c.name.startswith("Fraeser"):
            scene.collection.children.unlink(c)

    for row, kind in enumerate(ROWS):
        for i, (key, col) in enumerate(by_kind[kind]):
            pos = Vector((i * STEP, -row * ROW_GAP, 0))
            instance(col, pos, key)
            label(key, pos + Vector((0, -0.34, -0.05)))

    columns = max(len(v) for v in by_kind.values())
    combos = [(i, i, i) for i in range(columns)]
    for i, (w, t, p) in enumerate(combos):
        pos = Vector((i * STEP, -3 * ROW_GAP - 0.1, 0))
        for kind, idx in zip(ROWS, (w, t, p)):
            instance(by_kind[kind][idx][1], pos, "kombi")

    world = bpy.data.worlds.new("Studio")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.09, 0.09, 0.1, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 1.0
    scene.world = world

    center = Vector(((columns - 1) / 2 * STEP, -1.5 * ROW_GAP, 0.2))
    cam_data = bpy.data.cameras.new("Kamera")
    cam_data.type = "ORTHO"
    cam_data.ortho_scale = max(4.6, columns * STEP + 0.6)
    cam = bpy.data.objects.new("Kamera", cam_data)
    tilt = math.radians(50)
    cam.location = center + Vector((0, -6 * math.sin(tilt), 6 * math.cos(tilt)))
    cam.rotation_euler = (tilt, 0, 0)
    scene.collection.objects.link(cam)
    scene.camera = cam

    area("Key", center + Vector((-2, -3, 4)), (math.radians(40), math.radians(-25), 0), 900, 3)
    area("Fill", center + Vector((3, -2, 2)), (math.radians(60), math.radians(40), 0), 300, 4)
    area("Rim", center + Vector((0, 3, 2.5)), (math.radians(-55), 0, 0), 700, 4)

    set_engine(scene, engine)
    scene.render.resolution_x = 2400
    scene.render.resolution_y = 1700
    scene.render.film_transparent = False
    scene.view_settings.view_transform = "AgX"
    scene.render.filepath = os.path.abspath(out_path)
    bpy.ops.render.render(write_still=True)
    print("VORSCHAU", out_path)
    render_logos(by_kind["fusion-wheels"][1:], out_path.replace(".png", "-logos.png"), engine=engine)
    render_logos(by_kind["fusion-wheels"][1:], out_path.replace(".png", "-wheels.png"), tilt_deg=55, height=640,
                 aim_z=0.3, engine=engine)
    render_logos(by_kind["spin-tracks"], out_path.replace(".png", "-tracks.png"), tilt_deg=58, spacing=0.42,
                 height=520, aim_z=0.18, engine=engine)
    render_logos(by_kind["tips"], out_path.replace(".png", "-tips.png"), tilt_deg=84, spacing=0.26,
                 height=620, aim_z=0.11, engine=engine)


def render_logos(wheel_cols, out_path, tilt_deg=10, spacing=0.52, height=560, aim_z=0.0, engine=None):
    """Draufsicht auf die Fusion Wheels, um Logos und Klingenformen zu prüfen."""
    scene = bpy.data.scenes.new("Logos")
    for i, (key, col) in enumerate(wheel_cols):
        e = bpy.data.objects.new(key, None)
        e.instance_type = "COLLECTION"
        e.instance_collection = col
        e.location = (i * spacing, 0, 0)
        scene.collection.objects.link(e)
    world = bpy.data.worlds.new("Studio2")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.09, 0.09, 0.1, 1)
    scene.world = world
    cam_data = bpy.data.cameras.new("Oben")
    cam_data.type = "ORTHO"
    cam_data.ortho_scale = spacing * len(wheel_cols)
    cam = bpy.data.objects.new("Oben", cam_data)
    tilt = math.radians(tilt_deg)
    cam.location = ((len(wheel_cols) - 1) * spacing / 2, -2.0 * math.sin(tilt), aim_z + 2.0 * math.cos(tilt))
    cam.rotation_euler = (tilt, 0, 0)
    scene.collection.objects.link(cam)
    scene.camera = cam
    for name, loc, energy in (("Key", (-1, -1.5, 3), 500), ("Fill", (3, -1, 2), 200)):
        data = bpy.data.lights.new(name, "AREA")
        data.energy = energy
        data.size = 3
        obj = bpy.data.objects.new(name, data)
        obj.location = loc
        obj.rotation_euler = (math.radians(25), 0, 0)
        scene.collection.objects.link(obj)
    set_engine(scene, engine)
    scene.render.resolution_x = 2400
    scene.render.resolution_y = height
    scene.view_settings.view_transform = "AgX"
    scene.render.filepath = os.path.abspath(out_path)
    bpy.ops.render.render(write_still=True, scene=scene.name)
    print("LOGOS", out_path)
