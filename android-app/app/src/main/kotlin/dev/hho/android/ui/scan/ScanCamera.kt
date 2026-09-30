package dev.hho.android.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.scanner.BarcodeCameraSession
import dev.hho.android.data.scanner.BarcodeScanner
import dev.hho.android.data.scanner.DecodedBarcode
import javax.inject.Inject

@HiltViewModel
class ScanCameraViewModel
    @Inject
    constructor(
        val session: BarcodeCameraSession,
        val scanner: BarcodeScanner,
    ) : ViewModel()

internal class CameraPermission(
    val granted: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

@Composable
internal fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    return CameraPermission(
        granted = granted,
        request = { launcher.launch(Manifest.permission.CAMERA) },
        openSettings = { context.openAppSettings() },
    )
}

@Composable
internal fun ScanCameraPreview(
    isPaused: () -> Boolean,
    onBarcodes: (List<DecodedBarcode>) -> Unit,
    modifier: Modifier = Modifier,
    cameraViewModel: ScanCameraViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycleOwner) { onDispose { cameraViewModel.session.stop() } }
    LaunchedEffect(previewView) {
        try {
            cameraViewModel.session.start(
                lifecycleOwner = lifecycleOwner,
                scanner = PausableBarcodeScanner(cameraViewModel.scanner, isPaused),
                analyzerScope = scope,
                previewSurfaceProvider = previewView.surfaceProvider,
                onBarcodesDetected = onBarcodes,
                onScanError = { cameraError = "Scanning hit a problem; point the camera at the code again." },
            )
        } catch (failure: Exception) {
            cameraError = "The camera could not be started on this device."
        }
    }
    Column(modifier = modifier) {
        cameraError?.let { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun CameraPermissionDenied(
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Camera access is needed to scan barcodes and QR labels. Nothing is recorded or " +
                "uploaded; frames are decoded on this device. You can still find items with search.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRetry) { Text("Grant camera access") }
        OutlinedButton(onClick = onOpenSettings) { Text("Open app settings") }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
