package com.example.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.supabase.SupabaseManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed interface AuthState {
    data object Initializing : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(
        val userId: String,
        val email: String,
        val displayName: String,
        val avatarUrl: String? = null
    ) : AuthState
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    init {
        observeSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            try {
                SupabaseManager.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val user = SupabaseManager.auth.currentUserOrNull()
                            if (user != null) {
                                val meta = user.userMetadata
                                val name = meta?.get("full_name")?.jsonPrimitive?.contentOrNull
                                    ?: meta?.get("name")?.jsonPrimitive?.contentOrNull
                                    ?: user.email?.substringBefore("@")
                                    ?: "Sync User"
                                val avatar = meta?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                                    ?: meta?.get("picture")?.jsonPrimitive?.contentOrNull

                                _authState.value = AuthState.Authenticated(
                                    userId = user.id,
                                    email = user.email ?: "",
                                    displayName = name,
                                    avatarUrl = avatar
                                )
                                // Trigger automatic User & Wallet sync
                                SupabaseManager.syncUserAndWallet()
                            } else {
                                _authState.value = AuthState.Unauthenticated
                            }
                        }
                        is SessionStatus.NotAuthenticated,
                        is SessionStatus.RefreshFailure -> {
                            _authState.value = AuthState.Unauthenticated
                        }
                        is SessionStatus.Initializing -> {
                            _authState.value = AuthState.Initializing
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error collecting Supabase session status: ${e.message}")
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _loginUiState.value = LoginUiState(isLoading = true)

            val serverClientId = try {
                BuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank { DEFAULT_SERVER_CLIENT_ID }
            } catch (_: Throwable) {
                DEFAULT_SERVER_CLIENT_ID
            }

            try {
                val credentialManager = CredentialManager.create(context)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(serverClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )

                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    // Authenticate with Supabase via supabase.auth.signInWith(IDToken)
                    SupabaseManager.auth.signInWith(IDToken) {
                        this.idToken = idToken
                        provider = Google
                    }

                    // Automatic User & Wallet Sync
                    SupabaseManager.syncUserAndWallet(
                        fallbackName = googleIdTokenCredential.displayName,
                        fallbackAvatar = googleIdTokenCredential.profilePictureUri?.toString()
                    )

                    _loginUiState.value = LoginUiState(isLoading = false)
                } else {
                    _loginUiState.value = LoginUiState(
                        isLoading = false,
                        errorMessage = "Unsupported credential format received."
                    )
                }
            } catch (e: GetCredentialCancellationException) {
                Log.i(TAG, "Google sign-in cancelled by user")
                _loginUiState.value = LoginUiState(
                    isLoading = false,
                    infoMessage = "Sign-in was cancelled."
                )
            } catch (e: NoCredentialException) {
                Log.w(TAG, "No Google credentials available on device: ${e.message}")
                _loginUiState.value = LoginUiState(
                    isLoading = false,
                    errorMessage = "No Google account found on this device. Please add an account or use Demo Sign-In."
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Google Credential Manager error: ${e.message}", e)
                _loginUiState.value = LoginUiState(
                    isLoading = false,
                    errorMessage = "Authentication failed: ${e.localizedMessage ?: "Unknown error"}. You can also use Demo Sign-In."
                )
            }
        }
    }

    /**
     * Fallback authenticated entry for emulator development environments
     * where Google Play Services or Client ID might not be configured.
     */
    fun signInWithDemo(demoEmail: String = "hajarsync@gmail.com", demoName: String = "Hajar Alasaad") {
        viewModelScope.launch {
            _loginUiState.value = LoginUiState(isLoading = true)
            try {
                // Generate or use deterministic user ID
                val userId = "sync-user-" + demoEmail.hashCode().toString().removePrefix("-")
                _authState.value = AuthState.Authenticated(
                    userId = userId,
                    email = demoEmail,
                    displayName = demoName,
                    avatarUrl = null
                )

                // Sync profile & wallet in Supabase
                runCatching {
                    SupabaseManager.syncUserAndWallet(
                        fallbackName = demoName,
                        fallbackAvatar = null
                    )
                }

                _loginUiState.value = LoginUiState(isLoading = false)
            } catch (e: Throwable) {
                Log.e(TAG, "Demo sign in error: ${e.message}")
                _loginUiState.value = LoginUiState(
                    isLoading = false,
                    errorMessage = "Sign-in error: ${e.message}"
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching {
                SupabaseManager.auth.signOut()
            }
            _authState.value = AuthState.Unauthenticated
            _loginUiState.value = LoginUiState()
        }
    }

    fun clearMessages() {
        _loginUiState.value = _loginUiState.value.copy(errorMessage = null, infoMessage = null)
    }

    companion object {
        private const val TAG = "AuthViewModel"
        private const val DEFAULT_SERVER_CLIENT_ID = "placeholder-web-client-id.apps.googleusercontent.com"
    }
}
