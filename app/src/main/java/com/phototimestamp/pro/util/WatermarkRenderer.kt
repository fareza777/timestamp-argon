package com.phototimestamp.pro.util

import android.graphics.*
import com.phototimestamp.pro.model.*
import kotlin.math.max
import kotlin.math.min

data class WatermarkRenderData(
    val timestampMillis: Long = System.currentTimeMillis(),
    val gpsState: GpsLocationState = GpsLocationState(),
    val projectName: String = "Inspeksi & Dokumentasi Umum",
    val projectCode: String = "PRJ-UMUM-01",
    val docNumber: String = "DOC-2026-0001",
    val uniqueCode: String = "PTP-261014-8F3A",
    val sha256Preview: String = "e3b0c44298fc1c149afbf4c8996fb924",
    val signatureBitmap: Bitmap? = null,
    val customLogoBitmap: Bitmap? = null
)

object WatermarkRenderer {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Draws the complete professional watermark & signature stamp onto any Android Canvas.
     * Supports live preview (including smooth rotation in Landscape so bottom-right stays at
     * the bottom-right of the landscape frame!) as well as full-resolution Bitmap rendering.
     *
     * Returns the bounding RectF in the unrotated canvas space for interactive Drag & Drop.
     */
    fun drawWatermarkOnCanvas(
        canvas: Canvas,
        canvasWidth: Float,
        canvasHeight: Float,
        template: WatermarkTemplate,
        settings: AppSettings,
        data: WatermarkRenderData,
        deviceOrientationDegrees: Int = 0,
        bottomSafeInsetPx: Float = 0f,
        topSafeInsetPx: Float = 0f
    ): RectF {
        if (canvasWidth <= 10f || canvasHeight <= 10f) return RectF()

        val saveCount = canvas.save()

        val rotateForLandscape = settings.autoRotateLandscapeWatermark &&
            (deviceOrientationDegrees == 90 || deviceOrientationDegrees == 270 || deviceOrientationDegrees == 180)

        val virtualW: Float
        val virtualH: Float
        val effTopInset: Float
        val effBottomInset: Float

        if (rotateForLandscape) {
            when (deviceOrientationDegrees) {
                90 -> {
                    // Device rotated counter-clockwise (Landscape Left):
                    // Rotate canvas -90 (or +270) so text is upright for the user in Landscape
                    canvas.translate(0f, canvasHeight)
                    canvas.rotate(-90f)
                    virtualW = canvasHeight
                    virtualH = canvasWidth
                    effTopInset = 12f
                    effBottomInset = 12f
                }
                270 -> {
                    // Device rotated clockwise (Landscape Right):
                    // Rotate canvas +90 so text is upright for the user in Landscape
                    canvas.translate(canvasWidth, 0f)
                    canvas.rotate(90f)
                    virtualW = canvasHeight
                    virtualH = canvasWidth
                    effTopInset = 12f
                    effBottomInset = 12f
                }
                180 -> {
                    canvas.translate(canvasWidth, canvasHeight)
                    canvas.rotate(180f)
                    virtualW = canvasWidth
                    virtualH = canvasHeight
                    effTopInset = bottomSafeInsetPx
                    effBottomInset = topSafeInsetPx
                }
                else -> {
                    virtualW = canvasWidth
                    virtualH = canvasHeight
                    effTopInset = topSafeInsetPx
                    effBottomInset = bottomSafeInsetPx
                }
            }
        } else {
            virtualW = canvasWidth
            virtualH = canvasHeight
            effTopInset = topSafeInsetPx
            effBottomInset = bottomSafeInsetPx
        }

        // Scale relative to short side so watermark looks proportional on any resolution (720p..4K)
        val shortSide = min(virtualW, virtualH)
        val baseUnit = (shortSide / 390f) * template.fontSizeScale.coerceIn(0.6f, 1.8f)

        val typefaceNormal = resolveTypeface(template.fontFamily, bold = false)
        val typefaceBold = resolveTypeface(template.fontFamily, bold = true)

        val lat = if (settings.useManualLocationOverride) settings.manualLatitude else data.gpsState.latitude
        val lon = if (settings.useManualLocationOverride) settings.manualLongitude else data.gpsState.longitude
        val alt = if (settings.useManualLocationOverride) settings.manualAltitude else data.gpsState.altitude

        // Build content lines
        val timeString = if (template.showTime) {
            FormatUtils.formatTime(data.timestampMillis, settings, forceMillis = template.showMilliseconds)
        } else ""
        val dateString = if (template.showDate) {
            FormatUtils.formatDate(data.timestampMillis, settings)
        } else ""

        val isCleanTextOnly = template.backgroundStyle == WatermarkBackgroundStyle.TRANSPARENT_CLEAN &&
            !template.showHeaderBadge && !template.showQrCode && !template.showSignatureStamp && !template.showAccentBar

        val detailLines = mutableListOf<Pair<String, Boolean>>() // (text, isAccentOrHighlight)

        if (isCleanTextOnly) {
            // Classic Clean Timestamp Camera layout:
            // 1) Tanggal & Jam (satu baris rapi di atas atau tanggal di bawah jam)
            // 2) Koordinat GPS
            // 3) Lokasi / Alamat
            val combinedDateTime = listOf(dateString, timeString).filter { it.isNotBlank() }.joinToString("  ")
            if (combinedDateTime.isNotBlank()) {
                detailLines.add(combinedDateTime to false)
            }
            if (template.showGpsCoordinates && settings.gpsEnabled) {
                val coordStr = FormatUtils.formatCoordinates(
                    lat = lat,
                    lon = lon,
                    format = settings.coordinateFormat,
                    privacyMask = settings.privacyMaskExactCoordinates
                )
                detailLines.add(coordStr to false)
            }
            if (template.showAddress && settings.gpsEnabled) {
                val addr = if (settings.useManualLocationOverride) {
                    settings.manualAddressText
                } else {
                    data.gpsState.fullAddress(
                        includeStreet = settings.showStreetAddress,
                        includeCity = settings.showSubDistrictAndCity,
                        includePostal = settings.showPostalCode
                    )
                }
                if (addr.isNotBlank()) {
                    wrapTextToLines(addr, maxChars = 42).forEach { line ->
                        detailLines.add(line to false)
                    }
                }
            }
        } else {
            if (dateString.isNotBlank()) {
                detailLines.add(dateString to true)
            }

            if (template.showAddress && settings.gpsEnabled) {
                val addr = if (settings.useManualLocationOverride) {
                    settings.manualAddressText
                } else {
                    data.gpsState.fullAddress(
                        includeStreet = settings.showStreetAddress,
                        includeCity = settings.showSubDistrictAndCity,
                        includePostal = settings.showPostalCode
                    )
                }
                if (addr.isNotBlank()) {
                    wrapTextToLines(addr, maxChars = 42).forEach { line ->
                        detailLines.add(line to false)
                    }
                }
            }

            if (template.showGpsCoordinates && settings.gpsEnabled) {
                val coordStr = FormatUtils.formatCoordinates(
                    lat = lat,
                    lon = lon,
                    format = settings.coordinateFormat,
                    privacyMask = settings.privacyMaskExactCoordinates
                )
                detailLines.add("GPS: $coordStr" to false)
            }
        }

        // Telemetry line: Altitude, Accuracy, Speed, Compass
        if (settings.gpsEnabled) {
            val telemetryParts = mutableListOf<String>()
            if (template.showAltitude && settings.showAltitudeMeters) {
                telemetryParts.add(String.format("Alt: %.1fm mdpl", alt))
            }
            if (template.showAccuracy && settings.showGpsAccuracy) {
                telemetryParts.add(String.format("Akurasi: ±%.1fm", data.gpsState.accuracyMeters))
            }
            if (template.showCompass && settings.showCompassAzimuth) {
                telemetryParts.add("Arah: ${FormatUtils.formatCompassDirection(data.gpsState.bearingAzimuth)}")
            }
            if (template.showSpeed && settings.showSpeedKmh) {
                telemetryParts.add(String.format("Kec: %.1f km/j", data.gpsState.speedKmh))
            }
            if (telemetryParts.isNotEmpty()) {
                detailLines.add(telemetryParts.joinToString(" • ") to false)
            }
        }

        if (template.showProjectInfo && data.projectName.isNotBlank()) {
            detailLines.add("Proyek: ${data.projectName} (${data.projectCode})" to true)
        }

        if (template.showOfficerIdentity) {
            val officerParts = mutableListOf<String>()
            if (settings.officerName.isNotBlank()) officerParts.add(settings.officerName)
            if (settings.officerRole.isNotBlank()) officerParts.add(settings.officerRole)
            if (officerParts.isNotEmpty()) {
                detailLines.add("Petugas: ${officerParts.joinToString(" - ")}" to false)
            }
            if (settings.agencyName.isNotBlank()) {
                detailLines.add(settings.agencyName to false)
            }
        }

        if (template.showDocNumber || template.showUniqueCode) {
            val docParts = mutableListOf<String>()
            if (template.showDocNumber && data.docNumber.isNotBlank()) {
                docParts.add("No: ${data.docNumber}")
            }
            if (template.showUniqueCode && data.uniqueCode.isNotBlank()) {
                docParts.add("Kode: ${data.uniqueCode}")
            }
            if (docParts.isNotEmpty()) {
                detailLines.add(docParts.joinToString(" | ") to true)
            }
        }

        if (template.showCustomNote && settings.customNoteText.isNotBlank()) {
            wrapTextToLines("Catatan: ${settings.customNoteText}", maxChars = 44).forEach {
                detailLines.add(it to false)
            }
        }

        if ((template.showSha256Hash || settings.showShortHashOnPhoto) && settings.evidenceModeEnabled) {
            val shortHash = data.sha256Preview.take(16).uppercase()
            detailLines.add("SHA-256: $shortHash..." to true)
        }

        // Font sizes
        val timeFontSize = if (isCleanTextOnly) 0f else 23f * baseUnit
        val badgeFontSize = 9.5f * baseUnit
        val bodyFontSize = if (isCleanTextOnly) 12.8f * baseUnit else 11.2f * baseUnit
        val lineSpacing = if (isCleanTextOnly) 5.0f * baseUnit else 4.2f * baseUnit
        val paddingH = if (isCleanTextOnly) 2f * baseUnit else 12f * baseUnit
        val paddingV = if (isCleanTextOnly) 2f * baseUnit else 10f * baseUnit

        // Measure widths
        textPaint.typeface = typefaceBold
        textPaint.textSize = timeFontSize
        val timeWidth = if (!isCleanTextOnly && timeString.isNotBlank()) textPaint.measureText(timeString) else 0f

        textPaint.textSize = badgeFontSize
        val badgeText = template.headerBadgeText.ifBlank { "VERIFIED TIMESTAMP" }
        val badgeWidth = if (template.showHeaderBadge) textPaint.measureText(badgeText) + (28f * baseUnit) else 0f

        textPaint.typeface = typefaceNormal
        textPaint.textSize = bodyFontSize
        var maxLineW = max(timeWidth, badgeWidth)
        for ((line, isBold) in detailLines) {
            textPaint.typeface = if (isBold) typefaceBold else typefaceNormal
            val w = textPaint.measureText(line)
            if (w > maxLineW) maxLineW = w
        }

        // Side widgets: QR code & Signature stamp
        val qrSizePx = if (template.showQrCode) (template.qrSizeDp * baseUnit).coerceAtLeast(42f) else 0f
        val showSig = template.showSignatureStamp && data.signatureBitmap != null
        val sigWidthPx = if (showSig) 86f * baseUnit else 0f
        val sigHeightPx = if (showSig) 34f * baseUnit else 0f
        val sideColumnWidth = max(qrSizePx, sigWidthPx)
        val sideColumnSpacing = if (sideColumnWidth > 0f) 10f * baseUnit else 0f

        val accentBarW = if (template.showAccentBar) 4.5f * baseUnit else 0f
        val accentBarGap = if (template.showAccentBar) 8f * baseUnit else 0f

        val isFullBanner = template.backgroundStyle == WatermarkBackgroundStyle.FULL_FOOTER_BANNER
        val contentWidth = maxLineW + sideColumnWidth + sideColumnSpacing + accentBarW + accentBarGap + (paddingH * 2f)
        val boxWidth = if (isFullBanner) virtualW else min(virtualW * 0.94f, max(contentWidth, 210f * baseUnit))

        // Calculate total height
        var textColumnHeight = 0f
        if (template.showHeaderBadge) {
            textColumnHeight += badgeFontSize + (6f * baseUnit)
        }
        if (!isCleanTextOnly && timeString.isNotBlank()) {
            textColumnHeight += timeFontSize + (5f * baseUnit)
        }
        textColumnHeight += detailLines.size * (bodyFontSize + lineSpacing)

        var sideColumnHeight = 0f
        if (qrSizePx > 0f) sideColumnHeight += qrSizePx + (4f * baseUnit)
        if (showSig) sideColumnHeight += sigHeightPx + (14f * baseUnit)

        val boxHeight = max(textColumnHeight, sideColumnHeight) + (paddingV * 2f)
        val marginPx = if (isFullBanner) 0f else template.marginDp * baseUnit

        // Determine Box X & Y based on WatermarkPosition (Default: BOTTOM_RIGHT)
        val boxLeft: Float
        val boxTop: Float

        when (template.position) {
            WatermarkPosition.TOP_LEFT -> {
                boxLeft = marginPx
                boxTop = marginPx + effTopInset
            }
            WatermarkPosition.TOP_CENTER -> {
                boxLeft = (virtualW - boxWidth) / 2f
                boxTop = marginPx + effTopInset
            }
            WatermarkPosition.TOP_RIGHT -> {
                boxLeft = virtualW - boxWidth - marginPx
                boxTop = marginPx + effTopInset
            }
            WatermarkPosition.CENTER_LEFT -> {
                boxLeft = marginPx
                boxTop = (virtualH - boxHeight) / 2f
            }
            WatermarkPosition.CENTER -> {
                boxLeft = (virtualW - boxWidth) / 2f
                boxTop = (virtualH - boxHeight) / 2f
            }
            WatermarkPosition.CENTER_RIGHT -> {
                boxLeft = virtualW - boxWidth - marginPx
                boxTop = (virtualH - boxHeight) / 2f
            }
            WatermarkPosition.BOTTOM_LEFT -> {
                boxLeft = marginPx
                boxTop = virtualH - boxHeight - marginPx - effBottomInset
            }
            WatermarkPosition.BOTTOM_CENTER -> {
                boxLeft = (virtualW - boxWidth) / 2f
                boxTop = virtualH - boxHeight - marginPx - effBottomInset
            }
            WatermarkPosition.BOTTOM_RIGHT -> {
                boxLeft = virtualW - boxWidth - marginPx
                boxTop = virtualH - boxHeight - marginPx - effBottomInset
            }
            WatermarkPosition.CUSTOM_DRAG -> {
                val availW = (virtualW - boxWidth - (marginPx * 2f)).coerceAtLeast(1f)
                val availH = (virtualH - boxHeight - effTopInset - effBottomInset - (marginPx * 2f)).coerceAtLeast(1f)
                boxLeft = marginPx + (template.customXFraction.coerceIn(0f, 1f) * availW)
                boxTop = effTopInset + marginPx + (template.customYFraction.coerceIn(0f, 1f) * availH)
            }
        }

        val boxRect = RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight)
        val cornerRadiusPx = if (isFullBanner) 0f else template.cornerRadiusDp * baseUnit

        // 1. Draw Background Card / Banner
        if (template.backgroundStyle != WatermarkBackgroundStyle.TRANSPARENT_CLEAN && template.backgroundOpacity > 0.01f) {
            val baseBgColor = template.backgroundColorArgb.toInt()
            val alpha = (template.backgroundOpacity.coerceIn(0f, 1f) * 255).toInt()
            val bgColorWithAlpha = Color.argb(
                alpha,
                Color.red(baseBgColor),
                Color.green(baseBgColor),
                Color.blue(baseBgColor)
            )

            bgPaint.shader = null
            if (template.backgroundStyle == WatermarkBackgroundStyle.GRADIENT_PANEL) {
                val accentInt = template.accentColorArgb.toInt()
                val gradEnd = Color.argb(
                    (alpha * 0.85f).toInt(),
                    (Color.red(baseBgColor) * 0.7f + Color.red(accentInt) * 0.3f).toInt(),
                    (Color.green(baseBgColor) * 0.7f + Color.green(accentInt) * 0.3f).toInt(),
                    (Color.blue(baseBgColor) * 0.7f + Color.blue(accentInt) * 0.3f).toInt()
                )
                bgPaint.shader = LinearGradient(
                    boxRect.left, boxRect.top, boxRect.right, boxRect.bottom,
                    bgColorWithAlpha, gradEnd, Shader.TileMode.CLAMP
                )
            } else {
                bgPaint.color = bgColorWithAlpha
            }

            if (template.showShadow) {
                bgPaint.setShadowLayer(
                    template.shadowRadius * baseUnit,
                    0f,
                    2f * baseUnit,
                    template.shadowColorArgb.toInt()
                )
            } else {
                bgPaint.clearShadowLayer()
            }

            canvas.drawRoundRect(boxRect, cornerRadiusPx, cornerRadiusPx, bgPaint)
            bgPaint.clearShadowLayer()
        }

        // 2. Draw Border
        if (template.showBorder && template.backgroundStyle != WatermarkBackgroundStyle.TRANSPARENT_CLEAN) {
            borderPaint.color = template.borderColorArgb.toInt()
            borderPaint.strokeWidth = (template.borderWidthDp * baseUnit).coerceAtLeast(1f)
            canvas.drawRoundRect(boxRect, cornerRadiusPx, cornerRadiusPx, borderPaint)
        }

        // 3. Determine internal layout columns:
        // If alignment is RIGHT (default), text is on the right side (with optional right accent bar),
        // and the QR Code + Signature column sits neatly on the left side of the card!
        // If alignment is LEFT, QR + Signature sits on the right side, and text is on the left.
        val isRightAligned = template.alignment == WatermarkAlignment.RIGHT
        val isCenterAligned = template.alignment == WatermarkAlignment.CENTER
        val accentInt = template.accentColorArgb.toInt()

        val innerLeft = boxRect.left + paddingH
        val innerRight = boxRect.right - paddingH
        val innerTop = boxRect.top + paddingV

        // Draw Accent Bar
        var textAreaLeft = innerLeft
        var textAreaRight = innerRight

        if (template.showAccentBar) {
            accentPaint.color = accentInt
            val barOnRight = if (isRightAligned) true else template.accentBarOnRight
            if (barOnRight) {
                val barRect = RectF(
                    innerRight - accentBarW,
                    innerTop,
                    innerRight,
                    boxRect.bottom - paddingV
                )
                canvas.drawRoundRect(barRect, accentBarW / 2f, accentBarW / 2f, accentPaint)
                textAreaRight -= (accentBarW + accentBarGap)
            } else {
                val barRect = RectF(
                    innerLeft,
                    innerTop,
                    innerLeft + accentBarW,
                    boxRect.bottom - paddingV
                )
                canvas.drawRoundRect(barRect, accentBarW / 2f, accentBarW / 2f, accentPaint)
                textAreaLeft += (accentBarW + accentBarGap)
            }
        }

        // 4. Draw QR Code & Signature Stamp in Side Column
        if (sideColumnWidth > 0f) {
            val sideColLeft = if (isRightAligned) {
                textAreaLeft
            } else {
                textAreaRight - sideColumnWidth
            }
            if (isRightAligned) {
                textAreaLeft += sideColumnWidth + sideColumnSpacing
            } else {
                textAreaRight -= (sideColumnWidth + sideColumnSpacing)
            }

            var sideY = innerTop
            if (qrSizePx > 0f) {
                val qrContent = settings.customQrContentOverride.ifBlank {
                    "PTP|${data.docNumber}|${data.uniqueCode}|${String.format("%.5f,%.5f", lat, lon)}|${data.sha256Preview.take(12)}"
                }
                val qrBmp = FormatUtils.getOrCreateQrBitmap(qrContent, 160)
                val qrDest = RectF(
                    sideColLeft,
                    sideY,
                    sideColLeft + qrSizePx,
                    sideY + qrSizePx
                )
                badgePaint.color = Color.WHITE
                canvas.drawRoundRect(
                    RectF(qrDest.left - 2f, qrDest.top - 2f, qrDest.right + 2f, qrDest.bottom + 2f),
                    4f * baseUnit,
                    4f * baseUnit,
                    badgePaint
                )
                canvas.drawBitmap(qrBmp, null, qrDest, null)
                sideY += qrSizePx + (5f * baseUnit)
            }

            if (showSig && data.signatureBitmap != null) {
                val sigDest = RectF(
                    sideColLeft,
                    sideY,
                    sideColLeft + sigWidthPx,
                    sideY + sigHeightPx
                )
                // Subtle signature box
                badgePaint.color = Color.argb(60, 255, 255, 255)
                canvas.drawRoundRect(sigDest, 4f * baseUnit, 4f * baseUnit, badgePaint)
                canvas.drawBitmap(data.signatureBitmap, null, sigDest, null)

                textPaint.typeface = typefaceBold
                textPaint.textSize = 7.5f * baseUnit
                textPaint.color = accentInt
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.clearShadowLayer()
                canvas.drawText(
                    "TTD TERVERIFIKASI",
                    sigDest.centerX(),
                    sigDest.bottom + (9f * baseUnit),
                    textPaint
                )
            }
        }

        // 5. Draw Header Badge + Preset Logo Emblem + Main Timestamp + Detail Lines
        val textAnchorX = when (template.alignment) {
            WatermarkAlignment.RIGHT -> textAreaRight
            WatermarkAlignment.CENTER -> (textAreaLeft + textAreaRight) / 2f
            WatermarkAlignment.LEFT -> textAreaLeft
        }
        val paintAlign = when (template.alignment) {
            WatermarkAlignment.RIGHT -> Paint.Align.RIGHT
            WatermarkAlignment.CENTER -> Paint.Align.CENTER
            WatermarkAlignment.LEFT -> Paint.Align.LEFT
        }

        val baseTextColor = template.textColorArgb.toInt()
        val textAlpha = (template.textOpacity.coerceIn(0.1f, 1f) * 255).toInt()
        val mainTextColor = Color.argb(
            textAlpha,
            Color.red(baseTextColor),
            Color.green(baseTextColor),
            Color.blue(baseTextColor)
        )

        var cursorY = innerTop

        // Header Badge + Logo Preset
        if (template.showHeaderBadge) {
            val pillH = badgeFontSize + (6f * baseUnit)
            textPaint.typeface = typefaceBold
            textPaint.textSize = badgeFontSize
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.clearShadowLayer()
            val labelW = textPaint.measureText(badgeText)
            val logoSize = if (template.logoPreset != WatermarkLogoPreset.NONE) pillH * 0.85f else 0f
            val totalBadgeW = labelW + logoSize + (14f * baseUnit)

            val pillLeft = when {
                isRightAligned -> textAreaRight - totalBadgeW
                isCenterAligned -> ((textAreaLeft + textAreaRight) / 2f) - (totalBadgeW / 2f)
                else -> textAreaLeft
            }
            val pillRect = RectF(pillLeft, cursorY, pillLeft + totalBadgeW, cursorY + pillH)
            badgePaint.color = Color.argb(
                58,
                Color.red(accentInt),
                Color.green(accentInt),
                Color.blue(accentInt)
            )
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, badgePaint)

            var badgeContentX = pillRect.left + (6f * baseUnit)
            if (template.logoPreset != WatermarkLogoPreset.NONE) {
                val logoRect = RectF(
                    badgeContentX,
                    pillRect.centerY() - logoSize / 2f,
                    badgeContentX + logoSize,
                    pillRect.centerY() + logoSize / 2f
                )
                drawPresetLogo(canvas, logoRect, template.logoPreset, accentInt, data.customLogoBitmap)
                badgeContentX += logoSize + (4f * baseUnit)
            }

            textPaint.color = accentInt
            val badgeTextY = pillRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(badgeText, badgeContentX, badgeTextY, textPaint)

            cursorY += pillH + (4f * baseUnit)
        }

        // Apply text shadow if enabled
        if (template.showShadow) {
            textPaint.setShadowLayer(
                template.shadowRadius * 0.7f * baseUnit,
                0f,
                1.5f * baseUnit,
                template.shadowColorArgb.toInt()
            )
        } else {
            textPaint.clearShadowLayer()
        }

        textPaint.textAlign = paintAlign

        // Big Real-Time Clock (only in card/pro templates; Clean Text mode shows combined Date + Time line)
        if (!isCleanTextOnly && timeString.isNotBlank()) {
            textPaint.typeface = typefaceBold
            textPaint.textSize = timeFontSize
            textPaint.color = mainTextColor
            cursorY += timeFontSize * 0.88f
            canvas.drawText(timeString, textAnchorX, cursorY, textPaint)
            cursorY += (6f * baseUnit)
        }

        // Detail Lines (Date, Address, GPS Coordinates, Telemetry, Project, Officer, Doc Number, Hash)
        textPaint.textSize = bodyFontSize
        for ((lineText, isHighlighted) in detailLines) {
            textPaint.typeface = if (isHighlighted) typefaceBold else typefaceNormal
            textPaint.color = if (isHighlighted) accentInt else mainTextColor
            cursorY += bodyFontSize
            canvas.drawText(lineText, textAnchorX, cursorY, textPaint)
            cursorY += lineSpacing
        }

        textPaint.clearShadowLayer()
        canvas.restoreToCount(saveCount)
        return boxRect
    }

    private fun drawPresetLogo(
        canvas: Canvas,
        rect: RectF,
        preset: WatermarkLogoPreset,
        accentColor: Int,
        customBitmap: Bitmap?
    ) {
        if (preset == WatermarkLogoPreset.CUSTOM_IMAGE && customBitmap != null) {
            canvas.drawBitmap(customBitmap, null, rect, null)
            return
        }
        iconPaint.style = Paint.Style.FILL
        iconPaint.color = accentColor
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() * 0.45f

        when (preset) {
            WatermarkLogoPreset.SHIELD_VERIFIED, WatermarkLogoPreset.GOVERNMENT_INSTANSI -> {
                val path = Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r, cy - r * 0.45f)
                    lineTo(cx + r * 0.8f, cy + r * 0.4f)
                    lineTo(cx, cy + r)
                    lineTo(cx - r * 0.8f, cy + r * 0.4f)
                    lineTo(cx - r, cy - r * 0.45f)
                    close()
                }
                canvas.drawPath(path, iconPaint)
            }
            WatermarkLogoPreset.CONSTRUCTION_K3, WatermarkLogoPreset.LOGISTICS_POD -> {
                canvas.drawCircle(cx, cy, r, iconPaint)
                iconPaint.color = Color.BLACK
                canvas.drawRect(cx - r * 0.5f, cy - r * 0.15f, cx + r * 0.5f, cy + r * 0.15f, iconPaint)
            }
            WatermarkLogoPreset.SURVEY_GPS, WatermarkLogoPreset.FORENSIC_EVIDENCE -> {
                iconPaint.style = Paint.Style.STROKE
                iconPaint.strokeWidth = max(2f, r * 0.28f)
                canvas.drawCircle(cx, cy, r * 0.85f, iconPaint)
                iconPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, r * 0.35f, iconPaint)
            }
            else -> {
                canvas.drawCircle(cx, cy, r * 0.8f, iconPaint)
            }
        }
    }

    private fun resolveTypeface(family: WatermarkFontFamily, bold: Boolean): Typeface {
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        return when (family) {
            WatermarkFontFamily.SANS_SERIF -> Typeface.create(Typeface.SANS_SERIF, style)
            WatermarkFontFamily.MODERN_BOLD -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
            WatermarkFontFamily.MONOSPACE, WatermarkFontFamily.TERMINAL_TECH -> Typeface.create(Typeface.MONOSPACE, style)
            WatermarkFontFamily.SERIF_FORMAL -> Typeface.create(Typeface.SERIF, style)
        }
    }

    private fun wrapTextToLines(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= maxChars) {
                current.append(" ").append(word)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.take(2)
    }
}
