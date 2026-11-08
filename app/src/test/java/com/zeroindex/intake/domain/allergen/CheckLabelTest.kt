package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.rank
import com.zeroindex.intake.data.db.entity.worseOf
import com.zeroindex.intake.domain.model.AllergenMatchKind
import com.zeroindex.intake.domain.plan.PlanWatchTerms
import org.junit.Test

/** The soft "Check the label" tier (docs/ALLERGENS.md). */
class CheckLabelTest {

    private val dairyAliases = listOf(
        "milk", "butter", "cream", "cheese", "whey", "casein", "caseinate",
        "sodium caseinate", "lactose", "ghee", "curds", "milkfat", "buttermilk",
        "custard", "half and half", "condensed milk", "evaporated milk",
        "nonfat dry milk", "milk solids", "lactalbumin", "lactoglobulin",
        "rennet casein", "dairy",
    ).map { AllergenAlias("dairy", it) }

    private fun scan(
        name: String? = null,
        ingredients: String? = null,
        contains: List<String> = emptyList(),
        avoided: Set<String> = setOf("dairy"),
        inIngredients: Boolean = false,
        extra: List<CheckLabelTerm> = emptyList(),
    ) = AllergenMatcher.scan(
        aliases = dairyAliases,
        avoidedAllergens = avoided,
        name = name,
        ingredients = ingredients,
        structuredContains = contains,
        checkLabelTerms = HiddenCarriers.DAIRY_TERMS + extra,
        checkLabelInIngredients = inIngredients,
    )

    private fun assertCheckLabel(name: String) {
        val r = scan(name = name)
        assertThat(r.checkLabel).isNotEmpty()
        assertThat(r.hasAnything).isFalse()
        assertThat(r.blocks).isFalse()
        assertThat(r.hasCheckLabel).isTrue()
        assertThat(r.checkLabel.first().note).isNotEmpty()
    }

    private fun assertQuiet(name: String) {
        val r = scan(name = name)
        assertThat(r.matches).isEmpty()
    }

    // ---- the foods named in the user's hidden-dairy checklist ----

    @Test fun `taco bell seasoning is check the label`() = assertCheckLabel("Taco Bell mild seasoning")
    @Test fun `taco seasoning packet`() = assertCheckLabel("Taco seasoning packet")
    @Test fun `ranch seasoning`() = assertCheckLabel("Ranch seasoning")
    @Test fun `seasoning packets`() = assertCheckLabel("Seasoning packets")
    @Test fun `flour tortillas`() = assertCheckLabel("Flour tortillas")
    @Test fun `single tortilla`() = assertCheckLabel("Whole wheat tortilla")
    @Test fun `hoagie rolls`() = assertCheckLabel("Hoagie rolls")
    @Test fun `hot dog and hamburger rolls`() {
        assertCheckLabel("hot dog rolls")
        assertCheckLabel("Hamburger roll")
    }
    @Test fun `dinner roll`() = assertCheckLabel("Dinner rolls, 12 ct")
    @Test fun `buns`() {
        assertCheckLabel("Hamburger buns")
        assertCheckLabel("Hot dog bun")
    }
    @Test fun `breadcrumbs and panko`() {
        assertCheckLabel("Breadcrumbs")
        assertCheckLabel("Bread crumbs, Italian")
        assertCheckLabel("Panko")
    }
    @Test fun `crackers and saltines`() {
        assertCheckLabel("Saltines")
        assertCheckLabel("Wheat crackers")
        assertCheckLabel("Saltine cracker")
    }
    @Test fun `fritos and corn chips`() {
        assertCheckLabel("Fritos Original")
        assertCheckLabel("Corn chips")
    }
    @Test fun `natural flavoring`() {
        assertCheckLabel("Pre-seasoned chicken with natural flavoring")
        assertCheckLabel("Steak, natural flavors")
    }
    @Test fun `margarine and creamer`() {
        assertCheckLabel("Margarine spread")
        assertCheckLabel("Coffee creamer")
    }
    @Test fun `instant mashed potatoes and boxed rice`() {
        assertCheckLabel("Instant mashed potatoes")
        assertCheckLabel("Boxed rice pilaf mix")
        assertCheckLabel("Rice sides, chicken flavor")
    }
    @Test fun `caramel nougat chocolate and protein bars`() {
        assertCheckLabel("Salted caramel")
        assertCheckLabel("Nougat bar")
        assertCheckLabel("Dark chocolate")
        assertCheckLabel("Protein bar crunch")
    }
    @Test fun `brioche and croissant`() {
        assertCheckLabel("Brioche loaf")
        assertCheckLabel("Croissants")
    }

    // ---- false-positive guards ----

    @Test fun `egg roll is not flagged`() = assertQuiet("Egg roll")
    @Test fun `spring roll and sushi roll are not flagged`() {
        assertQuiet("Spring rolls")
        assertQuiet("California sushi roll")
    }
    @Test fun `rolled oats are not flagged`() = assertQuiet("Rolled oats")
    @Test fun `bunch is not a bun`() = assertQuiet("A bunch of bananas")
    @Test fun `caramelized onions are not caramel`() = assertQuiet("Caramelized onions")
    @Test fun `plain seasoning is not flagged`() {
        assertQuiet("Italian seasoning")
        assertQuiet("Everything bagel seasoning")
    }
    @Test fun `cracker substring words are not flagged`() = assertQuiet("Trail Crackerjack")
    @Test fun `chocolatey is not chocolate`() = assertQuiet("Chocolatey")

    // ---- tier behaviour ----

    @Test fun `ingredient text is only scanned for the soft tier when asked`() {
        assertThat(scan(name = "Burrito kit", ingredients = "flour tortillas, rice").matches).isEmpty()
        val r = scan(name = "Burrito kit", ingredients = "flour tortillas, rice", inIngredients = true)
        assertThat(r.checkLabel.single().field).isEqualTo(MatchField.INGREDIENTS)
    }

    @Test fun `not avoiding dairy gives no soft hit`() {
        assertThat(scan(name = "Flour tortillas", avoided = setOf("peanut")).matches).isEmpty()
        assertThat(scan(name = "Flour tortillas", avoided = emptySet()).matches).isEmpty()
    }

    @Test fun `the note is the human reason`() {
        val r = scan(name = "Hoagie rolls")
        assertThat(r.checkLabel.single().note).isEqualTo("Bread rolls and buns often contain milk or butter")
    }

    // ---- never-clear ----

    @Test fun `contains is never downgraded by a carrier`() {
        val r = scan(name = "Cheese tortillas")
        assertThat(r.blocks).isTrue()
        assertThat(r.checkLabel).isEmpty()
        assertThat(r.matches.all { it.kind == AllergenMatchKind.CONTAINS }).isTrue()
    }

    @Test fun `milk chocolate stays a hard match`() {
        val r = scan(name = "Milk chocolate")
        assertThat(r.blocks).isTrue()
        assertThat(r.checkLabel).isEmpty()
    }

    @Test fun `may contain is never downgraded by a carrier`() {
        val r = scan(name = "Oat milk tortillas")
        assertThat(r.blocks).isFalse()
        assertThat(r.mayContain).isNotEmpty()
        assertThat(r.checkLabel).isEmpty()
    }

    @Test fun `non-dairy creamer stays at least may contain`() {
        val r = scan(name = "Non-dairy creamer")
        assertThat(r.mayContain).isNotEmpty()
        assertThat(r.checkLabel).isEmpty()
    }

    @Test fun `open food facts milk tag still blocks a carrier`() {
        val r = scan(name = "Flour tortillas", contains = listOf("en:milk"))
        assertThat(r.blocks).isTrue()
        assertThat(r.checkLabel).isEmpty()
    }

    @Test fun `tags without milk still allow a name match`() {
        val r = scan(name = "Flour tortillas", contains = listOf("en:gluten"))
        assertThat(r.blocks).isFalse()
        assertThat(r.hasAnything).isFalse()
        assertThat(r.checkLabel).hasSize(1)
    }

    @Test fun `ingredient dairy beats a name carrier`() {
        val r = scan(name = "Crackers", ingredients = "wheat flour, whey, salt")
        assertThat(r.blocks).isTrue()
        assertThat(r.checkLabel).isEmpty()
    }

    @Test fun `model policy keeps a soft hit and still raises a claim`() {
        val soft = scan(name = "Flour tortillas")
        val verdict = ModelAllergenPolicy.merge(
            local = listOf(soft),
            claims = listOf(ModelAllergenClaim("Flour tortillas", listOf("dairy"), "often has milk")),
            avoided = setOf("dairy"),
        )
        assertThat(verdict.perItem[0]).isEqualTo(AllergenMatchKind.MAY_CONTAIN)
        assertThat(verdict.scan.mayContain).isNotEmpty()
    }

    @Test fun `model policy marks a soft only item as check the label`() {
        val verdict = ModelAllergenPolicy.merge(
            local = listOf(scan(name = "Flour tortillas")),
            claims = emptyList(),
            avoided = setOf("dairy"),
        )
        assertThat(verdict.perItem[0]).isEqualTo(AllergenMatchKind.CHECK_LABEL)
        assertThat(verdict.scan.hasAnything).isFalse()
    }

    @Test fun `model policy never downgrades a contains with a later soft item`() {
        val hard = scan(name = "Cheese")
        val verdict = ModelAllergenPolicy.merge(
            local = listOf(hard),
            claims = emptyList(),
            avoided = setOf("dairy"),
            extraLocal = listOf(scan(name = "Flour tortillas")),
        )
        assertThat(verdict.perItem[0]).isEqualTo(AllergenMatchKind.CONTAINS)
        assertThat(verdict.scan.blocks).isTrue()
    }

    // ---- ordering ----

    @Test fun `kinds are ordered`() {
        assertThat(AllergenMatchKind.CONTAINS.rank).isGreaterThan(AllergenMatchKind.MAY_CONTAIN.rank)
        assertThat(AllergenMatchKind.MAY_CONTAIN.rank).isGreaterThan(AllergenMatchKind.CHECK_LABEL.rank)
    }

    @Test fun `statuses are ordered`() {
        val order = listOf(
            AllergenStatus.CONTAINS, AllergenStatus.MAY_CONTAIN, AllergenStatus.CHECK_LABEL,
            AllergenStatus.CLEAR, AllergenStatus.UNKNOWN,
        )
        assertThat(order.map { it.rank }).isInOrder(Comparator.reverseOrder<Int>())
        assertThat(null.rank).isEqualTo(AllergenStatus.UNKNOWN.rank)
    }

    @Test fun `worse of picks the more serious verdict`() {
        assertThat(worseOf(AllergenStatus.CHECK_LABEL, AllergenStatus.CLEAR)).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(worseOf(AllergenStatus.CLEAR, AllergenStatus.CHECK_LABEL)).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(worseOf(AllergenStatus.MAY_CONTAIN, AllergenStatus.CHECK_LABEL)).isEqualTo(AllergenStatus.MAY_CONTAIN)
        assertThat(worseOf(AllergenStatus.CHECK_LABEL, AllergenStatus.CONTAINS)).isEqualTo(AllergenStatus.CONTAINS)
        assertThat(worseOf(AllergenStatus.CONTAINS, AllergenStatus.CHECK_LABEL)).isEqualTo(AllergenStatus.CONTAINS)
        assertThat(worseOf(null, AllergenStatus.UNKNOWN)).isNull()
    }

    @Test fun `stored statuses decode by name and gain no migration`() {
        assertThat(AllergenStatus.valueOf("CHECK_LABEL")).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(AllergenStatus.valueOf("MAY_CONTAIN")).isEqualTo(AllergenStatus.MAY_CONTAIN)
    }

    // ---- plan "watch for" integration ----

    @Test fun `quoted phrases in the plan checklist become terms`() {
        val terms = PlanWatchTerms.extract(
            listOf(
                "Anything labeled \"natural flavoring\" on pre-seasoned meat",
                "\"Non-dairy\" creamer, which can still contain sodium caseinate",
                "Flour tortillas and hamburger, hot dog and hoagie rolls",
                "Look for “malted milk powder” in shakes",
            ),
        )
        assertThat(terms.map { it.phrase }).containsExactly("natural flavoring", "malted milk powder")
        assertThat(terms.first().reason).contains("watch-for")
    }

    @Test fun `unquoted checklist lines are not guessed at`() {
        val terms = PlanWatchTerms.extract(listOf("Seasoning packets", "Flour tortillas", "Restaurant steak"))
        assertThat(terms).isEmpty()
    }

    @Test fun `an extracted plan term flags foods and reaches ingredients`() {
        val extra = PlanWatchTerms.extract(listOf("Watch the \"onion dip mix\" at parties"))
        val r = scan(name = "Onion dip mix, 1 oz", extra = extra)
        assertThat(r.checkLabel.single().note).contains("watch-for")
        assertThat(scan(name = "Onion dip mix", extra = emptyList()).matches).isEmpty()
    }

    @Test fun `a plan term cannot downgrade a stronger hit`() {
        val extra = PlanWatchTerms.extract(listOf("Watch the \"cheese sauce\" at the counter"))
        val r = scan(name = "Cheese sauce", extra = extra)
        assertThat(r.blocks).isTrue()
        assertThat(r.checkLabel).isEmpty()
    }

    @Test fun `hit lookup for list rows`() {
        assertThat(HiddenCarriers.firstHit(HiddenCarriers.DAIRY_TERMS, "Flour tortillas")).isNotNull()
        assertThat(HiddenCarriers.firstHit(HiddenCarriers.DAIRY_TERMS, "Chicken breast")).isNull()
        assertThat(HiddenCarriers.firstHit(HiddenCarriers.DAIRY_TERMS, null)).isNull()
    }
}
