package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.auth.AuthState
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.LoginScreen
import com.example.ui.navigation.AuthenticatedApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0F19)
                ) {
                    val authState by authViewModel.authState.collectAsState()

                    // Strict Mandatory Authentication Guard (Gating)
                    Crossfade(
                        targetState = authState,
                        label = "auth_guard_transition"
                    ) { state ->
                        when (state) {
                            is AuthState.Initializing -> {
                                AuthInitializingScreen()
                            }
                            is AuthState.Unauthenticated -> {
                                // Locked navigation: Unauthenticated users are strictly kept on LoginScreen
                                LoginScreen(authViewModel = authViewModel)
                            }
                            is AuthState.Authenticated -> {
                                // Authenticated state unlocks Home, Radar, Messages, Explore, Profile, Wallet
                                AuthenticatedApp(
                                    authViewModel = authViewModel,
                                    authenticatedState = state
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthInitializingScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0F1D), Color(0xFF0F172A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF06B6D4))
                        )
                    )
                    .border(2.dp, Color(0xFF818CF8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            CircularProgressIndicator(
                color = Color(0xFF6366F1),
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Securing Sync Session...",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Verifying Supabase authentication & credentials",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }
    }
}
