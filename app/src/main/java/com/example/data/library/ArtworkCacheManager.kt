package com.example.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.intelligence.LocalMoodAndAffinityEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class CachedArtworkResult(
    val path: String?,
    val dominantColorArgb: Int
)

/**
 * Downscales and caches extracted album art to disk so Track models only hold a lightweight path,
 * preventing large ByteArray allocations in RAM and enabling fast Coil caching.
 * Also extracts a compact dominant color ARGB on Dispatchers.Default for offline mood classification.
 */
class ArtworkCacheManager(context: Context) {

    private val appContext = context.applicationContext
    private val cacheDir: File by lazy {
        File(appContext.filesDir, "cadence_artwork")
    }

    suspend fun cacheArtworkBytes(bytes: ByteArray?): String? =
        cacheArtworkBytesWithColor(bytes).path

    suspend fun cacheArtworkBytesWithColor(bytes: ByteArray?): CachedArtworkResult = withContext(Dispatchers.IO) {
        if (bytes == null || bytes.isEmpty()) return@withContext CachedArtworkResult(null, 0)

        val dir = cacheDir
        if (!dir.exists()) dir.mkdirs()

        val key = sha256Prefix(bytes)
        val outFile = File(dir, "$key.jpg")
        if (outFile.exists() && outFile.length() > 0L) {
            val cachedColor = extractDominantColorFromPath(outFile.absolutePath)
            return@withContext CachedArtworkResult(outFile.absolutePath, cachedColor)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext CachedArtworkResult(null, 0)
        }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_ARTWORK_PX)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: return@withContext CachedArtworkResult(null, 0)

        val tmpFile = File(cacheDir, "$key-${System.nanoTime()}.tmp")
        try {
            val dominantColor = withContext(Dispatchers.Default) {
                LocalMoodAndAffinityEngine.extractDominantColorArgb(bitmap)
            }
            FileOutputStream(tmpFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, stream)
            }
            if (!outFile.exists()) {
                if (!tmpFile.renameTo(outFile)) {
                    tmpFile.copyTo(outFile, overwrite = true)
                    tmpFile.delete()
                }
            } else {
                tmpFile.delete()
            }
            CachedArtworkResult(outFile.absolutePath, dominantColor)
        } catch (_: Exception) {
            tmpFile.delete()
            CachedArtworkResult(null, 0)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Decodes a tiny (<= 64px) sample of a cached file or content:// album-art URI on IO/Default
     * to extract its dominant color ARGB with minimal memory overhead.
     */
    suspend fun extractDominantColorFromPath(artworkPath: String?): Int = withContext(Dispatchers.IO) {
        if (artworkPath.isNullOrBlank()) return@withContext 0
        try {
            val bitmap: Bitmap? = if (artworkPath.startsWith("content://")) {
                val uri = Uri.parse(artworkPath)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext 0
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, COLOR_SAMPLE_PX)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            } else {
                val file = File(artworkPath)
                if (!file.exists() || file.length() <= 0L) return@withContext 0
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext 0
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, COLOR_SAMPLE_PX)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            }
            if (bitmap == null) return@withContext 0
            try {
                withContext(Dispatchers.Default) {
                    LocalMoodAndAffinityEngine.extractDominantColorArgb(bitmap)
                }
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        var halfW = width / 2
        var halfH = height / 2
        while ((halfW / inSampleSize) >= maxDim && (halfH / inSampleSize) >= maxDim) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun sha256Prefix(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_ARTWORK_PX = 512
        private const val COLOR_SAMPLE_PX = 64
    }
}
