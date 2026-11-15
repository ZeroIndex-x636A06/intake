#!/usr/bin/env python3
"""Builds the QA seed backup: a plain `intake-export` JSON that Settings > Sync and export > Import accepts.

Everything here is invented. Nothing comes from release/ (the owner's real files).

    generate.py [--end YYYY-MM-DD] [--tz Area/City] [--now-hour H] [-o out.json]

`--end` is the last day of data (default: today in --tz); the data covers the 21 days up to it, weigh-ins 35 days.
`seed.sh` calls it with the emulator's own date and time zone so Today shows data. The committed
`intake-qa-seed.json` is one run of it, kept so the format can be diffed when the schema changes.

Format: docs/SYNC-EXPORT.md, app/.../data/backup/BackupService.kt. Row shapes: the entities in data/db/entity.
Schema version: keep SCHEMA in step with IntakeDatabase.VERSION (an older value imports fine; a newer one is refused).
"""
import argparse, datetime as dt, hashlib, json, random, uuid
from zoneinfo import ZoneInfo

SCHEMA = 4  # check IntakeDatabase.VERSION; the importer only refuses a LARGER number
LB = 0.45359237
SEED_TS = 1_767_225_600_000  # the app's SEED_TIMESTAMP (2026-01-01 UTC)


def seed_id(kind, key):  # same as the app's seedId(): Java nameUUIDFromBytes (MD5, version 3)
    b = bytearray(hashlib.md5(f"intake-seed:{kind}:{key.strip().lower()}".encode()).digest())
    b[6] = (b[6] & 0x0F) | 0x30
    b[8] = (b[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(b)))


def qid(kind, key):  # ids for rows this seed owns
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"intake-qa-seed/{kind}/{key}"))


def build(end, tz, now_ms):
    rnd = random.Random(20260101)
    Z = ZoneInfo(tz)

    def at(day, h, m=0):
        return int(dt.datetime(day.year, day.month, day.day, h, m, tzinfo=Z).timestamp() * 1000)

    T = {}  # table -> rows

    def row(table, **kw):
        kw.setdefault("createdAt", now_ms)
        kw.setdefault("updatedAt", now_ms)
        kw.setdefault("deletedAt", None)
        T.setdefault(table, []).append(kw)
        return kw

    # ---- foods (per 100 g; per 100 ml for drinks) -----------------------------------------
    F = {}

    def food(name, serv_g, label, kcal, p, c, f, tags="", fiber=0.0, sugar=0.0, sodium=0.0, sat=0.0, brand=None,
             source="DATABASE", fav=False, **extra):
        r = row("food", id=qid("food", name), name=name, brand=brand, servingSizeGrams=serv_g, servingLabel=label,
                kcalPer100g=kcal, proteinPer100g=p, carbsPer100g=c, fatPer100g=f, fiberPer100g=fiber, sugarPer100g=sugar,
                sodiumPer100g=sodium, saturatedFatPer100g=sat, source=source, allergenTags=tags, isVerified=True,
                isFavorite=fav, timesLogged=0, **extra)
        F[name] = r
        return r

    MILK = "en:milk"
    # dairy-free staples
    food("Rolled oats, dry", 40, "1/2 cup (40 g)", 379, 13.2, 67.7, 6.5, fiber=10.1, sugar=1.0, sodium=6, sat=1.1, tags="en:oats | rolled oats", fav=True)
    food("Egg, whole, cooked", 50, "1 large egg (50 g)", 155, 12.6, 1.1, 10.6, sugar=1.1, sodium=124, sat=3.3, tags="en:eggs | egg")
    food("Banana", 118, "1 medium (118 g)", 89, 1.1, 22.8, 0.3, fiber=2.6, sugar=12.2, sodium=1, sat=0.1)
    food("Chicken breast, cooked", 120, "1 breast (120 g)", 165, 31.0, 0.0, 3.6, sodium=74, sat=1.0, fav=True)
    food("White rice, cooked", 160, "1 cup (160 g)", 130, 2.7, 28.2, 0.3, fiber=0.4, sodium=1, sat=0.1)
    food("Black beans, cooked", 130, "1/2 cup (130 g)", 132, 8.9, 23.7, 0.5, fiber=8.7, sugar=0.3, sodium=1, sat=0.1)
    food("Broccoli, steamed", 90, "1 cup (90 g)", 35, 2.4, 7.2, 0.4, fiber=3.3, sugar=1.4, sodium=41)
    food("Salmon fillet, baked", 150, "1 fillet (150 g)", 206, 22.1, 0.0, 12.4, sodium=61, sat=2.5, tags="en:fish | salmon")
    food("Sweet potato, baked", 130, "1 medium (130 g)", 90, 2.0, 20.7, 0.2, fiber=3.3, sugar=6.5, sodium=36)
    food("Peanut butter", 32, "2 tbsp (32 g)", 588, 25.1, 20.0, 50.4, fiber=6.0, sugar=9.2, sodium=429, sat=10.0, tags="en:peanuts | roasted peanuts, salt", fav=True)
    food("Sourdough bread", 50, "1 slice (50 g)", 259, 8.5, 51.0, 1.7, fiber=2.4, sugar=3.0, sodium=520, sat=0.4, tags="en:gluten | wheat flour, water, salt")
    food("Ground beef 90/10, cooked", 113, "4 oz (113 g)", 217, 26.1, 0.0, 11.7, sodium=72, sat=4.7)
    food("Apple", 182, "1 medium (182 g)", 52, 0.3, 13.8, 0.2, fiber=2.4, sugar=10.4, sodium=1)
    food("Olive oil", 14, "1 tbsp (14 g)", 884, 0.0, 0.0, 100.0, sat=13.8)
    food("Bell pepper, red", 120, "1 pepper (120 g)", 31, 1.0, 6.0, 0.3, fiber=2.1, sugar=4.2, sodium=4)
    food("Corn tortilla chips", 28, "1 oz (28 g)", 489, 7.0, 63.0, 23.0, fiber=5.0, sugar=1.0, sodium=420, sat=3.0, tags="corn, vegetable oil, salt")
    food("Salsa, tomato", 32, "2 tbsp (32 g)", 36, 1.5, 7.0, 0.2, fiber=1.8, sugar=4.0, sodium=430, tags="tomatoes, onions, peppers, salt")
    food("Almond milk, unsweetened", 240, "1 cup (240 ml)", 15, 0.6, 0.3, 1.2, fiber=0.2, sodium=70, sat=0.1, tags="en:nuts | almond milk", drinkCategory="MILK")
    food("Spaghetti, cooked", 140, "1 cup (140 g)", 158, 5.8, 30.9, 0.9, fiber=1.8, sodium=1, tags="en:gluten | durum wheat semolina")
    food("Turkey chili, homemade", 250, "1 bowl (250 g)", 98, 9.4, 9.6, 2.6, fiber=3.0, sodium=310, sat=0.7, source="MANUAL", tags="turkey, beans, tomatoes, onion, chili powder")
    # dairy-containing and hidden-dairy foods
    food("Cheddar cheese", 28, "1 slice (28 g)", 403, 22.9, 3.1, 33.3, sodium=653, sat=18.9, tags=f"{MILK} | cheddar cheese (milk), salt")
    food("Greek yogurt, plain", 170, "1 cup (170 g)", 97, 9.0, 3.9, 5.0, sugar=3.6, sodium=35, sat=3.2, tags=f"{MILK} | cultured pasteurized milk, cream")
    food("Milk, whole", 244, "1 cup (244 ml)", 61, 3.2, 4.8, 3.3, sugar=4.8, sodium=43, sat=1.9, tags=f"{MILK} | whole milk", drinkCategory="MILK")
    food("Butter, salted", 14, "1 tbsp (14 g)", 717, 0.9, 0.1, 81.1, sodium=643, sat=51.4, tags=f"{MILK} | cream, salt")
    food("Whey protein powder", 31, "1 scoop (31 g)", 387, 77.0, 8.0, 5.0, sugar=4.0, sodium=250, sat=3.0, brand="Test Brand", source="MANUAL",
         tags=f"{MILK} | whey protein concentrate, soy lecithin, natural flavor")
    food("Milk chocolate bar", 43, "1 bar (43 g)", 535, 7.7, 59.4, 29.7, sugar=51.5, sodium=79, sat=18.5, brand="Test Brand", source="MANUAL",
         tags=f"{MILK},en:soybeans | sugar, cocoa butter, whole milk powder, chocolate, soy lecithin")
    food("Pepperoni pizza slice", 107, "1 slice (107 g)", 276, 12.2, 29.5, 11.7, sodium=640, sat=4.9, source="MANUAL",
         tags=f"{MILK},en:gluten | wheat flour, mozzarella cheese, pepperoni, tomato sauce")
    food("Ranch dressing", 30, "2 tbsp (30 g)", 430, 1.0, 5.0, 45.0, sodium=840, sat=7.0, brand="Test Brand", source="MANUAL",
         tags="soybean oil, buttermilk, egg yolk, salt, garlic")
    food("Dark chocolate 70%", 28, "1 square (28 g)", 598, 7.8, 45.9, 42.6, sugar=24.0, sodium=24, sat=24.5, brand="Test Brand", source="MANUAL",
         tags="cocoa mass, sugar, cocoa butter | may contain milk")
    food("Protein bar, chocolate", 60, "1 bar (60 g)", 350, 33.0, 38.0, 9.0, sugar=6.0, sodium=250, sat=4.0, brand="Test Brand", source="MANUAL",
         tags="whey protein isolate, collagen, almonds, cocoa, sweetener")
    # seeded drinks the app already has (same ids and stamp as DrinkSeed, so import changes nothing there)
    for nm, kc, pr, ca, fa, su, so, caf, cat, serv, lab in [
        ("Water", 0, 0, 0, 0, 0, 0, 0, "WATER", 250, "1 glass (250 ml)"),
        ("Coffee, black", 1, .1, 0, 0, 0, 2, 40, "COFFEE", 240, "1 cup (240 ml)"),
        ("Black tea", 1, 0, .3, 0, 0, 3, 20, "TEA", 240, "1 cup (240 ml)"),
        ("Oat milk, unsweetened", 43, 1.0, 6.7, 1.5, 4.0, 40, 0, "MILK", 240, "1 cup (240 ml)"),
    ]:
        fid = seed_id("drink", nm)
        r = dict(id=fid, name=nm, brand=None, servingSizeGrams=serv, servingLabel=lab, kcalPer100g=kc, proteinPer100g=pr,
                 carbsPer100g=ca, fatPer100g=fa, fiberPer100g=0.8 if nm.startswith("Oat") else 0.0, sugarPer100g=su, sodiumPer100g=so,
                 saturatedFatPer100g=0.2 if nm.startswith("Oat") else 0.0, source="DATABASE", allergenTags="", isVerified=True,
                 isFavorite=False, timesLogged=0, drinkCategory=cat, caffeineMgPer100ml=caf, abvPercent=None,
                 hydrationFactor=1.0, createdAt=SEED_TS, updatedAt=SEED_TS, deletedAt=None)
        T.setdefault("food", []).append(r)
        F[nm] = r

    # custom drinks (CustomDrinkRecipe JSON; nutrition columns are the per-100 ml result, as CustomDrink.compute gives it)
    def custom_drink(base, base_ml, addins, caffeine_per100):
        b = F[base]
        tot = {k: b[c] * base_ml / 100 for k, c in [("kcal", "kcalPer100g"), ("p", "proteinPer100g"), ("c", "carbsPer100g"),
                                                    ("f", "fatPer100g"), ("fi", "fiberPer100g"), ("su", "sugarPer100g"),
                                                    ("so", "sodiumPer100g"), ("sat", "saturatedFatPer100g")]}
        vol = base_ml
        for a in addins:
            for k in tot:
                tot[k] += a[k] * a["grams"] / 100
            vol += a["volumeMl"]
        per = {k: v * 100 / vol for k, v in tot.items()}
        recipe = {"baseFoodId": b["id"], "baseName": base, "baseVolumeMl": base_ml, "addIns": [a["json"] for a in addins]}
        return per, vol, recipe

    def addin(key, name, amount, unit, grams, liquid, kcal, p, c, f, su, so, sat=0.0, fi=0.0, tags=""):
        k = dict(kcal=kcal, p=p, c=c, f=f, fi=fi, su=su, so=so, sat=sat, grams=grams, volumeMl=grams if liquid else 0.0)
        k["json"] = {"key": key, "name": name, "amount": amount, "unit": unit, "grams": grams, "volumeMl": k["volumeMl"],
                     "kcal": kcal, "protein": p, "carbs": c, "fat": f, "fiber": fi, "sugar": su, "sodium": so,
                     "saturatedFat": sat, "allergenTags": tags, "unlisted": ""}
        return k

    sugar3 = addin("sugar", "Sugar", 3.0, "TSP", 12.6, False, 387.0, 0.0, 99.98, 0.0, 99.8, 1.0)
    oat2 = addin("oat", "Oat milk", 2.0, "TBSP", 30.0, True, 48.0, 1.0, 8.0, 1.5, 4.0, 40.0, sat=0.2, fi=0.8, tags="oat milk")
    for nm, base, ml, ins, cat, caf, tags in [
        ("Coffee, black + 3 tsp sugar", "Coffee, black", 240.0, [sugar3], "COFFEE", 40.0, "sugar"),
        ("Black tea + 2 tbsp oat milk", "Black tea", 240.0, [oat2], "TEA", 20.0, "oat milk"),
    ]:
        per, vol, rec = custom_drink(base, ml, ins, caf)
        cafper = caf * ml / 100 * 100 / vol
        food(nm, round(vol, 1), f"{round(vol)} ml", round(per["kcal"], 2), round(per["p"], 2), round(per["c"], 2), round(per["f"], 2),
             fiber=round(per["fi"], 2), sugar=round(per["su"], 2), sodium=round(per["so"], 2), sat=round(per["sat"], 2),
             source="MANUAL", tags=tags, fav=True, drinkCategory=cat, caffeineMgPer100ml=round(cafper, 2), abvPercent=None,
             hydrationFactor=1.0, customDrink=json.dumps(rec), unlistedNutrients="")

    # ---- recipes ---------------------------------------------------------------------------
    R = {}

    def recipe(name, servings, mins, ingredients, instr, status, notes=None, nutrition=True, text="", **extra):
        rid = qid("recipe", name)
        tot = dict(k=0.0, p=0.0, c=0.0, f=0.0)
        for i, (fn, g) in enumerate(ingredients):
            fd = F[fn]
            m = g / 100
            tot["k"] += fd["kcalPer100g"] * m; tot["p"] += fd["proteinPer100g"] * m
            tot["c"] += fd["carbsPer100g"] * m; tot["f"] += fd["fatPer100g"] * m
            row("recipe_ingredient", id=qid("ri", f"{name}/{fn}"), recipeId=rid, foodId=fd["id"], quantityGrams=g, displayOrder=i, groupLabel=None)
        kw = {}
        if nutrition and ingredients:
            kw = dict(kcalPerServing=round(tot["k"] / servings), proteinPerServing=round(tot["p"] / servings, 1),
                      carbsPerServing=round(tot["c"] / servings, 1), fatPerServing=round(tot["f"] / servings, 1))
        R[name] = row("recipe", id=rid, name=name, servings=servings, instructions=instr, totalTimeMinutes=mins,
                      sourceWeekNumber=None, origin="USER", sourceUrl=None, ingredientsText=text, allergenStatus=status,
                      notes=notes, **kw, **extra)
        R[name]["_per"] = kw
        return R[name]

    recipe("Chicken fajita bowls", 4, 35,
           [("Chicken breast, cooked", 480), ("White rice, cooked", 640), ("Black beans, cooked", 260), ("Bell pepper, red", 240), ("Olive oil", 28)],
           "Slice and saute the chicken and peppers in oil with fajita spices. Serve over rice and beans.", "CLEAR",
           notes="Dairy-free as written. Do not add sour cream or cheese.", prepMinutes=15, cookMinutes=20)
    recipe("Baked mac and cheese", 6, 50,
           [("Spaghetti, cooked", 840), ("Cheddar cheese", 340), ("Milk, whole", 488), ("Butter, salted", 56)],
           "Boil the pasta, make the cheese sauce with milk and butter, bake until bubbling.", "CONTAINS",
           notes="Contains milk and cheese (from the owner's dairy days; kept for guests).", prepMinutes=15, cookMinutes=35)
    recipe("Salmon, sweet potato and broccoli", 2, 40,
           [("Salmon fillet, baked", 300), ("Sweet potato, baked", 260), ("Broccoli, steamed", 180), ("Olive oil", 14)],
           "Roast everything on one tray at 425 F for 25 minutes.", "CLEAR", prepMinutes=10, cookMinutes=30)
    recipe("Turkey chili with sour cream", 6, 60,
           [("Turkey chili, homemade", 1500), ("Greek yogurt, plain", 170)],
           "Simmer the chili. Top with a spoon of Greek yogurt.", "CONTAINS", prepMinutes=15, cookMinutes=45)
    recipe("Grandma's vegetable soup", 4, 90, [], "Everything in the big pot, simmer until soft. Season to taste.", None, nutrition=False,
           text="2 carrots\n1 onion\n3 stalks celery\n1 can diced tomatoes\n4 cups stock\nsalt and pepper",
           notes="Written down from memory; no nutrition listed on purpose.")
    # recipe 1 is dairy-free, 5 has no nutrition (kcalPerServing etc. are null), 2 and 4 contain dairy

    # ---- meal templates --------------------------------------------------------------------
    for tname, items in [
        ("Usual breakfast", [("Rolled oats, dry", 80), ("Banana", 118), ("Egg, whole, cooked", 100), ("Almond milk, unsweetened", 240)]),
        ("Post-workout shake", [("Banana", 118), ("Peanut butter", 32), ("Almond milk, unsweetened", 300)]),
    ]:
        tid = qid("tpl", tname)
        row("meal_template", id=tid, name=tname, nfcTagUid=None, nfcWrittenAt=None, lastLoggedAt=at(end - dt.timedelta(days=2), 7, 30))
        for i, (fn, g) in enumerate(items):
            row("meal_template_item", id=qid("tpli", f"{tname}/{fn}"), mealTemplateId=tid, foodId=F[fn]["id"], quantityGrams=g, displayOrder=i)

    # ---- food + water logs -----------------------------------------------------------------
    counts = {}

    def log(day, h, m, slot, fname, grams, label=None, despite=None, foodid="food", name=None, macros=None):
        fd = F.get(fname)
        n = name or fname
        if macros is None:
            k = grams / 100
            macros = dict(kcal=fd["kcalPer100g"] * k, protein=fd["proteinPer100g"] * k, carbs=fd["carbsPer100g"] * k,
                          fat=fd["fatPer100g"] * k, fiber=fd["fiberPer100g"] * k, sugar=fd["sugarPer100g"] * k,
                          sodium=fd["sodiumPer100g"] * k, saturatedFat=fd["saturatedFatPer100g"] * k)
        lab = label or (fd["servingLabel"] if fd else None)
        if despite:
            n = f"{n} (Logged despite: {despite})"
            lab = f"{lab} · Logged despite: {despite}" if lab else f"Logged despite: {despite}"
        ts = at(day, h, m)
        if ts > now_ms:
            return None
        r = row("food_log", id=qid("log", f"{day}/{h}:{m}/{slot}/{fname}"), foodId=(fd["id"] if fd and foodid == "food" else None),
                loggedAt=ts, mealSlot=slot, quantityGrams=grams, foodNameAtLog=n, servingLabelAtLog=lab,
                **{k: round(v, 2) for k, v in macros.items()}, sourcePhotoUri=None, aiConfidence=None, wasEdited=False,
                unlistedNutrients="", createdAt=ts, updatedAt=ts)
        if fd:
            counts[fname] = counts.get(fname, 0) + 1
        return r

    def log_recipe(day, h, m, slot, rname):
        rc = R[rname]["_per"]
        r = log(day, h, m, slot, None, None, label="1 serving", name=rname, foodid="none",
                macros=dict(kcal=rc["kcalPerServing"], protein=rc["proteinPerServing"], carbs=rc["carbsPerServing"], fat=rc["fatPerServing"]))
        return r

    def drink(day, h, m, fname, ml):
        r = log(day, h, m, "DRINKS", fname, ml, label=f"{int(ml)} ml")
        if r:
            row("hydration_log", id=qid("hyd", f"{day}/{h}:{m}/{fname}"), loggedAt=r["loggedAt"], volumeMl=ml, presetId=None,
                drinkType={"Coffee": "COFFEE", "Black": "TEA"}.get(fname.split(",")[0].split(" ")[0], "WATER"),
                hydrationFactor=1.0, caffeineMg=round((F[fname].get("caffeineMgPer100ml") or 0) * ml / 100, 1), alcoholMl=0.0,
                foodLogId=r["id"], createdAt=r["loggedAt"], updatedAt=r["loggedAt"])

    def water(day, h, m, ml):
        ts = at(day, h, m)
        if ts <= now_ms:
            row("hydration_log", id=qid("water", f"{day}/{h}:{m}"), loggedAt=ts, volumeMl=ml, presetId=None, drinkType="WATER",
                hydrationFactor=1.0, caffeineMg=0.0, alcoholMl=0.0, foodLogId=None, createdAt=ts, updatedAt=ts)

    days = [end - dt.timedelta(days=i) for i in range(20, -1, -1)]  # 21 days, oldest first
    for i, d in enumerate(days):
        ago = (end - d).days
        j = lambda: rnd.randint(-12, 12)
        # breakfast
        if i % 3 == 0:
            log(d, 7, 30 + j() % 15, "BREAKFAST", "Rolled oats, dry", 80)
            log(d, 7, 31, "BREAKFAST", "Banana", 118)
            log(d, 7, 32, "BREAKFAST", "Almond milk, unsweetened", 240)
        elif i % 3 == 1:
            log(d, 7, 45, "BREAKFAST", "Egg, whole, cooked", 150)
            log(d, 7, 46, "BREAKFAST", "Sourdough bread", 100)
            log(d, 7, 47, "BREAKFAST", "Apple", 182)
        else:
            log(d, 8, 0, "BREAKFAST", "Rolled oats, dry", 80)
            log(d, 8, 1, "BREAKFAST", "Peanut butter", 32)
            log(d, 8, 2, "BREAKFAST", "Banana", 118)
        drink(d, 7, 20, "Coffee, black + 3 tsp sugar", 252.6)
        # lunch
        if i % 4 == 0:
            log_recipe(d, 12, 40, "LUNCH", "Chicken fajita bowls")
        elif i % 4 == 1:
            log(d, 12, 30, "LUNCH", "Chicken breast, cooked", 180)
            log(d, 12, 31, "LUNCH", "White rice, cooked", 240)
            log(d, 12, 32, "LUNCH", "Broccoli, steamed", 135)
        elif i % 4 == 2:
            log(d, 12, 45, "LUNCH", "Turkey chili, homemade", 375)
            log(d, 12, 46, "LUNCH", "Corn tortilla chips", 28)
        else:
            log(d, 12, 35, "LUNCH", "Ground beef 90/10, cooked", 113)
            log(d, 12, 36, "LUNCH", "White rice, cooked", 160)
            log(d, 12, 37, "LUNCH", "Black beans, cooked", 130)
        # dinner
        if i % 5 == 0:
            log_recipe(d, 18, 50, "DINNER", "Salmon, sweet potato and broccoli")
        elif i % 5 == 1:
            log_recipe(d, 19, 0, "DINNER", "Chicken fajita bowls")
        elif i % 5 == 2:
            log(d, 18, 40, "DINNER", "Ground beef 90/10, cooked", 170)
            log(d, 18, 41, "DINNER", "Spaghetti, cooked", 280)
            log(d, 18, 42, "DINNER", "Olive oil", 14)
        elif i % 5 == 3:
            log(d, 19, 10, "DINNER", "Salmon fillet, baked", 150)
            log(d, 19, 11, "DINNER", "Sweet potato, baked", 260)
            log(d, 19, 12, "DINNER", "Broccoli, steamed", 180)
        else:
            log(d, 19, 5, "DINNER", "Chicken breast, cooked", 200)
            log(d, 19, 6, "DINNER", "White rice, cooked", 240)
            log(d, 19, 7, "DINNER", "Bell pepper, red", 120)
        # snacks
        if i % 2 == 0:
            log(d, 15, 30, "SNACK", "Apple", 182)
            log(d, 15, 31, "SNACK", "Peanut butter", 32)
        if i % 3 == 2:
            log(d, 21, 0, "SNACK", "Dark chocolate 70%", 28)
        if i % 4 == 3:
            log(d, 16, 0, "SNACK", "Corn tortilla chips", 28)
            log(d, 16, 1, "SNACK", "Salsa, tomato", 64)
        if i % 3 == 1:
            drink(d, 15, 0, "Black tea + 2 tbsp oat milk", 270)
        # water: 3 to 6 glasses
        for k, (h, m) in enumerate([(9, 0), (11, 0), (14, 0), (16, 30), (19, 30), (21, 30)][: 3 + (i % 4)]):
            water(d, h, m, 500 if k % 2 == 0 else 355)

    # dairy foods: two logged before the allergy was set to strict (plain), four "Choose anyway" / knowingly (annotated)
    d = lambda ago: end - dt.timedelta(days=ago)
    log(d(19), 15, 45, "SNACK", "Whey protein powder", 31)
    log(d(18), 22, 0, "SNACK", "Milk chocolate bar", 43)
    log(d(14), 13, 0, "LUNCH", "Pepperoni pizza slice", 214, despite="dairy")       # Choose anyway, eat-out
    log(d(11), 19, 30, "DINNER", "Cheddar cheese", 28, despite="dairy")
    log_r = log_recipe(d(9), 19, 45, "DINNER", "Baked mac and cheese")
    if log_r:  # recipe serving, dairy, Choose anyway
        log_r["foodNameAtLog"] += " (Logged despite: dairy)"
        log_r["servingLabelAtLog"] = "1 serving · Logged despite: dairy"
    log(d(6), 12, 50, "LUNCH", "Greek yogurt, plain", 170, despite="dairy")
    log(d(3), 20, 15, "SNACK", "Ranch dressing", 30, label="2 tbsp (30 g)")           # hidden dairy, logged with no warning shown
    log_recipe(d(1), 12, 30, "LUNCH", "Turkey chili with sour cream")

    for fname, n in counts.items():
        F[fname]["timesLogged"] = n

    # ---- body metrics: 35 days, most days, a 14-day gap in the middle -----------------------
    w = 195 * LB + 0.4
    for ago in range(34, -1, -1):
        if 7 <= ago <= 20:
            continue  # the gap
        if ago in (30, 27, 24, 5):
            continue  # a few skipped days
        w += rnd.uniform(-0.35, 0.25) - 0.012
        ts = at(end - dt.timedelta(days=ago), 6, 50)
        if ts > now_ms:
            continue
        extra = {}
        if ago in (34, 21, 0):
            extra = dict(waistCm=round(94.0 - (34 - ago) * 0.05, 1), chestCm=106.0, armCm=36.5)
        row("body_metric", id=qid("bm", ago), measuredAt=ts, weightKg=round(w, 2), source="MANUAL", createdAt=ts, updatedAt=ts, **extra)

    # ---- nutrition target ------------------------------------------------------------------
    row("nutrition_target", id=qid("target", 1), effectiveFrom=at(days[0], 0), kcal=2260, proteinG=170, carbsG=245, fatG=70, isAutoCalculated=False)

    # ---- allergen profile: dairy, STRICT. (The singleton id is the app's.) -------------------
    row("allergen_profile", id="allergen-profile", avoidedAllergens="dairy", severityLevel="STRICT", notes="Milk allergy. Strict.")

    # ---- training --------------------------------------------------------------------------
    EX = {}

    def ex(name, muscle, equip, sec="", rest=120, bw=False):
        EX[name] = row("exercise", id=seed_id("exercise", name), name=name, primaryMuscle=muscle, secondaryMuscles=sec, equipment=equip,
                       isBodyweight=bw, defaultRestSec=rest, notes=None, videoUri=None, photoPath=None, createdAt=SEED_TS, updatedAt=SEED_TS)

    ex("Barbell Back Squat", "quads", "barbell", "glutes,hamstrings", 150)
    ex("Barbell Bench Press", "chest", "barbell", "triceps,front delts", 150)
    ex("Romanian Deadlift", "hamstrings", "barbell", "glutes,lower back", 150)
    ex("Overhead Press", "shoulders", "barbell", "triceps", 120)
    ex("Pull-Up", "lats", "bodyweight", "biceps", 120, bw=True)
    ex("Dumbbell Row", "lats", "dumbbell", "biceps,rear delts", 90)
    ex("Dumbbell Curl", "biceps", "dumbbell", "", 75)
    ex("Dumbbell Lateral Raise", "shoulders", "dumbbell", "", 60)
    ex("Walking Lunge", "quads", "dumbbell", "glutes", 90)
    ex("Incline Dumbbell Press", "chest", "dumbbell", "triceps,front delts", 120)

    prog = qid("prog", "ul4")
    row("program", id=prog, name="Home gym upper/lower", description="Four days a week, barbell and dumbbells at home.", isActive=True,
        currentWeek=2, currentDayIndex=2, isStandalone=False, videoId=None, deloadEveryWeeks=None)
    layout = [
        ("Upper A", [("Barbell Bench Press", 4, 5, 8, 95), ("Dumbbell Row", 3, 8, 12, 60), ("Overhead Press", 3, 6, 10, 55), ("Pull-Up", 3, 5, 10, 0), ("Dumbbell Curl", 2, 10, 15, 25)]),
        ("Lower A", [("Barbell Back Squat", 4, 5, 8, 155), ("Romanian Deadlift", 3, 8, 10, 135), ("Walking Lunge", 3, 10, 12, 35), ("Dumbbell Lateral Raise", 3, 12, 15, 15)]),
        ("Upper B", [("Incline Dumbbell Press", 3, 8, 12, 50), ("Pull-Up", 4, 5, 10, 0), ("Overhead Press", 3, 6, 10, 55), ("Dumbbell Row", 3, 8, 12, 60), ("Dumbbell Curl", 2, 10, 15, 25)]),
        ("Lower B", [("Romanian Deadlift", 4, 6, 8, 145), ("Barbell Back Squat", 3, 8, 10, 135), ("Walking Lunge", 3, 10, 12, 35)]),
    ]
    PD, PE = {}, {}
    for di, (dname, exs) in enumerate(layout):
        did = qid("pday", dname)
        PD[dname] = did
        row("program_day", id=did, programId=prog, dayIndex=di, name=dname, weekday=None)
        PE[dname] = []
        for oi, (en, sets, lo, hi, lb) in enumerate(exs):
            pe = row("program_exercise", id=qid("pex", f"{dname}/{en}"), programDayId=did, exerciseId=EX[en]["id"], displayOrder=oi,
                     targetSets=sets, targetRepsMin=lo, targetRepsMax=hi, targetRpe=8.0, progressionRuleId=None,
                     restSec=EX[en]["defaultRestSec"], startWeightKg=round(lb * LB, 1) if lb else None, notes=None)
            PE[dname].append((pe, en, sets, lo, hi, lb))
    sessions = [(19, "Upper A", 0), (17, "Lower A", 0), (14, "Upper B", 0), (12, "Lower B", 0), (7, "Upper A", 1), (5, "Lower A", 1)]
    for ago, dname, bump in sessions:
        day = end - dt.timedelta(days=ago)
        start = at(day, 17, 30)
        sid = qid("sess", f"{ago}/{dname}")
        t = start
        sets_rows = []
        for pe, en, nsets, lo, hi, lb in PE[dname]:
            top = lb + (5 if bump and lb >= 60 else (2.5 if bump and lb else 0))
            for si in range(nsets + (1 if (pe["displayOrder"] == 0 and lb) else 0)):
                warm = pe["displayOrder"] == 0 and lb and si == 0
                wlb = round(lb * 0.6 / 2.5) * 2.5 if warm else top
                reps = 8 if warm else max(lo, hi - si - (0 if bump else 1))
                t += 150 * 1000
                sets_rows.append(dict(id=qid("set", f"{sid}/{en}/{si}"), sessionId=sid, exerciseId=EX[en]["id"],
                                      setIndex=si, weightKg=round(wlb * LB, 2), reps=reps, rpe=None if warm else rnd.choice([7.0, 7.5, 8.0, 8.5, 9.0]),
                                      isWarmup=bool(warm), isFailure=False, restTakenSec=150 if si else None, completedAt=t,
                                      createdAt=t, updatedAt=t, deletedAt=None))
        row("workout_session", id=sid, programDayId=PD[dname], startedAt=start, finishedAt=t + 120_000, notes="Felt strong." if ago == 7 else None,
            bodyweightKg=round(88.4 + rnd.uniform(-.4, .4), 1), createdAt=start, updatedAt=t)
        for s in sets_rows:
            T.setdefault("set_log", []).append(s)

    # ---- saved eat-out options (origin PLAN_B is what "Save" in Healthier nearby writes) -------
    row("eat_out_option", id=qid("eo", 1), planWeekId=None, venueName="Burrito Barn (invented)",
        whatToOrder="Chicken burrito bowl: rice, black beans, fajita veg, salsa. No cheese, no sour cream.",
        cautionNote="Say milk allergy. The queso and sour cream are dairy.", estimatedKcal=620, estimatedProtein=45,
        estimatedCarbs=70, estimatedFat=16, origin="PLAN_B", address="12 Example Street", allergenStatus="CLEAR")
    row("eat_out_option", id=qid("eo", 2), planWeekId=None, venueName="Slice House (invented)",
        whatToOrder="Grilled chicken salad, dressing on the side. Ask for no cheese.", cautionNote="Shared fryer and prep surfaces.",
        estimatedKcal=480, estimatedProtein=42, estimatedCarbs=18, estimatedFat=26, origin="PLAN_B", address="40 Sample Road",
        allergenStatus="CHECK_LABEL")

    # ---- an imported restaurant menu (pasted-text source) ----------------------------------
    msrc = qid("msrc", 1)
    items = [
        ("Grilled chicken plate", "Chicken breast, rice, steamed vegetables.", "chicken breast, white rice, broccoli, carrots, olive oil, salt", 540, 48, 55, 12, "CLEAR"),
        ("Classic cheeseburger", "Beef patty, American cheese, brioche bun.", "beef, american cheese (milk), brioche bun (wheat, butter, eggs, milk), pickles", 830, 44, 52, 48, "CONTAINS"),
        ("Garden salad", "Greens, tomato, cucumber, house ranch.", "lettuce, tomato, cucumber, ranch dressing (buttermilk, soybean oil, egg)", 310, 6, 14, 26, "CONTAINS"),
        ("Oatmeal with berries", "Steel-cut oats, mixed berries.", "oats, water, blueberries, strawberries, brown sugar", 290, 8, 55, 5, "CLEAR"),
        ("Pancake stack", "Three buttermilk pancakes, maple syrup.", "wheat flour, buttermilk, eggs, sugar, baking powder, butter, maple syrup", 760, 16, 120, 24, "CONTAINS"),
        ("Black bean burrito", "Black beans, rice, peppers, salsa, tortilla.", "flour tortilla, black beans, rice, peppers, onion, salsa", 520, 18, 85, 11, "CLEAR"),
        ("Chocolate shake", "Hand-spun chocolate shake.", "ice cream, milk, chocolate syrup", 690, 14, 96, 28, "CONTAINS"),
        ("Fish and chips", "Beer-battered cod with fries.", "cod, wheat flour, beer, potatoes, vegetable oil, salt", 880, 36, 82, 44, "CLEAR"),
    ]
    row("menu_source", id=msrc, restaurantName="Sunrise Diner (invented)", kind="TEXT", location="", pastedText="\n".join(f"{n} - {d}" for n, d, *_ in items),
        enabled=True, lastRunAt=now_ms, lastStatus="OK", lastError=None, itemCount=len(items))
    for k, (n, dsc, ing, kc, p, c, f, st) in enumerate(items):
        row("menu_item", id=qid("mi", n), sourceId=msrc, restaurantName="Sunrise Diner (invented)", name=n, description=dsc, ingredientsText=ing,
            kcal=kc, proteinG=p, carbsG=c, fatG=f, macrosPublished=True, allergenStatus=st, allergenMatches="milk" if st == "CONTAINS" else "",
            priceCents=None, importedAt=now_ms)

    # ---- hydration presets are seeded by the app; nothing to add ----------------------------

    # ---- settings (docs/SYNC-EXPORT.md: each value typed) -----------------------------------
    profile = {
        "sex": "MALE", "birthYear": 1990, "heightCm": 183.0, "build": "AVERAGE", "phase": "CUT", "pace": "GENTLE", "activity": "STANDING",
        "trainingDaysPerWeek": 4, "sessionMinutes": 60, "equipment": ["barbell", "dumbbell", "bodyweight"], "equipmentPreset": "CUSTOM",
        "equipmentItems": ["barbell_olympic", "plates", "power_rack", "adjustable_bench", "dumbbells_adjustable", "pull_up_bar",
                           "dip_station", "resistance_bands", "kettlebells"],
        "dumbbellMaxKg": 36.0, "experience": "SOME", "proteinPerKg": 1.9,
    }
    S = lambda t, v: {"type": t, "value": v}
    settings = {"version": 1, "values": {
        "weight_unit": S("string", "POUNDS"), "length_unit": S("string", "INCHES"), "volume_unit": S("string", "US_FLUID_OUNCES"),
        "portion_unit": S("string", "GRAMS"), "profile_display_name": S("string", "Sam Tester"),
        "profile_json": S("string", json.dumps(profile)), "auto_targets": S("bool", False), "goal_weight_kg": S("double", round(185 * LB, 2)),
        "protein_g_per_kg": S("double", 1.9), "default_rest_sec": S("int", 120), "bar_weight_kg": S("double", 20.0),
        "ai_master_enabled": S("bool", False),
    }}

    tables = {k: v for k, v in T.items()}
    for v in tables.values():
        for r in v:
            r.pop("_per", None)
    return {"format": "intake-export", "formatVersion": 1, "schemaVersion": SCHEMA, "exportedAt": now_ms, "tables": tables, "settings": settings}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--end"); ap.add_argument("--tz", default="America/New_York"); ap.add_argument("--now-hour", type=float)
    ap.add_argument("-o", "--out", default="intake-qa-seed.json")
    a = ap.parse_args()
    Z = ZoneInfo(a.tz)
    now = dt.datetime.now(Z)
    if a.now_hour is not None:
        now = now.replace(hour=int(a.now_hour), minute=int((a.now_hour % 1) * 60))
    end = dt.date.fromisoformat(a.end) if a.end else now.date()
    if a.end and not a.now_hour:  # a fixed past day: pretend it is the evening of that day
        now = dt.datetime(end.year, end.month, end.day, 22, 0, tzinfo=Z)
    doc = build(end, a.tz, int(now.timestamp() * 1000))
    with open(a.out, "w") as f:
        json.dump(doc, f, indent=1)
    print(a.out, {k: len(v) for k, v in doc["tables"].items()})


if __name__ == "__main__":
    main()
