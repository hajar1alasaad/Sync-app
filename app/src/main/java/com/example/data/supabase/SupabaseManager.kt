package com.example.data.supabase

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ProfileRow
import com.example.data.model.WalletRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object SupabaseManager {
    private const val TAG = "SupabaseManager"

    private val configuredUrl: String = try {
        BuildConfig.SUPABASE_URL.ifBlank { "https://placeholder.supabase.co" }
    } catch (_: Throwable) {
        "https://placeholder.supabase.co"
    }

    private val configuredKey: String = try {
        BuildConfig.SUPABASE_ANON_KEY.ifBlank { "placeholder-anon-key" }
    } catch (_: Throwable) {
        "placeholder-anon-key"
    }

    val client: SupabaseClient by lazy {
        val safeUrl = if (configuredUrl.startsWith("http://") || configuredUrl.startsWith("https://")) {
            configuredUrl
        } else {
            "https://$configuredUrl"
        }

        createSupabaseClient(
            supabaseUrl = safeUrl,
            supabaseKey = configuredKey
        ) {
            httpEngine = OkHttp.create()
            install(Auth)
            install(Postgrest)
        }
    }

    val auth get() = client.auth
    val postgrest get() = client.postgrest

    /**
     * Automatic User & Wallet Sync:
     * Checks if the user exists in profiles table. If not, inserts a new profile row.
     * Automatically initializes a corresponding row in the wallets table with a balance of 0.0.
     */
    suspend fun syncUserAndWallet(
        fallbackName: String? = null,
        fallbackAvatar: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUserOrNull() ?: return@runCatching
            val userId = user.id
            Log.d(TAG, "Starting sync for authenticated user: $userId")

            // 1. Check or insert profile
            val existingProfiles = runCatching {
                client.from("profiles")
                    .select {
                        filter {
                            eq("id", userId)
                        }
                    }
                    .decodeList<ProfileRow>()
            }.getOrElse {
                Log.w(TAG, "Failed to query profiles table: ${it.message}")
                emptyList()
            }

            if (existingProfiles.isEmpty()) {
                val email = user.email ?: ""
                val metadata = user.userMetadata
                val fullName = metadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                    ?: metadata?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: fallbackName
                    ?: email.substringBefore("@").ifBlank { "Sync Member" }
                val avatarUrl = metadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                    ?: metadata?.get("picture")?.jsonPrimitive?.contentOrNull
                    ?: fallbackAvatar

                val newProfile = ProfileRow(
                    id = userId,
                    email = email,
                    fullName = fullName,
                    avatarUrl = avatarUrl
                )

                runCatching {
                    client.from("profiles").insert(newProfile)
                    Log.d(TAG, "Inserted new profile for user: $userId")
                }.onFailure { err ->
                    Log.w(TAG, "Error inserting profile: ${err.message}")
                }
            }

            // 2. Check or initialize wallet with balance 0.0
            val existingWallets = runCatching {
                client.from("wallets")
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<WalletRow>()
            }.getOrElse {
                Log.w(TAG, "Failed to query wallets table: ${it.message}")
                emptyList()
            }

            if (existingWallets.isEmpty()) {
                val newWallet = WalletRow(
                    userId = userId,
                    balance = 0.0,
                    currency = "USD"
                )
                runCatching {
                    client.from("wallets").insert(newWallet)
                    Log.d(TAG, "Initialized new wallet for user: $userId with balance 0.0")
                }.onFailure { err ->
                    Log.w(TAG, "Error inserting wallet: ${err.message}")
                }
            }
        }
    }
}
