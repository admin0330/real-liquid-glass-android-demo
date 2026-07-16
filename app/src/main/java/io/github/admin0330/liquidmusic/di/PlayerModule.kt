package io.github.admin0330.liquidmusic.di

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.SessionToken
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.admin0330.liquidmusic.player.MusicService
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.VALUE_PARAMETER,
)
annotation class PlaybackSessionToken

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.VALUE_PARAMETER,
)
annotation class PlaybackApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @Provides
    @Singleton
    @PlaybackSessionToken
    fun providePlaybackSessionToken(
        @ApplicationContext context: Context,
    ): SessionToken = SessionToken(
        context,
        ComponentName(context, MusicService::class.java),
    )

    @Provides
    @Singleton
    @PlaybackApplicationScope
    fun providePlaybackApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
