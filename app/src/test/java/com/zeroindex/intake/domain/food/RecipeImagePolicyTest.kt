package com.zeroindex.intake.domain.food

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.food.ProductImagePolicy.Decision
import com.zeroindex.intake.domain.food.ProductImagePolicy.Source
import org.junit.Test

/** The recipe-photo rule beside the Open Food Facts one (see ProductImagePolicyTest, which must keep passing). */
class RecipeImagePolicyTest {

    @Test fun `a recipe photo may come from any public https host`() {
        listOf(
            "https://bellyfull.net/wp-content/uploads/2021/02/Chicken-Alfredo.jpg",
            "https://i0.wp.com/example.test/a.jpg?resize=600",
            "https://cdn.some-cdn.example/photos/pasta.webp",
        ).forEach {
            assertThat(ProductImagePolicy.decide(it, offEnabled = false, online = true, source = Source.RECIPE_PAGE))
                .isEqualTo(Decision.FETCH_OK)
        }
    }

    @Test fun `a recipe photo does not depend on the Open Food Facts switch, but offline it is cache only`() {
        val url = "https://example.test/a.jpg"
        assertThat(ProductImagePolicy.decide(url, offEnabled = false, online = false, source = Source.RECIPE_PAGE))
            .isEqualTo(Decision.CACHE_ONLY)
        assertThat(ProductImagePolicy.decide(url, offEnabled = true, online = true, source = Source.RECIPE_PAGE))
            .isEqualTo(Decision.FETCH_OK)
    }

    @Test fun `plain http, credentials, odd ports and addresses inside the network are refused`() {
        listOf(
            null,
            "",
            "http://example.test/a.jpg",
            "ftp://example.test/a.jpg",
            "https://user:pw@example.test/a.jpg",
            "https://example.test@evil.test/a.jpg".replace("evil.test", "localhost"),
            "https://example.test:8443/a.jpg",
            "https://localhost/a.jpg",
            "https://127.0.0.1/a.jpg",
            "https://192.168.1.20/a.jpg",
            "https://10.0.0.5/a.jpg",
            "https://[::1]/a.jpg",
            "https://printer.local/a.jpg",
            "https://nas.lan/a.jpg",
            "https://intranet/a.jpg",
            "https://exa mple.test/a.jpg",
            "not a url",
        ).forEach {
            assertThat(ProductImagePolicy.decide(it, offEnabled = true, online = true, source = Source.RECIPE_PAGE))
                .isEqualTo(Decision.SKIP)
        }
    }

    @Test fun `Open Food Facts images are unchanged and an arbitrary host is still refused for them`() {
        assertThat(ProductImagePolicy.decide("https://images.openfoodfacts.org/a.jpg", true, true)).isEqualTo(Decision.FETCH_OK)
        assertThat(ProductImagePolicy.decide("https://example.test/a.jpg", true, true)).isEqualTo(Decision.SKIP)
        assertThat(ProductImagePolicy.decide("https://example.test/a.jpg", true, true, Source.OPEN_FOOD_FACTS)).isEqualTo(Decision.SKIP)
        assertThat(ProductImagePolicy.decide("https://images.openfoodfacts.org/a.jpg", false, true)).isEqualTo(Decision.SKIP)
    }

    @Test fun `recipe photos have a bigger cap than product thumbnails, and both are capped`() {
        assertThat(ProductImagePolicy.maxBytes(Source.OPEN_FOOD_FACTS)).isEqualTo(400 * 1024)
        assertThat(ProductImagePolicy.maxBytes(Source.RECIPE_PAGE)).isEqualTo(3 * 1024 * 1024)
        assertThat(ProductImagePolicy.maxBytes(Source.RECIPE_PAGE)).isGreaterThan(ProductImagePolicy.maxBytes(Source.OPEN_FOOD_FACTS))
    }

    @Test fun `the disk cache name does not depend on the source`() {
        val url = "https://example.test/a.jpg"
        assertThat(ProductImagePolicy.cacheName(url)).hasLength(64)
        assertThat(ProductImagePolicy.cacheName(url)).doesNotContain("/")
    }
}
