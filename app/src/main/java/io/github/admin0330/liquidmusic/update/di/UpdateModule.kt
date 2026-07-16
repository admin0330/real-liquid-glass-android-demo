package io.github.admin0330.liquidmusic.update.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.admin0330.liquidmusic.update.network.HttpsUpdateHttpClient
import io.github.admin0330.liquidmusic.update.network.UpdateHttpClient
import io.github.admin0330.liquidmusic.update.platform.AndroidApkIdentityVerifier
import io.github.admin0330.liquidmusic.update.platform.AndroidInstallIntentFactory
import io.github.admin0330.liquidmusic.update.platform.AndroidInstalledAppInfoProvider
import io.github.admin0330.liquidmusic.update.platform.ApkIdentityVerifier
import io.github.admin0330.liquidmusic.update.platform.InstallIntentFactory
import io.github.admin0330.liquidmusic.update.platform.InstalledAppInfoProvider
import io.github.admin0330.liquidmusic.update.repository.DefaultUpdateRepository
import io.github.admin0330.liquidmusic.update.repository.PreferencesUpdateManifestUrlProvider
import io.github.admin0330.liquidmusic.update.repository.UpdateManifestUrlProvider
import io.github.admin0330.liquidmusic.update.repository.UpdateRepository
import io.github.admin0330.liquidmusic.update.storage.UpdateFileStore
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdateIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds
    abstract fun bindRepository(implementation: DefaultUpdateRepository): UpdateRepository

    @Binds
    abstract fun bindManifestUrlProvider(
        implementation: PreferencesUpdateManifestUrlProvider,
    ): UpdateManifestUrlProvider

    @Binds
    abstract fun bindHttpClient(implementation: HttpsUpdateHttpClient): UpdateHttpClient

    @Binds
    abstract fun bindInstalledAppInfo(
        implementation: AndroidInstalledAppInfoProvider,
    ): InstalledAppInfoProvider

    @Binds
    abstract fun bindApkIdentityVerifier(
        implementation: AndroidApkIdentityVerifier,
    ): ApkIdentityVerifier

    @Binds
    abstract fun bindInstallIntentFactory(
        implementation: AndroidInstallIntentFactory,
    ): InstallIntentFactory

    companion object {
        @Provides
        @Singleton
        fun provideFileStore(@ApplicationContext context: Context): UpdateFileStore =
            UpdateFileStore(context.filesDir.resolve("updates"))

        @Provides
        @UpdateIoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
