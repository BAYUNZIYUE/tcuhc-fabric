#!/usr/bin/env python3
"""T5 — numerical test of the MARINE sea-floor rewrite.

The MARINE sea floor is a pure function of (x, z) plus integer arithmetic, so it
can be verified without Minecraft. Both the old (`valueNoise`) and new
(`gradientNoise` + domain warp + rotated octaves) algorithms are ported 1:1 from
`mixins/core/MinecraftServerMixin.java`, including Java's 64-bit wrapping
arithmetic and `>>>` unsigned shift, and then measured.

Two defects are under test:

1. **Faceting.** Value noise uses smoothstep interpolation, whose derivative is
   zero at every lattice point. The old octaves are axis-aligned with periods
   80/40/20, so at any point where x and z are both multiples of 80 *all three*
   octaves are simultaneously flat. That produces the quilt of quadrilateral
   plateaus with straight edges the report describes. Metric: mean gradient
   magnitude sampled on the 80-block lattice, relative to the mean over the
   whole field. Near 0 means flat spots on a grid; near 1 means no preferred
   grid.

2. **Bounds contract.** `DensityFunction.minValue()/maxValue()` must bound
   `sample()`. The old code declared [-0.5, 0.5] while sampling [-1, 1], so the
   ChunkNoiseSampler could discard interpolation cells that held real terrain.

Run:  python docs/agent_run/2026-09-12-issue-fixes/tests/test_noise.py
"""

from __future__ import annotations

import math
import os
import re
import sys

MASK64 = (1 << 64) - 1


def u64(value: int) -> int:
    """Java long -> unsigned 64-bit representation (two's complement bit pattern)."""
    return value & MASK64


# --------------------------------------------------------------------------
# OLD: value noise, as SubmergedDensityFunction used before this change
# --------------------------------------------------------------------------

def hash2d(x: int, z: int, seed: int) -> float:
    h = u64(u64(x) * 341873128712) ^ u64(u64(z) * 132897987541) ^ u64(seed)
    h = u64(u64(h ^ (h >> 17)) * 0x68E31DA4)
    h = h ^ (h >> 13)
    return ((h & 0xFFFF) / 65535.0) * 2.0 - 1.0


def value_noise(x: float, z: float, seed: int) -> float:
    ix = math.floor(x)
    iz = math.floor(z)
    fx = x - ix
    fz = z - iz
    fx = fx * fx * (3.0 - 2.0 * fx)
    fz = fz * fz * (3.0 - 2.0 * fz)
    v00 = hash2d(ix, iz, seed)
    v10 = hash2d(ix + 1, iz, seed)
    v01 = hash2d(ix, iz + 1, seed)
    v11 = hash2d(ix + 1, iz + 1, seed)
    i0 = v00 + (v10 - v00) * fx
    i1 = v01 + (v11 - v01) * fx
    return i0 + (i1 - i0) * fz


OLD_CENTER_Y = 48.0
OLD_AMPLITUDE = 23.0


def old_floor_height(x: float, z: float) -> float:
    h = 0.0
    h += value_noise(x / 80.0, z / 80.0, 0x5DEECE66D) * 12.0
    h += value_noise(x / 40.0, z / 40.0, 0xBEEFDEAD) * 4.0
    h += value_noise(x / 20.0, z / 20.0, 0xCAFEBABE) * 2.0
    return OLD_CENTER_Y + h * OLD_AMPLITUDE / 18.0


def old_sample(x: int, y: int, z: int) -> float:
    diff = old_floor_height(x, z) - y
    if diff > 4.0:
        return 1.0
    if diff < -4.0:
        return -1.0
    return diff / 4.0


OLD_DECLARED_MIN = -0.5
OLD_DECLARED_MAX = 0.5

# --------------------------------------------------------------------------
# NEW: gradient noise + domain warp + rotated octaves
# --------------------------------------------------------------------------

def hash64(x: int, z: int, seed: int) -> int:
    h = u64(u64(x) * 0x9E3779B97F4A7C15) ^ u64(u64(z) * 0xC2B2AE3D27D4EB4F) ^ u64(u64(seed) * 0x165667B19E3779F9)
    h ^= h >> 33
    h = u64(h * 0xFF51AFD7ED558CCD)
    h ^= h >> 33
    h = u64(h * 0xC4CEB9FE1A85EC53)
    h ^= h >> 33
    return h


def fade(t: float) -> float:
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0)


_GRADIENTS = [
    (1.0, 0.0),
    (-1.0, 0.0),
    (0.0, 1.0),
    (0.0, -1.0),
    (0.70710678, 0.70710678),
    (-0.70710678, 0.70710678),
    (0.70710678, -0.70710678),
    (-0.70710678, -0.70710678),
]


def gradient_dot(x: int, z: int, dx: float, dz: float, seed: int) -> float:
    index = (hash64(x, z, seed) >> 24) & 7
    gx, gz = _GRADIENTS[index]
    return gx * dx + gz * dz


def gradient_noise(x: float, z: float, seed: int) -> float:
    x0 = math.floor(x)
    z0 = math.floor(z)
    fx = x - x0
    fz = z - z0
    u = fade(fx)
    v = fade(fz)
    n00 = gradient_dot(x0, z0, fx, fz, seed)
    n10 = gradient_dot(x0 + 1, z0, fx - 1.0, fz, seed)
    n01 = gradient_dot(x0, z0 + 1, fx, fz - 1.0, seed)
    n11 = gradient_dot(x0 + 1, z0 + 1, fx - 1.0, fz - 1.0, seed)
    a = n00 + (n10 - n00) * u
    b = n01 + (n11 - n01) * u
    return (a + (b - a) * v) * 1.4142135623730951


# Parsed straight out of the Java source so the port cannot silently drift from it.
_MIXIN = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "..", "..",
    "src", "main", "java", "me", "fallenbreath", "tcuhc",
    "mixins", "core", "MinecraftServerMixin.java",
)


def _java_constant(name: str) -> float:
    with open(os.path.abspath(_MIXIN), encoding="utf-8") as handle:
        source = handle.read()
    start = source.index("private static class SubmergedDensityFunction")
    body = source[start:]
    match = re.search(rf"private static final double {name} = (-?[\d.]+)", body)
    if match is None:
        raise AssertionError(f"{name} not found in SubmergedDensityFunction")
    return float(match.group(1))


NEW_CENTER_Y = _java_constant("CENTER_Y")
NEW_AMPLITUDE = _java_constant("AMPLITUDE")
NEW_FLOOR_RAMP = _java_constant("FLOOR_RAMP")
NEW_OCTAVE_WEIGHT_SUM = 1.0 + 0.5 + 0.25 + 0.145 + 0.08


def _octave(x: float, z: float, inverse_scale: float, cos: float, sin: float, seed: int) -> float:
    rx = (x * cos - z * sin) * inverse_scale
    rz = (x * sin + z * cos) * inverse_scale
    return gradient_noise(rx, rz, seed)


def new_floor_height(x: float, z: float) -> float:
    warp_x = x + gradient_noise(x / 210.0, z / 210.0, 0x0CEA41) * 48.0
    warp_z = z + gradient_noise(x / 210.0 + 5.7, z / 210.0 - 3.1, 0x0CEA42) * 48.0

    h = 0.0
    h += _octave(warp_x, warp_z, 1.0 / 190.0, 1.0, 0.0, 0x0CEA01) * 1.0
    h += _octave(warp_x, warp_z, 1.0 / 86.0, 0.8253356, 0.5646425, 0x0CEA02) * 0.5
    h += _octave(warp_x, warp_z, 1.0 / 39.0, 0.2674988, 0.9635582, 0x0CEA03) * 0.25
    h += _octave(warp_x, warp_z, 1.0 / 17.0, -0.5048461, 0.8632094, 0x0CEA04) * 0.145
    h += _octave(warp_x, warp_z, 1.0 / 6.0, 0.6967067, -0.7173561, 0x0CEA05) * 0.08
    return NEW_CENTER_Y + h / NEW_OCTAVE_WEIGHT_SUM * NEW_AMPLITUDE


def new_sample(x: int, y: int, z: int) -> float:
    diff = new_floor_height(x, z) - y
    if diff > NEW_FLOOR_RAMP:
        return 1.0
    if diff < -NEW_FLOOR_RAMP:
        return -1.0
    return diff / NEW_FLOOR_RAMP


NEW_DECLARED_MIN = -1.0
NEW_DECLARED_MAX = 1.0


# --------------------------------------------------------------------------
# Measurements
# --------------------------------------------------------------------------

def gradient_magnitude(height_fn, x: float, z: float, eps: float = 0.5) -> float:
    dx = (height_fn(x + eps, z) - height_fn(x - eps, z)) / (2 * eps)
    dz = (height_fn(x, z + eps) - height_fn(x, z - eps)) / (2 * eps)
    return math.hypot(dx, dz)


def lattice_flatness_ratio(height_fn, lattice: int = 80, count: int = 40) -> float:
    """Mean |grad| on the lattice, divided by the mean |grad| over the whole field.

    ~0 means the field is flat exactly on a grid (faceted). ~1 means the lattice
    is in no way special, which is what a good surface looks like.
    """
    on_lattice = []
    everywhere = []
    for i in range(count):
        for j in range(count):
            lx = (i - count // 2) * lattice
            lz = (j - count // 2) * lattice
            on_lattice.append(gradient_magnitude(height_fn, float(lx), float(lz)))
            # Offset well away from any lattice point of any octave.
            everywhere.append(gradient_magnitude(height_fn, lx + 37.3, lz + 23.7))
    mean_lattice = sum(on_lattice) / len(on_lattice)
    mean_all = sum(everywhere) / len(everywhere)
    return mean_lattice / mean_all if mean_all > 0 else float("inf")


SEA_LEVEL = 63.0


def envelope(height_fn, span: int = 3000, step: int = 37) -> tuple[float, float, float]:
    """(min height, max height, percentage of sampled columns at or above sea level)."""
    lo = float("inf")
    hi = float("-inf")
    above = 0
    total = 0
    for x in range(-span, span, step):
        for z in range(-span, span, step):
            h = height_fn(float(x), float(z))
            lo = min(lo, h)
            hi = max(hi, h)
            total += 1
            if h >= SEA_LEVEL:
                above += 1
    return lo, hi, 100.0 * above / total


def sample_range(sample_fn, span: int = 600, step: int = 13) -> tuple[float, float]:
    lo = float("inf")
    hi = float("-inf")
    for x in range(-span, span, step):
        for z in range(-span, span, step):
            for y in range(20, 90, 3):
                s = sample_fn(x, y, z)
                lo = min(lo, s)
                hi = max(hi, s)
    return lo, hi


# --------------------------------------------------------------------------

class Results:
    def __init__(self) -> None:
        self.passed = 0
        self.failed = 0

    def check(self, name: str, condition: bool, detail: str) -> None:
        if condition:
            self.passed += 1
            print(f"  PASS  {name}\n        {detail}")
        else:
            self.failed += 1
            print(f"  FAIL  {name}\n        {detail}")


def main() -> int:
    r = Results()

    print("T5.1 — bounds contract: minValue()/maxValue() must bound sample()")
    old_lo, old_hi = sample_range(old_sample)
    new_lo, new_hi = sample_range(new_sample)
    r.check(
        "old SubmergedDensityFunction violated its declared bounds",
        old_lo < OLD_DECLARED_MIN or old_hi > OLD_DECLARED_MAX,
        f"declared [{OLD_DECLARED_MIN}, {OLD_DECLARED_MAX}], actually sampled "
        f"[{old_lo:.3f}, {old_hi:.3f}] -> contract violated, as diagnosed",
    )
    r.check(
        "new SubmergedDensityFunction honours its declared bounds",
        new_lo >= NEW_DECLARED_MIN - 1e-9 and new_hi <= NEW_DECLARED_MAX + 1e-9,
        f"declared [{NEW_DECLARED_MIN}, {NEW_DECLARED_MAX}], actually sampled "
        f"[{new_lo:.3f}, {new_hi:.3f}]",
    )

    print("\nT5.2 — faceting: gradient magnitude on the noise lattice vs everywhere")
    old_ratio = lattice_flatness_ratio(old_floor_height)
    new_ratio = lattice_flatness_ratio(new_floor_height)
    r.check(
        "old floor was measurably flat on the 80-block lattice",
        old_ratio < 0.25,
        f"mean |grad| on lattice / mean |grad| overall = {old_ratio:.4f} "
        f"(<0.25 means flat spots on a grid -> faceted plateaus)",
    )
    r.check(
        "new floor has no preferred lattice",
        new_ratio > 0.6,
        f"mean |grad| on lattice / mean |grad| overall = {new_ratio:.4f} "
        f"(>0.6 means the lattice is unremarkable)",
    )
    r.check(
        "faceting improved",
        new_ratio > old_ratio * 2,
        f"ratio went {old_ratio:.4f} -> {new_ratio:.4f}",
    )

    print("\nT5.3 — gameplay envelope must be preserved, not just de-faceted")
    # MARINE seeds oak logs and saplings in its bonus chests, so the small islands the old floor
    # produced where its peaks broke the surface are part of the mode, not an artefact. Gradient
    # noise is far more concentrated around its mean than value noise, so keeping the old
    # CENTER_Y/AMPLITUDE of 48/23 removed every island (measured: 0.000% coverage). They were
    # recalibrated to 50/32 to restore the old envelope without reintroducing the faceting.
    old_lo, old_hi, old_islands = envelope(old_floor_height)
    new_lo, new_hi, new_islands = envelope(new_floor_height)
    print(f"        old: range [{old_lo:.1f}, {old_hi:.1f}], {old_islands:.3f}% of area at/above sea level")
    print(f"        new: range [{new_lo:.1f}, {new_hi:.1f}], {new_islands:.3f}% of area at/above sea level")
    r.check(
        "islands still form",
        new_islands > 0.2,
        f"{new_islands:.3f}% of sampled columns reach sea level {SEA_LEVEL:.0f} or above",
    )
    r.check(
        "island coverage stays close to the old floor",
        abs(new_islands - old_islands) < 0.4,
        f"old {old_islands:.3f}% vs new {new_islands:.3f}% (tolerance 0.4 points)",
    )
    r.check(
        "peak height stays close to the old floor",
        abs(new_hi - old_hi) < 4.0,
        f"old max {old_hi:.1f} vs new max {new_hi:.1f}",
    )
    r.check(
        "the floor is still overwhelmingly underwater",
        new_islands < 5.0 and new_lo < 45.0,
        f"{100 - new_islands:.2f}% submerged, deepest point {new_lo:.1f}",
    )

    print(f"\n{r.passed} passed, {r.failed} failed")
    return 1 if r.failed else 0


if __name__ == "__main__":
    sys.exit(main())
