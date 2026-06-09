// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.uallsi.medaboutyou.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen camera scanner. Recognises a package barcode / FMD Datamatrix
 * on-device (bundled ML Kit) and hands the raw value back via [onResult] once.
 */
@Composable
fun ScanScreen(onResult: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnResult by rememberUpdatedState(onResult)

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            val handled = remember { AtomicBoolean(false) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val scanner = BarcodeScanning.getClient()
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(
                            ContextCompat.getMainExecutor(ctx),
                            BarcodeAnalyzer(scanner, handled) { raw -> latestOnResult(raw) },
                        )
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                            )
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )
            Text(
                stringResource(R.string.scan_hint),
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.scan_permission_needed),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.scan_grant_camera))
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel), tint = Color.White)
        }
    }
}

/**
 * Feeds frames to ML Kit and reports the first barcode. The CameraX opt-in is
 * confined to [analyze] (the marker annotation, which Android Lint accepts).
 */
private class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val handled: AtomicBoolean,
    private val onCode: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    @ExperimentalGetImage
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || handled.get()) {
            proxy.close(); return
        }
        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                val raw = codes.firstOrNull()?.rawValue
                if (raw != null && handled.compareAndSet(false, true)) onCode(raw)
            }
            .addOnCompleteListener { proxy.close() }
    }
}
