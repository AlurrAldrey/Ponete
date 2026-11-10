package com.example.hellowolds.spotify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Fake Spotify data for local testing (debug builds with `mockSpotify=true` in gradle.properties),
 * so trying the app doesn't spend the developer account's Spotify quota. Nothing here touches the
 * network: covers are generated pixel art, written once to the app's cache and loaded as files.
 */
object MockSpotify {
    /** How long the fake library takes to "load", so the loading screen still shows. */
    const val LOAD_DELAY_MS = 2_500L

    val profile = SpotifyProfile(id = "mock", displayName = "Gordo (mock)", product = "premium")

    // Mixed lengths on purpose, to exercise the title fitting (short, long, accents, ñ).
    private val names = listOf(
        "Siesta Eterna", "Asado de Domingo", "Mate Amargo", "Ruido Blanco",
        "El Gordo en la Luna", "Cumbia para Robots", "Lluvia en Quilmes", "Piñata",
        "Discos de Papá Vol. 1", "Nunca Te Vas a Ir del Todo", "Bajo Cero", "Kiosco 24 Horas",
        "Canciones para Lavar los Platos", "Ómnibus", "Dale Que Va", "La Última Fila",
        "Verano del 98", "Radio Fantasma", "Hasta Acá Llegué", "Sí"
    )
    private val artists = listOf(
        "Los Tíos", "La Banda del Patio", "Gordo y los Flacos", "Fantasma Club",
        "Señora Cassette", "Los Discos de Papá", "Turbo Siesta", "Mono Estéreo"
    )

    /** 40 fake albums; their covers are generated on first use and cached as PNGs. */
    suspend fun savedAlbums(context: Context): List<SpotifyAlbum> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "mock_covers").apply { mkdirs() }
        List(40) { i ->
            val name = names[i % names.size] + if (i >= names.size) " (Remasterizado)" else ""
            val file = File(dir, "cover_$i.png")
            if (!file.exists()) {
                file.outputStream().use { coverFor(i).compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            SpotifyAlbum(
                id = "mock-$i",
                name = name,
                artistName = artists[i % artists.size],
                imageUrl = Uri.fromFile(file).toString(),
                totalTracks = 8 + i % 7,
                releaseDate = "${1970 + (i * 7) % 55}"
            )
        }
    }

    /** A 16×16 pixel-art cover from 3 colors, scaled up to 320×320 with hard edges. */
    private fun coverFor(seed: Int): Bitmap {
        val rnd = Random(seed * 7919 + 13)
        val hue = rnd.nextFloat() * 360f
        val colors = intArrayOf(
            Color.HSVToColor(floatArrayOf(hue, 0.55f + rnd.nextFloat() * 0.35f, 0.25f + rnd.nextFloat() * 0.2f)),
            Color.HSVToColor(floatArrayOf((hue + 30f + rnd.nextFloat() * 60f) % 360f, 0.6f + rnd.nextFloat() * 0.3f, 0.75f)),
            Color.HSVToColor(floatArrayOf((hue + 160f + rnd.nextFloat() * 60f) % 360f, 0.3f + rnd.nextFloat() * 0.4f, 0.95f))
        )
        val n = 16
        val cell = 20
        val pattern = seed % 5
        val bmp = Bitmap.createBitmap(n * cell, n * cell, Bitmap.Config.ARGB_8888)
        for (y in 0 until n) for (x in 0 until n) {
            val k = when (pattern) {
                0 -> (x + y) / 3 % 3                                         // diagonal stripes
                1 -> ((x / 4) + (y / 4)) % 2 + if (rnd.nextFloat() < 0.08f) 1 else 0 // checker blocks
                2 -> minOf(minOf(x, n - 1 - x), minOf(y, n - 1 - y)) / 2 % 3  // concentric squares
                3 -> if (hypot(x - 7.5, y - 7.5) < 5.5) 2 else (y / 6) % 2   // sun over bands
                else -> rnd.nextInt(3)                                        // noise
            }.coerceIn(0, 2)
            val c = colors[k]
            for (py in 0 until cell) for (px in 0 until cell) bmp.setPixel(x * cell + px, y * cell + py, c)
        }
        return bmp
    }
}
