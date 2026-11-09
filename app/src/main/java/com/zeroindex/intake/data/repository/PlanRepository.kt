package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.PlanDao
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.dao.ShoppingListDao
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.ShoppingListItemEntity
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class PlanRepository @Inject constructor(
    private val planDao: PlanDao,
    private val recipeDao: RecipeDao,
    private val shoppingListDao: ShoppingListDao,
    private val clock: Clock,
) {
    fun observeAllEatOutOptions(): Flow<List<EatOutOptionEntity>> = planDao.observeAllEatOutOptions()

    /** SPEC §9: current week's options plus the ones that apply to every week. */
    fun observeEatOutOptions(planWeekId: String?): Flow<List<EatOutOptionEntity>> =
        planDao.observeEatOutOptions(planWeekId)

    fun observeShoppingList(planWeekId: String): Flow<List<ShoppingListItemEntity>> =
        shoppingListDao.observeForWeek(planWeekId)

    fun observeRecipes(): Flow<List<RecipeEntity>> = recipeDao.observeAll()

    suspend fun recipe(id: String): RecipeEntity? = recipeDao.findById(id)
    suspend fun weekRecipes(planWeekId: String) = planDao.weekRecipes(planWeekId)
    suspend fun ingredients(recipeId: String) = recipeDao.ingredientsOf(recipeId)

    suspend fun setChecked(itemId: String, checked: Boolean) =
        shoppingListDao.setChecked(itemId, checked, clock.nowMillis())

    /** SPEC §9: check state resets when the week changes. */
    suspend fun resetChecks(planWeekId: String) =
        shoppingListDao.resetChecks(planWeekId, clock.nowMillis())

    suspend fun addShoppingItem(item: ShoppingListItemEntity) = shoppingListDao.upsert(item)

    suspend fun softDeleteShoppingItem(id: String) =
        shoppingListDao.softDelete(id, clock.nowMillis())
}
