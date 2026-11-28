package com.zeroindex.intake.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.foodDataStore: DataStore<Preferences> by preferencesDataStore(name = "intake_food_data")

/**
 * The switches and the language choice for the food database features (docs/FOOD-DATA.md). Kept in their
 * own small store so they do not touch [SettingsRepository].
 *
 * - [offSearch]: search Open Food Facts while typing in Add food. Sends the query
 *   text only. On by default, like the barcode lookup it sits beside, and easy to
 *   turn off.
 * - [offContribute]: show the "Contribute to Open Food Facts" action. Off by default.
 *   Even when on, nothing is ever sent without a confirmation each time.
 */
data class FoodDataSettings(
    val offSearch: Boolean = true,
    val offContribute: Boolean = false,
    /** Show Open Food Facts products whose name is in another language or script. Off by default. */
    val offOtherLanguages: Boolean = false,
    /**
     * The "Food search language": an ISO 639-1 code from `OffLanguage.CHOICES`, or blank for the phone's language.
     * English is always searched as well.
     */
    val offLanguage: String = "",
)

@Singleton
class FoodDataPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val offSearch = booleanPreferencesKey("off_search_enabled")
        val offContribute = booleanPreferencesKey("off_contribute_enabled")
        val offOtherLanguages = booleanPreferencesKey("off_other_languages")
        val offLanguage = stringPreferencesKey("off_language")
    }

    val settings: Flow<FoodDataSettings> = context.foodDataStore.data.map { p ->
        FoodDataSettings(
            offSearch = p[Keys.offSearch] ?: true,
            offContribute = p[Keys.offContribute] ?: false,
            offOtherLanguages = p[Keys.offOtherLanguages] ?: false,
            offLanguage = p[Keys.offLanguage].orEmpty(),
        )
    }

    suspend fun setOffSearch(enabled: Boolean) {
        context.foodDataStore.edit { it[Keys.offSearch] = enabled }
    }

    suspend fun setOffOtherLanguages(enabled: Boolean) {
        context.foodDataStore.edit { it[Keys.offOtherLanguages] = enabled }
    }

    /** Blank means "Phone language". */
    suspend fun setOffLanguage(code: String) {
        context.foodDataStore.edit { it[Keys.offLanguage] = code.trim().lowercase() }
    }

    suspend fun setOffContribute(enabled: Boolean) {
        context.foodDataStore.edit { it[Keys.offContribute] = enabled }
    }

    suspend fun rawSnapshot(): Map<String, Any?> = context.foodDataStore.rawSnapshot()

    suspend fun restorePortable(values: Map<String, com.zeroindex.intake.domain.backup.PortableSettings.Value>) =
        context.foodDataStore.restore(values)
}
