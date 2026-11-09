package com.zeroindex.intake.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.zeroindex.intake.domain.backup.PortableSettings
import kotlinx.coroutines.flow.first

/** Every preference as `name -> value`. [PortableSettings.encode] decides which ones may leave the phone. */
internal suspend fun DataStore<Preferences>.rawSnapshot(): Map<String, Any?> =
    data.first().asMap().mapKeys { it.key.name }

/** Writes already-filtered values back. Names that are not portable are skipped again here, as a second guard. */
internal suspend fun DataStore<Preferences>.restore(values: Map<String, PortableSettings.Value>) {
    edit { p ->
        values.forEach { (name, v) ->
            if (!PortableSettings.isPortable(name)) return@forEach
            when (v) {
                is PortableSettings.Value.Str -> p[stringPreferencesKey(name)] = v.v
                is PortableSettings.Value.Bool -> p[booleanPreferencesKey(name)] = v.v
                is PortableSettings.Value.IntV -> p[intPreferencesKey(name)] = v.v
                is PortableSettings.Value.LongV -> p[longPreferencesKey(name)] = v.v
                is PortableSettings.Value.DoubleV -> p[doublePreferencesKey(name)] = v.v
                is PortableSettings.Value.StrSet -> p[stringSetPreferencesKey(name)] = v.v
            }
        }
    }
}
