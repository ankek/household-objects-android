package dev.hho.android.data.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class BarcodeFrameAnalyzer(
    private val scanner: BarcodeScanner,
    private val analyzerScope: CoroutineScope,
    private val onBarcodesDetected: (List<DecodedBarcode>) -> Unit,
    private val onScanError: (Throwable) -> Unit = {},
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        analyzerScope.launch {
            try {
                val results = scanner.scan(imageProxy)
                if (results.isNotEmpty()) {
                    onBarcodesDetected(results)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                onScanError(failure)
            } finally {
                imageProxy.close()
            }
        }
    }
}
