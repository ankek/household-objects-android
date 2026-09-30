package dev.hho.android.ui.photos

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.hho.android.ui.scan.CameraPermissionDenied
import dev.hho.android.ui.scan.rememberCameraPermission

@Composable
internal fun PhotoCaptureScreen(
    itemId: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoCaptureViewModel = hiltViewModel(),
) {
    val permission = rememberCameraPermission()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    if (!permission.granted) {
        CameraPermissionDenied(
            onRetry = permission.request,
            onOpenSettings = permission.openSettings,
            modifier = modifier,
        )
        TextButton(onClick = onCancel) { Text("Cancel") }
        return
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    LaunchedEffect(previewView) {
        try {
            viewModel.capture.bind(lifecycleOwner, previewView.surfaceProvider)
        } catch (e: Exception) {
            viewModel.cameraFailed()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        TextButton(onClick = onCancel, modifier = Modifier.padding(8.dp)) { Text("Cancel") }
        state.error?.let {
            Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }
        AndroidView(factory = { previewView }, modifier = Modifier.weight(1f).fillMaxWidth())
        Button(
            onClick = { viewModel.shoot(itemId) },
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(if (state.submitting) "Saving..." else "Take photo")
        }
    }
}
