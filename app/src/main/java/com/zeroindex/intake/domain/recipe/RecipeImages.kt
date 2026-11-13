package com.zeroindex.intake.domain.recipe

import java.net.URI

/**
 * Finds a recipe page's photo. The order is: the recipe's own structured data (handled by [RecipePageParser], which
 * follows every form of `Recipe.image`), then the page's `og:image`, then `twitter:image`, then the first large
 * `<img>` in the article. Every address is made absolute against the page. Pure and total.
 */
internal object RecipeImages {
    private val META = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val IMG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ARTICLE = Regex("""<article\b.*?</article\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val NOT_A_PHOTO = Regex("""logo|icon|avatar|sprite|pixel|badge|emoji|gravatar|spinner|placeholder|blank|tracking|\.svg|\.gif|data:image|banner-ad|/ads?/""", RegexOption.IGNORE_CASE)

    /** An absolute http(s) address for [raw] as found on the page at [base], or null if it is not usable. */
    fun absolute(raw: String?, base: String?): String? {
        val text = raw?.let { HtmlText.decode(it) }?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.startsWith("data:", ignoreCase = true)) return null
        val resolved = runCatching {
            when {
                text.startsWith("http://", true) || text.startsWith("https://", true) -> URI(text.replace(" ", "%20"))
                else -> {
                    val b = base?.takeIf { it.startsWith("http", true) } ?: return null
                    URI(b).resolve(URI(text.replace(" ", "%20")))
                }
            }
        }.getOrNull() ?: return null
        return resolved.toString().takeIf { it.startsWith("http", ignoreCase = true) && resolved.host != null }
    }

    /** og:image, then twitter:image, then the first large image in the article. */
    fun fromHtml(html: String, pageUrl: String?): String? =
        meta(html, pageUrl, "og:image", "og:image:secure_url", "og:image:url")
            ?: meta(html, pageUrl, "twitter:image", "twitter:image:src")
            ?: firstLargeImage(html, pageUrl)

    private fun meta(html: String, pageUrl: String?, vararg keys: String): String? {
        val wanted = keys.map { it.lowercase() }
        val tags = META.findAll(html).map { it.value }.toList()
        for (key in wanted) {
            for (tag in tags) {
                val name = (attr(tag, "property") ?: attr(tag, "name") ?: attr(tag, "itemprop"))?.trim()?.lowercase()
                if (name != key) continue
                absolute(attr(tag, "content"), pageUrl)?.let { return it }
            }
        }
        return null
    }

    private fun firstLargeImage(html: String, pageUrl: String?): String? {
        val scope = ARTICLE.find(html)?.value ?: html
        for (m in IMG.findAll(scope)) {
            val tag = m.value
            val src = attr(tag, "data-lazy-src") ?: attr(tag, "data-src") ?: attr(tag, "data-pin-media")
                ?: bestFromSrcset(attr(tag, "srcset") ?: attr(tag, "data-srcset")) ?: attr(tag, "src") ?: continue
            if (NOT_A_PHOTO.containsMatchIn(src) || NOT_A_PHOTO.containsMatchIn(attr(tag, "class").orEmpty())) continue
            val w = attr(tag, "width")?.filter(Char::isDigit)?.toIntOrNull()
            val h = attr(tag, "height")?.filter(Char::isDigit)?.toIntOrNull()
            // "Large": a stated size of at least 300 wide (or 200 tall) when the page states one; a page that states
            // none is trusted when the file looks like a photo.
            val large = when {
                w != null && w < 300 -> false
                h != null && h < 200 -> false
                w != null || h != null -> true
                else -> Regex("""\.(?:jpe?g|webp|png)(?:[?#]|$)""", RegexOption.IGNORE_CASE).containsMatchIn(src)
            }
            if (!large) continue
            absolute(src, pageUrl)?.let { return it }
        }
        return null
    }

    /** The widest candidate in a srcset ("a.jpg 400w, b.jpg 800w"). */
    private fun bestFromSrcset(srcset: String?): String? {
        if (srcset.isNullOrBlank()) return null
        return srcset.split(',').mapNotNull { part ->
            val bits = part.trim().split(Regex("""\s+"""))
            val url = bits.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val width = bits.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
            url to width
        }.maxByOrNull { it.second }?.first
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""(?<![\w-])${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE).find(tag)
            ?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }
}
