package com.zeroindex.intake.domain.training.equipment

import com.zeroindex.intake.domain.training.generator.Equipment
import com.zeroindex.intake.domain.training.generator.EquipmentPreset

/** The four groups of the equipment checklist. */
enum class EquipmentGroup(val label: String) {
    FREE_WEIGHTS("Free weights"),
    RACKS_AND_BENCHES("Racks and benches"),
    MACHINES("Machines"),
    OTHER("Other"),
}

/**
 * One specific piece of equipment a person can have. [id] is what is stored (a JSON set in the profile, so there is no
 * database change) and never changes once shipped; [label] is for people.
 */
enum class EquipmentItem(val id: String, val label: String, val group: EquipmentGroup, val hint: String = "") {
    // Free weights
    BARBELL_OLYMPIC("barbell_olympic", "Barbell (Olympic)", EquipmentGroup.FREE_WEIGHTS, "2 in sleeves, 20 kg bar"),
    BARBELL_STANDARD("barbell_standard", "Barbell (standard)", EquipmentGroup.FREE_WEIGHTS, "1 in sleeves"),
    EZ_BAR("ez_bar", "EZ bar", EquipmentGroup.FREE_WEIGHTS),
    TRAP_BAR("trap_bar", "Trap bar", EquipmentGroup.FREE_WEIGHTS),
    DUMBBELLS_FIXED("dumbbells_fixed", "Dumbbells (fixed)", EquipmentGroup.FREE_WEIGHTS),
    DUMBBELLS_ADJUSTABLE("dumbbells_adjustable", "Dumbbells (adjustable)", EquipmentGroup.FREE_WEIGHTS),
    KETTLEBELLS("kettlebells", "Kettlebells", EquipmentGroup.FREE_WEIGHTS),
    PLATES("plates", "Weight plates", EquipmentGroup.FREE_WEIGHTS),

    // Racks and benches
    POWER_RACK("power_rack", "Power rack", EquipmentGroup.RACKS_AND_BENCHES),
    SQUAT_STAND("squat_stand", "Squat stand", EquipmentGroup.RACKS_AND_BENCHES),
    SMITH_MACHINE("smith_machine", "Smith machine", EquipmentGroup.RACKS_AND_BENCHES),
    FLAT_BENCH("flat_bench", "Flat bench", EquipmentGroup.RACKS_AND_BENCHES),
    ADJUSTABLE_BENCH("adjustable_bench", "Adjustable bench", EquipmentGroup.RACKS_AND_BENCHES),
    DECLINE_BENCH("decline_bench", "Decline bench", EquipmentGroup.RACKS_AND_BENCHES),
    PREACHER_BENCH("preacher_bench", "Preacher bench", EquipmentGroup.RACKS_AND_BENCHES),
    DIP_STATION("dip_station", "Dip station", EquipmentGroup.RACKS_AND_BENCHES),
    PULL_UP_BAR("pull_up_bar", "Pull-up bar", EquipmentGroup.RACKS_AND_BENCHES),

    // Machines
    CABLE_STACK("cable_stack", "Cable stack or functional trainer", EquipmentGroup.MACHINES),
    LAT_PULLDOWN("lat_pulldown", "Lat pulldown", EquipmentGroup.MACHINES),
    SEATED_ROW("seated_row", "Seated row", EquipmentGroup.MACHINES),
    LEG_PRESS("leg_press", "Leg press", EquipmentGroup.MACHINES),
    HACK_SQUAT("hack_squat", "Hack squat", EquipmentGroup.MACHINES),
    LEG_EXTENSION("leg_extension", "Leg extension", EquipmentGroup.MACHINES),
    LEG_CURL("leg_curl", "Leg curl", EquipmentGroup.MACHINES),
    CHEST_PRESS_MACHINE("chest_press_machine", "Chest press", EquipmentGroup.MACHINES),
    PEC_DECK("pec_deck", "Pec deck", EquipmentGroup.MACHINES),
    SHOULDER_PRESS_MACHINE("shoulder_press_machine", "Shoulder press", EquipmentGroup.MACHINES, "also used for the lateral raise machine"),
    CALF_RAISE("calf_raise_machine", "Calf raise", EquipmentGroup.MACHINES),
    HIP_THRUST_MACHINE("hip_thrust_machine", "Hip thrust", EquipmentGroup.MACHINES),
    ASSISTED_PULL_UP("assisted_pull_up", "Assisted pull-up", EquipmentGroup.MACHINES),
    HIP_ABDUCTION_MACHINE("hip_abduction_machine", "Hip abduction", EquipmentGroup.MACHINES),

    // Other
    RESISTANCE_BANDS("resistance_bands", "Resistance bands", EquipmentGroup.OTHER),
    SUSPENSION_TRAINER("suspension_trainer", "Suspension trainer", EquipmentGroup.OTHER),
    MEDICINE_BALL("medicine_ball", "Medicine ball", EquipmentGroup.OTHER),
    LANDMINE("landmine", "Landmine", EquipmentGroup.OTHER),
    SLED("sled", "Sled", EquipmentGroup.OTHER),
    ROWER("rower", "Rower", EquipmentGroup.OTHER),
    BIKE("bike", "Bike", EquipmentGroup.OTHER),
    TREADMILL("treadmill", "Treadmill", EquipmentGroup.OTHER),
    JUMP_ROPE("jump_rope", "Jump rope", EquipmentGroup.OTHER),
    FOAM_ROLLER("foam_roller", "Foam roller", EquipmentGroup.OTHER),
}

/** The item list, the quick-pick presets, the move from the old five kinds, and the kinds an item set still stands for. */
object EquipmentItems {
    val ALL: List<EquipmentItem> = EquipmentItem.entries
    val ALL_IDS: Set<String> = ALL.map { it.id }.toSet()

    fun byId(id: String): EquipmentItem? = ALL.firstOrNull { it.id == id }

    fun inGroup(group: EquipmentGroup): List<EquipmentItem> = ALL.filter { it.group == group }

    /** Drops ids this version does not know (a profile from a newer build), so nothing unknown is ever stored back. */
    fun normalize(ids: Collection<String>): Set<String> = ids.filter { it in ALL_IDS }.toSet()

    private fun ids(vararg items: EquipmentItem): Set<String> = items.map { it.id }.toSet()

    private val BARBELLS = ids(EquipmentItem.BARBELL_OLYMPIC, EquipmentItem.BARBELL_STANDARD, EquipmentItem.EZ_BAR, EquipmentItem.TRAP_BAR)
    private val DUMBBELLS = ids(EquipmentItem.DUMBBELLS_FIXED, EquipmentItem.DUMBBELLS_ADJUSTABLE)
    private val CABLES = ids(EquipmentItem.CABLE_STACK, EquipmentItem.LAT_PULLDOWN, EquipmentItem.SEATED_ROW)
    private val MACHINES: Set<String> = inGroup(EquipmentGroup.MACHINES).map { it.id }.toSet() - CABLES

    /** What each quick pick fills in. Full gym is every item. */
    fun presetItems(preset: EquipmentPreset): Set<String> = when (preset) {
        EquipmentPreset.FULL_GYM -> ALL_IDS
        EquipmentPreset.HOME_DUMBBELLS -> ids(EquipmentItem.DUMBBELLS_ADJUSTABLE, EquipmentItem.ADJUSTABLE_BENCH, EquipmentItem.PULL_UP_BAR)
        EquipmentPreset.BODYWEIGHT_ONLY -> ids(EquipmentItem.PULL_UP_BAR)
        EquipmentPreset.CUSTOM -> emptySet()
    }

    /** The preset whose items are exactly [items], or CUSTOM. */
    fun presetFor(items: Set<String>): EquipmentPreset =
        EquipmentPreset.entries.firstOrNull { it != EquipmentPreset.CUSTOM && presetItems(it) == items } ?: EquipmentPreset.CUSTOM

    /**
     * The move from the old five kinds to items, for a profile saved before the checklist existed. A saved preset (with its
     * kinds unchanged) gives that preset's items. Otherwise each kind adds what it used to allow, so nothing a person could do
     * disappears: barbell adds a barbell, plates, a rack and benches; dumbbell adds dumbbells and an adjustable bench; machine
     * adds the machines, a Smith machine and a preacher bench; cable adds the cable items; bodyweight adds a pull-up bar and a
     * dip station. The person can untick anything they do not have.
     */
    fun fromLegacy(kinds: Set<String>, preset: EquipmentPreset? = null): Set<String> {
        val k = kinds.map { it.trim().lowercase() }.toSet()
        if (preset != null && preset != EquipmentPreset.CUSTOM && k == preset.kinds) return presetItems(preset)
        val out = linkedSetOf<String>()
        if (Equipment.BARBELL in k) {
            out += ids(EquipmentItem.BARBELL_OLYMPIC, EquipmentItem.PLATES, EquipmentItem.POWER_RACK, EquipmentItem.FLAT_BENCH, EquipmentItem.ADJUSTABLE_BENCH)
        }
        if (Equipment.DUMBBELL in k) out += ids(EquipmentItem.DUMBBELLS_FIXED, EquipmentItem.ADJUSTABLE_BENCH)
        if (Equipment.MACHINE in k) out += MACHINES + ids(EquipmentItem.SMITH_MACHINE, EquipmentItem.PREACHER_BENCH, EquipmentItem.SEATED_ROW)
        if (Equipment.CABLE in k) out += CABLES
        if (Equipment.BODYWEIGHT in k) out += ids(EquipmentItem.PULL_UP_BAR, EquipmentItem.DIP_STATION)
        return out
    }

    /**
     * The old coarse kinds an item set still stands for (the video suggestions and the old library filter read them).
     * Bodyweight is always there.
     */
    fun kindsOf(items: Set<String>): Set<String> {
        val out = linkedSetOf(Equipment.BODYWEIGHT)
        if (items.any { it in BARBELLS }) out += Equipment.BARBELL
        if (items.any { it in DUMBBELLS }) out += Equipment.DUMBBELL
        if (items.any { it in MACHINES || it == EquipmentItem.SMITH_MACHINE.id }) out += Equipment.MACHINE
        if (items.any { it in CABLES }) out += Equipment.CABLE
        return out
    }

    /** "12 items: barbell, dumbbells ..." for the plan explainer. */
    fun summary(items: Set<String>): String {
        val labels = ALL.filter { it.id in items }.map { it.label.lowercase() }
        return when {
            labels.isEmpty() -> "none"
            labels.size <= 6 -> labels.joinToString(", ")
            else -> "${labels.size} items (${labels.take(4).joinToString(", ")} and more)"
        }
    }
}

/**
 * What an exercise needs, in items. A requirement is a list of groups; every group must be met, and a group is met by owning
 * any one of its items. The rules read the exercise's old kind and its name, so library, custom and imported exercises all
 * work. An exercise that names nothing (or an unknown kind) needs nothing.
 */
object EquipmentRequirements {
    private fun any(vararg items: EquipmentItem): Set<EquipmentItem> = items.toSet()

    private val BARBELL = any(EquipmentItem.BARBELL_OLYMPIC, EquipmentItem.BARBELL_STANDARD)
    private val BAR_ANY = any(EquipmentItem.BARBELL_OLYMPIC, EquipmentItem.BARBELL_STANDARD, EquipmentItem.EZ_BAR, EquipmentItem.TRAP_BAR)
    private val PLATES = any(EquipmentItem.PLATES)
    private val BENCH = any(EquipmentItem.FLAT_BENCH, EquipmentItem.ADJUSTABLE_BENCH)
    private val RACK = any(EquipmentItem.POWER_RACK, EquipmentItem.SQUAT_STAND, EquipmentItem.SMITH_MACHINE)
    private val DUMBBELLS = any(EquipmentItem.DUMBBELLS_FIXED, EquipmentItem.DUMBBELLS_ADJUSTABLE)
    private val ANY_MACHINE: Set<EquipmentItem> = EquipmentItems.inGroup(EquipmentGroup.MACHINES).toSet() - EquipmentItem.CABLE_STACK

    fun forExercise(name: String, kind: String): List<Set<EquipmentItem>> {
        val n = name.trim().lowercase()
        val k = kind.trim().lowercase()

        // Gear named in the exercise's own name wins: it is what the lift is done with.
        when {
            "kettlebell" in n -> return listOf(any(EquipmentItem.KETTLEBELLS))
            "band" in n && "barbell" !in n -> return listOf(any(EquipmentItem.RESISTANCE_BANDS))
            "trx" in n || "suspension" in n -> return listOf(any(EquipmentItem.SUSPENSION_TRAINER))
            "landmine" in n -> return listOf(any(EquipmentItem.LANDMINE), BARBELL)
            "sled" in n -> return listOf(any(EquipmentItem.SLED))
            "rowing machine" in n || "rower" in n || "ergometer" in n -> return listOf(any(EquipmentItem.ROWER))
            "bike" in n || "cycling" in n -> return listOf(any(EquipmentItem.BIKE))
            "treadmill" in n -> return listOf(any(EquipmentItem.TREADMILL))
            "jump rope" in n || "skipping" in n -> return listOf(any(EquipmentItem.JUMP_ROPE))
            "medicine ball" in n || "med ball" in n -> return listOf(any(EquipmentItem.MEDICINE_BALL))
            "foam roll" in n -> return listOf(any(EquipmentItem.FOAM_ROLLER))
            "trap bar" in n || "hex bar" in n -> return listOf(any(EquipmentItem.TRAP_BAR), PLATES)
            "smith" in n -> return listOf(any(EquipmentItem.SMITH_MACHINE))
        }

        return when (k) {
            Equipment.BARBELL -> barbell(n)
            Equipment.DUMBBELL -> dumbbell(n)
            Equipment.MACHINE -> machine(n)
            Equipment.CABLE -> cable(n)
            Equipment.BODYWEIGHT -> bodyweight(n)
            else -> emptyList()
        }
    }

    private fun barbell(n: String): List<Set<EquipmentItem>> = when {
        "bench press" in n && "incline" in n -> listOf(BARBELL, PLATES, any(EquipmentItem.ADJUSTABLE_BENCH))
        "bench press" in n && "decline" in n -> listOf(BARBELL, PLATES, any(EquipmentItem.DECLINE_BENCH))
        "bench press" in n -> listOf(BARBELL, PLATES, BENCH)
        "squat" in n -> listOf(BARBELL, PLATES, RACK)
        "hip thrust" in n -> listOf(BARBELL, PLATES, BENCH)
        "skull crusher" in n || "lying triceps" in n -> listOf(BAR_ANY - EquipmentItem.TRAP_BAR, PLATES, BENCH)
        "curl" in n -> listOf(BAR_ANY - EquipmentItem.TRAP_BAR, PLATES)
        "ez" in n.split(' ', '-') -> listOf(any(EquipmentItem.EZ_BAR), PLATES)
        else -> listOf(BARBELL, PLATES)
    }

    private fun dumbbell(n: String): List<Set<EquipmentItem>> = when {
        "incline" in n -> listOf(DUMBBELLS, any(EquipmentItem.ADJUSTABLE_BENCH))
        "bench press" in n || ("fly" in n && "rear" !in n) || "pullover" in n -> listOf(DUMBBELLS, BENCH)
        "farmer" in n || "swing" in n -> listOf(DUMBBELLS + EquipmentItem.KETTLEBELLS)
        else -> listOf(DUMBBELLS)
    }

    private fun machine(n: String): List<Set<EquipmentItem>> = when {
        "leg press" in n -> listOf(any(EquipmentItem.LEG_PRESS))
        "hack squat" in n -> listOf(any(EquipmentItem.HACK_SQUAT))
        "leg extension" in n -> listOf(any(EquipmentItem.LEG_EXTENSION))
        "leg curl" in n -> listOf(any(EquipmentItem.LEG_CURL))
        "chest press" in n -> listOf(any(EquipmentItem.CHEST_PRESS_MACHINE))
        "pec deck" in n || "reverse pec" in n || n.endsWith(" fly") -> listOf(any(EquipmentItem.PEC_DECK))
        "shoulder press" in n || "lateral raise" in n -> listOf(any(EquipmentItem.SHOULDER_PRESS_MACHINE))
        "calf raise" in n -> listOf(any(EquipmentItem.CALF_RAISE))
        "hip thrust" in n -> listOf(any(EquipmentItem.HIP_THRUST_MACHINE))
        "assisted" in n -> listOf(any(EquipmentItem.ASSISTED_PULL_UP))
        "abduction" in n || "adduction" in n -> listOf(any(EquipmentItem.HIP_ABDUCTION_MACHINE))
        "row" in n -> listOf(any(EquipmentItem.SEATED_ROW))
        "preacher" in n -> listOf(any(EquipmentItem.PREACHER_BENCH))
        else -> listOf(ANY_MACHINE)
    }

    private fun cable(n: String): List<Set<EquipmentItem>> = when {
        "pulldown" in n && "straight" !in n && "triceps" !in n -> listOf(any(EquipmentItem.LAT_PULLDOWN, EquipmentItem.CABLE_STACK))
        "seated" in n && "row" in n -> listOf(any(EquipmentItem.SEATED_ROW, EquipmentItem.CABLE_STACK))
        else -> listOf(any(EquipmentItem.CABLE_STACK))
    }

    private fun bodyweight(n: String): List<Set<EquipmentItem>> = when {
        "assisted" in n -> listOf(any(EquipmentItem.ASSISTED_PULL_UP))
        "pull-up" in n || "pullup" in n || "chin-up" in n || "chinup" in n || "hanging" in n ->
            listOf(any(EquipmentItem.PULL_UP_BAR, EquipmentItem.POWER_RACK))
        "chest dip" in n || n == "dip" || n == "dips" || "parallel bar" in n -> listOf(any(EquipmentItem.DIP_STATION))
        "inverted row" in n -> listOf(any(EquipmentItem.SMITH_MACHINE, EquipmentItem.POWER_RACK, EquipmentItem.SUSPENSION_TRAINER, EquipmentItem.PULL_UP_BAR))
        else -> emptyList()
    }

    /** Whether [owned] (item ids) covers everything the exercise needs. */
    fun canDo(name: String, kind: String, owned: Set<String>): Boolean =
        forExercise(name, kind).all { group -> group.any { it.id in owned } }

    /** What is missing, one entry per unmet group ("Pull-up bar or Power rack"), for "needs ..." text. */
    fun missing(name: String, kind: String, owned: Set<String>): List<String> =
        forExercise(name, kind).filter { group -> group.none { it.id in owned } }
            .map { group -> group.joinToString(" or ") { it.label } }
}
