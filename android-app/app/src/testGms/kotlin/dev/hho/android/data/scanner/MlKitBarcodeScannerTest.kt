package dev.hho.android.data.scanner

import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MlKitBarcodeScannerTest {

    @Test
    fun `maps every ML Kit format this project names`() {
        assertEquals(BarcodeFormat.QR_CODE, mapMlKitFormat(Barcode.FORMAT_QR_CODE))
        assertEquals(BarcodeFormat.AZTEC, mapMlKitFormat(Barcode.FORMAT_AZTEC))
        assertEquals(BarcodeFormat.DATA_MATRIX, mapMlKitFormat(Barcode.FORMAT_DATA_MATRIX))
        assertEquals(BarcodeFormat.PDF_417, mapMlKitFormat(Barcode.FORMAT_PDF417))
        assertEquals(BarcodeFormat.CODE_39, mapMlKitFormat(Barcode.FORMAT_CODE_39))
        assertEquals(BarcodeFormat.CODE_93, mapMlKitFormat(Barcode.FORMAT_CODE_93))
        assertEquals(BarcodeFormat.CODE_128, mapMlKitFormat(Barcode.FORMAT_CODE_128))
        assertEquals(BarcodeFormat.CODABAR, mapMlKitFormat(Barcode.FORMAT_CODABAR))
        assertEquals(BarcodeFormat.ITF, mapMlKitFormat(Barcode.FORMAT_ITF))
        assertEquals(BarcodeFormat.EAN_8, mapMlKitFormat(Barcode.FORMAT_EAN_8))
        assertEquals(BarcodeFormat.EAN_13, mapMlKitFormat(Barcode.FORMAT_EAN_13))
        assertEquals(BarcodeFormat.UPC_A, mapMlKitFormat(Barcode.FORMAT_UPC_A))
        assertEquals(BarcodeFormat.UPC_E, mapMlKitFormat(Barcode.FORMAT_UPC_E))
    }

    @Test
    fun `falls back to UNKNOWN for a format this project does not name`() {
        assertEquals(BarcodeFormat.UNKNOWN, mapMlKitFormat(Barcode.FORMAT_UNKNOWN))
        assertEquals(BarcodeFormat.UNKNOWN, mapMlKitFormat(Barcode.FORMAT_ALL_FORMATS))
        assertEquals(BarcodeFormat.UNKNOWN, mapMlKitFormat(-1))
    }

    @Test
    fun `maps a detection with a non-null raw value into a DecodedBarcode`() {
        val result = mapMlKitBarcode(rawValue = "0012345678905", mlKitFormat = Barcode.FORMAT_EAN_13)

        assertEquals(DecodedBarcode("0012345678905", BarcodeFormat.EAN_13), result)
    }

    @Test
    fun `drops a detection with a null raw value instead of reporting a placeholder`() {
        assertNull(mapMlKitBarcode(rawValue = null, mlKitFormat = Barcode.FORMAT_QR_CODE))
    }

    @Test
    fun `scan returns an empty list and does not close the proxy when the frame has no backing image`() =
        runTest {
            val scanner = MlKitBarcodeScanner()
            val imageProxy = NoImageFakeImageProxy()

            val result = scanner.scan(imageProxy)

            assertEquals(emptyList<DecodedBarcode>(), result)
            assertEquals(0, imageProxy.closeCount)
        }
}

private class NoImageFakeImageProxy : ImageProxy {
    var closeCount = 0
        private set

    override fun close() {
        closeCount++
    }

    override fun getCropRect() = throw UnsupportedOperationException("not exercised by this test")

    override fun setCropRect(rect: android.graphics.Rect?) = Unit

    override fun getFormat(): Int = 0

    override fun getHeight(): Int = 0

    override fun getWidth(): Int = 0

    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = emptyArray()

    override fun getImageInfo(): ImageInfo = throw UnsupportedOperationException("not exercised by this test")

    override fun getImage(): android.media.Image? = null
}
