package com.example.hellowolds.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.hellowolds.R
import com.example.hellowolds.spotify.SpotifyAlbum
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// Values from design/HOME_SCREEN_HANDOFF(3).md and home-screen-preview(1).html.

internal val Jersey10 = FontFamily(Font(R.font.jersey10_regular))

private const val HOLD_S = 5f
private const val FADE_S = 2.5f
private const val ALBUM_BLEND_S = 0.9f
// Colors blend in over the same time the cover pops in, so both land together.
private const val COVER_POP_MS = 550
private const val ALBUM_IN_BLEND_S = COVER_POP_MS / 1000f
private const val CELL_DP = 15f
private const val BG_SPEED = 40f / 120f
private const val HUE_SWAY_S = 12f
private const val HUE_SWAY_DEG = 15f
private const val WAVE_GROW_S = 1.1f
private const val WAVE_LIFE_S = 1.3f
internal const val MAX_WAVES = 4

private const val ERASE_MS = 28L
private const val TYPE_MS = 60L
// The title erases and types in a fixed time whatever its length (1.2s in total).
private const val TITLE_ERASE_MS = 400L

// Intro after the loading screen (ms from home appearing / from the spiral starting).
private const val INTRO_TYPE_DELAY_MS = 300L
private const val INTRO_WAIT_COVERS_MS = 2500L
private const val INTRO_CTAS_DELAY_MS = 1100L
private const val INTRO_SECTION_DELAY_MS = 1400L
private val INTRO_RISE = 32.dp
private const val TITLE_TYPE_MS = 800L
private const val CURSOR_BLINK_NANOS = 350_000_000L

/** Block cursor blink on the shared clock, so it stays in phase across the loading → home handoff. */
internal fun cursorBlinkOn(nanos: Long) = (nanos / CURSOR_BLINK_NANOS) % 2 == 0L

/** Title size meaning "the home title's automatic size" (see [rememberHomeTitleLayout]). */
private const val HOME_SIZE_AUTO = 0
private const val HOME_TITLE_MAX = 88
private const val ALBUM_TITLE_MAX = 116
internal const val HOME_LINE_HEIGHT = 0.75f

internal const val HOME_TITLE = "PONETE\nALGO,\nGORDO!"
private const val TITLE_MIN = 40
internal val TITLE_W = 334.dp
internal val TITLE_H = 291.dp

// Screen margins, shared with the loading screen so its cursor lands on the home title.
internal val SCREEN_PAD_H = 28.dp
internal val SCREEN_PAD_TOP = 32.dp
private val PRIMARY_H = 60.dp
private val SECONDARY_H = 52.dp
private val CTA_GAP = 14.dp
private val SECTION_LABEL_H = 22.dp
private val SECTION_GAP = 14.dp
private val SECTION_COVER = 168.dp
private val SECTION_BOTTOM = 56.dp
/** Height of the album section anchored at the bottom, including its bottom margin. */
internal val SECTION_TOTAL = SECTION_LABEL_H + SECTION_GAP + SECTION_COVER + SECTION_BOTTOM
/** Least room kept for the Random button between the title and the albums. */

/** Home title [size] (sp), its rendered [height], and its [top] within the area above the albums. */
internal class HomeTitleLayout(val size: Int, val height: Dp, val top: Dp)

/** Gap between the bottom of the home title and the buttons. */
private val TITLE_CTA_GAP = 32.dp

/**
 * The home title's size and place: as large as fits [maxWidth] (up to [HOME_TITLE_MAX]) with the
 * buttons below it in [maxHeight] (the padded area above the album section); title + buttons are
 * centered there as one group. Shared with the loading screen so its cursor lands exactly on it.
 */
@Composable
internal fun rememberHomeTitleLayout(maxWidth: Dp, maxHeight: Dp): HomeTitleLayout {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(maxWidth, maxHeight, density) {
        with(density) {
            val maxW = maxWidth.roundToPx()
            val maxH = (maxHeight - TITLE_CTA_GAP - CTAS_HEIGHT).toPx()
            val lines = HOME_TITLE.lines()
            // The real rendered height (the font's line box is a bit taller than the line height).
            fun height(size: Int) = measurer.measure(
                text = HOME_TITLE,
                style = TextStyle(fontFamily = Jersey10, fontSize = size.sp, lineHeight = (size * HOME_LINE_HEIGHT).sp)
            ).size.height.toFloat()
            fun tooWide(size: Int) = lines.any {
                measurer.measure(
                    text = it, style = TextStyle(fontFamily = Jersey10, fontSize = size.sp),
                    softWrap = false, maxLines = 1
                ).size.width > maxW
            }
            var size = HOME_TITLE_MAX
            while (size > TITLE_MIN && (height(size) > maxH || tooWide(size))) size -= 2
            val h = height(size).toDp()
            val top = ((maxHeight - (h + TITLE_CTA_GAP + CTAS_HEIGHT)) / 2).coerceAtLeast(0.dp)
            HomeTitleLayout(size, h, top)
        }
    }
}
private val CTAS_HEIGHT = PRIMARY_H + CTA_GAP + SECONDARY_H
private val CTAS_BOTTOM = 48.dp
private val COVER_GAP = 24.dp
private val COVER_MIN = 80.dp
private val COVER_MAX = 300.dp
private val COVER_SHADOW = 10.dp
internal val RING = 5.dp

// The design moves a set of 8 covers in 24s. Covers grow with the screen, so the
// speed is "8 covers per 24s" at whatever size they are; a big library doesn't
// make the strip race.
private const val STRIP_COVERS_PER_S = 8f / 24f
private const val STRIP_MAX_COVERS = 24
private val STRIP_GAP = 12.dp

private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val CtaEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val CoverPop = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f)

internal data class HomeTheme(
    val palette: List<Color>,
    val title: Color,
    val primaryBg: Color,
    val primaryFg: Color,
    val secondaryBg: Color,
    val secondaryFg: Color,
    val secondaryBorder: Color
)

private fun palette(vararg hex: Long) = hex.map { Color(it) }

private val Themes = listOf(
    // Night neon
    HomeTheme(
        palette(0xFF0B0420, 0xFF160A3A, 0xFF24105A, 0xFF3A1470, 0xFF5B1A9E, 0xFF7A1FD0, 0xFF5B1A9E, 0xFF3A1470, 0xFF24105A, 0xFF160A3A),
        title = Color(0xFFC6FF3D), primaryBg = Color(0xFFC6FF3D), primaryFg = Color(0xFF0B0420),
        secondaryBg = Color(0xFF0B0420), secondaryFg = Color(0xFFC6FF3D), secondaryBorder = Color(0xFFC6FF3D)
    ),
    // Sunset pop
    HomeTheme(
        palette(0xFFFF3D2E, 0xFFFF5A24, 0xFFFF7A1A, 0xFFFF9A14, 0xFFFFB81A, 0xFFFFD84D, 0xFFFFB81A, 0xFFFF9A14, 0xFFFF7A1A, 0xFFFF5A24),
        title = Color(0xFF2A0A4A), primaryBg = Color(0xFF2A0A4A), primaryFg = Color(0xFFFFE9A8),
        secondaryBg = Color(0xFFFFF4D6), secondaryFg = Color(0xFF2A0A4A), secondaryBorder = Color(0xFF2A0A4A)
    ),
    // Acid mint
    HomeTheme(
        palette(0xFF00C2FF, 0xFF00D4E0, 0xFF00E0B8, 0xFF3DF5A0, 0xFF7CFF7A, 0xFFA6FF5C, 0xFF7CFF7A, 0xFF3DF5A0, 0xFF00E0B8, 0xFF00D4E0),
        title = Color(0xFF000000), primaryBg = Color(0xFF000000), primaryFg = Color(0xFFA6FF5C),
        secondaryBg = Color(0xFFFFFFFF), secondaryFg = Color(0xFF000000), secondaryBorder = Color(0xFF000000)
    ),
    // Electric blue
    HomeTheme(
        palette(0xFF03102E, 0xFF061C4A, 0xFF0A2A6E, 0xFF0F3D99, 0xFF1752CC, 0xFF1E6BFF, 0xFF1752CC, 0xFF0F3D99, 0xFF0A2A6E, 0xFF061C4A),
        title = Color(0xFFFFFFFF), primaryBg = Color(0xFFFFFFFF), primaryFg = Color(0xFF0F3D99),
        secondaryBg = Color(0xFF03102E), secondaryFg = Color(0xFFFFFFFF), secondaryBorder = Color(0xFFFFFFFF)
    ),
    // Neon violet
    HomeTheme(
        palette(0xFF7B3CFF, 0xFF9D4BFF, 0xFFC24DFF, 0xFFE040FB, 0xFFFF3DCB, 0xFFFF5AA0, 0xFFFF3DCB, 0xFFE040FB, 0xFFC24DFF, 0xFF9D4BFF),
        title = Color(0xFF1E1E1E), primaryBg = Color(0xFFFEFEFE), primaryFg = Color(0xFF000000),
        secondaryBg = Color(0xFF1E1E1E), secondaryFg = Color(0xFFFEFEFE), secondaryBorder = Color(0xFF000000)
    )
)

/** Straight sRGB mix, like the design's canvas code (Compose's lerp mixes in Oklab). */
private fun mix(a: Color, b: Color, k: Float) = Color(
    red = a.red + (b.red - a.red) * k,
    green = a.green + (b.green - a.green) * k,
    blue = a.blue + (b.blue - a.blue) * k
)

private fun mixTheme(a: HomeTheme, b: HomeTheme, k: Float) = HomeTheme(
    palette = a.palette.zip(b.palette) { x, y -> mix(x, y, k) },
    title = mix(a.title, b.title, k),
    primaryBg = mix(a.primaryBg, b.primaryBg, k),
    primaryFg = mix(a.primaryFg, b.primaryFg, k),
    secondaryBg = mix(a.secondaryBg, b.secondaryBg, k),
    secondaryFg = mix(a.secondaryFg, b.secondaryFg, k),
    secondaryBorder = mix(a.secondaryBorder, b.secondaryBorder, k)
)

private fun smoothstep(p: Float) = p * p * (3f - 2f * p)

/** Index in [Themes] the cycle starts from when the app opens: Acid mint (the green one). */
private const val START_THEME = 2

/** How many albums from the top of the library feed the home color cycle. */
private const val CYCLE_ALBUMS = 5

/**
 * The color cycle: each theme holds 5s, then crossfades to the next over 2.5s. Defaults to the
 * built-in [Themes] starting at Acid mint; home swaps in themes taken from album covers.
 */
internal fun cycleTheme(seconds: Float, themes: List<HomeTheme> = Themes, startIndex: Int = START_THEME): HomeTheme {
    val period = HOLD_S + FADE_S
    val seg = seconds / period + startIndex
    val i = floor(seg).toInt().mod(themes.size)
    val local = (seg - floor(seg)) * period
    if (local <= HOLD_S || themes.size == 1) return themes[i]
    return mixTheme(themes[i], themes[(i + 1) % themes.size], smoothstep((local - HOLD_S) / FADE_S))
}

/** A color cycle built from album covers, started (at its first album) at [startSeconds]. */
private class AlbumCycle(val themes: List<HomeTheme>, val startSeconds: Float)

/** A blend from whatever was showing to an album's colors, or back to the live cycle when [to] is null. */
private class Overlay(val from: HomeTheme, val to: HomeTheme?, val startNanos: Long, val durationS: Float)

// ---------- Colors from the album cover ----------

private val NearWhite = Color(0xFFFFFFFF)
private val NearBlack = Color(0xFF111111)

private fun luminance(c: Color) = ColorUtils.calculateLuminance(c.toArgb())
private fun contrast(a: Color, b: Color) = ColorUtils.calculateContrast(a.toArgb(), b.toArgb())
private fun saturation(c: Color): Float {
    val mx = maxOf(c.red, c.green, c.blue)
    val mn = minOf(c.red, c.green, c.blue)
    return if (mx == 0f) 0f else (mx - mn) / mx
}

/** Builds the selected-album theme from representative cover colors (rules in the handoff). */
private fun themeFromColors(raw: List<Color>): HomeTheme {
    val sorted = raw.distinct().sortedBy { luminance(it) }
    // Keep 4, dark → light, interpolating when the cover gave fewer.
    val cents = List(4) { i ->
        if (sorted.size == 1) sorted[0]
        else {
            val t = i / 3f * (sorted.size - 1)
            val k = floor(t).toInt().coerceAtMost(sorted.size - 2)
            mix(sorted[k], sorted[k + 1], t - k)
        }
    }
    // 6 stops through the 4 colors, mirrored into the same 10-step ramp as the home themes.
    val stops = List(6) { i ->
        val t = i / 5f * 3f
        val k = floor(t).toInt().coerceAtMost(2)
        mix(cents[k], cents[k + 1], t - k)
    }
    val ramp = listOf(0, 1, 2, 3, 4, 5, 4, 3, 2, 1).map { stops[it] }

    fun worstContrast(text: Color) = ramp.minOf { contrast(text, it) }
    val title = if (worstContrast(NearWhite) >= worstContrast(NearBlack)) NearWhite else NearBlack
    val darkText = title == NearBlack
    val primaryFg = cents.sortedByDescending { saturation(it) }.firstOrNull { contrast(it, title) >= 4.5 }
        ?: if (darkText) NearWhite else NearBlack
    val secondaryBg = if (darkText) cents[3] else cents[0]
    return HomeTheme(ramp, title, title, primaryFg, secondaryBg, title, title)
}

/** Minimum contrast between the title and every background color in the home album cycle. */
private const val CYCLE_MIN_CONTRAST = 4.5

/** Mixes [c] toward black or white (away from [text]) in small steps until it contrasts enough. */
private fun pushAwayFrom(c: Color, text: Color, minContrast: Double): Color {
    val toward = if (text == NearWhite) Color.Black else Color.White
    var k = 0f
    var out = c
    while (contrast(text, out) < minContrast && k < 1f) {
        k += 0.05f
        out = mix(c, toward, k)
    }
    return out
}

/**
 * Home-cycle version of an album theme: keeps the background on one side of the title color, so
 * muted, mid-tone covers don't put white text over light grays. Album picks keep the exact colors.
 */
private fun readableForCycle(t: HomeTheme): HomeTheme = t.copy(
    palette = t.palette.map { pushAwayFrom(it, t.title, CYCLE_MIN_CONTRAST) },
    secondaryBg = pushAwayFrom(t.secondaryBg, t.title, CYCLE_MIN_CONTRAST)
)

/**
 * Loading-screen theme: a calm night ramp, deep navy → indigo → soft violet (mirrored, like the
 * home themes), under a white title.
 */
internal val LoadingTheme: HomeTheme = HomeTheme(
    palette(0xFF0D1028, 0xFF151A3E, 0xFF1F2556, 0xFF2B2F6E, 0xFF3A3A85, 0xFF4B4699, 0xFF3A3A85, 0xFF2B2F6E, 0xFF1F2556, 0xFF151A3E),
    title = NearWhite,
    primaryBg = NearWhite,
    primaryFg = NearBlack,
    secondaryBg = NearBlack,
    secondaryFg = NearWhite,
    secondaryBorder = NearWhite
)

private suspend fun loadCover(context: Context, imageUrl: String?): Bitmap? {
    if (imageUrl.isNullOrEmpty()) return null
    val request = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build()
    return (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
}

private suspend fun albumThemeFrom(bitmap: Bitmap): HomeTheme? {
    return withContext(Dispatchers.Default) {
        val p = Palette.from(bitmap).maximumColorCount(16).generate()
        val colors = listOfNotNull(
            p.darkMutedSwatch, p.darkVibrantSwatch, p.mutedSwatch,
            p.vibrantSwatch, p.lightVibrantSwatch, p.lightMutedSwatch
        ).map { Color(it.rgb) }.ifEmpty {
            p.swatches.sortedByDescending { it.population }.take(4).map { Color(it.rgb) }
        }
        if (colors.isEmpty()) null else themeFromColors(colors)
    }
}

// ---------- Background ----------

/** -15° to +15° and back, 12s each way, ease-in-out. */
private fun hueDegrees(seconds: Float): Float {
    val t = seconds / HUE_SWAY_S
    val frac = t - floor(t)
    val p = if (floor(t).toInt() % 2 == 0) frac else 1f - frac
    return -HUE_SWAY_DEG + 2f * HUE_SWAY_DEG * smoothstep(p)
}

/** Same matrix as CSS `hue-rotate()` / SVG feColorMatrix hueRotate. */
private fun hueRotation(degrees: Float): ColorMatrix {
    val rad = Math.toRadians(degrees.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    return ColorMatrix(
        floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

internal class Wave(val fx: Float, val fy: Float, val startNanos: Long)

private fun mixArgb(a: Int, b: Int, k: Float): Int {
    fun ch(shift: Int) = (((a shr shift) and 0xFF) + (((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * k).roundToInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/**
 * A spiral wipe of the background from [from] to the current colors: each cell fades over as an
 * Archimedean spiral front, winding out from the screen center, sweeps past it.
 */
internal class Spiral(val from: List<Color>, val startNanos: Long, val durationS: Float)

private const val SPIRAL_TURNS = 2.5f
private const val SPIRAL_S = 2.2f
private const val SPIRAL_SOFT = 0.15f       // width of the band where each cell fades old → new

/** One pixel per background cell; drawn scaled up with no smoothing. */
private class PixelField {
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private val palette = IntArray(10)
    private val oldPalette = IntArray(10)

    fun render(cols: Int, rows: Int, nanos: Long, theme: HomeTheme, waves: MutableList<Wave>, spiral: Spiral?): Bitmap {
        val bmp = bitmap?.takeIf { it.width == cols && it.height == rows }
            ?: Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888).also {
                bitmap = it
                pixels = IntArray(cols * rows)
            }

        val p = theme.palette.size
        for (n in 0 until p) palette[n] = theme.palette[n].toArgb()

        // Spiral wipe: cells whose spiral position is past the front still show the old colors.
        // Progress runs past 1 by the soft band, so every cell finishes its fade.
        val spiralP = spiral?.let {
            smoothstep(((nanos - it.startNanos) / 1e9f / it.durationS).coerceIn(0f, 1f)) * (1f + SPIRAL_SOFT)
        }
        if (spiral != null) for (n in 0 until p) oldPalette[n] = spiral.from[n % spiral.from.size].toArgb()
        val scx = cols / 2f
        val scy = rows / 2f
        val maxR = hypot(scx, scy)

        val s = nanos / 1e9f * BG_SPEED
        val cx = cols * (0.5f + 0.45f * sin(s * 0.7f))
        val cy = rows * (0.5f + 0.4f * cos(s * 0.53f))

        waves.removeAll { (nanos - it.startNanos) / 1e9f >= WAVE_LIFE_S }
        val reach = hypot(cols.toFloat(), rows.toFloat())
        val wx = FloatArray(waves.size)
        val wy = FloatArray(waves.size)
        val wr = FloatArray(waves.size)
        val wf = FloatArray(waves.size)
        waves.forEachIndexed { k, w ->
            val age = (nanos - w.startNanos) / 1e9f
            wx[k] = w.fx * cols
            wy[k] = w.fy * rows
            wr[k] = age * reach / WAVE_GROW_S
            wf[k] = 1f - age / WAVE_LIFE_S
        }

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val v = sin(x * 0.18f + s * 1.3f) +
                    sin(y * 0.11f - s * 0.9f) +
                    sin(x * 0.12f + y * 0.08f + s * 1.1f) +
                    sin(hypot(x - cx, y - cy) * 0.28f - s * 1.7f)
                var push = 0f
                for (k in wx.indices) {
                    val d = hypot(x - wx[k], y - wy[k]) - wr[k]
                    push += exp(-(d * d) / 6f) * wf[k] * 5f
                }
                val index = floor((v + 4f) / 8f * p * 2f + s * 0.8f + push).toInt().mod(p)
                var k = 1f   // 0 = old colors, 1 = new
                if (spiralP != null && spiralP < 1f + SPIRAL_SOFT) {
                    // Position along an Archimedean spiral winding out from the center.
                    val px = x + 0.5f - scx
                    val py = y + 0.5f - scy
                    val r = hypot(px, py) / maxR
                    val a = (atan2(py, px) / (2f * PI.toFloat())) + 0.5f
                    val f = (r * SPIRAL_TURNS + a) / (SPIRAL_TURNS + 1f)
                    k = smoothstep(((spiralP - f) / SPIRAL_SOFT).coerceIn(0f, 1f))
                }
                pixels[y * cols + x] = when {
                    k >= 1f -> palette[index]
                    k <= 0f -> oldPalette[index]
                    else -> mixArgb(oldPalette[index], palette[index], k)
                }
            }
        }
        bmp.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        return bmp
    }
}

@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Frame time of the app's first animated frame; shared so screens hand off without a color jump. */
private var appClockStart = -1L

/**
 * Nanoseconds since the app's first animated frame (shared by all screens, so the theme cycle and
 * background keep flowing from the loading screen into home). Stays 0 with "Remove animations".
 */
@Composable
internal fun rememberFrameNanos(reduceMotion: Boolean): State<Long> {
    val nanos = remember { mutableLongStateOf(0L) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        withFrameNanos { if (appClockStart < 0) appClockStart = it }
        while (true) {
            withFrameNanos { nanos.longValue = it - appClockStart }
        }
    }
    return nanos
}

/** The full-screen animated pixel field, with tap waves and the slow hue sway. Decorative. */
@Composable
internal fun PixelBackground(
    nanos: () -> Long,
    theme: () -> HomeTheme,
    waves: MutableList<Wave>,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    spiral: () -> Spiral? = { null }
) {
    val field = remember { PixelField() }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics {}
    ) {
        val now = nanos()
        val cellPx = CELL_DP.dp.toPx()
        val cols = ceil(size.width / cellPx).toInt().coerceAtLeast(1)
        val rows = ceil(size.height / cellPx).toInt().coerceAtLeast(1)
        val bmp = field.render(cols, rows, now, theme(), waves, spiral())
        drawImage(
            image = bmp.asImageBitmap(),
            dstSize = IntSize((cols * cellPx).roundToInt(), (rows * cellPx).roundToInt()),
            filterQuality = FilterQuality.None,
            colorFilter = if (reduceMotion) null
            else ColorFilter.colorMatrix(hueRotation(hueDegrees(now / 1e9f)))
        )
    }
}

// ---------- Screen ----------

private enum class Mode { Home, Selected, Collections }

/** Placeholder shown by "Colecciones" until there's a collections screen. */
private const val COLLECTIONS_TITLE = "PARA GORDO, ESTO TODAVÍA NO LO HICE"

/** Lets a new action cancel the typewriters and delayed steps of the previous one. */
private class ActionState {
    var job: Job? = null
    var token = 0
}

/** A random album chosen ahead of time, with its cover and colors loading (or loaded). */
private class PreparedPick(val album: SpotifyAlbum, val load: Deferred<Pair<Bitmap?, HomeTheme?>>)

private fun openInSpotify(context: Context, album: SpotifyAlbum) {
    if (album.id.startsWith("mock-")) return // fake album (debug mock mode): nothing to open
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/album/${album.id}")))
    } catch (_: Exception) {
    }
}

@Composable
fun HomeScreen(
    albums: List<SpotifyAlbum>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    /** True when home replaces the loading screen: type the title in, spiral the colors, fade in the rest. */
    intro: Boolean = false,
    /** An album to show right away (from the "Disco del día" widget); cleared via [onAlbumOpened]. */
    openAlbumId: String? = null,
    onAlbumOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    // ---- Clock and colors ----
    val frameNanos by rememberFrameNanos(reduceMotion)
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var albumCycle by remember { mutableStateOf<AlbumCycle?>(null) }
    // During the intro the UI keeps the loading screen's colors until the spiral starts.
    val introOn = intro && !reduceMotion
    if (introOn) remember { overlay = Overlay(LoadingTheme, LoadingTheme, 0L, 1f) }
    var spiral by remember { mutableStateOf<Spiral?>(null) }
    // The live color cycle, without blends (the spiral wipes the background straight into it).
    val baseTheme by remember {
        derivedStateOf {
            val seconds = frameNanos / 1e9f
            val c = albumCycle
            if (c == null) cycleTheme(seconds)
            else cycleTheme(seconds - c.startSeconds, c.themes, startIndex = 0)
        }
    }
    // Only changes (and recomposes) during blends, not while a color set holds.
    val theme by remember {
        derivedStateOf {
            val base = baseTheme
            val o = overlay ?: return@derivedStateOf base
            val p = if (reduceMotion) 1f
            else ((frameNanos - o.startNanos) / 1e9f / o.durationS).coerceIn(0f, 1f)
            mixTheme(o.from, o.to ?: base, smoothstep(p))
        }
    }
    fun blendTo(target: HomeTheme?, durationS: Float) {
        overlay = Overlay(theme, target, frameNanos, durationS)
    }
    val paletteCache = remember { mutableMapOf<String, HomeTheme>() }

    // ---- Background waves ----
    val waves = remember { mutableListOf<Wave>() }

    // ---- Layout measurements (root coordinates) ----
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var titleBounds by remember { mutableStateOf(Rect.Zero) }
    var contentBounds by remember { mutableStateOf(Rect.Zero) }
    var primaryCenter by remember { mutableStateOf(Offset.Zero) }
    var secondaryCenter by remember { mutableStateOf(Offset.Zero) }

    fun addWave(center: Offset) {
        if (reduceMotion || rootBounds.width <= 0f) return
        val origin = center - rootBounds.topLeft
        waves.add(Wave(origin.x / rootBounds.width, origin.y / rootBounds.height, frameNanos))
        if (waves.size > MAX_WAVES) waves.removeAt(0)
    }

    // ---- State ----
    var mode by remember { mutableStateOf(Mode.Home) }
    var current by remember { mutableStateOf<SpotifyAlbum?>(null) }
    var titleText by remember { mutableStateOf(if (introOn) "" else HOME_TITLE) }
    var titleFinal by remember { mutableStateOf(HOME_TITLE) }
    var titleSize by remember { mutableIntStateOf(HOME_SIZE_AUTO) }
    var homeTitleSize by remember { mutableIntStateOf(ALBUM_TITLE_MAX) }
    var ctasHomeTop by remember { mutableFloatStateOf(0f) }
    var titleCentered by remember { mutableStateOf(false) }
    // The intro starts with the cursor the loading screen brought up to the title's spot.
    var cursorOn by remember { mutableStateOf(introOn) }
    var primaryText by remember { mutableStateOf("Random") }
    var primaryIcon by remember { mutableStateOf(ShuffleIcon) }
    var secondaryText by remember { mutableStateOf("Colecciones") }
    var secondaryIcon by remember { mutableStateOf(CardsIcon) }
    var announcement by remember { mutableStateOf("") }
    var coverAlbum by remember { mutableStateOf<SpotifyAlbum?>(null) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var coverTop by remember { mutableFloatStateOf(0f) }
    var coverSide by remember { mutableFloatStateOf(0f) }
    val action = remember { ActionState() }
    var nextPick by remember { mutableStateOf<PreparedPick?>(null) }
    val randomFocus = remember { FocusRequester() }

    val slide = remember { Animatable(0f) }
    val sectionDrop = remember { Animatable(0f) }
    val sectionAlpha = remember { Animatable(1f) }
    val coverScale = remember { Animatable(0.4f) }
    val coverAlpha = remember { Animatable(0f) }
    // Intro fade/slide-in of the buttons and the album section (1 = in place).
    val introCtas = remember { Animatable(if (introOn) 0f else 1f) }
    val introSection = remember { Animatable(if (introOn) 0f else 1f) }
    var introDone by remember { mutableStateOf(!introOn) }
    // Random fades out on the Colecciones placeholder (1 = shown); its button turns into "Volver".
    val primaryShown = remember { Animatable(1f) }
    // "Volver" moves up into Random's place, right under the title (0 = its own place, 1 = up).
    val volverRise = remember { Animatable(0f) }

    fun animate(a: Animatable<Float, AnimationVector1D>, target: Float, spec: AnimationSpec<Float>) {
        scope.launch { if (reduceMotion) a.snapTo(target) else a.animateTo(target, spec) }
    }

    // ---- Geometry (px) ----
    val ctasSelectedTop = contentBounds.bottom - with(density) { (CTAS_BOTTOM + CTAS_HEIGHT).toPx() }
    val slideDistance = max(0f, ctasSelectedTop - ctasHomeTop)

    fun titleStyle(size: Int) = TextStyle(
        fontFamily = Jersey10,
        fontSize = size.sp,
        lineHeight = (size * 0.75f).sp
    )

    /** Largest size (116 → 40, step 4) at which [text] fits the title box without breaking words. */
    fun fitTitle(text: String): Pair<Int, Float> {
        val maxW = (if (titleBounds.width > 0f) titleBounds.width else with(density) { TITLE_W.toPx() }).toInt()
        val maxH = if (titleBounds.height > 0f) titleBounds.height else with(density) { TITLE_H.toPx() }
        val words = text.split(' ', '\n').filter { it.isNotEmpty() }
        fun measure(size: Int) = textMeasurer.measure(
            text = text, style = titleStyle(size), constraints = Constraints(maxWidth = maxW)
        )
        fun fits(size: Int): Boolean {
            val wordsFit = words.all {
                textMeasurer.measure(text = it, style = titleStyle(size), softWrap = false, maxLines = 1).size.width <= maxW
            }
            return wordsFit && measure(size).size.height <= maxH
        }
        var size = minOf(homeTitleSize, ALBUM_TITLE_MAX)
        while (size > TITLE_MIN && !fits(size)) size -= 4
        // The last line's baseline, i.e. where the letters visibly end (the layout box
        // continues below it with the font's descender space).
        return size to measure(size).lastBaseline
    }

    /** Erase from the end (28ms/char), call [onCleared], then type [to] (60ms/char). */
    suspend fun typewrite(get: () -> String, set: (String) -> Unit, to: String, onCleared: () -> Unit = {}) {
        if (reduceMotion) {
            onCleared()
            set(to)
            return
        }
        var text = get()
        while (text.isNotEmpty()) {
            text = text.dropLast(1)
            set(text)
            delay(ERASE_MS)
        }
        onCleared()
        for (n in 1..to.length) {
            set(to.take(n))
            if (n < to.length) delay(TYPE_MS)
        }
    }

    // Album names are centered; the home title stays left-aligned. Both switch while the title is empty.
    /** Runs [step] each frame with progress 0→1 over [durationMs]. */
    suspend fun timed(durationMs: Long, step: (Float) -> Unit) {
        val start = withFrameNanos { it }
        var p = 0f
        while (p < 1f) {
            withFrameNanos { now -> p = ((now - start) / 1e6f / durationMs).coerceAtMost(1f) }
            step(p)
        }
    }

    /** Typewriter with a fixed duration per phase, so long and short titles take the same time. */
    suspend fun retitle(text: String, size: Int, centered: Boolean) {
        val onCleared = {
            titleSize = size
            titleCentered = centered
        }
        if (reduceMotion) {
            onCleared()
            titleText = text
            titleFinal = text
            return
        }
        cursorOn = true
        val from = titleText
        if (from.isNotEmpty()) timed(TITLE_ERASE_MS) { p -> titleText = from.dropLast(ceil(p * from.length).toInt()) }
        onCleared()
        // First character right away, the last one exactly at the end.
        if (text.isNotEmpty()) {
            titleText = text.take(1)
            timed(TITLE_TYPE_MS) { p -> titleText = text.take(1 + floor(p * (text.length - 1)).toInt()) }
        }
        cursorOn = false
        titleFinal = text
    }

    suspend fun relabel(selected: Boolean) = coroutineScope {
        launch {
            typewrite({ primaryText }, { primaryText = it }, if (selected) "Escuchar" else "Random") {
                primaryIcon = if (selected) PlayIcon else ShuffleIcon
            }
        }
        launch {
            typewrite({ secondaryText }, { secondaryText = it }, if (selected) "Otro" else "Colecciones") {
                secondaryIcon = if (selected) ShuffleIcon else CardsIcon
            }
        }
    }

    fun hideCover() {
        animate(coverScale, 0.4f, tween(250, easing = CssEase))
        animate(coverAlpha, 0f, tween(250, easing = CssEase))
    }

    fun showCover() {
        animate(coverScale, 1f, tween(COVER_POP_MS, easing = CoverPop))
        animate(coverAlpha, 1f, tween(400, easing = CssEase))
    }

    /** Downloads a cover and works out its colors (colors cached per album). */
    fun loadCoverAndTheme(album: SpotifyAlbum): Deferred<Pair<Bitmap?, HomeTheme?>> = scope.async {
        val bitmap = loadCover(context, album.imageUrl)
        val albumTheme = bitmap?.let {
            paletteCache[album.id] ?: albumThemeFrom(it)?.also { t -> paletteCache[album.id] = t }
        }
        bitmap to albumTheme
    }

    // Once the library is in, the cycle takes its colors from the first few albums' covers.
    val cycleAlbums = albums.take(CYCLE_ALBUMS)
    LaunchedEffect(cycleAlbums.map { it.id }) {
        if (cycleAlbums.isEmpty()) return@LaunchedEffect
        val themes = cycleAlbums.map { loadCoverAndTheme(it) }
            .mapNotNull { it.await().second?.let(::readableForCycle) }
        if (themes.isEmpty()) return@LaunchedEffect
        // Cross-fade from what's showing into the album cycle, unless an album is selected
        // (its colors are showing; going back home will blend into the new cycle) or the intro
        // is running (its spiral and blend already head for the live cycle, whichever it is).
        if (introDone && overlay?.to == null) blendTo(null, FADE_S)
        albumCycle = AlbumCycle(themes, frameNanos / 1e9f)
    }

    /** Picks the next random album ahead of time and starts loading it, so its cover is ready on tap. */
    fun prepareNext() {
        if (albums.isEmpty()) return
        val candidates = albums.filter { it.id != current?.id }.ifEmpty { albums }
        val album = candidates.random()
        nextPick = PreparedPick(album, loadCoverAndTheme(album))
    }

    /** Picks a random album, or [chosen] when given (e.g. the widget's album of the day). */
    fun pickAlbum(origin: Offset, chosen: SpotifyAlbum? = null) {
        addWave(origin)
        if (albums.isEmpty()) return
        action.job?.cancel()
        action.token++
        // Use the album prepared in the background when it's still valid; otherwise pick now.
        val prepared = if (chosen != null) null
        else nextPick?.takeIf { p -> p.album.id != current?.id && albums.any { it.id == p.album.id } }
        if (chosen == null) nextPick = null
        val album = chosen ?: prepared?.album
            ?: if (albums.size > 1) albums.filter { it.id != current?.id }.random() else albums[0]
        val cover = prepared?.load ?: loadCoverAndTheme(album)
        current = album
        prepareNext()
        val firstPick = mode != Mode.Selected

        if (firstPick) {
            mode = Mode.Selected
            animate(slide, 1f, tween(700, easing = CtaEase))
            animate(sectionDrop, 1f, tween(500, easing = CssEase))
            animate(sectionAlpha, 0f, tween(400, easing = CssEase))
        } else {
            hideCover()
        }

        val (size, textBaseline) = fitTitle(album.name)
        action.job = scope.launch {
            if (firstPick) launch { relabel(selected = true) }
            launch {
                retitle(album.name, size, centered = true)
                announcement = album.name
            }
            launch {
                // The cover is usually loaded already (prepared in the background); then the new
                // cover appears and the colors start blending in the same frame.
                delay(if (firstPick) 350L else 250L)
                val (bitmap, albumTheme) = cover.await()
                coverAlbum = album
                coverBitmap = bitmap
                if (albumTheme != null) blendTo(albumTheme, ALBUM_IN_BLEND_S)
                // Cover fills the space between the title's letters and the Escuchar ring (at
                // least 24dp each side) and is centered in it, so the visible gaps are equal.
                // Its 10dp drop shadow counts as part of the cover.
                val textBottom = titleBounds.top + textBaseline
                val buttonsTop = ctasSelectedTop - with(density) { RING.toPx() }
                val shadow = with(density) { COVER_SHADOW.toPx() }
                val space = buttonsTop - textBottom
                val side = (space - shadow - 2 * with(density) { COVER_GAP.toPx() })
                    .coerceIn(with(density) { COVER_MIN.toPx() }, with(density) { COVER_MAX.toPx() })
                coverTop = textBottom + (space - side - shadow) / 2f - rootBounds.top
                coverSide = side
                showCover()
            }
        }
    }

    fun goHome() {
        if (mode != Mode.Selected) return
        action.job?.cancel()
        action.token++
        mode = Mode.Home
        current = null
        hideCover()
        animate(slide, 0f, tween(700, easing = CtaEase))
        animate(sectionDrop, 0f, tween(500, easing = CssEase))
        animate(sectionAlpha, 1f, tween(400, easing = CssEase))
        blendTo(null, ALBUM_BLEND_S)
        action.job = scope.launch {
            launch { relabel(selected = false) }
            launch {
                retitle(HOME_TITLE, HOME_SIZE_AUTO, centered = false)
                announcement = "Inicio"
            }
        }
        try {
            randomFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    /** "Colecciones": the buttons and albums fade away and the title types the placeholder. */
    fun openCollections(origin: Offset) {
        addWave(origin)
        if (mode != Mode.Home) return
        action.job?.cancel()
        action.token++
        mode = Mode.Collections
        animate(primaryShown, 0f, tween(400, easing = CssEase))
        animate(volverRise, 1f, tween(600, easing = CtaEase))
        animate(sectionDrop, 1f, tween(500, easing = CssEase))
        animate(sectionAlpha, 0f, tween(400, easing = CssEase))
        val (size, _) = fitTitle(COLLECTIONS_TITLE)
        action.job = scope.launch {
            launch {
                typewrite({ secondaryText }, { secondaryText = it }, "Volver") { secondaryIcon = BackIcon }
            }
            retitle(COLLECTIONS_TITLE, size, centered = false)
            announcement = COLLECTIONS_TITLE
        }
    }

    /** Back from the placeholder: everything returns and the home title types again. */
    fun closeCollections() {
        if (mode != Mode.Collections) return
        action.job?.cancel()
        action.token++
        mode = Mode.Home
        animate(primaryShown, 1f, tween(500, easing = CtaEase))
        animate(volverRise, 0f, tween(600, easing = CtaEase))
        animate(sectionDrop, 0f, tween(500, easing = CssEase))
        animate(sectionAlpha, 1f, tween(400, easing = CssEase))
        action.job = scope.launch {
            launch {
                typewrite({ secondaryText }, { secondaryText = it }, "Colecciones") { secondaryIcon = CardsIcon }
            }
            retitle(HOME_TITLE, HOME_SIZE_AUTO, centered = false)
            announcement = "Inicio"
        }
        try {
            randomFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    BackHandler(enabled = mode == Mode.Selected) { goHome() }

    // Opened from the widget: once home is ready (after the intro), show that album.
    LaunchedEffect(openAlbumId, introDone, albums) {
        val id = openAlbumId ?: return@LaunchedEffect
        if (!introDone || albums.isEmpty()) return@LaunchedEffect
        onAlbumOpened()
        val album = albums.firstOrNull { it.id == id } ?: return@LaunchedEffect
        if (mode == Mode.Selected && current?.id == id) return@LaunchedEffect
        if (mode == Mode.Collections) closeCollections()
        pickAlbum(primaryCenter, album)
    }
    BackHandler(enabled = mode == Mode.Collections) { closeCollections() }

    // Intro after the loading screen: type the title in, then spiral the background from the
    // loading colors into the album colors (waiting briefly for the covers), then fade the rest in.
    LaunchedEffect(Unit) {
        if (!introOn) return@LaunchedEffect
        launch {
            delay(INTRO_TYPE_DELAY_MS)
            retitle(HOME_TITLE, HOME_SIZE_AUTO, centered = false)
            announcement = "Inicio"
        }
        withTimeoutOrNull(INTRO_WAIT_COVERS_MS) { snapshotFlow { albumCycle }.first { it != null } }
        spiral = Spiral(LoadingTheme.palette, frameNanos, SPIRAL_S)
        blendTo(null, SPIRAL_S)
        launch {
            delay(INTRO_CTAS_DELAY_MS)
            introCtas.animateTo(1f, tween(500, easing = CtaEase))
        }
        launch {
            delay(INTRO_SECTION_DELAY_MS)
            introSection.animateTo(1f, tween(600, easing = CtaEase))
        }
        delay((SPIRAL_S * 1000).toLong())
        spiral = null
        introDone = true
    }

    // Have the first pick ready as soon as the library is loaded.
    LaunchedEffect(albums) {
        if (nextPick == null) prepareNext()
    }

    // ---- UI ----
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootBounds = it.boundsInRoot() }
    ) {
        PixelBackground(
            nanos = { frameNanos },
            theme = { if (spiral != null) baseTheme else theme },
            waves = waves,
            reduceMotion = reduceMotion,
            spiral = { spiral }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .onGloballyPositioned { contentBounds = it.boundsInRoot() }
        ) {
          // Title on top (sized to fill the width), Random centered in the space left above the
          // album section, which is anchored at the bottom.
          BoxWithConstraints(
              modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth()
                  .padding(start = SCREEN_PAD_H, end = SCREEN_PAD_H, top = SCREEN_PAD_TOP)
          ) {
            val titleLayout = rememberHomeTitleLayout(maxWidth, maxHeight)
            SideEffect { homeTitleSize = titleLayout.size }
            Column(modifier = Modifier.fillMaxSize()) {
                // Title and buttons as one group, centered in the space above the albums.
                Spacer(Modifier.height(titleLayout.top))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(titleLayout.height)
                        .onGloballyPositioned { titleBounds = it.boundsInRoot() }
                ) {
                    val cursorVisible = cursorOn && (reduceMotion || cursorBlinkOn(frameNanos))
                    Text(
                        text = titleText + if (cursorVisible) "▌" else "",
                        color = theme.title,
                        style = titleStyle(if (titleSize == HOME_SIZE_AUTO) titleLayout.size else titleSize),
                        textAlign = if (titleCentered) TextAlign.Center else TextAlign.Start,
                        // Never clipped by the box (e.g. by the taller cursor glyph while typing).
                        modifier = Modifier
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .fillMaxWidth()
                            .clearAndSetSemantics {
                                heading()
                                contentDescription = titleFinal
                            }
                    )
                }

              Spacer(Modifier.height(TITLE_CTA_GAP))
              run {
                // Moved with `offset` (layout, not a graphics layer) so the ring keeps
                // blending with the background while the buttons slide.
                // The intro fades them in (a graphics layer only while fading, so the ring's
                // blend works again once they're fully in).
                Column(
                    modifier = Modifier
                        // Before the offset, so it reports the resting (home) position.
                        .onGloballyPositioned { ctasHomeTop = it.boundsInRoot().top }
                        .offset {
                            val intro = (1f - introCtas.value) * INTRO_RISE.toPx()
                            IntOffset(0, (slide.value * slideDistance + intro).roundToInt())
                        }
                        .then(
                            if (introCtas.value < 1f) Modifier.graphicsLayer { alpha = introCtas.value }
                            else Modifier
                        )
                ) {
                    Button(
                        onClick = {
                            if (!introDone || mode == Mode.Collections) return@Button
                            if (mode == Mode.Home) pickAlbum(primaryCenter)
                            else {
                                addWave(primaryCenter)
                                // TODO: play in-app once there's a player; for now open the album in Spotify.
                                current?.let { openInSpotify(context, it) }
                            }
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primaryBg,
                            contentColor = theme.primaryFg
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PRIMARY_H)
                            .focusRequester(randomFocus)
                            .onGloballyPositioned { primaryCenter = it.boundsInRoot().center }
                            // A graphics layer only while hidden/fading (it also holds the ring, so the
                            // ring fades too); none when shown, so the ring's blend reads the background.
                            .then(
                                if (primaryShown.value < 1f) Modifier.graphicsLayer { alpha = primaryShown.value }
                                else Modifier
                            )
                            .invertedRing()
                            .semantics { contentDescription = if (mode == Mode.Selected) "Escuchar" else "Random" }
                    ) {
                        Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            primaryText,
                            fontFamily = Jersey10, fontSize = 36.sp, lineHeight = 36.sp, letterSpacing = 0.04.em,
                            modifier = Modifier.clearAndSetSemantics {}
                        )
                    }

                    Spacer(Modifier.height(CTA_GAP))
                    OutlinedButton(
                        onClick = {
                            if (!introDone) return@OutlinedButton
                            when (mode) {
                                Mode.Selected -> pickAlbum(secondaryCenter)
                                Mode.Home -> openCollections(secondaryCenter)
                                Mode.Collections -> {
                                    addWave(secondaryCenter)
                                    closeCollections()
                                }
                            }
                        },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(3.dp, theme.secondaryBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = theme.secondaryBg,
                            contentColor = theme.secondaryFg
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            // Up into Random's place on the Colecciones placeholder (layout offset,
                            // so its tap target and wave origin move with it).
                            .offset { IntOffset(0, -((PRIMARY_H + CTA_GAP).toPx() * volverRise.value).roundToInt()) }
                            .fillMaxWidth()
                            .height(SECONDARY_H)
                            .onGloballyPositioned { secondaryCenter = it.boundsInRoot().center }
                            .semantics {
                                contentDescription = when (mode) {
                                    Mode.Selected -> "Otro"
                                    Mode.Collections -> "Volver"
                                    Mode.Home -> "Colecciones"
                                }
                            }
                    ) {
                        Icon(secondaryIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            secondaryText,
                            fontFamily = Jersey10, fontSize = 28.sp, lineHeight = 28.sp, letterSpacing = 0.04.em,
                            modifier = Modifier.clearAndSetSemantics {}
                        )
                    }
                }
            }

            }
          }

            // Album section anchored at the bottom; drops and fades out while an album is selected.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = SECTION_BOTTOM)
                    .offset {
                        val intro = (1f - introSection.value) * INTRO_RISE.toPx()
                        IntOffset(0, (sectionDrop.value * 160.dp.toPx() + intro).roundToInt())
                    }
                    .graphicsLayer { alpha = sectionAlpha.value * introSection.value },
                verticalArrangement = Arrangement.spacedBy(SECTION_GAP)
            ) {
                Text(
                    text = if (isLoading && albums.isEmpty()) "CARGANDO…" else "${albums.size} ÁLBUMES",
                    color = theme.title,
                    fontFamily = Jersey10,
                    fontSize = 22.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.06.em,
                    modifier = Modifier.padding(horizontal = SCREEN_PAD_H)
                )
                AlbumStrip(
                    albums = albums,
                    nanos = { if (reduceMotion) 0L else frameNanos },
                    modifier = Modifier.height(SECTION_COVER)
                )
            }
        }

        // The picked cover: decorative, never takes touches.
        val bitmap = coverBitmap
        if (coverAlbum != null && coverSide > 0f) {
            // The same bitmap the colors were taken from, so it's ready the moment it appears.
            Box(
                modifier = Modifier
                    .offset { IntOffset(((rootBounds.width - coverSide) / 2f).roundToInt(), coverTop.roundToInt()) }
                    .size(with(density) { coverSide.toDp() })
                    .graphicsLayer {
                        scaleX = coverScale.value
                        scaleY = coverScale.value
                        alpha = coverAlpha.value
                    }
                    .clearAndSetSemantics {}
                    .drawBehind {
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.25f),
                            topLeft = Offset(0f, COVER_SHADOW.toPx()),
                            size = size,
                            cornerRadius = CornerRadius(16.dp.toPx())
                        )
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Announces the final title once (never letter by letter).
        Box(
            modifier = Modifier
                .size(1.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = announcement
                }
        )
    }
}

/**
 * A 5dp ring around the Random button that inverts whatever is behind it
 * (white with Difference blend). Advanced blend modes need Android 10+;
 * older devices show a plain white ring.
 */
internal fun Modifier.invertedRing() = drawBehind {
    val ring = RING.toPx()
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(-ring, -ring),
        size = Size(size.width + 2 * ring, size.height + 2 * ring),
        cornerRadius = CornerRadius(size.height / 2 + ring),
        blendMode = BlendMode.Difference
    )
}

/** Decorative auto-scrolling strip of real covers, looping seamlessly to the left. */
@Composable
private fun AlbumStrip(albums: List<SpotifyAlbum>, nanos: () -> Long, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .clearAndSetSemantics {}
    ) {
        // Each cover's side is the height left for the strip.
        val cover = maxHeight
        val step = cover + STRIP_GAP
        // Repeat a short library so one set is wider than the screen, then render two sets.
        val minCovers = ceil(maxWidth / step).toInt() + 1
        val set = remember(albums, minCovers) {
            val base = albums.take(STRIP_MAX_COVERS)
            if (base.isEmpty()) base
            else List(ceil(minCovers / base.size.toFloat()).toInt().coerceAtLeast(1)) { base }.flatten()
        }
        if (set.isEmpty()) return@BoxWithConstraints
        val stepPx = with(LocalDensity.current) { step.toPx() }
        val setWidthPx = set.size * stepPx
        val speedPx = STRIP_COVERS_PER_S * stepPx

        Row(
            horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
            modifier = Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .padding(start = 24.dp)
                .graphicsLayer {
                    translationX = -((nanos() / 1e9f * speedPx) % setWidthPx)
                }
        ) {
            (set + set).forEach { album ->
                AsyncImage(
                    model = album.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(cover)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                )
            }
        }
    }
}

/** Two stacked cards (collections). */
private val CardsIcon: ImageVector = ImageVector.Builder(
    name = "Cards", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).path(
    stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
) {
    // Front card
    moveTo(5f, 9f); horizontalLineToRelative(10f)
    arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); verticalLineToRelative(8f)
    arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); horizontalLineToRelative(-10f)
    arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); verticalLineToRelative(-8f)
    arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); close()
    // Back card, peeking out top-right
    moveTo(7f, 6f); verticalLineToRelative(-1f)
    arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); horizontalLineToRelative(10f)
    arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); verticalLineToRelative(8f)
    arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); horizontalLineToRelative(-1f)
}.build()

internal val ShuffleIcon: ImageVector = ImageVector.Builder(
    name = "Shuffle", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).path(
    stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
) {
    moveTo(16f, 3f); horizontalLineToRelative(5f); verticalLineToRelative(5f)
    moveTo(4f, 20f); lineTo(21f, 3f)
    moveTo(21f, 16f); verticalLineToRelative(5f); horizontalLineToRelative(-5f)
    moveTo(15f, 15f); lineToRelative(6f, 6f)
    moveTo(4f, 4f); lineToRelative(5f, 5f)
}.build()

/** Left arrow ("Volver"). */
private val BackIcon: ImageVector = ImageVector.Builder(
    name = "Back", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).path(
    stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f,
    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
) {
    moveTo(20f, 12f); horizontalLineTo(5f)
    moveTo(11f, 5f); lineTo(4f, 12f); lineTo(11f, 19f)
}.build()

private val PlayIcon: ImageVector = ImageVector.Builder(
    name = "Play", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).path(fill = SolidColor(Color.Black)) {
    moveTo(7f, 4.5f); verticalLineToRelative(15f); lineToRelative(13f, -7.5f); close()
}.build()
