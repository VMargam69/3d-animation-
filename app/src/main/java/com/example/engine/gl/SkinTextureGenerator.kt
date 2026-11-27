package com.example.engine.gl

import android.graphics.Bitmap
import android.graphics.Color
import com.example.engine.model.SkinPreset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates crisp procedural 256x256 albedo/skin textures offline on-device so characters and props
 * can use realistic warm skin shading, streetwear denim, brushed titanium, carbon fiber, cyber grids,
 * royal gold brocade, wood grain, or UV checker maps without bundling large image assets.
 */
object SkinTextureGenerator {

    private const val SIZE = 256

    fun generateBitmap(preset: SkinPreset): Bitmap? {
        if (preset == SkinPreset.NONE) return null
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(SIZE * SIZE)

        for (y in 0 until SIZE) {
            val v = y.toFloat() / SIZE
            for (x in 0 until SIZE) {
                val u = x.toFloat() / SIZE
                val idx = y * SIZE + x
                pixels[idx] = when (preset) {
                    SkinPreset.NATURAL_SKIN -> sampleSkin(x, y, warm = true)
                    SkinPreset.STYLIZED_PORCELAIN -> sampleSkin(x, y, warm = false)
                    SkinPreset.DENIM_STREETWEAR -> sampleDenim(x, y)
                    SkinPreset.TITANIUM_ARMOR -> sampleBrushedMetal(x, y)
                    SkinPreset.CARBON_FIBER -> sampleCarbonFiber(x, y)
                    SkinPreset.CYBER_CIRCUIT -> sampleCyberCircuit(x, y)
                    SkinPreset.ROYAL_BROCADE -> sampleBrocade(u, v)
                    SkinPreset.WARM_WOOD -> sampleWoodGrain(u, v)
                    SkinPreset.UV_CHECKER -> sampleChecker(x, y)
                    SkinPreset.NONE -> Color.WHITE
                }
            }
        }
        bmp.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        return bmp
    }

    private fun hash(x: Int, y: Int): Float {
        var n = x * 374761393 + y * 668265263
        n = (n xor (n shr 13)) * 1274126177
        return ((n xor (n shr 16)) and 0xFFFF) / 65535f
    }

    private fun sampleSkin(x: Int, y: Int, warm: Boolean): Int {
        val micro = (hash(x / 2, y / 2) - 0.5f) * 0.055f
        val blush = sin(x * 0.05f) * cos(y * 0.05f) * 0.025f
        val r = if (warm) 0.98f + micro + blush else 0.99f + micro * 0.6f
        val g = if (warm) 0.91f + micro - blush * 0.4f else 0.95f + micro * 0.6f
        val b = if (warm) 0.86f + micro - blush * 0.6f else 0.93f + micro * 0.6f
        return rgb(r, g, b)
    }

    private fun sampleDenim(x: Int, y: Int): Int {
        val twill = ((x + y) % 6)
        val warp = if (twill < 2) 0.84f else 0.96f
        val grain = (hash(x, y / 2) - 0.5f) * 0.07f
        val stitch = if (y % 64 == 0 && (x / 6) % 2 == 0) 0.14f else 0f
        return rgb(warp + grain + stitch, warp + grain + stitch * 0.8f, warp + 0.04f + grain)
    }

    private fun sampleBrushedMetal(x: Int, y: Int): Int {
        val streak = (hash(x / 16, y) - 0.5f) * 0.14f
        val fine = (hash(x / 4, y) - 0.5f) * 0.05f
        val seam = if (x % 128 < 2 || y % 128 < 2) -0.18f else 0f
        val v = 0.90f + streak + fine + seam
        return rgb(v * 0.96f, v * 0.99f, v * 1.03f)
    }

    private fun sampleCarbonFiber(x: Int, y: Int): Int {
        val cx = (x / 8) % 2
        val cy = (y / 8) % 2
        val diag = ((x + y) / 4) % 2
        val v = when {
            cx == cy && diag == 0 -> 0.52f
            cx == cy -> 0.68f
            diag == 0 -> 0.78f
            else -> 0.60f
        }
        return rgb(v, v * 1.02f, v * 1.06f)
    }

    private fun sampleCyberCircuit(x: Int, y: Int): Int {
        val gridX = x % 32
        val gridY = y % 32
        val isTrace = gridX == 0 || gridY == 0 || (gridX == 16 && y % 64 < 28) || (gridY == 16 && x % 64 < 28)
        val isNode = abs(gridX - 16) <= 2 && abs(gridY - 16) <= 2
        return when {
            isNode -> rgb(0.35f, 0.96f, 1.0f)
            isTrace -> rgb(0.45f, 0.78f, 0.98f)
            else -> rgb(0.76f, 0.80f, 0.88f)
        }
    }

    private fun sampleBrocade(u: Float, v: Float): Int {
        val motif = sin(u * 25.1327f) * sin(v * 25.1327f)
        val diamond = abs(((u * 8f) % 1f) - 0.5f) + abs(((v * 8f) % 1f) - 0.5f)
        val goldAccent = if (diamond < 0.24f || motif > 0.62f) 1.0f else 0.82f
        return rgb(goldAccent, goldAccent * 0.95f, goldAccent * 0.80f)
    }

    private fun sampleWoodGrain(u: Float, v: Float): Int {
        val wave = sin((u * 18f + sin(v * 6.28f) * 0.6f) * 3.14159f)
        val ring = 0.76f + 0.18f * (wave * 0.5f + 0.5f)
        return rgb(ring, ring * 0.86f, ring * 0.70f)
    }

    private fun sampleChecker(x: Int, y: Int): Int {
        val even = ((x / 32) + (y / 32)) % 2 == 0
        return if (even) rgb(0.95f, 0.97f, 1.0f) else rgb(0.32f, 0.38f, 0.52f)
    }

    private fun rgb(r: Float, g: Float, b: Float): Int = Color.rgb(
        (r.coerceIn(0f, 1f) * 255f).toInt(),
        (g.coerceIn(0f, 1f) * 255f).toInt(),
        (b.coerceIn(0f, 1f) * 255f).toInt()
    )
}
