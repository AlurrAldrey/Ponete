package com.example.hellowolds.home

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CONNECT_TITLE = "CONECTATE\nA SPOTI,\nGORDO!"
private const val LOADING_TITLE = "ESPERA\nGORDO,\nESTAMOS\nCARGANDO\nTU MÚSICA"

/**
 * Shown until the user is logged in to Spotify. Looks like the home screen (same background,
 * themes and title), with one button that opens Spotify's login page. While a saved login is
 * loading the library it shows "ESPERA GORDO, ESTAMOS CARGANDO TU MÚSICA" and a pixel loader.
 * When [finishing] turns true (library loaded) it erases the title, fades the loader out and
 * calls [onFinished], so home can type its own title in.
 */
@Composable
fun ConnectScreen(
    isLoading: Boolean,
    error: String?,
    authUrlToLoad: String?,
    onConnect: () -> Unit,
    onAuthUrlOpened: () -> Unit,
    modifier: Modifier = Modifier,
    finishing: Boolean = false,
    onFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(authUrlToLoad) {
        val url = authUrlToLoad
        if (!url.isNullOrEmpty()) {
            try {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            } catch (_: Exception) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            onAuthUrlOpened()
        }
    }

    val reduceMotion = rememberReduceMotion()
    val frameNanos by rememberFrameNanos(reduceMotion)
    // A calm dusk background while the library loads; the normal cycle otherwise.
    val theme by remember(isLoading) {
        derivedStateOf { if (isLoading) LoadingTheme else cycleTheme(frameNanos / 1e9f) }
    }
    val waves = remember { mutableListOf<Wave>() }

    // Hand-off to home: erase the loading title from the end while the loader fades out, leaving
    // the block cursor where the text started; then the cursor travels up to the home title's
    // spot (growing to its size) and home types its title from there.
    var shownChars by remember { mutableIntStateOf(LOADING_TITLE.length) }
    var erasing by remember { mutableStateOf(false) }
    var traveling by remember { mutableStateOf(false) }
    val travel = remember { Animatable(0f) }
    var loadingTitleTop by remember { mutableFloatStateOf(0f) }   // px from the column's top
    var loadingTitleSize by remember { mutableIntStateOf(116) }
    val loaderAlpha = remember { Animatable(1f) }
    LaunchedEffect(finishing) {
        if (!finishing) return@LaunchedEffect
        if (reduceMotion) {
            onFinished()
            return@LaunchedEffect
        }
        erasing = true
        launch { loaderAlpha.animateTo(0f, tween(LOADER_FADE_MS)) }
        val start = withFrameNanos { it }
        var p = 0f
        while (p < 1f) {
            withFrameNanos { now -> p = ((now - start) / 1e6f / LOADING_ERASE_MS).coerceAtMost(1f) }
            shownChars = LOADING_TITLE.length - max(1, ceil(p * LOADING_TITLE.length).toInt())
        }
        delay(CURSOR_REST_MS)
        traveling = true
        travel.animateTo(1f, tween(CURSOR_TRAVEL_MS, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
        onFinished()
    }

    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    fun connect() {
        if (!reduceMotion && rootBounds.width > 0f) {
            val origin = buttonCenter - rootBounds.topLeft
            waves.add(Wave(origin.x / rootBounds.width, origin.y / rootBounds.height, frameNanos))
            if (waves.size > MAX_WAVES) waves.removeAt(0)
        }
        onConnect()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootBounds = it.boundsInRoot() }
    ) {
        PixelBackground(nanos = { frameNanos }, theme = { theme }, waves = waves, reduceMotion = reduceMotion)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(start = SCREEN_PAD_H, end = SCREEN_PAD_H, top = SCREEN_PAD_TOP)
        ) {
            if (isLoading) {
                // While loading, the title grows to fill everything above the loader strip.
                FittedTitle(
                    text = LOADING_TITLE,
                    shown = when {
                        traveling -> ""
                        erasing -> LOADING_TITLE.take(shownChars) + if (cursorBlinkOn(frameNanos)) "▌" else ""
                        else -> LOADING_TITLE
                    },
                    onTextTop = { top, size ->
                        loadingTitleTop = top
                        loadingTitleSize = size
                    },
                    color = theme.title,
                    // Looser lines, so the accent on "MÚSICA" clears "CARGANDO" above it.
                    lineHeight = LOADING_LINE_HEIGHT,
                    maxSize = 200,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f)
                )
            } else {
                FittedTitle(text = CONNECT_TITLE, color = theme.title, modifier = Modifier.height(TITLE_H))
            }

            Spacer(Modifier.height(24.dp))

            if (isLoading) {
                // A slim equalizer straight on the background, a bit above the bottom edge.
                PixelEqualizer(
                    color = theme.title,
                    shadow = contrastShadow(theme.title),
                    nanos = { frameNanos },
                    modifier = Modifier
                        .padding(bottom = 112.dp)
                        .fillMaxWidth()
                        .height(STRIP_H)
                        .graphicsLayer { alpha = loaderAlpha.value }
                        .semantics { contentDescription = "Cargando" }
                )
            } else {
                Button(
                    onClick = { connect() },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primaryBg,
                        contentColor = theme.primaryFg
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .onGloballyPositioned { buttonCenter = it.boundsInRoot().center }
                        .invertedRing()
                ) {
                    Icon(LoginIcon, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Conectar",
                        fontFamily = Jersey10, fontSize = 36.sp, lineHeight = 36.sp, letterSpacing = 0.04.em
                    )
                }
            }

            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    error,
                    color = theme.title,
                    fontFamily = Jersey10,
                    fontSize = 22.sp,
                    lineHeight = 24.sp
                )
            }
        }

        // The cursor on its way from the erased loading title up to the home title's spot. Same
        // insets/padding as the column above (= home's title box), so it lands exactly where
        // home draws its own cursor.
        if (traveling) {
            val k = travel.value
            val lineH = LOADING_LINE_HEIGHT + (HOME_LINE_HEIGHT - LOADING_LINE_HEIGHT) * k
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(start = SCREEN_PAD_H, end = SCREEN_PAD_H, top = SCREEN_PAD_TOP)
            ) {
                // Home's title area is this box minus the album section at the bottom.
                val home = rememberHomeTitleLayout(maxWidth, maxHeight - SECTION_TOTAL)
                val size = loadingTitleSize + (home.size - loadingTitleSize) * k
                val homeTop = with(LocalDensity.current) { home.top.toPx() }
                Text(
                    text = "▌",
                    color = theme.title,
                    style = TextStyle(fontFamily = Jersey10, fontSize = size.sp, lineHeight = (size * lineH).sp),
                    modifier = Modifier
                        .offset { IntOffset(0, (loadingTitleTop * (1f - k) + homeTop * k).roundToInt()) }
                        .clearAndSetSemantics {}
                )
            }
        }
    }
}

private val STRIP_H = 40.dp
private const val LOADING_ERASE_MS = 600L
private const val LOADER_FADE_MS = 400
private const val CURSOR_REST_MS = 250L
private const val CURSOR_TRAVEL_MS = 550
private const val LOADING_LINE_HEIGHT = 0.9f
private val EQ_CELL = 6.dp             // target size; stretched so the columns fill the width exactly
private val EQ_COL_GAP = 6.dp          // airier between bars than within them
private val EQ_ROW_GAP = 2.dp
private val EQ_SHADOW = 2.dp

/** Near-black or white, whichever stands out more against [c], for the loader's drop shadow. */
private fun contrastShadow(c: Color): Color =
    if (ColorUtils.calculateLuminance(c.toArgb()) > 0.4) Color(0xFF111111) else Color.White
private const val EQ_FPS = 10f         // stepped motion, like a sprite animation

/**
 * Loading indicator: a pixel-block equalizer that fills its whole box, each block with a hard
 * [shadow] so it stays visible over the moving background. Each column is a bar of
 * square cells that grows up and down from the middle row, bouncing at its own speed. The cell
 * size is stretched so the columns span the full width; rows fill the height (odd count, centered).
 * Time is stepped at [EQ_FPS] so it moves in hard pixel jumps. With animations off it's still.
 */
@Composable
private fun PixelEqualizer(color: Color, shadow: Color, nanos: () -> Long, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val shadowOffset = EQ_SHADOW.toPx().roundToInt().toFloat()
        val colGap = EQ_COL_GAP.toPx()
        val rowGap = EQ_ROW_GAP.toPx()
        val cols = ((size.width + colGap) / (EQ_CELL.toPx() + colGap)).toInt().coerceAtLeast(1)
        val cell = (size.width - colGap * (cols - 1)) / cols
        val step = cell + colGap
        val rowStep = cell + rowGap
        var rows = ((size.height + rowGap) / rowStep).toInt().coerceAtLeast(1)
        if (rows % 2 == 0) rows -= 1
        val top = (size.height - (rows * rowStep - rowGap)) / 2f
        val mid = rows / 2
        val maxLevel = mid + 1   // 1 = just the center cell

        val t = floor(nanos() / 1e9f * EQ_FPS) / EQ_FPS
        for (c in 0 until cols) {
            // Two sines per column (different speeds/phases) so the bars don't look in sync;
            // a center bias makes the middle columns a bit taller, like a waveform.
            val v = 0.5f + 0.3f * sin(t * (3.1f + c * 0.37f) + c * 1.7f) +
                0.2f * sin(t * (5.3f - c * 0.21f) + c * 0.9f)
            val bias = 0.2f * (1f - 2f * abs(c - (cols - 1) / 2f) / max(1, cols - 1)) - 0.05f
            val level = 1 + ((v + bias) * (maxLevel - 1) + 0.5f).toInt().coerceIn(0, maxLevel - 1)
            // Snap to whole pixels so every block has crisp edges.
            val x0 = (c * step).roundToInt().toFloat()
            val x1 = (c * step + cell).roundToInt().toFloat()
            for (r in (mid - level + 1)..(mid + level - 1)) {
                val y0 = (top + r * rowStep).roundToInt().toFloat()
                val y1 = (top + r * rowStep + cell).roundToInt().toFloat()
                // Hard pixel drop shadow first, so the blocks read over any background color.
                drawRect(shadow, topLeft = Offset(x0 + shadowOffset, y0 + shadowOffset), size = Size(x1 - x0, y1 - y0))
                drawRect(color, topLeft = Offset(x0, y0), size = Size(x1 - x0, y1 - y0))
            }
        }
    }
}

/**
 * A title in the box given by [modifier], at the largest size up to [maxSize]sp (in 2sp steps)
 * where no line is wider than the box and the whole text fits its height.
 */
@Composable
private fun FittedTitle(
    text: String,
    color: Color,
    /** What's drawn (e.g. part of [text] while erasing); the size is always fitted to [text]. */
    shown: String = text,
    modifier: Modifier = Modifier,
    lineHeight: Float = 0.75f,
    maxSize: Int = 116,
    alignment: Alignment = Alignment.TopStart,
    /** Reports where the full text's block starts (px from this box's top) and its fitted size. */
    onTextTop: (Float, Int) -> Unit = { _, _ -> }
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        val maxW = with(density) { maxWidth.roundToPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val lineCount = text.lines().size
        val size = remember(text, maxW, maxH, lineHeight, maxSize) {
            var s = maxSize
            while (s > 40 && (lineCount * with(density) { (s * lineHeight).sp.toPx() } > maxH || text.lines().any { line ->
                    textMeasurer.measure(
                        text = line,
                        style = TextStyle(fontFamily = Jersey10, fontSize = s.sp),
                        softWrap = false,
                        maxLines = 1
                    ).size.width > maxW
                })) s -= 2
            s
        }
        val style = TextStyle(fontFamily = Jersey10, fontSize = size.sp, lineHeight = (size * lineHeight).sp)
        // The block keeps the full text's height, so erasing (or typing) doesn't move the lines.
        val fullH = remember(text, style, maxW) {
            textMeasurer.measure(text = text, style = style, constraints = Constraints(maxWidth = maxW)).size.height
        }
        val top = if (alignment == Alignment.CenterStart) ((maxH - fullH) / 2f).coerceAtLeast(0f) else 0f
        SideEffect { onTextTop(top, size) }
        Box(modifier = Modifier.fillMaxWidth().height(with(density) { fullH.toDp() })) {
            Text(
                text = shown,
                color = color,
                style = style,
                modifier = Modifier.clearAndSetSemantics {
                    heading()
                    contentDescription = text
                }
            )
        }
    }
}

private val LoginIcon: ImageVector = ImageVector.Builder(
    name = "Login", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).path(
    stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
) {
    moveTo(15f, 3f); horizontalLineToRelative(4f)
    arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); verticalLineToRelative(14f)
    arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); horizontalLineToRelative(-4f)
    moveTo(10f, 17f); lineToRelative(5f, -5f); lineToRelative(-5f, -5f)
    moveTo(15f, 12f); horizontalLineTo(3f)
}.build()
