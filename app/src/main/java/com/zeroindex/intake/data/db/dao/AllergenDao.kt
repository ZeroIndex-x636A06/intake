package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.AllergenAliasEntity
import com.zeroindex.intake.data.db.entity.AllergenProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllergenDao {
    @Upsert suspend fun upsertProfile(profile: AllergenProfileEntity)
    @Upsert suspend fun upsertAliases(aliases: List<AllergenAliasEntity>)

    @Query("SELECT * FROM allergen_profile WHERE id = :id AND deletedAt IS NULL")
    fun observeProfile(id: String = AllergenProfileEntity.SINGLETON_ID): Flow<AllergenProfileEntity?>

    @Query("SELECT * FROM allergen_profile WHERE id = :id AND deletedAt IS NULL")
    suspend fun profile(id: String = AllergenProfileEntity.SINGLETON_ID): AllergenProfileEntity?

    /**
     * The whole alias table. SPEC §7.6 needs the reference screen to load
     * instantly from local data, and this table is small enough to hold in
     * memory for matching rather than round-tripping SQL per term.
     */
    @Query("SELECT * FROM allergen_alias WHERE deletedAt IS NULL ORDER BY allergen, aliasTerm")
    fun observeAliases(): Flow<List<AllergenAliasEntity>>

    @Query("SELECT * FROM allergen_alias WHERE deletedAt IS NULL ORDER BY allergen, aliasTerm")
    suspend fun aliases(): List<AllergenAliasEntity>

    @Query("UPDATE allergen_alias SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteAlias(id: String, now: Long)
}
