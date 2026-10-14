package com.phototimestamp.pro.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.phototimestamp.pro.data.AppRepository
import com.phototimestamp.pro.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

object ExportAndShareHelper {

    suspend fun processAndSaveCapturedBitmap(
        context: Context,
        rawBitmap: Bitmap,
        isFrontCamera: Boolean,
        deviceOrientationDegrees: Int,
        repository: AppRepository,
        gpsState: GpsLocationState,
        isBeforeAfter: Boolean = false,
        isBatch: Boolean = false
    ): PhotoRecord = withContext(Dispatchers.IO) {
        val settings = repository.settings.value
        val template = repository.getActiveTemplate()
        val project = repository.getActiveProject()
        val nowMillis = System.currentTimeMillis()

        // 1. Rotate / Mirror bitmap to match physical device orientation (Portrait vs Landscape)
        val orientedBitmap = orientAndCropBitmap(
            source = rawBitmap,
            isFrontCamera = isFrontCamera,
            mirrorFront = settings.mirrorFrontCamera,
            deviceOrientationDegrees = if (settings.autoRotateLandscapeWatermark) deviceOrientationDegrees else 0,
            aspectRatio = settings.aspectRatioPreset,
            resolution = settings.resolutionPreset
        )

        val docNumber = repository.consumeAndIncrementDocNumber()
        val lat = if (settings.useManualLocationOverride) settings.manualLatitude else gpsState.latitude
        val lon = if (settings.useManualLocationOverride) settings.manualLongitude else gpsState.longitude
        val alt = if (settings.useManualLocationOverride) settings.manualAltitude else gpsState.altitude
        val uniqueCode = FormatUtils.generateUniqueCode(settings.uniqueCodePrefix, nowMillis, lat, lon)

        val datePart = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMillis))
        val ext = if (settings.exportImageFormat.equals("PNG", ignoreCase = true)) "png" else "jpg"
        val cleanDoc = docNumber.replace(Regex("[^a-zA-Z0-9_-]"), "")
        val fileName = "PTP_${datePart}_${cleanDoc}.$ext"

        // Save original without watermark if Dual Save enabled
        if (settings.saveOriginalWithoutWatermark && !isBatch && !isBeforeAfter) {
            val origFile = File(repository.internalPhotosDir, "ORIG_$fileName")
            FileOutputStream(origFile).use { out ->
                orientedBitmap.compress(Bitmap.CompressFormat.JPEG, settings.jpegQuality.coerceIn(70, 100), out)
            }
        }

        // Compute pre-hash seed for visual watermark display
        val seedHash = FormatUtils.computeSha256Hex(
            "$fileName|$nowMillis|$lat|$lon|$docNumber|$uniqueCode".toByteArray()
        )

        // 2. Draw Watermark onto a mutable copy of orientedBitmap
        // Since orientedBitmap is already rotated to Landscape when held in Landscape,
        // passing deviceOrientationDegrees = 0 places the watermark at the bottom-right corner
        // of the Landscape image, right-aligned!
        val mutableBmp = orientedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBmp)
        val renderData = WatermarkRenderData(
            timestampMillis = nowMillis,
            gpsState = gpsState,
            projectName = project.name,
            projectCode = project.code,
            docNumber = docNumber,
            uniqueCode = uniqueCode,
            sha256Preview = seedHash,
            signatureBitmap = repository.getSignatureBitmap(),
            customLogoBitmap = repository.getCustomLogoBitmap()
        )

        WatermarkRenderer.drawWatermarkOnCanvas(
            canvas = canvas,
            canvasWidth = mutableBmp.width.toFloat(),
            canvasHeight = mutableBmp.height.toFloat(),
            template = template,
            settings = settings,
            data = renderData,
            deviceOrientationDegrees = 0
        )

        // 3. Write to internal storage file
        val outputFile = File(repository.internalPhotosDir, fileName)
        FileOutputStream(outputFile).use { out ->
            val fmt = if (ext == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            mutableBmp.compress(fmt, settings.jpegQuality.coerceIn(70, 100), out)
        }

        val addrString = if (settings.useManualLocationOverride) {
            settings.manualAddressText
        } else {
            gpsState.fullAddress(
                includeStreet = settings.showStreetAddress,
                includeCity = settings.showSubDistrictAndCity,
                includePostal = settings.showPostalCode
            )
        }

        // 4. Write EXIF metadata (if JPG) before computing final SHA-256 so file bytes never change afterwards
        if (ext == "jpg" && !settings.stripSensitiveDeviceExif) {
            runCatching {
                val exif = ExifInterface(outputFile.absolutePath)
                val exifDate = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(nowMillis))
                exif.setAttribute(ExifInterface.TAG_DATETIME, exifDate)
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDate)
                exif.setAttribute(ExifInterface.TAG_ARTIST, "${settings.officerName} (${settings.agencyName})")
                exif.setAttribute(
                    ExifInterface.TAG_IMAGE_DESCRIPTION,
                    "PTP-EVIDENCE|Doc:$docNumber|Code:$uniqueCode|GPS:$lat,$lon"
                )
                if (settings.embedSha256InExif) {
                    exif.setAttribute(
                        ExifInterface.TAG_USER_COMMENT,
                        "PTP-EVIDENCE|Doc:$docNumber|Code:$uniqueCode|SeedHash:${seedHash.take(24)}"
                    )
                }
                if (!settings.privacyMaskExactCoordinates) {
                    exif.setLatLong(lat, lon)
                }
                exif.saveAttributes()
            }
        }

        // 5. Compute exact bit-for-bit SHA-256 of the final file on disk
        val finalFileBytes = outputFile.readBytes()
        val finalSha256 = FormatUtils.computeSha256Hex(finalFileBytes)

        // 6. Also copy to public MediaStore gallery if enabled
        if (settings.saveToPublicGallery) {
            saveFileToMediaStore(context, outputFile, if (ext == "png") "image/png" else "image/jpeg")
        }

        val formattedCoords = FormatUtils.formatCoordinates(
            lat = lat,
            lon = lon,
            format = settings.coordinateFormat,
            privacyMask = settings.privacyMaskExactCoordinates
        )
        val formattedTime = "${FormatUtils.formatDate(nowMillis, settings)} ${FormatUtils.formatTime(nowMillis, settings)}"

        val record = PhotoRecord(
            id = UUID.randomUUID().toString(),
            filePath = outputFile.absolutePath,
            fileName = fileName,
            projectId = project.id,
            projectName = project.name,
            docNumber = docNumber,
            uniqueCode = uniqueCode,
            timestampMillis = nowMillis,
            formattedTimestamp = formattedTime,
            latitude = lat,
            longitude = lon,
            formattedCoordinates = formattedCoords,
            address = addrString,
            altitude = alt,
            accuracy = gpsState.accuracyMeters,
            azimuthDegrees = gpsState.bearingAzimuth,
            officerName = settings.officerName,
            agencyName = settings.agencyName,
            officerRole = settings.officerRole,
            customNote = settings.customNoteText,
            templateId = template.id,
            sha256Hash = finalSha256,
            width = mutableBmp.width,
            height = mutableBmp.height,
            fileSizeBytes = outputFile.length(),
            orientationDegrees = deviceOrientationDegrees,
            isBeforeAfter = isBeforeAfter,
            isBatchWatermarked = isBatch
        )

        // Pre-scale an in-memory preview bitmap so clicking Preview is 0ms instant!
        val previewScale = min(800f / mutableBmp.width.toFloat(), 800f / mutableBmp.height.toFloat()).coerceAtMost(1f)
        val fastPreviewBmp = Bitmap.createScaledBitmap(
            mutableBmp,
            (mutableBmp.width * previewScale).toInt().coerceAtLeast(120),
            (mutableBmp.height * previewScale).toInt().coerceAtLeast(120),
            true
        )

        repository.addPhotoRecord(record, fastPreviewBmp)
        record
    }

    suspend fun processBatchWatermarkUris(
        context: Context,
        uris: List<Uri>,
        repository: AppRepository,
        gpsState: GpsLocationState,
        onProgress: (Int, Int) -> Unit
    ): List<PhotoRecord> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PhotoRecord>()
        val total = uris.size
        uris.forEachIndexed { index, uri ->
            onProgress(index + 1, total)
            runCatching {
                val bmp = decodeSampledBitmapFromUri(context, uri, 2560) ?: return@runCatching
                val record = processAndSaveCapturedBitmap(
                    context = context,
                    rawBitmap = bmp,
                    isFrontCamera = false,
                    deviceOrientationDegrees = 0,
                    repository = repository,
                    gpsState = gpsState,
                    isBeforeAfter = false,
                    isBatch = true
                )
                results.add(record)
            }
        }
        results
    }

    suspend fun createBeforeAfterCollage(
        context: Context,
        beforeBitmap: Bitmap,
        afterBitmap: Bitmap,
        isSideBySide: Boolean,
        titleText: String,
        beforeLabel: String,
        afterLabel: String,
        repository: AppRepository,
        gpsState: GpsLocationState
    ): PhotoRecord = withContext(Dispatchers.IO) {
        val outW = if (isSideBySide) 1920 else 1440
        val outH = if (isSideBySide) 1440 else 1920
        val collage = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collage)
        canvas.drawColor(Color.parseColor("#0B132B"))

        val headerH = 88f
        val gap = 14f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw Title Bar
        paint.color = Color.parseColor("#111C38")
        canvas.drawRect(0f, 0f, outW.toFloat(), headerH, paint)
        paint.color = Color.parseColor("#10B981")
        canvas.drawRect(0f, headerH - 4f, outW.toFloat(), headerH, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = 34f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            titleText.ifBlank { "LAPORAN PERBANDINGAN BEFORE / AFTER" },
            28f,
            56f,
            paint
        )

        val contentTop = headerH + gap
        val contentBottom = outH - gap

        val rect1: RectF
        val rect2: RectF
        if (isSideBySide) {
            val halfW = (outW - gap * 3f) / 2f
            rect1 = RectF(gap, contentTop, gap + halfW, contentBottom)
            rect2 = RectF(gap * 2f + halfW, contentTop, outW - gap, contentBottom)
        } else {
            val halfH = (contentBottom - contentTop - gap) / 2f
            rect1 = RectF(gap, contentTop, outW - gap, contentTop + halfH)
            rect2 = RectF(gap, contentTop + halfH + gap, outW - gap, contentBottom)
        }

        drawCenterCropBitmap(canvas, beforeBitmap, rect1)
        drawCenterCropBitmap(canvas, afterBitmap, rect2)

        // Draw BEFORE & AFTER badges
        drawComparisonBadge(canvas, rect1.left + 18f, rect1.top + 18f, beforeLabel.ifBlank { "BEFORE / SEBELUM" }, Color.parseColor("#F59E0B"))
        drawComparisonBadge(canvas, rect2.left + 18f, rect2.top + 18f, afterLabel.ifBlank { "AFTER / SESUDAH" }, Color.parseColor("#10B981"))

        processAndSaveCapturedBitmap(
            context = context,
            rawBitmap = collage,
            isFrontCamera = false,
            deviceOrientationDegrees = 0,
            repository = repository,
            gpsState = gpsState,
            isBeforeAfter = true,
            isBatch = false
        )
    }

    suspend fun exportPhotosToPdfReport(
        context: Context,
        records: List<PhotoRecord>,
        repository: AppRepository,
        reportTitle: String = "LAPORAN DOKUMENTASI FOTO BER-TIMESTAMP"
    ): File? = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext null
        return@withContext try {
            val settings = repository.settings.value
            val pdfDoc = PdfDocument()
            val pageW = 1240
            val pageH = 1754 // High-res A4 ratio

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 32f
                color = Color.parseColor("#0B132B")
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textSize = 21f
                color = Color.parseColor("#334155")
            }
            val boldLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 20f
                color = Color.parseColor("#0F172A")
            }
            val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 18f
                color = Color.parseColor("#059669")
            }
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            records.forEachIndexed { index, rec ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val c = page.canvas
                c.drawColor(Color.WHITE)

                // Header Banner
                boxPaint.color = Color.parseColor("#0B132B")
                c.drawRect(0f, 0f, pageW.toFloat(), 145f, boxPaint)
                boxPaint.color = Color.parseColor("#10B981")
                c.drawRect(0f, 145f, pageW.toFloat(), 153f, boxPaint)

                titlePaint.color = Color.WHITE
                titlePaint.textSize = 30f
                c.drawText(settings.agencyName.ifBlank { "PHOTO TIMESTAMP PRO" }, 60f, 62f, titlePaint)

                subPaint.color = Color.parseColor("#94A3B8")
                subPaint.textSize = 20f
                c.drawText("$reportTitle  •  Halaman ${index + 1} dari ${records.size}", 60f, 102f, subPaint)
                c.drawText("Proyek: ${rec.projectName}  |  No. Dokumen: ${rec.docNumber}", 60f, 130f, subPaint)

                // Photo Frame
                val imgTop = 190f
                val imgBottom = 1060f
                val imgRect = RectF(60f, imgTop, pageW - 60f, imgBottom)
                boxPaint.color = Color.parseColor("#F1F5F9")
                c.drawRoundRect(imgRect, 16f, 16f, boxPaint)

                val photoFile = File(rec.filePath)
                if (photoFile.exists()) {
                    val bmp = BitmapFactory.decodeFile(photoFile.absolutePath)
                    if (bmp != null) {
                        val scale = min(
                            (imgRect.width() - 24f) / bmp.width.toFloat(),
                            (imgRect.height() - 24f) / bmp.height.toFloat()
                        )
                        val drawW = bmp.width * scale
                        val drawH = bmp.height * scale
                        val left = imgRect.centerX() - drawW / 2f
                        val top = imgRect.centerY() - drawH / 2f
                        c.drawBitmap(bmp, null, RectF(left, top, left + drawW, top + drawH), null)
                    }
                }

                // Metadata Table
                var tableY = 1110f
                if (settings.pdfIncludeMetadataTable) {
                    boxPaint.color = Color.parseColor("#F8FAFC")
                    val tableRect = RectF(60f, tableY, pageW - 60f, 1500f)
                    c.drawRoundRect(tableRect, 14f, 14f, boxPaint)

                    boxPaint.style = Paint.Style.STROKE
                    boxPaint.strokeWidth = 2f
                    boxPaint.color = Color.parseColor("#CBD5E1")
                    c.drawRoundRect(tableRect, 14f, 14f, boxPaint)
                    boxPaint.style = Paint.Style.FILL

                    val rows = listOf(
                        "Nomor Dokumentasi" to "${rec.docNumber}   (Kode Unik: ${rec.uniqueCode})",
                        "Waktu Pengambilan" to rec.formattedTimestamp,
                        "Lokasi / Alamat" to rec.address,
                        "Koordinat GPS" to "${rec.formattedCoordinates}  (Alt: ${String.format("%.1fm", rec.altitude)}, Akurasi: ±${String.format("%.1fm", rec.accuracy)})",
                        "Petugas & Instansi" to "${rec.officerName} — ${rec.officerRole} (${rec.agencyName})",
                        "Catatan Lapangan" to rec.customNote.ifBlank { "-" },
                        "SHA-256 Integritas" to rec.sha256Hash
                    )

                    var rowY = tableY + 46f
                    for ((label, value) in rows) {
                        c.drawText(label, 88f, rowY, boldLabelPaint)
                        if (label.contains("SHA-256")) {
                            c.drawText(value, 360f, rowY, monoPaint)
                        } else {
                            subPaint.color = Color.parseColor("#1E293B")
                            c.drawText(value.take(68), 360f, rowY, subPaint)
                        }
                        rowY += 52f
                    }
                    tableY = 1525f
                }

                // Footer Signature & QR Verification
                if (settings.pdfIncludeSignatureBlock) {
                    val qrBmp = FormatUtils.getOrCreateQrBitmap(
                        "PTP-PDF|${rec.docNumber}|${rec.sha256Hash.take(20)}",
                        160
                    )
                    c.drawBitmap(qrBmp, null, RectF(60f, tableY, 210f, tableY + 150f), null)
                    subPaint.color = Color.parseColor("#475569")
                    subPaint.textSize = 18f
                    c.drawText("Dokumen ini diverifikasi secara kriptografi (SHA-256 Offline Evidence).", 230f, tableY + 55f, subPaint)
                    c.drawText("Dicetak oleh Photo Timestamp Pro pada ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}", 230f, tableY + 90f, subPaint)

                    val sigBmp = repository.getSignatureBitmap()
                    if (sigBmp != null) {
                        c.drawBitmap(sigBmp, null, RectF(pageW - 340f, tableY, pageW - 80f, tableY + 100f), null)
                    }
                    boldLabelPaint.textAlign = Paint.Align.CENTER
                    c.drawText(rec.officerName, pageW - 210f, tableY + 130f, boldLabelPaint)
                    subPaint.textAlign = Paint.Align.CENTER
                    c.drawText(rec.officerRole, pageW - 210f, tableY + 156f, subPaint)
                    boldLabelPaint.textAlign = Paint.Align.LEFT
                    subPaint.textAlign = Paint.Align.LEFT
                }

                pdfDoc.finishPage(page)
            }

            val pdfFileName = "Laporan_PTP_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
            val pdfFile = File(repository.exportedReportsDir, pdfFileName)
            FileOutputStream(pdfFile).use { out ->
                pdfDoc.writeTo(out)
            }
            pdfDoc.close()
            pdfFile
        } catch (_: Exception) {
            null
        }
    }

    fun sharePhotoDirectToWhatsApp(context: Context, record: PhotoRecord) {
        val file = File(record.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File foto tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mapsLink = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", record.latitude, record.longitude)
        val caption = buildString {
            append("*DOKUMENTASI PHOTO TIMESTAMP PRO*\n")
            append("No. Dok: *${record.docNumber}* (${record.uniqueCode})\n")
            append("Proyek: ${record.projectName}\n")
            append("Waktu: ${record.formattedTimestamp}\n")
            append("Lokasi: ${record.address}\n")
            append("GPS: ${record.formattedCoordinates}\n")
            append("Maps: $mapsLink\n")
            append("Petugas: ${record.officerName} (${record.agencyName})\n")
            append("SHA-256: `${record.sha256Hash.take(20)}...`")
        }

        val targetPackage = when {
            isPackageInstalled(context, "com.whatsapp") -> "com.whatsapp"
            isPackageInstalled(context, "com.whatsapp.w4b") -> "com.whatsapp.w4b"
            else -> null
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (record.fileName.endsWith(".png", true)) "image/png" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (targetPackage != null) {
                setPackage(targetPackage)
            }
        }

        try {
            if (targetPackage != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(
                    context,
                    "WhatsApp tidak terdeteksi, membuka menu Bagikan...",
                    Toast.LENGTH_SHORT
                ).show()
                context.startActivity(Intent.createChooser(intent, "Bagikan Dokumentasi Foto"))
            }
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "Bagikan Dokumentasi Foto"))
        }
    }

    fun shareFileGeneral(context: Context, file: File, mimeType: String, title: String) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDimension || boundsOpts.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun orientAndCropBitmap(
        source: Bitmap,
        isFrontCamera: Boolean,
        mirrorFront: Boolean,
        deviceOrientationDegrees: Int,
        aspectRatio: CameraAspectRatioPreset,
        resolution: CameraResolutionPreset
    ): Bitmap {
        val matrix = Matrix()
        if (isFrontCamera && mirrorFront) {
            matrix.postScale(-1f, 1f)
        }
        when (deviceOrientationDegrees) {
            90 -> matrix.postRotate(-90f)
            270 -> matrix.postRotate(90f)
            180 -> matrix.postRotate(180f)
        }

        val rotated = if (!matrix.isIdentity) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        val isLandscape = rotated.width > rotated.height
        val targetRatio = when (aspectRatio) {
            CameraAspectRatioPreset.RATIO_1_1 -> 1f
            CameraAspectRatioPreset.RATIO_16_9 -> if (isLandscape) 16f / 9f else 9f / 16f
            CameraAspectRatioPreset.RATIO_4_3 -> if (isLandscape) 4f / 3f else 3f / 4f
        }

        val currentRatio = rotated.width.toFloat() / rotated.height.toFloat()
        val cropW: Int
        val cropH: Int
        if (abs(currentRatio - targetRatio) < 0.03f) {
            cropW = rotated.width
            cropH = rotated.height
        } else if (currentRatio > targetRatio) {
            cropH = rotated.height
            cropW = (cropH * targetRatio).toInt().coerceIn(1, rotated.width)
        } else {
            cropW = rotated.width
            cropH = (cropW / targetRatio).toInt().coerceIn(1, rotated.height)
        }

        val cropX = ((rotated.width - cropW) / 2).coerceAtLeast(0)
        val cropY = ((rotated.height - cropH) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(rotated, cropX, cropY, cropW, cropH)

        val maxSideTarget = resolution.width
        val currentLongSide = maxOf(cropped.width, cropped.height)
        return if (currentLongSide != maxSideTarget && currentLongSide > 0) {
            val scale = maxSideTarget.toFloat() / currentLongSide.toFloat()
            val finalW = (cropped.width * scale).toInt().coerceAtLeast(320)
            val finalH = (cropped.height * scale).toInt().coerceAtLeast(320)
            Bitmap.createScaledBitmap(cropped, finalW, finalH, true)
        } else {
            cropped
        }
    }

    private fun drawCenterCropBitmap(canvas: Canvas, bmp: Bitmap, dest: RectF) {
        val save = canvas.save()
        val path = Path().apply { addRoundRect(dest, 14f, 14f, Path.Direction.CW) }
        canvas.clipPath(path)
        val scale = maxOf(dest.width() / bmp.width.toFloat(), dest.height() / bmp.height.toFloat())
        val w = bmp.width * scale
        val h = bmp.height * scale
        val left = dest.centerX() - w / 2f
        val top = dest.centerY() - h / 2f
        canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), null)
        canvas.restoreToCount(save)
    }

    private fun drawComparisonBadge(canvas: Canvas, left: Float, top: Float, label: String, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = 24f
        val textW = paint.measureText(label)
        val rect = RectF(left, top, left + textW + 32f, top + 44f)
        paint.color = Color.argb(220, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRoundRect(rect, 10f, 10f, paint)
        paint.color = Color.BLACK
        canvas.drawText(label, left + 16f, top + 30f, paint)
    }

    private fun saveFileToMediaStore(context: Context, file: File, mimeType: String) {
        runCatching {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/PhotoTimestampPro")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
