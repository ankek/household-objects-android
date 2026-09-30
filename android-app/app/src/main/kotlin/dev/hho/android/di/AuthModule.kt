package dev.hho.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.hho.android.data.auth.AndroidKeystoreDeviceTokenCipher
import dev.hho.android.data.auth.AuthRepositoryImpl
import dev.hho.android.data.auth.DataStoreDeviceIdProvider
import dev.hho.android.data.auth.DefaultDeviceLabelProvider
import dev.hho.android.data.auth.DeviceIdProvider
import dev.hho.android.data.auth.DeviceLabelProvider
import dev.hho.android.data.auth.DeviceTokenCipher
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.auth.DeviceTokenStore
import dev.hho.android.data.auth.InstanceScopedDeviceTokenProvider
import dev.hho.android.data.auth.KeystoreDeviceTokenStore
import dev.hho.android.domain.AuthRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindDeviceTokenProvider(impl: InstanceScopedDeviceTokenProvider): DeviceTokenProvider

    @Binds
    @Singleton
    abstract fun bindDeviceTokenStore(impl: KeystoreDeviceTokenStore): DeviceTokenStore

    @Binds
    @Singleton
    abstract fun bindDeviceTokenCipher(impl: AndroidKeystoreDeviceTokenCipher): DeviceTokenCipher

    @Binds
    @Singleton
    abstract fun bindDeviceLabelProvider(impl: DefaultDeviceLabelProvider): DeviceLabelProvider

    @Binds
    @Singleton
    abstract fun bindDeviceIdProvider(impl: DataStoreDeviceIdProvider): DeviceIdProvider
}
