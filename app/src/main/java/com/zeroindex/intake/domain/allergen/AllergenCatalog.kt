package com.zeroindex.intake.domain.allergen

import com.zeroindex.intake.domain.model.KeywordLevel

/** One keyword shipped with the app. Plurals are matched automatically, so only the singular is listed. */
data class SeedKeyword(val term: String, val level: KeywordLevel)

/**
 * An allergen that ships with the app. [key] is the stable lower-case name stored in the profile
 * and on every keyword row, so it never changes once released; [displayName] is what people read.
 */
data class BuiltInAllergen(
    val key: String,
    val displayName: String,
    /** "US top 9" or "EU additions": where the allergen comes from. */
    val group: String,
    /** One plain sentence for the list row. */
    val summary: String,
    val keywords: List<SeedKeyword>,
)

/**
 * The predefined allergens: the US top nine (milk, egg, fish, crustacean shellfish, tree nuts,
 * peanuts, wheat, soy, sesame) and the EU-14 additions (gluten, celery, mustard, lupin, molluscs,
 * sulphites). Each carries common names, derived ingredients and hidden sources, graded by how
 * firmly the word itself speaks:
 * - CONTAINS: the word means the allergen is in the food (or is a name for it).
 * - MAY_CONTAIN: the food usually has it, but not always (a dish that is often made with it).
 * Foods that merely tend to hide the allergen ("check the label" by food type, not by ingredient name)
 * live in [HiddenCarriers]; the user can still add their own check-the-label keywords to any allergen.
 *
 * Seeding rule (see AllergenSeed): rows upsert by a deterministic id on every open, never overwrite
 * a row the user has touched, and never weaken one that exists.
 */
object AllergenCatalog {
    const val DAIRY = "dairy"
    const val EGG = "egg"
    const val FISH = "fish"
    const val SHELLFISH = "shellfish"
    const val TREE_NUTS = "tree nuts"
    const val PEANUTS = "peanuts"
    const val WHEAT = "wheat"
    const val SOY = "soy"
    const val SESAME = "sesame"
    const val GLUTEN = "gluten"
    const val CELERY = "celery"
    const val MUSTARD = "mustard"
    const val LUPIN = "lupin"
    const val MOLLUSCS = "molluscs"
    const val SULPHITES = "sulphites"

    private const val US = "US top 9"
    private const val EU = "EU additions"

    private val FLOURS_WITHOUT_WHEAT = listOf(
        "almond flour", "coconut flour", "rice flour", "chickpea flour", "corn flour", "cornflour", "potato flour",
        "tapioca flour", "buckwheat flour", "sorghum flour", "cassava flour", "gluten free flour", "banana flour",
        "lentil flour", "soy flour", "peanut flour", "arrowroot flour", "quinoa flour", "oat flour", "teff flour",
        "millet flour", "amaranth flour", "masa harina", "cauliflower flour", "hazelnut flour", "flaxseed flour",
        "tigernut flour", "plantain flour", "pea flour", "fava flour",
    )

    private fun csv(level: KeywordLevel, text: String): List<SeedKeyword> =
        text.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.map { SeedKeyword(it, level) }

    private fun contains(text: String) = csv(KeywordLevel.CONTAINS, text)
    private fun may(text: String) = csv(KeywordLevel.MAY_CONTAIN, text)

    /**
     * The original dairy aliases, in their original order. Kept as a separate list so the seed ids
     * (`seed-dairy-<term>`) and the first-run behaviour of existing installs never change.
     */
    val DAIRY_CORE: List<String> = listOf(
        "milk", "butter", "cream", "cheese", "whey", "casein", "caseinate",
        "sodium caseinate", "lactose", "ghee", "curds", "milkfat", "buttermilk",
        "custard", "half and half", "condensed milk", "evaporated milk",
        "nonfat dry milk", "milk solids", "lactalbumin", "lactoglobulin",
        "rennet casein", "dairy",
    )

    private val dairy: List<SeedKeyword> = DAIRY_CORE.map { SeedKeyword(it, KeywordLevel.CONTAINS) } +
        contains(
            """yogurt, yoghurt, kefir, skyr, quark, sour cream, cream cheese, ice cream, gelato, whipped cream,
            whipping cream, heavy cream, double cream, single cream, light cream, clotted cream, creme fraiche,
            milk powder, powdered milk, dry milk, skim milk, skimmed milk, nonfat milk, non-fat milk, whole milk,
            low fat milk, lowfat milk, goat milk, goats milk, goat's milk, sheep milk, sheep's milk, buffalo milk,
            raw milk, malted milk, milk protein, milk protein concentrate, milk protein isolate, milk fat, milk sugar,
            whey protein, whey powder, sweet whey, acid whey, hydrolyzed whey, hydrolysed whey, hydrolyzed casein,
            calcium caseinate, potassium caseinate, magnesium caseinate, ammonium caseinate, micellar casein,
            casein hydrolysate, lactoferrin, anhydrous milkfat, butterfat, butter oil, butter solids,
            butter flavor, butter flavour, buttermilk powder, clarified butter, brown butter, browned butter,
            buttered, buttery, creamed, paneer, ricotta, mascarpone, mozzarella, cheddar, parmesan, parmigiano,
            romano, pecorino, provolone, swiss cheese, gruyere, gouda, edam, emmental, brie, camembert, feta,
            halloumi, blue cheese, gorgonzola, roquefort, stilton, monterey jack, pepper jack, colby, havarti,
            fontina, asiago, manchego, queso, queso fresco, cotija, american cheese, string cheese, cottage cheese,
            cheese curds, cheese sauce, cheese powder, nacho cheese, cheesy,
            fondue, mac and cheese, macaroni and cheese, alfredo, bechamel, hollandaise, tzatziki, raita, lassi,
            dulce de leche, milkshake, milk shake, white chocolate,
            milk chocolate, panna cotta, creme brulee, tiramisu, rennet, dahi, khoya, curd, kheer, recaldent""",
        ) +
        may(
            """gratin, au gratin, bisque, chowder, scalloped potatoes, ranch dressing, caesar dressing, pizza,
            lasagna, lasagne, quesadilla, nachos, carbonara, risotto, enchilada, creamy, mashed potatoes,
            hot chocolate, cocoa mix, latte, cappuccino, flat white, cafe au lait, cheeseburger, cheesesteak, cheesecake""",
        )

    private val egg: List<SeedKeyword> =
        contains(
            """egg, egg white, egg yolk, whole egg, egg wash, egg noodle, egg powder, egg solids, dried egg, powdered egg,
            eggnog, albumin, albumen, ovalbumin, ovomucoid, ovomucin, ovovitellin, ovoglobulin, ovotransferrin,
            livetin, vitellin, lysozyme, mayonnaise, mayo, aioli, meringue, meringue powder, hollandaise, bearnaise,
            quiche, frittata, omelet, omelette, souffle, french toast, tamago, shakshuka, deviled egg,
            egg substitute""",
        ) +
        may(
            """custard, caesar dressing, caesar salad, brioche, challah, fresh pasta, surimi, fried rice, pad thai,
            tempura, battered, breaded, meatball, meatloaf, pancake, waffle, crepe, muffin, cake, ice cream,
            gelato, eggy, macaron, zabaglione, tiramisu, carbonara""",
        )

    private val fish: List<SeedKeyword> =
        contains(
            """fish, fish sauce, fish stock, fish stick, fish finger, fish and chips, fish paste, fish gelatin,
            anchovy, anchovies, bass, sea bass, bream, catfish, cod, flounder, grouper, haddock, hake, halibut,
            herring, mackerel, mahi mahi, perch, pike, plaice, pollock, salmon, sardine, snapper, swordfish,
            tilapia, trout, tuna, ahi, albacore, bonito, katsuobushi, carp, eel, unagi, monkfish, orange roughy,
            rockfish, smelt, sprat, pilchard, whiting, yellowtail, hamachi, shad, arctic char, turbot, mullet,
            pompano, marlin, wahoo, barramundi, branzino, basa, pangasius, kingfish, cobia, sturgeon, caviar, roe,
            lox, kipper, whitebait, surimi, imitation crab, nam pla, bagoong, isinglass, gefilte, taramasalata,
            sashimi, bottarga, tobiko, masago, ikura, dover sole, lemon sole, sablefish, black cod, john dory,
            fish oil, cod liver oil""",
        ) +
        may(
            """dashi, sushi, poke, caesar dressing, caesar salad, worcestershire, worcestershire sauce, seafood,
            bouillabaisse, cioppino, paella, pissaladiere, nicoise, fish tacos, ceviche, kedgeree""",
        )

    private val shellfish: List<SeedKeyword> =
        contains(
            """shrimp, prawn, crab, crab meat, crab cake, lobster, crayfish, crawfish, crawdad, langoustine,
            langostino, scampi, krill, shellfish, crustacean, barnacle, shrimp paste, belacan, balachan, ebi,
            lobster bisque, shrimp scampi, chitosan, crab stick, imitation crab, soft shell crab""",
        ) +
        may(
            """gumbo, jambalaya, paella, etouffee, bouillabaisse, cioppino, tom yum, seafood, surf and turf,
            glucosamine, seafood boil, low country boil, pad thai, laksa, bisque""",
        )

    private val molluscs: List<SeedKeyword> =
        contains(
            """mollusc, mollusk, clam, mussel, oyster, scallop, squid, calamari, octopus, cuttlefish, snail, escargot,
            abalone, whelk, conch, periwinkle, cockle, limpet, razor clam, geoduck, oyster sauce, squid ink,
            clam chowder, takoyaki, sea snail, sea scallop, baby octopus""",
        ) +
        may(
            """paella, seafood, bouillabaisse, cioppino, xo sauce, seafood boil, low country boil, tom yum, sushi""",
        )

    private val treeNuts: List<SeedKeyword> =
        contains(
            """tree nut, nut, almond, almond milk, almond butter, almond flour, almond extract, almond paste, amaretto,
            amaretti, marzipan, frangipane, praline, gianduja, nutella, hazelnut praline, brazil nut, cashew,
            cashew milk, cashew cream, chestnut, hazelnut, filbert, macadamia, pecan, pistachio, walnut, hickory nut,
            beechnut, ginkgo nut, mixed nuts, nut butter, nut milk, nut oil, nut paste, nut meal, nut flour,
            nut extract, nutmeat, orgeat, frangelico, nocino, mandelonas, nut cluster, nut cheese,
            praline paste, baklava, walnut oil, hazelnut spread, almond oil""",
        ) +
        may(
            """pesto, pine nut, pignoli, nougat, mortadella, granola, trail mix, biscotti, muesli, energy bar, snack bar,
            nut-free bar, macaroon, macaron, cassata, bakewell, stollen, torrone, turron""",
        )

    private val peanuts: List<SeedKeyword> =
        contains(
            """peanut, peanut butter, peanut oil, peanut flour, peanut sauce, peanut paste, peanut protein, groundnut,
            ground nut, goober, arachis, arachis oil, monkey nut, beer nut, mixed nuts, nut mix, satay, mandelonas,
            peanut brittle, peanut candy, boiled peanut, spanish peanut""",
        ) +
        may(
            """nuts, pad thai, kung pao, mole, nougat, satay sauce, african stew, groundnut stew, trail mix, praline, marzipan""",
        )

    private val wheatCore =
        """wheat, wheat flour, whole wheat, wheat germ, wheat bran, wheat starch, wheat protein, wheat gluten,
        hydrolyzed wheat protein, vital wheat gluten, wheatberry, wheat berry, cracked wheat, durum, durum wheat,
        semolina, spelt, kamut, khorasan, einkorn, emmer, farro, bulgur, bulghur, couscous, seitan, triticale,
        farina, graham, graham flour, atta, maida, plain flour, all-purpose flour, all purpose flour, bread flour,
        cake flour, self-raising flour, self rising flour, enriched flour, enriched wheat flour, bleached flour,
        unbleached flour, white flour, whole wheat flour, pastry flour, strong flour, panko, breadcrumb, bread crumb,
        crouton, malted wheat, wheat malt, wheatgerm, freekeh, orzo, udon, ramen, flour"""

    private val wheatLikely =
        """bread, toast, bun, pita, naan, bagel, croissant, biscuit, cracker, cookie, cake, muffin, pancake, waffle,
        scone, pastry, pie crust, pizza, pizza dough, dough, pasta, spaghetti, macaroni, lasagna, lasagne, fettuccine,
        linguine, penne, ravioli, tortellini, gnocchi, noodle, dumpling, wonton, gyoza, pierogi, empanada, breaded,
        battered, tempura, crumbed, schnitzel, soy sauce, teriyaki, hoisin, beer, sandwich, wrap, flatbread,
        pretzel, donut, doughnut, brownie, cupcake, granola, cereal, stuffing, gravy, roux"""

    private val wheat: List<SeedKeyword> = contains(wheatCore) + may(wheatLikely)

    private val gluten: List<SeedKeyword> =
        contains(
            """gluten, wheat, barley, rye, triticale, malt, malt extract, malt vinegar, malted barley, barley malt,
            malt syrup, barley flour, rye flour, rye bread, pumpernickel, pearl barley, barley water, brewer's yeast,
            $wheatCore""",
        ) +
        may("""oat, oatmeal, rolled oats, oat flour, beer, ale, lager, stout, malt liquor, $wheatLikely""")

    private val soy: List<SeedKeyword> =
        contains(
            """soy, soya, soybean, soy bean, edamame, tofu, tempeh, miso, natto, soy sauce, shoyu, tamari, soy milk,
            soya milk, soy protein, soy protein isolate, soy protein concentrate, soy flour, soy lecithin, soy oil,
            soybean oil, soy nut, soy yogurt, soy cheese, soy butter, soy sauce powder, yuba, kinako, doenjang,
            okara, bean curd, textured soy protein, textured vegetable protein, tvp, hydrolyzed soy protein,
            soy grits, soy fiber, soy isolate, soy albumin, soy sprouts, dou fu, sufu, kecap manis, tonyu""",
        ) +
        may(
            """teriyaki, hoisin, gochujang, stir fry sauce, lecithin, hydrolyzed vegetable protein, hydrolysed vegetable protein,
            vegetable protein, vegetable broth powder, asian sauce, ponzu, edamame hummus, miso soup, veggie burger,
            meat substitute, imitation meat, plant-based meat, vegan meat, protein shake mix""",
        )

    private val sesame: List<SeedKeyword> =
        contains(
            """sesame, sesame seed, sesame oil, sesame paste, sesame flour, sesame salt, tahini, tahina, halva, halvah,
            gomasio, gomashio, benne, benne seed, til, gingelly, gingelly oil, sesamol, sesamolina, tehina, hummus,
            hummus bi tahina, goma, ajinomoto goma""",
        ) +
        may(
            """za'atar, zaatar, dukkah, furikake, everything bagel, everything seasoning, baba ganoush, falafel, simit,
            sesame bun, sesame bagel, sesame cracker, sesame snaps, halva bar, benne wafers, chinese five spice,
            sesame chicken""",
        )

    private val celery: List<SeedKeyword> =
        contains(
            """celery, celery seed, celery salt, celery powder, celery root, celeriac, celery leaf, celery juice, celery stalk,
            celery stick, celery extract, mirepoix, soffritto, holy trinity, cajun trinity, celery heart, celery sauce,
            celery soup""",
        ) +
        may("""bloody mary, vegetable juice, v8, waldorf salad, chicken salad, tuna salad, egg salad, potato salad""")

    private val mustard: List<SeedKeyword> =
        contains(
            """mustard, mustard seed, mustard powder, mustard flour, mustard oil, mustard greens, mustard sauce, dijon,
            dijonnaise, yellow mustard, brown mustard, wholegrain mustard, whole grain mustard, english mustard,
            honey mustard, hot mustard, spicy mustard, mustard dressing, sarson, karashi, mostarda,
            mustard plant, mustard relish""",
        ) +
        may(
            """vinaigrette, barbecue sauce, bbq sauce, pickle, relish, deviled egg, curry, hot dog, pastrami,
            bratwurst, frankfurter, sausage, marinade, remoulade, tartar sauce, piccalilli, chow chow""",
        )

    private val lupin: List<SeedKeyword> =
        contains(
            """lupin, lupine, lupin flour, lupin seed, lupin bean, lupini, lupini bean, lupin protein, lupin fibre,
            lupin fiber, lupin milk, lupin lecithin, lupinus, lupin pasta, lupin bread, lupin hummus, lupin protein isolate,
            lupin sprouts, lupine seed, sweet lupin, lupin kernel, lupin bran""",
        )

    private val sulphites: List<SeedKeyword> =
        contains(
            """sulphite, sulfite, sulphur dioxide, sulfur dioxide, sodium sulphite, sodium sulfite, sodium bisulphite,
            sodium bisulfite, sodium metabisulphite, sodium metabisulfite, potassium metabisulphite,
            potassium metabisulfite, potassium bisulphite, potassium bisulfite, calcium sulphite, calcium sulfite,
            calcium bisulphite, calcium bisulfite, metabisulfite, metabisulphite, bisulfite, bisulphite, e220, e221,
            e222, e223, e224, e225, e226, e227, e228, so2, sulfiting agent, sulphiting agent""",
        ) +
        may(
            """wine, red wine, white wine, wine vinegar, cooking wine, mulled wine, sangria, sherry, vermouth,
            cider, hard cider, dried fruit, dried apricot, dried peach, dried apple, raisin, sultana, golden raisin,
            sulphured molasses, sulfured molasses, sauerkraut, maraschino cherry, pickled onion, bottled lemon juice,
            bottled lime juice, grape juice, dehydrated potato, dried potato""",
        )

    val ALL: List<BuiltInAllergen> = listOf(
        BuiltInAllergen(DAIRY, "Milk and dairy", US, "Milk, cheese, butter, cream, whey, casein and everything made from them.", dairy.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(EGG, "Egg", US, "Eggs and egg-based foods such as mayonnaise and meringue.", egg.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(FISH, "Fish", US, "Finned fish, fish sauces and fish oils.", fish.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(SHELLFISH, "Crustacean shellfish", US, "Shrimp, crab, lobster, crayfish and similar.", shellfish.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(TREE_NUTS, "Tree nuts", US, "Almonds, cashews, walnuts, pecans, pistachios, hazelnuts and other tree nuts.", treeNuts.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(PEANUTS, "Peanuts", US, "Peanuts and peanut oils, butters and sauces.", peanuts.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(WHEAT, "Wheat", US, "Wheat flour and foods made with it, including spelt, semolina and farro.", wheat.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(SOY, "Soy", US, "Soybeans, tofu, soy sauce, miso, lecithin and soy proteins.", soy.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(SESAME, "Sesame", US, "Sesame seeds, oil and tahini.", sesame.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(GLUTEN, "Gluten (cereals)", EU, "Wheat, rye, barley and oats, and foods made from them.", gluten.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(CELERY, "Celery", EU, "Celery stalks, root, leaves, seeds and salt.", celery.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(MUSTARD, "Mustard", EU, "Mustard seeds, powder, greens and prepared mustard.", mustard.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(LUPIN, "Lupin", EU, "Lupin seeds and flour, sometimes used in gluten-free and pastry foods.", lupin.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(MOLLUSCS, "Molluscs", EU, "Clams, mussels, oysters, scallops, squid, octopus and snails.", molluscs.distinctBy { it.term.replace('-', ' ') }),
        BuiltInAllergen(SULPHITES, "Sulphites", EU, "Sulphur dioxide and sulphites, common in wine, dried fruit and preserved foods.", sulphites.distinctBy { it.term.replace('-', ' ') }),
    )

    /**
     * Phrases that contain an allergen word without being the allergen ("peanut butter" is not butter,
     * "almond flour" is not wheat flour). A keyword hit that lies inside one of these phrases is dropped.
     * Per allergen key; the phrases are matched like keywords (whole words, plurals, hyphens as spaces).
     */
    val NOT_THE_ALLERGEN: Map<String, List<String>> = mapOf(
        DAIRY to listOf(
            "peanut butter", "almond butter", "cashew butter", "nut butter", "seed butter", "sunflower butter",
            "sunflower seed butter", "cocoa butter", "shea butter", "apple butter", "butter bean", "butter lettuce",
            "butter beans", "cream of tartar", "cream soda", "soy butter", "tahini butter", "pumpkin butter",
            "milk thistle", "coconut butter",
        ),
        WHEAT to FLOURS_WITHOUT_WHEAT,
        GLUTEN to FLOURS_WITHOUT_WHEAT.filter { it != "oat flour" },
        TREE_NUTS to listOf("water chestnut", "horse chestnut"),
        MOLLUSCS to listOf("oyster mushroom", "oyster cracker"),
    )

    /**
     * Words that, right before a dairy word, make it a plant substitute: "oat milk", "vegan butter",
     * "cashew cheese". Such a hit is "check the label" with a reason, never "contains", and never
     * "may contain": most are dairy-free, a few use casein.
     */
    val PLANT_QUALIFIERS: Set<String> = setOf(
        "oat", "almond", "soy", "soya", "coconut", "rice", "cashew", "hemp", "pea", "macadamia", "flax",
        "hazelnut", "pistachio", "walnut", "nut", "seed", "sunflower", "vegan", "plant", "oatly", "faux",
        "chickpea", "quinoa", "lupin",
    )

    private val byKey: Map<String, BuiltInAllergen> = ALL.associateBy { it.key }

    fun find(key: String): BuiltInAllergen? = byKey[key.trim().lowercase()]

    fun displayName(key: String): String =
        find(key)?.displayName ?: key.trim().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    /** Every keyword in the catalog as engine aliases, for tests and for callers with no database. */
    fun aliases(): List<AllergenAlias> =
        ALL.flatMap { a -> a.keywords.map { AllergenAlias(a.key, it.term, it.level) } }
}
