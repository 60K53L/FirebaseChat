package com.example.firebasechat

data class ChatMessage(
    val key: String = "",
    val uid: String = "",
    val name: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val imageBase64: String = "",
    val ts: Long = 0L
)
