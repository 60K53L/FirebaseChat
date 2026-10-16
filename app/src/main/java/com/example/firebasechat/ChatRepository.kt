package com.example.firebasechat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class ChatRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val messagesRef = FirebaseDatabase.getInstance().getReference("messages")
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")

    fun signInAnon(callback: (String?) -> Unit) {
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    callback(auth.currentUser?.uid)
                } else {
                    callback(null)
                }
            }
    }

    fun currentUid(): String? = auth.currentUser?.uid

    fun saveToken(uid: String, token: String) {
        usersRef.child(uid).child("token").setValue(token)
    }

    fun send(uid: String, name: String, text: String, imageBase64: String = "") {
        val payload = mutableMapOf<String, Any>(
            "uid" to uid,
            "name" to name.trim().ifBlank { "Anonim" },
            "text" to text.trim(),
            "ts" to ServerValue.TIMESTAMP
        )
        if (imageBase64.isNotBlank()) {
            payload["imageBase64"] = imageBase64
        }
        messagesRef.push().setValue(payload)
    }

    fun listen(onChanged: (List<ChatMessage>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                for (child in snapshot.children) {
                    val uid = child.child("uid").getValue(String::class.java) ?: ""
                    val name = child.child("name").getValue(String::class.java) ?: ""
                    val text = child.child("text").getValue(String::class.java) ?: ""
                    val imageUrl = child.child("imageUrl").getValue(String::class.java) ?: ""
                    val imageBase64 = child.child("imageBase64").getValue(String::class.java) ?: ""
                    val ts = child.child("ts").getValue(Long::class.java) ?: 0L
                    list.add(ChatMessage(child.key ?: "", uid, name, text, imageUrl, imageBase64, ts))
                }
                list.sortBy { it.ts }
                onChanged(list)
            }

            override fun onCancelled(error: DatabaseError) {
            }
        }
        messagesRef.limitToLast(200).addValueEventListener(listener)
        return listener
    }

    fun deleteMessage(key: String) {
        if (key.isNotBlank()) {
            messagesRef.child(key).removeValue()
        }
    }

    fun deleteAllMessages() {
        messagesRef.removeValue()
    }

    fun removeListener(listener: ValueEventListener) {
        messagesRef.removeEventListener(listener)
    }
}
