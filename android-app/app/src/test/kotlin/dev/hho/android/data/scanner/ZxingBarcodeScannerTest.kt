package dev.hho.android.data.scanner

import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import com.google.zxing.BarcodeFormat as ZxingFormat

class ZxingBarcodeScannerTest {

    @Test
    fun `maps every ZXing format this project names`() {
        val expected =
            mapOf(
                ZxingFormat.QR_CODE to BarcodeFormat.QR_CODE,
                ZxingFormat.AZTEC to BarcodeFormat.AZTEC,
                ZxingFormat.DATA_MATRIX to BarcodeFormat.DATA_MATRIX,
                ZxingFormat.PDF_417 to BarcodeFormat.PDF_417,
                ZxingFormat.CODE_39 to BarcodeFormat.CODE_39,
                ZxingFormat.CODE_93 to BarcodeFormat.CODE_93,
                ZxingFormat.CODE_128 to BarcodeFormat.CODE_128,
                ZxingFormat.CODABAR to BarcodeFormat.CODABAR,
                ZxingFormat.ITF to BarcodeFormat.ITF,
                ZxingFormat.EAN_8 to BarcodeFormat.EAN_8,
                ZxingFormat.EAN_13 to BarcodeFormat.EAN_13,
                ZxingFormat.UPC_A to BarcodeFormat.UPC_A,
                ZxingFormat.UPC_E to BarcodeFormat.UPC_E,
            )
        expected.forEach { (zx, ours) -> assertEquals(ours, mapZxingFormat(zx)) }
    }

    @Test
    fun `unnamed or null format maps to UNKNOWN`() {
        assertEquals(BarcodeFormat.UNKNOWN, mapZxingFormat(ZxingFormat.MAXICODE))
        assertEquals(BarcodeFormat.UNKNOWN, mapZxingFormat(null))
    }

    @Test
    fun `null text is dropped and non-null text is mapped`() {
        assertEquals(null, mapZxingBarcode(null, ZxingFormat.QR_CODE))
        assertEquals(DecodedBarcode("x", BarcodeFormat.EAN_13), mapZxingBarcode("x", ZxingFormat.EAN_13))
    }

    @Test
    fun `repack drops row padding and honours pixel stride`() {
        val plane = byteArrayOf(1, 9, 2, 9, 9, 3, 9, 4, 9, 9)
        assertEquals(listOf<Byte>(1, 2, 3, 4), repackLuminance(plane, 2, 2, 5, 2).toList())
    }

    @Test
    fun `rotate 90 clockwise turns a 2x3 into a 3x2`() {
        val (data, w, h) = rotateLuminance(byteArrayOf(1, 2, 3, 4, 5, 6), 2, 3, 90)
        assertEquals(3 to 2, w to h)
        assertEquals(listOf<Byte>(5, 3, 1, 6, 4, 2), data.toList())
        val (d270, _, _) = rotateLuminance(byteArrayOf(1, 2, 3, 4, 5, 6), 2, 3, 270)
        assertEquals(listOf<Byte>(2, 4, 6, 1, 3, 5), d270.toList())
        val (d180, _, _) = rotateLuminance(byteArrayOf(1, 2, 3, 4, 5, 6), 2, 3, 180)
        assertEquals(listOf<Byte>(6, 5, 4, 3, 2, 1), d180.toList())
    }

    @Test
    fun `decodes a real QR through the luminance path`() {
        val q = qrLuminance("HHO-ITEM-42")
        val result = decodeLuminance(q.data, q.size, q.size, q.size, 1, 0)
        assertEquals(listOf(DecodedBarcode("HHO-ITEM-42", BarcodeFormat.QR_CODE)), result)
    }

    @Test
    fun `decodes with row padding, pixel stride and rotation`() {
        val q = qrLuminance("rotated-payload")
        val (ccw, _, _) = rotateLuminance(q.data, q.size, q.size, 270)
        val pixelStride = 2
        val rowStride = q.size * pixelStride + 7
        val strided = ByteArray(rowStride * q.size) { 0x55 }
        for (y in 0 until q.size) for (x in 0 until q.size) {
            strided[y * rowStride + x * pixelStride] = ccw[y * q.size + x]
        }
        val result = decodeLuminance(strided, q.size, q.size, rowStride, pixelStride, 90)
        assertEquals(listOf(DecodedBarcode("rotated-payload", BarcodeFormat.QR_CODE)), result)
    }

    @Test
    fun `blank frame yields an empty list without throwing`() {
        assertTrue(decodeLuminance(ByteArray(64 * 64) { 0x80.toByte() }, 64, 64, 64, 1, 0).isEmpty())
        assertTrue(decodeLuminance(ByteArray(0), 0, 0, 0, 1, 0).isEmpty())
    }

    @Test
    fun `scan decodes a real QR from an ImageProxy and never closes it`() =
        runTest {
            val q = qrLuminance("via-proxy")
            val proxy = ZxingFakeImageProxy(q.data, q.size, q.size, q.size, 1, 0)
            val result = ZxingBarcodeScanner().scan(proxy)
            assertEquals(listOf(DecodedBarcode("via-proxy", BarcodeFormat.QR_CODE)), result)
            assertEquals(0, proxy.closeCount)
        }

    @Test
    fun `scan on an imageless frame with no planes returns empty and does not close`() =
        runTest {
            val proxy = ZxingFakeImageProxy(ByteArray(0), 0, 0, 0, 1, 0, noPlanes = true)
            assertTrue(ZxingBarcodeScanner().scan(proxy).isEmpty())
            assertEquals(0, proxy.closeCount)
        }

    private class Qr(val data: ByteArray, val size: Int)

    private fun qrLuminance(text: String): Qr {
        val matrix =
            QRCodeWriter().encode(
                text,
                ZxingFormat.QR_CODE,
                240,
                240,
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4),
            )
        val size = matrix.width
        val data = ByteArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            data[y * size + x] = if (matrix.get(x, y)) 0x00 else 0xFF.toByte()
        }
        return Qr(data, size)
    }
}

private class ZxingFakeImageProxy(
    y: ByteArray,
    private val w: Int,
    private val h: Int,
    rowStride: Int,
    pixelStride: Int,
    rotation: Int,
    noPlanes: Boolean = false,
) : ImageProxy {
    var closeCount = 0
        private set

    private val plane =
        object : ImageProxy.PlaneProxy {
            override fun getRowStride() = rowStride

            override fun getPixelStride() = pixelStride

            override fun getBuffer(): ByteBuffer = ByteBuffer.wrap(y)
        }
    private val planeArray: Array<ImageProxy.PlaneProxy> = if (noPlanes) emptyArray() else arrayOf(plane)
    private val info =
        object : ImageInfo {
            override fun getRotationDegrees() = rotation

            override fun getTimestamp() = 0L

            override fun getTagBundle() = throw UnsupportedOperationException()

            override fun populateExifData(exifBuilder: androidx.camera.core.impl.utils.ExifData.Builder) = Unit
        }

    override fun close() {
        closeCount++
    }

    override fun getCropRect() = throw UnsupportedOperationException()

    override fun setCropRect(rect: android.graphics.Rect?) = Unit

    override fun getFormat(): Int = 0

    override fun getHeight(): Int = h

    override fun getWidth(): Int = w

    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = planeArray

    override fun getImageInfo(): ImageInfo = info

    override fun getImage(): android.media.Image? = null
}
