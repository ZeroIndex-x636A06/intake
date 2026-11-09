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
    fun migrationTwoToThreeCreatesTheCoachHistoryTableEmptyAndUsable() {
        val v2 = createAt(2)
        assertThat(entities(2)).doesNotContainKey("coach_suggestion")
        MIGRATION_2_3_STATEMENTS.forEach { v2.exec(it) }
        assertThat(v2.rows("SELECT COUNT(*) AS n FROM coach_suggestion").single()["n"]).isEqualTo(0)
        v2.exec(
            "INSERT INTO coach_suggestion (id, checkInDay, suggestionKey, kind, title, why, details, actionJson, priority, status, " +
                "decidedAt, aiTitle, aiWhy, createdAt, updatedAt, deletedAt) VALUES ('c1', 20000, 'cal:down', 'WEIGHT_TREND', " +
                "'Eat 150 fewer calories', 'why', NULL, '{}', 10, 'PENDING', NULL, NULL, NULL, 1, 1, NULL)",
        )
        assertThat(v2.rows("SELECT status FROM coach_suggestion").single()["status"]).isEqualTo("PENDING")
        assertMatches(v2, 3)
        v2.close()
    }

    @Test
    fun migrationTwoToThreeCreatesTheAiUsageLogEmptyAndUsable() {
        val v2 = createAt(2)
        assertThat(entities(2)).doesNotContainKey("ai_usage")
        MIGRATION_2_3_STATEMENTS.forEach { v2.exec(it) }
        assertThat(v2.rows("SELECT COUNT(*) AS n FROM ai_usage").single()["n"]).isEqualTo(0)
        v2.exec(
            "INSERT INTO ai_usage (id, feature, model, triggerKind, promptTokens, completionTokens, costUsd, costReported, " +
                "success, at, createdAt, updatedAt, deletedAt) VALUES ('u1', 'PLAN_B', 'a/b', 'USER_ACTION', 1200, 300, 0.0042, 1, " +
                "1, 1700000000000, 1, 1, NULL)",
        )
        v2.exec(
            "INSERT INTO ai_usage (id, feature, model, triggerKind, promptTokens, completionTokens, costUsd, costReported, " +
                "success, at, createdAt, updatedAt) VALUES ('u2', 'PLAN_B', 'a/b', 'USER_ACTION', NULL, NULL, 0.0, 0, 0, 1700000001000, 1, 1)",
        )
        assertThat(v2.rows("SELECT SUM(costUsd) AS s FROM ai_usage WHERE deletedAt IS NULL").single()["s"]).isEqualTo(0.0042)
        assertMatches(v2, 3)
        v2.close()
    }

    @Test
    fun releasedVersionTwoSchemaIsFrozen() {
        // 2.json is the schema of the installed APK (built at 4952404). Nothing later may touch it.
        val v2 = entities(2)
        assertThat(v2).doesNotContainKey("allergen_definition")
        val aliasColumns = v2.getValue("allergen_alias")["fields"]!!.jsonArray
            .map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
        assertThat(aliasColumns).containsExactly("id", "allergen", "aliasTerm", "createdAt", "updatedAt", "deletedAt")
        val foodColumns = v2.getValue("food")["fields"]!!.jsonArray
            .map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
        assertThat(foodColumns).doesNotContain("offContributedAt")
        assertThat(v2.keys).containsAtLeast("sync_conflict", "hydration_log", "backup_run")
    }

    @Test
    fun releasedVersionTwoUpgradesToThreeWithAllergenAndFoodChanges() {
        val c = createAt(2)
        MIGRATION_2_3_STATEMENTS.forEach { c.exec(it) }
        assertMatches(c, 3)
        c.close()
    }

    @Test
    fun migrationTwoToThreeMarksNonSeedKeywordsCustomAndLeavesFoodsUncontributed() {
        val c = createAt(2)
        c.exec(
            "INSERT INTO allergen_alias (id, allergen, aliasTerm, createdAt, updatedAt) VALUES " +
                "('seed-dairy-whey', 'dairy', 'whey', 1, 1), ('3f2a-user-typed', 'dairy', 'oat creamer', 2, 2)",
        )
        c.exec(
            "INSERT INTO food (id, name, servingSizeGrams, servingLabel, kcalPer100g, proteinPer100g, carbsPer100g, " +
                "fatPer100g, fiberPer100g, sugarPer100g, sodiumPer100g, saturatedFatPer100g, source, allergenTags, " +
                "isVerified, isFavorite, timesLogged, createdAt, updatedAt) " +
                "VALUES ('f1', 'Rice', 100, '100 g', 130, 2.7, 28, 0.3, 0.4, 0, 1, 0.1, 'MANUAL', '', 0, 0, 0, 1, 1)",
        )
        MIGRATION_2_3_STATEMENTS.forEach { c.exec(it) }

        val seed = c.rows("SELECT * FROM allergen_alias WHERE id = 'seed-dairy-whey'").single()
        assertThat(seed["isCustom"]).isEqualTo(0)
        assertThat(seed["isEnabled"]).isEqualTo(1)
        assertThat(seed["level"]).isEqualTo("CONTAINS")
        val typed = c.rows("SELECT * FROM allergen_alias WHERE id = '3f2a-user-typed'").single()
        assertThat(typed["isCustom"]).isEqualTo(1)
        assertThat(typed["isEnabled"]).isEqualTo(1)
        assertThat(typed["level"]).isEqualTo("CONTAINS")
        assertThat(typed["aliasTerm"]).isEqualTo("oat creamer")
        assertThat(c.rows("SELECT * FROM food WHERE id = 'f1'").single()["offContributedAt"]).isNull()
        assertThat(c.rows("SELECT COUNT(*) AS n FROM allergen_definition").single()["n"]).isEqualTo(0)
        c.close()
    }

    @Test
    fun migrationTwoToThreeTurnsExistingWaterIntoPlainWaterAndKeepsEveryTotal() {
        val c = createAt(2)
        c.exec("INSERT INTO hydration_log (id, loggedAt, volumeMl, presetId, createdAt, updatedAt) VALUES ('h1', 10, 500, 'p1', 1, 1)")
        c.exec("INSERT INTO hydration_log (id, loggedAt, volumeMl, createdAt, updatedAt, deletedAt) VALUES ('h2', 20, 250, 1, 1, 5)")
        c.exec("INSERT INTO hydration_preset (id, name, volumeMl, displayOrder, createdAt, updatedAt) VALUES ('p1', 'Bottle', 500, 1, 1, 1)")
        MIGRATION_2_3_STATEMENTS.forEach { c.exec(it) }

        val rows = c.rows("SELECT * FROM hydration_log ORDER BY id")
        assertThat(rows).hasSize(2)
        rows.forEach {
            assertThat(it["drinkType"]).isEqualTo("WATER")
            assertThat((it["hydrationFactor"] as Number).toDouble()).isEqualTo(1.0)
            assertThat((it["caffeineMg"] as Number).toDouble()).isEqualTo(0.0)
            assertThat((it["alcoholMl"] as Number).toDouble()).isEqualTo(0.0)
        }
        assertThat((rows[0]["volumeMl"] as Number).toDouble()).isEqualTo(500.0)
        assertThat(rows[1]["deletedAt"]).isEqualTo(5)
        assertThat(c.rows("SELECT drinkType FROM hydration_preset").single()["drinkType"]).isEqualTo("WATER")
        assertMatches(c, 3)
        c.close()
    }

    @Test
    fun hydrationColumnsAreOnlyInVersionThree() {
        val v2 = entities(2).getValue("hydration_log")["fields"]!!.jsonArray
            .map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
        assertThat(v2).containsExactly("id", "loggedAt", "volumeMl", "presetId", "createdAt", "updatedAt", "deletedAt")
        val v3 = entities(3).getValue("hydration_log")["fields"]!!.jsonArray
            .map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
        assertThat(v3).containsAtLeast("drinkType", "hydrationFactor", "caffeineMg", "alcoholMl")
    }

    @Test
    fun migrationTwoToThreeAddsExerciseColumnsAndKeepsEveryExistingRow() {
        val c = createAt(2)
        c.exec("INSERT INTO exercise (id, name, primaryMuscle, secondaryMuscles, equipment, isBodyweight, defaultRestSec, notes, createdAt, updatedAt) VALUES ('e1', 'Arm circles', 'shoulders', '', '', 1, 60, 'mine', 1, 1)")
        c.exec("INSERT INTO program (id, name, description, isActive, currentWeek, currentDayIndex, createdAt, updatedAt) VALUES ('p1', 'Old plan', '', 1, 3, 1, 1, 1)")
        c.exec("INSERT INTO program_day (id, programId, dayIndex, name, createdAt, updatedAt) VALUES ('d1', 'p1', 0, 'Day 1', 1, 1)")
        c.exec("INSERT INTO progression_rule (id, name, type, params, createdAt, updatedAt) VALUES ('r1', 'DP', 'DOUBLE_PROGRESSION', '{}', 1, 1)")
        c.exec("INSERT INTO program_exercise (id, programDayId, exerciseId, displayOrder, targetSets, targetRepsMin, targetRepsMax, progressionRuleId, restSec, createdAt, updatedAt) VALUES ('x1', 'd1', 'e1', 0, 3, 8, 12, 'r1', 60, 1, 1)")
        assertThat(entities(2).getValue("exercise")["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }).doesNotContain("photoPath")

        MIGRATION_2_3_STATEMENTS.forEach { c.exec(it) }

        val e = c.rows("SELECT * FROM exercise WHERE id = 'e1'").single()
        assertThat(e["photoPath"]).isNull()
        assertThat(e["notes"]).isEqualTo("mine")
        val p = c.rows("SELECT * FROM program WHERE id = 'p1'").single()
        assertThat(p["isStandalone"]).isEqualTo(0)
        assertThat(p["isActive"]).isEqualTo(1)
        assertThat(c.rows("SELECT weekday FROM program_day WHERE id = 'd1'").single()["weekday"]).isNull()
        assertThat(c.rows("SELECT notes FROM program_exercise WHERE id = 'x1'").single()["notes"]).isNull()
        // A saved workout is a standalone program with one day; the program list query leaves it out.
        c.exec("INSERT INTO program (id, name, description, isActive, currentWeek, currentDayIndex, isStandalone, createdAt, updatedAt) VALUES ('w1', 'Arm day', '', 0, 1, 0, 1, 1, 1)")
        assertThat(c.rows("SELECT id FROM program WHERE deletedAt IS NULL AND isStandalone = 0").map { it["id"] }).containsExactly("p1")
        assertMatches(c, 3)
        c.close()
    }

    @Test
    fun exerciseColumnsAreOnlyInVersionThree() {
        fun cols(v: Int, table: String) = entities(v).getValue(table)["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
        assertThat(cols(2, "program")).doesNotContain("isStandalone")
        assertThat(cols(2, "program_day")).doesNotContain("weekday")
        assertThat(cols(2, "program_exercise")).doesNotContain("notes")
        assertThat(cols(3, "program")).contains("isStandalone")
        assertThat(cols(3, "program_day")).contains("weekday")
        assertThat(cols(3, "program_exercise")).contains("notes")
        assertThat(cols(3, "exercise")).contains("photoPath")
    }

    private fun planWeek(c: Connection, id: String, number: Int, dish: String, notes: String = "", deleted: String = "NULL") =
        c.exec(
            "INSERT INTO plan_week (id, weekNumber, mainDishName, proteinType, notes, createdAt, updatedAt, deletedAt) " +
                "VALUES ('$id', $number, '$dish', 'Chicken', '${notes.replace("'", "''")}', 1, 1, $deleted)",
        )

    @Test
    fun migrationTwoToThreeTurnsExistingPlanWeeksIntoMyPlanAndMovesTheWeekZeroData() {
        val c = createAt(2)
        planWeek(c, "w0", 0, "Every week", """{"intro":"Hello","pantry":["rice"]}""")
        planWeek(c, "w1", 1, "Taco bowls", "a note")
        planWeek(c, "w2", 2, "Burgers")
        planWeek(c, "w3", 3, "Removed week", deleted = "5")
        c.exec("INSERT INTO shopping_list_item (id, planWeekId, name, quantity, category, isChecked, isPantryStaple, createdAt, updatedAt) VALUES ('s1', 'w1', 'Onions', '', 'PRODUCE', 1, 0, 1, 1)")
        c.exec("INSERT INTO eat_out_option (id, planWeekId, venueName, whatToOrder, cautionNote, estimatedKcal, estimatedProtein, createdAt, updatedAt) VALUES ('e1', 'w1', 'Subway', 'Bowl', '', 500, 30, 1, 1)")

        MIGRATION_2_3_STATEMENTS.forEach { c.exec(it) }
        assertMatches(c, 3)

        val plan = c.rows("SELECT * FROM meal_plan").single()
        assertThat(plan["id"]).isEqualTo("plan-default")
        assertThat(plan["name"]).isEqualTo("My plan")
        assertThat(plan["isActive"]).isEqualTo(1)
        assertThat(plan["loopMode"]).isEqualTo("NONE")
        assertThat(plan["deletedAt"]).isNull()
        assertThat(plan["metaJson"]).isEqualTo("""{"intro":"Hello","pantry":["rice"]}""")
        val day = (plan["startDate"] as Number).toLong()
        assertThat(day).isGreaterThan(19_000L) // an epoch day, not millis
        assertThat(day).isLessThan(100_000L)

        // Every week, the removed one included, belongs to the plan; week 0 is retired softly, never deleted.
        val weeks = c.rows("SELECT * FROM plan_week ORDER BY weekNumber")
        assertThat(weeks.map { it["planId"] }.toSet()).containsExactly("plan-default")
        assertThat(weeks.map { it["weekNumber"] }).containsExactly(0, 1, 2, 3).inOrder()
        assertThat(weeks.first { it["weekNumber"] == 0 }["deletedAt"]).isNotNull()
        assertThat(weeks.first { it["weekNumber"] == 1 }["deletedAt"]).isNull()
        assertThat(weeks.first { it["weekNumber"] == 1 }["notes"]).isEqualTo("a note")
        assertThat(weeks.first { it["weekNumber"] == 3 }["deletedAt"]).isEqualTo(5)

        // Children keep pointing at their weeks.
        assertThat(c.rows("SELECT isChecked FROM shopping_list_item WHERE id = 's1'").single()["isChecked"]).isEqualTo(1)
        assertThat(c.rows("SELECT planWeekId FROM eat_out_option WHERE id = 'e1'").single()["planWeekId"]).isEqualTo("w1")
        c.close()
    }

    @Test
    fun migrationTwoToThreeMakesNoPlanWhenThereAreNoPlanWeeks() {
        val c = createAt(2)
        // Only a retired week: nothing live to build a plan from.
        planWeek(c, "w1", 1, "Old", deleted = "5")
        MIGRATION_2_3_STATEMENTS.forEach { c.exec(it) }
        assertMatches(c, 3)
        assertThat(c.rows("SELECT COUNT(*) AS n FROM meal_plan").single()["n"]).isEqualTo(0)
        c.close()
    }

    @Test
    fun fullChainOneToTwoToThreeMatchesTheCurrentSchema() {
        val c = createAt(1)
        MIGRATION_1_2_STATEMENTS.forEach { c.exec(it) }
        assertMatches(c, 2)
        MIGRATION_2_3_STATEMENTS.forEach { c.exec(it) }
        assertMatches(c, 3)
        assertThat(IntakeDatabase.VERSION).isEqualTo(3)
        c.close()
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
