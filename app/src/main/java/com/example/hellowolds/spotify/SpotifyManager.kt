package com.example.hellowolds.spotify

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

object SpotifyManager {
    /** Identifies this app to Spotify (from the Spotify Developer Dashboard). Not a secret: the same for every user. */
    const val CLIENT_ID = "7e34a846ce014407b51feaaf4694be32"
    const val REDIRECT_URI = "com.example.hellowolds://spotify-callback"
    private const val SCOPES = "user-read-private user-read-email user-read-currently-playing user-library-read"

    fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val codeVerifier = ByteArray(32)
        secureRandom.nextBytes(codeVerifier)
        return Base64.encodeToString(
            codeVerifier,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes, 0, bytes.size)
        val digest = messageDigest.digest()
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun buildAuthUrl(clientId: String, codeChallenge: String): String {
        val encodedRedirectUri = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        val encodedScope = URLEncoder.encode(SCOPES, "UTF-8")
        val encodedChallenge = URLEncoder.encode(codeChallenge, "UTF-8")
        val encodedClientId = URLEncoder.encode(clientId, "UTF-8")

        return "https://accounts.spotify.com/authorize?" +
                "client_id=$encodedClientId" +
                "&response_type=code" +
                "&redirect_uri=$encodedRedirectUri" +
                "&code_challenge_method=S256" +
                "&code_challenge=$encodedChallenge" +
                "&scope=$encodedScope"
    }

    suspend fun exchangeCodeForToken(
        code: String,
        clientId: String,
        codeVerifier: String
    ): Result<SpotifyTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://accounts.spotify.com/api/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            val bodyParams = mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to clientId,
                "code_verifier" to codeVerifier
            )

            val postData = bodyParams.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val tokenResponse = SpotifyTokenResponse(
                    accessToken = json.getString("access_token"),
                    tokenType = json.getString("token_type"),
                    expiresIn = json.getInt("expires_in"),
                    refreshToken = if (json.has("refresh_token")) json.getString("refresh_token") else null,
                    scope = json.optString("scope", "")
                )
                Result.success(tokenResponse)
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "Error $responseCode"
                Result.failure(Exception("Failed to exchange code: $errorText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String
    ): Result<SpotifyTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://accounts.spotify.com/api/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            val bodyParams = mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to clientId
            )

            val postData = bodyParams.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val tokenResponse = SpotifyTokenResponse(
                    accessToken = json.getString("access_token"),
                    tokenType = json.getString("token_type"),
                    expiresIn = json.getInt("expires_in"),
                    refreshToken = json.optString("refresh_token", refreshToken),
                    scope = json.optString("scope", "")
                )
                Result.success(tokenResponse)
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "Error $responseCode"
                Result.failure(Exception("Failed to refresh token: $errorText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(accessToken: String): Result<SpotifyProfile> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.spotify.com/v1/me")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val id = json.optString("id", "")
                val displayName = json.optString("display_name", "Spotify User")
                val email = json.optString("email", "")
                val product = json.optString("product", "standard")
                val images = json.optJSONArray("images")
                val avatarUrl = if (images != null && images.length() > 0) {
                    images.getJSONObject(0).optString("url")
                } else null
                val followers = json.optJSONObject("followers")?.optInt("total", 0) ?: 0

                Result.success(
                    SpotifyProfile(
                        id = id,
                        displayName = displayName,
                        email = email,
                        product = product,
                        avatarUrl = avatarUrl,
                        followersCount = followers
                    )
                )
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "Error $responseCode"
                Result.failure(Exception("Failed to fetch user profile: $errorText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCurrentlyPlaying(accessToken: String): Result<CurrentlyPlaying?> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.spotify.com/v1/me/player/currently-playing")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")

            val responseCode = connection.responseCode
            if (responseCode == 204) {
                Result.success(null)
            } else if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().readText()
                if (responseText.isBlank()) {
                    Result.success(null)
                } else {
                    val json = JSONObject(responseText)
                    val isPlaying = json.optBoolean("is_playing", false)
                    val item = json.optJSONObject("item")
                    if (item != null) {
                        val title = item.optString("name", "Unknown Track")
                        val artistsArray = item.optJSONArray("artists")
                        val artistName = if (artistsArray != null && artistsArray.length() > 0) {
                            artistsArray.getJSONObject(0).optString("name", "Unknown Artist")
                        } else "Unknown Artist"

                        Result.success(CurrentlyPlaying(title = title, artist = artistName, isPlaying = isPlaying))
                    } else {
                        Result.success(null)
                    }
                }
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Spotify said "too many requests"; [retryAfterSec] is from the Retry-After header, if any. */
    class RateLimitedException(val retryAfterSec: Long?, message: String) : Exception(message)

    private class AlbumPage(val albums: List<SpotifyAlbum>, val total: Int, val next: String?)

    private const val SAVED_ALBUMS_URL = "https://api.spotify.com/v1/me/albums?limit=50"

    /** One page of saved albums (newest saved first). */
    private fun fetchAlbumPage(accessToken: String, pageUrl: String): AlbumPage {
        Log.d("SpotifyManager", "Fetching saved albums from: $pageUrl")
        val connection = URL(pageUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
            Log.e("SpotifyManager", "Error fetching saved albums ($responseCode): $errorText")
            if (responseCode == 429) {
                throw RateLimitedException(connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull(), errorText)
            }
            throw Exception("Failed to fetch saved albums ($responseCode): $errorText")
        }
        val json = JSONObject(connection.inputStream.bufferedReader().readText())
        val items = json.optJSONArray("items")
        val albums = if (items == null) emptyList() else List(items.length()) { i ->
            val itemObj = items.getJSONObject(i)
            parseAlbum(itemObj.optJSONObject("album") ?: itemObj)
        }
        val next = if (json.has("next") && !json.isNull("next")) json.getString("next") else null
        return AlbumPage(albums, json.optInt("total", albums.size), next)
    }

    private fun parseAlbum(albumObj: JSONObject): SpotifyAlbum {
        val artistsArray = albumObj.optJSONArray("artists")
        val artistName = if (artistsArray != null && artistsArray.length() > 0) {
            artistsArray.getJSONObject(0).optString("name", "Unknown Artist")
        } else "Unknown Artist"
        val imagesArray = albumObj.optJSONArray("images")
        val imageUrl = if (imagesArray != null && imagesArray.length() > 0) {
            imagesArray.getJSONObject(0).optString("url")
        } else null
        return SpotifyAlbum(
            id = albumObj.optString("id", ""),
            name = albumObj.optString("name", "Unknown Album"),
            artistName = artistName,
            imageUrl = imageUrl,
            totalTracks = albumObj.optInt("total_tracks", 0),
            releaseDate = albumObj.optString("release_date", "")
        )
    }

    /** The whole saved-albums library, page by page (about one request per 50 albums). */
    suspend fun fetchSavedAlbums(accessToken: String): Result<List<SpotifyAlbum>> = withContext(Dispatchers.IO) {
        try {
            val albumList = mutableListOf<SpotifyAlbum>()
            var nextUrl: String? = SAVED_ALBUMS_URL
            while (nextUrl != null) {
                val page = fetchAlbumPage(accessToken, nextUrl)
                albumList += page.albums
                nextUrl = page.next
            }
            Log.d("SpotifyManager", "Successfully fetched ${albumList.size} saved albums")
            Result.success(albumList)
        } catch (e: Exception) {
            Log.e("SpotifyManager", "Exception fetching saved albums", e)
            Result.failure(e)
        }
    }

    /**
     * Brings a cached library up to date with as few requests as possible. Saved albums come
     * newest first, so it reads pages from the top only until it reaches an album it already
     * knows (usually just 1 request), and adds the new ones in front. If the total then doesn't
     * match Spotify's (albums were removed), it falls back to a full download.
     */
    suspend fun syncSavedAlbums(accessToken: String, cached: List<SpotifyAlbum>?): Result<List<SpotifyAlbum>> {
        if (cached.isNullOrEmpty()) return fetchSavedAlbums(accessToken)
        return withContext(Dispatchers.IO) {
            try {
                val known = cached.mapTo(HashSet()) { it.id }
                val newAlbums = mutableListOf<SpotifyAlbum>()
                var nextUrl: String? = SAVED_ALBUMS_URL
                var total = -1
                var reachedKnown = false
                while (nextUrl != null && !reachedKnown) {
                    val page = fetchAlbumPage(accessToken, nextUrl)
                    total = page.total
                    for (album in page.albums) {
                        if (album.id in known) {
                            reachedKnown = true
                            break
                        }
                        newAlbums += album
                    }
                    nextUrl = page.next
                }
                val merged = newAlbums + cached
                if (merged.size == total) {
                    Log.d("SpotifyManager", "Library up to date: ${newAlbums.size} new, $total total")
                    Result.success(merged)
                } else {
                    Log.d("SpotifyManager", "Library changed (${merged.size} vs $total), refetching all")
                    fetchSavedAlbums(accessToken)
                }
            } catch (e: Exception) {
                Log.e("SpotifyManager", "Exception syncing saved albums", e)
                Result.failure(e)
            }
        }
    }
}
