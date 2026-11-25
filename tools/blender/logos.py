"""Logos für den Emblem-Chip der Fusion Wheels, als normierte 2D-Umrisse (-1..1, y nach oben)."""
import math

TAU = 2 * math.pi


def arc_band(cx, cy, r_out, r_in, a0, a1, steps=40, sx=1.0):
    outer = [(cx + sx * r_out * math.cos(a0 + (a1 - a0) * i / steps), cy + r_out * math.sin(a0 + (a1 - a0) * i / steps))
             for i in range(steps + 1)]
    inner = [(cx + sx * r_in * math.cos(a1 - (a1 - a0) * i / steps), cy + r_in * math.sin(a1 - (a1 - a0) * i / steps))
             for i in range(steps + 1)]
    return outer + inner


def mirror(right_half):
    """Rechte Hälfte (von oben Mitte im Uhrzeigersinn bis unten Mitte) zu symmetrischer Kontur."""
    left = [(-x, y) for x, y in reversed(right_half) if abs(x) > 1e-9]
    return right_half + left


def logo_shield():
    shield = [(-0.68, 0.82), (0.68, 0.82), (0.7, 0.2)]
    shield += [(0.7 * math.cos(t) , -0.05 + 0.85 * math.sin(t) * 1.0) for t in
               [(-0.05 - 0.5 * i / 12) * math.pi for i in range(1, 12)]]
    shield += [(0.0, -0.9)]
    shield += [(-x, y) for x, y in reversed(shield[3:-1])]
    shield += [(-0.7, 0.2)]
    sword = [(-0.06, -0.62), (0.0, -0.78), (0.06, -0.62), (0.06, 0.3), (0.32, 0.3), (0.32, 0.4), (0.07, 0.4),
             (0.07, 0.58), (0.12, 0.68), (0.0, 0.8), (-0.12, 0.68), (-0.07, 0.58), (-0.07, 0.4), (-0.32, 0.4),
             (-0.32, 0.3), (-0.06, 0.3)]
    return [shield], [sword]


def logo_swirl():
    """Wirbel mit drei Armen, angelehnt ans Spiel-Logo."""
    arms = []
    steps = 30
    for k in range(3):
        base = TAU * k / 3
        outer, inner = [], []
        for i in range(steps + 1):
            t = i / steps
            r = 0.16 + 0.82 * t
            a = base + 2.0 * t
            w = 0.36 * (1 - t) ** 0.85 + 0.03
            outer.append(((r + w / 2) * math.cos(a), (r + w / 2) * math.sin(a)))
            inner.append(((r - w / 2) * math.cos(a), (r - w / 2) * math.sin(a)))
        arms.append(outer + list(reversed(inner)))
    core = [(0.12 * math.cos(TAU * i / 24), 0.12 * math.sin(TAU * i / 24)) for i in range(24)]
    return arms, [core]


def logo_dragon():
    head = [(-0.85, -0.25), (-0.62, 0.12), (-0.78, 0.62), (-0.42, 0.38), (-0.2, 0.86), (-0.05, 0.45),
            (0.3, 0.4), (0.62, 0.26), (0.92, 0.08), (0.62, 0.0), (0.88, -0.16), (0.5, -0.12), (0.62, -0.3),
            (0.3, -0.24), (0.18, -0.46), (-0.1, -0.38), (-0.3, -0.75), (-0.42, -0.42)]
    eye = [(0.08, 0.2), (0.32, 0.27), (0.2, 0.14)]
    return [head], [eye]


def logo_wolf_moon():
    moon = arc_band(-0.08, 0.06, 0.98, 0.84, math.radians(95), math.radians(265), steps=40)
    wolf = mirror([(0.0, 0.42), (0.12, 0.47), (0.28, 0.42), (0.52, 0.86), (0.5, 0.28), (0.6, 0.06),
                   (0.52, -0.16), (0.3, -0.3), (0.18, -0.56), (0.0, -0.74)])
    wolf = [(0.12 + x * 0.66, y * 0.74) for x, y in wolf]
    eyes = [[(0.12 + x * 0.66, y * 0.74) for x, y in e] for e in
            ([(0.1, 0.08), (0.3, 0.16), (0.24, 0.02)], [(-0.1, 0.08), (-0.3, 0.16), (-0.24, 0.02)])]
    return [moon, wolf], eyes


def logo_raven():
    right = [(0.0, 0.62), (0.06, 0.58), (0.09, 0.47), (0.14, 0.32), (0.36, 0.5), (0.66, 0.62),
             (0.98, 0.5), (0.8, 0.38), (0.92, 0.28), (0.7, 0.2), (0.8, 0.08), (0.54, 0.04), (0.34, -0.06),
             (0.16, -0.2), (0.14, -0.5), (0.24, -0.82), (0.0, -0.62)]
    eyes = [[(0.02, 0.5), (0.07, 0.53), (0.05, 0.47)], [(-0.02, 0.5), (-0.07, 0.53), (-0.05, 0.47)]]
    return [mirror(right)], eyes


def logo_griffin():
    right = [(0.0, 0.72), (0.12, 0.62), (0.14, 0.45), (0.3, 0.42), (0.55, 0.62), (0.95, 0.7), (0.78, 0.45),
             (0.92, 0.38), (0.7, 0.22), (0.82, 0.12), (0.55, 0.02), (0.3, -0.05), (0.2, -0.35), (0.32, -0.75),
             (0.12, -0.6), (0.0, -0.8)]
    chest = [(0.0, 0.26), (0.08, 0.1), (0.0, -0.06), (-0.08, 0.1)]
    return [mirror(right)], [chest]


LOGOS = {
    "bollwerk": logo_shield,
    "wirbel": logo_swirl,
    "drachenzahn": logo_dragon,
    "eisenmond": logo_wolf_moon,
    "phantom": logo_raven,
    "greif": logo_griffin,
}
