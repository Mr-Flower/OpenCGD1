package it.cgd1.sveglia.data

import android.util.Log
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

    /** Ricordare la sveglia è una comodità: se il disco non collabora l'app continua a funzionare. */
    suspend fun save(clock: SavedClock) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_ADDRESS] = clock.address
                if (clock.name != null) prefs[KEY_NAME] = clock.name else prefs.remove(KEY_NAME)
            }
        } catch (e: IOException) {
            Log.w("SavedClockStore", "saved clock not persisted", e)
        }
    }

    private companion object {
        val KEY_ADDRESS = stringPreferencesKey("address")
        val KEY_NAME = stringPreferencesKey("name")
    }
}
