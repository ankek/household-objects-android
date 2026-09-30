package dev.hho.android.data.scanner

import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.google.zxing.BarcodeFormat as ZxingFormat

class ZxingBarcodeScanner
    @Inject
    constructor() : BarcodeScanner {

        override suspend fun scan(imageProxy: ImageProxy): List<DecodedBarcode> {
            val plane = imageProxy.planes.firstOrNull() ?: return emptyList()
            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val rotation = imageProxy.imageInfo.rotationDegrees
            val buffer = plane.buffer.duplicate().also { it.rewind() }
            val luminance = ByteArray(buffer.remaining()).also { buffer.get(it) }
            return withContext(Dispatchers.Default) {
                decodeLuminance(luminance, width, height, rowStride, pixelStride, rotation)
            }
        }

        override fun close() = Unit
    }

internal fun decodeLuminance(
    yPlane: ByteArray,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    rotationDegrees: Int,
): List<DecodedBarcode> {
    if (width <= 0 || height <= 0) return emptyList()
    val packed = repackLuminance(yPlane, width, height, rowStride, pixelStride)
    val (data, w, h) = rotateLuminance(packed, width, height, rotationDegrees)
    val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
    val hints = mapOf(DecodeHintType.TRY_HARDER to true)
    return decodeSource(source, hints)
        ?: decodeSource(InvertedLuminanceSource(source), hints)
        ?: emptyList()
}

private fun decodeSource(
    source: LuminanceSource,
    hints: Map<DecodeHintType, Any>,
): List<DecodedBarcode>? =
    try {
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
        listOfNotNull(mapZxingBarcode(result.text, result.barcodeFormat))
    } catch (_: NotFoundException) {
        null
    } catch (_: ReaderException) {
        null
    }

internal fun repackLuminance(
    yPlane: ByteArray,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
): ByteArray {
    val out = ByteArray(width * height)
    for (row in 0 until height) {
        val rowStart = row * rowStride
        for (col in 0 until width) {
            val index = rowStart + col * pixelStride
            if (index < yPlane.size) out[row * width + col] = yPlane[index]
        }
    }
    return out
}

internal fun rotateLuminance(
    data: ByteArray,
    width: Int,
    height: Int,
    degrees: Int,
): Triple<ByteArray, Int, Int> =
    when (((degrees % 360) + 360) % 360) {
        90 -> {
            val out = ByteArray(data.size)
            for (y in 0 until height) for (x in 0 until width) out[x * height + (height - 1 - y)] = data[y * width + x]
            Triple(out, height, width)
        }
        180 -> Triple(data.reversedArray(), width, height)
        270 -> {
            val out = ByteArray(data.size)
            for (y in 0 until height) for (x in 0 until width) out[(width - 1 - x) * height + y] = data[y * width + x]
            Triple(out, height, width)
        }
        else -> Triple(data, width, height)
    }

internal fun mapZxingBarcode(
    text: String?,
    format: ZxingFormat?,
): DecodedBarcode? = text?.let { DecodedBarcode(it, mapZxingFormat(format)) }

internal fun mapZxingFormat(format: ZxingFormat?): BarcodeFormat =
    when (format) {
        ZxingFormat.QR_CODE -> BarcodeFormat.QR_CODE
        ZxingFormat.AZTEC -> BarcodeFormat.AZTEC
        ZxingFormat.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
        ZxingFormat.PDF_417 -> BarcodeFormat.PDF_417
        ZxingFormat.CODE_39 -> BarcodeFormat.CODE_39
        ZxingFormat.CODE_93 -> BarcodeFormat.CODE_93
        ZxingFormat.CODE_128 -> BarcodeFormat.CODE_128
        ZxingFormat.CODABAR -> BarcodeFormat.CODABAR
        ZxingFormat.ITF -> BarcodeFormat.ITF
        ZxingFormat.EAN_8 -> BarcodeFormat.EAN_8
        ZxingFormat.EAN_13 -> BarcodeFormat.EAN_13
        ZxingFormat.UPC_A -> BarcodeFormat.UPC_A
        ZxingFormat.UPC_E -> BarcodeFormat.UPC_E
        else -> BarcodeFormat.UNKNOWN
    }
