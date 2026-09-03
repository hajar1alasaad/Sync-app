package com.example.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FollowRow
import com.example.data.model.PostRow
import com.example.data.model.ProfileRow
import com.example.data.model.WalletRow
import com.example.data.supabase.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Success(
        val profile: ProfileRow,
        val postsCount: Int = 0,
        val followersCount: Int = 0,
        val followingCount: Int = 0,
        val balance: Double = 0.0,
        val posts: List<PostRow> = emptyList(),
        val isRefreshing: Boolean = false
    ) : ProfileUiState
    data object Empty : ProfileUiState
    data class Error(val message: String, val fallbackData: Success? = null) : ProfileUiState
}

class ProfileViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile(explicitUserId: String? = null) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            fetchDataInternal(explicitUserId)
        }
    }

    fun refreshProfile(explicitUserId: String? = null) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is ProfileUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            }
            fetchDataInternal(explicitUserId)
        }
    }

    private suspend fun fetchDataInternal(explicitUserId: String?) = withContext(Dispatchers.IO) {
        try {
            val currentUser = SupabaseManager.auth.currentUserOrNull()
            val userId = explicitUserId ?: currentUser?.id

            if (userId.isNullOrBlank()) {
                // If user is missing from session, fallback to safe default rather than crashing
                _uiState.value = ProfileUiState.Success(
                    profile = ProfileRow(
                        id = "unknown",
                        fullName = "Sync User",
                        email = "user@sync.app"
                    ),
                    postsCount = 0,
                    followersCount = 0,
                    followingCount = 0,
                    balance = 0.0,
                    posts = emptyList()
                )
                return@withContext
            }

            // 1. Fetch Profile
            val profile = runCatching {
                val list = SupabaseManager.client.from("profiles")
                    .select {
                        filter { eq("id", userId) }
                    }
                    .decodeList<ProfileRow>()
                list.firstOrNull()
            }.getOrNull() ?: ProfileRow(
                id = userId,
                email = currentUser?.email ?: "",
                fullName = currentUser?.email?.substringBefore("@")?.ifBlank { "Sync Member" } ?: "Sync Member",
                avatarUrl = null
            )

            // 2. Dynamic Query: postsCount (total rows where user_id = currentUserId in posts)
            val userPosts = runCatching {
                SupabaseManager.client.from("posts")
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<PostRow>()
            }.getOrElse { err ->
                Log.w(TAG, "Querying posts table failed safely: ${err.message}")
                emptyList()
            }
            val postsCount = userPosts.size

            // 3. Dynamic Query: followersCount (total rows where followed_id = currentUserId in follows)
            val followersCount = runCatching {
                val list = SupabaseManager.client.from("follows")
                    .select {
                        filter { eq("followed_id", userId) }
                    }
                    .decodeList<FollowRow>()
                list.size
            }.getOrElse { err ->
                Log.w(TAG, "Querying followers failed safely: ${err.message}")
                0
            }

            // 4. Dynamic Query: followingCount (total rows where follower_id = currentUserId in follows)
            val followingCount = runCatching {
                val list = SupabaseManager.client.from("follows")
                    .select {
                        filter { eq("follower_id", userId) }
                    }
                    .decodeList<FollowRow>()
                list.size
            }.getOrElse { err ->
                Log.w(TAG, "Querying following failed safely: ${err.message}")
                0
            }

            // 5. Dynamic Query: balance (user's real-time balance from wallets table)
            val balance = runCatching {
                val wallets = SupabaseManager.client.from("wallets")
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<WalletRow>()
                wallets.firstOrNull()?.balance ?: 0.0
            }.getOrElse { err ->
                Log.w(TAG, "Querying wallet balance failed safely: ${err.message}")
                0.0
            }

            _uiState.value = ProfileUiState.Success(
                profile = profile,
                postsCount = postsCount,
                followersCount = followersCount,
                followingCount = followingCount,
                balance = balance,
                posts = userPosts,
                isRefreshing = false
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error fetching profile safely: ${e.message}", e)
            // Absolute crash prevention: provide zero fallback
            _uiState.value = ProfileUiState.Success(
                profile = ProfileRow(
                    id = explicitUserId ?: "guest",
                    fullName = "Sync Member",
                    email = null
                ),
                postsCount = 0,
                followersCount = 0,
                followingCount = 0,
                balance = 0.0,
                posts = emptyList(),
                isRefreshing = false
            )
        }
    }

    fun createPost(content: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = SupabaseManager.auth.currentUserOrNull() ?: return@launch
            runCatching {
                val newPost = PostRow(
                    userId = user.id,
                    content = content,
                    authorName = user.email?.substringBefore("@") ?: "Sync User"
                )
                SupabaseManager.client.from("posts").insert(newPost)
            }.onSuccess {
                refreshProfile(user.id)
                withContext(Dispatchers.Main) { onComplete() }
            }.onFailure { err ->
                Log.w(TAG, "Failed to insert post: ${err.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ProfileViewModel"
    }
}
