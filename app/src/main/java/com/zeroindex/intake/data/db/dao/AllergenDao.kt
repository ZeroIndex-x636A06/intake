package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.AllergenAliasEntity
import com.zeroindex.intake.data.db.entity.AllergenDefinitionEntity
import com.zeroindex.intake.data.db.entity.AllergenProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllergenDao {
    @Upsert suspend fun upsertProfile(profile: AllergenProfileEntity)
    @Upsert suspend fun upsertAliases(aliases: List<AllergenAliasEntity>)
    @Upsert suspend fun upsertAlias(alias: AllergenAliasEntity)
    @Upsert suspend fun upsertDefinition(definition: AllergenDefinitionEntity)

    @Query("SELECT * FROM allergen_profile WHERE id = :id AND deletedAt IS NULL")
    fun observeProfile(id: String = AllergenProfileEntity.SINGLETON_ID): Flow<AllergenProfileEntity?>

    @Query("SELECT * FROM allergen_profile WHERE id = :id AND deletedAt IS NULL")
    suspend fun profile(id: String = AllergenProfileEntity.SINGLETON_ID): AllergenProfileEntity?

    /**
     * The keywords the engine matches against: not deleted and not switched off. SPEC §7.6 needs the
     * reference screen to load instantly from local data, and this table is small enough to hold in
     * memory for matching rather than round-tripping SQL per term.
     */
    @Query("SELECT * FROM allergen_alias WHERE deletedAt IS NULL AND isEnabled = 1 ORDER BY allergen, aliasTerm")
    fun observeAliases(): Flow<List<AllergenAliasEntity>>

    @Query("SELECT * FROM allergen_alias WHERE deletedAt IS NULL AND isEnabled = 1 ORDER BY allergen, aliasTerm")
    suspend fun aliases(): List<AllergenAliasEntity>

    /** Every live keyword including switched-off ones, for the My allergens screens. */
    @Query("SELECT * FROM allergen_alias WHERE deletedAt IS NULL ORDER BY allergen, aliasTerm")
    fun observeAllAliases(): Flow<List<AllergenAliasEntity>>

    /** Including soft-deleted rows, so re-adding a removed keyword revives it instead of colliding. */
    @Query("SELECT * FROM allergen_alias WHERE allergen = :allergen AND aliasTerm = :term LIMIT 1")
    suspend fun aliasIncludingDeleted(allergen: String, term: String): AllergenAliasEntity?

    @Query("SELECT * FROM allergen_alias WHERE id = :id")
    suspend fun aliasById(id: String): AllergenAliasEntity?

    @Query("UPDATE allergen_alias SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteAlias(id: String, now: Long)

    @Query("UPDATE allergen_alias SET deletedAt = :now, updatedAt = :now WHERE allergen = :allergen AND isCustom = 1 AND deletedAt IS NULL")
    suspend fun softDeleteCustomAliasesOf(allergen: String, now: Long)

    @Query("SELECT * FROM allergen_definition WHERE deletedAt IS NULL ORDER BY isCustom, sortOrder, displayName")
    fun observeDefinitions(): Flow<List<AllergenDefinitionEntity>>

    @Query("SELECT * FROM allergen_definition WHERE deletedAt IS NULL ORDER BY isCustom, sortOrder, displayName")
    suspend fun definitions(): List<AllergenDefinitionEntity>

    @Query("SELECT * FROM allergen_definition WHERE name = :name LIMIT 1")
    suspend fun definitionIncludingDeleted(name: String): AllergenDefinitionEntity?

    @Query("UPDATE allergen_definition SET deletedAt = :now, updatedAt = :now WHERE id = :id AND isCustom = 1")
    suspend fun softDeleteDefinition(id: String, now: Long)
}
