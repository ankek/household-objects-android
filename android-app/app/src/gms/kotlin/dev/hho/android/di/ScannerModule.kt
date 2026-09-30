package dev.hho.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.hho.android.data.scanner.BarcodeScanner
import dev.hho.android.data.scanner.MlKitBarcodeScanner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {

    @Binds
    @Singleton
    abstract fun bindBarcodeScanner(impl: MlKitBarcodeScanner): BarcodeScanner
}
