package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.auth.AuthState
import com.example.ui.auth.AuthViewModel
import com.example.ui.profile.ProfileScreen
import com.example.ui.profile.ProfileViewModel
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.wallet.WalletScreen
import com.example.ui.wallet.WalletViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Radar : Screen("radar", "Radar", Icons.Default.Radar)
    data object Explore : Screen("explore", "Explore", Icons.Default.Explore)
    data object Messages : Screen("messages", "Messages", Icons.Default.ChatBubble)
    data object Profile : Screen("profile", "Profile", Icons.Default.Person)
    data object Wallet : Screen("wallet", "Wallet", Icons.Default.AccountBalanceWallet)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Radar,
    Screen.Explore,
    Screen.Messages,
    Screen.Profile
)

@Composable
fun AuthenticatedApp(
    authViewModel: AuthViewModel,
    authenticatedState: AuthState.Authenticated,
    navController: NavHostController = rememberNavController(),
    profileViewModel: ProfileViewModel = viewModel(),
    walletViewModel: WalletViewModel = viewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom bar for main 5 tabs, hide on sub-screen Wallet
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        containerColor = Color(0xFF0B0F19),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color(0xFF111827),
                    contentColor = Color(0xFF94A3B8),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .border(
                            width = 1.dp,
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        )
                        .testTag("bottom_nav_bar")
                ) {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.label,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.label,
                                    fontSize = 11.sp
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color(0xFF818CF8),
                                unselectedIconColor = Color(0xFF64748B),
                                unselectedTextColor = Color(0xFF64748B),
                                indicatorColor = Color(0xFF4F46E5)
                            ),
                            modifier = Modifier.testTag("nav_item_${screen.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen()
            }
            composable(Screen.Radar.route) {
                RadarScreen()
            }
            composable(Screen.Explore.route) {
                ExploreScreen()
            }
            composable(Screen.Messages.route) {
                MessagesScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    profileViewModel = profileViewModel,
                    onNavigateToWallet = {
                        navController.navigate(Screen.Wallet.route)
                    },
                    onSignOut = {
                        authViewModel.signOut()
                    }
                )
            }
            composable(Screen.Wallet.route) {
                WalletScreen(
                    walletViewModel = walletViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
