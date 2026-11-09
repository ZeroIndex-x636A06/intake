"""Runs the conversion on the tiny fixture in tools/fixtures.

    python3 -I tools/test_build_food_db.py

The Gradle unit test `FoodRefAssetTest` runs this too when python3 is available.
"""

import os
import sqlite3
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import build_food_db  # noqa: E402


class BuildFoodDbTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dir = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.dir.name, "food.db")
        cls.stats = build_food_db.build(
            os.path.join(HERE, "fixtures", "sr"), os.path.join(HERE, "fixtures", "ff"), cls.out
        )
        cls.db = sqlite3.connect(cls.out)

    @classmethod
    def tearDownClass(cls):
        cls.db.close()
        cls.dir.cleanup()

    def food(self, name):
        cursor = self.db.execute("SELECT * FROM ref_food WHERE name = ?", (name,))
        columns = [c[0] for c in cursor.description]
        row = cursor.fetchone()
        return dict(zip(columns, row)) if row else None

    def test_user_version_matches_room(self):
        self.assertEqual(self.db.execute("PRAGMA user_version").fetchone()[0], build_food_db.DB_VERSION)

    def test_only_the_wanted_data_types_and_foods_with_energy(self):
        names = {r[0] for r in self.db.execute("SELECT name FROM ref_food")}
        self.assertEqual(
            names,
            {"Cheese, cheddar", "Apples, raw, with skin", "Rice, white, cooked", "Hummus, commercial"},
        )
        self.assertEqual(self.stats["skipped_without_energy"], 1)

    def test_values_are_per_100g_and_noise_is_stripped(self):
        cheese = self.food("Cheese, cheddar")
        self.assertEqual((cheese["kcal"], cheese["protein"], cheese["carbs"], cheese["fat"]), (403, 22.87, 3.37, 33.31))
        self.assertEqual((cheese["sodium"], cheese["saturatedFat"], cheese["sugar"]), (653, 18.87, 0.48))
        self.assertEqual(cheese["category"], "Dairy and Egg Products")

    def test_portions_are_capped_labelled_and_sanitised(self):
        cheese = self.food("Cheese, cheddar")
        self.assertEqual(cheese["portions"], "1 cup, diced|132;1 cup, shredded|113;1 slice (1 oz)|28")

    def test_foundation_foods_replace_sr_legacy_of_the_same_name(self):
        self.assertEqual(self.food("Apples, raw, with skin")["dataset"], "FF")

    def test_zero_weight_portions_are_dropped(self):
        self.assertEqual(self.food("Rice, white, cooked")["portions"], "1 cup|158")

    def test_energy_falls_back_to_atwater_and_foundation_ids(self):
        rice = self.food("Rice, white, cooked")  # no energy row: 4*2.69 + 4*28.17 + 9*0.28 = 125.96
        self.assertEqual(rice["kcal"], 126.0)
        apple = self.food("Apples, raw, with skin")  # nutrient 2047, sugar from 1063
        self.assertEqual((apple["kcal"], apple["sugar"]), (57.1, 10.1))

    def test_blank_fdc_ids_in_the_csv_do_not_crash(self):
        self.assertEqual(self.food("Hummus, commercial")["portions"], "2 tablespoon|33.9")


if __name__ == "__main__":
    unittest.main()
