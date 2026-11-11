package com.zeroindex.intake.data.recipe

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.recipe.IngredientDraft
import com.zeroindex.intake.domain.recipe.RecipeEditing
import org.junit.Test

class RecipeUrlTest {
    private fun ok(s: String) = (RecipeUrl.check(s) as RecipeUrl.Checked.Ok).url.toString()
    private fun bad(s: String) = (RecipeUrl.check(s) as RecipeUrl.Checked.Bad).message

    @Test fun `https is accepted and a bare host gets https`() {
        assertThat(ok("https://example.com/recipe?id=3")).isEqualTo("https://example.com/recipe?id=3")
        assertThat(ok("  example.com/lemon-chicken ")).isEqualTo("https://example.com/lemon-chicken")
    }

    @Test fun `plain http only for local hosts`() {
        assertThat(bad("http://example.com/r")).contains("https")
        assertThat(ok("http://localhost:8080/r")).startsWith("http://localhost")
        assertThat(ok("http://192.168.1.20/r")).startsWith("http://192.168.1.20")
        assertThat(ok("http://10.0.0.5/r")).isNotEmpty()
        assertThat(ok("http://172.20.1.1/r")).isNotEmpty()
        assertThat(ok("http://pi.local/r")).isNotEmpty()
        assertThat(bad("http://172.40.1.1/r")).isNotEmpty()
        assertThat(bad("http://8.8.8.8/r")).isNotEmpty()
        assertThat(bad("http://example.com.local.evil.com/r")).isNotEmpty()
    }

    @Test fun `other schemes and junk are refused`() {
        assertThat(bad("file:///etc/passwd")).isNotEmpty()
        assertThat(bad("ftp://example.com/r")).isNotEmpty()
        assertThat(bad("javascript:alert(1)")).isNotEmpty()
        assertThat(bad("")).isNotEmpty()
        assertThat(bad("two words")).isNotEmpty()
    }

    @Test fun `the user agent names the app and the purpose and nothing else`() {
        assertThat(RecipeFetcher.USER_AGENT).startsWith("Intake/")
        assertThat(RecipeFetcher.USER_AGENT).endsWith("(recipe import)")
    }

    @Test fun `the size cap is 5 MB`() {
        assertThat(RecipeFetcher.MAX_BYTES).isEqualTo(5L * 1024 * 1024)
    }

    @Test fun `utm, fbclid and gclid are stripped and other parameters stay in order`() {
        assertThat(ok("https://www.eatwell101.com/tomato-zucchini-pasta-recipe?utm_source=Pinterest&utm_medium=organic"))
            .isEqualTo("https://www.eatwell101.com/tomato-zucchini-pasta-recipe")
        assertThat(ok("https://example.com/r?id=3&UTM_Campaign=x&fbclid=abc&page=2&gclid=g1"))
            .isEqualTo("https://example.com/r?id=3&page=2")
        assertThat(ok("https://example.com/r?fbclid=abc")).isEqualTo("https://example.com/r")
    }

    @Test fun `a parameter that only looks like tracking is kept`() {
        assertThat(RecipeUrl.isTrackingParam("utm")).isFalse()
        assertThat(RecipeUrl.isTrackingParam("my_utm_x")).isFalse()
        assertThat(ok("https://example.com/r?utm=1&fbclid_old=2")).isEqualTo("https://example.com/r?utm=1&fbclid_old=2")
    }
}

class RecipeEditingTest {
    private fun ing(name: String, grams: Double?, k: Double? = null, p: Double? = null, c: Double? = null, f: Double? = null) =
        IngredientDraft(key = name, name = name, grams = grams, kcalPer100g = k, proteinPer100g = p, carbsPer100g = c, fatPer100g = f)

    @Test fun `live macros count only complete ingredients and name the rest`() {
        val m = RecipeEditing.macros(
            listOf(ing("rice", 200.0, 130.0, 2.7, 28.0, 0.3), ing("chicken", 400.0, 165.0, 31.0, 0.0, 3.6), ing("salt", null), ing("oil", 14.0, 884.0)),
            servings = 2,
        )
        assertThat(m.knownCount).isEqualTo(2)
        assertThat(m.isComplete).isFalse()
        assertThat(m.missing).containsExactly("salt", "14 g oil").inOrder()
        assertThat(m.total.kcal).isWithin(0.01).of(260.0 + 660.0)
        assertThat(m.perServing.kcal).isWithin(0.01).of(460.0)
        assertThat(m.perServing.protein).isWithin(0.01).of((5.4 + 124.0) / 2)
    }

    @Test fun `complete when every ingredient has data`() {
        val m = RecipeEditing.macros(listOf(ing("rice", 100.0, 130.0, 2.7, 28.0, 0.3)), 1)
        assertThat(m.isComplete).isTrue()
    }

    @Test fun `no ingredients is empty and zero servings count as one`() {
        assertThat(RecipeEditing.macros(emptyList(), 0).isEmpty).isTrue()
        assertThat(RecipeEditing.macros(listOf(ing("x", 100.0, 100.0, 0.0, 0.0, 0.0)), 0).perServing.kcal).isWithin(0.01).of(100.0)
    }

    @Test fun `blank names are ignored`() {
        assertThat(RecipeEditing.macros(listOf(ing("", 100.0, 100.0, 0.0, 0.0, 0.0)), 1).isEmpty).isTrue()
    }

    @Test fun `steps and lines`() {
        assertThat(RecipeEditing.stepsFromText("1. Mix\n\nStep 2: Bake\nServe")).containsExactly("Mix", "Bake", "Serve").inOrder()
        assertThat(RecipeEditing.ingredientLines(" a \n\n b")).containsExactly("a", "b").inOrder()
        val d = RecipeEditing.draftFromLine("k", "200 g chicken")
        assertThat(d.grams).isEqualTo(200.0)
        assertThat(d.name).isEqualTo("chicken")
        assertThat(d.line).isEqualTo("200 g chicken")
    }
}
