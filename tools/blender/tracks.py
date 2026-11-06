"""Spin Tracks: Grundplatte, eine eigene Bauform je Stil und oben die facettierte Ratsche.

Höhenband z 0.12 – 0.25, Radius bis 0.18. Die Ratsche greift von unten in das Fusion Wheel.
"""
import math

from geometry import (TAU, blade, box, cut_radial, finish, glow, grid_solid, knots, lathe, material, metal, mix,
                      polar, polygon_r, ring, sector, sphere, spiral, taper, torus, tube)


class Kit:
    def __init__(self, d):
        self.chrome = metal(d["metal"], 0.16)
        self.gun = metal("#2b2e35", 0.28)
        self.layer = material("Farbschicht", mix("#16181d", d["accent"], 0.48), roughness=0.15, coat=0.4,
                              emission=d["accent"], strength=0.35)
        self.lacquer = material("Lack", mix("#16181d", d["accent"], 0.4), metallic=0.5, roughness=0.2, coat=1.0)
        self.glow = glow(d["accent"])
        self.bright = metal("#e8ebf0", 0.06)


LOBES = {
    "teardrop": lambda s: (s / 0.78) ** 1.6 if s < 0.78 else (1 - s) / 0.22,
    "point": lambda s: (1 - abs(2 * s - 1)) ** 0.8,
    "tooth": lambda s: s ** 1.3 if s < 0.88 else (1 - s) / 0.12 * 0.88 ** 1.3,
}


def base_plate(kit, col, outline=0.17):
    plate = ring("Grundplatte", 0.058, outline, 0.123, 0.138, kit.chrome, col, n=240)
    cut_radial(plate, 48, 0.168, 0.138, 0.003, 0.004, 0.018)
    finish(plate, bevel=0.0015, angle=30)


def core(kit, col):
    c = ring("Kern", 0.044, 0.066, 0.123, 0.247, kit.chrome, col, n=96)
    cut_radial(c, 6, 0.055, 0.247, 0.006, 0.006, 0.03)
    finish(c, bevel=0.0018)


def ratchet(kit, col, lobes, lobe, r_base=0.104, r_lobe=0.052, twist=0.0):
    """Ratsche mit Nocken: Grat innen, Fase außen, Nocken steigen zur Spitze an, Chrom-Inlays."""
    profile = [(0.0, 0.236), (0.35, 0.246), (0.78, 0.24), (1.0, 0.224)]

    def zone(u, t):
        if t == 0.0:
            return 1
        s = sector(TAU * u, lobes)
        if 0.3 < t < 0.8 and 0.42 < s < 0.47:
            return 2
        return 0

    obj = grid_solid("Ratsche", lobes * 48, [t for t, _ in profile],
                     polar(0.064, lambda a: r_base + r_lobe * lobe(sector(a, lobes)), twist),
                     lambda u, t: knots(t, profile) + 0.005 * lobe(sector(TAU * u, lobes)) * t,
                     lambda u, t: 0.19, [kit.layer, kit.gun, kit.chrome], col, closed=True, zone=zone)
    finish(obj, bevel=0.0012, angle=22)


def tier(name, r_out, z0, z1, mats, col, sides=12):
    """Facettierte Stufe (Vieleck) mit Fase außen."""
    profile = [(0.0, z1), (0.7, z1 + 0.003), (1.0, z1 - 0.012)]
    obj = grid_solid(name, sides * 8, [t for t, _ in profile], polar(0.066, polygon_r(sides, r_out, TAU / (2 * sides))),
                     lambda u, t: knots(t, profile), lambda u, t: z0, mats, col, closed=True,
                     zone=lambda u, t: 1 if t >= 0.7 or t == 0.0 else 0)
    return obj


# ---------------------------------------------------------------- Bauformen

def orbit(d, kit, col):
    """Freilaufender Außenring mit sichtbaren Kugeln in einer Leuchtrille."""
    base_plate(kit, col)
    body = tier("Koerper", 0.142, 0.138, 0.19, [kit.gun, kit.chrome], col)
    cut_radial(body, 12, 0.142, 0.17, 0.014, 0.005, 0.016, TAU / 24)
    finish(body, bevel=0.0012, angle=25)
    groove = [(0.0, 0.164), (0.2, 0.169), (0.32, 0.157), (0.68, 0.157), (0.8, 0.169), (1.0, 0.162)]
    race = grid_solid("Laufring", 160, [t for t, _ in groove], polar(0.146, 0.179), lambda u, t: knots(t, groove),
                      lambda u, t: 0.138, [kit.chrome], col, closed=True)
    finish(race, bevel=0.001, angle=25)
    torus("Rillenleuchte", 0.1625, 0.002, 0.1585, kit.glow, col, n=160)
    for k in range(14):
        a = TAU * k / 14
        sphere("Kugel", 0.0085, (0.1625 * math.cos(a), 0.1625 * math.sin(a), 0.1635), kit.bright, col, seg=16)
    ratchet(kit, col, 3, LOBES["teardrop"], twist=0.25)
    core(kit, col)


def kern(d, kit, col):
    """Schwere Trommel mit acht Rippen im Wechsel Chrom und Lack."""
    base_plate(kit, col)
    drum = lathe("Trommel", [(0.064, 0.138), (0.098, 0.138), (0.098, 0.198), (0.09, 0.206), (0.064, 0.206)],
                 kit.chrome, col, n=16, closed=True)
    finish(drum, bevel=0.001, angle=25)
    torus("Trommelleuchte", 0.0985, 0.0022, 0.172, kit.glow, col, n=96)
    for k in range(8):
        fin = blade("Rippe", spiral(0.09, 0.172, TAU * k / 8, 0.18), taper(0.012, 0.004), taper(0.012, 0.004),
                    lambda t: 0.204 - 0.042 * t, lambda t: 0.007, lambda t: 0.139,
                    [kit.chrome if k % 2 == 0 else kit.lacquer, kit.gun], col, ts=8,
                    zone=lambda u, t: 1 if t == 0.0 else 0)
        finish(fin, bevel=0.001, angle=22)
    ratchet(kit, col, 4, LOBES["point"])
    core(kit, col)


def turm(d, kit, col):
    """Drei Stufen mit Zinnen, nach oben schmaler."""
    t1 = tier("Stufe", 0.176, 0.123, 0.155, [kit.chrome, kit.gun], col)
    cut_radial(t1, 24, 0.176, 0.155, 0.003, 0.004, 0.012)
    finish(t1, bevel=0.0012, angle=25)
    t2 = tier("Stufe", 0.15, 0.15, 0.186, [kit.lacquer, kit.chrome], col)
    cut_radial(t2, 6, 0.15, 0.189, 0.012, 0.022, 0.04)
    finish(t2, bevel=0.0012, angle=25)
    t3 = tier("Stufe", 0.124, 0.18, 0.214, [kit.chrome, kit.gun], col)
    cut_radial(t3, 6, 0.124, 0.217, 0.01, 0.018, 0.04, TAU / 12)
    finish(t3, bevel=0.0012, angle=25)
    torus("Stufenleuchte", 0.151, 0.0022, 0.152, kit.glow, col, n=128)
    torus("Stufenleuchte", 0.125, 0.0022, 0.182, kit.glow, col, n=128)
    ratchet(kit, col, 6, LOBES["point"], r_base=0.1, r_lobe=0.036)
    core(kit, col)


def bastion(d, kit, col):
    """Sechseckige Festung mit Spitztürmen an den Ecken und Leuchtschlitzen in den Mauern."""
    base_plate(kit, col, polygon_r(6, 0.176, TAU / 12))
    profile = [(0.0, 0.2), (0.6, 0.203), (1.0, 0.19)]
    wall = grid_solid("Mauer", 6 * 16, [t for t, _ in profile], polar(0.1, polygon_r(6, 0.15)),
                      lambda u, t: knots(t, profile), lambda u, t: 0.138, [kit.chrome, kit.gun], col, closed=True,
                      zone=lambda u, t: 1 if t >= 0.6 or t == 0.0 else 0)
    finish(wall, bevel=0.0012, angle=25)
    for k in range(6):
        a = TAU * k / 6
        x, y = 0.15 * math.cos(a), 0.15 * math.sin(a)
        turret = lathe("Turm", [(0.0, 0.128), (0.024, 0.128), (0.024, 0.206), (0.029, 0.212), (0.029, 0.22),
                                (0.016, 0.226), (0.0, 0.254)], kit.gun, col, n=8)
        turret.location = (x, y, 0.0)
        finish(turret, bevel=0.001, angle=25)
        torus("Turmring", 0.0245, 0.0018, 0.19, kit.glow, col, n=32).location = (x, y, 0.0)
        b = a + TAU / 12
        r = 0.15 * math.cos(TAU / 12) + 0.001
        box("Mauerschlitz", (r * math.cos(b), r * math.sin(b), 0.17), (0.004, 0.032, 0.01), b, kit.glow, col)
    ratchet(kit, col, 6, LOBES["tooth"])
    core(kit, col)


def feder(d, kit, col):
    """Spiralfeder mit fünf gedrehten Finnen darunter."""
    base_plate(kit, col)
    turns, steps = 3.5, 300
    pts = [(0.162 * math.cos(TAU * turns * i / steps), 0.162 * math.sin(TAU * turns * i / steps),
            0.144 + 0.044 * i / steps) for i in range(steps + 1)]
    tube("Feder", pts, 0.0085, kit.chrome, col)
    for k in range(5):
        fin = blade("Finne", spiral(0.07, 0.148, TAU * k / 5, 1.0, power=1.2), taper(0.014, 0.004),
                    taper(0.01, 0.003), lambda t: 0.205 - 0.03 * t, lambda t: 0.006, lambda t: 0.14,
                    [kit.lacquer, kit.chrome], col, ts=16, zone=lambda u, t: 1 if u > 0.85 else 0)
        finish(fin, bevel=0.001, angle=22)
    ratchet(kit, col, 5, LOBES["teardrop"], twist=0.4)
    core(kit, col)


def kelch(d, kit, col):
    """Ohne Grundplatte: facettierter Kelch, der nach oben breiter wird, mit Stützrippen an den Flanken."""
    body = lathe("Kelch", [(0.066, 0.123), (0.084, 0.123), (0.1, 0.148), (0.13, 0.174), (0.163, 0.197),
                           (0.176, 0.205), (0.176, 0.214), (0.066, 0.214)], kit.chrome, col, n=8, closed=True)
    finish(body, bevel=0.0012, angle=20)
    for k in range(8):
        a = TAU * k / 8 + TAU / 16
        rib = blade("Stuetzrippe", spiral(0.082, 0.17, a, 0.12), taper(0.008, 0.004), taper(0.008, 0.004),
                    lambda t: 0.132 + 0.075 * t, lambda t: 0.006, lambda t: 0.118 + 0.07 * t,
                    [kit.lacquer if k % 2 else kit.chrome, kit.gun], col, ts=10,
                    zone=lambda u, t: 1 if t == 0.0 else 0)
        finish(rib, bevel=0.001, angle=22)
    torus("Kelchleuchte", 0.172, 0.0024, 0.2, kit.glow, col, n=128)
    torus("Fussleuchte", 0.086, 0.002, 0.128, kit.glow, col, n=64)
    ratchet(kit, col, 4, LOBES["teardrop"], twist=0.3)
    core(kit, col)


BUILDERS = {"orbit": orbit, "core": kern, "tower": turm, "bastion": bastion, "spring": feder,
            "chalice": kelch}


def build_track(d, col, key=""):
    BUILDERS.get(d["style"], kern)(d, Kit(d), col)
