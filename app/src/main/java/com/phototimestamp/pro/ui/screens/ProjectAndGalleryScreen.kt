package com.phototimestamp.pro.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.phototimestamp.pro.data.AppRepository
import com.phototimestamp.pro.model.*
import com.phototimestamp.pro.util.AdMobManager
import com.phototimestamp.pro.util.ExportAndShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectManagerScreen(
    repository: AppRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val projects by repository.projects.collectAsState()
    val templates by repository.templates.collectAsState()
    val photos by repository.photos.collectAsState()
    val settings by repository.settings.collectAsState()

    var editingProject by remember { mutableStateOf<ProjectItem?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Project Manager", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            text = "Kelompokkan Foto, Template & Penomoran per Kegiatan",
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
                    Button(
                        onClick = {
                            isCreatingNew = true
                            editingProject = ProjectItem(
                                id = "proj_${UUID.randomUUID().toString().take(8)}",
                                name = "",
                                code = "PRJ-2026-0${projects.size + 1}",
                                clientOrLocation = "",
                                templateId = settings.activeTemplateId,
                                docPrefix = "DOC-0${projects.size + 1}-",
                                nextDocSequence = 1,
                                notes = ""
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Proyek Baru", fontSize = 12.sp)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(projects, key = { it.id }) { proj ->
                val isActive = settings.activeProjectId == proj.id
                val projectPhotos = photos.filter { it.projectId == proj.id }
                val assignedTemplate = templates.firstOrNull { it.id == proj.templateId } ?: templates.first()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isActive) 2.dp else 1.dp,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(proj.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    text = "Kode: ${proj.code} • Lokasi/Klien: ${proj.clientOrLocation.ifBlank { "-" }}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (isActive) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "PROYEK AKTIF",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Template: ${assignedTemplate.name}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Penomoran Otomatis: ${String.format("%s%04d", proj.docPrefix, proj.nextDocSequence)} • Total Foto: ${projectPhotos.size} foto",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (proj.notes.isNotBlank()) {
                            Text(
                                text = "Catatan: ${proj.notes}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    repository.selectActiveProject(proj)
                                    Toast.makeText(context, "Proyek '${proj.name}' diaktifkan!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isActive) "Sedang Aktif" else "Aktifkan Proyek", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    isCreatingNew = false
                                    editingProject = proj
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                            }

                            if (projectPhotos.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            Toast.makeText(context, "Membuat PDF Laporan Proyek...", Toast.LENGTH_SHORT).show()
                                            val pdf = ExportAndShareHelper.exportPhotosToPdfReport(
                                                context = context,
                                                records = projectPhotos,
                                                repository = repository,
                                                reportTitle = "LAPORAN PROYEK: ${proj.name.uppercase()}"
                                            )
                                            if (pdf != null) {
                                                ExportAndShareHelper.shareFileGeneral(
                                                    context,
                                                    pdf,
                                                    "application/pdf",
                                                    "Bagikan PDF Laporan ${proj.name}"
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", modifier = Modifier.size(16.dp))
                                }
                            }

                            if (proj.id != "proj_default") {
                                IconButton(onClick = { repository.deleteProject(proj.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingProject?.let { draft ->
        var name by remember(draft) { mutableStateOf(draft.name) }
        var code by remember(draft) { mutableStateOf(draft.code) }
        var client by remember(draft) { mutableStateOf(draft.clientOrLocation) }
        var prefix by remember(draft) { mutableStateOf(draft.docPrefix) }
        var nextSeq by remember(draft) { mutableStateOf(draft.nextDocSequence.toString()) }
        var selectedTplId by remember(draft) { mutableStateOf(draft.templateId) }
        var notes by remember(draft) { mutableStateOf(draft.notes) }

        AlertDialog(
            onDismissRequest = { editingProject = null },
            title = {
                Text(
                    if (isCreatingNew) "Buat Proyek Kegiatan Baru" else "Edit Proyek Kegiatan",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Proyek / Kegiatan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Kode Proyek") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = client,
                        onValueChange = { client = it },
                        label = { Text("Klien / Lokasi Proyek") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = prefix,
                            onValueChange = { prefix = it },
                            label = { Text("Prefix Nomor Dok") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = nextSeq,
                            onValueChange = { nextSeq = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Mulai Nomor") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("Template Watermark Proyek:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(templates) { tpl ->
                            FilterChip(
                                selected = selectedTplId == tpl.id,
                                onClick = { selectedTplId = tpl.id },
                                label = { Text(tpl.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Catatan Kegiatan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = draft.copy(
                            name = name.ifBlank { "Proyek Lapangan Baru" },
                            code = code.ifBlank { "PRJ-01" },
                            clientOrLocation = client,
                            docPrefix = prefix.ifBlank { "DOC-" },
                            nextDocSequence = nextSeq.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            templateId = selectedTplId,
                            notes = notes
                        )
                        repository.saveProject(updated)
                        repository.selectActiveProject(updated)
                        editingProject = null
                    }
                ) {
                    Text("Simpan & Aktifkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProject = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryAndToolsScreen(
    repository: AppRepository,
    gpsState: GpsLocationState,
    adMobManager: AdMobManager,
    onBack: () -> Unit,
    onOpenEvidenceVerifier: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val photos by repository.photos.collectAsState()
    val projects by repository.projects.collectAsState()

    var selectedProjectFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPhotoDetail by remember { mutableStateOf<PhotoRecord?>(null) }

    // Batch Watermark state
    var isBatchProcessing by remember { mutableStateOf(false) }
    var batchProgressText by remember { mutableStateOf("") }

    // Before/After Collage state
    var showBeforeAfterDialog by remember { mutableStateOf(false) }
    var beforeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var afterBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var beforeAfterSideBySide by remember { mutableStateOf(true) }
    var beforeAfterTitle by remember { mutableStateOf("LAPORAN PROGRESS BEFORE & AFTER") }

    val batchPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isBatchProcessing = true
                val added = ExportAndShareHelper.processBatchWatermarkUris(
                    context = context,
                    uris = uris,
                    repository = repository,
                    gpsState = gpsState,
                    onProgress = { cur, total ->
                        batchProgressText = "Memproses Batch Watermark $cur dari $total foto..."
                    }
                )
                isBatchProcessing = false
                Toast.makeText(
                    context,
                    "Berhasil menerapkan watermark ke ${added.size} foto!",
                    Toast.LENGTH_LONG
                ).show()
                if (activity != null) {
                    adMobManager.maybeShowFairInterstitialAfterExport(activity)
                }
            }
        }
    }

    val beforeImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                beforeBitmap = ExportAndShareHelper.decodeSampledBitmapFromUri(context, uri, 1600)
            }
        }
    }

    val afterImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                afterBitmap = ExportAndShareHelper.decodeSampledBitmapFromUri(context, uri, 1600)
            }
        }
    }

    val filteredPhotos = remember(photos, selectedProjectFilter, searchQuery) {
        photos.filter { rec ->
            val matchProj = selectedProjectFilter == "ALL" || rec.projectId == selectedProjectFilter
            val q = searchQuery.trim().lowercase()
            val matchSearch = q.isEmpty() ||
                rec.docNumber.lowercase().contains(q) ||
                rec.address.lowercase().contains(q) ||
                rec.projectName.lowercase().contains(q) ||
                rec.uniqueCode.lowercase().contains(q) ||
                rec.customNote.lowercase().contains(q)
            matchProj && matchSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Galeri Internal, Batch & Ekspor", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            text = "${filteredPhotos.size} Dokumentasi Tersimpan",
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
                    IconButton(onClick = onOpenEvidenceVerifier) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = "Verifikasi SHA-256")
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
            // 1. Power Tools Bar: Batch Watermark, Before/After, Export PDF Report
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { batchPickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesomeMotion, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Batch Watermark", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        // Pre-populate with latest 2 photos if available
                        if (photos.size >= 2 && beforeBitmap == null && afterBitmap == null) {
                            scope.launch(Dispatchers.IO) {
                                beforeBitmap = BitmapFactory.decodeFile(photos[1].filePath)
                                afterBitmap = BitmapFactory.decodeFile(photos[0].filePath)
                            }
                        }
                        showBeforeAfterDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Before / After", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        if (filteredPhotos.isEmpty()) {
                            Toast.makeText(context, "Belum ada foto untuk diekspor ke PDF", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        scope.launch {
                            Toast.makeText(context, "Menyusun Laporan PDF (${filteredPhotos.size} halaman)...", Toast.LENGTH_SHORT).show()
                            val pdfFile = ExportAndShareHelper.exportPhotosToPdfReport(
                                context = context,
                                records = filteredPhotos,
                                repository = repository
                            )
                            if (pdfFile != null) {
                                if (activity != null) {
                                    adMobManager.maybeShowFairInterstitialAfterExport(activity) {
                                        ExportAndShareHelper.shareFileGeneral(
                                            context,
                                            pdfFile,
                                            "application/pdf",
                                            "Bagikan Laporan PDF Dokumentasi"
                                        )
                                    }
                                } else {
                                    ExportAndShareHelper.shareFileGeneral(
                                        context,
                                        pdfFile,
                                        "application/pdf",
                                        "Bagikan Laporan PDF Dokumentasi"
                                    )
                                }
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ekspor PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isBatchProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = batchProgressText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            // 2. Search & Project Filter Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nomor dok, alamat, proyek, kode unik...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedProjectFilter == "ALL",
                        onClick = { selectedProjectFilter = "ALL" },
                        label = { Text("Semua Proyek (${photos.size})", fontSize = 11.sp) }
                    )
                }
                items(projects) { proj ->
                    val count = photos.count { it.projectId == proj.id }
                    FilterChip(
                        selected = selectedProjectFilter == proj.id,
                        onClick = { selectedProjectFilter = proj.id },
                        label = { Text("${proj.name} ($count)", fontSize = 11.sp) }
                    )
                }
            }

            // 3. Photo Grid
            if (filteredPhotos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PhotoCameraBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Belum ada foto dokumentasi di kategori ini.",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Ambil foto dari Kamera atau gunakan fitur Batch Watermark di atas.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredPhotos, key = { it.id }) { rec ->
                        GalleryPhotoCard(
                            record = rec,
                            onClick = { selectedPhotoDetail = rec },
                            onShareWhatsApp = {
                                ExportAndShareHelper.sharePhotoDirectToWhatsApp(context, rec)
                            }
                        )
                    }
                }
            }
        }
    }

    // Photo Detail & Metadata Modal
    selectedPhotoDetail?.let { rec ->
        PhotoDetailDialog(
            record = rec,
            onDismiss = { selectedPhotoDetail = null },
            onShareWhatsApp = {
                ExportAndShareHelper.sharePhotoDirectToWhatsApp(context, rec)
            },
            onShareGeneral = {
                val f = File(rec.filePath)
                val mime = if (rec.fileName.endsWith(".png", true)) "image/png" else "image/jpeg"
                ExportAndShareHelper.shareFileGeneral(context, f, mime, "Bagikan Foto ${rec.docNumber}")
            },
            onExportSinglePdf = {
                scope.launch {
                    val pdf = ExportAndShareHelper.exportPhotosToPdfReport(
                        context = context,
                        records = listOf(rec),
                        repository = repository,
                        reportTitle = "SERTIFIKAT DOKUMENTASI FOTO #${rec.docNumber}"
                    )
                    if (pdf != null) {
                        ExportAndShareHelper.shareFileGeneral(context, pdf, "application/pdf", "Bagikan PDF ${rec.docNumber}")
                    }
                }
            },
            onDelete = {
                repository.deletePhotoRecord(rec.id)
                selectedPhotoDetail = null
                Toast.makeText(context, "Foto dihapus", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Before / After Collage Builder Dialog
    if (showBeforeAfterDialog) {
        AlertDialog(
            onDismissRequest = { showBeforeAfterDialog = false },
            title = { Text("Generator Foto Before / After", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Gabungkan 2 foto (Sebelum & Sesudah) lengkap dengan label perbandingan dan stempel Timestamp Pro.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = beforeAfterTitle,
                        onValueChange = { beforeAfterTitle = it },
                        label = { Text("Judul Laporan Perbandingan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = beforeAfterSideBySide,
                            onClick = { beforeAfterSideBySide = true },
                            label = { Text("Kiri - Kanan (Horizontal)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = !beforeAfterSideBySide,
                            onClick = { beforeAfterSideBySide = false },
                            label = { Text("Atas - Bawah (Vertikal)", fontSize = 11.sp) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Before Box
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("FOTO BEFORE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF59E0B))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(12.dp))
                                    .clickable { beforeImageLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (beforeBitmap != null) {
                                    Image(
                                        bitmap = beforeBitmap!!.asImageBitmap(),
                                        contentDescription = "Before",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("Pilih Gambar", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }

                        // After Box
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("FOTO AFTER", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF10B981))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                                    .clickable { afterImageLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (afterBitmap != null) {
                                    Image(
                                        bitmap = afterBitmap!!.asImageBitmap(),
                                        contentDescription = "After",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("Pilih Gambar", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val b1 = beforeBitmap
                        val b2 = afterBitmap
                        if (b1 == null || b2 == null) {
                            Toast.makeText(context, "Pilih foto Before dan After terlebih dahulu", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showBeforeAfterDialog = false
                        scope.launch {
                            val record = ExportAndShareHelper.createBeforeAfterCollage(
                                context = context,
                                beforeBitmap = b1,
                                afterBitmap = b2,
                                isSideBySide = beforeAfterSideBySide,
                                titleText = beforeAfterTitle,
                                beforeLabel = "BEFORE / SEBELUM",
                                afterLabel = "AFTER / SESUDAH",
                                repository = repository,
                                gpsState = gpsState
                            )
                            selectedPhotoDetail = record
                            Toast.makeText(context, "Kolase Before/After berhasil dibuat!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Buat Kolase & Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBeforeAfterDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun GalleryPhotoCard(
    record: PhotoRecord,
    onClick: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, record.filePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(record.filePath, opts)
            }.getOrNull()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .background(Color(0xFF0F172A))
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = record.docNumber,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (record.isBeforeAfter) {
                    Surface(
                        color = Color(0xCCF59E0B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "BEFORE/AFTER",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onShareWhatsApp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .background(Color(0xCC10B981), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share WhatsApp",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = record.docNumber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.formattedTimestamp,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    text = record.address,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PhotoDetailDialog(
    record: PhotoRecord,
    onDismiss: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onShareGeneral: () -> Unit,
    onExportSinglePdf: () -> Unit,
    onDelete: () -> Unit
) {
    val fullBmp by produceState<Bitmap?>(initialValue = null, record.filePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(record.filePath, opts)
            }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(record.docNumber, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(record.projectName, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFEF4444))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (fullBmp != null) {
                    Image(
                        bitmap = fullBmp!!.asImageBitmap(),
                        contentDescription = record.docNumber,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    )
                }

                Text("Waktu: ${record.formattedTimestamp}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Alamat: ${record.address}", fontSize = 12.sp)
                Text("Koordinat: ${record.formattedCoordinates}", fontSize = 12.sp)
                Text("Petugas: ${record.officerName} (${record.agencyName})", fontSize = 12.sp)
                Text("Kode Unik: ${record.uniqueCode} • Dimensi: ${record.width}x${record.height}", fontSize = 11.sp)

                Surface(
                    color = Color(0x2210B981),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "SHA-256 INTEGRITY HASH (OFFLINE EVIDENCE):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = record.sha256Hash,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onShareWhatsApp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Langsung ke WhatsApp", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportSinglePdf,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onShareGeneral,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bagikan File", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceVerifierScreen(
    repository: AppRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos by repository.photos.collectAsState()

    var verificationResult by remember { mutableStateOf<EvidenceVerificationResult?>(null) }

    val externalFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                verificationResult = repository.verifyPhotoIntegrity(uri, "File Eksternal")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Evidence Mode (SHA-256 Offline)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            text = "Verifikasi Keaslian & Integritas Kriptografi Foto",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Mesin Verifikasi Forensik SHA-256", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = "100% Offline • Memeriksa kecocokan bit-per-bit terhadap Evidence Registry",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { externalFilePicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih File Foto untuk Uji Keaslian SHA-256")
                    }
                }
            }

            // Verification Result Banner
            verificationResult?.let { res ->
                val badgeColor = if (res.isVerified) Color(0xFF10B981) else Color(0xFFEF4444)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, badgeColor, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (res.isVerified) Icons.Default.Verified else Icons.Default.Warning,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = res.statusTitle,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = badgeColor
                            )
                        }

                        Text(res.statusMessage, fontSize = 12.sp)
                        HorizontalDivider()
                        Text("SHA-256 Terhitung dari File:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(
                            text = res.computedSha256,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = badgeColor
                        )

                        res.registeredRecord?.let { rec ->
                            HorizontalDivider()
                            Text("Data Registri Asli yang Cocok:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("• Nomor Dokumen: ${rec.docNumber} (${rec.uniqueCode})", fontSize = 12.sp)
                            Text("• Proyek: ${rec.projectName}", fontSize = 12.sp)
                            Text("• Waktu Potret: ${rec.formattedTimestamp}", fontSize = 12.sp)
                            Text("• Koordinat GPS: ${rec.formattedCoordinates}", fontSize = 12.sp)
                            Text("• Petugas: ${rec.officerName} (${rec.agencyName})", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Quick Verify from Internal Evidence Registry
            Text(
                text = "Registri Bukti Lokal (${photos.size} Foto Terdaftar)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            photos.take(20).forEach { rec ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                val file = File(rec.filePath)
                                if (file.exists()) {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    verificationResult = repository.verifyPhotoIntegrity(uri, rec.fileName)
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${rec.docNumber} • ${rec.fileName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(rec.formattedTimestamp, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "SHA-256: ${rec.sha256Hash.take(28)}...",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val file = File(rec.filePath)
                                    if (file.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        verificationResult = repository.verifyPhotoIntegrity(uri, rec.fileName)
                                    }
                                }
                            }
                        ) {
                            Text("Uji Hash", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
