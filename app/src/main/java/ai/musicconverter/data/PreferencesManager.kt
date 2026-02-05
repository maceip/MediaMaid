package ai.musicconverter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    val autoConvertEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_AUTO_CONVERT_ENABLED] ?: false
    }

    // Keep Original Files: false = delete originals (default), true = keep originals
    val keepOriginalFiles: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_KEEP_ORIGINAL] ?: false // Default: don't keep (delete originals)
    }

    suspend fun setAutoConvertEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTO_CONVERT_ENABLED] = enabled
        }
    }

    suspend fun setKeepOriginalFiles(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_KEEP_ORIGINAL] = enabled
        }
    }

    companion object {
        private val KEY_AUTO_CONVERT_ENABLED = booleanPreferencesKey("auto_convert_enabled")
        private val KEY_KEEP_ORIGINAL = booleanPreferencesKey("keep_original")
    }
}
