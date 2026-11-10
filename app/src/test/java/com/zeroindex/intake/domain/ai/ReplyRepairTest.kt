package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test

class ReplyRepairTest {

    @Serializable
    private data class Item(val name: String, val kcal: Double? = null, val tags: List<String> = emptyList())

    @Serializable
    private data class Menu(val items: List<Item>)

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(text: String) = ReplyRepair.decode(json, Menu.serializer(), text)

    private fun ok(text: String): Menu = (decode(text) as ReplyDecode.Ok).value

    @Test
    fun `clean json is untouched`() {
        assertThat(ReplyRepair.extractJson("""{"items":[]}""")).isEqualTo("""{"items":[]}""")
        assertThat(ok("""{"items":[{"name":"a","kcal":10}]}""").items.single().kcal).isEqualTo(10.0)
    }

    @Test
    fun `a markdown fence is stripped, with or without a language tag`() {
        assertThat(ok("```json\n{\"items\":[{\"name\":\"a\"}]}\n```").items).hasSize(1)
        assertThat(ok("```\n{\"items\":[{\"name\":\"b\"}]}\n```").items.single().name).isEqualTo("b")
    }

    @Test
    fun `prose before and after the object is ignored`() {
        val reply = "Sure! Here is the menu you asked for:\n{\"items\":[{\"name\":\"a\"}]}\nLet me know if you need more."
        assertThat(ok(reply).items.single().name).isEqualTo("a")
    }

    @Test
    fun `the outermost object is taken, not the first closing brace`() {
        val reply = """note {"items":[{"name":"a","tags":["x"]},{"name":"b"}]} done"""
        assertThat(ok(reply).items.map { it.name }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `braces and quotes inside strings do not confuse the scan`() {
        val reply = """{"items":[{"name":"mac } and \"cheese\" {","kcal":5}]}"""
        assertThat(ok(reply).items.single().name).isEqualTo("""mac } and "cheese" {""")
    }

    @Test
    fun `trailing commas are tolerated in objects and arrays`() {
        assertThat(ok("""{"items":[{"name":"a",},{"name":"b"},],}""").items).hasSize(2)
        assertThat(ok("{\"items\":[{\"name\":\"a\",\"tags\":[\"x\",\n]\n,\n}\n,\n]}").items.single().tags).containsExactly("x")
    }

    @Test
    fun `a comma inside a string before a brace is kept`() {
        assertThat(ok("""{"items":[{"name":"salt, }"}]}""").items.single().name).isEqualTo("salt, }")
    }

    @Test
    fun `a byte order mark and surrounding whitespace are ignored`() {
        assertThat(ok(0xFEFF.toChar() + "  \n{\"items\":[]}\n ").items).isEmpty()
    }

    @Test
    fun `a reply cut off by the token limit is invalid, with a reason that says so`() {
        val result = decode("""{"items":[{"name":"a"},{"name":"b""")
        assertThat(result).isInstanceOf(ReplyDecode.Invalid::class.java)
        assertThat((result as ReplyDecode.Invalid).error).contains("cut off")
    }

    @Test
    fun `no json at all is invalid`() {
        val result = decode("I cannot help with that.") as ReplyDecode.Invalid
        assertThat(result.error).contains("no JSON")
    }

    @Test
    fun `json of the wrong shape is invalid and the reason is one short line`() {
        val result = decode("""{"items":"none"}""") as ReplyDecode.Invalid
        assertThat(result.error).isNotEmpty()
        assertThat(result.error.lines()).hasSize(1)
        assertThat(result.error.length).isAtMost(300)
    }

    @Test
    fun `unknown keys and a missing optional field are accepted`() {
        assertThat(ok("""{"items":[{"name":"a","extra":true}],"note":"x"}""").items.single().kcal).isNull()
    }

    @Test
    fun `a null where a default exists is coerced to the default`() {
        assertThat(ok("""{"items":[{"name":"a","tags":null}]}""").items.single().tags).isEmpty()
    }
}
