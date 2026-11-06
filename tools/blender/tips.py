"""Tips: gestufter Schaft, Flansch, facettierter Körper und eine Kontaktform je Typ."""
import math

import bmesh

from geometry import (TAU, box, new_object, cut_radial, finish, glow, lathe, material, metal, mix, plastic, polar_solid,
                      polygon_r, ring, rubber, sphere, tint, torus)


def build_tip(d, col, key=""):
    style = d["style"]
    accent = glow(d["accent"])
    shell = material("Gehaeuse", mix("#16181d", d["accent"], 0.42), roughness=0.16, coat=1.0)
    m = metal("#b9bec8", 0.18)

    shaft = lathe("Schaft", [(0, 0.14), (0.045, 0.14), (0.045, 0.175), (0.04, 0.18), (0.04, 0.215),
                             (0.044, 0.22), (0.044, 0.25), (0.036, 0.256), (0.036, 0.284), (0.03, 0.296),
                             (0, 0.296)], m, col, n=48)
    finish(shaft, bevel=0.001, angle=30)
    for i in range(3):
        a = TAU * i / 3
        rib = box("Rippe", (0.0425 * math.cos(a), 0.0425 * math.sin(a), 0.235), (0.006, 0.008, 0.026), a,
                  metal("#3b3f47", 0.25), col)
        finish(rib, bevel=0.001)
    torus("Schaftleuchte", 0.041, 0.0022, 0.2, accent, col, n=64)
    collar = ring("Kragen", 0.045, polygon_r(6, 0.056), 0.14, 0.158, metal("#3b3f47", 0.25), col, n=6 * 16)
    finish(collar, bevel=0.0015, angle=30)

    # Flansch und facettierter Körper (Zehnkant, plane Flächen statt Rippen)
    flange = lathe("Flansch", [(0.0, 0.124), (0.078, 0.124), (0.084, 0.131), (0.079, 0.141), (0.0, 0.141)],
                   plastic(mix("#16181d", d["accent"], 0.7), 0.18, 1.0), col, n=12)
    finish(flange, bevel=0.0012, angle=20)
    housing = lathe("Gehaeuse", [(0.0, 0.056), (0.046, 0.056), (0.058, 0.07), (0.064, 0.112), (0.071, 0.121),
                                 (0.071, 0.126), (0.0, 0.126)], shell, col, n=10)
    finish(housing, bevel=0.0012, angle=20)
    band = ring("Metallband", 0.05, polygon_r(10, 0.0655), 0.088, 0.097, m, col, n=40)
    finish(band, bevel=0.0008, angle=20)
    torus("Fussleuchte", 0.05, 0.0025, 0.062, accent, col, n=64)
    foot = ring("Fussring", 0.03, 0.054, 0.05, 0.058, m, col, n=96)
    cut_radial(foot, 24, 0.054, 0.058, 0.003, 0.0035, 0.008)
    finish(foot, bevel=0.0008)

    c = d["color"]
    if style == "flat":
        contact = lathe("Kontakt", [(0, -0.008), (0.056, -0.008), (0.066, 0.004), (0.066, 0.054), (0, 0.054)], rubber(c), col)
        cut_radial(contact, 10, 0.035, 0.0, 0.004, 0.006, 0.05)
        finish(contact, 0.0015)
    elif style == "needle":
        cone = polar_solid("Kontakt", 0, TAU, lambda a: 0.004, polygon_r(8, 0.05), lambda r, a: 0.054 - 0.07 * (1 - r / 0.05),
                           lambda r, a: 0.056, metal(c, 0.15), col, n_ang=8 * 8, n_rad=8)
        finish(cone, 0.001, angle=25)
        torus("Nadelring", 0.04, 0.004, 0.046, accent, col, n=64)
    elif style == "ball":
        cup = lathe("Schale", [(0, 0.026), (0.056, 0.026), (0.064, 0.056), (0, 0.056)], m, col)
        finish(cup, 0.001)
        torus("Schalenleuchte", 0.058, 0.0025, 0.03, accent, col, n=64)
        sphere("Kontakt", 0.046, (0, 0, 0.036), metal(c, 0.08), col)
    elif style == "claw":
        lathe("Kontaktbasis", [(0, 0.024), (0.054, 0.024), (0.058, 0.056), (0, 0.056)], rubber(c), col)
        for i in range(6):
            a = TAU * i / 6
            claw = polar_solid("Kralle", a, a + 0.55, lambda aa: 0.02, lambda aa: 0.066,
                               lambda r, aa, a=a: 0.03 - 0.04 * ((aa - a) / 0.55),
                               lambda r, aa: 0.04, rubber(tint(c, 0.85)), col, n_ang=8, n_rad=4)
            finish(claw, 0.001, angle=25)
    elif style == "spear":
        drill_tip(c, accent, col)
    elif style == "bearing":
        torus("Lagerring", 0.038, 0.013, 0.03, metal(c, 0.18), col, n=96, m=12)
        torus("Kaefig", 0.038, 0.004, 0.042, accent, col, n=96)
        for i in range(10):
            a = TAU * i / 10
            sphere("Kugel", 0.009, (0.038 * math.cos(a), 0.038 * math.sin(a), 0.017), metal("#e6e9ee", 0.08), col, seg=16)
        finish(lathe("Spitze", [(0, 0.0), (0.012, 0.006), (0.022, 0.05), (0, 0.05)], metal("#9aa0aa"), col), 0.0015)
    else:  # dome
        dome = lathe("Kontakt", [(0, -0.01), (0.034, -0.004), (0.056, 0.016), (0.066, 0.054), (0, 0.054)], plastic(c, 0.2), col)
        finish(dome, 0.0015)
        for i in range(3):
            a = TAU * i / 3
            polar_solid("Kuppelstreifen", a, a + 0.35, lambda aa: 0.035, lambda aa: 0.052,
                        lambda r, aa: 0.012 + 0.6 * (r - 0.035), lambda r, aa: 0.016 + 0.62 * (r - 0.035),
                        accent, col, n_ang=8, n_rad=3)
    return housing


def drill_tip(color, accent, col, points=5, twist=2.2):
    """Gedrehter Sternbohrer: Querschnitt mit `points` Spitzen, der sich zur Spitze hin dreht und verjüngt."""
    bm = bmesh.new()
    rings = []
    levels = 14
    for i in range(levels):
        f = i / levels
        z = 0.056 - 0.066 * f
        scale = (1 - f) ** 0.85
        rot = twist * f
        ring = []
        for k in range(points * 2):
            a = TAU * k / (points * 2) + rot
            r = (0.068 if k % 2 == 0 else 0.03) * scale + 0.0015
            ring.append(bm.verts.new((r * math.cos(a), r * math.sin(a), z)))
        rings.append(ring)
    apex = bm.verts.new((0.0, 0.0, -0.01))
    m = points * 2
    bm.faces.new(rings[0])
    for lo, hi in zip(rings[1:], rings):
        for k in range(m):
            bm.faces.new((hi[k], lo[k], lo[(k + 1) % m], hi[(k + 1) % m]))
    for k in range(m):
        bm.faces.new((rings[-1][k], apex, rings[-1][(k + 1) % m]))
    spear = new_object("Speer", bm, metal(color, 0.14), col)
    finish(spear, bevel=0.0008, angle=20)
    torus("Speerleuchte", 0.05, 0.003, 0.054, accent, col, n=64)
