package com.example.hellowolds.spotify

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The saved-albums library as last fetched, with when it was saved. */
class CachedLibrary(val albums: List<SpotifyAlbum>, val savedAt: Long)

/**
 * The saved-albums library on disk (a JSON file in the app's private storage), so launches can
 * show it right away instead of re-downloading the whole library from Spotify every time.
 */
class LibraryCache(context: Context) {
    private val file = File(context.filesDir, "spotify_library.json")

    suspend fun load(): CachedLibrary? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            val json = JSONObject(file.readText())
            val items = json.getJSONArray("albums")
            val albums = List(items.length()) { i ->
                val o = items.getJSONObject(i)
                SpotifyAlbum(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    artistName = o.optString("artist"),
                    imageUrl = if (o.isNull("image")) null else o.optString("image"),
                    totalTracks = o.optInt("tracks"),
                    releaseDate = o.optString("release")
                )
            }
            CachedLibrary(albums, json.optLong("savedAt"))
        } catch (_: Exception) {
            null // unreadable or old format: behave as if there's no cache
        }
    }

    suspend fun save(albums: List<SpotifyAlbum>) = withContext(Dispatchers.IO) {
        val items = JSONArray()
        albums.forEach { a ->
            items.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("artist", a.artistName)
                    .put("image", a.imageUrl ?: JSONObject.NULL)
                    .put("tracks", a.totalTracks)
                    .put("release", a.releaseDate)
            )
        }
        val json = JSONObject().put("savedAt", System.currentTimeMillis()).put("albums", items)
        // Write to a temp file first so a crash mid-write never leaves a broken cache.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    fun clear() {
        file.delete()
    }
}
