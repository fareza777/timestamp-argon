package com.phototimestamp.pro.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.Toast
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
fun TemplatesScreen(
    repository: AppRepository,
    gpsState: GpsLocationState,
    onBack: () -> Unit,
    onEditTemplate: () -> Unit
) {
    val context = LocalContext.current
    val templates by repository.templates.collectAsState()
    val settings by repository.settings.collectAsState()
    val activeProject = remember(settings.activeProjectId) { repository.getActiveProject() }

    val categories = remember(templates) {
        listOf("Semua") + templates.map { it.category }.distinct()
    }
    var selectedCategory by remember { mutableStateOf("Semua") }

    val filteredTemplates = remember(templates, selectedCategory) {
        if (selectedCategory == "Semua") templates
        else templates.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("16+ Template Watermark Pro", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            text = "${templates.size} Template Siap Pakai & Dapat Diedit",
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
                            repository.resetTemplatesToDefaults()
                            Toast.makeText(context, "Semua template direset ke standar", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Reset Default", fontSize = 12.sp)
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
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredTemplates, key = { it.id }) { tpl ->
                    val isActive = settings.activeTemplateId == tpl.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tpl.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        text = "${tpl.category} • ${tpl.position.labelId} • ${tpl.alignment.labelId}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (isActive) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "AKTIF",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(tpl.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Live Watermark Preview Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(175.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF111827))
                                        )
                                    )
                            ) {
                                LiveWatermarkOverlay(
                                    template = tpl,
                                    settings = settings,
                                    gpsState = gpsState,
                                    project = activeProject,
                                    docNumber = repository.peekCurrentDocNumber(),
                                    signatureBitmap = repository.getSignatureBitmap(),
                                    customLogoBitmap = repository.getCustomLogoBitmap()
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        repository.updateSettings { it.copy(activeTemplateId = tpl.id) }
                                        Toast.makeText(context, "${tpl.name} diaktifkan", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isActive) "Sedang Dipakai" else "Pakai Template", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        repository.updateSettings { it.copy(activeTemplateId = tpl.id) }
                                        onEditTemplate()
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit Visual", fontSize = 12.sp)
                                }

                                IconButton(
                                    onClick = {
                                        val copyId = "tpl_copy_${UUID.randomUUID().toString().take(6)}"
                                        val duplicated = tpl.copy(
                                            id = copyId,
                                            name = "${tpl.name} (Salinan)",
                                            isBuiltIn = false
                                        )
                                        repository.saveTemplate(duplicated)
                                        Toast.makeText(context, "Template berhasil diduplikasi", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplikat")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureStampScreen(
    repository: AppRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by repository.settings.collectAsState()

    var strokes by remember { mutableStateOf(listOf<List<Offset>>()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedInkArgb by remember { mutableLongStateOf(settings.signatureStrokeColorArgb) }
    var padSizePx by remember { mutableStateOf(Pair(680, 240)) }

    val inkColors = listOf(
        0xFF38BDF8L to "Biru Muda",
        0xFFFFFFFFL to "Putih",
        0xFF10B981L to "Emerald",
        0xFFF59E0BL to "Emas",
        0xFF1D4ED8L to "Biru Tinta"
    )

    fun saveDrawnSignatureToRepo() {
        if (strokes.isEmpty()) {
            Toast.makeText(context, "Goreskan tanda tangan terlebih dahulu di area kanvas", Toast.LENGTH_SHORT).show()
            return
        }
        val outW = 360
        val outH = 130
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val scaleX = outW.toFloat() / padSizePx.first.coerceAtLeast(1).toFloat()
        val scaleY = outH.toFloat() / padSizePx.second.coerceAtLeast(1).toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = selectedInkArgb.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.forEach { points ->
            if (points.size > 1) {
                val path = Path()
                path.moveTo(points.first().x * scaleX, points.first().y * scaleY)
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x * scaleX, points[i].y * scaleY)
                }
                canvas.drawPath(path, paint)
            }
        }

        repository.saveSignatureBitmap(bmp)
        repository.updateSettings { it.copy(signatureStrokeColorArgb = selectedInkArgb) }
        Toast.makeText(context, "Tanda tangan digital berhasil disimpan!", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signature Stamp & Identitas Petugas", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
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
            // 1. Officer & Agency Identity Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Data Petugas & Instansi", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    OutlinedTextField(
                        value = settings.officerName,
                        onValueChange = { v -> repository.updateSettings { it.copy(officerName = v) } },
                        label = { Text("Nama Petugas / Pemeriksa") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = settings.agencyName,
                        onValueChange = { v -> repository.updateSettings { it.copy(agencyName = v) } },
                        label = { Text("Nama Instansi / Perusahaan / Dinas") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = settings.officerRole,
                        onValueChange = { v -> repository.updateSettings { it.copy(officerRole = v) } },
                        label = { Text("Jabatan / NIP / Divisi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 2. Automatic Documentation Number & Unique Code Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Penomoran Dokumentasi Otomatis & Kode Unik", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = settings.docNumberPrefix,
                            onValueChange = { v -> repository.updateSettings { it.copy(docNumberPrefix = v) } },
                            label = { Text("Prefix Nomor Dok") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = settings.currentDocSequence.toString(),
                            onValueChange = { v ->
                                val num = v.filter { it.isDigit() }.toIntOrNull() ?: 1
                                repository.updateSettings { it.copy(currentDocSequence = num.coerceAtLeast(1)) }
                            },
                            label = { Text("Urutan Saat Ini") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Increment Nomor Dokumentasi", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = "Preview Nomor Berikutnya: ${repository.peekCurrentDocNumber()}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Switch(
                            checked = settings.autoIncrementDocNumber,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(autoIncrementDocNumber = v) } }
                        )
                    }

                    OutlinedTextField(
                        value = settings.uniqueCodePrefix,
                        onValueChange = { v -> repository.updateSettings { it.copy(uniqueCodePrefix = v) } },
                        label = { Text("Prefix Kode Unik Verifikasi (mis. PTP / BUKTI)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3. Interactive Digital Signature Drawing Pad
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kanvas Tanda Tangan Digital", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Switch(
                            checked = settings.hasDigitalSignature,
                            onCheckedChange = { v -> repository.updateSettings { it.copy(hasDigitalSignature = v) } }
                        )
                    }

                    Text(
                        text = "Tanda tangani langsung di kotak bawah menggunakan jari Anda. Tanda tangan akan dicetak pada stempel foto & laporan PDF.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(inkColors) { (hex, label) ->
                            val sel = selectedInkArgb == hex
                            FilterChip(
                                selected = sel,
                                onClick = { selectedInkArgb = hex },
                                label = { Text(label, fontSize = 11.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(hex))
                                    )
                                }
                            )
                        }
                    }

                    // Touch Drawing Pad Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF090D16))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStroke = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStroke = currentStroke + change.position
                                    },
                                    onDragEnd = {
                                        if (currentStroke.size > 1) {
                                            strokes = strokes + listOf(currentStroke)
                                        }
                                        currentStroke = emptyList()
                                    }
                                )
                            }
                    ) {
                        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                            padSizePx = Pair(size.width.toInt(), size.height.toInt())

                            // Baseline guide
                            drawLine(
                                color = Color.White.copy(alpha = 0.16f),
                                start = Offset(24.dp.toPx(), size.height * 0.78f),
                                end = Offset(size.width - 24.dp.toPx(), size.height * 0.78f),
                                strokeWidth = 1.dp.toPx()
                            )

                            val allStrokes = if (currentStroke.isNotEmpty()) strokes + listOf(currentStroke) else strokes
                            allStrokes.forEach { pts ->
                                if (pts.size > 1) {
                                    val composePath = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(pts.first().x, pts.first().y)
                                        for (i in 1 until pts.size) {
                                            lineTo(pts[i].x, pts[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = composePath,
                                        color = Color(selectedInkArgb),
                                        style = Stroke(
                                            width = 3.5.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }

                        if (strokes.isEmpty() && currentStroke.isEmpty()) {
                            Text(
                                text = "Goreskan Tanda Tangan di Sini",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                strokes = emptyList()
                                currentStroke = emptyList()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bersihkan Kanvas")
                        }

                        Button(
                            onClick = { saveDrawnSignatureToRepo() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan Tanda Tangan")
                        }
                    }
                }
            }
        }
    }
}
