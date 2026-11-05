package com.example.hellowolds

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hellowolds.spotify.SpotifyCard
import com.example.hellowolds.ui.theme.HelloWoldsTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.handleSpotifyRedirect(intent)
        enableEdgeToEdge()
        setContent {
            HelloWoldsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpotifyAppBackground(modifier = Modifier.padding(innerPadding)) {
                        SpotifyHomeScreen(viewModel = mainViewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModel.handleSpotifyRedirect(intent)
    }
}

@Composable
fun SpotifyAppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val brush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF191414),
            Color(0xFF121212)
        )
    )

    Box(modifier = modifier.fillMaxSize().background(brush)) {
        content()
    }
}

@Composable
fun SpotifyHomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Main Spotify Card
        SpotifyCard(viewModel = viewModel)
    }
}
