package com.example.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Downscales and caches extracted album art to disk so Track models only hold a lightweight path,
 * preventing large ByteArray allocations in RAM and enabling fast Coil caching.
 */
class ArtworkCacheManager(context: Context) {

    private val appContext = context.applicationContext
    private val cacheDir: File by lazy {
        File(appContext.filesDir, "cadence_artwork")
    }

    suspend fun cacheArtworkBytes(bytes: ByteArray?): String? = withContext(Dispatchers.IO) {
        if (bytes == null || bytes.isEmpty()) return@withContext null

        val dir = cacheDir
        if (!dir.exists()) dir.mkdirs()

        val key = sha256Prefix(bytes)
        val outFile = File(dir, "$key.jpg")
        if (outFile.exists() && outFile.length() > 0L) {
            return@withContext outFile.absolutePath
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_ARTWORK_PX)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: return@withContext null

        val tmpFile = File(cacheDir, "$key-${System.nanoTime()}.tmp")
        try {
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
            outFile.absolutePath
        } catch (_: Exception) {
            tmpFile.delete()
            null
        } finally {
            bitmap.recycle()
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
    }
}
