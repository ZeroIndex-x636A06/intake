package com.zeroindex.intake.domain.recipe

import com.zeroindex.intake.data.foodref.FoodRefSearch
import com.zeroindex.intake.data.foodref.RefFoodEntity
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking

/** The real bundled USDA file, searched the way the app searches it, for tests of the ingredient estimate. */
object RefDbLookup {
    private val asset = listOf("src/main/assets/food_ref_v1.db", "app/src/main/assets/food_ref_v1.db").map(::File).first { it.exists() }

    fun <T> withLookup(block: suspend (suspend (String) -> List<ReferenceFood>) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${asset.absolutePath}").use { c -> runBlocking { block { q -> search(c, q) } } }

    private fun search(c: Connection, query: String): List<ReferenceFood> {
        val tokens = FoodRefSearch.queryTokens(query)
        if (tokens.isEmpty()) return emptyList()
        val p = tokens + List(FoodRefSearch.MAX_TOKENS - tokens.size) { "" }
        val sql = """SELECT * FROM ref_food WHERE name LIKE '%' || ? || '%' AND (? = '' OR name LIKE '%' || ? || '%')
            AND (? = '' OR name LIKE '%' || ? || '%') AND (? = '' OR name LIKE '%' || ? || '%')
            ORDER BY LENGTH(name), name LIMIT ${FoodRefSearch.CANDIDATE_LIMIT}"""
        val rows = mutableListOf<RefFoodEntity>()
        c.prepareStatement(sql).use { st ->
            st.setString(1, p[0]); st.setString(2, p[1]); st.setString(3, p[1])
            st.setString(4, p[2]); st.setString(5, p[2]); st.setString(6, p[3]); st.setString(7, p[3])
            st.executeQuery().use { rs ->
                while (rs.next()) {
                    rows += RefFoodEntity(
                        rs.getInt("id"), rs.getString("name"), rs.getString("category"), rs.getDouble("kcal"), rs.getDouble("protein"),
                        rs.getDouble("carbs"), rs.getDouble("fat"), rs.getDouble("fiber"), rs.getDouble("sugar"), rs.getDouble("sodium"),
                        rs.getDouble("saturatedFat"), rs.getString("portions"), rs.getString("dataset"),
                    )
                }
            }
        }
        return FoodRefSearch.rank(query, rows, FoodRefSearch.RESULT_LIMIT).map {
            ReferenceFood(
                it.id, it.name, it.kcal, it.protein, it.carbs, it.fat, it.fiber, it.sugar, it.sodium, it.saturatedFat,
                FoodRefSearch.parsePortions(it.portions).map { p -> ReferencePortion(p.label, p.grams) },
            )
        }
    }

}
