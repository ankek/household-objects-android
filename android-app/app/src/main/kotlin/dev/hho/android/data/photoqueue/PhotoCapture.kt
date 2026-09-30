package dev.hho.android.data.photoqueue

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface PhotoCapture {
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    )

    fun unbind()

    suspend fun capture(): File
}

internal class CameraXPhotoCapture
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PhotoCapture {
        private var provider: ProcessCameraProvider? = null
        private var imageCapture: ImageCapture? = null

        override suspend fun bind(
            lifecycleOwner: LifecycleOwner,
            surfaceProvider: Preview.SurfaceProvider,
        ) {
            val cameraProvider = awaitProvider()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
            val capture =
                ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            provider = cameraProvider
            imageCapture = capture
        }

        override fun unbind() {
            provider?.unbindAll()
            provider = null
            imageCapture = null
        }

        override suspend fun capture(): File {
            val useCase = imageCapture ?: throw IOException("The camera is not ready")
            val dir = File(context.cacheDir, "photo-capture").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    useCase.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) = continuation.resume(Unit)

                            override fun onError(exception: ImageCaptureException) =
                                continuation.resumeWithException(IOException("Capture failed", exception))
                        },
                    )
                }
            } catch (e: Throwable) {
                file.delete()
                throw e
            }
            return file
        }

        private suspend fun awaitProvider(): ProcessCameraProvider =
            suspendCancellableCoroutine { continuation ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    {
                        try {
                            continuation.resume(future.get())
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
                continuation.invokeOnCancellation { future.cancel(false) }
            }
    }
