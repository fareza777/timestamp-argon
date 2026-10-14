package com.phototimestamp.pro.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.phototimestamp.pro.model.AppSettings
import com.phototimestamp.pro.model.CoordinateFormat
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor

object FormatUtils {

    private val qrBitmapCache = LruCache<String, Bitmap>(24)

    fun formatDate(timestampMillis: Long, settings: AppSettings): String {
        val adjusted = timestampMillis + (settings.customTimeOffsetSeconds * 1000L)
        val locale = if (settings.language.code == "id") Locale("id", "ID") else Locale.US
        val date = Date(adjusted)
        val baseDate = try {
            SimpleDateFormat(settings.dateFormatPattern, locale).format(date)
        } catch (_: Exception) {
            SimpleDateFormat("dd MMM yyyy", locale).format(date)
        }
        return if (settings.showDayOfWeek) {
            val dayName = SimpleDateFormat("EEEE", locale).format(date)
            "$dayName, $baseDate"
        } else {
            baseDate
        }
    }

    fun formatTime(timestampMillis: Long, settings: AppSettings, forceMillis: Boolean = false): String {
        val adjusted = timestampMillis + (settings.customTimeOffsetSeconds * 1000L)
        val locale = if (settings.language.code == "id") Locale("id", "ID") else Locale.US
        val date = Date(adjusted)
        val pattern = when {
            settings.showMilliseconds || forceMillis -> {
                if (settings.use24HourFormat) "HH:mm:ss.SSS" else "hh:mm:ss.SSS a"
            }
            else -> {
                if (settings.use24HourFormat) settings.timeFormatPattern else "hh:mm:ss a"
            }
        }
        val baseTime = try {
            SimpleDateFormat(pattern, locale).format(date)
        } catch (_: Exception) {
            SimpleDateFormat("HH:mm:ss", locale).format(date)
        }
        return if (settings.showTimezoneLabel) {
            val tz = TimeZone.getDefault()
            val shortTz = when (tz.rawOffset / (3600 * 1000)) {
                7 -> "WIB"
                8 -> "WITA"
                9 -> "WIT"
                else -> tz.getDisplayName(false, TimeZone.SHORT, locale)
            }
            "$baseTime $shortTz"
        } else {
            baseTime
        }
    }

    fun formatCoordinates(
        lat: Double,
        lon: Double,
        format: CoordinateFormat,
        privacyMask: Boolean = false
    ): String {
        if (privacyMask) {
            return String.format(Locale.US, "%.2f***°, %.2f***°", lat, lon)
        }
        return when (format) {
            CoordinateFormat.DECIMAL -> {
                val latDir = if (lat >= 0) "N" else "S"
                val lonDir = if (lon >= 0) "E" else "W"
                String.format(Locale.US, "%.6f° %s, %.6f° %s", abs(lat), latDir, abs(lon), lonDir)
            }
            CoordinateFormat.DMS -> {
                "${toDms(lat, true)}  ${toDms(lon, false)}"
            }
            CoordinateFormat.UTM_GRID -> {
                val zone = floor((lon + 180.0) / 6.0).toInt() + 1
                val band = if (lat >= 0) "N" else "M"
                val easting = ((lon - ((zone - 1) * 6 - 180 + 3)) * 111320.0 + 500000.0).toInt()
                val northing = ((if (lat < 0) 10000000.0 else 0.0) + lat * 110574.0).toInt()
                "UTM ${zone}${band} ${easting}E ${northing}N"
            }
        }
    }

    private fun toDms(coord: Double, isLat: Boolean): String {
        val dir = if (isLat) {
            if (coord >= 0) "N" else "S"
        } else {
            if (coord >= 0) "E" else "W"
        }
        val absCoord = abs(coord)
        val deg = floor(absCoord).toInt()
        val minFull = (absCoord - deg) * 60.0
        val min = floor(minFull).toInt()
        val sec = (minFull - min) * 60.0
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", deg, min, sec, dir)
    }

    fun formatCompassDirection(azimuthDegrees: Float): String {
        val norm = ((azimuthDegrees % 360f) + 360f) % 360f
        val dir = when {
            norm < 22.5f || norm >= 337.5f -> "U (N)"
            norm < 67.5f -> "TL (NE)"
            norm < 112.5f -> "T (E)"
            norm < 157.5f -> "TG (SE)"
            norm < 202.5f -> "S (S)"
            norm < 247.5f -> "BD (SW)"
            norm < 292.5f -> "B (W)"
            else -> "BL (NW)"
        }
        return String.format(Locale.US, "%.0f° %s", norm, dir)
    }

    fun generateUniqueCode(prefix: String, timestampMillis: Long, lat: Double, lon: Double): String {
        val seed = "$prefix|$timestampMillis|${String.format(Locale.US, "%.4f,%.4f", lat, lon)}"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        val hex = digest.take(3).joinToString("") { "%02X".format(it) }
        val dateCode = SimpleDateFormat("yyMMdd", Locale.US).format(Date(timestampMillis))
        val cleanPrefix = prefix.trim().ifEmpty { "PTP" }
        return "$cleanPrefix-$dateCode-$hex"
    }

    fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun getOrCreateQrBitmap(content: String, sizePx: Int = 180): Bitmap {
        val safeContent = content.ifBlank { "https://phototimestamp.pro/verified" }
        val key = "$sizePx|$safeContent"
        qrBitmapCache.get(key)?.let { return it }

        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(safeContent, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
            qrBitmapCache.put(key, bmp)
            bmp
        } catch (_: Exception) {
            val fallback = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            fallback.eraseColor(Color.WHITE)
            fallback
        }
    }
}
