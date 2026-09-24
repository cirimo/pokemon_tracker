package net.pokedex

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.pokedex.core.model.backup.AppInfo

/**
 * What only :app knows. A backup records the version that wrote it, and BuildConfig lives
 * here, not in :core:data.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun appInfo(): AppInfo = AppInfo(versionName = BuildConfig.VERSION_NAME, versionCode = BuildConfig.VERSION_CODE)
}
