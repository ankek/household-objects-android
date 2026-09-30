package dev.hho.android.data.scanner

import androidx.camera.core.ImageProxy
import java.io.Closeable

interface BarcodeScanner : Closeable {
    suspend fun scan(imageProxy: ImageProxy): List<DecodedBarcode>
}
