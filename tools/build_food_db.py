#!/usr/bin/env python3
"""Builds the bundled offline food database (docs/FOOD-DATA.md).

Input: the CSV downloads of USDA FoodData Central, which is public domain
(CC0 1.0). SR Legacy is required; Foundation Foods is optional.

    https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip
    https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_csv_2025-12-18.zip

Output: a small SQLite file whose single table matches the Room entity
`RefFoodEntity` exactly (app/src/main/java/.../data/foodref/RefFoodEntity.kt),
so Room's `createFromAsset` accepts it. Room validates the schema of a
pre-packaged database against the entity, so if you change the table here,
change the entity too (and bump FoodRefDatabase.VERSION and the asset name).

Usage:

    python3 tools/build_food_db.py --sr <sr_legacy_dir> [--ff <foundation_dir>] \
        --out app/src/main/assets/food_ref_v1.db

Python standard library only. Every value in the output is per 100 g; sodium is
in milligrams, like the rest of the app. Portions are "label|grams" pairs
joined with ";", at most three per food.
"""

import argparse
import csv
import os
import re
import sqlite3
import sys

DB_VERSION = 1

# FoodData Central nutrient ids. Energy has three spellings: SR Legacy uses 1008,
# Foundation Foods mostly uses the Atwater factors 2047 / 2048.
ENERGY_IDS = (1008, 2047, 2048)
PROTEIN, FAT, CARBS, FIBER, SODIUM, SAT_FAT = 1003, 1004, 1005, 1079, 1093, 1258
SUGAR_IDS = (2000, 1063)  # "Sugars, Total" and the NLEA variant Foundation Foods uses
WANTED = {*ENERGY_IDS, PROTEIN, FAT, CARBS, FIBER, SODIUM, SAT_FAT, *SUGAR_IDS}

MAX_PORTIONS = 3
MAX_LABEL = 48

SCHEMA = """
CREATE TABLE `ref_food` (
    `id` INTEGER NOT NULL,
    `name` TEXT NOT NULL,
    `category` TEXT NOT NULL,
    `kcal` REAL NOT NULL,
    `protein` REAL NOT NULL,
    `carbs` REAL NOT NULL,
    `fat` REAL NOT NULL,
    `fiber` REAL NOT NULL,
    `sugar` REAL NOT NULL,
    `sodium` REAL NOT NULL,
    `saturatedFat` REAL NOT NULL,
    `portions` TEXT NOT NULL,
    `dataset` TEXT NOT NULL,
    PRIMARY KEY(`id`)
)
"""


def read_csv(directory, name):
    path = os.path.join(directory, name)
    with open(path, newline="", encoding="utf-8") as handle:
        yield from csv.DictReader(handle)


def to_int(text):
    try:
        return int(text)
    except (TypeError, ValueError):
        return None


def to_float(text):
    try:
        return float(text)
    except (TypeError, ValueError):
        return None


# USDA appends this program note to some names; it only adds noise to a search result.
NAME_NOISE = re.compile(r"\s*\(Includes foods for USDA's Food Distribution Program\)", re.IGNORECASE)


def clean_name(text):
    return NAME_NOISE.sub("", text).strip()


def load_foods(directory, data_type):
    """fdc_id -> (description, category id) for one dataset."""
    foods = {}
    for row in read_csv(directory, "food.csv"):
        if row["data_type"] == data_type and row["description"].strip() and to_int(row["fdc_id"]) is not None:
            foods[int(row["fdc_id"])] = (clean_name(row["description"]), row["food_category_id"])
    return foods


def load_categories(directory):
    return {row["id"]: row["description"].strip() for row in read_csv(directory, "food_category.csv")}


def load_nutrients(directory, ids):
    """fdc_id -> {nutrient id: amount}, streaming so the 36 MB file stays out of memory."""
    values = {}
    for row in read_csv(directory, "food_nutrient.csv"):
        nutrient = to_int(row["nutrient_id"])
        if nutrient not in WANTED:
            continue
        fdc = to_int(row["fdc_id"])
        if fdc not in ids:
            continue
        amount = to_float(row["amount"])
        if amount is not None:
            values.setdefault(fdc, {})[nutrient] = amount
    return values


def number_text(value):
    return ("%g" % value) if value is not None else ""


def portion_label(amount, unit, description, modifier):
    """'1 cup, chopped', '1 slice', '1 serving'. Units named 'undetermined' are dropped."""
    parts = []
    quantity = number_text(to_float(amount)) if to_float(amount) else ""
    unit = (unit or "").strip()
    if unit.lower() in ("undetermined", "n/a", ""):
        unit = ""
    description = (description or "").strip()
    modifier = (modifier or "").strip()
    head = " ".join(p for p in (quantity, unit) if p)
    tail = ", ".join(p for p in (description, modifier) if p)
    if head and tail:
        # "1 cup" + "chopped" reads better with a comma; "1" + "slice" does not need one.
        parts.append(head + (", " if unit else " ") + tail)
    else:
        parts.append(head or tail)
    label = parts[0].replace("|", "/").replace(";", ",").strip()
    if len(label) > MAX_LABEL:
        label = label[: MAX_LABEL - 1].rstrip() + "…"
    return label


def load_portions(directory, ids):
    units = {row["id"]: row["name"] for row in read_csv(directory, "measure_unit.csv")}
    rows = {}
    for row in read_csv(directory, "food_portion.csv"):
        fdc = to_int(row["fdc_id"])
        if fdc not in ids:
            continue
        grams = to_float(row["gram_weight"])
        if grams is None or grams <= 0:
            continue
        label = portion_label(row["amount"], units.get(row["measure_unit_id"]), row["portion_description"], row["modifier"])
        if not label:
            continue
        seq = to_float(row["seq_num"])
        rows.setdefault(fdc, []).append((seq if seq is not None else 999.0, label, grams))
    portions = {}
    for fdc, items in rows.items():
        items.sort(key=lambda item: item[0])
        seen, kept = set(), []
        for _, label, grams in items:
            if label.lower() in seen:
                continue
            seen.add(label.lower())
            kept.append("%s|%s" % (label, number_text(round(grams, 1))))
            if len(kept) == MAX_PORTIONS:
                break
        portions[fdc] = ";".join(kept)
    return portions


def first_of(values, ids):
    for nutrient in ids:
        if nutrient in values:
            return values[nutrient]
    return None


def build_rows(directory, data_type, dataset):
    foods = load_foods(directory, data_type)
    categories = load_categories(directory)
    nutrients = load_nutrients(directory, foods.keys())
    portions = load_portions(directory, foods.keys())
    rows, skipped = [], 0
    for fdc, (name, category_id) in sorted(foods.items()):
        values = nutrients.get(fdc, {})
        protein = values.get(PROTEIN, 0.0)
        carbs = values.get(CARBS, 0.0)
        fat = values.get(FAT, 0.0)
        kcal = first_of(values, ENERGY_IDS)
        if kcal is None and (protein or carbs or fat):
            kcal = 4 * protein + 4 * carbs + 9 * fat  # Atwater general factors
        if kcal is None:
            skipped += 1
            continue
        rows.append(
            (
                fdc,
                name,
                categories.get(category_id, ""),
                round(kcal, 1),
                round(protein, 2),
                round(carbs, 2),
                round(fat, 2),
                round(values.get(FIBER, 0.0), 2),
                round(first_of(values, SUGAR_IDS) or 0.0, 2),
                round(values.get(SODIUM, 0.0), 1),
                round(values.get(SAT_FAT, 0.0), 2),
                portions.get(fdc, ""),
                dataset,
            )
        )
    return rows, skipped


def build(sr_dir, ff_dir, out_path):
    rows, skipped = build_rows(sr_dir, "sr_legacy_food", "SR")
    counts = {"SR": len(rows), "FF": 0}
    if ff_dir:
        ff_rows, ff_skipped = build_rows(ff_dir, "foundation_food", "FF")
        skipped += ff_skipped
        counts["FF"] = len(ff_rows)
        rows = rows + ff_rows
    # One row per name: Foundation Foods replaces SR Legacy, otherwise the lowest id wins.
    by_name = {}
    for row in sorted(rows, key=lambda r: (r[12] != "FF", r[0])):
        by_name.setdefault(row[1].lower(), row)
    rows = list(by_name.values())
    rows.sort(key=lambda row: row[0])

    if os.path.exists(out_path):
        os.remove(out_path)
    parent = os.path.dirname(out_path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    connection = sqlite3.connect(out_path)
    try:
        connection.execute(SCHEMA)
        connection.executemany("INSERT INTO ref_food VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)
        # Room opens a pre-packaged database only when user_version matches.
        connection.execute("PRAGMA user_version = %d" % DB_VERSION)
        connection.commit()
        connection.execute("VACUUM")
    finally:
        connection.close()
    return {"rows": len(rows), "skipped_without_energy": skipped, **{"source_" + k: v for k, v in counts.items()}}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sr", required=True, help="directory with the SR Legacy CSV files")
    parser.add_argument("--ff", help="directory with the Foundation Foods CSV files (optional)")
    parser.add_argument("--out", required=True, help="SQLite file to write")
    args = parser.parse_args(argv)
    stats = build(args.sr, args.ff, args.out)
    size = os.path.getsize(args.out)
    print("wrote %s: %s, %.2f MB" % (args.out, stats, size / 1_000_000))
    return 0


if __name__ == "__main__":
    sys.exit(main())
