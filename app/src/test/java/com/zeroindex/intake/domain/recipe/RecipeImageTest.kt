package com.zeroindex.intake.domain.recipe

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Recipe Hunter's thumbnail: JSON-LD in every form, then og:image, twitter:image, the first large picture. */
class RecipeImageTest {

    private val page = "https://food.example/recipes/lemon-chicken/"

    private fun html(image: String? = null, head: String = "", body: String = "", graph: String? = null): String {
        val ld = graph ?: """{"@context":"https://schema.org","@type":"Recipe","name":"Lemon chicken",
            "recipeIngredient":["1 lb chicken"],"recipeInstructions":"Cook it."${if (image != null) ""","image":$image""" else ""}}"""
        return """<html><head>$head<script type="application/ld+json">$ld</script></head><body>$body</body></html>"""
    }

    private fun image(h: String, url: String? = page): String? =
        (RecipePageParser.parse(h, url) as PageParse.Found).recipe.imageUrl

    @Test fun `json ld image as a plain string`() {
        assertThat(image(html("\"https://cdn.example/a.jpg\""))).isEqualTo("https://cdn.example/a.jpg")
    }

    @Test fun `json ld image as an array takes the first usable entry`() {
        assertThat(image(html("""["https://cdn.example/a.jpg","https://cdn.example/b.jpg"]"""))).isEqualTo("https://cdn.example/a.jpg")
    }

    @Test fun `json ld image as an ImageObject with url`() {
        assertThat(image(html("""{"@type":"ImageObject","url":"https://cdn.example/o.jpg","width":1200}"""))).isEqualTo("https://cdn.example/o.jpg")
    }

    @Test fun `json ld image as an array of ImageObjects with contentUrl`() {
        assertThat(image(html("""[{"@type":"ImageObject","contentUrl":"https://cdn.example/c.jpg"}]"""))).isEqualTo("https://cdn.example/c.jpg")
    }

    @Test fun `json ld image as an id reference into the graph`() {
        val graph = """{"@context":"https://schema.org","@graph":[
            {"@type":"WebPage","@id":"https://food.example/lemon/#webpage"},
            {"@type":"ImageObject","@id":"https://food.example/lemon/#primaryimage","url":"https://cdn.example/g.jpg","width":900,"height":600},
            {"@type":"Recipe","name":"Lemon chicken","recipeIngredient":["1 lb chicken"],"recipeInstructions":"Cook it.",
             "image":{"@id":"https://food.example/lemon/#primaryimage"}}]}"""
        assertThat(image(html(graph = graph))).isEqualTo("https://cdn.example/g.jpg")
    }

    @Test fun `json ld image as an array holding an id reference`() {
        val graph = """{"@graph":[
            {"@type":"ImageObject","@id":"#img","contentUrl":"https://cdn.example/r.jpg"},
            {"@type":"Recipe","name":"Lemon chicken","recipeIngredient":["1 lb chicken"],"recipeInstructions":"Cook it.","image":[{"@id":"#img"}]}]}"""
        assertThat(image(html(graph = graph))).isEqualTo("https://cdn.example/r.jpg")
    }

    @Test fun `a relative json ld image is made absolute`() {
        assertThat(image(html("\"/wp-content/uploads/lemon.jpg\""))).isEqualTo("https://food.example/wp-content/uploads/lemon.jpg")
        assertThat(image(html("\"lemon.jpg\""))).isEqualTo("https://food.example/recipes/lemon-chicken/lemon.jpg")
        assertThat(image(html("\"//cdn.example/p.jpg\""))).isEqualTo("https://cdn.example/p.jpg")
    }

    @Test fun `og image is used when the structured data has no image`() {
        val head = """<meta property="og:image" content="https://cdn.example/og.jpg">"""
        assertThat(image(html(head = head))).isEqualTo("https://cdn.example/og.jpg")
    }

    @Test fun `og image works with the attributes in the other order and entities in the address`() {
        val head = """<meta content="/img/og.jpg?a=1&amp;b=2" property="og:image" />"""
        assertThat(image(html(head = head))).isEqualTo("https://food.example/img/og.jpg?a=1&b=2")
    }

    @Test fun `structured data wins over og image`() {
        val head = """<meta property="og:image" content="https://cdn.example/og.jpg">"""
        assertThat(image(html("\"https://cdn.example/ld.jpg\"", head = head))).isEqualTo("https://cdn.example/ld.jpg")
    }

    @Test fun `twitter image is next after og image`() {
        val head = """<meta name="twitter:image" content="https://cdn.example/tw.jpg">"""
        assertThat(image(html(head = head))).isEqualTo("https://cdn.example/tw.jpg")
        val both = head + """<meta property="og:image" content="https://cdn.example/og.jpg">"""
        assertThat(image(html(head = both))).isEqualTo("https://cdn.example/og.jpg")
    }

    @Test fun `the first large image in the article is the last resort`() {
        val body = """<article><img src="/logo.png" width="40" height="40">
            <img src="/img/tiny.jpg" width="50" height="50">
            <img src="/img/hero.jpg" width="1200" height="800"><img src="/img/second.jpg" width="1200"></article>"""
        assertThat(image(html(body = body))).isEqualTo("https://food.example/img/hero.jpg")
    }

    @Test fun `a lazy loaded large image is read from data-src or the widest srcset entry`() {
        val lazy = """<article><img data-src="/img/lazy.jpg" src="data:image/gif;base64,R0lG" class="lazyload"></article>"""
        assertThat(image(html(body = lazy))).isEqualTo("https://food.example/img/lazy.jpg")
        val set = """<article><img srcset="/img/s.jpg 400w, /img/l.jpg 1200w" src="/img/s.jpg"></article>"""
        assertThat(image(html(body = set))).isEqualTo("https://food.example/img/l.jpg")
    }

    @Test fun `icons, svgs and tracking pixels are not photos`() {
        val body = """<article><img src="/img/icon-print.png"><img src="/a.svg" width="600"><img src="/px.gif" width="600"></article>"""
        assertThat(image(html(body = body))).isNull()
    }

    @Test fun `no image anywhere gives null`() {
        assertThat(image(html())).isNull()
    }

    @Test fun `a relative address with no page address cannot be resolved`() {
        assertThat(image(html("\"/lemon.jpg\""), url = null)).isNull()
    }
}
