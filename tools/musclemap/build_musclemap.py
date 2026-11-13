#!/usr/bin/env python3
"""Original anatomical-style front/back figure for the muscle map.

Draws the figure from scratch (no third-party art) and writes:
  - app/.../domain/training/muscles/MuscleGeometry.kt  (the data the app draws)
  - an SVG preview (same Catmull-Rom smoothing as MuscleMap.smooth), for design/mockup-r5/muscle-map.png

Usage: python3 tools/musclemap/build_musclemap.py [--svg out.svg] [--kt path]
All coordinates are figure units: 100 wide by 200 tall per view, centre line x = 50. Everything is authored for the
right half of the picture (x >= 50) and mirrored, so left and right always match.
"""
import argparse, math

W = 100.0


def mir(p):
    return (W - p[0], p[1])


# ---------------------------------------------------------------- limbs: centre line with a half width per y
class Limb:
    def __init__(self, rows):
        self.rows = rows  # (y, cx, half_width), y ascending

    def at(self, y):
        r = self.rows
        if y <= r[0][0]:
            return r[0][1], r[0][2]
        for a, b in zip(r, r[1:]):
            if a[0] <= y <= b[0]:
                t = (y - a[0]) / (b[0] - a[0])
                return a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t
        return r[-1][1], r[-1][2]

    def pt(self, y, s):
        """s = -1 inner edge .. +1 outer edge (the body's centre line is the inner side)."""
        cx, hw = self.at(y)
        return (cx + s * hw, y)

    def outline(self):
        ys = sorted({r[0] for r in self.rows})
        outer = [self.pt(y, 1) for y in ys]
        inner = [self.pt(y, -1) for y in reversed(ys)]
        return outer + inner

    def shape(self, *spec):
        """spec = (y, s) pairs, in drawing order."""
        return [self.pt(y, s) for y, s in spec]


ARM = Limb([
    (33, 73.0, 3.0), (36, 74.4, 6.0), (40, 75.4, 7.0), (48, 77.0, 7.2), (58, 78.4, 6.4), (70, 79.6, 5.4), (82, 80.2, 4.8),
    (86, 80.6, 5.3), (96, 82.0, 5.4), (106, 83.4, 4.4), (116, 84.4, 3.3), (119, 84.8, 3.5),
    (126, 85.6, 4.2), (132, 85.8, 3.8), (137, 86.0, 1.8),
])
LEG = Limb([
    (104, 60.6, 10.6), (116, 61.4, 11.4), (128, 61.4, 10.0), (140, 59.6, 8.0), (148, 58.4, 6.6),
    (154, 58.4, 6.4), (162, 59.2, 7.4), (170, 58.6, 6.4), (180, 57.4, 4.4), (188, 56.8, 3.4),
])


# ---------------------------------------------------------------- silhouette (right half; mirrored)
def joined(half):
    return half + [mir(p) for p in reversed(half[1:-1])]


def ellipse(cx, cy, rx, ry, n=16):
    return [(cx + rx * math.cos(2 * math.pi * i / n), cy + ry * math.sin(2 * math.pi * i / n)) for i in range(n)]


TORSO_HALF = [
    (50, 29), (57, 30.5), (64, 33), (70, 36), (72.5, 41), (71.5, 47), (70, 54), (68.6, 62), (66.4, 72),
    (64.6, 82), (64.4, 90), (66.8, 98), (69.5, 105), (68.5, 112), (60, 116), (50, 116),
]
HEAD = [(50, 2.4), (55, 3.4), (58, 8), (58, 14.5), (56.4, 19.6), (53.6, 23.6), (50, 25),
        (46.4, 23.6), (43.6, 19.6), (42, 14.5), (42, 8), (45, 3.4)]
NECK = [(45.0, 21), (55.0, 21), (56.2, 27), (58.5, 31.5), (41.5, 31.5), (43.8, 27)]
FOOT = [(52.8, 186), (60.6, 186), (62, 192), (63.6, 196.5), (59, 198.8), (52, 198.8), (51.6, 193)]


def silhouette():
    parts = [HEAD, NECK, joined(TORSO_HALF)]
    arm = ARM.outline()
    parts += [arm, [mir(p) for p in reversed(arm)]]
    leg = LEG.outline()
    parts += [leg, [mir(p) for p in reversed(leg)]]
    parts += [FOOT, [mir(p) for p in reversed(FOOT)]]
    return parts


# ---------------------------------------------------------------- muscles (right half; the pair is mirrored)
FRONT = []
BACK = []


def reg(view, name, pts):
    view.append((name, pts))


# --- front
reg(FRONT, "CHEST_UPPER", [(51.2, 36.4), (58, 35.6), (64.6, 36.2), (69.6, 38.6), (69.4, 44.4), (63, 45.6), (56, 46.4), (51.2, 45.6)])
reg(FRONT, "CHEST_MID_LOWER", [(51.2, 47.6), (56, 48), (63, 47), (69.2, 46.4), (68.6, 53), (64.4, 58), (58, 59.4), (51.2, 58)])
reg(FRONT, "FRONT_DELT", ARM.shape((35.5, -0.4), (36.4, 0.2), (41, 0.3), (48, 0.2), (54, -0.1), (56, -0.7), (50, -1.05), (42, -1.1), (37.5, -1.0)))
reg(FRONT, "SIDE_DELT", ARM.shape((36.4, 0.52), (37.8, 1.05), (44, 1.08), (51, 0.9), (54.5, 0.5), (48, 0.36), (41, 0.38)))
reg(FRONT, "BICEPS", ARM.shape((58, -0.85), (59.5, 0.1), (68, 0.45), (78, 0.1), (79, -0.55), (68, -0.9)))
reg(FRONT, "FOREARMS", ARM.shape((87, -0.9), (87, 0.85), (96, 0.95), (108, 0.55), (117, 0.15), (116, -0.4), (107, -0.8), (96, -0.95)))
reg(FRONT, "ABS", [(50.8, 59.8), (57, 59.4), (57.4, 70), (57, 82), (55.8, 93), (50.8, 99)])
reg(FRONT, "OBLIQUES", [(58.8, 60.4), (64.2, 58), (66, 68), (65.2, 80), (64.4, 90), (61.4, 93.4), (58.6, 88), (59.2, 74)])
reg(FRONT, "HIP_ABDUCTORS", [(64.4, 95), (68, 99.4), (70, 106), (69, 113), (65, 112), (63, 104)])
reg(FRONT, "QUADS", LEG.shape((107, -0.2), (106.5, 0.55), (112, 0.97), (124, 0.97), (136, 0.7), (146, 0.4), (147, -0.35), (138, -0.5), (126, -0.5), (114, -0.45)))
reg(FRONT, "HIP_ADDUCTORS", LEG.shape((108, -1.0), (110, -0.55), (124, -0.6), (136, -0.5), (140, -0.9), (124, -1.0)))
reg(FRONT, "CALVES", LEG.shape((155, -0.8), (154, 0.2), (160, 0.95), (170, 0.75), (178, 0.25), (176, -0.5), (166, -0.85)))

# --- back
reg(BACK, "UPPER_TRAPS", [(50.8, 25.5), (56.5, 28), (63, 31), (70, 35.6), (66.5, 41.5), (58.5, 47), (50.8, 54)])
reg(BACK, "MID_BACK", [(50.8, 56), (58, 48.8), (65, 43.8), (66.2, 52), (62, 60.5), (56.5, 68), (50.8, 71)])
reg(BACK, "LATS", [(62.6, 56.4), (68.4, 47.6), (70.8, 54), (68.6, 66), (65, 80), (59.6, 89), (56.6, 80), (57.8, 68)])
reg(BACK, "LOWER_BACK", [(50.8, 73), (56.6, 70.6), (59.6, 82), (58.8, 94), (54.4, 99), (50.8, 98.4)])
reg(BACK, "REAR_DELT", ARM.shape((35.5, -0.4), (36.4, 0.4), (41, 1.08), (50, 1.0), (55, 0.2), (51, -0.7), (43, -1.1), (37.5, -1.0)))
reg(BACK, "TRICEPS", ARM.shape((56, -0.8), (58, 0.55), (70, 0.8), (80, 0.35), (80.5, -0.5), (70, -0.9)))
reg(BACK, "FOREARMS", ARM.shape((87, -0.9), (87, 0.85), (96, 0.95), (108, 0.55), (117, 0.15), (116, -0.4), (107, -0.8), (96, -0.95)))
reg(BACK, "HIP_ABDUCTORS", [(62.6, 95.6), (68, 98.4), (70, 104.4), (66.4, 107.4), (62, 102)])
reg(BACK, "GLUTES", [(50.8, 100), (57.5, 97.6), (65, 101.5), (69.4, 108), (68, 117.4), (59, 122), (50.8, 119.6)])
reg(BACK, "HAMSTRINGS", LEG.shape((123, -0.9), (121, 0.25), (122, 0.97), (134, 0.95), (145, 0.5), (146, -0.2), (138, -0.5), (130, -0.85)))
reg(BACK, "CALVES", LEG.shape((152, -0.7), (151, 0.1), (157, 0.97), (166, 0.93), (174, 0.35), (180, -0.1), (172, -0.75), (162, -0.95)))

# --- thin anatomy lines (open strokes; right half, mirrored), for the shape language only
LINES_FRONT = [
    [(50.4, 31), (53.8, 33.6), (59, 35.4), (65, 36.6)],                     # collarbone
    [(50.8, 59), (50.8, 70), (50.8, 85), (50.8, 99)],                       # linea alba
    [(50.8, 69.6), (53, 70), (55.6, 69.4)], [(50.8, 80.6), (53, 81), (55.4, 80.4)],  # ab lines
    [(50.8, 92.5), (52.4, 93), (54.2, 92.2)],
    [(56.2, 148.4), (58, 150), (60.2, 148.4)],                              # kneecap
]
LINES_BACK = [
    [(50.4, 22), (50.4, 40), (50.4, 56), (50.4, 72), (50.4, 98)],           # spine
    [(50.8, 119), (50.8, 110), (50.8, 100)],                                # glute cleft
    [(56.6, 150.6), (58.6, 152.4), (60.4, 150.6)],                          # knee crease
]


# ---------------------------------------------------------------- Catmull-Rom as cubic Beziers (matches MuscleMap.smooth)
def smooth_closed(pts):
    n = len(pts)
    out = []
    for i in range(n):
        p0, p1, p2, p3 = pts[(i - 1) % n], pts[i], pts[(i + 1) % n], pts[(i + 2) % n]
        out.append((p1, (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6),
                    (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6), p2))
    return out


def smooth_open(pts):
    n = len(pts)
    ext = [pts[0]] + pts + [pts[-1]]
    out = []
    for i in range(n - 1):
        p0, p1, p2, p3 = ext[i], ext[i + 1], ext[i + 2], ext[i + 3]
        out.append((p1, (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6),
                    (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6), p2))
    return out


def d_closed(pts):
    segs = smooth_closed(pts)
    s = "M%.2f %.2f" % segs[0][0]
    for _, c1, c2, e in segs:
        s += " C%.2f %.2f %.2f %.2f %.2f %.2f" % (c1 + c2 + e)
    return s + "Z"


def d_open(pts):
    segs = smooth_open(pts)
    s = "M%.2f %.2f" % segs[0][0]
    for _, c1, c2, e in segs:
        s += " C%.2f %.2f %.2f %.2f %.2f %.2f" % (c1 + c2 + e)
    return s


def mirrored_ring(pts):
    return [mir(p) for p in reversed(pts)]


# ---------------------------------------------------------------- outputs
COLORS = {  # a preview colouring that shows the under / in range / over statuses
    "CHEST_UPPER": "#2f9e8f", "CHEST_MID_LOWER": "#2f9e8f", "FRONT_DELT": "#e0a030", "SIDE_DELT": "#e0a030",
    "BICEPS": "#2f9e8f", "FOREARMS": "#9aa5ab", "ABS": "#c9604a", "OBLIQUES": "#e0a030", "QUADS": "#2f9e8f",
    "HIP_ADDUCTORS": "#9aa5ab", "HIP_ABDUCTORS": "#9aa5ab", "CALVES": "#e0a030", "UPPER_TRAPS": "#9aa5ab",
    "MID_BACK": "#2f9e8f", "LATS": "#2f9e8f", "LOWER_BACK": "#e0a030", "REAR_DELT": "#e0a030", "TRICEPS": "#c9604a",
    "GLUTES": "#2f9e8f", "HAMSTRINGS": "#c9604a",
}


def svg(path):
    gap = 16
    total = 2 * 100 + gap
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d 205" width="%d" height="%d">' % (total, total * 4, 205 * 4),
           '<rect width="100%" height="100%" fill="#f6f4ef"/>']
    for view, lines, ox in [(FRONT, LINES_FRONT, 0), (BACK, LINES_BACK, 100 + gap)]:
        out.append('<g transform="translate(%d 1)">' % ox)
        for part in silhouette():
            out.append('<path d="%s" fill="#dcd6ca" stroke="#8d867a" stroke-width="0.5"/>' % d_closed(part))
        for name, pts in view:
            fill = COLORS[name]
            for ring in (pts, mirrored_ring(pts)):
                out.append('<path d="%s" fill="%s" fill-opacity="0.9" stroke="#3b3a36" stroke-width="0.45" stroke-linejoin="round"/>' % (d_closed(ring), fill))
        for ln in lines:
            for l in (ln, [mir(p) for p in ln]):
                out.append('<path d="%s" fill="none" stroke="#3b3a36" stroke-opacity="0.55" stroke-width="0.35" stroke-linecap="round"/>' % d_open(l))
        out.append('</g>')
    out.append('<text x="50" y="203" font-size="5" text-anchor="middle" fill="#555" font-family="sans-serif">Front</text>')
    out.append('<text x="%d" y="203" font-size="5" text-anchor="middle" fill="#555" font-family="sans-serif">Back</text>' % (100 + gap + 50))
    out.append('</svg>')
    open(path, "w").write("\n".join(out))


def kt_pts(pts):
    return "pts(" + ", ".join("%.1f, %.1f" % (round(x, 1), round(y, 1)) for x, y in pts) + ")"


def kotlin(path):
    L = []
    L.append("package com.zeroindex.intake.domain.training.muscles\n")
    L.append("import com.zeroindex.intake.domain.training.muscles.MuscleMap.Outline")
    L.append("import com.zeroindex.intake.domain.training.muscles.MuscleMap.Pt\n")
    L.append("/**")
    L.append(" * GENERATED by tools/musclemap/build_musclemap.py. Do not edit by hand; change the script and run it again.")
    L.append(" * An original anatomical-style figure (no third-party art). 100 wide by 200 tall per view; both sides are listed,")
    L.append(" * already mirrored, so left and right match.")
    L.append(" */")
    L.append("internal object MuscleGeometry {")
    L.append("    private fun pts(vararg xy: Double): List<Pt> = List(xy.size / 2) { Pt(xy[it * 2], xy[it * 2 + 1]) }\n")
    L.append("    val SILHOUETTE: List<List<Pt>> = listOf(")
    for part in silhouette():
        L.append("        " + kt_pts(part) + ",")
    L.append("    )\n")
    for label, view in (("FRONT", FRONT), ("BACK", BACK)):
        L.append("    val %s: List<Outline> = listOf(" % label)
        for name, pts in view:
            L.append("        Outline(MuscleRegion.%s, %s)," % (name, kt_pts(pts)))
            L.append("        Outline(MuscleRegion.%s, %s)," % (name, kt_pts(mirrored_ring(pts))))
        L.append("    )\n")
    for label, lines in (("FRONT_LINES", LINES_FRONT), ("BACK_LINES", LINES_BACK)):
        L.append("    /** Thin anatomy lines (open strokes). */")
        L.append("    val %s: List<List<Pt>> = listOf(" % label)
        for ln in lines:
            L.append("        " + kt_pts(ln) + ",")
            L.append("        " + kt_pts([mir(p) for p in ln]) + ",")
        L.append("    )\n")
    L.append("}")
    open(path, "w").write("\n".join(L) + "\n")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--svg")
    ap.add_argument("--kt")
    a = ap.parse_args()
    if a.svg:
        svg(a.svg)
    if a.kt:
        kotlin(a.kt)
