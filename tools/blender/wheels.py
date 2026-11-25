"""Fusion Wheels: jedes Rad mit eigenem Aufbau aus Klingen in mehreren Ebenen, Sockel und Emblem-Chip.

Drehrichtung im Spiel ist (von oben in Blender) gegen den Uhrzeigersinn. Klingenspitzen zeigen deshalb in
+Winkel-Richtung, die Vorderkante (u = 1 in `blade`) ist die scharfe Schneide.
Höhenband z 0.21 – 0.36, Hörner und Türme ragen bis etwa 0.39 nach oben.
"""
import math

from geometry import (DARK, TAU, blade, box, clear, cut_boxes, cut_radial, drill, finish, flat_shape,
                      glow, grid_solid, knots, lathe, material, metal, mix, plastic, polar, polygon_r, rivet, ring,
                      sector, spiral, sweep, taper, tint, torus)
from logos import LOGOS, logo_raven


class Kit:
    """Materialien eines Rads aus seinen Designfarben."""

    def __init__(self, d):
        self.chrome = metal(d["metal"], 0.12)
        self.gun = metal(tint(d["metal"], 0.36), 0.3)
        self.lacquer = material("Lack", mix(d["layer"], d["accent"], 0.16), metallic=0.45, roughness=0.2, coat=1.0)
        self.layer = material("Farbschicht", mix(d["layer"], d["accent"], 0.4), roughness=0.2, coat=0.3,
                              emission=d["accent"], strength=0.6)
        self.white = plastic("#eef0f3", 0.25, 0.8)
        self.glow = glow(d["accent"])
        self.dark = plastic(DARK, 0.4, 0.2)
        self.bolt = metal("#3a3d44", 0.3)


def point(spine, t, offset):
    """Punkt neben der Mittellinie (offset > 0 zur Vorderkante)."""
    x, y = spine(t)
    xa, ya = spine(max(t - 1e-3, 0.0))
    xb, yb = spine(min(t + 1e-3, 1.0))
    tx, ty = xb - xa, yb - ya
    length = math.hypot(tx, ty) or 1.0
    return x - ty / length * offset, y + tx / length * offset


def socket(kit, col, place_r, profile, z_bot=0.212, us=96, zone_t=0.8, notches=0, notch_r=0.0, offset=0.0):
    """Ringförmiger Sockel mit facettiertem Profil (t-Stützstellen aus `profile`)."""
    ts = [t for t, _ in profile]
    obj = grid_solid("Sockel", us, ts, place_r, lambda u, t: knots(t, profile), lambda u, t: z_bot,
                     [kit.chrome, kit.gun], col, closed=True,
                     zone=lambda u, t: 1 if t >= zone_t or t == 0.0 else 0)
    if notches:
        cut_radial(obj, notches, notch_r, 0.27, 0.08, 0.008, 0.03, offset)
    finish(obj, bevel=0.0012, angle=25)
    return obj


def seat(kit, col, sides, notches=0, r_out=0.118, top=0.35):
    """Chrom-Fassung um den Chip."""
    s = lathe("Fassung", [(0.076, 0.296), (r_out, 0.296), (r_out, top - 0.016), (r_out - 0.012, top - 0.002),
                          (0.084, top), (0.076, top - 0.006)], kit.chrome, col, n=sides, closed=True)
    if notches:
        cut_radial(s, notches, r_out, top - 0.016, 0.006, 0.0045, 0.014)
    finish(s, bevel=0.001, angle=25)
    return s


def chip(d, kit, col, key, z=0.336):
    """Emblem-Chip mit Logo, Leuchtring und Glaskuppel, vertieft in der Fassung."""
    lathe("Chip", [(0.0, 0.27), (0.077, 0.27), (0.077, z), (0.0, z)], plastic(tint(d["layer"], 0.9), 0.3, 0.8),
          col, n=96)
    base_shapes, detail_shapes = LOGOS.get(key, logo_raven)()
    flat_shape("Logo-Schatten", base_shapes, 0.06, z, 0.003, plastic("#0d0e11", 0.4, 0.2), col,
               offset=(0.0025, -0.0025))
    flat_shape("Logo", base_shapes, 0.06, z, 0.005, plastic("#f6f7f9", 0.2, 0.9), col)
    if detail_shapes:
        flat_shape("Logo-Akzent", detail_shapes, 0.06, z + 0.005, 0.002, kit.glow, col)
    torus("Chip-Leuchte", 0.077, 0.0022, z + 0.002, kit.glow, col)
    lathe("Kuppel", [(0.0, z + 0.002), (0.077, z + 0.002), (0.074, z + 0.008), (0.05, z + 0.014),
                     (0.0, z + 0.016)], clear(), col, n=96)
    finish(ring("Unterbau", 0.06, 0.16, 0.208, 0.226, kit.dark, col), bevel=0.003)


# ---------------------------------------------------------------- Bollwerk: Burg aus Schildplatten und Türmen

def bollwerk(d, kit, col):
    n = d["blades"]
    socket(kit, col, polar(0.1, polygon_r(12, 0.226, TAU / 24)),
           [(0.0, 0.272), (0.55, 0.28), (0.82, 0.272), (1.0, 0.238)], us=96, zone_t=0.82,
           notches=12, notch_r=0.226, offset=TAU / 24)

    shield = [(0.0, 1.0), (0.08, 0.45), (0.16, 0.8), (0.5, 0.0), (0.84, 0.8), (0.92, 0.45), (1.0, 1.0)]
    for k in range(n):
        a = TAU * k / n
        spine = spiral(0.112, 0.238, a, 0.14)

        def width(t):
            return (0.03 + 0.05 * t) * (1 - 0.82 * max(0.0, (t - 0.78) / 0.22) ** 1.4) + 0.002

        def ridge(t):
            return 0.358 - 0.05 * t

        plate = blade("Schild", spine, width, width, ridge, lambda t: 0.018, lambda t: 0.262 + 0.012 * t,
                      [kit.lacquer, kit.chrome, kit.gun], col, profile=shield, ts=24,
                      zone=lambda u, t: 1 if u < 0.16 or u > 0.84 or t > 0.93 else (2 if t == 0.0 else 0))
        finish(plate, bevel=0.0012, angle=22)
        for t in (0.3, 0.62):
            x, y = spine(t)
            rivet((x, y, ridge(t) - 0.002), kit.bolt, col)
        sweep("Schildleuchte", lambda s, sp=spine: sp(0.4 + 0.3 * s), lambda t, side: 0.0016,
                          lambda u, t: ridge(0.4 + 0.3 * t) + 0.0012, lambda u, t: ridge(0.4 + 0.3 * t) - 0.004,
                          [kit.glow], col, ts=8)

        # Turm zwischen zwei Platten: Achtkant mit Zinnen und Leuchtkristall
        b = a + TAU / (2 * n)
        tx, ty = 0.197 * math.cos(b), 0.197 * math.sin(b)
        tower = lathe("Turm", [(0.0, 0.24), (0.027, 0.24), (0.027, 0.35), (0.032, 0.357), (0.032, 0.381),
                               (0.0, 0.381)], kit.chrome, col, n=8)
        tower.location = (tx, ty, 0.0)
        cut_boxes(tower, [((tx, ty, 0.381), (0.09, 0.011, 0.026), b),
                          ((tx, ty, 0.381), (0.09, 0.011, 0.026), b + math.pi / 2)])
        finish(tower, bevel=0.0012, angle=25)
        crystal = lathe("Kristall", [(0.0, 0.356), (0.011, 0.356), (0.0, 0.392)], kit.glow, col, n=4)
        crystal.location = (tx, ty, 0.0)
        box("Turmschlitz", (0.223 * math.cos(b), 0.223 * math.sin(b), 0.318), (0.004, 0.012, 0.03), b, kit.glow, col)
    seat(kit, col, 12, 12)


# ---------------------------------------------------------------- Wirbel: zwei Ebenen geschwungener Flügel

def wirbel(d, kit, col):
    n = d["blades"]
    socket(kit, col, polar(0.085, polygon_r(10, 0.172)), [(0.0, 0.282), (0.6, 0.29), (1.0, 0.262)], us=80,
           zone_t=0.6)
    knife = [(0.0, 1.0), (0.12, 0.6), (0.68, 0.0), (0.9, 0.55), (1.0, 1.0)]
    for k in range(n):
        a = TAU * k / n
        low = spiral(0.12, 0.234, a + TAU / (2 * n), 1.25, power=1.15)
        w = blade("Unterfluegel", low, taper(0.066, 0.002, 0.9), taper(0.04, 0.0015, 1.2),
                  lambda t: 0.306 - 0.016 * t, lambda t: 0.016, lambda t: 0.258 + 0.018 * t,
                  [kit.chrome, kit.gun], col, profile=knife, ts=30,
                  zone=lambda u, t: 1 if u < 0.12 or t == 0.0 else 0)
        finish(w, bevel=0.001, angle=22)

        up = spiral(0.1, 0.238, a, 1.6, power=1.25)

        def ridge(t):
            return 0.336 + 0.034 * math.sin(math.pi * t) ** 0.8 - 0.012 * t

        w = blade("Fluegel", up, taper(0.076, 0.002, 0.8), taper(0.044, 0.0015, 1.1), ridge,
                  lambda t: 0.02, lambda t: 0.292 + 0.008 * t, [kit.layer, kit.white, kit.chrome, kit.gun], col,
                  profile=knife, ts=34,
                  zone=lambda u, t: 1 if u > 0.88 else (2 if u < 0.12 else (3 if t == 0.0 else 0)))
        finish(w, bevel=0.001, angle=22)
        x, y = up(0.22)
        rivet((x, y, ridge(0.22) - 0.003), kit.bolt, col)
    seat(kit, col, 10, 10)


# ---------------------------------------------------------------- Drachenzahn: Hornklauen mit Zackenrücken

def drachenzahn(d, kit, col):
    n = d["blades"]
    socket(kit, col, polar(0.09, polygon_r(8, 0.2, TAU / 16)), [(0.0, 0.29), (0.5, 0.298), (0.8, 0.29),
                                                                (1.0, 0.258)], us=96, zone_t=0.8)
    claw = [(0.0, 1.0), (0.1, 0.55), (0.62, 0.0), (0.9, 0.6), (1.0, 1.0)]

    def saw(t):
        return (t * 6) % 1.0 if 0.1 < t < 0.85 else 0.0

    for k in range(n):
        a = TAU * k / n
        spine = spiral(0.11, 0.246, a, 0.95, power=2.0)

        def ridge(t):
            return 0.326 + 0.07 * t - 0.05 * t ** 3

        def drop(t):
            return 0.024 * (1 - 0.4 * t)

        def zone(u, t):
            if t == 0.0:
                return 2
            if u < 0.1:
                return 2
            if 0.62 < u < 0.82 and 0.16 < t < 0.27:
                return 3
            if 0.1 <= u < 0.48 and 0.12 < t < 0.86:
                return 1
            return 0

        fang = blade("Fangzahn", spine, lambda t: (0.022 + 0.06 * (1 - t) ** 1.1) * (1 + 0.34 * saw(t)),
                     taper(0.052, 0.0015, 1.4), ridge, drop, lambda t: 0.262 + 0.06 * t - 0.02 * t * t,
                     [kit.chrome, kit.lacquer, kit.gun, kit.glow], col, profile=claw, ts=48, zone=zone)
        finish(fang, bevel=0.001, angle=22)
        hx, hy = point(spine, 0.42, -0.01)
        drill(fang, [(hx, hy, 0.0095)])

        sub = spiral(0.12, 0.224, a + TAU / (2 * n), 0.7, power=1.8)
        s = blade("Nebenzahn", sub, taper(0.05, 0.0015), taper(0.034, 0.0015, 1.3), lambda t: 0.312 + 0.012 * t,
                  lambda t: 0.014, lambda t: 0.27 + 0.02 * t, [kit.layer, kit.chrome], col, profile=claw, ts=24,
                  zone=lambda u, t: 1 if u > 0.88 else 0)
        finish(s, bevel=0.001, angle=22)
    seat(kit, col, 8, 8)


# ---------------------------------------------------------------- Eisenmond: zwei aufsteigende Sicheln auf schwerem Ring

def eisenmond(d, kit, col):
    socket(kit, col, polar(0.12, polygon_r(16, 0.212)), [(0.0, 0.286), (0.4, 0.296), (0.8, 0.29), (1.0, 0.258)],
           us=128, zone_t=0.8, notches=16, notch_r=0.212)
    moon = [(0.0, 1.0), (0.1, 0.5), (0.3, 0.0), (0.85, 0.6), (1.0, 1.0)]
    for k in range(2):
        a = TAU * k / 2

        def spine(t, a=a):
            ang = a - 1.3 + 2.6 * t
            r = 0.19 + 0.05 * (2 * t - 1) ** 2
            return r * math.cos(ang), r * math.sin(ang)

        def ridge(t):
            return 0.33 + 0.05 * math.sin(math.pi * t)

        # außen schmale Chromkante, nach innen bauchig: Sichel mit zwei Hörnern über den Ring hinaus
        def teeth(t):
            return 0.012 * ((t * 9) % 1.0) ** 1.5 if 0.12 < t < 0.88 else 0.0

        crescent = blade("Sichel", spine, lambda t: 0.008 * math.sin(math.pi * t) + 0.0015 + teeth(t),
                         lambda t: 0.06 * math.sin(math.pi * t) ** 0.8 + 0.0015, ridge,
                         lambda t: 0.02 * math.sin(math.pi * t) + 0.006,
                         lambda t: 0.272 + 0.02 * math.sin(math.pi * t),
                         [kit.lacquer, kit.chrome, kit.gun], col, profile=moon, ts=56,
                         zone=lambda u, t: 1 if u < 0.3 else (2 if u > 0.85 or t == 0.0 else 0))
        finish(crescent, bevel=0.001, angle=22)
        for t in (0.35, 0.65):
            rivet((*point(spine, t, 0.024), ridge(t) - 0.014), kit.bolt, col)

        # kleiner Leuchtmond zwischen den Sicheln
        b = a + TAU / 4

        def small(t, b=b):
            ang = b - 0.6 + 1.2 * t
            r = 0.17 + 0.02 * (2 * t - 1) ** 2
            return r * math.cos(ang), r * math.sin(ang)

        s = blade("Nebenmond", small, lambda t: 0.005 * math.sin(math.pi * t) + 0.0015,
                  lambda t: 0.03 * math.sin(math.pi * t) ** 0.8 + 0.0015,
                  lambda t: 0.312 + 0.02 * math.sin(math.pi * t), lambda t: 0.012 * math.sin(math.pi * t) + 0.004,
                  lambda t: 0.284, [kit.layer, kit.chrome], col, profile=moon, ts=28,
                  zone=lambda u, t: 1 if u < 0.3 else 0)
        finish(s, bevel=0.001, angle=22)
    seat(kit, col, 16, 16)


# ---------------------------------------------------------------- Phantom: zerfetzter Umhang und Sensen

def phantom(d, kit, col):
    n = d["blades"]
    weights = (1.0, 0.55, 0.8)

    def tatters(a):
        s = sector(a, n * 3)
        w = weights[int(a * n * 3 / TAU) % 3]
        tooth = s ** 1.6 if s < 0.82 else (1 - s) / 0.18 * 0.82 ** 1.6
        return 0.172 + 0.05 * w * tooth

    cloak = grid_solid("Umhang", n * 3 * 16, [0.0, 0.6, 1.0], polar(0.1, tatters),
                       lambda u, t: knots(t, [(0.0, 0.3), (0.6, 0.292), (1.0, 0.27)]), lambda u, t: 0.232,
                       [kit.layer, kit.gun], col, closed=True, zone=lambda u, t: 1 if t == 0.0 else 0)
    finish(cloak, bevel=0.001, angle=25)
    socket(kit, col, polar(0.085, polygon_r(10, 0.15)), [(0.0, 0.29), (0.7, 0.296), (1.0, 0.272)], us=80,
           zone_t=0.7)

    scythe = [(0.0, 1.0), (0.12, 0.55), (0.7, 0.0), (0.9, 0.5), (1.0, 1.0)]
    for k in range(n):
        a = TAU * k / n
        spine = spiral(0.105, 0.248, a, 1.15, power=2.4)

        def ridge(t):
            return 0.334 + 0.042 * t ** 1.5

        s = blade("Sense", spine, taper(0.046, 0.0015, 1.2), taper(0.03, 0.0015, 1.5), ridge, lambda t: 0.016,
                  lambda t: 0.27 + 0.07 * t ** 1.5, [kit.gun, kit.chrome, kit.lacquer], col, profile=scythe, ts=40,
                  zone=lambda u, t: 1 if u > 0.88 else (2 if 0.22 < u < 0.55 and 0.18 < t < 0.8 else 0))
        finish(s, bevel=0.001, angle=22)
        hx, hy = point(spine, 0.32, -0.006)
        drill(s, [(hx, hy, 0.0075)])

        short = spiral(0.11, 0.206, a + TAU / (2 * n), 0.6, power=2.0)
        s = blade("Dorn", short, taper(0.03, 0.0015), taper(0.02, 0.0015, 1.3), lambda t: 0.318 + 0.014 * t,
                  lambda t: 0.012, lambda t: 0.275 + 0.02 * t, [kit.chrome, kit.gun], col, profile=scythe, ts=20,
                  zone=lambda u, t: 1 if u < 0.12 else 0)
        finish(s, bevel=0.001, angle=22)
    seat(kit, col, 10, 10)


# ---------------------------------------------------------------- Greif: Federkranz mit schrägen Klingen

def greif(d, kit, col):
    n = d["blades"]
    swirl = [(0.0, 0.29), (0.5, 0.296), (1.0, 0.272)]

    def wave(a):
        return 0.2 + 0.012 * math.sin(n * a)

    def stripe(u, t):
        if t == 0.0:
            return 2
        w = (u * n - 0.6 * t) % 1.0
        return 1 if 0.12 < w < 0.2 or 0.55 < w < 0.6 else 0

    layer = grid_solid("Farbebene", n * 48, [t for t, _ in swirl], polar(0.1, wave),
                       lambda u, t: knots(t, swirl), lambda u, t: 0.236, [kit.layer, kit.white, kit.gun], col,
                       closed=True, zone=stripe)
    finish(layer, bevel=0.001, angle=25)
    socket(kit, col, polar(0.13, 0.19), [(0.0, 0.3), (0.5, 0.306), (1.0, 0.284)], us=96, zone_t=0.8,
           z_bot=0.26)

    # Federkranz: n Flammenbögen aus je 8 Federn, die wie Schuppen übereinanderliegen
    feather = [(0.0, 1.0), (0.12, 0.5), (0.55, 0.0), (0.9, 0.55), (1.0, 1.0)]
    per = 6
    for g in range(n):
        for j in range(per):
            a = TAU * (g + 0.62 * j / per) / n
            grow = j / (per - 1)
            reach = 0.18 + 0.045 * grow
            spine = spiral(0.135, reach, a, 0.38 + 0.2 * grow, power=1.5)
            base = 0.31 + 0.003 * j

            def ridge(t, base=base, grow=grow):
                return base + (0.014 + 0.012 * grow) * math.sin(math.pi * min(t / 0.7, 1.0)) * (1 - 0.4 * t)

            def back(t, grow=grow):
                saw = ((t * 3) % 1.0) * 0.3 if 0.2 < t < 0.9 else 0.0
                return (0.022 + 0.016 * grow) * (1 - t) ** 0.7 * (1 + saw) + 0.0015

            f = blade("Feder", spine, back, taper(0.014, 0.0015, 1.2), ridge, lambda t: 0.014,
                      lambda t, base=base: base - 0.03, [kit.chrome, kit.gun], col, profile=feather, ts=14,
                      zone=lambda u, t: 1 if u < 0.1 or t == 0.0 else 0)
            finish(f, bevel=0.0008, angle=22)

        # Klinge mit Chromrücken, schräg aus dem Kranz heraus
        b = TAU * (g + 0.8) / n
        back_plate = blade("Klingenruecken", spiral(0.15, 0.246, b - 0.08, 0.5), taper(0.044, 0.026),
                           taper(0.016, 0.01), lambda t: 0.31 + 0.012 * t, lambda t: 0.008, lambda t: 0.27,
                           [kit.chrome, kit.gun], col, ts=12, zone=lambda u, t: 1 if t == 0.0 else 0)
        finish(back_plate, bevel=0.001, angle=22)
        edge = [(0.0, 1.0), (0.1, 0.4), (0.5, 0.0), (0.88, 0.4), (1.0, 1.0)]

        def wing_zone(u, t):
            if t == 0.0:
                return 3
            if 0.38 < t < 0.7 and 0.3 < u < 0.7 and abs((u - 0.5) * 2) < 1.2 * (t - 0.38) / 0.32 + 0.15:
                return 2
            if u > 0.9:
                return 1
            return 0

        wing = blade("Klinge", spiral(0.152, 0.249, b, 0.48), taper(0.04, 0.024), taper(0.03, 0.016),
                     lambda t: 0.336 + 0.016 * t, lambda t: 0.012, lambda t: 0.29,
                     [kit.lacquer, kit.chrome, kit.white, kit.gun], col, profile=edge, ts=16, zone=wing_zone)
        finish(wing, bevel=0.001, angle=22)
    seat(kit, col, 12, 12)


BUILDERS = {
    "fortress": bollwerk,
    "vortex": wirbel,
    "fang": drachenzahn,
    "moon": eisenmond,
    "phantom": phantom,
    "feather": greif,
}


def build_wheel(d, col, key=""):
    kit = Kit(d)
    BUILDERS.get(d["shape"], drachenzahn)(d, kit, col)
    chip(d, kit, col, key)
