package com.armsx2.ui.home

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.sin
import kotlin.random.Random

/**
 * Nexus2 default backdrop: the four controller symbols (○ △ ✕ □) streaming out of a soft
 * coloured nebula towards the viewer, on the app's near-black. An original Canvas drawing (no
 * logo, no video file): ~220 stroked shapes and three radial glows per frame on the 2D renderer,
 * so it runs the same on Mali, Adreno or anything else, with no GL context and no textures.
 *
 * Each particle lives in a small 3D box and moves along -z; the screen position is a plain
 * perspective divide, so shapes start as specks at the centre and grow and fade as they pass.
 */
@Composable
fun NexusParticleBackground(modifier: Modifier = Modifier, intensity: Float = 1f, centerY: Float = 0.36f) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var start = 0L
        withInfiniteAnimationFrameNanos { start = it }
        while (true) withInfiniteAnimationFrameNanos { now -> time.floatValue = (now - start) / 1_000_000_000f }
    }
    val field = remember { ParticleField(COUNT) }

    Canvas(modifier) {
        val t = time.floatValue
        drawRect(BASE)
        val c = Offset(size.width / 2f, size.height * centerY)
        val r = size.minDimension
        // Nebula: three slow, offset glows, like light behind smoke.
        NEBULA.forEachIndexed { i, color ->
            val phase = t * 0.21f + i * 2.1f
            val centre = c + Offset(sin(phase) * r * 0.06f, sin(phase * 0.7f + 1f) * r * 0.05f)
            val radius = r * (0.42f + 0.05f * sin(phase * 1.3f))
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.20f * intensity), Color.Transparent), centre, radius),
                radius,
                centre,
            )
        }
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f * intensity), Color.Transparent), c, r * 0.12f),
            r * 0.12f,
            c,
        )
        field.step(t)
        field.draw(this, c, r, intensity)
    }
}

private const val COUNT = 220
private val BASE = Color(0xFF090B12) // the app background, so the Home hero fades into it seamlessly
private val NEBULA = listOf(Color(0xFF8257FF), Color(0xFF35A8FF), Color(0xFFE0457B))

/** ○ △ ✕ □, each with its own colour, as on the pad. */
private val SHAPE_COLORS = arrayOf(
    Color(0xFFFF4D6D), // circle
    Color(0xFF4ADE80), // triangle
    Color(0xFF4FB4FF), // cross
    Color(0xFFC26BFF), // square
)

private class ParticleField(val n: Int) {
    private val rnd = Random(7)
    private val x = FloatArray(n)
    private val y = FloatArray(n)
    private val z = FloatArray(n)
    private val speed = FloatArray(n)
    private val rot = FloatArray(n)
    private val spin = FloatArray(n)
    private val kind = IntArray(n)
    private val scale = FloatArray(n)
    private var last = -1f
    private val triangle = Path()

    init {
        for (i in 0 until n) spawn(i, z0 = 0.05f + rnd.nextFloat() * 0.95f)
    }

    private fun spawn(i: Int, z0: Float = 1f) {
        // Mostly near the axis, a few wide: gives the dense core and the stragglers of the clip.
        val spread = if (rnd.nextFloat() < 0.75f) 0.35f else 1.1f
        x[i] = (rnd.nextFloat() * 2f - 1f) * spread
        y[i] = (rnd.nextFloat() * 2f - 1f) * spread
        z[i] = z0
        speed[i] = 0.10f + rnd.nextFloat() * 0.16f
        rot[i] = rnd.nextFloat() * 360f
        spin[i] = (rnd.nextFloat() * 2f - 1f) * 70f
        kind[i] = rnd.nextInt(4)
        scale[i] = 0.6f + rnd.nextFloat() * 0.8f
    }

    fun step(t: Float) {
        val dt = if (last < 0f) 0f else (t - last).coerceIn(0f, 0.1f)
        last = t
        for (i in 0 until n) {
            z[i] -= speed[i] * dt
            rot[i] += spin[i] * dt
            if (z[i] <= 0.04f) spawn(i)
        }
    }

    fun draw(scope: DrawScope, c: Offset, r: Float, intensity: Float) = with(scope) {
        for (i in 0 until n) {
            val zi = z[i]
            val px = c.x + x[i] / zi * r * 0.16f
            val py = c.y + y[i] / zi * r * 0.16f
            if (px < -60f || py < -60f || px > size.width + 60f || py > size.height + 60f) {
                spawn(i); continue
            }
            val s = minOf((r * 0.004f / zi) * scale[i], r * 0.028f)
            if (s < 0.6f) continue
            // Fade in from the far plane, fade out right before passing the camera.
            val near = 1f - zi
            val alpha = (near * 1.6f).coerceIn(0f, 1f) * ((zi - 0.04f) / 0.14f).coerceIn(0f, 1f) * 0.95f * intensity
            val color = SHAPE_COLORS[kind[i]].copy(alpha = alpha)
            val stroke = Stroke(width = (s * 0.34f).coerceAtLeast(1.2f), cap = StrokeCap.Round, join = StrokeJoin.Round)
            translate(px, py) {
                rotate(rot[i], Offset.Zero) {
                // Tumble: squash one axis with the spin so the shapes read as turning in 3D.
                val fx = kotlin.math.abs(kotlin.math.cos(Math.toRadians(rot[i] * 1.7).toFloat())).coerceAtLeast(0.25f)
                scale(fx, 1f, Offset.Zero) {
                    when (kind[i]) {
                        0 -> drawCircle(color, s, Offset.Zero, style = stroke)
                        1 -> {
                            triangle.reset()
                            triangle.moveTo(0f, -s * 1.1f)
                            triangle.lineTo(s * 0.95f, s * 0.6f)
                            triangle.lineTo(-s * 0.95f, s * 0.6f)
                            triangle.close()
                            drawPath(triangle, color, style = stroke)
                        }
                        2 -> {
                            drawLine(color, Offset(-s, -s), Offset(s, s), stroke.width, StrokeCap.Round)
                            drawLine(color, Offset(-s, s), Offset(s, -s), stroke.width, StrokeCap.Round)
                        }
                        else -> drawRect(color, Offset(-s * 0.85f, -s * 0.85f), Size(s * 1.7f, s * 1.7f), style = stroke)
                    }
                }
                }
            }
        }
    }
}
