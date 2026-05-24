package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val encryptedContent: String, // encrypted with AES-256
    val category: String = "General",
    val isPinned: Boolean = false,
    val tags: String = "", // Comma-separated tags
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "passwords")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val platform: String,
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val encryptedPassword: String, // encrypted with AES-256
    val websiteUrl: String = "",
    val notes: String = "", // non-sensitive list details
    val category: String = "Login",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
