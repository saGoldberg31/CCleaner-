package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.model.AnalyzedPhotoItem
import com.example.model.PhotoCompressionPreset
import com.example.model.PhotoQualityIssue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

data class PhotoCompressionResult(
    val processedCount: Int,
    val originalBytes: Long,
    val compressedBytes: Long
) {
    val savedBytes: Long
        get() = (originalBytes - compressedBytes).coerceAtLeast(0L)
}

class PhotoOptimizerEngine(private val context: Context) {

    suspend fun analyzePhotos(
        uris: List<Uri>,
        preset: PhotoCompressionPreset
    ): List<AnalyzedPhotoItem> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext emptyList()

        val seenHashes = mutableMapOf<String, String>()
        val results = mutableListOf<AnalyzedPhotoItem>()

        for (uri in uris) {
            val (name, sizeBytes) = queryMeta(uri)
            val hash = computeSha256(uri)
            val isDuplicate = if (hash != null) {
                if (hash in seenHashes) {
                    true
                } else {
                    seenHashes[hash] = name
                    false
                }
            } else false

            val (origW, origH) = readImageBounds(uri)
            val sampleBitmap = decodeDownsampled(uri, maxSidePx = 256)
            val (sharpness, meanLum) = if (sampleBitmap != null) {
                computeQualityMetrics(sampleBitmap)
            } else {
                100.0 to 128.0
            }

            val issue = when {
                isDuplicate -> PhotoQualityIssue.DUPLICATE
                sharpness < 65.0 -> PhotoQualityIssue.BLURRY
                meanLum < 32.0 -> PhotoQualityIssue.UNDEREXPOSED
                meanLum > 235.0 -> PhotoQualityIssue.OVEREXPOSED
                else -> PhotoQualityIssue.NONE
            }

            val estimatedBytes = estimateCompressedBytes(
                sampleBitmap = sampleBitmap,
                originalBytes = sizeBytes,
                origW = origW,
                origH = origH,
                preset = preset
            )

            val thumbnail = sampleBitmap?.let {
                runCatching { Bitmap.createScaledBitmap(it, 96, 96, true) }.getOrNull()
            }
            if (sampleBitmap != null && sampleBitmap != thumbnail) {
                sampleBitmap.recycle()
            }

            results.add(
                AnalyzedPhotoItem(
                    uri = uri,
                    displayName = name,
                    sizeBytes = sizeBytes,
                    width = origW,
                    height = origH,
                    sharpnessScore = sharpness,
                    meanLuminance = meanLum,
                    issue = issue,
                    duplicateGroupHash = hash?.take(8),
                    estimatedCompressedBytes = estimatedBytes,
                    thumbnail = thumbnail,
                    isSelected = issue != PhotoQualityIssue.NONE || estimatedBytes < sizeBytes
                )
            )
        }

        results.sortedByDescending { it.estimatedSavedBytes }
    }

    suspend fun reestimateWithPreset(
        items: List<AnalyzedPhotoItem>,
        preset: PhotoCompressionPreset
    ): List<AnalyzedPhotoItem> = withContext(Dispatchers.IO) {
        items.map { item ->
            val est = estimateFromDimensions(
                originalBytes = item.sizeBytes,
                origW = item.width,
                origH = item.height,
                preset = preset
            )
            item.copy(estimatedCompressedBytes = est)
        }
    }

    suspend fun compressAndSavePhotos(
        items: List<AnalyzedPhotoItem>,
        preset: PhotoCompressionPreset
    ): PhotoCompressionResult = withContext(Dispatchers.IO) {
        var processed = 0
        var originalTotal = 0L
        var compressedTotal = 0L

        for (item in items) {
            val bitmap = decodeDownsampled(item.uri, maxSidePx = preset.maxDimensionPx) ?: continue
            val scaled = scaleToMaxDimension(bitmap, preset.maxDimensionPx)
            if (scaled != bitmap) bitmap.recycle()

            val outBytes = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, preset.quality, outBytes)
            scaled.recycle()

            val compressedData = outBytes.toByteArray()
            val outName = "cleanpro_${System.currentTimeMillis()}_${item.displayName.substringBeforeLast('.')}.jpg"

            val savedOk = saveOptimizedJpeg(outName, compressedData)
            if (savedOk) {
                processed++
                originalTotal += item.sizeBytes
                compressedTotal += compressedData.size.toLong()
            }
        }

        PhotoCompressionResult(
            processedCount = processed,
            originalBytes = originalTotal,
            compressedBytes = compressedTotal
        )
    }

    private fun saveOptimizedJpeg(fileName: String, bytes: ByteArray): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/CleanPro"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val destUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(destUri)?.use { stream: OutputStream ->
                    stream.write(bytes)
                } ?: return false
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(destUri, values, null, null)
                true
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "CleanPro"
                ).apply { mkdirs() }
                File(dir, fileName).writeBytes(bytes)
                true
            }
        }.getOrDefault(false)
    }

    private fun estimateCompressedBytes(
        sampleBitmap: Bitmap?,
        originalBytes: Long,
        origW: Int,
        origH: Int,
        preset: PhotoCompressionPreset
    ): Long {
        if (originalBytes <= 0L) return 0L
        if (sampleBitmap != null && origW > 0 && origH > 0) {
            val testStream = ByteArrayOutputStream()
            sampleBitmap.compress(Bitmap.CompressFormat.JPEG, preset.quality, testStream)
            val sampleCompressed = testStream.size().toDouble()
            val samplePixels = (sampleBitmap.width * sampleBitmap.height).coerceAtLeast(1).toDouble()
            val targetScale = if (max(origW, origH) > preset.maxDimensionPx) {
                preset.maxDimensionPx.toDouble() / max(origW, origH).toDouble()
            } else 1.0
            val targetPixels = (origW * targetScale) * (origH * targetScale)
            val estimated = ((sampleCompressed / samplePixels) * targetPixels * 0.85).roundToInt().toLong()
            return estimated.coerceIn(1024L, originalBytes)
        }
        return estimateFromDimensions(originalBytes, origW, origH, preset)
    }

    private fun estimateFromDimensions(
        originalBytes: Long,
        origW: Int,
        origH: Int,
        preset: PhotoCompressionPreset
    ): Long {
        if (originalBytes <= 0L) return 0L
        val dimRatio = if (max(origW, origH) > preset.maxDimensionPx && max(origW, origH) > 0) {
            val s = preset.maxDimensionPx.toDouble() / max(origW, origH).toDouble()
            s * s
        } else 1.0
        val qualityRatio = when (preset) {
            PhotoCompressionPreset.LIGHT -> 0.72
            PhotoCompressionPreset.BALANCED -> 0.48
            PhotoCompressionPreset.HIGH_SAVING -> 0.30
        }
        return (originalBytes * dimRatio * qualityRatio).toLong().coerceIn(1024L, originalBytes)
    }

    private fun queryMeta(uri: Uri): Pair<String, Long> {
        var name = "photo.jpg"
        var size = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    private fun readImageBounds(uri: Uri): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }
        return opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
    }

    private fun decodeDownsampled(uri: Uri, maxSidePx: Int): Bitmap? {
        val (w, h) = readImageBounds(uri)
        var inSampleSize = 1
        if (w > 0 && h > 0) {
            val maxDim = max(w, h)
            while (maxDim / inSampleSize > maxSidePx * 2) {
                inSampleSize *= 2
            }
        }
        val opts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, maxDim: Int): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= maxDim || maxSide <= 0) return bitmap
        val ratio = maxDim.toFloat() / maxSide.toFloat()
        val newW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun computeSha256(uri: Uri): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(16 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    companion object {
        /**
         * Computes 4-neighbor Laplacian edge variance (blur detector) and mean luminance.
         * Low Laplacian variance indicates soft/blurry focus.
         */
        fun computeQualityMetrics(bitmap: Bitmap): Pair<Double, Double> {
            val w = bitmap.width
            val h = bitmap.height
            if (w < 3 || h < 3) return 100.0 to 128.0

            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            val lum = DoubleArray(w * h)
            var sumLum = 0.0
            for (i in pixels.indices) {
                val p = pixels[i]
                val l = 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
                lum[i] = l
                sumLum += l
            }
            val meanLum = sumLum / pixels.size

            var lapSum = 0.0
            var lapSqSum = 0.0
            var count = 0
            for (y in 1 until h - 1) {
                val rowOffset = y * w
                for (x in 1 until w - 1) {
                    val idx = rowOffset + x
                    val lap = lum[idx - 1] + lum[idx + 1] + lum[idx - w] + lum[idx + w] - 4.0 * lum[idx]
                    lapSum += lap
                    lapSqSum += lap * lap
                    count++
                }
            }
            if (count == 0) return 100.0 to meanLum
            val meanLap = lapSum / count
            val variance = (lapSqSum / count) - (meanLap * meanLap)
            return variance.coerceAtLeast(0.0) to meanLum
        }
    }
}
