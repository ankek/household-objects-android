package dev.hho.android.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.di.DataStoreModule
import dev.hho.android.di.DeviceTokenDataStore
import dev.hho.android.di.NetworkModule
import dev.hho.android.di.ProbeHttpClient
import dev.hho.android.di.RoomModule
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkTripwire @Inject constructor() : Interceptor {
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        requests += "${chain.request().method} ${chain.request().url}"
        throw IOException("NetworkTripwire: network access attempted in an offline test")
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RoomModule::class])
object TestRoomModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HhoDatabase =
        Room.inMemoryDatabaseBuilder(context, HhoDatabase::class.java).allowMainThreadQueries().build()

    @Provides fun itemDao(db: HhoDatabase) = db.itemDao()
    @Provides fun warrantyBlockDao(db: HhoDatabase) = db.warrantyBlockDao()
    @Provides fun soldToBlockDao(db: HhoDatabase) = db.soldToBlockDao()
    @Provides fun purchasedFromBlockDao(db: HhoDatabase) = db.purchasedFromBlockDao()
    @Provides fun itemIdentificationDao(db: HhoDatabase) = db.itemIdentificationDao()
    @Provides fun itemCustomFieldDao(db: HhoDatabase) = db.itemCustomFieldDao()
    @Provides fun stockAdjustmentDao(db: HhoDatabase) = db.stockAdjustmentDao()
    @Provides fun locationDao(db: HhoDatabase) = db.locationDao()
    @Provides fun labelDao(db: HhoDatabase) = db.labelDao()
    @Provides fun itemLabelDao(db: HhoDatabase) = db.itemLabelDao()
    @Provides fun attachmentDao(db: HhoDatabase) = db.attachmentDao()
    @Provides fun syncStateDao(db: HhoDatabase) = db.syncStateDao()
    @Provides fun outboxDao(db: HhoDatabase) = db.outboxDao()
    @Provides fun receivingDao(db: HhoDatabase) = db.receivingDao()
    @Provides fun photoQueueDao(db: HhoDatabase) = db.photoQueueDao()
    @Provides fun conflictRecordDao(db: HhoDatabase) = db.conflictRecordDao()
    @Provides fun syncRunStateDao(db: HhoDatabase) = db.syncRunStateDao()
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkModule::class])
object TestNetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(tripwire: NetworkTripwire): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(tripwire).build()

    @Provides
    @Singleton
    @ProbeHttpClient
    fun provideProbeOkHttpClient(tripwire: NetworkTripwire): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(tripwire).build()
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStoreModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> = fresh(context)

    @Provides
    @Singleton
    @DeviceTokenDataStore
    fun provideDeviceTokenDataStore(@ApplicationContext context: Context): DataStore<Preferences> = fresh(context)

    private fun fresh(context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb") },
        )
}
