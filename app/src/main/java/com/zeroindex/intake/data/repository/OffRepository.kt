package com.zeroindex.intake.data.repository

import android.util.Log
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.remote.Connectivity
import com.zeroindex.intake.data.remote.openfoodfacts.FoodIngredients
import com.zeroindex.intake.data.remote.openfoodfacts.OffContribution
import com.zeroindex.intake.data.remote.openfoodfacts.OffContributionForm
import com.zeroindex.intake.data.remote.openfoodfacts.OffContributionService
import com.zeroindex.intake.data.remote.openfoodfacts.OffMapping
import com.zeroindex.intake.data.remote.openfoodfacts.OffSearchService
import com.zeroindex.intake.data.remote.openfoodfacts.OffWriteResponse
import com.zeroindex.intake.data.settings.FoodDataPrefs
import com.zeroindex.intake.BuildConfig
import com.zeroindex.intake.domain.food.OffLanguage
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

sealed interface OffSearchOutcome {
    data class Results(val foods: List<FoodEntity>) : OffSearchOutcome

    /** The section is hidden: switched off in settings, or the phone is offline. */
    data object Unavailable : OffSearchOutcome

    data object Failed : OffSearchOutcome
}

/**
 * Open Food Facts search (docs/FOOD-DATA.md). The only thing sent is the query text,
 * plus the User-Agent the shared client adds. Results are not saved until one is chosen.
 */
@Singleton
class OffSearchRepository @Inject constructor(
    private val service: OffSearchService,
    private val prefs: FoodDataPrefs,
    private val connectivity: Connectivity,
    private val foodRepository: FoodRepository,
) {
    suspend fun search(query: String): OffSearchOutcome {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY) return OffSearchOutcome.Results(emptyList())
        val settings = prefs.settings.first()
        if (!settings.offSearch || !connectivity.isOnline()) return OffSearchOutcome.Unavailable
        val language = Locale.getDefault().language.ifBlank { "en" }
        return try {
            // The user's language, then English, are searched and returned; results whose name is in a script
            // the user does not read are left out unless "Show products in other languages" is on.
            val hits = service.search(
                query = trimmed,
                fields = OffLanguage.searchFields(language, OffSearchService.FIELDS),
                langs = OffLanguage.searchLanguages(language).joinToString(","),
            ).hits
            val foods = hits
                .mapNotNull { OffMapping.fromHit(it, language) }
                .filter { it.readable || settings.offOtherLanguages }
                .map { it.food }
                .distinctBy { it.barcode }
            OffSearchOutcome.Results(foods)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Open Food Facts search failed", e)
            OffSearchOutcome.Failed
        } catch (e: Exception) {
            Log.w(TAG, "Open Food Facts search failed (${e.javaClass.simpleName})")
            OffSearchOutcome.Failed
        }
    }

    /**
     * Saves a chosen result to the user's foods. A barcode already on the phone
     * returns the existing food, so the user's edits and history are not replaced.
     */
    suspend fun adopt(food: FoodEntity): FoodEntity {
        val barcode = food.barcode
        if (barcode != null) foodRepository.findByBarcode(barcode)?.let { return it }
        return foodRepository.save(food)
    }

    companion object {
        const val MIN_QUERY = 3
        private const val TAG = "OffSearchRepository"
    }
}

sealed interface ContributionOutcome {
    data object Sent : ContributionOutcome
    data object NoCredentials : ContributionOutcome
    data object NotEligible : ContributionOutcome

    /** The Settings switch is off. */
    data object NotEnabled : ContributionOutcome

    /** Open Food Facts answered but refused, e.g. a wrong password. [message] is theirs, shown as-is. */
    data class Rejected(val message: String) : ContributionOutcome
    data class Failed(val message: String) : ContributionOutcome
}

/**
 * Contributing a user-made product to Open Food Facts. Opt-in, one product at a
 * time, and only after the user has seen [preview] and confirmed. Never called
 * from anywhere that runs on its own.
 */
@Singleton
class OffContributionRepository @Inject constructor(
    private val service: OffContributionService,
    private val keys: ApiKeyStore,
    private val prefs: FoodDataPrefs,
    private val foodRepository: FoodRepository,
) {
    /** Exactly what [contribute] will send, minus the account name and password. */
    fun preview(food: FoodEntity, quantity: String?): OffContribution =
        OffContributionForm.build(food, quantity, FoodIngredients.text(food.allergenTags))

    suspend fun contribute(food: FoodEntity, quantity: String?): ContributionOutcome {
        if (!prefs.settings.first().offContribute) return ContributionOutcome.NotEnabled
        if (!OffContributionForm.isEligible(food)) return ContributionOutcome.NotEligible
        val user = keys.offUsername()
        val password = keys.offPassword()
        if (user == null || password == null) return ContributionOutcome.NoCredentials

        val body = preview(food, quantity).toFormBody(user, password, BuildConfig.VERSION_NAME)
        return try {
            val outcome = interpret(service.submit(body))
            if (outcome is ContributionOutcome.Sent) foodRepository.markContributed(food.id)
            outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            // Only the status code is logged: never the request, and never the credentials.
            Log.w(TAG, "Open Food Facts contribution failed with HTTP ${e.code()}")
            ContributionOutcome.Failed("Open Food Facts answered with an error (${e.code()}). Nothing was changed.")
        } catch (e: IOException) {
            ContributionOutcome.Failed("No connection. Nothing was sent.")
        } catch (e: Exception) {
            Log.w(TAG, "Open Food Facts contribution failed (${e.javaClass.simpleName})")
            ContributionOutcome.Failed("Open Food Facts sent back something unexpected. Check your account and try again.")
        }
    }

    companion object {
        private const val TAG = "OffContribution"

        fun interpret(response: OffWriteResponse): ContributionOutcome =
            if (response.accepted) {
                ContributionOutcome.Sent
            } else {
                ContributionOutcome.Rejected(
                    response.statusVerbose?.takeIf { it.isNotBlank() }
                        ?: "Open Food Facts did not accept the product.",
                )
            }
    }
}
