package dev.hho.android.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ItemEntity::class,
        WarrantyBlockEntity::class,
        SoldToBlockEntity::class,
        PurchasedFromBlockEntity::class,
        ItemIdentificationEntity::class,
        ItemCustomFieldEntity::class,
        StockAdjustmentEntity::class,
        LocationEntity::class,
        LabelEntity::class,
        ItemLabelEntity::class,
        AttachmentEntity::class,
        SyncStateEntity::class,
        OutboxMutationEntity::class,
        ReceivingSessionEntity::class,
        ReceivingLineEntity::class,
        PhotoQueueEntryEntity::class,
        ConflictRecordEntity::class,
        SyncRunStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HhoDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    abstract fun warrantyBlockDao(): WarrantyBlockDao

    abstract fun soldToBlockDao(): SoldToBlockDao

    abstract fun purchasedFromBlockDao(): PurchasedFromBlockDao

    abstract fun itemIdentificationDao(): ItemIdentificationDao

    abstract fun itemCustomFieldDao(): ItemCustomFieldDao

    abstract fun stockAdjustmentDao(): StockAdjustmentDao

    abstract fun locationDao(): LocationDao

    abstract fun labelDao(): LabelDao

    abstract fun itemLabelDao(): ItemLabelDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun syncStateDao(): SyncStateDao

    abstract fun outboxDao(): OutboxDao

    abstract fun receivingDao(): ReceivingDao

    abstract fun photoQueueDao(): PhotoQueueDao

    abstract fun conflictRecordDao(): ConflictRecordDao

    abstract fun syncRunStateDao(): SyncRunStateDao
}

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `outbox_mutation` (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`mutation_id` TEXT NOT NULL, `entity_type` TEXT NOT NULL, `entity_id` TEXT NOT NULL, " +
                "`op` TEXT NOT NULL, `base_version` INTEGER NOT NULL, `fields_json` TEXT NOT NULL, " +
                "`state` TEXT NOT NULL, `attempt_count` INTEGER NOT NULL, `last_error` TEXT, " +
                "`last_attempt_at` INTEGER, `created_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outbox_mutation_mutation_id` ON `outbox_mutation` (`mutation_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_mutation_state_seq` ON `outbox_mutation` (`state`, `seq`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_mutation_entity_type_entity_id` ON `outbox_mutation` (`entity_type`, `entity_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `receiving_session` (`id` TEXT NOT NULL, `vendor` TEXT NOT NULL, " +
                "`order_reference` TEXT, `purchased_on` INTEGER, `status` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `completed_at` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receiving_session_status` ON `receiving_session` (`status`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `receiving_line` (`session_id` TEXT NOT NULL, `item_id` TEXT NOT NULL, " +
                "`expected_qty` INTEGER, `received_qty` INTEGER NOT NULL, PRIMARY KEY(`session_id`, `item_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `photo_queue_entry` (`id` TEXT NOT NULL, `item_id` TEXT NOT NULL, " +
                "`file_path` TEXT NOT NULL, `sha256` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, " +
                "`category` TEXT NOT NULL, `state` TEXT NOT NULL, `attempt_count` INTEGER NOT NULL, " +
                "`next_attempt_at` INTEGER, `last_error` TEXT, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_queue_entry_state_created_at` ON `photo_queue_entry` (`state`, `created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_queue_entry_item_id` ON `photo_queue_entry` (`item_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `conflict_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`mutation_id` TEXT NOT NULL, `entity_type` TEXT NOT NULL, `entity_id` TEXT NOT NULL, " +
                "`field_name` TEXT NOT NULL, `losing_value_json` TEXT, `server_value_json` TEXT, " +
                "`detected_at` INTEGER NOT NULL, `origin` TEXT NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conflict_record_mutation_id_entity_id_field_name` ON `conflict_record` (`mutation_id`, `entity_id`, `field_name`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_run_state` (`id` INTEGER NOT NULL, `last_run_at` INTEGER, " +
                "`last_push_at` INTEGER, `last_error` TEXT, `last_error_at` INTEGER, `conflict_cursor` TEXT, " +
                "`reconcile_pending` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
        )
    }
}
