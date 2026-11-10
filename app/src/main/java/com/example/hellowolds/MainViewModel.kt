package com.example.hellowolds

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hellowolds.spotify.LibraryCache
import com.example.hellowolds.spotify.MockSpotify
import com.example.hellowolds.spotify.SpotifyManager
import com.example.hellowolds.spotify.SpotifyAlbum
import com.example.hellowolds.spotify.SpotifyState
import com.example.hellowolds.widget.AlbumOfDayWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** Refresh the saved library from Spotify at most this often; otherwise use the copy on disk. */
private const val LIBRARY_REFRESH_MS = 6 * 60 * 60 * 1000L
/** How long to stay off Spotify after a 429 that doesn't say how long (Retry-After missing). */
private const val DEFAULT_BACKOFF_MS = 60 * 60 * 1000L

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("spotify_prefs", Context.MODE_PRIVATE)
    private val libraryCache = LibraryCache(application)

    var spotifyState by mutableStateOf(SpotifyState())
        private set

    /** Album to open when the app was launched from the "Disco del día" widget. */
    var pendingAlbumId by mutableStateOf<String?>(null)
        private set

    fun openAlbumFromWidget(albumId: String?) {
        if (albumId != null) pendingAlbumId = albumId
    }

    fun clearPendingAlbum() {
        pendingAlbumId = null
    }

    init {
        if (BuildConfig.MOCK_SPOTIFY) {
            loadMockLibrary()
        } else if (hasStoredToken()) {
            checkAndFetchSpotifyData()
        }
    }

    /** Debug mock mode: a fake logged-in user and library, without any Spotify request. */
    private fun loadMockLibrary() {
        spotifyState = spotifyState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            delay(MockSpotify.LOAD_DELAY_MS)
            val albums = MockSpotify.savedAlbums(getApplication())
            spotifyState = spotifyState.copy(
                isLoggedIn = true,
                isLoading = false,
                profile = MockSpotify.profile,
                albums = albums,
                error = null
            )
            AlbumOfDayWidget.requestUpdate(getApplication())
        }
    }

    fun startSpotifyAuth() {
        val codeVerifier = SpotifyManager.generateCodeVerifier()
        val codeChallenge = SpotifyManager.generateCodeChallenge(codeVerifier)

        // Store verifier for token exchange
        prefs.edit().putString("code_verifier", codeVerifier).apply()

        val authUrl = SpotifyManager.buildAuthUrl(SpotifyManager.CLIENT_ID, codeChallenge)
        spotifyState = spotifyState.copy(
            authUrlToLoad = authUrl,
            isLoading = false,
            error = null
        )
    }

    fun cancelSpotifyAuth() {
        spotifyState = spotifyState.copy(authUrlToLoad = null, isLoading = false)
    }

    fun handleSpotifyRedirectUri(uri: Uri) {
        spotifyState = spotifyState.copy(authUrlToLoad = null)

        if (uri.scheme == "com.example.hellowolds" && uri.host == "spotify-callback") {
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")

            if (error != null) {
                spotifyState = spotifyState.copy(
                    isLoading = false,
                    error = "Spotify authentication failed: $error"
                )
                return
            }

            if (code != null) {
                val codeVerifier = prefs.getString("code_verifier", null)

                if (codeVerifier == null) {
                    spotifyState = spotifyState.copy(
                        isLoading = false,
                        error = "Session expired. Please try connecting again."
                    )
                    return
                }

                viewModelScope.launch {
                    spotifyState = spotifyState.copy(isLoading = true, error = null)
                    val tokenResult = SpotifyManager.exchangeCodeForToken(code, SpotifyManager.CLIENT_ID, codeVerifier)

                    tokenResult.onSuccess { tokenResp ->
                        val expiresAt = System.currentTimeMillis() + (tokenResp.expiresIn * 1000L)
                        prefs.edit()
                            .putString("access_token", tokenResp.accessToken)
                            .putString("refresh_token", tokenResp.refreshToken)
                            .putLong("token_expires_at", expiresAt)
                            .apply()

                        // A fresh login may be a different account: don't trust a saved library.
                        libraryCache.clear()
                        syncLibrary(tokenResp.accessToken, cached = null)
                    }.onFailure { ex ->
                        spotifyState = spotifyState.copy(
                            isLoading = false,
                            error = ex.message ?: "Failed to authorize Spotify"
                        )
                    }
                }
            }
        }
    }

    fun handleSpotifyRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        handleSpotifyRedirectUri(uri)
    }

    private fun hasStoredToken(): Boolean {
        return !prefs.getString("access_token", null).isNullOrEmpty()
    }

    /**
     * Startup with a saved login: show the library saved on the phone right away, and only ask
     * Spotify for changes when that copy is older than [LIBRARY_REFRESH_MS] (usually 1 request).
     * Without a saved copy it downloads the whole library, behind the loading screen.
     */
    fun checkAndFetchSpotifyData() {
        if (prefs.getString("access_token", null) == null) return
        viewModelScope.launch {
            val cached = libraryCache.load()
            if (cached != null) {
                spotifyState = spotifyState.copy(isLoggedIn = true, isLoading = false, albums = cached.albums, error = null)
                if (System.currentTimeMillis() - cached.savedAt < LIBRARY_REFRESH_MS) return@launch
            } else {
                // Show "loading" right away, so the connect button doesn't flash while the token refreshes.
                spotifyState = spotifyState.copy(isLoading = true, error = null)
            }
            val token = validAccessToken() ?: return@launch
            syncLibrary(token, cached?.albums)
        }
    }

    /** The stored access token, refreshed first if it has expired; null (and logged out) if that fails. */
    private suspend fun validAccessToken(): String? {
        val accessToken = prefs.getString("access_token", null) ?: return null
        val refreshToken = prefs.getString("refresh_token", null)
        val expiresAt = prefs.getLong("token_expires_at", 0L)
        if (System.currentTimeMillis() < expiresAt || refreshToken.isNullOrEmpty()) return accessToken

        val result = SpotifyManager.refreshAccessToken(refreshToken, SpotifyManager.CLIENT_ID)
        val tokenResp = result.getOrNull()
        if (tokenResp == null) {
            disconnectSpotify()
            return null
        }
        val editor = prefs.edit()
            .putString("access_token", tokenResp.accessToken)
            .putLong("token_expires_at", System.currentTimeMillis() + tokenResp.expiresIn * 1000L)
        if (tokenResp.refreshToken != null) editor.putString("refresh_token", tokenResp.refreshToken)
        editor.apply()
        return tokenResp.accessToken
    }

    /**
     * Brings the library up to date ([cached] = what's already on screen, or null to download it
     * all). Respects Spotify's back-off: after a 429 no request is made until the wait is over,
     * and a saved library keeps showing in the meantime.
     */
    private suspend fun syncLibrary(token: String, cached: List<SpotifyAlbum>?) {
        val blockedUntil = prefs.getLong("spotify_blocked_until", 0L)
        if (System.currentTimeMillis() < blockedUntil) {
            if (cached == null) spotifyState = spotifyState.copy(isLoading = false, error = waitMessage(blockedUntil))
            return
        }
        if (cached == null) spotifyState = spotifyState.copy(isLoading = true, error = null)

        SpotifyManager.syncSavedAlbums(token, cached)
            .onSuccess { albums ->
                libraryCache.save(albums)
                AlbumOfDayWidget.requestUpdate(getApplication())
                spotifyState = spotifyState.copy(isLoggedIn = true, isLoading = false, albums = albums, error = null)
            }
            .onFailure { ex ->
                var error = "No pude cargar tu biblioteca: ${ex.message}"
                if (ex is SpotifyManager.RateLimitedException) {
                    val waitMs = ex.retryAfterSec?.times(1000L) ?: DEFAULT_BACKOFF_MS
                    val until = System.currentTimeMillis() + waitMs
                    prefs.edit().putLong("spotify_blocked_until", until).apply()
                    error = waitMessage(until)
                }
                // With a saved library on screen, keep it and stay quiet; otherwise explain.
                if (cached == null) spotifyState = spotifyState.copy(isLoading = false, error = error)
            }
    }

    private fun waitMessage(until: Long): String {
        val minutes = ceil((until - System.currentTimeMillis()) / 60_000.0).toLong().coerceAtLeast(1)
        return "Spotify nos pidió que esperemos un rato. Probá de nuevo en $minutes min."
    }

    fun disconnectSpotify() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("token_expires_at")
            .remove("code_verifier")
            .apply()
        libraryCache.clear()

        spotifyState = spotifyState.copy(
            isLoggedIn = false,
            isLoading = false,
            profile = null,
            currentlyPlaying = null,
            albums = emptyList(),
            error = null
        )
    }
}
