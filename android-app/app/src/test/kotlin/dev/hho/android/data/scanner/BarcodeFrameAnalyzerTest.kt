package dev.hho.android.data.scanner

import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeFrameAnalyzerTest {

    @Test
    fun `forwards the frame to the scanner and reports a non-empty result`() =
        runTest {
            val expected = listOf(DecodedBarcode("12345", BarcodeFormat.EAN_13))
            val scanner = FakeBarcodeScanner(expected)
            var reported: List<DecodedBarcode>? = null
            val analyzer =
                BarcodeFrameAnalyzer(
                    scanner = scanner,
                    analyzerScope = this,
                    onBarcodesDetected = { reported = it },
                    onScanError = { error("unexpected error: $it") },
                )
            val imageProxy = FakeImageProxy()

            analyzer.analyze(imageProxy)
            advanceUntilIdle()

            assertEquals(1, scanner.scanCallCount)
            assertEquals(expected, reported)
            assertEquals(1, imageProxy.closeCount)
        }

    @Test
    fun `does not report a result when the scanner finds nothing, but still closes the frame once`() =
        runTest {
            val scanner = FakeBarcodeScanner(emptyList())
            var reported: List<DecodedBarcode>? = null
            val analyzer =
                BarcodeFrameAnalyzer(
                    scanner = scanner,
                    analyzerScope = this,
                    onBarcodesDetected = { reported = it },
                )
            val imageProxy = FakeImageProxy()

            analyzer.analyze(imageProxy)
            advanceUntilIdle()

            assertEquals(null, reported)
            assertEquals(1, imageProxy.closeCount)
        }

    @Test
    fun `closes the frame exactly once even when the scanner throws`() =
        runTest {
            val failure = RuntimeException("decode failed")
            val scanner = FakeBarcodeScanner(toThrow = failure)
            var reportedError: Throwable? = null
            val analyzer =
                BarcodeFrameAnalyzer(
                    scanner = scanner,
                    analyzerScope = this,
                    onBarcodesDetected = { error("unexpected result: $it") },
                    onScanError = { reportedError = it },
                )
            val imageProxy = FakeImageProxy()

            analyzer.analyze(imageProxy)
            advanceUntilIdle()

            assertEquals(failure, reportedError)
            assertEquals(1, imageProxy.closeCount)
        }
}

private class FakeBarcodeScanner(
    private val result: List<DecodedBarcode> = emptyList(),
    private val toThrow: Throwable? = null,
) : BarcodeScanner {
    var scanCallCount = 0
        private set

    override suspend fun scan(imageProxy: ImageProxy): List<DecodedBarcode> {
        scanCallCount++
        toThrow?.let { throw it }
        return result
    }

    override fun close() = Unit
}

private class FakeImageProxy : ImageProxy {
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
