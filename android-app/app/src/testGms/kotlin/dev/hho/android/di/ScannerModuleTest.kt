package dev.hho.android.di

import dagger.Component
import dev.hho.android.data.scanner.BarcodeScanner
import dev.hho.android.data.scanner.MlKitBarcodeScanner
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Singleton

@Singleton
@Component(modules = [ScannerModule::class])
interface ScannerTestComponent {
    fun barcodeScanner(): BarcodeScanner
}

class ScannerModuleTest {

    @Test
    fun `BarcodeScanner resolves to MlKitBarcodeScanner`() {
        val component = DaggerScannerTestComponent.create()

        assertTrue(component.barcodeScanner() is MlKitBarcodeScanner)
    }
}
