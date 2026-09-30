package dev.hho.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import dev.hho.android.data.photoqueue.PhotoPostSyncHook
import dev.hho.android.data.photoqueue.PhotoQueueStatusSource
import dev.hho.android.data.sync.PhotoQueueStatus
import dev.hho.android.data.sync.PostSyncHook

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PhotoSyncHookModule {
    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindPhotoHook(hook: PhotoPostSyncHook): PostSyncHook

    @Binds
    abstract fun bindPhotoStatus(source: PhotoQueueStatusSource): PhotoQueueStatus

    @Multibinds
    abstract fun postSyncHooks(): Set<@JvmSuppressWildcards PostSyncHook>
}
