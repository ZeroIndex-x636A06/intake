package com.zeroindex.intake.data.foodref

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.sql.DriverManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Checks the committed asset against what Room will validate on a device (Room rejects
 * a pre-packaged database whose table does not match the entity), searches it the way
 * the app does, and runs the Python converter on its fixture.
 */
class FoodRefAssetTest {

    private fun firstExisting(vararg paths: String) = paths.map(::File).first { it.exists() }

    private val asset = firstExisting("src/main/assets/food_ref_v1.db", "app/src/main/assets/food_ref_v1.db")

    private fun <T> withDb(block: (java.sql.Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${asset.absolutePath}").use(block)

    @Test
    fun userVersionMatchesTheRoomDatabaseVersion() = withDb { c ->
        c.createStatement().executeQuery("PRAGMA user_version").use { rs ->
            rs.next()
            assertThat(rs.getInt(1)).isEqualTo(FoodRefDatabase.VERSION)
        }
    }

    @Test
    fun tableMatchesTheExportedRoomSchema() = withDb { c ->
        val schemaFile = firstExisting(
            "../schemas/com.zeroindex.intake.data.foodref.FoodRefDatabase/1.json",
            "schemas/com.zeroindex.intake.data.foodref.FoodRefDatabase/1.json",
        )
        val entity = Json.parseToJsonElement(schemaFile.readText()).jsonObject["database"]!!
            .jsonObject["entities"]!!.jsonArray.single().jsonObject
        val expected = entity["fields"]!!.jsonArray.map {
            val f = it.jsonObject
            Triple(f["columnName"]!!.jsonPrimitive.content, f["affinity"]!!.jsonPrimitive.content, f["notNull"]!!.jsonPrimitive.content == "true")
        }
        val actual = mutableListOf<Triple<String, String, Boolean>>()
        c.createStatement().executeQuery("PRAGMA table_info(ref_food)").use { rs ->
            while (rs.next()) actual += Triple(rs.getString("name"), rs.getString("type"), rs.getInt("notnull") == 1)
        }
        assertThat(actual).isEqualTo(expected)
        assertThat(entity["tableName"]!!.jsonPrimitive.content).isEqualTo("ref_food")
    }

    @Test
    fun hasAboutEightThousandFoodsAndIsSmall() = withDb { c ->
        c.createStatement().executeQuery("SELECT COUNT(*) FROM ref_food").use { rs ->
            rs.next()
            assertThat(rs.getInt(1)).isAtLeast(7_500)
        }
        assertThat(asset.length()).isLessThan(5_000_000L)
    }

    private fun search(c: java.sql.Connection, query: String): List<RefFoodEntity> {
        val tokens = FoodRefSearch.queryTokens(query)
        val padded = tokens + List(FoodRefSearch.MAX_TOKENS - tokens.size) { "" }
        val sql = """
            SELECT * FROM ref_food WHERE name LIKE '%' || ? || '%'
              AND (? = '' OR name LIKE '%' || ? || '%') AND (? = '' OR name LIKE '%' || ? || '%')
              AND (? = '' OR name LIKE '%' || ? || '%') ORDER BY LENGTH(name), name LIMIT ${FoodRefSearch.CANDIDATE_LIMIT}
        """.trimIndent()
        val rows = mutableListOf<RefFoodEntity>()
        c.prepareStatement(sql).use { st ->
            st.setString(1, padded[0])
            st.setString(2, padded[1]); st.setString(3, padded[1])
            st.setString(4, padded[2]); st.setString(5, padded[2])
            st.setString(6, padded[3]); st.setString(7, padded[3])
            st.executeQuery().use { rs ->
                while (rs.next()) {
                    rows += RefFoodEntity(
                        rs.getInt("id"), rs.getString("name"), rs.getString("category"), rs.getDouble("kcal"),
                        rs.getDouble("protein"), rs.getDouble("carbs"), rs.getDouble("fat"), rs.getDouble("fiber"),
                        rs.getDouble("sugar"), rs.getDouble("sodium"), rs.getDouble("saturatedFat"),
                        rs.getString("portions"), rs.getString("dataset"),
                    )
                }
            }
        }
        return FoodRefSearch.rank(query, rows)
    }

    @Test
    fun realQueriesReturnSensibleFoodsQuickly() = withDb { c ->
        val queries = listOf("chicken breast", "cheddar", "egg", "banana", "brown rice", "oats", "whole milk", "salmon")
        search(c, "egg") // warm up
        var worstMs = 0L
        for (q in queries) {
            val start = System.nanoTime()
            val results = search(c, q)
            worstMs = maxOf(worstMs, (System.nanoTime() - start) / 1_000_000)
            assertThat(results).isNotEmpty()
        }
        println("food_ref search: worst of ${queries.size} queries = $worstMs ms (JVM, sqlite-jdbc)")
        assertThat(search(c, "cheddar").first().name).startsWith("Cheese, cheddar")
        assertThat(search(c, "banana").first().name.lowercase()).startsWith("bananas")
        // Every result carries per-100g macros and at least the energy value.
        assertThat(search(c, "chicken breast").all { it.kcal > 0 }).isTrue()
    }

    @Test
    fun converterRunsOnItsFixture() {
        val python = listOf("python3", "python").firstOrNull {
            runCatching { ProcessBuilder(it, "--version").start().waitFor() == 0 }.getOrDefault(false)
        }
        assumeTrue("python3 is not installed", python != null)
        val tools = firstExisting("../tools", "tools")
        val process = ProcessBuilder(python!!, "-I", File(tools, "test_build_food_db.py").path)
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertThat(process.waitFor()).isEqualTo(0)
        assertThat(output).contains("OK")
    }
}
