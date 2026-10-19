package it.cgd1.sveglia.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.cgd1.sveglia.data.AppCompatLanguageController
import it.cgd1.sveglia.data.BleClockRepository
import it.cgd1.sveglia.data.ClockRepository
import it.cgd1.sveglia.data.LanguageController
import it.cgd1.sveglia.data.ringtone.AndroidRingtoneRepository
import it.cgd1.sveglia.data.ringtone.AudioTrackRingtonePlayer
import it.cgd1.sveglia.data.ringtone.RingtonePlayer
import it.cgd1.sveglia.data.ringtone.RingtoneRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindClockRepository(impl: BleClockRepository): ClockRepository

    @Binds
    abstract fun bindRingtoneRepository(impl: AndroidRingtoneRepository): RingtoneRepository

    @Binds
    abstract fun bindRingtonePlayer(impl: AudioTrackRingtonePlayer): RingtonePlayer

    @Binds
    abstract fun bindLanguageController(impl: AppCompatLanguageController): LanguageController

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                // La 0.1 salvava la sveglia nelle SharedPreferences "cgd1", con le stesse chiavi.
                migrations = listOf(SharedPreferencesMigration(context, "cgd1")),
                produceFile = { context.preferencesDataStoreFile("cgd1") },
            )

        @Provides
        @DefaultDispatcher
        fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
