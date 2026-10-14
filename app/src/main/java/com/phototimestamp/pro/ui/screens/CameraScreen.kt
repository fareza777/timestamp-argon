package com.phototimestamp.pro.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaActionSound
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.phototimestamp.pro.data.AppRepository
import com.phototimestamp.pro.model.*
import com.phototimestamp.pro.ui.components.LiveWatermarkOverlay
import com.phototimestamp.pro.util.ExportAndShareHelper
import com.phototimestamp.pro.util.LocationAndSensorTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun CameraScreen(
    repository: AppRepository,
    locationTracker: LocationAndSensorTracker,
    volumeKeyEvents: SharedFlow<Int>,
    hasCameraPermission: Boolean,
    hasLocationPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenWatermarkEditor: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSignatureDialog: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEvidenceVerifier: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val localView = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val settings by repository.settings.collectAsState()
    val templates by repository.templates.collectAsState()
    val projects by repository.projects.collectAsState()
    val photos by repository.photos.collectAsState()
    val latestThumbBitmap by repository.latestPreviewBitmap.collectAsState()
    val gpsState by locationTracker.gpsState.collectAsState()
    val deviceOrientation by locationTracker.deviceOrientationDegrees.collectAsState()
    val rollAngle by locationTracker.rollAngleDegrees.collectAsState()

    val latestPhoto = photos.firstOrNull()
    var previewModalPhoto by remember { mutableStateOf<PhotoRecord?>(null) }

    val activeTemplate = remember(settings.activeTemplateId, templates) {
        repository.getActiveTemplate()
    }
    val activeProject = remember(settings.activeProjectId, projects) {
        repository.getActiveProject()
    }
    val nextDocNumber = remember(settings.docNumberPrefix, settings.currentDocSequence) {
        repository.peekCurrentDocNumber()
    }

    var isVideoMode by remember { mutableStateOf(false) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    val lensFacing = if (settings.useFrontCamera) {
        CameraSelector.LENS_FACING_FRONT
    } else {
        CameraSelector.LENS_FACING_BACK
    }

    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(8.0f) }
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }

    var isCapturing by remember { mutableStateOf(false) }
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var shutterFlashVisible by remember { mutableStateOf(false) }
    var focusTapPoint by remember { mutableStateOf<Offset?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCaptureUseCase by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    val targetIconRotation = when (deviceOrientation) {
        90 -> -90f
        270 -> 90f
        180 -> 180f
        else -> 0f
    }
    val animatedIconRotation by animateFloatAsState(
        targetValue = targetIconRotation,
        animationSpec = tween(durationMillis = 260),
        label = "iconRotation"
    )

    // Recording duration timer
    LaunchedEffect(isRecordingVideo) {
        if (isRecordingVideo) {
            recordingSeconds = 0
            while (isRecordingVideo) {
                delay(1000L)
                recordingSeconds += 1
            }
        } else {
            recordingSeconds = 0
        }
    }

    // Bind CameraX whenever lensFacing, aspectRatio, or Photo/Video mode changes
    LaunchedEffect(hasCameraPermission, lensFacing, settings.aspectRatioPreset, isVideoMode) {
        if (!hasCameraPermission) return@LaunchedEffect
        runCatching {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val aspectRatioInt = when (settings.aspectRatioPreset) {
                        CameraAspectRatioPreset.RATIO_16_9 -> AspectRatio.RATIO_16_9
                        else -> AspectRatio.RATIO_4_3
                    }

                    val preview = Preview.Builder()
                        .setTargetAspectRatio(aspectRatioInt)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    cameraProvider.unbindAll()

                    val cam = if (isVideoMode) {
                        val recorder = Recorder.Builder()
                            .setQualitySelector(
                                QualitySelector.from(
                                    Quality.HD,
                                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                                )
                            )
                            .build()
                        val vidCapture = VideoCapture.withOutput(recorder)
                        videoCaptureUseCase = vidCapture
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            vidCapture
                        )
                    } else {
                        val imgCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setTargetAspectRatio(aspectRatioInt)
                            .build()
                        imageCaptureUseCase = imgCapture
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            imgCapture
                        )
                    }

                    cameraInstance = cam
                    cam.cameraInfo.zoomState.value?.let { zs ->
                        minZoomRatio = zs.minZoomRatio
                        maxZoomRatio = zs.maxZoomRatio.coerceAtLeast(4f)
                        zoomRatio = zs.zoomRatio
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    // Apply Flash & Torch Mode changes immediately
    LaunchedEffect(cameraInstance, settings.flashMode, isVideoMode) {
        val cam = cameraInstance ?: return@LaunchedEffect
        val capture = imageCaptureUseCase
        runCatching {
            when (settings.flashMode) {
                FlashModeSetting.OFF -> {
                    cam.cameraControl.enableTorch(false)
                    capture?.flashMode = ImageCapture.FLASH_MODE_OFF
                }
                FlashModeSetting.AUTO -> {
                    cam.cameraControl.enableTorch(false)
                    capture?.flashMode = ImageCapture.FLASH_MODE_AUTO
                }
                FlashModeSetting.ON -> {
                    cam.cameraControl.enableTorch(isVideoMode)
                    capture?.flashMode = ImageCapture.FLASH_MODE_ON
                }
                FlashModeSetting.TORCH -> {
                    capture?.flashMode = ImageCapture.FLASH_MODE_OFF
                    cam.cameraControl.enableTorch(true)
                }
            }
        }
    }

    // Auto-hide focus ring
    LaunchedEffect(focusTapPoint) {
        if (focusTapPoint != null) {
            delay(900L)
            focusTapPoint = null
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleVideoRecording() {
        val vidCapture = videoCaptureUseCase ?: return
        val currentRec = activeRecording
        if (isRecordingVideo && currentRec != null) {
            currentRec.stop()
            activeRecording = null
            isRecordingVideo = false
            return
        }

        val name = "VID_PTP_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/PhotoTimestampPro")
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val pending = vidCapture.output
            .prepareRecording(context, mediaStoreOutput)
            .apply {
                if (hasAudioPerm) withAudioEnabled()
            }

        isRecordingVideo = true
        if (settings.hapticFeedbackEnabled) {
            localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    isRecordingVideo = false
                    activeRecording = null
                    if (!event.hasError()) {
                        Toast.makeText(context, "Video tersimpan di Galeri (DCIM/PhotoTimestampPro)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Rekaman selesai", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun triggerPhotoCapture() {
        if (isCapturing) return
        scope.launch {
            isCapturing = true
            if (settings.timerSeconds > 0) {
                for (sec in settings.timerSeconds downTo 1) {
                    countdownRemaining = sec
                    if (settings.hapticFeedbackEnabled) {
                        localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    delay(1000L)
                }
                countdownRemaining = 0
            }

            if (settings.hapticFeedbackEnabled) {
                localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            if (settings.shutterSoundEnabled) {
                runCatching {
                    MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
                }
            }
            shutterFlashVisible = true
            scope.launch {
                delay(100L)
                shutterFlashVisible = false
            }

            val capture = imageCaptureUseCase
            val previewBitmapFallback = runCatching { previewView.bitmap }.getOrNull()

            if (capture != null && hasCameraPermission) {
                capture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            isCapturing = false
                            scope.launch(Dispatchers.Default) {
                                val rawBmp = runCatching {
                                    val bmp = image.toBitmap()
                                    val rot = image.imageInfo.rotationDegrees
                                    if (rot != 0) {
                                        val m = Matrix().apply { postRotate(rot.toFloat()) }
                                        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                    } else {
                                        bmp
                                    }
                                }.getOrElse {
                                    previewBitmapFallback ?: createStudioFallbackBitmap()
                                }
                                image.close()

                                ExportAndShareHelper.processAndSaveCapturedBitmap(
                                    context = context,
                                    rawBitmap = rawBmp,
                                    isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT,
                                    deviceOrientationDegrees = deviceOrientation,
                                    repository = repository,
                                    gpsState = gpsState
                                )
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            isCapturing = false
                            scope.launch(Dispatchers.Default) {
                                val fallbackBmp = previewBitmapFallback ?: createStudioFallbackBitmap()
                                ExportAndShareHelper.processAndSaveCapturedBitmap(
                                    context = context,
                                    rawBitmap = fallbackBmp,
                                    isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT,
                                    deviceOrientationDegrees = deviceOrientation,
                                    repository = repository,
                                    gpsState = gpsState
                                )
                            }
                        }
                    }
                )
            } else {
                val fallbackBmp = previewBitmapFallback ?: createStudioFallbackBitmap()
                ExportAndShareHelper.processAndSaveCapturedBitmap(
                    context = context,
                    rawBitmap = fallbackBmp,
                    isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT,
                    deviceOrientationDegrees = deviceOrientation,
                    repository = repository,
                    gpsState = gpsState
                )
                isCapturing = false
            }
        }
    }

    fun onMainShutterClick() {
        if (isVideoMode) {
            toggleVideoRecording()
        } else {
            triggerPhotoCapture()
        }
    }

    LaunchedEffect(volumeKeyEvents, settings.volumeButtonAction, isVideoMode) {
        volumeKeyEvents.collect { keyCode ->
            when (settings.volumeButtonAction) {
                VolumeButtonAction.SHUTTER -> onMainShutterClick()
                VolumeButtonAction.ZOOM -> {
                    val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 0.4f else -0.4f
                    val nextZoom = (zoomRatio + delta).coerceIn(minZoomRatio, maxZoomRatio)
                    zoomRatio = nextZoom
                    cameraInstance?.cameraControl?.setZoomRatio(nextZoom)
                }
                VolumeButtonAction.NONE -> {}
            }
        }
    }

    val watermarkSafeInsetPx = with(density) { 10.dp.toPx() }
    val watermarkTopInsetPx = with(density) { 16.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080C14))
    ) {
        // 1. TOP CAMERA PREVIEW AREA (Above the Bottom Control Card!)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                val newZoom = (zoomRatio * zoomChange).coerceIn(minZoomRatio, maxZoomRatio)
                                zoomRatio = newZoom
                                cameraInstance?.cameraControl?.setZoomRatio(newZoom)
                            }
                        }
                        .pointerInput(settings.touchToCapture, isVideoMode) {
                            detectTapGestures(
                                onTap = { offset ->
                                    focusTapPoint = offset
                                    runCatching {
                                        val factory = previewView.meteringPointFactory
                                        val point = factory.createPoint(offset.x, offset.y)
                                        val action = FocusMeteringAction.Builder(point).build()
                                        cameraInstance?.cameraControl?.startFocusAndMetering(action)
                                    }
                                    if (settings.touchToCapture && !isVideoMode) {
                                        triggerPhotoCapture()
                                    }
                                }
                            )
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF090D16))
                            )
                        )
                )
            }

            // Optional Grid (OFF by default)
            if (settings.gridType != CameraGridType.NONE || settings.showLevelIndicator) {
                CameraGridAndLevelOverlay(
                    gridType = settings.gridType,
                    showLevel = settings.showLevelIndicator,
                    rollDegrees = rollAngle
                )
            }

            // Tap-to-Focus Reticle
            focusTapPoint?.let { pt ->
                ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = 28.dp.toPx(),
                        center = pt,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // Real-Time Live Watermark Overlay right at the bottom-right corner of the Camera Preview!
            LiveWatermarkOverlay(
                template = activeTemplate,
                settings = settings,
                gpsState = gpsState,
                project = activeProject,
                docNumber = nextDocNumber,
                signatureBitmap = repository.getSignatureBitmap(),
                customLogoBitmap = repository.getCustomLogoBitmap(),
                deviceOrientationDegrees = deviceOrientation,
                topSafeInsetPx = watermarkTopInsetPx,
                bottomSafeInsetPx = watermarkSafeInsetPx,
                enableDragAndDrop = activeTemplate.position == WatermarkPosition.CUSTOM_DRAG,
                onDragPositionChange = { fx, fy ->
                    repository.saveTemplate(
                        activeTemplate.copy(
                            position = WatermarkPosition.CUSTOM_DRAG,
                            customXFraction = fx,
                            customYFraction = fy
                        )
                    )
                }
            )

            // Video Recording Timer Indicator (only visible while recording video)
            if (isRecordingVideo) {
                Surface(
                    color = Color(0xCCDC2626),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp)
                ) {
                    val mins = recordingSeconds / 60
                    val secs = recordingSeconds % 60
                    Text(
                        text = String.format(Locale.US, "● REC  %02d:%02d", mins, secs),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // Permission Banner (if not granted yet)
            if (!hasCameraPermission || !hasLocationPermission) {
                Surface(
                    color = Color(0xDD0F172A),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Izinkan Kamera & Lokasi GPS",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = onRequestPermissions) {
                            Text("Aktifkan Izin")
                        }
                    }
                }
            }

            // Timer Countdown Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = countdownRemaining > 0,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = "$countdownRemaining",
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Shutter Flash Animation
            androidx.compose.animation.AnimatedVisibility(
                visible = shutterFlashVisible,
                enter = fadeIn(animationSpec = tween(35)),
                exit = fadeOut(animationSpec = tween(120))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.7f))
                )
            }
        }

        // 2. BOTTOM CONTROL CARD (Mode Foto/Video, Preview Foto Kiri, Shutter Tengah, & Balik Selfie Kanan)
        Surface(
            color = Color(0xFF0E1525),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row in Bottom Card: Mode Switcher Pill (FOTO | VIDEO) + Subtle Settings Icon on Right
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1E293B))
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (!isVideoMode) Color.White else Color.Transparent)
                                .clickable(enabled = !isRecordingVideo) { isVideoMode = false }
                                .padding(horizontal = 18.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "FOTO",
                                color = if (!isVideoMode) Color.Black else Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isVideoMode) Color(0xFFEF4444) else Color.Transparent)
                                .clickable(enabled = !isCapturing) { isVideoMode = true }
                                .padding(horizontal = 18.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "VIDEO",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .rotate(animatedIconRotation)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pengaturan",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Main Controls Row: [Kotak Preview Hasil Foto]   [SHUTTER]   [Balik ke Selfie]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Small Square Photo Preview Box (Clickable!)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .border(
                                width = 1.5.dp,
                                color = Color.White.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (latestPhoto != null) {
                                    previewModalPhoto = latestPhoto
                                } else {
                                    onOpenGallery()
                                }
                            }
                            .rotate(animatedIconRotation),
                        contentAlignment = Alignment.Center
                    ) {
                        if (latestThumbBitmap != null) {
                            Image(
                                bitmap = latestThumbBitmap!!.asImageBitmap(),
                                contentDescription = "Preview Foto Terakhir",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = "Galeri Foto",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Center: Shutter Button (White for Photo, Red / Stop Square for Video)
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(
                                width = 3.5.dp,
                                color = if (isVideoMode) Color(0xFFEF4444) else Color.White,
                                shape = CircleShape
                            )
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isVideoMode -> Color.Transparent
                                    isCapturing -> Color.LightGray
                                    else -> Color.White
                                }
                            )
                            .clickable(enabled = !isCapturing) { onMainShutterClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVideoMode) {
                            Box(
                                modifier = Modifier
                                    .size(if (isRecordingVideo) 28.dp else 54.dp)
                                    .clip(if (isRecordingVideo) RoundedCornerShape(6.dp) else CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                        } else if (isCapturing) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Right: Flip Front / Back Selfie Camera Button
                    IconButton(
                        onClick = {
                            repository.updateSettings {
                                it.copy(useFrontCamera = !it.useFrontCamera)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .rotate(animatedIconRotation)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Balik ke Selfie",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    // Quick Photo Preview Dialog when user clicks the left thumbnail box (0ms instant from RAM!)
    previewModalPhoto?.let { rec ->
        val fullPreviewBmp = latestThumbBitmap

        AlertDialog(
            onDismissRequest = { previewModalPhoto = null },
            title = {
                Text(
                    text = "Hasil Foto Terakhir",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (fullPreviewBmp != null) {
                        Image(
                            bitmap = fullPreviewBmp!!.asImageBitmap(),
                            contentDescription = rec.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black)
                        )
                    }
                    Text(
                        text = rec.formattedTimestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = rec.address,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            ExportAndShareHelper.sharePhotoDirectToWhatsApp(context, rec)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kirim ke WhatsApp", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        previewModalPhoto = null
                        onOpenGallery()
                    }
                ) {
                    Text("Buka Semua Galeri")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewModalPhoto = null }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun CameraGridAndLevelOverlay(
    gridType: CameraGridType,
    showLevel: Boolean,
    rollDegrees: Float
) {
    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val lineColor = Color.White.copy(alpha = 0.32f)
        val stroke = 1.dp.toPx()

        when (gridType) {
            CameraGridType.RULE_OF_THIRDS_3X3 -> {
                for (i in 1..2) {
                    drawLine(lineColor, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), stroke)
                    drawLine(lineColor, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), stroke)
                }
            }
            CameraGridType.GRID_4X4 -> {
                for (i in 1..3) {
                    drawLine(lineColor, Offset(w * i / 4f, 0f), Offset(w * i / 4f, h), stroke)
                    drawLine(lineColor, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), stroke)
                }
            }
            CameraGridType.GOLDEN_RATIO -> {
                val phi = 0.382f
                val invPhi = 0.618f
                drawLine(lineColor, Offset(w * phi, 0f), Offset(w * phi, h), stroke)
                drawLine(lineColor, Offset(w * invPhi, 0f), Offset(w * invPhi, h), stroke)
                drawLine(lineColor, Offset(0f, h * phi), Offset(w, h * phi), stroke)
                drawLine(lineColor, Offset(0f, h * invPhi), Offset(w, h * invPhi), stroke)
            }
            CameraGridType.CROSSHAIR_LEVEL -> {
                val cx = w / 2f
                val cy = h / 2f
                val len = 36.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - len, cy), Offset(cx + len, cy), 1.5.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.5f), Offset(cx, cy - len), Offset(cx, cy + len), 1.5.dp.toPx())
            }
            CameraGridType.NONE -> {}
        }

        if (showLevel) {
            val cx = w / 2f
            val cy = h / 2f
            val isLevel = abs(rollDegrees) < 1.8f
            val barColor = if (isLevel) Color(0xFF10B981) else Color(0x99F59E0B)
            val halfLen = 46.dp.toPx()
            val rad = Math.toRadians(rollDegrees.toDouble())
            val dx = (halfLen * kotlin.math.cos(rad)).toFloat()
            val dy = (halfLen * kotlin.math.sin(rad)).toFloat()
            drawLine(
                color = barColor,
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy),
                strokeWidth = if (isLevel) 2.5.dp.toPx() else 1.5.dp.toPx()
            )
        }
    }
}

private fun createStudioFallbackBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(1920, 1440, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f, 0f, 1920f, 1440f,
        intArrayOf(
            AndroidColor.parseColor("#0F172A"),
            AndroidColor.parseColor("#1E293B"),
            AndroidColor.parseColor("#0F766E")
        ),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, 1920f, 1440f, paint)
    return bmp
}
