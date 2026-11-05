package com.example.hellowolds.spotify

data class SpotifyProfile(
    val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val product: String = "",
    val avatarUrl: String? = null,
    val followersCount: Int = 0
)

data class CurrentlyPlaying(
    val title: String,
    val artist: String,
    val isPlaying: Boolean
)

data class SpotifyAlbum(
    val id: String,
    val name: String,
    val artistName: String,
    val imageUrl: String?,
    val totalTracks: Int,
    val releaseDate: String
)

data class SpotifyTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Int,
    val refreshToken: String?,
    val scope: String
)

data class SpotifyState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val profile: SpotifyProfile? = null,
    val currentlyPlaying: CurrentlyPlaying? = null,
    val albums: List<SpotifyAlbum> = emptyList(),
    val error: String? = null,
    val clientId: String = "",
    val authUrlToLoad: String? = null
)
