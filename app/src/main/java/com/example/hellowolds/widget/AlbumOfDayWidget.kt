package com.example.hellowolds.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.hellowolds.BuildConfig
import com.example.hellowolds.MainActivity
import com.example.hellowolds.R
import com.example.hellowolds.spotify.LibraryCache
import com.example.hellowolds.spotify.MockSpotify
import com.example.hellowolds.spotify.SpotifyAlbum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

/**
 * "Disco del día" home-screen widget: one album from the library saved on the phone, the same all
 * day (it changes at midnight). It never calls the Spotify API; the cover comes from Spotify's
 * image servers (or the mock covers) through Coil's cache. Tapping it opens the app on that album.
 */
class AlbumOfDayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val views = buildViews(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Intent extra with the album to open when the app is launched from the widget. */
        const val EXTRA_ALBUM_ID = "com.example.hellowolds.ALBUM_ID"

        private const val COVER_PX = 360
        private const val TEXT_W = 520            // px width the text image is drawn at (then scaled)
        private const val DEFAULT_LABEL = 0xFFFFE9A8.toInt()

        /** Redraws any widgets on the home screen (e.g. after the library changed). */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AlbumOfDayWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, AlbumOfDayWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        /** Today's album: a pick seeded by the date, so it's stable for the whole day. */
        suspend fun albumOfTheDay(context: Context): SpotifyAlbum? {
            val albums = if (BuildConfig.MOCK_SPOTIFY) MockSpotify.savedAlbums(context)
            else LibraryCache(context).load()?.albums
            if (albums.isNullOrEmpty()) return null
            val today = Calendar.getInstance()
            val seed = today.get(Calendar.YEAR) * 1000L + today.get(Calendar.DAY_OF_YEAR)
            return albums[Random(seed).nextInt(albums.size)]
        }

        private suspend fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_album_of_day)
            val album = albumOfTheDay(context)

            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (album != null) open.putExtra(EXTRA_ALBUM_ID, album.id)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            if (album == null) {
                views.setImageViewBitmap(
                    R.id.widget_text,
                    textImage(context, context.getString(R.string.widget_label), context.getString(R.string.widget_empty), DEFAULT_LABEL)
                )
                return views
            }

            views.setContentDescription(
                R.id.widget_cover,
                "${context.getString(R.string.widget_label)}: ${album.name}, ${album.artistName}"
            )
            val label = context.getString(R.string.widget_label)
            views.setImageViewBitmap(R.id.widget_text, textImage(context, label, album.name, DEFAULT_LABEL))
            val cover = loadCover(context, album.imageUrl) ?: return views
            views.setImageViewBitmap(R.id.widget_cover, roundCorners(cover, COVER_PX * 0.06f))

            // Colors from the cover, like the app: a dark tone for the background, white title,
            // and a light tone for the label (only if it stands out enough).
            val palette = Palette.from(cover).maximumColorCount(16).generate()
            val bg = (palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch)?.rgb
            if (bg != null) {
                val dark = ColorUtils.blendARGB(bg, 0xFF000000.toInt(), 0.35f)
                views.setInt(R.id.widget_bg, "setColorFilter", dark)
                val accent = listOfNotNull(palette.lightVibrantSwatch, palette.lightMutedSwatch, palette.vibrantSwatch)
                    .map { it.rgb }
                    .firstOrNull { ColorUtils.calculateContrast(it, dark) >= 4.5 }
                    ?: DEFAULT_LABEL
                views.setImageViewBitmap(R.id.widget_text, textImage(context, label, album.name, accent))
            }
            return views
        }

        private suspend fun loadCover(context: Context, url: String?): Bitmap? {
            if (url.isNullOrEmpty()) return null
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(COVER_PX)
                .allowHardware(false)
                .build()
            return (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap(COVER_PX, COVER_PX)
        }

        /** "DISCO DEL DÍA" + the album name (up to 2 lines) in the app's pixel font, as an image. */
        private fun textImage(context: Context, label: String, title: String, labelColor: Int): Bitmap {
            val font = ResourcesCompat.getFont(context, R.font.jersey10_regular)
            val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = font
                textSize = 40f
                letterSpacing = 0.06f
                color = labelColor
            }
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = font
                textSize = 68f
                color = 0xFFFFFFFF.toInt()
            }
            val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, TEXT_W)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0f, 0.85f)
                .build()
            val labelH = (labelPaint.fontMetrics.descent - labelPaint.fontMetrics.ascent)
            val gap = 4f
            val bmp = Bitmap.createBitmap(TEXT_W, (labelH + gap + titleLayout.height).toInt() + 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawText(label, 0f, -labelPaint.fontMetrics.ascent, labelPaint)
            canvas.translate(0f, labelH + gap)
            titleLayout.draw(canvas)
            return bmp
        }

        /** Widgets can't clip images, so the corners are rounded in the bitmap itself. */
        private fun roundCorners(src: Bitmap, radius: Float): Bitmap {
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, paint)
            return out
        }
    }
}
