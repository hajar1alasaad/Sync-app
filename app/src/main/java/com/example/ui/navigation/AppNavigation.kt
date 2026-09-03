package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.ui.auth.AuthState
import com.example.ui.auth.AuthViewModel
import com.example.ui.profile.ProfileViewModel
import com.example.ui.wallet.WalletViewModel

@Composable
fun AuthenticatedApp(
    authViewModel: AuthViewModel,
    authenticatedState: AuthState.Authenticated,
    navController: NavHostController = rememberNavController(),
    profileViewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    walletViewModel: WalletViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    NavGraph(
        authViewModel = authViewModel,
        authenticatedState = authenticatedState,
        navController = navController,
        profileViewModel = profileViewModel,
        walletViewModel = walletViewModel
    )
}
