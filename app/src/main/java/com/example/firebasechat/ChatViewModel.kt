package com.example.firebasechat

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application = application
    private val repository = ChatRepository()

    val uid = mutableStateOf<String?>(null)
    val messages = mutableStateListOf<ChatMessage>()
    val error = mutableStateOf<String?>(null)
    val uploading = mutableStateOf(false)

    private var listener: ValueEventListener? = null
    private val notifiedKeys = mutableSetOf<String>()
    private var initialMessagesLoaded = false

    init {
        signIn()
    }

    private fun signIn() {
        repository.signInAnon { result ->
            viewModelScope.launch {
                if (result == null) {
                    error.value = "Giriş başarısız. Firebase ayarlarını kontrol edin."
                } else {
                    uid.value = result
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        repository.saveToken(result, token)
                    }
                    listener = repository.listen { updated ->
                        if (initialMessagesLoaded) {
                            updated
                                .filter { it.key !in notifiedKeys && it.uid != uid.value }
                                .forEach { msg ->
                                    val body = if (msg.imageBase64.isNotBlank()) "📷 Fotoğraf" else msg.text
                                    showLocalNotification(msg.name.ifBlank { "Yeni mesaj" }, body)
                                }
                        }
                        notifiedKeys.clear()
                        notifiedKeys.addAll(updated.map { it.key })
                        initialMessagesLoaded = true
                        messages.clear()
                        messages.addAll(updated)
                    }
                }
            }
        }
    }

    fun send(name: String, text: String) {
        val currentUid = uid.value ?: return
        if (text.isBlank()) return
        repository.send(currentUid, name, text)
    }

    fun sendPhoto(name: String, uri: Uri) {
        val currentUid = uid.value ?: return
        uploading.value = true
        viewModelScope.launch {
            val base64 = withContext(Dispatchers.IO) { compressUri(app, uri) }
            uploading.value = false
            if (base64 == null) {
                error.value = "Fotoğraf işlenemedi."
            } else {
                repository.send(currentUid, name, "📷 Fotoğraf", base64)
            }
        }
    }

    private fun showLocalNotification(title: String, body: String) {
        val channelId = "chat_messages"
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Sohbet Mesajları", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onCleared() {
        super.onCleared()
        listener?.let { repository.removeListener(it) }
    }

    private fun compressUri(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()
            if (bitmap == null) return null
            val maxSize = 512
            val scale = minOf(
                maxSize.toFloat() / bitmap.width,
                maxSize.toFloat() / bitmap.height,
                1f
            )
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}
