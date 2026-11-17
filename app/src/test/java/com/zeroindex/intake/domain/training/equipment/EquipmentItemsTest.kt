package com.zeroindex.intake.domain.training.equipment

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.goals.UserProfile
import com.zeroindex.intake.domain.training.SwapCandidate
import com.zeroindex.intake.domain.training.SwapRanking
import com.zeroindex.intake.domain.training.generator.Equipment
import com.zeroindex.intake.domain.training.generator.EquipmentPreset
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import com.zeroindex.intake.domain.training.generator.GeneratorInputs
import com.zeroindex.intake.domain.training.generator.generateProgram
import org.junit.Test

class EquipmentItemsTest {

    private fun ids(vararg i: EquipmentItem) = i.map { it.id }.toSet()

    @Test
    fun `the checklist has the four groups and every named item`() {
        assertThat(EquipmentGroup.entries.map { it.label }).containsExactly("Free weights", "Racks and benches", "Machines", "Other").inOrder()
        for (g in EquipmentGroup.entries) assertThat(EquipmentItems.inGroup(g)).isNotEmpty()
        assertThat(EquipmentItems.ALL.map { it.id }).containsNoDuplicates()
        val labels = EquipmentItems.ALL.map { it.label }
        assertThat(labels).containsAtLeast(
            "Barbell (Olympic)", "EZ bar", "Trap bar", "Dumbbells (fixed)", "Dumbbells (adjustable)", "Kettlebells", "Weight plates",
            "Power rack", "Squat stand", "Smith machine", "Flat bench", "Adjustable bench", "Decline bench", "Preacher bench", "Dip station", "Pull-up bar",
            "Lat pulldown", "Seated row", "Leg press", "Hack squat", "Leg extension", "Leg curl", "Chest press", "Pec deck", "Shoulder press",
            "Calf raise", "Hip thrust", "Assisted pull-up",
            "Resistance bands", "Suspension trainer", "Medicine ball", "Landmine", "Sled", "Rower", "Bike", "Treadmill", "Jump rope", "Foam roller",
        )
    }

    @Test
    fun `quick picks fill the list and match back to their preset`() {
        assertThat(EquipmentItems.presetItems(EquipmentPreset.FULL_GYM)).isEqualTo(EquipmentItems.ALL_IDS)
        for (p in listOf(EquipmentPreset.FULL_GYM, EquipmentPreset.HOME_DUMBBELLS, EquipmentPreset.BODYWEIGHT_ONLY)) {
            assertThat(EquipmentItems.presetFor(EquipmentItems.presetItems(p))).isEqualTo(p)
            assertThat(EquipmentItems.kindsOf(EquipmentItems.presetItems(p))).isEqualTo(p.kinds)
        }
        assertThat(EquipmentItems.presetFor(ids(EquipmentItem.KETTLEBELLS))).isEqualTo(EquipmentPreset.CUSTOM)
    }

    @Test
    fun `old kinds migrate to items and lose nothing`() {
        val gym = EquipmentItems.fromLegacy(Equipment.FULL_GYM, EquipmentPreset.FULL_GYM)
        assertThat(gym).isEqualTo(EquipmentItems.ALL_IDS)
        val custom = EquipmentItems.fromLegacy(setOf(Equipment.BARBELL, Equipment.DUMBBELL), EquipmentPreset.CUSTOM)
        assertThat(custom).containsAtLeast(
            EquipmentItem.BARBELL_OLYMPIC.id, EquipmentItem.PLATES.id, EquipmentItem.POWER_RACK.id,
            EquipmentItem.FLAT_BENCH.id, EquipmentItem.DUMBBELLS_FIXED.id,
        )
        assertThat(custom).doesNotContain(EquipmentItem.LEG_PRESS.id)
        // Everything the old kind rule allowed is still allowed after the move.
        for (kinds in listOf(setOf(Equipment.BARBELL), setOf(Equipment.DUMBBELL), setOf(Equipment.MACHINE), setOf(Equipment.CABLE), setOf(Equipment.BODYWEIGHT))) {
            val items = EquipmentItems.fromLegacy(kinds, EquipmentPreset.CUSTOM)
            for (e in ExerciseCatalog.ALL.filter { it.equipment in kinds }) {
                assertThat(EquipmentRequirements.canDo(e.name, e.equipment, items)).isTrue()
            }
        }
        assertThat(EquipmentItems.kindsOf(custom)).containsExactly(Equipment.BODYWEIGHT, Equipment.BARBELL, Equipment.DUMBBELL)
    }

    @Test
    fun `a profile saved before the checklist reads as items, and ticking items keeps the kinds in step`() {
        val old = UserProfile(equipment = Equipment.HOME_DUMBBELLS, equipmentPreset = EquipmentPreset.HOME_DUMBBELLS)
        assertThat(old.equipmentItems).isNull()
        assertThat(old.effectiveEquipmentItems).isEqualTo(EquipmentItems.presetItems(EquipmentPreset.HOME_DUMBBELLS))
        val ticked = old.withEquipmentItems(ids(EquipmentItem.LEG_PRESS, EquipmentItem.CABLE_STACK))
        assertThat(ticked.equipmentPreset).isEqualTo(EquipmentPreset.CUSTOM)
        assertThat(ticked.equipment).containsExactly(Equipment.BODYWEIGHT, Equipment.MACHINE, Equipment.CABLE)
        assertThat(ticked.withEquipmentPreset(EquipmentPreset.FULL_GYM).effectiveEquipmentItems).isEqualTo(EquipmentItems.ALL_IDS)
        assertThat(old.withEquipmentItems(setOf("not_an_item")).effectiveEquipmentItems).isEmpty()
    }

    @Test
    fun `requirements name the specific items`() {
        fun can(name: String, kind: String, vararg have: EquipmentItem) = EquipmentRequirements.canDo(name, kind, have.map { it.id }.toSet())
        // barbell lifts need a bar and plates
        assertThat(can("Deadlift", Equipment.BARBELL, EquipmentItem.BARBELL_STANDARD, EquipmentItem.PLATES)).isTrue()
        assertThat(can("Deadlift", Equipment.BARBELL, EquipmentItem.BARBELL_OLYMPIC)).isFalse()
        // bench press needs a bench; incline needs an adjustable one; squat needs a rack
        assertThat(can("Bench press", Equipment.BARBELL, EquipmentItem.BARBELL_OLYMPIC, EquipmentItem.PLATES)).isFalse()
        assertThat(can("Bench press", Equipment.BARBELL, EquipmentItem.BARBELL_OLYMPIC, EquipmentItem.PLATES, EquipmentItem.FLAT_BENCH)).isTrue()
        assertThat(can("Incline bench press", Equipment.BARBELL, EquipmentItem.BARBELL_OLYMPIC, EquipmentItem.PLATES, EquipmentItem.FLAT_BENCH)).isFalse()
        assertThat(can("Incline bench press", Equipment.BARBELL, EquipmentItem.BARBELL_OLYMPIC, EquipmentItem.PLATES, EquipmentItem.ADJUSTABLE_BENCH)).isTrue()
        assertThat(can("Back squat", Equipment.BARBELL, EquipmentItem.BARBELL_OLYMPIC, EquipmentItem.PLATES)).isFalse()
        assertThat(can("Back squat", Equipment.BARBELL, EquipmentItem.BARBELL_OLYMPIC, EquipmentItem.PLATES, EquipmentItem.SQUAT_STAND)).isTrue()
        assertThat(can("Skull crusher", Equipment.BARBELL, EquipmentItem.EZ_BAR, EquipmentItem.PLATES, EquipmentItem.FLAT_BENCH)).isTrue()
        // dumbbells, fixed or adjustable
        assertThat(can("Hammer curl", Equipment.DUMBBELL, EquipmentItem.DUMBBELLS_ADJUSTABLE)).isTrue()
        assertThat(can("Hammer curl", Equipment.DUMBBELL)).isFalse()
        assertThat(can("Incline dumbbell press", Equipment.DUMBBELL, EquipmentItem.DUMBBELLS_FIXED, EquipmentItem.FLAT_BENCH)).isFalse()
        // machines are exact
        assertThat(can("Leg press", Equipment.MACHINE, EquipmentItem.LEG_PRESS)).isTrue()
        assertThat(can("Leg press", Equipment.MACHINE, EquipmentItem.HACK_SQUAT)).isFalse()
        assertThat(can("Pec deck", Equipment.MACHINE, EquipmentItem.PEC_DECK)).isTrue()
        assertThat(can("Calf raise", Equipment.MACHINE, EquipmentItem.CALF_RAISE)).isTrue()
        assertThat(can("Preacher curl", Equipment.MACHINE, EquipmentItem.PREACHER_BENCH)).isTrue()
        // cable work: a pulldown can use the stack or the dedicated machine
        assertThat(can("Lat pulldown", Equipment.CABLE, EquipmentItem.LAT_PULLDOWN)).isTrue()
        assertThat(can("Lat pulldown", Equipment.CABLE, EquipmentItem.CABLE_STACK)).isTrue()
        assertThat(can("Face pull", Equipment.CABLE, EquipmentItem.LAT_PULLDOWN)).isFalse()
        // bodyweight
        assertThat(can("Pull-up", Equipment.BODYWEIGHT)).isFalse()
        assertThat(can("Pull-up", Equipment.BODYWEIGHT, EquipmentItem.PULL_UP_BAR)).isTrue()
        assertThat(can("Chest dip", Equipment.BODYWEIGHT, EquipmentItem.DIP_STATION)).isTrue()
        assertThat(can("Push-up", Equipment.BODYWEIGHT)).isTrue()
        // named gear and unknown kinds
        assertThat(can("Kettlebell swing", Equipment.DUMBBELL, EquipmentItem.KETTLEBELLS)).isTrue()
        assertThat(can("Resistance band pull-apart", "", EquipmentItem.RESISTANCE_BANDS)).isTrue()
        assertThat(can("Sled push", "", EquipmentItem.SLED)).isTrue()
        assertThat(can("Mystery move", "")).isTrue()
    }

    @Test
    fun `missing lists what is not owned`() {
        assertThat(EquipmentRequirements.missing("Bench press", Equipment.BARBELL, ids(EquipmentItem.BARBELL_OLYMPIC, EquipmentItem.PLATES)))
            .containsExactly("Flat bench or Adjustable bench")
    }

    @Test
    fun `every catalog exercise is doable with the full gym and bodyweight ones with nothing but a bar`() {
        for (e in ExerciseCatalog.ALL) assertThat(EquipmentRequirements.canDo(e.name, e.equipment, EquipmentItems.ALL_IDS)).isTrue()
        val none = emptySet<String>()
        for (e in ExerciseCatalog.ALL.filter { it.equipment != Equipment.BODYWEIGHT }) assertThat(EquipmentRequirements.canDo(e.name, e.equipment, none)).isFalse()
    }

    @Test
    fun `the generator only picks lifts the ticked items allow`() {
        val items = ids(EquipmentItem.DUMBBELLS_ADJUSTABLE, EquipmentItem.ADJUSTABLE_BENCH, EquipmentItem.LEG_PRESS, EquipmentItem.PULL_UP_BAR)
        val p = generateProgram(GeneratorInputs(equipment = EquipmentItems.kindsOf(items), equipmentItems = items, daysPerWeek = 4))
        assertThat(p.days.flatMap { it.exercises }).isNotEmpty()
        for (e in p.days.flatMap { it.exercises }) {
            val c = ExerciseCatalog.byId(e.exerciseId)!!
            assertThat(EquipmentRequirements.canDo(c.name, c.equipment, items)).isTrue()
        }
    }

    @Test
    fun `swap ranking puts exercises you can do first, by items`() {
        val current = SwapCandidate("a", "Leg press", "quads", emptyList(), Equipment.MACHINE)
        val hack = SwapCandidate("b", "Hack squat", "quads", emptyList(), Equipment.MACHINE)
        val goblet = SwapCandidate("c", "Goblet squat", "quads", emptyList(), Equipment.DUMBBELL)
        val items = ids(EquipmentItem.DUMBBELLS_FIXED)
        val ranked = SwapRanking.rank(current, listOf(hack, goblet), owned = Equipment.FULL_GYM, ownedItems = items)
        assertThat(ranked.map { it.candidate.id }).containsExactly("c", "b").inOrder()
        assertThat(ranked.first().ownsEquipment).isTrue()
        assertThat(ranked.last().ownsEquipment).isFalse()
        // with no item list the old kinds still decide
        val legacy = SwapRanking.rank(current, listOf(hack, goblet), owned = setOf(Equipment.MACHINE))
        assertThat(legacy.first().candidate.id).isEqualTo("b")
    }
}
