package com.example.playback

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.data.Track
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Maps domain Track objects into Media3 MediaItems with full metadata and artwork
 * for lock-screen, Bluetooth, and system notification controls.
 * Low-end optimized: caps inline artwork payloads to 128 KB (downscaling to 320px RGB_565 if larger)
 * to keep Binder IPC transactions comfortably below the 1 MB system transaction buffer.
 */
class MediaItemMapper(
    private val context: Context
) {
    suspend fun toMediaItem(track: Track): MediaItem? {
        val uri = resolvePlayableUri(track) ?: return null

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setGenre(track.genre.ifBlank { track.genreTag })

        applyArtwork(metadataBuilder, track)

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun resolvePlayableUri(track: Track): Uri? =
        track.localUriString?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }

    private fun applyArtwork(builder: MediaMetadata.Builder, track: Track) {
        track.artworkResId?.let { resId ->
            val resUri = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.packageName)
                .appendPath(resId.toString())
                .build()
            builder.setArtworkUri(resUri)
            return
        }

        val path = track.artworkPath ?: return
        if (path.startsWith("content://")) {
            builder.setArtworkUri(Uri.parse(path))
            return
        }

        val file = if (path.startsWith("file://")) {
            Uri.parse(path).path?.let { File(it) }
        } else {
            File(path)
        }

        if (file == null || !file.exists() || file.length() <= 0L) return

        val bytes = if (file.length() <= MAX_INLINE_ARTWORK_BYTES) {
            runCatching { file.readBytes() }.getOrNull()
        } else {
            downscaleToBinderSafeJpeg(file)
        }

        if (bytes != null && bytes.isNotEmpty() && bytes.size <= MAX_INLINE_ARTWORK_BYTES) {
            builder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
    }

    private fun downscaleToBinderSafeJpeg(file: File): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while ((bounds.outWidth / sampleSize) > NOTIFICATION_ART_MAX_PX &&
            (bounds.outHeight / sampleSize) > NOTIFICATION_ART_MAX_PX
        ) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        try {
            ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
        } finally {
            bmp.recycle()
        }
    }.getOrNull()

    companion object {
        private const val MAX_INLINE_ARTWORK_BYTES = 128_000L
        private const val NOTIFICATION_ART_MAX_PX = 320
    }
}
