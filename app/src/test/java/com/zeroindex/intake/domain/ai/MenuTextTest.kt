package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MenuTextTest {

    @Test
    fun `scripts and tags go, words and line breaks stay`() {
        val html = """
            <html><head><title>x</title><style>.a{}</style></head><body>
            <script>var menu = {};</script>
            <h2>Bowls</h2><div>Chicken bowl &amp; rice<br>Fajita veg</div>
            <p>Price&nbsp;$11.50</p></body></html>
        """.trimIndent()
        val text = MenuText.fromHtml(html)
        assertThat(text).contains("Bowls")
        assertThat(text).contains("Chicken bowl & rice")
        assertThat(text).contains("Fajita veg")
        assertThat(text).contains("Price \$11.50")
        assertThat(text).doesNotContain("var menu")
        assertThat(text).doesNotContain(".a{}")
    }

    @Test
    fun `bot checks and empty shells read as blocked`() {
        assertThat(MenuText.looksBlocked("Checking your browser before accessing the site. " + "x".repeat(500))).isTrue()
        assertThat(MenuText.looksBlocked("Loading…")).isTrue()
        assertThat(MenuText.looksBlocked("Chicken bowl rice beans salsa. ".repeat(30))).isFalse()
    }

    @Test
    fun `delivery marketplaces are not scraped around`() {
        assertThat(MenuText.forbidsScraping("https://www.doordash.com/store/chipotle-123/")).isTrue()
        assertThat(MenuText.forbidsScraping("https://order.ubereats.com/x")).isTrue()
        assertThat(MenuText.forbidsScraping("https://mycafe.square.site/menu")).isFalse()
        assertThat(MenuText.forbidsScraping("https://notdoordash.com/menu")).isFalse()
    }
}
