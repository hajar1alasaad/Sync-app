package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRow(
    @SerialName("id") val id: String = "",
    @SerialName("email") val email: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class WalletRow(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String = "",
    @SerialName("balance") val balance: Double = 0.0,
    @SerialName("currency") val currency: String? = "USD",
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PostRow(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String = "",
    @SerialName("content") val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("likes_count") val likesCount: Int = 0
)

@Serializable
data class FollowRow(
    @SerialName("follower_id") val followerId: String = "",
    @SerialName("followed_id") val followedId: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TransactionRow(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String = "",
    @SerialName("amount") val amount: Double = 0.0,
    @SerialName("type") val type: String = "deposit", // "deposit", "withdrawal", "transfer"
    @SerialName("description") val description: String = "",
    @SerialName("created_at") val createdAt: String? = null
)
