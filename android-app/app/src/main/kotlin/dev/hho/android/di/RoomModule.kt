package dev.hho.android.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.hho.android.data.room.AttachmentDao
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemCustomFieldDao
import dev.hho.android.data.room.ItemDao
import dev.hho.android.data.room.ItemIdentificationDao
import dev.hho.android.data.room.ItemLabelDao
import dev.hho.android.data.room.LabelDao
import dev.hho.android.data.room.LocationDao
import dev.hho.android.data.room.MIGRATION_1_2
import dev.hho.android.data.room.ConflictRecordDao
import dev.hho.android.data.room.OutboxDao
import dev.hho.android.data.room.PhotoQueueDao
import dev.hho.android.data.room.ReceivingDao
import dev.hho.android.data.room.SyncRunStateDao
import dev.hho.android.data.room.PurchasedFromBlockDao
import dev.hho.android.data.room.SoldToBlockDao
import dev.hho.android.data.room.StockAdjustmentDao
import dev.hho.android.data.room.SyncStateDao
import dev.hho.android.data.room.WarrantyBlockDao
import javax.inject.Singleton

private const val DATABASE_NAME = "hho.db"

@Module
@InstallIn(SingletonComponent::class)
object RoomModule {

    @Provides
    @Singleton
    fun provideHhoDatabase(
        @ApplicationContext context: Context,
    ): HhoDatabase = Room.databaseBuilder(context, HhoDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideItemDao(database: HhoDatabase): ItemDao = database.itemDao()

    @Provides
    fun provideWarrantyBlockDao(database: HhoDatabase): WarrantyBlockDao = database.warrantyBlockDao()

    @Provides
    fun provideSoldToBlockDao(database: HhoDatabase): SoldToBlockDao = database.soldToBlockDao()

    @Provides
    fun providePurchasedFromBlockDao(database: HhoDatabase): PurchasedFromBlockDao =
        database.purchasedFromBlockDao()

    @Provides
    fun provideItemIdentificationDao(database: HhoDatabase): ItemIdentificationDao =
        database.itemIdentificationDao()

    @Provides
    fun provideItemCustomFieldDao(database: HhoDatabase): ItemCustomFieldDao = database.itemCustomFieldDao()

    @Provides
    fun provideStockAdjustmentDao(database: HhoDatabase): StockAdjustmentDao = database.stockAdjustmentDao()

    @Provides
    fun provideLocationDao(database: HhoDatabase): LocationDao = database.locationDao()

    @Provides
    fun provideLabelDao(database: HhoDatabase): LabelDao = database.labelDao()

    @Provides
    fun provideItemLabelDao(database: HhoDatabase): ItemLabelDao = database.itemLabelDao()

    @Provides
    fun provideAttachmentDao(database: HhoDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideSyncStateDao(database: HhoDatabase): SyncStateDao = database.syncStateDao()

    @Provides
    fun provideOutboxDao(database: HhoDatabase): OutboxDao = database.outboxDao()

    @Provides
    fun provideReceivingDao(database: HhoDatabase): ReceivingDao = database.receivingDao()

    @Provides
    fun providePhotoQueueDao(database: HhoDatabase): PhotoQueueDao = database.photoQueueDao()

    @Provides
    fun provideConflictRecordDao(database: HhoDatabase): ConflictRecordDao = database.conflictRecordDao()

    @Provides
    fun provideSyncRunStateDao(database: HhoDatabase): SyncRunStateDao = database.syncRunStateDao()
}
