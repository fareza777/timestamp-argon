package com.phototimestamp.pro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.phototimestamp.pro.data.AppRepository
import com.phototimestamp.pro.model.VolumeButtonAction
import com.phototimestamp.pro.ui.screens.*
import com.phototimestamp.pro.ui.theme.PhotoTimestampProTheme
import com.phototimestamp.pro.util.AdMobManager
import com.phototimestamp.pro.util.LocationAndSensorTracker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class AppDestination {
    CAMERA,
    WATERMARK_EDITOR,
    TEMPLATES,
    SIGNATURE_STAMP,
    PROJECTS,
    GALLERY,
    EVIDENCE_VERIFIER,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppRepository
    private lateinit var locationTracker: LocationAndSensorTracker
    private lateinit var adMobManager: AdMobManager
    private val _volumeKeyEvents = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    private var activeDestination: AppDestination = AppDestination.CAMERA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = AppRepository(applicationContext)
        locationTracker = LocationAndSensorTracker(applicationContext)
        adMobManager = AdMobManager(applicationContext, repository)
        adMobManager.initialize()

        setContent {
            val settings by repository.settings.collectAsState()
            val gpsState by locationTracker.gpsState.collectAsState()

            // Keep screen bright when configured
            LaunchedEffect(settings.keepScreenBright) {
                if (settings.keepScreenBright) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            var hasCameraPerm by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }
            var hasLocationPerm by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                hasCameraPerm = result[Manifest.permission.CAMERA] == true || hasCameraPerm
                hasLocationPerm = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true || hasLocationPerm
                locationTracker.startTracking(hasLocationPerm)
            }

            LaunchedEffect(Unit) {
                if (!hasCameraPerm || !hasLocationPerm) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else {
                    locationTracker.startTracking(true)
                }
            }

            var currentDestination by remember { mutableStateOf(AppDestination.CAMERA) }
            LaunchedEffect(currentDestination) {
                activeDestination = currentDestination
            }

            BackHandler(enabled = currentDestination != AppDestination.CAMERA) {
                currentDestination = AppDestination.CAMERA
            }

            PhotoTimestampProTheme(
                themeMode = settings.themeMode,
                accentColor = settings.accentColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Keep CameraScreen alive as base layer so opening/closing Settings is 0ms instant!
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraScreen(
                            repository = repository,
                            locationTracker = locationTracker,
                            volumeKeyEvents = _volumeKeyEvents.asSharedFlow(),
                            hasCameraPermission = hasCameraPerm,
                            hasLocationPermission = hasLocationPerm,
                            onRequestPermissions = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onOpenWatermarkEditor = { currentDestination = AppDestination.WATERMARK_EDITOR },
                            onOpenTemplates = { currentDestination = AppDestination.TEMPLATES },
                            onOpenSignatureDialog = { currentDestination = AppDestination.SIGNATURE_STAMP },
                            onOpenProjects = { currentDestination = AppDestination.PROJECTS },
                            onOpenGallery = { currentDestination = AppDestination.GALLERY },
                            onOpenSettings = { currentDestination = AppDestination.SETTINGS },
                            onOpenEvidenceVerifier = { currentDestination = AppDestination.EVIDENCE_VERIFIER }
                        )

                        when (currentDestination) {
                            AppDestination.CAMERA -> {}

                            AppDestination.WATERMARK_EDITOR -> WatermarkEditorScreen(
                                repository = repository,
                                gpsState = gpsState,
                                onBack = { currentDestination = AppDestination.CAMERA },
                                onOpenSignaturePad = { currentDestination = AppDestination.SIGNATURE_STAMP }
                            )

                            AppDestination.TEMPLATES -> TemplatesScreen(
                                repository = repository,
                                gpsState = gpsState,
                                onBack = { currentDestination = AppDestination.CAMERA },
                                onEditTemplate = { currentDestination = AppDestination.WATERMARK_EDITOR }
                            )

                            AppDestination.SIGNATURE_STAMP -> SignatureStampScreen(
                                repository = repository,
                                onBack = { currentDestination = AppDestination.CAMERA }
                            )

                            AppDestination.PROJECTS -> ProjectManagerScreen(
                                repository = repository,
                                onBack = { currentDestination = AppDestination.CAMERA }
                            )

                            AppDestination.GALLERY -> GalleryAndToolsScreen(
                                repository = repository,
                                gpsState = gpsState,
                                adMobManager = adMobManager,
                                onBack = { currentDestination = AppDestination.CAMERA },
                                onOpenEvidenceVerifier = { currentDestination = AppDestination.EVIDENCE_VERIFIER }
                            )

                            AppDestination.EVIDENCE_VERIFIER -> EvidenceVerifierScreen(
                                repository = repository,
                                onBack = { currentDestination = AppDestination.CAMERA }
                            )

                            AppDestination.SETTINGS -> SettingsScreen(
                                repository = repository,
                                gpsState = gpsState,
                                adMobManager = adMobManager,
                                onBack = { currentDestination = AppDestination.CAMERA },
                                onOpenGallery = { currentDestination = AppDestination.GALLERY },
                                onOpenWatermarkEditor = { currentDestination = AppDestination.WATERMARK_EDITOR },
                                onOpenTemplates = { currentDestination = AppDestination.TEMPLATES },
                                onOpenSignatureStamp = { currentDestination = AppDestination.SIGNATURE_STAMP },
                                onOpenProjects = { currentDestination = AppDestination.PROJECTS },
                                onOpenEvidenceVerifier = { currentDestination = AppDestination.EVIDENCE_VERIFIER }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val hasLoc = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        locationTracker.startTracking(hasLoc)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            activeDestination == AppDestination.CAMERA &&
            ::repository.isInitialized &&
            repository.settings.value.volumeButtonAction != VolumeButtonAction.NONE
        ) {
            _volumeKeyEvents.tryEmit(keyCode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        locationTracker.stopTracking()
    }
}
