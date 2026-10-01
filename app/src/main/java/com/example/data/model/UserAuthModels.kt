package com.example.data.model

enum class UploadMode {
    BOT,
    USER_ACCOUNT;

    val title: String
        get() = when (this) {
            BOT -> "Bot do Telegram"
            USER_ACCOUNT -> "Conta de Usuário (Telethon / MTProto)"
        }
}

data class UserSessionInfo(
    val isValid: Boolean = false,
    val phoneNumber: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val userId: Long = 0L,
    val sessionString: String = "",
    val dcId: Int = 2,
    val lastVerified: Long = 0L
) {
    val displayName: String
        get() = when {
            firstName.isNotBlank() && username.isNotBlank() -> "$firstName (@$username)"
            username.isNotBlank() -> "@$username"
            firstName.isNotBlank() -> firstName
            phoneNumber.isNotBlank() -> phoneNumber
            else -> "Conta do Telegram"
        }
}

sealed class AuthStep {
    object Idle : AuthStep()
    object Loading : AuthStep()
    data class CodeSent(val phone: String, val phoneCodeHash: String) : AuthStep()
    data class TwoFactorRequired(val phone: String, val phoneCodeHash: String, val hint: String = "") : AuthStep()
    data class Authenticated(val session: UserSessionInfo) : AuthStep()
    data class Error(val message: String) : AuthStep()
}
