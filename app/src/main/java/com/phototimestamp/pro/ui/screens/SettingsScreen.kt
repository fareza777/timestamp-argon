package com.phototimestamp.pro.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phototimestamp.pro.data.AppRepository
import com.phototimestamp.pro.model.*
import com.phototimestamp.pro.ui.components.LiveWatermarkOverlay
import com.phototimestamp.pro.util.AdMobManager
import com.phototimestamp.pro.util.ExportAndShareHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: AppRepository,
    gpsState: GpsLocationState,
    adMobManager: AdMobManager,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenWatermarkEditor: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSignatureStamp: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenEvidenceVerifier: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val settings by repository.settings.collectAsState()
    val templates by repository.templates.collectAsState()
    val photos by repository.photos.collectAsState()
    val activeTemplate = remember(settings.activeTemplateId, templates) { repository.getActiveTemplate() }
    val activeProject = remember(settings.activeProjectId) { repository.getActiveProject() }

    var searchQuery by remember { mutableStateOf("") }
    var showLivePreview by remember { mutableStateOf(false) }
    var showJsonRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }

    // Track which accordion section is expanded (default: Section 1 & 2 open)
    var expandedSections by remember { mutableStateOf(setOf(1, 2)) }

    fun toggleSection(id: Int) {
        expandedSections = if (expandedSections.contains(id)) {
            expandedSections - id
        } else {
            expandedSections + id
        }
    }

    val restoreFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                if (repository.importBackupJson(text)) {
                    Toast.makeText(context, "Backup pengaturan berhasil dipulihkan!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Format file backup JSON tidak valid", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun matchesSearch(vararg keywords: String): Boolean {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) return true
        return keywords.any { it.lowercase().contains(q) }
    }

    val isSearching = searchQuery.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Column {
                        Text(
                            text = "Pengaturan & Menu",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Photo Timestamp Pro",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    FilterChip(
                        selected = showLivePreview,
                        onClick = { showLivePreview = !showLivePreview },
                        label = { Text("Preview", fontSize = 11.5.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (showLivePreview) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Collapsible Live Preview Banner
            AnimatedVisibility(visible = showLivePreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF064E3B))
                            )
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                ) {
                    LiveWatermarkOverlay(
                        template = activeTemplate,
                        settings = settings,
                        gpsState = gpsState,
                        project = activeProject,
                        docNumber = repository.peekCurrentDocNumber(),
                        signatureBitmap = repository.getSignatureBitmap(),
                        customLogoBitmap = repository.getCustomLogoBitmap()
                    )
                    Surface(
                        color = Color(0x99000000),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "${activeTemplate.name} • ${activeTemplate.position.labelId}",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Sleek Search Bar Pill
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Cari pengaturan (format tanggal, GPS, kamera, tema...)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Main Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // BENTO GRID: QUICK FEATURES & TOOLS
                if (!isSearching) {
                    Text(
                        text = "MENU & FITUR UTAMA",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoMenuCard(
                            title = "Galeri & Share",
                            subtitle = "${photos.size} Foto • Batch • PDF",
                            icon = Icons.Default.PhotoLibrary,
                            accent = Color(0xFF10B981),
                            onClick = onOpenGallery,
                            modifier = Modifier.weight(1f)
                        )
                        BentoMenuCard(
                            title = "Editor Teks",
                            subtitle = "Posisi, Font & Gaya",
                            icon = Icons.Default.Tune,
                            accent = Color(0xFF38BDF8),
                            onClick = onOpenWatermarkEditor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoMenuCard(
                            title = "16+ Template",
                            subtitle = "Klasik Polos & Pro",
                            icon = Icons.Default.DashboardCustomize,
                            accent = Color(0xFFF59E0B),
                            onClick = onOpenTemplates,
                            modifier = Modifier.weight(1f)
                        )
                        BentoMenuCard(
                            title = "Proyek Kegiatan",
                            subtitle = activeProject.name,
                            icon = Icons.Default.FolderSpecial,
                            accent = Color(0xFFA855F7),
                            onClick = onOpenProjects,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoMenuCard(
                            title = "TTD & Petugas",
                            subtitle = "Kanvas Tanda Tangan",
                            icon = Icons.Default.Draw,
                            accent = Color(0xFFEC4899),
                            onClick = onOpenSignatureStamp,
                            modifier = Modifier.weight(1f)
                        )
                        BentoMenuCard(
                            title = "Bukti SHA-256",
                            subtitle = "Verifikasi Offline",
                            icon = Icons.Default.VerifiedUser,
                            accent = Color(0xFF22C55E),
                            onClick = onOpenEvidenceVerifier,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "PENGATURAN LENGKAP",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }

                // SECTION 1: KAMERA & PEMOTRETAN
                if (matchesSearch("kamera", "camera", "flash", "senter", "depan", "belakang", "grid", "timer", "shutter", "suara", "volume", "level", "landscape", "mirror")) {
                    ExpandableSettingsCard(
                        title = "Kamera & Pemotretan",
                        subtitle = "Flash, Timer, Grid, Suara & Orientasi Landscape",
                        icon = Icons.Default.PhotoCamera,
                        expanded = isSearching || expandedSections.contains(1),
                        onToggle = { toggleSection(1) }
                    ) {
                        SettingGroupLabel("Lensa Kamera Aktif")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !settings.useFrontCamera,
                                onClick = { repository.updateSettings { it.copy(useFrontCamera = false) } },
                                label = { Text("Kamera Belakang", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = settings.useFrontCamera,
                                onClick = { repository.updateSettings { it.copy(useFrontCamera = true) } },
                                label = { Text("Kamera Depan (Selfie)", fontSize = 12.sp) }
                            )
                        }

                        SettingGroupLabel("Mode Flash / Lampu Kilat")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(FlashModeSetting.entries) { fm ->
                                FilterChip(
                                    selected = settings.flashMode == fm,
                                    onClick = { repository.updateSettings { it.copy(flashMode = fm) } },
                                    label = { Text(fm.labelId, fontSize = 12.sp) }
                                )
                            }
                        }

                        SettingGroupLabel("Timer Hitung Mundur")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Mati", 3 to "3 Detik", 5 to "5 Detik", 10 to "10 Detik").forEach { (sec, lbl) ->
                                FilterChip(
                                    selected = settings.timerSeconds == sec,
                                    onClick = { repository.updateSettings { it.copy(timerSeconds = sec) } },
                                    label = { Text(lbl, fontSize = 12.sp) }
                                )
                            }
                        }

                        SettingGroupLabel("Garis Bantu (Grid)")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CameraGridType.entries) { grid ->
                                FilterChip(
                                    selected = settings.gridType == grid,
                                    onClick = { repository.updateSettings { it.copy(gridType = grid) } },
                                    label = { Text(grid.labelId, fontSize = 12.sp) }
                                )
                            }
                        }

                        SettingGroupLabel("Tombol Volume Fisik")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(VolumeButtonAction.entries) { act ->
                                FilterChip(
                                    selected = settings.volumeButtonAction == act,
                                    onClick = { repository.updateSettings { it.copy(volumeButtonAction = act) } },
                                    label = { Text(act.labelId, fontSize = 12.sp) }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                        SettingsSwitchRow(
                            title = "Otomatis Pindah Kanan Bawah saat Landscape",
                            subtitle = "Teks otomatis berputar ke pojok kanan bawah saat HP dimiringkan",
                            checked = settings.autoRotateLandscapeWatermark,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(autoRotateLandscapeWatermark = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Suara Rana Kamera (Shutter Sound)",
                            subtitle = "Default: Mati (Tanpa Suara)",
                            checked = settings.shutterSoundEnabled,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(shutterSoundEnabled = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Getaran Halus (Haptic Feedback)",
                            subtitle = "Umpan balik getar saat menekan tombol shutter",
                            checked = settings.hapticFeedbackEnabled,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(hapticFeedbackEnabled = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Sentuh Layar untuk Memotret",
                            subtitle = "Ketuk area kamera untuk fokus sekaligus memotret",
                            checked = settings.touchToCapture,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(touchToCapture = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Cerminkan Kamera Depan (Mirror Selfie)",
                            subtitle = "Simpan foto selfie sesuai arah tampilan layar",
                            checked = settings.mirrorFrontCamera,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(mirrorFrontCamera = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Garis Waterpass Kemiringan di Tengah",
                            subtitle = "Default: Mati (Layar bersih)",
                            checked = settings.showLevelIndicator,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(showLevelIndicator = v) } }
                        )
                    }
                }

                // SECTION 2: TIMESTAMP, TANGGAL & JAM
                if (matchesSearch("timestamp", "tanggal", "jam", "waktu", "date", "time", "format", "zona", "milidetik")) {
                    ExpandableSettingsCard(
                        title = "Format Tanggal & Jam",
                        subtitle = "Gaya penulisan tanggal, jam 24h, nama hari & zona waktu",
                        icon = Icons.Default.Schedule,
                        expanded = isSearching || expandedSections.contains(2),
                        onToggle = { toggleSection(2) }
                    ) {
                        val datePatterns = listOf(
                            "dd MMM yyyy" to "14 Okt 2026",
                            "dd MMMM yyyy" to "14 Oktober 2026",
                            "dd/MM/yyyy" to "14/10/2026",
                            "yyyy-MM-dd" to "2026-10-14"
                        )
                        SettingGroupLabel("Format Tanggal")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(datePatterns) { (pattern, example) ->
                                FilterChip(
                                    selected = settings.dateFormatPattern == pattern,
                                    onClick = { repository.updateSettings { it.copy(dateFormatPattern = pattern) } },
                                    label = { Text(example, fontSize = 12.sp) }
                                )
                            }
                        }

                        SettingsSwitchRow(
                            title = "Format Jam 24-Jam (HH:mm:ss)",
                            subtitle = "Matikan untuk format 12-Jam (AM/PM)",
                            checked = settings.use24HourFormat,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(use24HourFormat = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Tampilkan Nama Hari",
                            subtitle = "Contoh: Rabu, 14 Okt 2026",
                            checked = settings.showDayOfWeek,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(showDayOfWeek = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Tampilkan Zona Waktu (WIB / WITA / WIT)",
                            subtitle = "Menampilkan singkatan zona waktu di belakang jam",
                            checked = settings.showTimezoneLabel,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(showTimezoneLabel = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Tampilkan Milidetik (.SSS)",
                            subtitle = "Presisi waktu hingga seperseribu detik",
                            checked = settings.showMilliseconds,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(showMilliseconds = v) } }
                        )
                    }
                }

                // SECTION 3: LOKASI & KOORDINAT GPS
                if (matchesSearch("gps", "lokasi", "alamat", "koordinat", "akurasi", "altitude", "elevasi", "kompas", "manual")) {
                    ExpandableSettingsCard(
                        title = "Lokasi & Koordinat GPS",
                        subtitle = "Format koordinat, detail alamat & input lokasi manual",
                        icon = Icons.Default.LocationOn,
                        expanded = isSearching || expandedSections.contains(3),
                        onToggle = { toggleSection(3) }
                    ) {
                        SettingsSwitchRow(
                            title = "Aktifkan Lokasi & Koordinat GPS",
                            subtitle = "Tampilkan alamat dan titik koordinat pada foto",
                            checked = settings.gpsEnabled,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(gpsEnabled = v) } }
                        )

                        SettingGroupLabel("Format Penulisan Koordinat")
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            CoordinateFormat.entries.forEach { fmt ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { repository.updateSettings { it.copy(coordinateFormat = fmt) } }
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = settings.coordinateFormat == fmt,
                                        onClick = { repository.updateSettings { it.copy(coordinateFormat = fmt) } }
                                    )
                                    Text(fmt.labelId, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        SettingsSwitchRow(
                            title = "Tampilkan Nama Jalan",
                            subtitle = "Jalan utama dari hasil pencari lokasi GPS",
                            checked = settings.showStreetAddress,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(showStreetAddress = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Tampilkan Kecamatan, Kota & Provinsi",
                            subtitle = "Nama wilayah administrasi tempat memotret",
                            checked = settings.showSubDistrictAndCity,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(showSubDistrictAndCity = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Tampilkan Kode Pos",
                            subtitle = "Sertakan nomor kode pos di akhir alamat",
                            checked = settings.showPostalCode,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(showPostalCode = v) } }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                        SettingsSwitchRow(
                            title = "Gunakan Alamat & Koordinat Manual",
                            subtitle = "Ketik sendiri lokasi jika berada di dalam ruangan",
                            checked = settings.useManualLocationOverride,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(useManualLocationOverride = v) } }
                        )

                        if (settings.useManualLocationOverride) {
                            OutlinedTextField(
                                value = settings.manualAddressText,
                                onValueChange = { v -> repository.updateSettings { it.copy(manualAddressText = v) } },
                                label = { Text("Alamat Manual") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = settings.manualLatitude.toString(),
                                    onValueChange = { v ->
                                        v.toDoubleOrNull()?.let { d ->
                                            repository.updateSettings { it.copy(manualLatitude = d) }
                                        }
                                    },
                                    label = { Text("Latitude") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = settings.manualLongitude.toString(),
                                    onValueChange = { v ->
                                        v.toDoubleOrNull()?.let { d ->
                                            repository.updateSettings { it.copy(manualLongitude = d) }
                                        }
                                    },
                                    label = { Text("Longitude") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // SECTION 4: GAYA TEKS & WATERMARK
                if (matchesSearch("watermark", "teks putih", "posisi", "rata kanan", "pojok kanan bawah", "font", "ukuran")) {
                    ExpandableSettingsCard(
                        title = "Gaya Teks & Posisi Watermark",
                        subtitle = "Default: Teks Putih Polos di Kanan Bawah (Rata Kanan)",
                        icon = Icons.Default.TextFields,
                        expanded = isSearching || expandedSections.contains(4),
                        onToggle = { toggleSection(4) }
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Template Aktif: ${activeTemplate.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${activeTemplate.position.labelId} • ${activeTemplate.alignment.labelId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    repository.updateSettings {
                                        it.copy(
                                            activeTemplateId = "tpl_standard_pro",
                                            showShortHashOnPhoto = false
                                        )
                                    }
                                    Toast.makeText(
                                        context,
                                        "Diatur ke Teks Putih Polos (Kanan Bawah)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Set Teks Putih Polos", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onOpenWatermarkEditor,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Kustomisasi Penuh", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // SECTION 5: RESOLUSI, KUALITAS & PENYIMPANAN
                if (matchesSearch("resolusi", "kualitas", "penyimpanan", "jpg", "png", "4k", "1080p", "galeri")) {
                    ExpandableSettingsCard(
                        title = "Resolusi & Kualitas Foto",
                        subtitle = "Ukuran piksel, rasio aspek & penyimpanan galeri",
                        icon = Icons.Default.HighQuality,
                        expanded = isSearching || expandedSections.contains(5),
                        onToggle = { toggleSection(5) }
                    ) {
                        SettingGroupLabel("Resolusi Foto")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CameraResolutionPreset.entries) { res ->
                                FilterChip(
                                    selected = settings.resolutionPreset == res,
                                    onClick = { repository.updateSettings { it.copy(resolutionPreset = res) } },
                                    label = { Text(res.labelId, fontSize = 12.sp) }
                                )
                            }
                        }

                        SettingGroupLabel("Rasio Bingkai")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CameraAspectRatioPreset.entries) { ratio ->
                                FilterChip(
                                    selected = settings.aspectRatioPreset == ratio,
                                    onClick = { repository.updateSettings { it.copy(aspectRatioPreset = ratio) } },
                                    label = { Text(ratio.labelId, fontSize = 12.sp) }
                                )
                            }
                        }

                        SettingGroupLabel("Kualitas Gambar JPEG (${settings.jpegQuality}%)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(75 to "75%", 85 to "85%", 95 to "95%", 100 to "100%").forEach { (q, lbl) ->
                                FilterChip(
                                    selected = settings.jpegQuality == q,
                                    onClick = { repository.updateSettings { it.copy(jpegQuality = q) } },
                                    label = { Text(lbl, fontSize = 12.sp) }
                                )
                            }
                        }

                        SettingsSwitchRow(
                            title = "Simpan ke Galeri Publik HP",
                            subtitle = "Otomatis tersimpan di folder DCIM/PhotoTimestampPro",
                            checked = settings.saveToPublicGallery,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(saveToPublicGallery = v) } }
                        )
                        SettingsSwitchRow(
                            title = "Simpan Cadangan Foto Asli Tanpa Teks",
                            subtitle = "Simpan 2 versi: dengan timestamp & polos",
                            checked = settings.saveOriginalWithoutWatermark,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(saveOriginalWithoutWatermark = v) } }
                        )
                    }
                }

                // SECTION 6: TEMA, BAHASA & PRIVASI
                if (matchesSearch("privasi", "bahasa", "tema", "dark", "light", "aksen", "warna")) {
                    ExpandableSettingsCard(
                        title = "Tampilan, Tema & Bahasa",
                        subtitle = "Mode Gelap/Terang, warna aksen & bahasa",
                        icon = Icons.Default.Palette,
                        expanded = isSearching || expandedSections.contains(6),
                        onToggle = { toggleSection(6) }
                    ) {
                        SettingGroupLabel("Mode Tema")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { repository.updateSettings { it.copy(themeMode = mode) } },
                                    label = { Text(mode.labelId, fontSize = 12.sp) }
                                )
                            }
                        }

                        SettingGroupLabel("Warna Aksen")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AppAccentColor.entries) { accent ->
                                FilterChip(
                                    selected = settings.accentColor == accent,
                                    onClick = { repository.updateSettings { it.copy(accentColor = accent) } },
                                    label = { Text(accent.labelId, fontSize = 12.sp) }
                                )
                            }
                        }

                        SettingGroupLabel("Bahasa")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = settings.language == lang,
                                    onClick = { repository.updateSettings { it.copy(language = lang) } },
                                    label = { Text(lang.labelId, fontSize = 12.sp) }
                                )
                            }
                        }

                        SettingsSwitchRow(
                            title = "Samarkan Digit Akhir Koordinat (Mode Privasi)",
                            subtitle = "Sembunyikan titik presisi akhir GPS",
                            checked = settings.privacyMaskExactCoordinates,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(privacyMaskExactCoordinates = v) } }
                        )
                    }
                }

                // SECTION 7: BACKUP / RESTORE & PRO PASS
                if (matchesSearch("backup", "restore", "cadangan", "json", "ekspor", "admob", "pro")) {
                    ExpandableSettingsCard(
                        title = "Backup, Restore & Status Pro",
                        subtitle = "Cadangkan pengaturan ke file JSON & status bebas iklan",
                        icon = Icons.Default.CloudSync,
                        expanded = isSearching || expandedSections.contains(7),
                        onToggle = { toggleSection(7) }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val json = repository.exportBackupJson()
                                    val backupFile = File(repository.exportedReportsDir, "PhotoTimestampPro_Backup.json")
                                    backupFile.writeText(json)
                                    ExportAndShareHelper.shareFileGeneral(
                                        context,
                                        backupFile,
                                        "application/json",
                                        "Backup Pengaturan JSON"
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ekspor Backup", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { restoreFilePicker.launch("*/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Impor File", fontSize = 12.sp)
                            }
                        }

                        val isAdFree = adMobManager.isAdFreeActive()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isAdFree) "Pro Pass 24 Jam Aktif" else "Aktifkan Pro Pass 24 Jam",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Kamera selalu 100% bebas iklan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    if (activity != null) {
                                        adMobManager.showRewardedAdForProPass(activity) {}
                                    }
                                }
                            ) {
                                Text(if (isAdFree) "Perpanjang" else "Aktifkan", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showJsonRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showJsonRestoreDialog = false },
            title = { Text("Backup / Restore JSON", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = restoreJsonInput,
                    onValueChange = { restoreJsonInput = it },
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (repository.importBackupJson(restoreJsonInput)) {
                            Toast.makeText(context, "Pengaturan dipulihkan!", Toast.LENGTH_SHORT).show()
                            showJsonRestoreDialog = false
                        }
                    }
                ) {
                    Text("Pulihkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonRestoreDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun BentoMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExpandableSettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
