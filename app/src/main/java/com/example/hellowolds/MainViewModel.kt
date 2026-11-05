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
import com.example.hellowolds.spotify.SpotifyManager
import com.example.hellowolds.spotify.SpotifyState
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("spotify_prefs", Context.MODE_PRIVATE)

    var spotifyState by mutableStateOf(SpotifyState())
        private set

    init {
        // Initialize Spotify state from saved preferences
        val savedClientId = prefs.getString("client_id", "") ?: ""
        spotifyState = spotifyState.copy(clientId = savedClientId)

        if (savedClientId.isNotEmpty() && hasStoredToken()) {
            checkAndFetchSpotifyData()
        }
    }

    fun setSpotifyClientId(newId: String) {
        prefs.edit().putString("client_id", newId.trim()).apply()
        spotifyState = spotifyState.copy(clientId = newId.trim(), error = null)
    }

    fun startSpotifyAuth() {
        val clientId = spotifyState.clientId.ifEmpty {
            prefs.getString("client_id", "") ?: ""
        }

        if (clientId.isBlank()) {
            spotifyState = spotifyState.copy(error = "Please enter your Spotify Client ID first.")
            return
        }

        val codeVerifier = SpotifyManager.generateCodeVerifier()
        val codeChallenge = SpotifyManager.generateCodeChallenge(codeVerifier)

        // Store verifier for token exchange
        prefs.edit().putString("code_verifier", codeVerifier).apply()

        val authUrl = SpotifyManager.buildAuthUrl(clientId, codeChallenge)
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
                val savedClientId = spotifyState.clientId.ifEmpty {
                    prefs.getString("client_id", "") ?: ""
                }
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
                    val tokenResult = SpotifyManager.exchangeCodeForToken(code, savedClientId, codeVerifier)

                    tokenResult.onSuccess { tokenResp ->
                        val expiresAt = System.currentTimeMillis() + (tokenResp.expiresIn * 1000L)
                        prefs.edit()
                            .putString("access_token", tokenResp.accessToken)
                            .putString("refresh_token", tokenResp.refreshToken)
                            .putLong("token_expires_at", expiresAt)
                            .apply()

                        fetchSpotifyProfile(tokenResp.accessToken)
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

    fun checkAndFetchSpotifyData() {
        val clientId = spotifyState.clientId.ifEmpty { prefs.getString("client_id", "") ?: "" }
        val accessToken = prefs.getString("access_token", null)
        val refreshToken = prefs.getString("refresh_token", null)
        val expiresAt = prefs.getLong("token_expires_at", 0L)

        if (accessToken == null) return

        viewModelScope.launch {
            if (System.currentTimeMillis() >= expiresAt && !refreshToken.isNullOrEmpty() && clientId.isNotEmpty()) {
                val refreshResult = SpotifyManager.refreshAccessToken(refreshToken, clientId)
                refreshResult.onSuccess { tokenResp ->
                    val newExpiresAt = System.currentTimeMillis() + (tokenResp.expiresIn * 1000L)
                    val editor = prefs.edit()
                        .putString("access_token", tokenResp.accessToken)
                        .putLong("token_expires_at", newExpiresAt)
                    if (tokenResp.refreshToken != null) {
                        editor.putString("refresh_token", tokenResp.refreshToken)
                    }
                    editor.apply()
                    fetchSpotifyProfile(tokenResp.accessToken)
                }.onFailure {
                    disconnectSpotify()
                }
            } else {
                fetchSpotifyProfile(accessToken)
            }
        }
    }

    private suspend fun fetchSpotifyProfile(token: String) {
        spotifyState = spotifyState.copy(isLoading = true)
        val profileResult = SpotifyManager.fetchUserProfile(token)
        val playingResult = SpotifyManager.fetchCurrentlyPlaying(token)
        val albumsResult = SpotifyManager.fetchSavedAlbums(token)

        profileResult.onSuccess { profile ->
            val albumError = albumsResult.exceptionOrNull()?.message
            val errorMsg = if (albumError != null) {
                "Saved albums error: $albumError. Please Disconnect and reconnect to authorize library access."
            } else null

            spotifyState = spotifyState.copy(
                isLoggedIn = true,
                isLoading = false,
                profile = profile,
                currentlyPlaying = playingResult.getOrNull(),
                albums = albumsResult.getOrDefault(emptyList()),
                error = errorMsg
            )
        }.onFailure { ex ->
            spotifyState = spotifyState.copy(
                isLoading = false,
                error = "Failed to load profile: ${ex.message}"
            )
        }
    }

    fun disconnectSpotify() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("token_expires_at")
            .remove("code_verifier")
            .apply()

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
