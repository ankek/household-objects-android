package dev.hho.android.data.scanner

import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitBarcodeScanner
    @Inject
    constructor() : BarcodeScanner {

        private val client: com.google.mlkit.vision.barcode.BarcodeScanner by lazy { BarcodeScanning.getClient() }

        override suspend fun scan(imageProxy: ImageProxy): List<DecodedBarcode> {
            val mediaImage = imageProxy.image ?: return emptyList()
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val barcodes = client.process(inputImage).await()
            return barcodes.mapNotNull { mapMlKitBarcode(it.rawValue, it.format) }
        }

        override fun close() {
            client.close()
        }
    }

internal fun mapMlKitBarcode(rawValue: String?, mlKitFormat: Int): DecodedBarcode? =
    rawValue?.let { DecodedBarcode(it, mapMlKitFormat(mlKitFormat)) }

internal fun mapMlKitFormat(mlKitFormat: Int): BarcodeFormat =
    when (mlKitFormat) {
        Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
        Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
        Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
        Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
        Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
        Barcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
        Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
        Barcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
        Barcode.FORMAT_ITF -> BarcodeFormat.ITF
        Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
        Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
        Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
        Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
        else -> BarcodeFormat.UNKNOWN
    }

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
