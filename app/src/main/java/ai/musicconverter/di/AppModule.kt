package ai.musicconverter.di

import android.content.Context
import ai.musicconverter.data.MusicFileScanner
import ai.musicconverter.data.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMusicFileScanner(
        @ApplicationContext context: Context
    ): MusicFileScanner = MusicFileScanner(context)

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager = PreferencesManager(context)
}
