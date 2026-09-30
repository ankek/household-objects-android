package dev.hho.android.ui.scan

import androidx.camera.core.ImageProxy
import dev.hho.android.data.scanner.BarcodeScanner
import dev.hho.android.data.scanner.DecodedBarcode

class PausableBarcodeScanner(
    private val delegate: BarcodeScanner,
    private val isPaused: () -> Boolean,
) : BarcodeScanner {
    override suspend fun scan(imageProxy: ImageProxy): List<DecodedBarcode> =
        if (isPaused()) emptyList() else delegate.scan(imageProxy)

    override fun close() = Unit
}
