package com.phototimestamp.pro.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phototimestamp.pro.data.AppRepository
import com.phototimestamp.pro.model.*
import com.phototimestamp.pro.ui.components.LiveWatermarkOverlay
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkEditorScreen(
    repository: AppRepository,
    gpsState: GpsLocationState,
    onBack: () -> Unit,
    onOpenSignaturePad: () -> Unit
) {
    val context = LocalContext.current
    val settings by repository.settings.collectAsState()
    val templates by repository.templates.collectAsState()
    val activeProject = remember(settings.activeProjectId) { repository.getActiveProject() }

    var draftTemplate by remember(settings.activeTemplateId, templates) {
        mutableStateOf(repository.getActiveTemplate())
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var previewLandscapeMode by remember { mutableStateOf(false) }
    var showSaveAsNewDialog by remember { mutableStateOf(false) }
    var newTemplateName by remember { mutableStateOf("${draftTemplate.name} (Kustom)") }

    val customLogoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val ok = repository.saveCustomLogoFromUri(uri)
            if (ok) {
                draftTemplate = draftTemplate.copy(logoPreset = WatermarkLogoPreset.CUSTOM_IMAGE)
                repository.saveTemplate(draftTemplate)
                Toast.makeText(context, "Logo kustom berhasil diterapkan!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateDraft(transform: (WatermarkTemplate) -> WatermarkTemplate) {
        val next = transform(draftTemplate)
        draftTemplate = next
        repository.saveTemplate(next)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Visual Watermark Editor", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            text = draftTemplate.name,
                            fontSize = 11.sp,
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
                    TextButton(
                        onClick = {
                            updateDraft {
                                it.copy(
                                    position = WatermarkPosition.BOTTOM_RIGHT,
                                    alignment = WatermarkAlignment.RIGHT
                                )
                            }
                            Toast.makeText(
                                context,
                                "Diatur ke Default: Pojok Kanan Bawah & Rata Kanan",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text("Reset Kanan Bawah", fontSize = 11.sp)
                    }
                    IconButton(onClick = { showSaveAsNewDialog = true }) {
                        Icon(Icons.Default.SaveAs, contentDescription = "Simpan Template Baru")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Interactive Drag & Drop Live Watermark Canvas Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(255.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF064E3B))
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                LiveWatermarkOverlay(
                    template = draftTemplate,
                    settings = settings,
                    gpsState = gpsState,
                    project = activeProject,
                    docNumber = repository.peekCurrentDocNumber(),
                    signatureBitmap = repository.getSignatureBitmap(),
                    customLogoBitmap = repository.getCustomLogoBitmap(),
                    deviceOrientationDegrees = if (previewLandscapeMode) 90 else 0,
                    enableDragAndDrop = true,
                    onDragPositionChange = { fx, fy ->
                        updateDraft {
                            it.copy(
                                position = WatermarkPosition.CUSTOM_DRAG,
                                customXFraction = fx,
                                customYFraction = fy
                            )
                        }
                    }
                )

                // Top-left interactive badge & Portrait/Landscape preview switcher
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xBB000000),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Geser Watermark (Drag & Drop)",
                            color = Color(0xFF6EE7B7),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = if (previewLandscapeMode) Color(0xFF10B981) else Color(0xBB000000),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { previewLandscapeMode = !previewLandscapeMode }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = null,
                                tint = if (previewLandscapeMode) Color.Black else Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (previewLandscapeMode) "Uji Landscape (90°)" else "Uji Portrait (0°)",
                                color = if (previewLandscapeMode) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Editor Category Tabs
            val tabs = listOf("Posisi & Rata", "Font & Data", "Warna & Efek", "Logo & QR")
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            // 3. Scrollable Tab Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    0 -> PositionAndAlignmentTab(
                        draft = draftTemplate,
                        settings = settings,
                        onUpdateDraft = ::updateDraft,
                        onUpdateSettings = repository::updateSettings
                    )
                    1 -> TypographyAndContentTab(
                        draft = draftTemplate,
                        settings = settings,
                        onUpdateDraft = ::updateDraft,
                        onUpdateSettings = repository::updateSettings
                    )
                    2 -> ColorsAndEffectsTab(
                        draft = draftTemplate,
                        onUpdateDraft = ::updateDraft
                    )
                    3 -> LogoQrAndSignatureTab(
                        draft = draftTemplate,
                        settings = settings,
                        onUpdateDraft = ::updateDraft,
                        onUpdateSettings = repository::updateSettings,
                        onPickCustomLogo = { customLogoLauncher.launch("image/*") },
                        onOpenSignaturePad = onOpenSignaturePad
                    )
                }
            }
        }
    }

    if (showSaveAsNewDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAsNewDialog = false },
            title = { Text("Simpan Sebagai Template Baru", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTemplateName,
                    onValueChange = { newTemplateName = it },
                    label = { Text("Nama Template") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newId = "tpl_custom_${UUID.randomUUID().toString().take(8)}"
                        val customTpl = draftTemplate.copy(
                            id = newId,
                            name = newTemplateName.ifBlank { "Template Kustom Pro" },
                            category = "Kustom Pengguna",
                            isBuiltIn = false
                        )
                        repository.saveTemplate(customTpl)
                        repository.updateSettings { it.copy(activeTemplateId = newId) }
                        draftTemplate = customTpl
                        showSaveAsNewDialog = false
                        Toast.makeText(context, "Template baru berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsNewDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun PositionAndAlignmentTab(
    draft: WatermarkTemplate,
    settings: AppSettings,
    onUpdateDraft: ((WatermarkTemplate) -> WatermarkTemplate) -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Perataan Teks (Text Alignment)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WatermarkAlignment.entries.forEach { align ->
                    val selected = draft.alignment == align
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateDraft { it.copy(alignment = align) } },
                        label = { Text(align.labelId, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider()

            Text("Posisi Watermark (9 Titik Presisi + Drag Bebas)", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            val gridPositions = listOf(
                listOf(WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_CENTER, WatermarkPosition.TOP_RIGHT),
                listOf(WatermarkPosition.CENTER_LEFT, WatermarkPosition.CENTER, WatermarkPosition.CENTER_RIGHT),
                listOf(WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_CENTER, WatermarkPosition.BOTTOM_RIGHT)
            )

            gridPositions.forEach { rowList ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowList.forEach { pos ->
                        val selected = draft.position == pos
                        OutlinedButton(
                            onClick = { onUpdateDraft { it.copy(position = pos) } },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = pos.labelId.replace(" (Default)", ""),
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Custom Free Drag Position Sliders
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mode Posisi Bebas (Drag & Drop)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Switch(
                    checked = draft.position == WatermarkPosition.CUSTOM_DRAG,
                    onCheckedChange = { enabled ->
                        onUpdateDraft {
                            it.copy(
                                position = if (enabled) WatermarkPosition.CUSTOM_DRAG else WatermarkPosition.BOTTOM_RIGHT
                            )
                        }
                    }
                )
            }

            if (draft.position == WatermarkPosition.CUSTOM_DRAG) {
                Text("Koordinat Horizontal X: ${(draft.customXFraction * 100).toInt()}%", fontSize = 12.sp)
                Slider(
                    value = draft.customXFraction,
                    onValueChange = { x -> onUpdateDraft { it.copy(customXFraction = x) } },
                    valueRange = 0f..1f
                )
                Text("Koordinat Vertikal Y: ${(draft.customYFraction * 100).toInt()}%", fontSize = 12.sp)
                Slider(
                    value = draft.customYFraction,
                    onValueChange = { y -> onUpdateDraft { it.copy(customYFraction = y) } },
                    valueRange = 0f..1f
                )
            }

            Text("Jarak Margin Tepi: ${draft.marginDp.toInt()} dp", fontSize = 12.sp)
            Slider(
                value = draft.marginDp,
                onValueChange = { m -> onUpdateDraft { it.copy(marginDp = m) } },
                valueRange = 0f..40f
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Otomatis Pindah Pojok Kanan Bawah saat Landscape", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        "Saat HP dimiringkan (Landscape), teks otomatis berputar mulus ke pojok kanan bawah foto landscape.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.autoRotateLandscapeWatermark,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(autoRotateLandscapeWatermark = v) } }
                )
            }
        }
    }
}

@Composable
private fun TypographyAndContentTab(
    draft: WatermarkTemplate,
    settings: AppSettings,
    onUpdateDraft: ((WatermarkTemplate) -> WatermarkTemplate) -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Jenis Huruf (Font Family)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WatermarkFontFamily.entries) { font ->
                    FilterChip(
                        selected = draft.fontFamily == font,
                        onClick = { onUpdateDraft { it.copy(fontFamily = font) } },
                        label = { Text(font.labelId, fontSize = 12.sp) }
                    )
                }
            }

            Text("Skala Ukuran Huruf: ${(draft.fontSizeScale * 100).toInt()}%", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Slider(
                value = draft.fontSizeScale,
                onValueChange = { s -> onUpdateDraft { it.copy(fontSizeScale = s) } },
                valueRange = 0.65f..1.65f
            )

            OutlinedTextField(
                value = draft.headerBadgeText,
                onValueChange = { txt -> onUpdateDraft { it.copy(headerBadgeText = txt) } },
                label = { Text("Teks Label Header Badge") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = settings.customNoteText,
                onValueChange = { txt -> onUpdateSettings { it.copy(customNoteText = txt) } },
                label = { Text("Catatan Kustom Lapangan (Custom Text)") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Komponen Informasi yang Ditampilkan", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            val toggles = listOf(
                Triple("Badge Header Atas", draft.showHeaderBadge) { v: Boolean -> onUpdateDraft { it.copy(showHeaderBadge = v) } },
                Triple("Jam Presisi Real-Time", draft.showTime) { v: Boolean -> onUpdateDraft { it.copy(showTime = v) } },
                Triple("Tanggal & Hari", draft.showDate) { v: Boolean -> onUpdateDraft { it.copy(showDate = v) } },
                Triple("Milidetik (.SSS)", draft.showMilliseconds) { v: Boolean -> onUpdateDraft { it.copy(showMilliseconds = v) } },
                Triple("Alamat Lengkap GPS", draft.showAddress) { v: Boolean -> onUpdateDraft { it.copy(showAddress = v) } },
                Triple("Koordinat GPS (Lat/Lon)", draft.showGpsCoordinates) { v: Boolean -> onUpdateDraft { it.copy(showGpsCoordinates = v) } },
                Triple("Elevasi / Altitude (mdpl)", draft.showAltitude) { v: Boolean -> onUpdateDraft { it.copy(showAltitude = v) } },
                Triple("Akurasi GPS (± meter)", draft.showAccuracy) { v: Boolean -> onUpdateDraft { it.copy(showAccuracy = v) } },
                Triple("Arah Kompas (Azimuth)", draft.showCompass) { v: Boolean -> onUpdateDraft { it.copy(showCompass = v) } },
                Triple("Kecepatan (km/jam)", draft.showSpeed) { v: Boolean -> onUpdateDraft { it.copy(showSpeed = v) } },
                Triple("Nama & Kode Proyek", draft.showProjectInfo) { v: Boolean -> onUpdateDraft { it.copy(showProjectInfo = v) } },
                Triple("Identitas Petugas & Instansi", draft.showOfficerIdentity) { v: Boolean -> onUpdateDraft { it.copy(showOfficerIdentity = v) } },
                Triple("Nomor Dokumentasi Otomatis", draft.showDocNumber) { v: Boolean -> onUpdateDraft { it.copy(showDocNumber = v) } },
                Triple("Kode Unik Verifikasi", draft.showUniqueCode) { v: Boolean -> onUpdateDraft { it.copy(showUniqueCode = v) } },
                Triple("Hash Bukti SHA-256", draft.showSha256Hash) { v: Boolean -> onUpdateDraft { it.copy(showSha256Hash = v) } },
                Triple("Catatan Kustom", draft.showCustomNote) { v: Boolean -> onUpdateDraft { it.copy(showCustomNote = v) } }
            )

            toggles.forEach { (label, checked, onChange) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, fontSize = 13.sp)
                    Switch(checked = checked, onCheckedChange = onChange)
                }
            }
        }
    }
}

@Composable
private fun ColorsAndEffectsTab(
    draft: WatermarkTemplate,
    onUpdateDraft: ((WatermarkTemplate) -> WatermarkTemplate) -> Unit
) {
    val colorOptions = listOf(
        0xFFFFFFFFL to "Putih",
        0xFF10B981L to "Emerald",
        0xFFF59E0BL to "Amber",
        0xFF38BDF8L to "Sky Blue",
        0xFFEF4444L to "Merah",
        0xFFA855F7L to "Ungu",
        0xFFFACC15L to "Kuning",
        0xFF0B132BL to "Navy Gelap",
        0xFF000000L to "Hitam"
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Gaya Latar Belakang (Background Style)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WatermarkBackgroundStyle.entries) { style ->
                    FilterChip(
                        selected = draft.backgroundStyle == style,
                        onClick = { onUpdateDraft { it.copy(backgroundStyle = style) } },
                        label = { Text(style.labelId, fontSize = 12.sp) }
                    )
                }
            }

            Text("Warna Teks Utama", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            ColorPaletteRow(
                colors = colorOptions,
                selectedColor = draft.textColorArgb,
                onSelect = { c -> onUpdateDraft { it.copy(textColorArgb = c) } }
            )

            Text("Warna Aksen & Highlight", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            ColorPaletteRow(
                colors = colorOptions,
                selectedColor = draft.accentColorArgb,
                onSelect = { c ->
                    onUpdateDraft {
                        it.copy(
                            accentColorArgb = c,
                            borderColorArgb = (0x88000000L or (c and 0x00FFFFFFL))
                        )
                    }
                }
            )

            Text("Warna Kotak Background", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            ColorPaletteRow(
                colors = colorOptions,
                selectedColor = draft.backgroundColorArgb,
                onSelect = { c -> onUpdateDraft { it.copy(backgroundColorArgb = c) } }
            )

            Text("Transparansi Background (Opacity): ${(draft.backgroundOpacity * 100).toInt()}%", fontSize = 12.sp)
            Slider(
                value = draft.backgroundOpacity,
                onValueChange = { op -> onUpdateDraft { it.copy(backgroundOpacity = op) } },
                valueRange = 0f..1f
            )

            Text("Transparansi Teks: ${(draft.textOpacity * 100).toInt()}%", fontSize = 12.sp)
            Slider(
                value = draft.textOpacity,
                onValueChange = { op -> onUpdateDraft { it.copy(textOpacity = op) } },
                valueRange = 0.2f..1f
            )

            Text("Lengkungan Sudut (Corner Radius): ${draft.cornerRadiusDp.toInt()} dp", fontSize = 12.sp)
            Slider(
                value = draft.cornerRadiusDp,
                onValueChange = { r -> onUpdateDraft { it.copy(cornerRadiusDp = r) } },
                valueRange = 0f..28f
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bayangan Teks & Kotak (Drop Shadow)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = draft.showShadow,
                    onCheckedChange = { s -> onUpdateDraft { it.copy(showShadow = s) } }
                )
            }
            if (draft.showShadow) {
                Text("Ketajaman Bayangan: ${draft.shadowRadius.toInt()} px", fontSize = 12.sp)
                Slider(
                    value = draft.shadowRadius,
                    onValueChange = { r -> onUpdateDraft { it.copy(shadowRadius = r) } },
                    valueRange = 1f..16f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Garis Tepi Kotak (Border)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = draft.showBorder,
                    onCheckedChange = { b -> onUpdateDraft { it.copy(showBorder = b) } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Garis Aksen Samping (Accent Bar)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = draft.showAccentBar,
                    onCheckedChange = { a -> onUpdateDraft { it.copy(showAccentBar = a) } }
                )
            }
        }
    }
}

@Composable
private fun LogoQrAndSignatureTab(
    draft: WatermarkTemplate,
    settings: AppSettings,
    onUpdateDraft: ((WatermarkTemplate) -> WatermarkTemplate) -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onPickCustomLogo: () -> Unit,
    onOpenSignaturePad: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Logo & Emblem Watermark", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            WatermarkLogoPreset.entries.forEach { preset ->
                val selected = draft.logoPreset == preset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else Color.Transparent
                        )
                        .clickable {
                            if (preset == WatermarkLogoPreset.CUSTOM_IMAGE) {
                                onPickCustomLogo()
                            } else {
                                onUpdateDraft { it.copy(logoPreset = preset) }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(preset.labelId, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    RadioButton(
                        selected = selected,
                        onClick = {
                            if (preset == WatermarkLogoPreset.CUSTOM_IMAGE) {
                                onPickCustomLogo()
                            } else {
                                onUpdateDraft { it.copy(logoPreset = preset) }
                            }
                        }
                    )
                }
            }

            OutlinedButton(
                onClick = onPickCustomLogo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pilih Gambar Logo Instansi dari Galeri")
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tampilkan QR Code Verifikasi", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Switch(
                    checked = draft.showQrCode,
                    onCheckedChange = { q -> onUpdateDraft { it.copy(showQrCode = q) } }
                )
            }

            if (draft.showQrCode) {
                Text("Ukuran QR Code: ${draft.qrSizeDp.toInt()} dp", fontSize = 12.sp)
                Slider(
                    value = draft.qrSizeDp,
                    onValueChange = { s -> onUpdateDraft { it.copy(qrSizeDp = s) } },
                    valueRange = 40f..90f
                )
                OutlinedTextField(
                    value = settings.customQrContentOverride,
                    onValueChange = { v -> onUpdateSettings { it.copy(customQrContentOverride = v) } },
                    label = { Text("Isi QR Kustom (Kosongkan untuk Otomatis GPS+Hash)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tampilkan Stempel Tanda Tangan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Switch(
                    checked = draft.showSignatureStamp,
                    onCheckedChange = { s -> onUpdateDraft { it.copy(showSignatureStamp = s) } }
                )
            }

            Button(
                onClick = onOpenSignaturePad,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Draw, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buka Kanvas Tanda Tangan & Identitas Petugas")
            }
        }
    }
}

@Composable
private fun ColorPaletteRow(
    colors: List<Pair<Long, String>>,
    selectedColor: Long,
    onSelect: (Long) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(colors) { (hex, label) ->
            val isSelected = selectedColor == hex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(hex) }
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(hex))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(label, fontSize = 10.sp)
            }
        }
    }
}
