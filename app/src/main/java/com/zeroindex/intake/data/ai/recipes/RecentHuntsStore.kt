package com.zeroindex.intake.data.ai.recipes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zeroindex.intake.domain.ai.RecentHunts
import com.zeroindex.intake.domain.ai.SavedHunt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.recentHuntsStore: DataStore<Preferences> by preferencesDataStore(name = "intake_recipe_hunts")

/**
 * Recent Recipe Hunter searches ([RecentHunts]): the parameters, the result counts, the cost, the time and every
 * result's recipe as the page gave it, so a search can be reopened without spending again.
 *
 * Kept in its own small preferences store, one JSON value, not in a Room table (so no schema change) and not in the
 * settings store the backup reads, so it is not in a backup or a sync. It is cache-like: what the pages said at the
 * time. Nothing here is a secret.
 */
@Singleton
class RecentHuntsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("recent_hunts_json")

    /** Live searches, newest first. */
    val recent: Flow<List<SavedHunt>> = context.recentHuntsStore.data.map { RecentHunts.live(RecentHunts.decode(it[key])) }

    /** Saves [hunt] as the newest, or replaces the entry with its id (a "Find more" on a saved search). */
    suspend fun save(hunt: SavedHunt, now: Long) {
        context.recentHuntsStore.edit { it[key] = RecentHunts.encode(RecentHunts.add(RecentHunts.decode(it[key]), hunt, now)) }
    }

    suspend fun softDelete(id: String, now: Long) {
        context.recentHuntsStore.edit { it[key] = RecentHunts.encode(RecentHunts.softDelete(RecentHunts.decode(it[key]), id, now)) }
    }

    suspend fun find(id: String): SavedHunt? = recent.first().firstOrNull { it.id == id }
}
