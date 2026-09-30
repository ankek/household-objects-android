package dev.hho.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.HhoDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object OutboxModule {

    @Provides
    @Singleton
    fun provideUuidV7Generator(): UuidV7Generator = UuidV7Generator()

    @Provides
    @Singleton
    fun provideOutboxRepository(db: HhoDatabase, ids: UuidV7Generator): OutboxRepository =
        OutboxRepository(db, ids)
}
