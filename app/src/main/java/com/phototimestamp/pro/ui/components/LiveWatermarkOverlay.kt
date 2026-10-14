package com.phototimestamp.pro.ui.components

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.phototimestamp.pro.model.*
import com.phototimestamp.pro.util.FormatUtils
import com.phototimestamp.pro.util.WatermarkRenderData
import com.phototimestamp.pro.util.WatermarkRenderer
import kotlinx.coroutines.delay

/**
 * Hardware-accelerated Live Watermark Overlay.
 * Updates only the Canvas Draw phase on clock ticks — zero Layout or Composition overhead!
 * Also supports direct interactive finger drag-and-drop to reposition the watermark.
 */
@Composable
fun LiveWatermarkOverlay(
    template: WatermarkTemplate,
    settings: AppSettings,
    gpsState: GpsLocationState,
    project: ProjectItem,
    docNumber: String,
    signatureBitmap: Bitmap?,
    customLogoBitmap: Bitmap?,
    deviceOrientationDegrees: Int = 0,
    bottomSafeInsetPx: Float = 0f,
    topSafeInsetPx: Float = 0f,
    enableDragAndDrop: Boolean = false,
    onDragPositionChange: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val needFastTick = template.showMilliseconds || settings.showMilliseconds
    LaunchedEffect(needFastTick) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(if (needFastTick) 90L else 500L)
        }
    }

    val uniqueCode = remember(settings.uniqueCodePrefix, gpsState.latitude, gpsState.longitude, currentTimeMillis / 60000L) {
        FormatUtils.generateUniqueCode(
            settings.uniqueCodePrefix,
            currentTimeMillis,
            gpsState.latitude,
            gpsState.longitude
        )
    }

    val shaPreview = remember(docNumber, uniqueCode) {
        FormatUtils.computeSha256Hex("LIVE|$docNumber|$uniqueCode".toByteArray())
    }

    var lastBoxRect by remember { mutableStateOf(RectF()) }
    var canvasW by remember { mutableFloatStateOf(1f) }
    var canvasH by remember { mutableFloatStateOf(1f) }

    val dragModifier = if (enableDragAndDrop && onDragPositionChange != null) {
        Modifier.pointerInput(template.id) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                val newCenterX = (lastBoxRect.centerX() + dragAmount.x).coerceIn(0f, canvasW)
                val newCenterY = (lastBoxRect.centerY() + dragAmount.y).coerceIn(0f, canvasH)
                val fracX = (newCenterX / canvasW.coerceAtLeast(1f)).coerceIn(0.02f, 0.98f)
                val fracY = (newCenterY / canvasH.coerceAtLeast(1f)).coerceIn(0.02f, 0.98f)
                onDragPositionChange(fracX, fracY)
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(dragModifier)
    ) {
        canvasW = size.width
        canvasH = size.height

        // Reading currentTimeMillis inside DrawScope only invalidates the draw layer!
        val renderData = WatermarkRenderData(
            timestampMillis = currentTimeMillis,
            gpsState = gpsState,
            projectName = project.name,
            projectCode = project.code,
            docNumber = docNumber,
            uniqueCode = uniqueCode,
            sha256Preview = shaPreview,
            signatureBitmap = signatureBitmap,
            customLogoBitmap = customLogoBitmap
        )

        lastBoxRect = WatermarkRenderer.drawWatermarkOnCanvas(
            canvas = drawContext.canvas.nativeCanvas,
            canvasWidth = size.width,
            canvasHeight = size.height,
            template = template,
            settings = settings,
            data = renderData,
            deviceOrientationDegrees = deviceOrientationDegrees,
            bottomSafeInsetPx = bottomSafeInsetPx,
            topSafeInsetPx = topSafeInsetPx
        )
    }
}
