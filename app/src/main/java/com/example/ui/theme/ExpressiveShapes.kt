package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

enum class ExpressiveArtworkShape(
    val id: String,
    val displayName: String,
    val subtitle: String
) {
    SCALLOP("scallop", "Scallop", "Organic M3 wave"),
    SQUIRCLE("squircle", "Squircle", "Soft superellipse"),
    CLOVER("clover", "Clover", "Four-petal bloom"),
    ARCH("arch", "Arch", "Minimal editorial");

    private val cachedShape: Shape by lazy {
        when (this) {
            SCALLOP -> ScallopedCircleShape(lobes = 10, amplitudeFraction = 0.052f)
            SQUIRCLE -> SuperellipseSquircleShape(exponent = 3.2f)
            CLOVER -> SoftCloverShape(amplitudeFraction = 0.115f)
            ARCH -> RoundedCornerShape(
                topStartPercent = 50,
                topEndPercent = 50,
                bottomStartPercent = 14,
                bottomEndPercent = 14
            )
        }
    }

    fun toComposeShape(): Shape = cachedShape

    companion object {
        fun fromId(id: String?): ExpressiveArtworkShape =
            entries.firstOrNull { it.id == id } ?: SCALLOP
    }
}

/**
 * Small bounded per-Size Outline cache to avoid rebuilding 100+ vertex Path objects on repeated
 * LazyColumn layouts for standard thumbnail and hero artwork dimensions.
 */
private class SizeOutlineCache(private val maxEntries: Int = 6) {
    private val map = LinkedHashMap<Long, Outline.Generic>(maxEntries, 0.75f, true)

    @Synchronized
    fun getOrCreate(size: Size, builder: () -> Path): Outline.Generic {
        val key = (size.width.toRawBits().toLong() shl 32) or (size.height.toRawBits().toLong() and 0xFFFFFFFFL)
        map[key]?.let { return it }
        val outline = Outline.Generic(builder())
        if (map.size >= maxEntries) {
            val eldestKey = map.keys.firstOrNull()
            if (eldestKey != null) map.remove(eldestKey)
        }
        map[key] = outline
        return outline
    }
}

/**
 * Signature Material 3 Expressive scalloped organic silhouette ("cookie" polygon).
 * Precomputes normalized unit vertices once and caches Outlines per Size; halves vertex stride
 * for compact thumbnails (<= 160px) to cut GPU stencil tessellation work on low-end GPUs.
 */
data class ScallopedCircleShape(
    private val lobes: Int = 10,
    private val amplitudeFraction: Float = 0.055f,
    private val rotationRadians: Float = 0f
) : Shape {
    private val unitX: FloatArray
    private val unitY: FloatArray
    private val outlineCache = SizeOutlineCache()

    init {
        val steps = 128
        unitX = FloatArray(steps + 1)
        unitY = FloatArray(steps + 1)
        val base = 1f - amplitudeFraction
        val twoPi = (2.0 * PI).toFloat()
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * twoPi
            val r = base + amplitudeFraction * cos(lobes * (theta + rotationRadians))
            unitX[i] = r * cos(theta)
            unitY[i] = r * sin(theta)
        }
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = outlineCache.getOrCreate(size) {
        val path = Path()
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val minDim = min(size.width, size.height)
        val radius = minDim * 0.5f
        val stride = if (minDim <= 160f) 2 else 1

        path.moveTo(cx + radius * unitX[0], cy + radius * unitY[0])
        var i = stride
        while (i < unitX.size) {
            path.lineTo(cx + radius * unitX[i], cy + radius * unitY[i])
            i += stride
        }
        path.close()
        path
    }
}

/**
 * Material 3 Expressive soft four-petal clover silhouette with precomputed vertices and Size cache.
 */
data class SoftCloverShape(
    private val amplitudeFraction: Float = 0.115f
) : Shape {
    private val unitX: FloatArray
    private val unitY: FloatArray
    private val outlineCache = SizeOutlineCache()

    init {
        val steps = 120
        unitX = FloatArray(steps + 1)
        unitY = FloatArray(steps + 1)
        val base = 1f - amplitudeFraction
        val twoPi = (2.0 * PI).toFloat()
        val quarterPi = (PI / 4.0).toFloat()
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * twoPi
            val r = base + amplitudeFraction * cos(4f * (theta - quarterPi))
            unitX[i] = r * cos(theta)
            unitY[i] = r * sin(theta)
        }
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = outlineCache.getOrCreate(size) {
        val path = Path()
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val minDim = min(size.width, size.height)
        val radius = minDim * 0.5f
        val stride = if (minDim <= 160f) 2 else 1

        path.moveTo(cx + radius * unitX[0], cy + radius * unitY[0])
        var i = stride
        while (i < unitX.size) {
            path.lineTo(cx + radius * unitX[i], cy + radius * unitY[i])
            i += stride
        }
        path.close()
        path
    }
}

/**
 * Tactile superellipse squircle shape with precomputed normalized coordinates and Size cache.
 */
data class SuperellipseSquircleShape(
    private val exponent: Float = 3.2f
) : Shape {
    private val unitX: FloatArray
    private val unitY: FloatArray
    private val outlineCache = SizeOutlineCache()

    init {
        val steps = 112
        unitX = FloatArray(steps + 1)
        unitY = FloatArray(steps + 1)
        val invExp = 2f / exponent
        val twoPi = (2.0 * PI).toFloat()
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * twoPi
            val cosT = cos(theta)
            val sinT = sin(theta)
            unitX[i] = sign(cosT) * abs(cosT).pow(invExp)
            unitY[i] = sign(sinT) * abs(sinT).pow(invExp)
        }
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = outlineCache.getOrCreate(size) {
        val path = Path()
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val rx = size.width * 0.5f
        val ry = size.height * 0.5f
        val stride = if (min(size.width, size.height) <= 160f) 2 else 1

        path.moveTo(cx + rx * unitX[0], cy + ry * unitY[0])
        var i = stride
        while (i < unitX.size) {
            path.lineTo(cx + rx * unitX[i], cy + ry * unitY[i])
            i += stride
        }
        path.close()
        path
    }
}
