package com.phototimestamp.pro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.phototimestamp.pro.model.*
import com.phototimestamp.pro.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class AppRepository(private val context: Context) {

    private val settingsFile = File(context.filesDir, "app_settings_v4.json")
    private val templatesFile = File(context.filesDir, "watermark_templates_v4.json")
    private val projectsFile = File(context.filesDir, "projects_v4.json")
    private val photosRegistryFile = File(context.filesDir, "photos_registry_v1.json")
    private val signatureFile = File(context.filesDir, "digital_signature.png")
    private val customLogoFile = File(context.filesDir, "custom_logo.png")
    val internalPhotosDir = File(context.filesDir, "captured_photos").apply { mkdirs() }
    val exportedReportsDir = File(context.filesDir, "exported_reports").apply { mkdirs() }

    private val _settings = MutableStateFlow(loadSettingsFromDisk())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _templates = MutableStateFlow(loadTemplatesFromDisk())
    val templates: StateFlow<List<WatermarkTemplate>> = _templates.asStateFlow()

    private val _projects = MutableStateFlow(loadProjectsFromDisk())
    val projects: StateFlow<List<ProjectItem>> = _projects.asStateFlow()

    private val _photos = MutableStateFlow(loadPhotosFromDisk())
    val photos: StateFlow<List<PhotoRecord>> = _photos.asStateFlow()

    private val _latestPreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val latestPreviewBitmap: StateFlow<Bitmap?> = _latestPreviewBitmap.asStateFlow()

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var cachedSignatureBitmap: Bitmap? = null
    private var cachedCustomLogoBitmap: Bitmap? = null

    init {
        ensureDefaultSignatureExists()
        preloadLatestPhotoBitmapAsync()
    }

    private fun preloadLatestPhotoBitmapAsync() {
        val newest = _photos.value.firstOrNull() ?: return
        repoScope.launch {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = BitmapFactory.decodeFile(newest.filePath, opts)
                if (bmp != null) {
                    _latestPreviewBitmap.value = bmp
                }
            }
        }
    }

    fun getActiveTemplate(): WatermarkTemplate {
        val activeProject = getActiveProject()
        val targetId = _settings.value.activeTemplateId.ifBlank { activeProject.templateId }
        return _templates.value.firstOrNull { it.id == targetId }
            ?: _templates.value.first()
    }

    fun getActiveProject(): ProjectItem {
        val targetId = _settings.value.activeProjectId
        return _projects.value.firstOrNull { it.id == targetId }
            ?: _projects.value.first()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        saveSettingsToDisk(updated)
    }

    fun saveTemplate(template: WatermarkTemplate) {
        val current = _templates.value.toMutableList()
        val idx = current.indexOfFirst { it.id == template.id }
        if (idx >= 0) {
            current[idx] = template
        } else {
            current.add(template)
        }
        _templates.value = current
        saveTemplatesToDisk(current)
    }

    fun resetTemplatesToDefaults() {
        val defaults = DefaultTemplates.getAllBuiltInTemplates()
        _templates.value = defaults
        saveTemplatesToDisk(defaults)
        updateSettings { it.copy(activeTemplateId = defaults.first().id) }
    }

    fun saveProject(project: ProjectItem) {
        val current = _projects.value.toMutableList()
        val idx = current.indexOfFirst { it.id == project.id }
        if (idx >= 0) {
            current[idx] = project
        } else {
            current.add(0, project)
        }
        _projects.value = current
        saveProjectsToDisk(current)
    }

    fun deleteProject(projectId: String) {
        if (projectId == "proj_default") return
        val current = _projects.value.filterNot { it.id == projectId }
        _projects.value = current
        saveProjectsToDisk(current)
        if (_settings.value.activeProjectId == projectId) {
            updateSettings { it.copy(activeProjectId = "proj_default") }
        }
    }

    fun selectActiveProject(project: ProjectItem) {
        updateSettings {
            it.copy(
                activeProjectId = project.id,
                activeTemplateId = project.templateId,
                docNumberPrefix = project.docPrefix,
                currentDocSequence = project.nextDocSequence
            )
        }
    }

    fun consumeAndIncrementDocNumber(): String {
        val s = _settings.value
        val activeProj = getActiveProject()
        val formatted = String.format("%s%04d", s.docNumberPrefix, s.currentDocSequence)
        if (s.autoIncrementDocNumber) {
            val nextSeq = s.currentDocSequence + 1
            updateSettings { it.copy(currentDocSequence = nextSeq) }
            saveProject(activeProj.copy(nextDocSequence = nextSeq))
        }
        return formatted
    }

    fun peekCurrentDocNumber(): String {
        val s = _settings.value
        return String.format("%s%04d", s.docNumberPrefix, s.currentDocSequence)
    }

    fun addPhotoRecord(record: PhotoRecord, inMemoryPreview: Bitmap? = null) {
        if (inMemoryPreview != null) {
            _latestPreviewBitmap.value = inMemoryPreview
        }
        val current = _photos.value.toMutableList()
        current.add(0, record)
        _photos.value = current
        repoScope.launch {
            savePhotosToDisk(current)
            if (inMemoryPreview == null) {
                preloadLatestPhotoBitmapAsync()
            }
        }
    }

    fun deletePhotoRecord(recordId: String) {
        val target = _photos.value.firstOrNull { it.id == recordId }
        if (target != null) {
            runCatching { File(target.filePath).delete() }
        }
        val updated = _photos.value.filterNot { it.id == recordId }
        _photos.value = updated
        savePhotosToDisk(updated)
    }

    fun getSignatureBitmap(): Bitmap? {
        if (!_settings.value.hasDigitalSignature) return null
        cachedSignatureBitmap?.let { return it }
        if (signatureFile.exists()) {
            cachedSignatureBitmap = BitmapFactory.decodeFile(signatureFile.absolutePath)
        }
        return cachedSignatureBitmap
    }

    fun saveSignatureBitmap(bitmap: Bitmap) {
        FileOutputStream(signatureFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        cachedSignatureBitmap = bitmap
        updateSettings { it.copy(hasDigitalSignature = true) }
    }

    fun clearSignatureBitmap() {
        if (signatureFile.exists()) signatureFile.delete()
        cachedSignatureBitmap = null
        updateSettings { it.copy(hasDigitalSignature = false) }
    }

    private fun ensureDefaultSignatureExists() {
        if (!signatureFile.exists()) {
            val bmp = Bitmap.createBitmap(340, 120, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                style = Paint.Style.STROKE
                strokeWidth = 5.5f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path().apply {
                moveTo(28f, 88f)
                cubicTo(65f, 22f, 110f, 95f, 148f, 44f)
                cubicTo(175f, 18f, 195f, 82f, 235f, 52f)
                cubicTo(265f, 34f, 290f, 65f, 315f, 40f)
                moveTo(52f, 96f)
                quadTo(170f, 76f, 296f, 84f)
            }
            canvas.drawPath(path, paint)
            FileOutputStream(signatureFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            cachedSignatureBitmap = bmp
        }
    }

    fun getCustomLogoBitmap(): Bitmap? {
        cachedCustomLogoBitmap?.let { return it }
        if (customLogoFile.exists()) {
            cachedCustomLogoBitmap = BitmapFactory.decodeFile(customLogoFile.absolutePath)
        }
        return cachedCustomLogoBitmap
    }

    fun saveCustomLogoFromUri(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val original = BitmapFactory.decodeStream(input) ?: return false
                val scaled = Bitmap.createScaledBitmap(original, 200, 200, true)
                FileOutputStream(customLogoFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                cachedCustomLogoBitmap = scaled
                updateSettings { it.copy(customLogoUriString = customLogoFile.absolutePath) }
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun verifyPhotoIntegrity(uri: Uri, displayName: String = ""): EvidenceVerificationResult =
        withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext EvidenceVerificationResult(
                        isVerified = false,
                        statusTitle = "Gagal Membaca File",
                        statusMessage = "File foto tidak dapat dibuka untuk pemeriksaan SHA-256.",
                        computedSha256 = "-"
                    )
                val computedHash = FormatUtils.computeSha256Hex(bytes)

                var exifComment: String? = null
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val exif = ExifInterface(stream)
                        exifComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
                            ?: exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
                    }
                }

                val matchedRecord = _photos.value.firstOrNull {
                    it.sha256Hash.equals(computedHash, ignoreCase = true)
                }

                if (matchedRecord != null) {
                    EvidenceVerificationResult(
                        isVerified = true,
                        statusTitle = "OTENTIK & TERVERIFIKASI (SHA-256 COCOK)",
                        statusMessage = "Integritas bit-per-bit 100% utuh! Foto ini terdaftar di Evidence Registry lokal dan belum pernah dimodifikasi sejak dipotret.",
                        computedSha256 = computedHash,
                        registeredRecord = matchedRecord,
                        exifEmbeddedHash = exifComment,
                        fileName = matchedRecord.fileName,
                        fileSizeBytes = bytes.size.toLong()
                    )
                } else if (!exifComment.isNullOrBlank() && exifComment!!.contains("PTP-EVIDENCE")) {
                    EvidenceVerificationResult(
                        isVerified = true,
                        statusTitle = "METADATA BUKti DITEMUKAN",
                        statusMessage = "Ditemukan sertifikat EXIF Photo Timestamp Pro ($exifComment), namun hash file saat ini berbeda dari registri lokal atau berasal dari perangkat lain.",
                        computedSha256 = computedHash,
                        exifEmbeddedHash = exifComment,
                        fileName = displayName.ifBlank { "Foto Eksternal" },
                        fileSizeBytes = bytes.size.toLong()
                    )
                } else {
                    EvidenceVerificationResult(
                        isVerified = false,
                        statusTitle = "HASH TIDAK TERDAFTAR / FILE TELAH DIMODIFIKASI",
                        statusMessage = "Peringatan: Hash SHA-256 file ini tidak cocok dengan foto manapun di Evidence Registry. File mungkin telah diedit, dikompres ulang (misal via chat), atau berasal dari luar aplikasi.",
                        computedSha256 = computedHash,
                        exifEmbeddedHash = exifComment,
                        fileName = displayName.ifBlank { "File Diuji" },
                        fileSizeBytes = bytes.size.toLong()
                    )
                }
            } catch (e: Exception) {
                EvidenceVerificationResult(
                    isVerified = false,
                    statusTitle = "Kesalahan Verifikasi",
                    statusMessage = e.localizedMessage ?: "Gagal memverifikasi file",
                    computedSha256 = "-"
                )
            }
        }

    fun exportBackupJson(): String {
        val root = JSONObject()
        root.put("backupVersion", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())
        root.put("appName", "Photo Timestamp Pro")
        root.put("settings", settingsToJson(_settings.value))
        val tplArr = JSONArray()
        _templates.value.forEach { tplArr.put(templateToJson(it)) }
        root.put("templates", tplArr)
        val projArr = JSONArray()
        _projects.value.forEach { projArr.put(projectToJson(it)) }
        root.put("projects", projArr)
        return root.toString(2)
    }

    fun importBackupJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            root.optJSONObject("settings")?.let { sJson ->
                val parsed = settingsFromJson(sJson)
                _settings.value = parsed
                saveSettingsToDisk(parsed)
            }
            root.optJSONArray("templates")?.let { tArr ->
                val list = mutableListOf<WatermarkTemplate>()
                for (i in 0 until tArr.length()) {
                    list.add(templateFromJson(tArr.getJSONObject(i)))
                }
                if (list.isNotEmpty()) {
                    _templates.value = list
                    saveTemplatesToDisk(list)
                }
            }
            root.optJSONArray("projects")?.let { pArr ->
                val list = mutableListOf<ProjectItem>()
                for (i in 0 until pArr.length()) {
                    list.add(projectFromJson(pArr.getJSONObject(i)))
                }
                if (list.isNotEmpty()) {
                    _projects.value = list
                    saveProjectsToDisk(list)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun loadSettingsFromDisk(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()
        return try {
            settingsFromJson(JSONObject(settingsFile.readText()))
        } catch (_: Exception) {
            AppSettings()
        }
    }

    private fun saveSettingsToDisk(s: AppSettings) {
        runCatching { settingsFile.writeText(settingsToJson(s).toString(2)) }
    }

    private fun loadTemplatesFromDisk(): List<WatermarkTemplate> {
        val defaults = DefaultTemplates.getAllBuiltInTemplates()
        if (!templatesFile.exists()) return defaults
        return try {
            val arr = JSONArray(templatesFile.readText())
            val loaded = mutableListOf<WatermarkTemplate>()
            for (i in 0 until arr.length()) {
                loaded.add(templateFromJson(arr.getJSONObject(i)))
            }
            if (loaded.size < 15) defaults else loaded
        } catch (_: Exception) {
            defaults
        }
    }

    private fun saveTemplatesToDisk(list: List<WatermarkTemplate>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { arr.put(templateToJson(it)) }
            templatesFile.writeText(arr.toString(2))
        }
    }

    private fun loadProjectsFromDisk(): List<ProjectItem> {
        val defaultProjects = listOf(
            ProjectItem(
                id = "proj_default",
                name = "Inspeksi & Dokumentasi Umum",
                code = "PRJ-UMUM-01",
                clientOrLocation = "Operasional Lapangan Nasional",
                templateId = "tpl_standard_pro",
                docPrefix = "DOC-2026-",
                nextDocSequence = 1,
                notes = "Proyek standar untuk dokumentasi harian dengan timestamp & GPS akurat."
            ),
            ProjectItem(
                id = "proj_konstruksi",
                name = "Pengawasan Konstruksi & Sipil",
                code = "KNS-2026-A",
                clientOrLocation = "Divisi Infrastruktur & K3",
                templateId = "tpl_construction_inspect",
                docPrefix = "KNS-2026-",
                nextDocSequence = 1,
                notes = "Dokumentasi progres struktur, material, dan audit keselamatan kerja."
            ),
            ProjectItem(
                id = "proj_forensik",
                name = "Bukti Audit & Investigasi Lapangan",
                code = "EVD-SHA256",
                clientOrLocation = "Tim Audit Internal & Kepatuhan",
                templateId = "tpl_forensic_evidence",
                docPrefix = "EVD-2026-",
                nextDocSequence = 1,
                notes = "Menggunakan hash SHA-256 otomatis untuk pembuktian keaslian foto."
            )
        )
        if (!projectsFile.exists()) return defaultProjects
        return try {
            val arr = JSONArray(projectsFile.readText())
            val list = mutableListOf<ProjectItem>()
            for (i in 0 until arr.length()) {
                list.add(projectFromJson(arr.getJSONObject(i)))
            }
            if (list.isEmpty()) defaultProjects else list
        } catch (_: Exception) {
            defaultProjects
        }
    }

    private fun saveProjectsToDisk(list: List<ProjectItem>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { arr.put(projectToJson(it)) }
            projectsFile.writeText(arr.toString(2))
        }
    }

    private fun loadPhotosFromDisk(): List<PhotoRecord> {
        if (!photosRegistryFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(photosRegistryFile.readText())
            val list = mutableListOf<PhotoRecord>()
            for (i in 0 until arr.length()) {
                val r = photoFromJson(arr.getJSONObject(i))
                if (File(r.filePath).exists()) {
                    list.add(r)
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePhotosToDisk(list: List<PhotoRecord>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { arr.put(photoToJson(it)) }
            photosRegistryFile.writeText(arr.toString(2))
        }
    }

    private fun settingsToJson(s: AppSettings): JSONObject = JSONObject().apply {
        put("resolutionPreset", s.resolutionPreset.name)
        put("aspectRatioPreset", s.aspectRatioPreset.name)
        put("gridType", s.gridType.name)
        put("flashMode", s.flashMode.name)
        put("timerSeconds", s.timerSeconds)
        put("shutterSoundEnabled", s.shutterSoundEnabled)
        put("hapticFeedbackEnabled", s.hapticFeedbackEnabled)
        put("volumeButtonAction", s.volumeButtonAction.name)
        put("touchToCapture", s.touchToCapture)
        put("keepScreenBright", s.keepScreenBright)
        put("showLevelIndicator", s.showLevelIndicator)
        put("useFrontCamera", s.useFrontCamera)
        put("mirrorFrontCamera", s.mirrorFrontCamera)
        put("autoRotateLandscapeWatermark", s.autoRotateLandscapeWatermark)
        put("dateFormatPattern", s.dateFormatPattern)
        put("timeFormatPattern", s.timeFormatPattern)
        put("use24HourFormat", s.use24HourFormat)
        put("showDayOfWeek", s.showDayOfWeek)
        put("showTimezoneLabel", s.showTimezoneLabel)
        put("showMilliseconds", s.showMilliseconds)
        put("customTimeOffsetSeconds", s.customTimeOffsetSeconds)
        put("gpsEnabled", s.gpsEnabled)
        put("coordinateFormat", s.coordinateFormat.name)
        put("showStreetAddress", s.showStreetAddress)
        put("showSubDistrictAndCity", s.showSubDistrictAndCity)
        put("showPostalCode", s.showPostalCode)
        put("showAltitudeMeters", s.showAltitudeMeters)
        put("showSpeedKmh", s.showSpeedKmh)
        put("showGpsAccuracy", s.showGpsAccuracy)
        put("showCompassAzimuth", s.showCompassAzimuth)
        put("targetAccuracyThresholdMeters", s.targetAccuracyThresholdMeters.toDouble())
        put("useManualLocationOverride", s.useManualLocationOverride)
        put("manualAddressText", s.manualAddressText)
        put("manualLatitude", s.manualLatitude)
        put("manualLongitude", s.manualLongitude)
        put("manualAltitude", s.manualAltitude)
        put("officerName", s.officerName)
        put("agencyName", s.agencyName)
        put("officerRole", s.officerRole)
        put("customNoteText", s.customNoteText)
        put("docNumberPrefix", s.docNumberPrefix)
        put("currentDocSequence", s.currentDocSequence)
        put("autoIncrementDocNumber", s.autoIncrementDocNumber)
        put("uniqueCodePrefix", s.uniqueCodePrefix)
        put("customQrContentOverride", s.customQrContentOverride)
        put("hasDigitalSignature", s.hasDigitalSignature)
        put("signatureStrokeColorArgb", s.signatureStrokeColorArgb)
        put("customLogoUriString", s.customLogoUriString)
        put("activeTemplateId", s.activeTemplateId)
        put("activeProjectId", s.activeProjectId)
        put("jpegQuality", s.jpegQuality)
        put("exportImageFormat", s.exportImageFormat)
        put("saveOriginalWithoutWatermark", s.saveOriginalWithoutWatermark)
        put("fileNamingPattern", s.fileNamingPattern)
        put("saveToPublicGallery", s.saveToPublicGallery)
        put("evidenceModeEnabled", s.evidenceModeEnabled)
        put("embedSha256InExif", s.embedSha256InExif)
        put("showShortHashOnPhoto", s.showShortHashOnPhoto)
        put("pdfPaperSize", s.pdfPaperSize)
        put("pdfIncludeMetadataTable", s.pdfIncludeMetadataTable)
        put("pdfIncludeSignatureBlock", s.pdfIncludeSignatureBlock)
        put("themeMode", s.themeMode.name)
        put("accentColor", s.accentColor.name)
        put("language", s.language.name)
        put("privacyMaskExactCoordinates", s.privacyMaskExactCoordinates)
        put("stripSensitiveDeviceExif", s.stripSensitiveDeviceExif)
        put("adFreeUntilMillis", s.adFreeUntilMillis)
        put("exportCountForInterstitial", s.exportCountForInterstitial)
    }

    private fun settingsFromJson(j: JSONObject): AppSettings {
        val d = AppSettings()
        return AppSettings(
            resolutionPreset = runCatching { CameraResolutionPreset.valueOf(j.optString("resolutionPreset", d.resolutionPreset.name)) }.getOrDefault(d.resolutionPreset),
            aspectRatioPreset = runCatching { CameraAspectRatioPreset.valueOf(j.optString("aspectRatioPreset", d.aspectRatioPreset.name)) }.getOrDefault(d.aspectRatioPreset),
            gridType = runCatching { CameraGridType.valueOf(j.optString("gridType", d.gridType.name)) }.getOrDefault(d.gridType),
            flashMode = runCatching { FlashModeSetting.valueOf(j.optString("flashMode", d.flashMode.name)) }.getOrDefault(d.flashMode),
            timerSeconds = j.optInt("timerSeconds", d.timerSeconds),
            shutterSoundEnabled = j.optBoolean("shutterSoundEnabled", false),
            hapticFeedbackEnabled = j.optBoolean("hapticFeedbackEnabled", d.hapticFeedbackEnabled),
            volumeButtonAction = runCatching { VolumeButtonAction.valueOf(j.optString("volumeButtonAction", d.volumeButtonAction.name)) }.getOrDefault(d.volumeButtonAction),
            touchToCapture = j.optBoolean("touchToCapture", d.touchToCapture),
            keepScreenBright = j.optBoolean("keepScreenBright", d.keepScreenBright),
            showLevelIndicator = j.optBoolean("showLevelIndicator", false),
            useFrontCamera = j.optBoolean("useFrontCamera", false),
            mirrorFrontCamera = j.optBoolean("mirrorFrontCamera", d.mirrorFrontCamera),
            autoRotateLandscapeWatermark = j.optBoolean("autoRotateLandscapeWatermark", d.autoRotateLandscapeWatermark),
            dateFormatPattern = j.optString("dateFormatPattern", d.dateFormatPattern),
            timeFormatPattern = j.optString("timeFormatPattern", d.timeFormatPattern),
            use24HourFormat = j.optBoolean("use24HourFormat", d.use24HourFormat),
            showDayOfWeek = j.optBoolean("showDayOfWeek", d.showDayOfWeek),
            showTimezoneLabel = j.optBoolean("showTimezoneLabel", d.showTimezoneLabel),
            showMilliseconds = j.optBoolean("showMilliseconds", d.showMilliseconds),
            customTimeOffsetSeconds = j.optInt("customTimeOffsetSeconds", d.customTimeOffsetSeconds),
            gpsEnabled = j.optBoolean("gpsEnabled", d.gpsEnabled),
            coordinateFormat = runCatching { CoordinateFormat.valueOf(j.optString("coordinateFormat", d.coordinateFormat.name)) }.getOrDefault(d.coordinateFormat),
            showStreetAddress = j.optBoolean("showStreetAddress", d.showStreetAddress),
            showSubDistrictAndCity = j.optBoolean("showSubDistrictAndCity", d.showSubDistrictAndCity),
            showPostalCode = j.optBoolean("showPostalCode", d.showPostalCode),
            showAltitudeMeters = j.optBoolean("showAltitudeMeters", d.showAltitudeMeters),
            showSpeedKmh = j.optBoolean("showSpeedKmh", d.showSpeedKmh),
            showGpsAccuracy = j.optBoolean("showGpsAccuracy", d.showGpsAccuracy),
            showCompassAzimuth = j.optBoolean("showCompassAzimuth", d.showCompassAzimuth),
            targetAccuracyThresholdMeters = j.optDouble("targetAccuracyThresholdMeters", d.targetAccuracyThresholdMeters.toDouble()).toFloat(),
            useManualLocationOverride = j.optBoolean("useManualLocationOverride", d.useManualLocationOverride),
            manualAddressText = j.optString("manualAddressText", d.manualAddressText),
            manualLatitude = j.optDouble("manualLatitude", d.manualLatitude),
            manualLongitude = j.optDouble("manualLongitude", d.manualLongitude),
            manualAltitude = j.optDouble("manualAltitude", d.manualAltitude),
            officerName = j.optString("officerName", d.officerName),
            agencyName = j.optString("agencyName", d.agencyName),
            officerRole = j.optString("officerRole", d.officerRole),
            customNoteText = j.optString("customNoteText", d.customNoteText),
            docNumberPrefix = j.optString("docNumberPrefix", d.docNumberPrefix),
            currentDocSequence = j.optInt("currentDocSequence", d.currentDocSequence),
            autoIncrementDocNumber = j.optBoolean("autoIncrementDocNumber", d.autoIncrementDocNumber),
            uniqueCodePrefix = j.optString("uniqueCodePrefix", d.uniqueCodePrefix),
            customQrContentOverride = j.optString("customQrContentOverride", d.customQrContentOverride),
            hasDigitalSignature = j.optBoolean("hasDigitalSignature", d.hasDigitalSignature),
            signatureStrokeColorArgb = j.optLong("signatureStrokeColorArgb", d.signatureStrokeColorArgb),
            customLogoUriString = j.optString("customLogoUriString", d.customLogoUriString),
            activeTemplateId = j.optString("activeTemplateId", d.activeTemplateId),
            activeProjectId = j.optString("activeProjectId", d.activeProjectId),
            jpegQuality = j.optInt("jpegQuality", d.jpegQuality),
            exportImageFormat = j.optString("exportImageFormat", d.exportImageFormat),
            saveOriginalWithoutWatermark = j.optBoolean("saveOriginalWithoutWatermark", d.saveOriginalWithoutWatermark),
            fileNamingPattern = j.optString("fileNamingPattern", d.fileNamingPattern),
            saveToPublicGallery = j.optBoolean("saveToPublicGallery", d.saveToPublicGallery),
            evidenceModeEnabled = j.optBoolean("evidenceModeEnabled", d.evidenceModeEnabled),
            embedSha256InExif = j.optBoolean("embedSha256InExif", d.embedSha256InExif),
            showShortHashOnPhoto = j.optBoolean("showShortHashOnPhoto", d.showShortHashOnPhoto),
            pdfPaperSize = j.optString("pdfPaperSize", d.pdfPaperSize),
            pdfIncludeMetadataTable = j.optBoolean("pdfIncludeMetadataTable", d.pdfIncludeMetadataTable),
            pdfIncludeSignatureBlock = j.optBoolean("pdfIncludeSignatureBlock", d.pdfIncludeSignatureBlock),
            themeMode = runCatching { AppThemeMode.valueOf(j.optString("themeMode", d.themeMode.name)) }.getOrDefault(d.themeMode),
            accentColor = runCatching { AppAccentColor.valueOf(j.optString("accentColor", d.accentColor.name)) }.getOrDefault(d.accentColor),
            language = runCatching { AppLanguage.valueOf(j.optString("language", d.language.name)) }.getOrDefault(d.language),
            privacyMaskExactCoordinates = j.optBoolean("privacyMaskExactCoordinates", d.privacyMaskExactCoordinates),
            stripSensitiveDeviceExif = j.optBoolean("stripSensitiveDeviceExif", d.stripSensitiveDeviceExif),
            adFreeUntilMillis = j.optLong("adFreeUntilMillis", d.adFreeUntilMillis),
            exportCountForInterstitial = j.optInt("exportCountForInterstitial", d.exportCountForInterstitial)
        )
    }

    private fun templateToJson(t: WatermarkTemplate): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("name", t.name)
        put("category", t.category)
        put("description", t.description)
        put("isBuiltIn", t.isBuiltIn)
        put("position", t.position.name)
        put("alignment", t.alignment.name)
        put("customXFraction", t.customXFraction.toDouble())
        put("customYFraction", t.customYFraction.toDouble())
        put("marginDp", t.marginDp.toDouble())
        put("fontSizeScale", t.fontSizeScale.toDouble())
        put("fontFamily", t.fontFamily.name)
        put("textColorArgb", t.textColorArgb)
        put("accentColorArgb", t.accentColorArgb)
        put("backgroundColorArgb", t.backgroundColorArgb)
        put("backgroundOpacity", t.backgroundOpacity.toDouble())
        put("textOpacity", t.textOpacity.toDouble())
        put("backgroundStyle", t.backgroundStyle.name)
        put("cornerRadiusDp", t.cornerRadiusDp.toDouble())
        put("showShadow", t.showShadow)
        put("shadowRadius", t.shadowRadius.toDouble())
        put("shadowColorArgb", t.shadowColorArgb)
        put("showBorder", t.showBorder)
        put("borderWidthDp", t.borderWidthDp.toDouble())
        put("borderColorArgb", t.borderColorArgb)
        put("showAccentBar", t.showAccentBar)
        put("accentBarOnRight", t.accentBarOnRight)
        put("logoPreset", t.logoPreset.name)
        put("showQrCode", t.showQrCode)
        put("qrSizeDp", t.qrSizeDp.toDouble())
        put("headerBadgeText", t.headerBadgeText)
        put("showHeaderBadge", t.showHeaderBadge)
        put("showDate", t.showDate)
        put("showTime", t.showTime)
        put("showDayName", t.showDayName)
        put("showTimezone", t.showTimezone)
        put("showMilliseconds", t.showMilliseconds)
        put("showAddress", t.showAddress)
        put("showGpsCoordinates", t.showGpsCoordinates)
        put("showAltitude", t.showAltitude)
        put("showSpeed", t.showSpeed)
        put("showAccuracy", t.showAccuracy)
        put("showCompass", t.showCompass)
        put("showProjectInfo", t.showProjectInfo)
        put("showOfficerIdentity", t.showOfficerIdentity)
        put("showSignatureStamp", t.showSignatureStamp)
        put("showDocNumber", t.showDocNumber)
        put("showUniqueCode", t.showUniqueCode)
        put("showSha256Hash", t.showSha256Hash)
        put("showCustomNote", t.showCustomNote)
    }

    private fun templateFromJson(j: JSONObject): WatermarkTemplate {
        val d = DefaultTemplates.getAllBuiltInTemplates().first()
        return WatermarkTemplate(
            id = j.optString("id", d.id),
            name = j.optString("name", d.name),
            category = j.optString("category", d.category),
            description = j.optString("description", d.description),
            isBuiltIn = j.optBoolean("isBuiltIn", true),
            position = runCatching { WatermarkPosition.valueOf(j.optString("position", WatermarkPosition.BOTTOM_RIGHT.name)) }.getOrDefault(WatermarkPosition.BOTTOM_RIGHT),
            alignment = runCatching { WatermarkAlignment.valueOf(j.optString("alignment", WatermarkAlignment.RIGHT.name)) }.getOrDefault(WatermarkAlignment.RIGHT),
            customXFraction = j.optDouble("customXFraction", 0.96).toFloat(),
            customYFraction = j.optDouble("customYFraction", 0.96).toFloat(),
            marginDp = j.optDouble("marginDp", 14.0).toFloat(),
            fontSizeScale = j.optDouble("fontSizeScale", 1.0).toFloat(),
            fontFamily = runCatching { WatermarkFontFamily.valueOf(j.optString("fontFamily", d.fontFamily.name)) }.getOrDefault(d.fontFamily),
            textColorArgb = j.optLong("textColorArgb", d.textColorArgb),
            accentColorArgb = j.optLong("accentColorArgb", d.accentColorArgb),
            backgroundColorArgb = j.optLong("backgroundColorArgb", d.backgroundColorArgb),
            backgroundOpacity = j.optDouble("backgroundOpacity", d.backgroundOpacity.toDouble()).toFloat(),
            textOpacity = j.optDouble("textOpacity", 1.0).toFloat(),
            backgroundStyle = runCatching { WatermarkBackgroundStyle.valueOf(j.optString("backgroundStyle", d.backgroundStyle.name)) }.getOrDefault(d.backgroundStyle),
            cornerRadiusDp = j.optDouble("cornerRadiusDp", 12.0).toFloat(),
            showShadow = j.optBoolean("showShadow", true),
            shadowRadius = j.optDouble("shadowRadius", 6.0).toFloat(),
            shadowColorArgb = j.optLong("shadowColorArgb", 0xDD000000),
            showBorder = j.optBoolean("showBorder", d.showBorder),
            borderWidthDp = j.optDouble("borderWidthDp", 1.2).toFloat(),
            borderColorArgb = j.optLong("borderColorArgb", d.borderColorArgb),
            showAccentBar = j.optBoolean("showAccentBar", d.showAccentBar),
            accentBarOnRight = j.optBoolean("accentBarOnRight", true),
            logoPreset = runCatching { WatermarkLogoPreset.valueOf(j.optString("logoPreset", d.logoPreset.name)) }.getOrDefault(d.logoPreset),
            showQrCode = j.optBoolean("showQrCode", d.showQrCode),
            qrSizeDp = j.optDouble("qrSizeDp", 54.0).toFloat(),
            headerBadgeText = j.optString("headerBadgeText", d.headerBadgeText),
            showHeaderBadge = j.optBoolean("showHeaderBadge", d.showHeaderBadge),
            showDate = j.optBoolean("showDate", true),
            showTime = j.optBoolean("showTime", true),
            showDayName = j.optBoolean("showDayName", true),
            showTimezone = j.optBoolean("showTimezone", true),
            showMilliseconds = j.optBoolean("showMilliseconds", false),
            showAddress = j.optBoolean("showAddress", true),
            showGpsCoordinates = j.optBoolean("showGpsCoordinates", true),
            showAltitude = j.optBoolean("showAltitude", d.showAltitude),
            showSpeed = j.optBoolean("showSpeed", false),
            showAccuracy = j.optBoolean("showAccuracy", d.showAccuracy),
            showCompass = j.optBoolean("showCompass", d.showCompass),
            showProjectInfo = j.optBoolean("showProjectInfo", d.showProjectInfo),
            showOfficerIdentity = j.optBoolean("showOfficerIdentity", d.showOfficerIdentity),
            showSignatureStamp = j.optBoolean("showSignatureStamp", d.showSignatureStamp),
            showDocNumber = j.optBoolean("showDocNumber", d.showDocNumber),
            showUniqueCode = j.optBoolean("showUniqueCode", d.showUniqueCode),
            showSha256Hash = j.optBoolean("showSha256Hash", false),
            showCustomNote = j.optBoolean("showCustomNote", d.showCustomNote)
        )
    }

    private fun projectToJson(p: ProjectItem): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("code", p.code)
        put("clientOrLocation", p.clientOrLocation)
        put("templateId", p.templateId)
        put("docPrefix", p.docPrefix)
        put("nextDocSequence", p.nextDocSequence)
        put("notes", p.notes)
        put("createdAtMillis", p.createdAtMillis)
    }

    private fun projectFromJson(j: JSONObject): ProjectItem = ProjectItem(
        id = j.optString("id", "proj_default"),
        name = j.optString("name", "Dokumentasi Umum"),
        code = j.optString("code", "PRJ-01"),
        clientOrLocation = j.optString("clientOrLocation", "-"),
        templateId = j.optString("templateId", "tpl_standard_pro"),
        docPrefix = j.optString("docPrefix", "DOC-2026-"),
        nextDocSequence = j.optInt("nextDocSequence", 1),
        notes = j.optString("notes", ""),
        createdAtMillis = j.optLong("createdAtMillis", System.currentTimeMillis())
    )

    private fun photoToJson(r: PhotoRecord): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("filePath", r.filePath)
        put("fileName", r.fileName)
        put("projectId", r.projectId)
        put("projectName", r.projectName)
        put("docNumber", r.docNumber)
        put("uniqueCode", r.uniqueCode)
        put("timestampMillis", r.timestampMillis)
        put("formattedTimestamp", r.formattedTimestamp)
        put("latitude", r.latitude)
        put("longitude", r.longitude)
        put("formattedCoordinates", r.formattedCoordinates)
        put("address", r.address)
        put("altitude", r.altitude)
        put("accuracy", r.accuracy.toDouble())
        put("azimuthDegrees", r.azimuthDegrees.toDouble())
        put("officerName", r.officerName)
        put("agencyName", r.agencyName)
        put("officerRole", r.officerRole)
        put("customNote", r.customNote)
        put("templateId", r.templateId)
        put("sha256Hash", r.sha256Hash)
        put("width", r.width)
        put("height", r.height)
        put("fileSizeBytes", r.fileSizeBytes)
        put("orientationDegrees", r.orientationDegrees)
        put("isBeforeAfter", r.isBeforeAfter)
        put("isBatchWatermarked", r.isBatchWatermarked)
    }

    private fun photoFromJson(j: JSONObject): PhotoRecord = PhotoRecord(
        id = j.optString("id"),
        filePath = j.optString("filePath"),
        fileName = j.optString("fileName"),
        projectId = j.optString("projectId", "proj_default"),
        projectName = j.optString("projectName", "Dokumentasi Umum"),
        docNumber = j.optString("docNumber", "DOC-0001"),
        uniqueCode = j.optString("uniqueCode", "PTP-000"),
        timestampMillis = j.optLong("timestampMillis", System.currentTimeMillis()),
        formattedTimestamp = j.optString("formattedTimestamp"),
        latitude = j.optDouble("latitude", 0.0),
        longitude = j.optDouble("longitude", 0.0),
        formattedCoordinates = j.optString("formattedCoordinates"),
        address = j.optString("address"),
        altitude = j.optDouble("altitude", 0.0),
        accuracy = j.optDouble("accuracy", 5.0).toFloat(),
        azimuthDegrees = j.optDouble("azimuthDegrees", 0.0).toFloat(),
        officerName = j.optString("officerName"),
        agencyName = j.optString("agencyName"),
        officerRole = j.optString("officerRole"),
        customNote = j.optString("customNote"),
        templateId = j.optString("templateId", "tpl_standard_pro"),
        sha256Hash = j.optString("sha256Hash"),
        width = j.optInt("width", 1920),
        height = j.optInt("height", 1080),
        fileSizeBytes = j.optLong("fileSizeBytes", 0L),
        orientationDegrees = j.optInt("orientationDegrees", 0),
        isBeforeAfter = j.optBoolean("isBeforeAfter", false),
        isBatchWatermarked = j.optBoolean("isBatchWatermarked", false)
    )
}
