package com.example.hellowolds

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.hellowolds.home.ConnectScreen
import com.example.hellowolds.home.HomeScreen
import com.example.hellowolds.ui.theme.HelloWoldsTheme
import com.example.hellowolds.widget.AlbumOfDayWidget
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.handleSpotifyRedirect(intent)
        mainViewModel.openAlbumFromWidget(intent?.getStringExtra(AlbumOfDayWidget.EXTRA_ALBUM_ID))
        enableEdgeToEdge()
        setContent {
            HelloWoldsTheme {
                val state = mainViewModel.spotifyState
                // Home appears only after the loading screen has erased its title, so the two
                // screens hand off smoothly (home then types its title in and fades in).
                var homeShown by rememberSaveable { mutableStateOf(false) }
                var homeIntro by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(state.isLoggedIn) { if (!state.isLoggedIn) homeShown = false }
                LaunchedEffect(homeShown) {
                    // Don't replay the intro if the activity is recreated later.
                    if (homeShown) {
                        delay(5_000)
                        homeIntro = false
                    }
                }
                if (state.isLoggedIn && homeShown) {
                    HomeScreen(
                        albums = state.albums,
                        isLoading = state.isLoading,
                        intro = homeIntro,
                        openAlbumId = mainViewModel.pendingAlbumId,
                        onAlbumOpened = mainViewModel::clearPendingAlbum
                    )
                } else {
                    ConnectScreen(
                        isLoading = state.isLoading || state.isLoggedIn,
                        error = state.error,
                        authUrlToLoad = state.authUrlToLoad,
                        onConnect = mainViewModel::startSpotifyAuth,
                        onAuthUrlOpened = mainViewModel::cancelSpotifyAuth,
                        finishing = state.isLoggedIn,
                        onFinished = {
                            homeIntro = true
                            homeShown = true
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModel.handleSpotifyRedirect(intent)
        mainViewModel.openAlbumFromWidget(intent.getStringExtra(AlbumOfDayWidget.EXTRA_ALBUM_ID))
    }
}
