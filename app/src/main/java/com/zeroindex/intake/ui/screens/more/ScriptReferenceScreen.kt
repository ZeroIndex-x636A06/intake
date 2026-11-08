package com.zeroindex.intake.ui.screens.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.docs.Markdown
import com.zeroindex.intake.domain.docs.MdBlock
import com.zeroindex.intake.domain.docs.MdInline
import com.zeroindex.intake.domain.docs.MdLinkTarget
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The wiki pages bundled from docs/scripting, in reading order. */
private val WikiPages = listOf(
    "README" to "Overview",
    "program" to "Programs",
    "progress-rules" to "Progress rules",
    "meal-plan" to "Meal plans",
    "errors" to "Errors",
    "ai-prompt" to "Optional: AI prompt",
    "examples/strength-block" to "Example program",
    "examples/meal-plan-sample" to "Example plan",
)

/**
 * The offline reference for Intake script: the Markdown in docs/scripting, bundled as assets and
 * drawn here. Links between pages open in the app, `#anchor` links scroll to the heading, and web
 * links are shown but do nothing: this screen never touches the network.
 */
@Composable
fun ScriptReferenceScreen(
    page: String,
    anchor: String?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // History of "page#anchor" so a followed link can be undone with Back.
    var history by rememberSaveable { mutableStateOf(arrayListOf(encode(page, anchor))) }
    val (currentPage, currentAnchor) = decode(history.last())

    fun open(target: String, targetAnchor: String?) {
        history = ArrayList(history + encode(target, targetAnchor))
    }
    BackHandler(enabled = history.size > 1) { history = ArrayList(history.dropLast(1)) }

    val text by produceState<String?>(initialValue = null, currentPage) {
        value = withContext(Dispatchers.IO) { readAsset(context, currentPage) }
    }
    val blocks = remember(text) { text?.let { Markdown.parse(it) }.orEmpty() }

    val listState = rememberLazyListState()
    LaunchedEffect(blocks, currentAnchor, history.size) {
        if (blocks.isEmpty()) return@LaunchedEffect
        val index = currentAnchor?.let { a -> blocks.indexOfFirst { it is MdBlock.Heading && it.anchor == a } } ?: -1
        // Item 0 is the page switcher, so block n is item n + 1.
        listState.scrollToItem(if (index >= 0) index + 1 else 0)
    }

    val onLink: (String) -> Unit = { raw ->
        when (val t = Markdown.resolveLink(raw)) {
            is MdLinkTarget.Anchor -> open(currentPage, t.anchor)
            is MdLinkTarget.Page -> if (WikiPages.any { it.first == resolvePage(currentPage, t.page) }) {
                open(resolvePage(currentPage, t.page), t.anchor)
            }
            MdLinkTarget.External -> Unit // No network from the docs.
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "pages") {
            Column {
                SubHead("Script reference", "Write a plan or program by hand. Works offline.")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WikiPages.forEach { (id, label) ->
                        FilterChip(
                            selected = id == currentPage,
                            onClick = { if (id != currentPage) open(id, null) },
                            label = { Text(label) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
        if (text == null) {
            item(key = "loading") { Text("Loading…", style = MaterialTheme.typography.bodyMedium) }
        }
        itemsIndexed(blocks, key = { index, _ -> "b$index" }) { _, block ->
            Block(block, currentPage, onLink)
        }
    }
}

@Composable
private fun Block(block: MdBlock, page: String, onLink: (String) -> Unit) {
    when (block) {
        is MdBlock.Heading -> Text(
            text = inline(block.inlines, onLink, MaterialTheme.colorScheme.primary),
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = if (block.level <= 2) 10.dp else 4.dp).semantics { heading() },
        )
        is MdBlock.Paragraph -> Text(
            text = inline(block.inlines, onLink, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { n, item ->
                Row {
                    Text(
                        if (block.ordered) "${n + 1}. " else "•  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = inline(item, onLink, MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
        is MdBlock.Code -> CodeBlock(block, copyable = page == "ai-prompt" && block.language == "text")
        is MdBlock.Table -> IntakeCard {
            // Plain monospace rows with padded columns; a long table scrolls sideways.
            val widths = block.rows.maxOfOrNull { it.size }?.let { cols ->
                (0 until cols).map { c -> block.rows.maxOf { r -> r.getOrNull(c)?.let(::stripMarks)?.length ?: 0 } }
            }.orEmpty()
            val rendered = block.rows.mapIndexed { r, cells ->
                widths.indices.joinToString("  ") { c -> (cells.getOrNull(c)?.let(::stripMarks) ?: "").padEnd(widths[c]) }.trimEnd() +
                    if (r == 0) "\n" + widths.joinToString("  ") { "-".repeat(it) } else ""
            }.joinToString("\n")
            Text(
                rendered,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
        MdBlock.Rule -> androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code, copyable: Boolean) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (copyable) {
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("Intake script prompt", block.text))
                    copied = true
                },
                shape = IntakeShape.Button,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (copied) "Copied" else "Copy prompt") }
        }
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = IntakeShape.Card,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = block.text,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            )
        }
    }
}

private fun stripMarks(s: String): String = Markdown.plainText(Markdown.inlines(s))

private fun inline(
    spans: List<MdInline>,
    onLink: (String) -> Unit,
    linkColor: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString { appendSpans(spans, onLink, linkColor) }

private fun AnnotatedString.Builder.appendSpans(
    spans: List<MdInline>,
    onLink: (String) -> Unit,
    linkColor: androidx.compose.ui.graphics.Color,
) {
    spans.forEach { s ->
        when (s) {
            is MdInline.Text -> append(s.text)
            is MdInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendSpans(s.children, onLink, linkColor) }
            is MdInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendSpans(s.children, onLink, linkColor) }
            is MdInline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = linkColor.copy(alpha = 0.08f))) { append(s.text) }
            is MdInline.Link -> withLink(
                LinkAnnotation.Clickable(
                    tag = s.target,
                    styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { onLink(s.target) },
                ),
            ) { appendSpans(s.children, onLink, linkColor) }
        }
    }
}

private fun encode(page: String, anchor: String?) = if (anchor == null) page else "$page#$anchor"

private fun decode(s: String): Pair<String, String?> {
    val i = s.indexOf('#')
    return if (i < 0) s to null else s.substring(0, i) to s.substring(i + 1).ifEmpty { null }
}

/** A link inside an example page is relative to its folder; wiki pages link to each other by name. */
private fun resolvePage(from: String, target: String): String {
    val base = from.substringBeforeLast('/', "")
    return if (target.startsWith("../")) target.removePrefix("../") else if (base.isNotEmpty() && "/" !in target && target !in WikiPages.map { it.first }) "$base/$target" else target
}

private fun readAsset(context: Context, page: String): String? = runCatching {
    context.assets.open("scripting/$page.md").bufferedReader().use { it.readText() }
}.getOrNull()
