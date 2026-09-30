package dev.hho.android.data.scanner

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BarcodeCameraSession
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private var cameraProvider: ProcessCameraProvider? = null
        private var analysisExecutor: ExecutorService? = null

        suspend fun start(
            lifecycleOwner: LifecycleOwner,
            scanner: BarcodeScanner,
            analyzerScope: CoroutineScope,
            previewSurfaceProvider: Preview.SurfaceProvider? = null,
            onBarcodesDetected: (List<DecodedBarcode>) -> Unit,
            onScanError: (Throwable) -> Unit = {},
        ) {
            val provider = ProcessCameraProvider.getInstance(context).await(context)
            cameraProvider = provider

            val executor = Executors.newSingleThreadExecutor()
            analysisExecutor = executor

            val preview =
                Preview.Builder().build().also { useCase ->
                    previewSurfaceProvider?.let { useCase.setSurfaceProvider(it) }
                }
            val analysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(
                            executor,
                            BarcodeFrameAnalyzer(scanner, analyzerScope, onBarcodesDetected, onScanError),
                        )
                    }

            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }

        fun stop() {
            cameraProvider?.unbindAll()
            cameraProvider = null
            analysisExecutor?.shutdown()
            analysisExecutor = null
        }
    }

private suspend fun <T> ListenableFuture<T>.await(context: Context): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (failure: Exception) {
                    continuation.resumeWithException(failure)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        continuation.invokeOnCancellation { cancel(false) }
    }
