"""Materialien und Geometrie-Helfer für die Teile-Skripte (Blender, bmesh)."""
import math

import bmesh
import bpy
from mathutils import Vector

TAU = 2 * math.pi


# ---------------------------------------------------------------- Materialien

def hex_rgb(h):
    h = h.lstrip("#")
    srgb = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in srgb)


_materials = {}


def material(name, color, metallic=0.0, roughness=0.4, alpha=1.0, emission=None, strength=0.0, coat=0.0):
    key = (name, color, metallic, roughness, alpha, emission, strength, coat)
    if key in _materials:
        return _materials[key]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*hex_rgb(color), 1)
    b.inputs["Metallic"].default_value = metallic
    b.inputs["Roughness"].default_value = roughness
    if coat:
        b.inputs["Coat Weight"].default_value = coat
        b.inputs["Coat Roughness"].default_value = 0.08
    if alpha < 1:
        b.inputs["Alpha"].default_value = alpha
        try:
            m.surface_render_method = "BLENDED"
        except AttributeError:
            m.blend_method = "BLEND"
    if emission:
        b.inputs["Emission Color"].default_value = (*hex_rgb(emission), 1)
        b.inputs["Emission Strength"].default_value = strength
    _materials[key] = m
    return m


def metal(color, rough=0.26):
    return material("Metall", color, metallic=1.0, roughness=rough)


def plastic(color, rough=0.22, coat=0.6):
    return material("Kunststoff", color, metallic=0.0, roughness=rough, coat=coat)


def clear():
    return material("Klar", "#f2f6ff", metallic=0.0, roughness=0.02, alpha=0.08)


def glow(color):
    return material("Leuchtkante", color, metallic=0.0, roughness=0.3, emission=color, strength=3.0)


def rubber(color):
    return material("Gummi", color, metallic=0.0, roughness=0.75)


DARK = "#1c1e23"


def mix(c1, c2, t):
    """Zwei Hex-Farben mischen (t = 0 → c1, t = 1 → c2)."""
    a = [int(c1.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)]
    b = [int(c2.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)]
    return "#" + "".join("%02x" % round(x + (y - x) * t) for x, y in zip(a, b))


def tint(color, factor):
    """Farbe abdunkeln (0 = schwarz, 1 = unverändert)."""
    h = color.lstrip("#")
    return "#" + "".join("%02x" % int(int(h[i:i + 2], 16) * factor) for i in (0, 2, 4))


# ---------------------------------------------------------------- Grundkörper

def new_object(name, bm, mat, collection):
    me = bpy.data.meshes.new(name)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    obj = bpy.data.objects.new(name, me)
    for m in mat if isinstance(mat, list) else [mat]:
        obj.data.materials.append(m)
    collection.objects.link(obj)
    return obj


def finish(obj, bevel=0.004, segments=2, angle=38):
    """Fase an harten Kanten, danach Kanten über dem Winkel scharf, Flächen glatt."""
    if bevel:
        mod = obj.modifiers.new("Fase", "BEVEL")
        mod.width = bevel
        mod.segments = segments
        mod.limit_method = "ANGLE"
        mod.angle_limit = math.radians(angle)
        mod.harden_normals = False
    obj.data.set_sharp_from_angle(angle=math.radians(angle))
    return obj


def ring(name, r_in, r_out, z0, z1, mat, col, n=256, z1_outer=None):
    """Rotationskörper zwischen Innen- und Außenkontur (beide dürfen vom Winkel abhängen).
    Mit `z1_outer` fällt die Oberseite nach außen ab (Keilprofil wie bei Angriffsklingen)."""
    rin = r_in if callable(r_in) else (lambda a, r=r_in: r)
    rout = r_out if callable(r_out) else (lambda a, r=r_out: r)
    bm = bmesh.new()
    rows = []
    for i in range(n):
        a = TAU * i / n
        c, s = math.cos(a), math.sin(a)
        ri, ro = rin(a), rout(a)
        zo = z1 if z1_outer is None else z1_outer
        rows.append((
            bm.verts.new((ri * c, ri * s, z0)), bm.verts.new((ro * c, ro * s, z0)),
            bm.verts.new((ri * c, ri * s, z1)), bm.verts.new((ro * c, ro * s, zo)),
        ))
    for i in range(n):
        a, b = rows[i], rows[(i + 1) % n]
        bm.faces.new((a[2], a[3], b[3], b[2]))
        bm.faces.new((a[0], b[0], b[1], a[1]))
        bm.faces.new((a[1], b[1], b[3], a[3]))
        bm.faces.new((a[0], a[2], b[2], b[0]))
    return new_object(name, bm, mat, col)


def lathe(name, profile, mat, col, n=96, closed=False):
    """Drehkörper aus einem (r, z)-Profil von unten nach oben; r = 0 schließt die Achse.

    closed: das Profil ist ein geschlossener Querschnitt (Ring), der letzte Punkt verbindet sich mit dem
    ersten, es gibt keine Deckflächen.
    """
    bm = bmesh.new()
    rings = []
    for r, z in profile:
        if r < 1e-6:
            rings.append([bm.verts.new((0, 0, z))])
        else:
            rings.append([bm.verts.new((r * math.cos(TAU * i / n), r * math.sin(TAU * i / n), z))
                          for i in range(n)])
    pairs = list(zip(rings, rings[1:])) + ([(rings[-1], rings[0])] if closed else [])
    for lo, hi in pairs:
        for i in range(n):
            j = (i + 1) % n
            if len(lo) == 1 and len(hi) == 1:
                continue
            if len(lo) == 1:
                bm.faces.new((lo[0], hi[j], hi[i]))
            elif len(hi) == 1:
                bm.faces.new((lo[i], lo[j], hi[0]))
            else:
                bm.faces.new((lo[i], lo[j], hi[j], hi[i]))
    for cap in () if closed else (rings[0], rings[-1]):
        if len(cap) > 2:
            bm.faces.new(cap if cap is rings[-1] else list(reversed(cap)))
    return new_object(name, bm, mat, col)


def torus(name, major, minor, z, mat, col, n=160, m=16):
    bm = bmesh.new()
    rows = []
    for i in range(n):
        a = TAU * i / n
        row = []
        for k in range(m):
            b = TAU * k / m
            r = major + minor * math.cos(b)
            row.append(bm.verts.new((r * math.cos(a), r * math.sin(a), z + minor * math.sin(b))))
        rows.append(row)
    for i in range(n):
        for k in range(m):
            bm.faces.new((rows[i][k], rows[(i + 1) % n][k], rows[(i + 1) % n][(k + 1) % m], rows[i][(k + 1) % m]))
    return new_object(name, bm, mat, col)


def box(name, center, size, angle, mat, col):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co = Vector((v.co.x * size[0], v.co.y * size[1], v.co.z * size[2]))
        v.co.rotate(_rot_z(angle))
        v.co += Vector(center)
    return new_object(name, bm, mat, col)


def _rot_z(angle):
    from mathutils import Matrix
    return Matrix.Rotation(angle, 3, "Z")


def sphere(name, r, center, mat, col, seg=32):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=seg, v_segments=seg // 2, radius=r)
    for v in bm.verts:
        v.co += Vector(center)
    return new_object(name, bm, mat, col)


def cut_radial(obj, count, r, z_top, depth, width, length, offset=0.0):
    """Kerben radial in die Oberseite fräsen (Boolean)."""
    cutters = bpy.data.collections.new("Fraeser")
    bpy.context.scene.collection.children.link(cutters)
    for i in range(count):
        a = TAU * i / count + offset
        c = box("Fraeser", (r * math.cos(a), r * math.sin(a), z_top), (length, width, depth * 2), a, obj.active_material, cutters)
        c.hide_render = True
    mod = obj.modifiers.new("Kerben", "BOOLEAN")
    mod.operation = "DIFFERENCE"
    mod.operand_type = "COLLECTION"
    mod.collection = cutters
    mod.solver = "EXACT"
    return cutters


def tube(name, points, radius, mat, col, m=12):
    """Rohr mit rundem Querschnitt entlang einer Punktfolge."""
    bm = bmesh.new()
    rings = []
    for i, p in enumerate(points):
        p = Vector(p)
        t = (Vector(points[min(i + 1, len(points) - 1)]) - Vector(points[max(i - 1, 0)])).normalized()
        n = t.cross(Vector((0, 0, 1)))
        if n.length < 1e-6:
            n = Vector((1, 0, 0))
        n.normalize()
        b = t.cross(n)
        rings.append([bm.verts.new(p + radius * (math.cos(TAU * k / m) * n + math.sin(TAU * k / m) * b))
                      for k in range(m)])
    for lo, hi in zip(rings, rings[1:]):
        for k in range(m):
            bm.faces.new((lo[k], lo[(k + 1) % m], hi[(k + 1) % m], hi[k]))
    bm.faces.new(list(reversed(rings[0])))
    bm.faces.new(rings[-1])
    return new_object(name, bm, mat, col)


def polar_solid(name, a0, a1, r_in, r_out, z_bot, z_top, mat, col, n_ang=192, n_rad=10):
    """Körper über einem Polar-Gitter mit Höhenprofil.

    r_in/r_out: Radius abhängig vom Winkel, z_bot/z_top: Höhe abhängig von (r, a).
    Ist der Winkelbereich ein voller Kreis, wird geschlossen, sonst entstehen Stirnflächen.
    """
    closed = abs((a1 - a0) - TAU) < 1e-9
    count = n_ang if closed else n_ang + 1
    bm = bmesh.new()
    top, bot = [], []
    for i in range(count):
        a = a0 + (a1 - a0) * i / n_ang
        ri, ro = r_in(a), r_out(a)
        c, s_ = math.cos(a), math.sin(a)
        trow, brow = [], []
        for j in range(n_rad + 1):
            r = ri + (ro - ri) * j / n_rad
            trow.append(bm.verts.new((r * c, r * s_, z_top(r, a))))
            brow.append(bm.verts.new((r * c, r * s_, z_bot(r, a))))
        top.append(trow)
        bot.append(brow)
    steps = n_ang if closed else n_ang
    for i in range(steps):
        k = (i + 1) % count if closed else i + 1
        for j in range(n_rad):
            bm.faces.new((top[i][j], top[i][j + 1], top[k][j + 1], top[k][j]))
            bm.faces.new((bot[i][j], bot[k][j], bot[k][j + 1], bot[i][j + 1]))
        bm.faces.new((top[i][n_rad], bot[i][n_rad], bot[k][n_rad], top[k][n_rad]))
        bm.faces.new((top[i][0], top[k][0], bot[k][0], bot[i][0]))
    if not closed:
        for i, sign in ((0, 1), (count - 1, -1)):
            for j in range(n_rad):
                quad = (top[i][j], bot[i][j], bot[i][j + 1], top[i][j + 1])
                bm.faces.new(quad if sign > 0 else tuple(reversed(quad)))
    return new_object(name, bm, mat, col)


def flat_shape(name, outlines, scale, z, height, mat, col, offset=(0.0, 0.0), rotate=0.0):
    """Flach extrudierte Silhouette(n) aus normierten 2D-Punkten (-1..1)."""
    bm = bmesh.new()
    cr, sr = math.cos(rotate), math.sin(rotate)
    for outline in outlines:
        pts = [((x * cr - y * sr) * scale + offset[0], (x * sr + y * cr) * scale + offset[1]) for x, y in outline]
        b = [bm.verts.new((x, y, z)) for x, y in pts]
        t = [bm.verts.new((x, y, z + height)) for x, y in pts]
        bm.faces.new(t)
        bm.faces.new(list(reversed(b)))
        for i in range(len(pts)):
            j = (i + 1) % len(pts)
            bm.faces.new((b[i], b[j], t[j], t[i]))
    bmesh.ops.triangulate(bm, faces=[f for f in bm.faces if len(f.verts) > 4])
    return new_object(name, bm, mat, col)


def sector(a, count, phase=0.0):
    return ((a - phase) * count / TAU) % 1.0


def smooth01(x):
    x = min(max(x, 0.0), 1.0)
    return x * x * (3 - 2 * x)


def knots(t, points):
    """Stückweise lineares Profil durch (t, z)-Punkte."""
    for (t0, z0), (t1, z1) in zip(points, points[1:]):
        if t <= t1:
            return z0 + (z1 - z0) * (t - t0) / max(t1 - t0, 1e-9)
    return points[-1][1]


def polygon_r(sides, r, rot=0.0):
    """Radius eines regelmäßigen Vielecks (Umkreis r) in Richtung a."""
    seg = TAU / sides
    return lambda a: r * math.cos(seg / 2) / math.cos(((a - rot) % seg) - seg / 2)


def _steps(n, closed=False):
    if isinstance(n, int):
        return [i / n for i in range(n if closed else n + 1)]
    steps = list(n)
    if closed and abs(steps[-1] - 1.0) < 1e-9:
        steps = steps[:-1]
    return steps


def boolean(obj, cutters, name):
    mod = obj.modifiers.new(name, "BOOLEAN")
    mod.operation = "DIFFERENCE"
    mod.operand_type = "COLLECTION"
    mod.collection = cutters
    mod.solver = "EXACT"


def cutter_collection():
    cutters = bpy.data.collections.new("Fraeser")
    bpy.context.scene.collection.children.link(cutters)
    return cutters


def cut_boxes(obj, boxes):
    """Quader (Mitte, Größe, Winkel) per Boolean herausschneiden."""
    cutters = cutter_collection()
    for center, size, angle in boxes:
        box("Schnitt", center, size, angle, obj.active_material, cutters).hide_render = True
    boolean(obj, cutters, "Schnitte")


def drill(obj, holes, z0=0.15, z1=0.45, n=20):
    """Runde Durchbrüche (x, y, Radius) senkrecht durch das Objekt."""
    cutters = cutter_collection()
    for x, y, r in holes:
        c = lathe("Bohrer", [(0.0, z0), (r, z0), (r, z1), (0.0, z1)], obj.active_material, cutters, n=n)
        c.location = (x, y, 0.0)
        c.hide_render = True
    boolean(obj, cutters, "Bohrungen")


# ---------------------------------------------------------------- Geformte Körper

def grid_solid(name, us, ts, place, z_top, z_bot, mats, col, closed=False, zone=None):
    """Körper über einem (u, t)-Gitter: u läuft entlang des Teils, t von innen nach außen (beide 0..1).

    `us`/`ts` sind eine Anzahl gleicher Schritte oder eine Liste von Stützstellen. place(u, t) gibt die Lage
    (x, y), z_top/z_bot die Höhe von Ober- und Unterseite. Knicke im Profil auf Stützstellen ergeben plane
    Facetten mit harten Kanten. `zone(u, t)` gibt pro Fläche den Material-Index; die Wände bekommen
    zone(u, 0) bzw. zone(u, 1), die Stirnseiten zone(0 oder 1, t).
    """
    us = _steps(us, closed)
    ts = _steps(ts)
    bm = bmesh.new()
    top, bot = [], []
    for u in us:
        trow, brow = [], []
        for t in ts:
            x, y = place(u, t)
            zt = z_top(u, t)
            trow.append(bm.verts.new((x, y, zt)))
            brow.append(bm.verts.new((x, y, min(z_bot(u, t), zt - 0.0015))))
        top.append(trow)
        bot.append(brow)

    def mark(face, u, t):
        if zone:
            face.material_index = zone(u, t)

    n, last = len(us), len(ts) - 1
    for i in range(n if closed else n - 1):
        k = (i + 1) % n
        uc = (us[i] + (us[k] if k else 1.0)) / 2
        for j in range(last):
            mark(bm.faces.new((top[i][j], top[i][j + 1], top[k][j + 1], top[k][j])), uc, (ts[j] + ts[j + 1]) / 2)
            mark(bm.faces.new((bot[i][j], bot[k][j], bot[k][j + 1], bot[i][j + 1])), uc, 0.0)
        mark(bm.faces.new((top[i][last], bot[i][last], bot[k][last], top[k][last])), uc, 1.0)
        mark(bm.faces.new((top[i][0], top[k][0], bot[k][0], bot[i][0])), uc, 0.0)
    if not closed:
        for i in (0, n - 1):
            for j in range(last):
                quad = (top[i][j], bot[i][j], bot[i][j + 1], top[i][j + 1])
                mark(bm.faces.new(quad if i == 0 else tuple(reversed(quad))), us[i], (ts[j] + ts[j + 1]) / 2)
    return new_object(name, bm, mats, col)


def polar(r_in, r_out, twist=0.0):
    """place-Funktion für Ringe: u = Winkel (0..1 = ein Umlauf), t = von r_in(a) nach r_out(a)."""
    rin = r_in if callable(r_in) else (lambda a, r=r_in: r)
    rout = r_out if callable(r_out) else (lambda a, r=r_out: r)

    def place(u, t):
        a = TAU * u + twist * t
        r = rin(a) + (rout(a) - rin(a)) * t
        return r * math.cos(a), r * math.sin(a)
    return place


def taper(w0, w1, power=1.0):
    """Breite von w0 an der Wurzel auf w1 an der Spitze."""
    return lambda t: w1 + (w0 - w1) * (1 - t) ** power


def spiral(r0, r1, a0, turn, power=1.0, bulge=0.0):
    """Mittellinie von (r0, a0) nach (r1, a0 + turn); power > 1 biegt erst zur Spitze hin (Haken)."""
    def f(t):
        r = r0 + (r1 - r0) * t + bulge * math.sin(math.pi * t)
        a = a0 + turn * t ** power
        return r * math.cos(a), r * math.sin(a)
    return f


def sweep(name, spine, half_width, z_top, z_bot, mats, col, us=(0.0, 0.5, 1.0), ts=24, zone=None):
    """Körper entlang einer Mittellinie spine(t), t = 0 Wurzel, 1 Spitze.

    half_width(t, side) ist die halbe Breite nach rechts (side -1, u = 0) bzw. links (side +1, u = 1) der
    Laufrichtung. Läuft die Mittellinie nach außen, liegt u = 1 in Drehrichtung (gegen den Uhrzeigersinn).
    """
    def place(u, t):
        x, y = spine(t)
        xa, ya = spine(max(t - 1e-3, 0.0))
        xb, yb = spine(min(t + 1e-3, 1.0))
        tx, ty = xb - xa, yb - ya
        length = math.hypot(tx, ty) or 1.0
        s = 2 * u - 1
        w = s * max(half_width(t, 1 if s > 0 else -1), 0.0012)
        return x - ty / length * w, y + tx / length * w
    return grid_solid(name, us, ts, place, z_top, z_bot, mats, col, zone=zone)


KNIFE = [(0.0, 1.0), (0.1, 0.55), (0.5, 0.0), (0.9, 0.55), (1.0, 1.0)]


def blade(name, spine, w_trail, w_lead, ridge, drop, under, mats, col, profile=KNIFE, ts=28, zone=None):
    """Klinge mit Messerquerschnitt: Grat auf ridge(t), zu den Kanten um drop(t) abfallend.

    profile: (u, Anteil von drop) – u = 0 Hinterkante, u = 1 Vorderkante (in Drehrichtung). Die
    Unterseite liegt auf under(t) und steigt zu den Kanten an, damit die Schneiden dünn werden.
    """
    us = [u for u, _ in profile]

    def top(u, t):
        return ridge(t) - drop(t) * knots(u, profile)

    def bottom(u, t):
        edge = max(0.0, 1 - min(u, 1 - u) / 0.12)
        return under(t) + 0.35 * drop(t) * edge

    return sweep(name, spine, lambda t, side: (w_lead if side > 0 else w_trail)(t), top, bottom, mats, col,
                 us=us, ts=ts, zone=zone)


def rivet(location, mat, col, r=0.006, h=0.005):
    obj = lathe("Niete", [(0.0, 0.0), (r, 0.0), (r, h * 0.6), (r * 0.6, h), (0.0, h * 1.05)], mat, col, n=12)
    obj.location = location
    return obj
