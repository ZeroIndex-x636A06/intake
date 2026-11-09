#!/usr/bin/env python3
"""Builds the bundled exercise photos and how-to text (docs/EXERCISE-IMAGES.md).

Source: the Free Exercise DB, https://github.com/yuhonas/free-exercise-db, released under the
Unlicense (public domain). Re-check LICENSE.md in that repository before you refresh the data.

Input: tools/exercise_image_map.json, a hand-reviewed map from an Intake catalog exercise name to
a Free Exercise DB id. Nothing is matched by fuzzy search. A catalog exercise that is not in the
map keeps only the muscle map in the app.

Output, under app/src/main/assets/exercises/:

    <catalog id>_0.webp   start position, about 480 px wide
    <catalog id>_1.webp   end position
    exercises.json        {"items": {"<catalog id>": {"name": ..., "steps": [3 to 5 short steps]}}}

The catalog id is the same UUID `ExerciseCatalog` derives from the name
(`seedId("exercise", name)`), computed here exactly as the JVM does.

Usage:

    python3 tools/build_exercise_images.py            # download, convert, write
    python3 tools/build_exercise_images.py --check    # only validate the map against the catalog

Python standard library plus Pillow (or, without Pillow, the `cwebp` tool). Downloads are cached in
--cache so a re-run is offline.
"""

import argparse
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RAW = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main"
CATALOG_KT = os.path.join(
    ROOT, "app/src/main/java/com/zeroindex/intake/domain/training/generator/ExerciseCatalog.kt"
)
MAP_JSON = os.path.join(HERE, "exercise_image_map.json")
DEFAULT_OUT = os.path.join(ROOT, "app/src/main/assets/exercises")
MAX_STEP_CHARS = 150
MAX_TOTAL_BYTES = 6 * 1024 * 1024


def seed_id(kind, key):
    """Java's UUID.nameUUIDFromBytes("intake-seed:<kind>:<key lowercased>")."""
    digest = bytearray(hashlib.md5(f"intake-seed:{kind}:{key.strip().lower()}".encode()).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))


def catalog_names():
    text = open(CATALOG_KT, encoding="utf-8").read()
    names = re.findall(r'^\s+ex\("([^"]+)",', text, flags=re.M)
    if len(names) < 50:
        sys.exit("Could not read the catalog names from ExerciseCatalog.kt")
    return names


def fetch(url, cache_path):
    if os.path.exists(cache_path):
        return open(cache_path, "rb").read()
    os.makedirs(os.path.dirname(cache_path), exist_ok=True)
    with urllib.request.urlopen(url, timeout=60) as resp:
        data = resp.read()
    with open(cache_path, "wb") as f:
        f.write(data)
    return data


def short_steps(instructions):
    """3 to 5 short steps: the first sentence of each kept step, spread across the whole movement."""
    cleaned = []
    for step in instructions:
        step = " ".join(step.split())
        if not step:
            continue
        first = re.split(r"(?<=[.!?])\s", step)[0]
        if len(first) > MAX_STEP_CHARS:
            first = first[: MAX_STEP_CHARS - 1].rsplit(" ", 1)[0].rstrip(",;:") + "."
        cleaned.append(first)
    if len(cleaned) <= 5:
        return cleaned
    # Keep the first, the last and three evenly spaced from the middle.
    idx = sorted({round(i * (len(cleaned) - 1) / 4) for i in range(5)})
    return [cleaned[i] for i in idx]


def convert(raw, out_path, width, quality):
    """Writes a webp (Pillow, else cwebp). Returns the path actually written."""
    try:
        from PIL import Image

        img = Image.open(io.BytesIO(raw)).convert("RGB")
        if img.width > width:
            img = img.resize((width, round(img.height * width / img.width)), Image.LANCZOS)
        img.save(out_path, "WEBP", quality=quality, method=6)
        return out_path
    except ImportError:
        pass  # Pillow missing or broken; try cwebp
    if shutil.which("cwebp"):
        tmp = out_path + ".src.jpg"
        open(tmp, "wb").write(raw)
        subprocess.run(
            ["cwebp", "-q", str(quality), "-resize", str(width), "0", tmp, "-o", out_path],
            check=True, capture_output=True,
        )
        os.remove(tmp)
        return out_path
    sys.exit("Need Pillow (python3 -c 'import PIL.Image') or the cwebp tool to write WebP files.")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--cache", default=os.path.join(ROOT, "build", "exercise-images-cache"))
    ap.add_argument("--width", type=int, default=480)
    ap.add_argument("--quality", type=int, default=75)
    ap.add_argument("--check", action="store_true", help="validate the map only; no downloads")
    ap.add_argument("--force", action="store_true", help="rewrite images that already exist")
    args = ap.parse_args()

    mapping = json.load(open(MAP_JSON, encoding="utf-8"))
    chosen = mapping["map"]
    unmatched = mapping.get("_unmatched", {})
    names = catalog_names()

    problems = []
    for n in chosen:
        if n not in names:
            problems.append(f"map names an exercise that is not in the catalog: {n!r}")
    for n in names:
        if n not in chosen and n not in unmatched:
            problems.append(f"catalog exercise is neither mapped nor listed as unmatched: {n!r}")
        if n in chosen and n in unmatched:
            problems.append(f"both mapped and unmatched: {n!r}")
    if len(set(chosen.values())) != len(chosen):
        dupes = sorted({v for v in chosen.values() if list(chosen.values()).count(v) > 1})
        print("note: one source exercise is used twice:", ", ".join(dupes))
    if problems:
        sys.exit("\n".join(problems))
    print(f"catalog {len(names)}, mapped {len(chosen)}, unmatched {len(names) - len(chosen)}")
    if args.check:
        return

    os.makedirs(args.cache, exist_ok=True)
    db = json.loads(fetch(f"{RAW}/dist/exercises.json", os.path.join(args.cache, "exercises.json")))
    by_id = {e["id"]: e for e in db}
    missing = [v for v in chosen.values() if v not in by_id]
    if missing:
        sys.exit("Not in the Free Exercise DB: " + ", ".join(missing))

    os.makedirs(args.out, exist_ok=True)
    items = {}
    total = 0
    for name, source_id in chosen.items():
        src = by_id[source_id]
        if len(src.get("images", [])) < 2:
            sys.exit(f"{source_id} has fewer than two images")
        cid = seed_id("exercise", name)
        for i in (0, 1):
            dest = os.path.join(args.out, f"{cid}_{i}.webp")
            if os.path.exists(dest) and not args.force:
                total += os.path.getsize(dest)
                continue
            raw = fetch(f"{RAW}/exercises/{src['images'][i]}", os.path.join(args.cache, src["images"][i]))
            written = convert(raw, dest, args.width, args.quality)
            total += os.path.getsize(written)
        items[cid] = {"name": name, "source": source_id, "steps": short_steps(src.get("instructions", []))}

    meta = {
        "source": "Free Exercise DB, https://github.com/yuhonas/free-exercise-db",
        "licence": "Unlicense (public domain)",
        "items": dict(sorted(items.items(), key=lambda kv: kv[1]["name"])),
    }
    with open(os.path.join(args.out, "exercises.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"wrote {len(items)} exercises, {total / 1024 / 1024:.2f} MB of images")
    if total > MAX_TOTAL_BYTES:
        sys.exit(f"Images are {total} bytes, over the {MAX_TOTAL_BYTES} budget. Lower --quality or --width.")


if __name__ == "__main__":
    main()
