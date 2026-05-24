package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.security.EncryptionHelper
import com.example.security.SecurityManager
import com.example.api.GeminiClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = VaultRepository(database.noteDao(), database.passwordDao())
    val securityManager = SecurityManager(application)

    // UI States
    val allNotes: StateFlow<List<Note>> = repository.allNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allPasswords: StateFlow<List<PasswordEntry>> = repository.allPasswords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Navigation and Lock system
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isSetupRequired = MutableStateFlow(!securityManager.isSetupComplete)
    val isSetupRequired: StateFlow<Boolean> = _isSetupRequired.asStateFlow()

    // AI summary and suggestions state
    private val _aiResult = MutableStateFlow<String?>(null)
    val aiResult: StateFlow<String?> = _aiResult.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    init {
        // If app does not have setup complete, show setup
        _isLocked.value = securityManager.isSetupComplete
    }

    // Auth actions
    fun setupCredentials(masterPass: String, pin: String, recovery: String) {
        viewModelScope.launch {
            securityManager.setMasterPassword(masterPass)
            securityManager.setPin(pin)
            securityManager.recoveryEmail = recovery
            _isSetupRequired.value = false
            _isLocked.value = false
        }
    }

    fun unlockWithPin(pin: String): Boolean {
        return if (securityManager.verifyPin(pin)) {
            _isLocked.value = false
            true
        } else {
            false
        }
    }

    fun unlockWithMaster(password: String): Boolean {
        return if (securityManager.verifyMasterPassword(password)) {
            _isLocked.value = false
            true
        } else {
            false
        }
    }

    fun lockVault() {
        _isLocked.value = true
    }

    fun changeMasterPassword(old: String, new: String): Boolean {
        return securityManager.changeMasterPassword(old, new)
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        return if (securityManager.verifyPin(oldPin)) {
            securityManager.setPin(newPin)
            true
        } else {
            false
        }
    }

    // Notes Actions
    fun createNote(title: String, content: String, category: String, tags: String, isPinned: Boolean = false) {
        viewModelScope.launch {
            val encrypted = EncryptionHelper.encrypt(content)
            val note = Note(
                title = title,
                encryptedContent = encrypted,
                category = category,
                tags = tags,
                isPinned = isPinned,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.insertNote(note)
        }
    }

    fun updateNote(note: Note, newTitle: String, newContent: String, newCategory: String, newTags: String, isPinned: Boolean) {
        viewModelScope.launch {
            val encrypted = EncryptionHelper.encrypt(newContent)
            val updated = note.copy(
                title = newTitle,
                encryptedContent = encrypted,
                category = newCategory,
                tags = newTags,
                isPinned = isPinned,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateNote(updated)
        }
    }

    fun togglePinNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    // Password Actions
    fun createPassword(
        platform: String,
        username: String,
        email: String,
        phone: String,
        pass: String,
        websiteUrl: String,
        notes: String,
        category: String
    ) {
        viewModelScope.launch {
            val encrypted = EncryptionHelper.encrypt(pass)
            val entry = PasswordEntry(
                platform = platform,
                username = username,
                email = email,
                phone = phone,
                encryptedPassword = encrypted,
                websiteUrl = websiteUrl,
                notes = notes,
                category = category,
                createdAt = System.currentTimeMillis()
            )
            repository.insertPassword(entry)
        }
    }

    fun updatePassword(
        entry: PasswordEntry,
        platform: String,
        username: String,
        email: String,
        phone: String,
        pass: String,
        websiteUrl: String,
        notes: String,
        category: String,
        isFavorite: Boolean
    ) {
        viewModelScope.launch {
            val encrypted = EncryptionHelper.encrypt(pass)
            val updated = entry.copy(
                platform = platform,
                username = username,
                email = email,
                phone = phone,
                encryptedPassword = encrypted,
                websiteUrl = websiteUrl,
                notes = notes,
                category = category,
                isFavorite = isFavorite
            )
            repository.updatePassword(updated)
        }
    }

    fun toggleFavoritePassword(entry: PasswordEntry) {
        viewModelScope.launch {
            repository.updatePassword(entry.copy(isFavorite = !entry.isFavorite))
        }
    }

    fun deletePassword(entry: PasswordEntry) {
        viewModelScope.launch {
            repository.deletePassword(entry)
        }
    }

    // AI functions
    fun summarizeNote(note: Note) {
        viewModelScope.launch {
            _aiLoading.value = true
            _aiResult.value = null
            val rawContent = EncryptionHelper.decrypt(note.encryptedContent)
            val summary = GeminiClient.summarizeNoteContent(note.title, rawContent)
            _aiResult.value = "AI Note Summary:\n$summary"
            _aiLoading.value = false
        }
    }

    fun selectSuggestedPassword(platform: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _aiLoading.value = true
            val suggested = GeminiClient.suggestStrongPassword(platform)
            onResult(suggested)
            _aiLoading.value = false
        }
    }

    fun clearAiResult() {
        _aiResult.value = null
    }

    // Decryption Helper for view checks
    fun decryptData(encrypted: String): String {
        return EncryptionHelper.decrypt(encrypted)
    }

    // Backup & Restore
    fun exportEncryptedBackup(): String {
        try {
            val outerJson = JSONObject()
            
            // Serialize notes
            val notesArray = JSONArray()
            allNotes.value.forEach { note ->
                val o = JSONObject()
                o.put("title", note.title)
                o.put("content", note.encryptedContent) // already securely encrypted, double safety
                o.put("category", note.category)
                o.put("isPinned", note.isPinned)
                o.put("tags", note.tags)
                o.put("createdAt", note.createdAt)
                o.put("updatedAt", note.updatedAt)
                notesArray.put(o)
            }
            outerJson.put("notes", notesArray)

            // Serialize passwords
            val passwordsArray = JSONArray()
            allPasswords.value.forEach { p ->
                val o = JSONObject()
                o.put("platform", p.platform)
                o.put("username", p.username)
                o.put("email", p.email)
                o.put("phone", p.phone)
                o.put("password", p.encryptedPassword) // already securely encrypted, double safety
                o.put("websiteUrl", p.websiteUrl)
                o.put("notes", p.notes)
                o.put("category", p.category)
                o.put("isFavorite", p.isFavorite)
                o.put("createdAt", p.createdAt)
                passwordsArray.put(o)
            }
            outerJson.put("passwords", passwordsArray)

            // Encrypt the full JSON backup payload
            val fullString = outerJson.toString()
            return EncryptionHelper.encrypt(fullString)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    fun importEncryptedBackup(encryptedBackup: String): Boolean {
        try {
            val decrypted = EncryptionHelper.decrypt(encryptedBackup)
            if (decrypted.isEmpty() || decrypted.startsWith("[Decryption Error]")) return false
            
            val outerJson = JSONObject(decrypted)
            
            viewModelScope.launch {
                // Import notes
                if (outerJson.has("notes")) {
                    val notesArray = outerJson.getJSONArray("notes")
                    for (i in 0 until notesArray.length()) {
                        val o = notesArray.getJSONObject(i)
                        val note = Note(
                            title = o.optString("title", "Imported Note"),
                            encryptedContent = o.optString("content", ""),
                            category = o.optString("category", "General"),
                            isPinned = o.optBoolean("isPinned", false),
                            tags = o.optString("tags", ""),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                        repository.insertNote(note)
                    }
                }

                // Import passwords
                if (outerJson.has("passwords")) {
                    val passArray = outerJson.getJSONArray("passwords")
                    for (i in 0 until passArray.length()) {
                        val o = passArray.getJSONObject(i)
                        val entry = PasswordEntry(
                            platform = o.optString("platform", "Unknown Platform"),
                            username = o.optString("username", ""),
                            email = o.optString("email", ""),
                            phone = o.optString("phone", ""),
                            encryptedPassword = o.optString("password", ""),
                            websiteUrl = o.optString("websiteUrl", ""),
                            notes = o.optString("notes", ""),
                            category = o.optString("category", "Login"),
                            isFavorite = o.optBoolean("isFavorite", false),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                        repository.insertPassword(entry)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            // Delete security manager options except setup logic
            securityManager.clearAllData()
            
            // Delete DB records
            allNotes.value.forEach { repository.deleteNote(it) }
            allPasswords.value.forEach { repository.deletePassword(it) }
            
            // Force return to setup screen
            _isSetupRequired.value = true
            _isLocked.value = true
        }
    }
}
