package com.example.data

import kotlinx.coroutines.flow.Flow

class VaultRepository(
    private val noteDao: NoteDao,
    private val passwordDao: PasswordDao
) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val allPasswords: Flow<List<PasswordEntry>> = passwordDao.getAllPasswords()

    suspend fun insertNote(note: Note) {
        noteDao.insertNote(note)
    }

    suspend fun updateNote(note: Note) {
        noteDao.updateNote(note)
    }

    suspend fun deleteNote(note: Note) {
        noteDao.deleteNote(note)
    }

    suspend fun getNoteById(id: Int): Note? {
        return noteDao.getNoteById(id)
    }

    suspend fun insertPassword(passwordEntry: PasswordEntry) {
        passwordDao.insertPassword(passwordEntry)
    }

    suspend fun updatePassword(passwordEntry: PasswordEntry) {
        passwordDao.updatePassword(passwordEntry)
    }

    suspend fun deletePassword(passwordEntry: PasswordEntry) {
        passwordDao.deletePassword(passwordEntry)
    }

    suspend fun getPasswordById(id: Int): PasswordEntry? {
        return passwordDao.getPasswordById(id)
    }
}
