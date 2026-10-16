package com.example.firebasechat

import android.Manifest
import android.content.Context
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FirebaseChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApp() {
    val viewModel: ChatViewModel = viewModel()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE) }

    var nickname by remember { mutableStateOf(prefs.getString("nickname", "") ?: "") }
    var showNameDialog by remember { mutableStateOf(nickname.isBlank()) }
    var draft by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var updateVersion by remember { mutableStateOf(0) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.uid.value) {
        val uid = viewModel.uid.value ?: return@LaunchedEffect
        FirebaseDatabase.getInstance().getReference("appUpdate")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val remoteVersion = (snapshot.child("versionCode").getValue(Long::class.java)
                        ?: snapshot.child("versionCode").getValue(Int::class.java)?.toLong()
                        ?: 0L).toInt()
                    val url = snapshot.child("url").getValue(String::class.java)
                    if (url.isNullOrBlank()) return
                    val pm = context.packageManager
                    val info = pm.getPackageInfo(context.packageName, 0)
                    val currentVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        info.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        info.versionCode
                    }
                    if (remoteVersion > currentVersion) {
                        updateUrl = url
                        updateVersion = remoteVersion
                        showUpdateDialog = true
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                }
            })
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.sendPhoto(nickname, uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showNameDialog) {
        var input by remember { mutableStateOf(nickname) }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Takma ad seç") },
            text = {
                Column {
                    Text("Mesajlarda görünecek adını yaz.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        label = { Text("Takma ad") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = input.trim().ifBlank { "Anonim" }
                    nickname = chosen
                    prefs.edit().putString("nickname", chosen).apply()
                    showNameDialog = false
                }) { Text("Kaydet") }
            }
        )
    }

    if (showUpdateDialog && updateUrl != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Güncelleme var (v$updateVersion)") },
            text = { Text("Yeni sürüm hazır. İndirilip kurulsun mu?") },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    isDownloading = true
                    val url = updateUrl ?: return@TextButton
                    scope.launch {
                        downloadAndInstall(context, url) { success ->
                            isDownloading = false
                        }
                    }
                }) { Text("İndir ve Kur") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("Daha sonra") }
            }
        )
    }

    if (isDownloading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("İndiriliyor...") },
            text = { Text("Güncelleme indiriliyor, lütfen bekleyin.") },
            confirmButton = { }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Açık Oda") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val error = viewModel.error.value
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp)
                )
            }

            val listState = rememberLazyListState()
            LaunchedEffect(viewModel.messages.size) {
                if (viewModel.messages.isNotEmpty()) {
                    listState.animateScrollToItem(viewModel.messages.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(viewModel.messages, key = { it.key }) { msg ->
                    MessageBubble(
                        message = msg,
                        isMine = msg.uid == viewModel.uid.value
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (viewModel.uploading.value) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Fotoğraf yükleniyor...")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    imagePicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }) {
                    Text("📷")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Mesaj yaz...") },
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.send(nickname, draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank() && viewModel.uid.value != null
                ) {
                    Text("Gönder")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isMine: Boolean) {
    val alignment = if (isMine) Arrangement.End else Arrangement.Start
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = alignment
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isMine)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = message.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                if (message.imageBase64.isNotBlank() || message.imageUrl.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    val model: Any = if (message.imageBase64.isNotBlank()) {
                        Base64.decode(message.imageBase64, Base64.DEFAULT)
                    } else {
                        message.imageUrl
                    }
                    AsyncImage(
                        model = model,
                        contentDescription = "Fotoğraf",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (message.text.isNotBlank() && message.text != "📷 Fotoğraf") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (message.imageUrl.isBlank() && message.imageBase64.isBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = formatTime(message.ts),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

private suspend fun downloadAndInstall(
    context: Context,
    url: String,
    onComplete: (Boolean) -> Unit
) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                onComplete(false)
                return@withContext
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("FirebaseChat güncelleme")
                .setDescription("İndiriliyor...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "FirebaseChat-update.apk")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadId = dm.enqueue(request)

            while (true) {
                val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        cursor.close()
                        val uri = dm.getUriForDownloadedFile(downloadId)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            openInstaller(context, uri)
                        }
                        onComplete(true)
                        return@withContext
                    }
                    if (status == DownloadManager.STATUS_FAILED) {
                        cursor.close()
                        onComplete(false)
                        return@withContext
                    }
                }
                cursor.close()
                kotlinx.coroutines.delay(1000)
            }
        } catch (_: Exception) {
            onComplete(false)
        }
    }
}

private fun openInstaller(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}
private fun formatTime(ts: Long): String {
    if (ts <= 0L) return ""
    return try {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    } catch (_: Exception) {
        ""
    }
}
