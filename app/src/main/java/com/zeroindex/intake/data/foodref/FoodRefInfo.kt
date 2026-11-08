package com.zeroindex.intake.data.foodref

/** Provenance and licence text for the food data, shown in the app and in docs/FOOD-DATA.md. */
object FoodRefInfo {
    const val SR_LEGACY_VERSION = "SR Legacy, April 2018"
    const val FOUNDATION_VERSION = "Foundation Foods, December 2025"

    const val USDA_CREDIT =
        "USDA FoodData Central ($SR_LEGACY_VERSION and $FOUNDATION_VERSION). U.S. Department of " +
            "Agriculture, Agricultural Research Service. Public domain (CC0 1.0). Bundled in the app " +
            "and searched offline."

    const val OFF_CREDIT =
        "Packaged foods and barcodes: Open Food Facts (openfoodfacts.org), made available under the " +
            "Open Database Licence (ODbL), with contents under the Database Contents Licence. " +
            "Looked up online only; none of it is bundled in the app."
}
