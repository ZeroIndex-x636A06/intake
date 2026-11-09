package com.zeroindex.intake.data.foodref

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * One row of the bundled USDA database (docs/FOOD-DATA.md). The table is built by
 * `tools/build_food_db.py` and must stay in step with it: Room validates the
 * pre-packaged file against this class when it first opens it.
 *
 * Every value is per 100 g; sodium is milligrams. [portions] is
 * "label|grams;label|grams", at most three, parsed by [FoodRefSearch.parsePortions].
 */
@Entity(tableName = "ref_food")
data class RefFoodEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val category: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodium: Double,
    val saturatedFat: Double,
    val portions: String,
    /** "SR" (SR Legacy) or "FF" (Foundation Foods). */
    val dataset: String,
)

@Dao
interface RefFoodDao {

    /**
     * Rows whose name contains every token (up to four; the caller passes "" for the
     * unused ones). Tokens are letters and digits only, so LIKE needs no escaping.
     * Shortest names first, because the ranker prefers them and the limit would
     * otherwise cut them off. A scan of about 8,000 short rows takes a few milliseconds.
     */
    @Query(
        """
        SELECT * FROM ref_food
        WHERE name LIKE '%' || :a || '%'
          AND (:b = '' OR name LIKE '%' || :b || '%')
          AND (:c = '' OR name LIKE '%' || :c || '%')
          AND (:d = '' OR name LIKE '%' || :d || '%')
        ORDER BY LENGTH(name), name
        LIMIT :limit
        """,
    )
    suspend fun candidates(a: String, b: String, c: String, d: String, limit: Int): List<RefFoodEntity>

    @Query("SELECT * FROM ref_food WHERE id = :id")
    suspend fun byId(id: Int): RefFoodEntity?

    @Query("SELECT COUNT(*) FROM ref_food")
    suspend fun count(): Int
}

/**
 * The bundled, read-only reference database. It is a SEPARATE database from
 * `intake.db` on purpose: the user's data never migrates because of it, and a new
 * dataset is just a new asset file. Nothing here is ever written, so replacing it
 * (see `fallbackToDestructiveMigration` in [com.zeroindex.intake.data.di.FoodRefModule])
 * cannot lose anything of the user's.
 */
@Database(entities = [RefFoodEntity::class], version = FoodRefDatabase.VERSION, exportSchema = true)
abstract class FoodRefDatabase : RoomDatabase() {
    abstract fun refFoodDao(): RefFoodDao

    companion object {
        const val VERSION = 1
        const val NAME = "food_ref.db"
        const val ASSET = "food_ref_v1.db"
    }
}
