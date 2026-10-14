package com.phototimestamp.pro.model

import androidx.compose.ui.graphics.Color

enum class WatermarkPosition(val labelId: String, val labelEn: String) {
    TOP_LEFT("Pojok Kiri Atas", "Top Left"),
    TOP_CENTER("Tengah Atas", "Top Center"),
    TOP_RIGHT("Pojok Kanan Atas", "Top Right"),
    CENTER_LEFT("Tengah Kiri", "Center Left"),
    CENTER("Tengah", "Center"),
    CENTER_RIGHT("Tengah Kanan", "Center Right"),
    BOTTOM_LEFT("Pojok Kiri Bawah", "Bottom Left"),
    BOTTOM_CENTER("Tengah Bawah", "Bottom Center"),
    BOTTOM_RIGHT("Pojok Kanan Bawah (Default)", "Bottom Right (Default)"),
    CUSTOM_DRAG("Posisi Bebas (Drag & Drop)", "Custom Free Drag")
}

enum class WatermarkAlignment(val labelId: String, val labelEn: String) {
    LEFT("Rata Kiri", "Align Left"),
    CENTER("Rata Tengah", "Align Center"),
    RIGHT("Rata Kanan (Default)", "Align Right (Default)")
}

enum class WatermarkFontFamily(val labelId: String) {
    SANS_SERIF("Modern Sans (Default)"),
    MODERN_BOLD("Industrial Bold"),
    MONOSPACE("Technical Monospace"),
    SERIF_FORMAL("Formal Serif (Instansi)"),
    TERMINAL_TECH("Cyber / CCTV Matrix")
}

enum class WatermarkBackgroundStyle(val labelId: String) {
    FROSTED_CARD("Kotak Transparan Elegan"),
    SOLID_PILL("Kotak Solid Kontras"),
    GRADIENT_PANEL("Gradasi Profesional"),
    FULL_FOOTER_BANNER("Banner Penuh Bawah"),
    TRANSPARENT_CLEAN("Tanpa Kotak (Teks + Bayangan)")
}

enum class WatermarkLogoPreset(val labelId: String) {
    NONE("Tanpa Logo"),
    SHIELD_VERIFIED("Perisai Terverifikasi"),
    GOVERNMENT_INSTANSI("Lambang Instansi / Dinas"),
    CONSTRUCTION_K3("Helm & Konstruksi K3"),
    SURVEY_GPS("Kompas & Geodesi GPS"),
    FORENSIC_EVIDENCE("Segel Bukti Forensik"),
    LOGISTICS_POD("Paket & Logistik POD"),
    CUSTOM_IMAGE("Logo Gambar Kustom")
}

enum class CoordinateFormat(val labelId: String, val example: String) {
    DECIMAL("Desimal (-6.208763, 106.845599)", "-6.208763°, 106.845599°"),
    DMS("DMS (6°12'31.5\"S 106°50'44.2\"E)", "6°12'31.5\"S 106°50'44.2\"E"),
    UTM_GRID("Grid / Plus Code (6P58QRRW+F6)", "Zone 48M 704182E 9313210N")
}

enum class CameraGridType(val labelId: String) {
    NONE("Tanpa Grid"),
    RULE_OF_THIRDS_3X3("Grid 3x3 (Rule of Thirds)"),
    GRID_4X4("Grid 4x4 Presisi"),
    GOLDEN_RATIO("Golden Ratio (Phi)"),
    CROSSHAIR_LEVEL("Crosshair + Waterpass Level")
}

enum class CameraResolutionPreset(val labelId: String, val width: Int, val height: Int) {
    SD_720P("HD 720p (Cepat & Hemat)", 1280, 720),
    HD_1080P("Full HD 1080p (Standar Pro)", 1920, 1080),
    QHD_1440P("Quad HD 2K (Detail Tinggi)", 2560, 1440),
    UHD_4K("Ultra HD 4K (Maksimal Sensor)", 3840, 2160)
}

enum class CameraAspectRatioPreset(val labelId: String) {
    RATIO_4_3("4:3 (Standar Kamera)"),
    RATIO_16_9("16:9 (Layar Penuh)"),
    RATIO_1_1("1:1 (Persegi / Laporan)")
}

enum class FlashModeSetting(val labelId: String) {
    OFF("Mati"),
    AUTO("Otomatis"),
    ON("Nyala"),
    TORCH("Senter Terus")
}

enum class AppThemeMode(val labelId: String) {
    DARK("Mode Gelap Pro"),
    LIGHT("Mode Terang Bersih"),
    SYSTEM("Ikuti Sistem HP")
}

enum class AppAccentColor(val labelId: String, val primaryHex: Long, val secondaryHex: Long) {
    EMERALD("Emerald Pro", 0xFF10B981, 0xFF059669),
    AMBER_GOLD("Amber Tactical", 0xFFF59E0B, 0xFFD97706),
    ROYAL_BLUE("Royal Survey", 0xFF3B82F6, 0xFF2563EB),
    CRIMSON("Forensic Red", 0xFFEF4444, 0xFFDC2626)
}

enum class AppLanguage(val labelId: String, val code: String) {
    INDONESIAN("Bahasa Indonesia", "id"),
    ENGLISH("English", "en")
}

enum class VolumeButtonAction(val labelId: String) {
    SHUTTER("Ambil Foto (Shutter)"),
    ZOOM("Perbesar / Perkecil (Zoom)"),
    NONE("Volume Sistem Standar")
}

data class WatermarkTemplate(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val isBuiltIn: Boolean = true,
    // Layout & Position (Defaults: Bottom-Right & Right-Aligned as requested!)
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val alignment: WatermarkAlignment = WatermarkAlignment.RIGHT,
    val customXFraction: Float = 0.96f, // 0.0 left .. 1.0 right
    val customYFraction: Float = 0.96f, // 0.0 top .. 1.0 bottom
    val marginDp: Float = 14f,
    // Typography & Colors (Default: Clean White Text Only, No Box!)
    val fontSizeScale: Float = 1.0f, // 0.6f .. 1.8f
    val fontFamily: WatermarkFontFamily = WatermarkFontFamily.SANS_SERIF,
    val textColorArgb: Long = 0xFFFFFFFF,
    val accentColorArgb: Long = 0xFFFFFFFF,
    val backgroundColorArgb: Long = 0x00000000,
    val backgroundOpacity: Float = 0.0f, // 0.0f .. 1.0f
    val textOpacity: Float = 1.0f,
    val backgroundStyle: WatermarkBackgroundStyle = WatermarkBackgroundStyle.TRANSPARENT_CLEAN,
    val cornerRadiusDp: Float = 12f,
    // Shadow & Border
    val showShadow: Boolean = true,
    val shadowRadius: Float = 7f,
    val shadowColorArgb: Long = 0xEE000000,
    val showBorder: Boolean = false,
    val borderWidthDp: Float = 1.2f,
    val borderColorArgb: Long = 0x6610B981,
    val showAccentBar: Boolean = false,
    val accentBarOnRight: Boolean = true, // Right bar matches right-aligned default
    // Visual Badges
    val logoPreset: WatermarkLogoPreset = WatermarkLogoPreset.NONE,
    val showQrCode: Boolean = false,
    val qrSizeDp: Float = 54f,
    val headerBadgeText: String = "VERIFIED TIMESTAMP",
    // Content Toggles (Default: Only Date/Time, Address/Lokasi, and GPS Coordinates!)
    val showHeaderBadge: Boolean = false,
    val showDate: Boolean = true,
    val showTime: Boolean = true,
    val showDayName: Boolean = true,
    val showTimezone: Boolean = true,
    val showMilliseconds: Boolean = false,
    val showAddress: Boolean = true,
    val showGpsCoordinates: Boolean = true,
    val showAltitude: Boolean = false,
    val showSpeed: Boolean = false,
    val showAccuracy: Boolean = false,
    val showCompass: Boolean = false,
    val showProjectInfo: Boolean = false,
    val showOfficerIdentity: Boolean = false,
    val showSignatureStamp: Boolean = false,
    val showDocNumber: Boolean = false,
    val showUniqueCode: Boolean = false,
    val showSha256Hash: Boolean = false,
    val showCustomNote: Boolean = false
)

data class AppSettings(
    // 1. Kamera Settings
    val resolutionPreset: CameraResolutionPreset = CameraResolutionPreset.HD_1080P,
    val aspectRatioPreset: CameraAspectRatioPreset = CameraAspectRatioPreset.RATIO_4_3,
    val gridType: CameraGridType = CameraGridType.NONE,
    val flashMode: FlashModeSetting = FlashModeSetting.OFF,
    val timerSeconds: Int = 0, // 0, 3, 5, 10
    val shutterSoundEnabled: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val volumeButtonAction: VolumeButtonAction = VolumeButtonAction.SHUTTER,
    val touchToCapture: Boolean = false,
    val keepScreenBright: Boolean = true,
    val showLevelIndicator: Boolean = false,
    val useFrontCamera: Boolean = false,
    val mirrorFrontCamera: Boolean = true,
    val autoRotateLandscapeWatermark: Boolean = true, // Moves watermark to landscape bottom-right smoothly!

    // 2. Timestamp & Date/Time Format
    val dateFormatPattern: String = "dd MMM yyyy", // e.g., 14 Okt 2026
    val timeFormatPattern: String = "HH:mm:ss", // 24-hour default
    val use24HourFormat: Boolean = true,
    val showDayOfWeek: Boolean = true,
    val showTimezoneLabel: Boolean = true,
    val showMilliseconds: Boolean = false,
    val customTimeOffsetSeconds: Int = 0,

    // 3. GPS, Akurasi & Lokasi
    val gpsEnabled: Boolean = true,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val showStreetAddress: Boolean = true,
    val showSubDistrictAndCity: Boolean = true,
    val showPostalCode: Boolean = true,
    val showAltitudeMeters: Boolean = true,
    val showSpeedKmh: Boolean = false,
    val showGpsAccuracy: Boolean = true,
    val showCompassAzimuth: Boolean = true,
    val targetAccuracyThresholdMeters: Float = 25f,
    val useManualLocationOverride: Boolean = false,
    val manualAddressText: String = "Jl. Jend. Sudirman Kav. 52-53, Jakarta Selatan",
    val manualLatitude: Double = -6.224960,
    val manualLongitude: Double = 106.809680,
    val manualAltitude: Double = 32.5,

    // 4. Identitas & Signature Stamp
    val officerName: String = "Budi Santoso, S.T.",
    val agencyName: String = "PT Nusantara Infrastruktur & Inspeksi",
    val officerRole: String = "Inspektur Lapangan Utama",
    val customNoteText: String = "Dokumentasi pemeriksaan kondisi lapangan sesuai SOP.",
    val docNumberPrefix: String = "DOC-2026-",
    val currentDocSequence: Int = 1,
    val autoIncrementDocNumber: Boolean = true,
    val uniqueCodePrefix: String = "PTP",
    val customQrContentOverride: String = "",
    val hasDigitalSignature: Boolean = true,
    val signatureStrokeColorArgb: Long = 0xFF38BDF8,
    val customLogoUriString: String = "",

    // 5. Active Template & Project
    val activeTemplateId: String = "tpl_standard_pro",
    val activeProjectId: String = "proj_default",

    // 6. Resolusi, Kualitas Foto & Penyimpanan
    val jpegQuality: Int = 95, // 75, 85, 95, 100
    val exportImageFormat: String = "JPG", // JPG or PNG
    val saveOriginalWithoutWatermark: Boolean = false,
    val fileNamingPattern: String = "PTP_{DATE}_{DOC}",
    val saveToPublicGallery: Boolean = true,

    // 7. Evidence Mode (SHA-256) & Ekspor
    val evidenceModeEnabled: Boolean = true,
    val embedSha256InExif: Boolean = true,
    val showShortHashOnPhoto: Boolean = false,
    val pdfPaperSize: String = "A4",
    val pdfIncludeMetadataTable: Boolean = true,
    val pdfIncludeSignatureBlock: Boolean = true,

    // 8. Privasi, Bahasa & Tema
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val accentColor: AppAccentColor = AppAccentColor.EMERALD,
    val language: AppLanguage = AppLanguage.INDONESIAN,
    val privacyMaskExactCoordinates: Boolean = false,
    val stripSensitiveDeviceExif: Boolean = false,

    // 9. Monetisasi AdMob
    val adFreeUntilMillis: Long = 0L,
    val exportCountForInterstitial: Int = 0
)

data class ProjectItem(
    val id: String,
    val name: String,
    val code: String,
    val clientOrLocation: String,
    val templateId: String,
    val docPrefix: String,
    val nextDocSequence: Int = 1,
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class PhotoRecord(
    val id: String,
    val filePath: String,
    val fileName: String,
    val projectId: String,
    val projectName: String,
    val docNumber: String,
    val uniqueCode: String,
    val timestampMillis: Long,
    val formattedTimestamp: String,
    val latitude: Double,
    val longitude: Double,
    val formattedCoordinates: String,
    val address: String,
    val altitude: Double,
    val accuracy: Float,
    val azimuthDegrees: Float,
    val officerName: String,
    val agencyName: String,
    val officerRole: String,
    val customNote: String,
    val templateId: String,
    val sha256Hash: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val orientationDegrees: Int = 0,
    val isBeforeAfter: Boolean = false,
    val isBatchWatermarked: Boolean = false
)

data class GpsLocationState(
    val latitude: Double = -6.175392,
    val longitude: Double = 106.827153,
    val altitude: Double = 18.4,
    val accuracyMeters: Float = 4.8f,
    val speedKmh: Float = 0.0f,
    val bearingAzimuth: Float = 45.0f,
    val streetAddress: String = "Jl. Medan Merdeka, Gambir",
    val districtAndCity: String = "Jakarta Pusat, DKI Jakarta",
    val postalCode: String = "10110",
    val hasRealFix: Boolean = false,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
) {
    fun fullAddress(includeStreet: Boolean, includeCity: Boolean, includePostal: Boolean): String {
        val parts = mutableListOf<String>()
        if (includeStreet && streetAddress.isNotBlank()) parts.add(streetAddress)
        if (includeCity && districtAndCity.isNotBlank()) parts.add(districtAndCity)
        if (includePostal && postalCode.isNotBlank()) parts.add(postalCode)
        return if (parts.isEmpty()) "Lokasi GPS Aktif" else parts.joinToString(", ")
    }
}

data class EvidenceVerificationResult(
    val isVerified: Boolean,
    val statusTitle: String,
    val statusMessage: String,
    val computedSha256: String,
    val registeredRecord: PhotoRecord? = null,
    val exifEmbeddedHash: String? = null,
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val verifiedAtMillis: Long = System.currentTimeMillis()
)
