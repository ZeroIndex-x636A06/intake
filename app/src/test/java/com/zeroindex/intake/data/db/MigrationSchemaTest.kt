package com.zeroindex.intake.data.db

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Runs every migration's real SQL against a database built from the committed
 * schema JSON, then checks the result is exactly the schema Room expects for
 * the next version: same tables, columns, types, nullability, defaults,
 * primary keys, indices and foreign keys. That is the same comparison Room
 * makes when it opens the database on a phone, where a mismatch is a crash on
 * launch - so this is the test that a migration will not brick the app.
 *
 * Runs on the JVM with sqlite-jdbc rather than as an instrumented test, so it
 * runs on every build instead of only when a device is attached.
 */
class MigrationSchemaTest {

    private val schemaDir = listOf(
        File("../schemas/com.zeroindex.intake.data.db.IntakeDatabase"),
        File("schemas/com.zeroindex.intake.data.db.IntakeDatabase"),
    ).first { it.isDirectory }

    private fun schema(version: Int): JsonObject =
        Json.parseToJsonElement(File(schemaDir, "$version.json").readText())
            .jsonObject["database"]!!.jsonObject

    private fun entities(version: Int): Map<String, JsonObject> =
        schema(version)["entities"]!!.jsonArray.associate {
            val e = it.jsonObject
            e["tableName"]!!.jsonPrimitive.content to e
        }

    private fun JsonObject.sql(key: String, table: String) =
        this[key]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)

    private fun createAt(version: Int): Connection {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
            entities(version).forEach { (table, entity) ->
                st.execute(entity.sql("createSql", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    st.execute(index.jsonObject.sql("createSql", table))
                }
            }
        }
        return connection
    }

    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.rows(sql: String): List<Map<String, Any?>> =
        createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                val meta = rs.metaData
                buildList {
                    while (rs.next()) {
                        add((1..meta.columnCount).associate { meta.getColumnName(it) to rs.getObject(it) })
                    }
                }
            }
        }

    @Test
    fun everyMigrationProducesTheSchemaRoomExpects() {
        val latest = IntakeDatabase.VERSION
        for (from in 1 until latest) {
            val connection = createAt(from)
            MIGRATION_STATEMENTS.getValue(from).forEach { connection.exec(it) }
            assertMatches(connection, from + 1)
            connection.close()
        }
    }

    @Test
    fun chainedFromVersionOneReachesTheLatestSchema() {
        val connection = createAt(1)
        for (from in 1 until IntakeDatabase.VERSION) {
            MIGRATION_STATEMENTS.getValue(from).forEach { connection.exec(it) }
        }
        assertMatches(connection, IntakeDatabase.VERSION)
        connection.close()
    }

    @Test
    fun migrationTwoToThreeAddsTheContributionTimestampAndKeepsFoods() {
        val c = createAt(2)
        c.exec(
            "INSERT INTO food (id, name, brand, barcode, servingSizeGrams, servingLabel, kcalPer100g, " +
                "proteinPer100g, carbsPer100g, fatPer100g, fiberPer100g, sugarPer100g, sodiumPer100g, " +
                "saturatedFatPer100g, source, allergenTags, isVerified, isFavorite, timesLogged, createdAt, updatedAt) " +
                "VALUES ('f1', 'Oat bar', NULL, '0894700010137', 40, '1 bar', 400, 10, 60, 12, 5, 20, 100, 3, " +
                "'BARCODE', '', 0, 0, 2, 1, 1)",
        )
        MIGRATION_2_3_STATEMENTS.forEach { c.exec(it) }
        val food = c.rows("SELECT * FROM food WHERE id = 'f1'").single()
        assertThat(food["name"]).isEqualTo("Oat bar")
        assertThat(food["timesLogged"]).isEqualTo(2)
        assertThat(food["offContributedAt"]).isNull()
        assertMatches(c, 3)
    }

    @Test
    fun migrationOneToTwoKeepsExistingRowsAndFillsDefaults() {
        val c = createAt(1)
        c.exec(
            "INSERT INTO recipe (id, name, servings, instructions, totalTimeMinutes, sourceWeekNumber, " +
                "createdAt, updatedAt, deletedAt) VALUES ('r1', 'Chicken rice', 4, 'Cook.', 35, 5, 1, 2, NULL)",
        )
        c.exec(
            "INSERT INTO body_metric (id, measuredAt, weightKg, bodyFatPercent, createdAt, updatedAt) " +
                "VALUES ('b1', 1000, 84.0, 18.5, 1, 1)",
        )
        c.exec(
            "INSERT INTO eat_out_option (id, planWeekId, venueName, whatToOrder, cautionNote, estimatedKcal, " +
                "estimatedProtein, createdAt, updatedAt) VALUES ('e1', NULL, 'Chipotle', 'Chicken bowl', '', 620, 42, 1, 1)",
        )
        c.exec("INSERT INTO meal_template (id, name, createdAt, updatedAt) VALUES ('m1', 'Prep box A', 1, 1)")

        MIGRATION_1_2_STATEMENTS.forEach { c.exec(it) }

        val recipe = c.rows("SELECT * FROM recipe WHERE id = 'r1'").single()
        assertThat(recipe["name"]).isEqualTo("Chicken rice")
        assertThat(recipe["sourceWeekNumber"]).isEqualTo(5)
        assertThat(recipe["origin"]).isEqualTo("USER")
        assertThat(recipe["ingredientsText"]).isEqualTo("")
        assertThat(recipe["kcalPerServing"]).isNull()

        val body = c.rows("SELECT * FROM body_metric WHERE id = 'b1'").single()
        assertThat(body["weightKg"]).isEqualTo(84.0)
        assertThat(body["bodyFatPercent"]).isEqualTo(18.5)
        assertThat(body["source"]).isEqualTo("MANUAL")
        assertThat(body["skeletalMuscleKg"]).isNull()

        val eatOut = c.rows("SELECT * FROM eat_out_option WHERE id = 'e1'").single()
        assertThat(eatOut["estimatedKcal"]).isEqualTo(620)
        assertThat(eatOut["estimatedCarbs"]).isEqualTo(0)
        assertThat(eatOut["origin"]).isEqualTo("PLAN")

        val template = c.rows("SELECT * FROM meal_template WHERE id = 'm1'").single()
        assertThat(template["name"]).isEqualTo("Prep box A")
        assertThat(template["nfcTagUid"]).isNull()

        // The new tables accept rows, foreign keys included.
        c.exec(
            "INSERT INTO menu_source (id, restaurantName, kind, location, enabled, lastStatus, itemCount, " +
                "createdAt, updatedAt) VALUES ('s1', 'Cava', 'URL', 'https://example.com', 1, 'NEVER_RUN', 0, 1, 1)",
        )
        c.exec(
            "INSERT INTO menu_item (id, sourceId, restaurantName, name, description, ingredientsText, " +
                "macrosPublished, allergenStatus, allergenMatches, importedAt, createdAt, updatedAt) " +
                "VALUES ('i1', 's1', 'Cava', 'Greens bowl', '', '', 0, 'CLEAR', '', 1, 1, 1)",
        )
        assertThat(c.rows("SELECT COUNT(*) AS n FROM menu_item").single()["n"]).isEqualTo(1)
        c.close()
    }

    // ----------------------------------------------------------------------

    private fun assertMatches(c: Connection, version: Int) {
        val expected = entities(version)
        val actualTables = c.rows(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
        ).map { it["name"] as String }.toSet()
        assertWithMessage("tables at v$version").that(actualTables).isEqualTo(expected.keys)

        expected.forEach { (table, entity) ->
            assertColumns(c, table, entity, version)
            assertIndices(c, table, entity, version)
            assertForeignKeys(c, table, entity, version)
        }
    }

    private fun assertColumns(c: Connection, table: String, entity: JsonObject, version: Int) {
        val actual = c.rows("PRAGMA table_info(`$table`)").associateBy { it["name"] as String }
        val pk = entity["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
        val fields = entity["fields"]!!.jsonArray.map { it.jsonObject }
        assertWithMessage("columns of $table at v$version")
            .that(actual.keys).isEqualTo(fields.map { it["columnName"]!!.jsonPrimitive.content }.toSet())

        fields.forEach { f ->
            val name = f["columnName"]!!.jsonPrimitive.content
            val col = actual.getValue(name)
            val where = "$table.$name at v$version"
            assertWithMessage("type of $where").that(col["type"])
                .isEqualTo(f["affinity"]!!.jsonPrimitive.content)
            val notNull = f["notNull"]?.jsonPrimitive?.booleanOrNull ?: false
            assertWithMessage("NOT NULL of $where").that(col["notnull"] == 1).isEqualTo(notNull)
            assertWithMessage("primary key of $where").that((col["pk"] as Int) > 0).isEqualTo(name in pk)
            // Room only checks defaults the entity declares; so does this.
            f["defaultValue"]?.jsonPrimitive?.contentOrNull?.let { default ->
                assertWithMessage("default of $where").that(col["dflt_value"]).isEqualTo(default)
            }
        }
    }

    private fun assertIndices(c: Connection, table: String, entity: JsonObject, version: Int) {
        val expected = entity["indices"]?.jsonArray?.associate {
            val i = it.jsonObject
            i["name"]!!.jsonPrimitive.content to
                (i["unique"]!!.jsonPrimitive.boolean to i["columnNames"]!!.jsonArray.map { n -> n.jsonPrimitive.content })
        }.orEmpty()
        val actual = c.rows("PRAGMA index_list(`$table`)")
            .filter { it["origin"] == "c" } // skip the automatic primary-key index
            .associate { row ->
                val name = row["name"] as String
                val columns = c.rows("PRAGMA index_info(`$name`)")
                    .sortedBy { it["seqno"] as Int }
                    .map { it["name"] as String }
                name to ((row["unique"] as Int == 1) to columns)
            }
        assertWithMessage("indices of $table at v$version").that(actual).isEqualTo(expected)
    }

    private fun assertForeignKeys(c: Connection, table: String, entity: JsonObject, version: Int) {
        val expected = entity["foreignKeys"]?.jsonArray?.map {
            val fk = it.jsonObject
            Triple(
                fk["table"]!!.jsonPrimitive.content,
                fk["columns"]!!.jsonArray.map { n -> n.jsonPrimitive.content },
                fk["referencedColumns"]!!.jsonArray.map { n -> n.jsonPrimitive.content },
            )
        }.orEmpty().toSet()
        val actual = c.rows("PRAGMA foreign_key_list(`$table`)")
            .groupBy { it["id"] as Int }
            .values
            .map { group ->
                val ordered = group.sortedBy { it["seq"] as Int }
                Triple(
                    ordered.first()["table"] as String,
                    ordered.map { it["from"] as String },
                    ordered.map { it["to"] as String },
                )
            }.toSet()
        assertWithMessage("foreign keys of $table at v$version").that(actual).isEqualTo(expected)
    }
}
