package it.cgd1.sveglia.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedClockStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val savedClock: Flow<SavedClock?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY_ADDRESS]?.let { SavedClock(it, prefs[KEY_NAME]) } }

    suspend fun save(clock: SavedClock) {
        dataStore.edit { prefs ->
            prefs[KEY_ADDRESS] = clock.address
            if (clock.name != null) prefs[KEY_NAME] = clock.name else prefs.remove(KEY_NAME)
        }
    }

    private companion object {
        val KEY_ADDRESS = stringPreferencesKey("address")
        val KEY_NAME = stringPreferencesKey("name")
    }
}
