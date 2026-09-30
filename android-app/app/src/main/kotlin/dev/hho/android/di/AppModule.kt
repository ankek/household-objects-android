package dev.hho.android.di

import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.hho.android.BuildConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBuildInfo(): BuildInfo = object : BuildInfo {
        override val applicationId: String = BuildConfig.APPLICATION_ID
        override val versionName: String = BuildConfig.VERSION_NAME
        override val minSdk: Int = Build.VERSION_CODES.O
    }
}
