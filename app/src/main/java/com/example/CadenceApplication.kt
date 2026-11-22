package com.example

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * Configures a bounded, low-RAM-friendly Coil ImageLoader singleton across the app:
 * - RGB_565 decoding (halves bitmap RAM for opaque album covers)
 * - Adaptive memory cache cap (12% on low-RAM devices, 20% on standard devices)
 * - 32 MB disk cache cap to prevent unbounded cache growth
 */
class CadenceApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true

        return ImageLoader.Builder(this)
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRam) 0.12 else 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "cadence_image_cache"))
                    .maxSizeBytes(32L * 1024L * 1024L)
                    .build()
            }
            .build()
    }
}
