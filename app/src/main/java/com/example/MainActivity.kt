package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Note
import com.example.data.PasswordEntry
import com.example.ui.VaultViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class ActiveTab {
    NOTES, PASSWORDS, FAVORITES, SETTINGS
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: VaultViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[VaultViewModel::class.java]

        // Dynamic initial flag SECURE setup
        if (viewModel.securityManager.isScreenshotProtectionEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        setContent {
            MyApplicationTheme {
                val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
                val isSetupRequired by viewModel.isSetupRequired.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        isSetupRequired -> {
                            FirstLaunchSetupScreen(viewModel = viewModel)
                        }
                        isLocked -> {
                            PinUnlockScreen(viewModel = viewModel, onScreenshotProtectChange = { enabled ->
                                setScreenshotProtection(enabled)
                            })
                        }
                        else -> {
                            VaultDashboardScreen(viewModel = viewModel, onScreenshotProtectChange = { enabled ->
                                setScreenshotProtection(enabled)
                            })
                        }
                    }
                }
            }
        }
    }

    private fun setScreenshotProtection(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

// ------ STRENGTH ASSESSMENT ------
enum class PasswordStrength(val label: String, val color: Color, val progress: Float) {
    EMPTY("No Password", Color(0xFF6B7280), 0.0f),
    WEAK("Weak Grade", DangerRed, 0.33f),
    MEDIUM("Medium Grade", WarningOrange, 0.66f),
    STRONG("Strong Shield", SafeGreen, 1.0f)
}

fun checkPasswordStrength(p: String): PasswordStrength {
    if (p.isEmpty()) return PasswordStrength.EMPTY
    var score = 0
    if (p.length >= 8) score++
    if (p.length >= 12) score++
    if (p.any { it.isUpperCase() }) score++
    if (p.any { it.isLowerCase() }) score++
    if (p.any { it.isDigit() }) score++
    if (p.any { !it.isLetterOrDigit() }) score++

    return when {
        score <= 2 -> PasswordStrength.WEAK
        score <= 4 -> PasswordStrength.MEDIUM
        else -> PasswordStrength.STRONG
    }
}

// ------ CLIPBOARD PROTECT HELPER ------
fun copyToClipboard(context: Context, text: String, label: String, autoClearSeconds: Int, scope: kotlinx.coroutines.CoroutineScope) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label Copied! Cleans in $autoClearSeconds s.", Toast.LENGTH_SHORT).show()

    scope.launch {
        delay(autoClearSeconds * 1000L)
        try {
            val currentClip = clipboard.primaryClip
            if (currentClip != null && currentClip.itemCount > 0) {
                val currentText = currentClip.getItemAt(0).text?.toString()
                if (currentText == text) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("VaultNote", ""))
                    Toast.makeText(context, "Secure clipboard auto-cleared.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}

// ------ 1. FIRST LAUNCH SETUP ------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstLaunchSetupScreen(viewModel: VaultViewModel) {
    var masterPassword by remember { mutableStateOf("") }
    var confirmMasterPassword by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var recoveryEmail by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepNavy, CardBackground)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(scrollState)
                .background(CardBackground.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Shield Logo",
                tint = ElectricIndigo,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "Initialize VaultNote",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Setup your offline secure keys. Master password will secure your database while security PIN handles fast unlocks.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            HorizontalDivider(color = BorderColor, thickness = 1.dp)

            // Inputs
            OutlinedTextField(
                value = masterPassword,
                onValueChange = { masterPassword = it },
                label = { Text("Master Password (AES Key)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("setup_master_pass"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricIndigo,
                    unfocusedBorderColor = BorderColor
                )
            )

            OutlinedTextField(
                value = confirmMasterPassword,
                onValueChange = { confirmMasterPassword = it },
                label = { Text("Confirm Master Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("setup_confirm_master_pass"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricIndigo,
                    unfocusedBorderColor = BorderColor
                )
            )

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pin = it },
                label = { Text("App Unlock PIN (4-6 Digits)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("setup_pin"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricIndigo,
                    unfocusedBorderColor = BorderColor
                )
            )

            OutlinedTextField(
                value = confirmPin,
                onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) confirmPin = it },
                label = { Text("Confirm App Unlock PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("setup_confirm_pin"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricIndigo,
                    unfocusedBorderColor = BorderColor
                )
            )

            OutlinedTextField(
                value = recoveryEmail,
                onValueChange = { recoveryEmail = it },
                label = { Text("Recovery Email (Optional)") },
                modifier = Modifier.fillMaxWidth().testTag("setup_recovery_email"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricIndigo,
                    unfocusedBorderColor = BorderColor
                )
            )

            if (error != null) {
                Text(
                    text = error!!,
                    color = DangerRed,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = {
                    if (masterPassword.length < 8) {
                        error = "Master Password must be at least 8 characters."
                    } else if (masterPassword != confirmMasterPassword) {
                        error = "Master Passwords do not match."
                    } else if (pin.length < 4) {
                        error = "PIN must be at least 4 digits."
                    } else if (pin != confirmPin) {
                        error = "PINs do not match."
                    } else {
                        viewModel.setupCredentials(masterPassword, pin, recoveryEmail)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_setup_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create Vault", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ------ 2. SECURE PIN UNLOCK SCREEN ------
@Composable
fun PinUnlockScreen(viewModel: VaultViewModel, onScreenshotProtectChange: (Boolean) -> Unit) {
    var pinValue by remember { mutableStateOf("") }
    var useMasterPassMode by remember { mutableStateOf(false) }
    var masterPassValue by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 400.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Lock Logo",
                tint = ElectricIndigo,
                modifier = Modifier.size(56.dp)
            )

            Text(
                text = "Vault is Sealed",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = if (useMasterPassMode) "Enter master password to unlock" else "Enter unlock PIN code",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            if (authError) {
                Text(
                    text = "Incorrect credentials. Try again.",
                    color = DangerRed,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (useMasterPassMode) {
                OutlinedTextField(
                    value = masterPassValue,
                    onValueChange = { masterPassValue = it },
                    label = { Text("Master Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("unlock_master_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricIndigo,
                        unfocusedBorderColor = BorderColor
                    )
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = { useMasterPassMode = false; authError = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Use PIN", color = ElectricIndigo)
                    }

                    Button(
                        onClick = {
                            if (viewModel.unlockWithMaster(masterPassValue)) {
                                authError = false
                            } else {
                                authError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("unlock_master_button")
                    ) {
                        Text("Unlock")
                    }
                }
            } else {
                // PIN indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    for (i in 1..6) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pinValue.length >= i) ElectricIndigo else BorderColor
                                )
                        )
                    }
                }

                // Numerical keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("Master", "0", "Back")
                    )

                    for (row in keys) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (key in row) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.5f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(CardBackground)
                                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                        .clickable {
                                            authError = false
                                            when (key) {
                                                "Back" -> {
                                                    if (pinValue.isNotEmpty()) {
                                                        pinValue = pinValue.dropLast(1)
                                                    }
                                                }
                                                "Master" -> {
                                                    useMasterPassMode = true
                                                }
                                                else -> {
                                                    if (pinValue.length < 6) {
                                                        pinValue += key
                                                    }
                                                    // Auto-trigger verify
                                                    if (pinValue.length >= 4) {
                                                        scope.launch {
                                                            delay(200)
                                                            if (viewModel.unlockWithPin(pinValue)) {
                                                                authError = false
                                                            } else if (pinValue.length >= 6) {
                                                                authError = true
                                                                pinValue = ""
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .testTag("keypad_$key"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (key == "Back") {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "Delete",
                                            tint = TextPrimary
                                        )
                                    } else if (key == "Master") {
                                        Text(
                                            text = "Key",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrightPurple
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------ 3. SECURE VAULT DASHBOARD ------
@Composable
fun VaultDashboardScreen(
    viewModel: VaultViewModel,
    onScreenshotProtectChange: (Boolean) -> Unit
) {
    var activeTab by remember { mutableStateOf(ActiveTab.NOTES) }
    var notesSearchQuery by remember { mutableStateOf("") }
    var notesFilterCategory by remember { mutableStateOf("All") }
    var vaultSearchQuery by remember { mutableStateOf("") }
    var vaultFilterCategory by remember { mutableStateOf("All") }

    // Dialog sheets management
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAddPasswordDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var editingPassword by remember { mutableStateOf<PasswordEntry?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = CardBackground,
                tonalElevation = 8.dp,
                modifier = Modifier.border(0.5.dp, BorderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = activeTab == ActiveTab.NOTES,
                    onClick = { activeTab = ActiveTab.NOTES },
                    icon = { Icon(Icons.Default.NoteAlt, "Notes") },
                    label = { Text("Notes") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = ElectricIndigo,
                        indicatorColor = ElectricIndigo,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("tab_notes")
                )
                NavigationBarItem(
                    selected = activeTab == ActiveTab.PASSWORDS,
                    onClick = { activeTab = ActiveTab.PASSWORDS },
                    icon = { Icon(Icons.Default.VpnKey, "Passwords") },
                    label = { Text("Passwords") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = ElectricIndigo,
                        indicatorColor = ElectricIndigo,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("tab_passwords")
                )
                NavigationBarItem(
                    selected = activeTab == ActiveTab.FAVORITES,
                    onClick = { activeTab = ActiveTab.FAVORITES },
                    icon = { Icon(Icons.Default.Favorite, "Favorites") },
                    label = { Text("Favorites") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = ElectricIndigo,
                        indicatorColor = ElectricIndigo,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("tab_favorites")
                )
                NavigationBarItem(
                    selected = activeTab == ActiveTab.SETTINGS,
                    onClick = { activeTab = ActiveTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = ElectricIndigo,
                        indicatorColor = ElectricIndigo,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        },
        floatingActionButton = {
            if (activeTab == ActiveTab.NOTES || activeTab == ActiveTab.PASSWORDS) {
                FloatingActionButton(
                    onClick = {
                        if (activeTab == ActiveTab.NOTES) {
                            showAddNoteDialog = true
                        } else {
                            showAddPasswordDialog = true
                        }
                    },
                    containerColor = ElectricIndigo,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add")
                ) {
                    Icon(Icons.Default.Add, "Add Entry")
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNavy)
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_vaultnote_logo),
                            contentDescription = "VaultNote Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column {
                            Text(
                                text = "VaultNote",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = ElectricIndigo,
                                fontSize = 22.sp
                            )
                            Text(
                                text = "Offline Secure Ecosystem",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Sophisticated User Avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CardBackground)
                                .border(1.dp, BorderColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AK",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricIndigo
                            )
                        }

                        IconButton(
                            onClick = { viewModel.lockVault() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(CardBackground, RoundedCornerShape(10.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                .testTag("lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock App",
                                tint = AccentPink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

                // Quick Actions Section matching Sophisticated Dark HTML
                if (activeTab == ActiveTab.NOTES || activeTab == ActiveTab.PASSWORDS) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Quick Action: New Note
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(ActionBoxPurple)
                                .clickable { showAddNoteDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ElectricIndigo),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Note",
                                    tint = ActionBoxPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "New Note",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "In secure block",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Right Quick Action: Add Secret (Add Password)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF1C1B1F))
                                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                                .clickable { showAddPasswordDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BrightPurple),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = "Add Secret",
                                    tint = ElectricIndigo,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Add Secret",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Encrypted key",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Render Active Content State
                when (activeTab) {
                    ActiveTab.NOTES -> {
                        NotesViewTabContent(
                            viewModel = viewModel,
                            searchQuery = notesSearchQuery,
                            onSearchChange = { notesSearchQuery = it },
                            filterCategory = notesFilterCategory,
                            onFilterChange = { notesFilterCategory = it },
                            onEditNote = { editingNote = it }
                        )
                    }
                    ActiveTab.PASSWORDS -> {
                        PasswordsViewTabContent(
                            viewModel = viewModel,
                            searchQuery = vaultSearchQuery,
                            onSearchChange = { vaultSearchQuery = it },
                            filterCategory = vaultFilterCategory,
                            onFilterChange = { vaultFilterCategory = it },
                            onEditPassword = { editingPassword = it }
                        )
                    }
                    ActiveTab.FAVORITES -> {
                        FavoritesViewTabContent(
                            viewModel = viewModel,
                            onEditNote = { editingNote = it },
                            onEditPassword = { editingPassword = it }
                        )
                    }
                    ActiveTab.SETTINGS -> {
                        SettingsViewTabContent(
                            viewModel = viewModel,
                            onScreenshotProtectChange = onScreenshotProtectChange
                        )
                    }
                }
            }

            // AI Overlay notification window
            val aiStatusText by viewModel.aiResult.collectAsStateWithLifecycle()
            val aiLoadingState by viewModel.aiLoading.collectAsStateWithLifecycle()

            if (aiLoadingState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        modifier = Modifier.padding(24.dp).border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = ElectricIndigo)
                            Text("Gemini AI is processing...", color = TextPrimary)
                        }
                    }
                }
            }

            if (aiStatusText != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearAiResult() },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, "AI", tint = BrightPurple)
                            Text("Gemini Summarization", fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    },
                    text = {
                        Text(aiStatusText!!, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearAiResult() }) {
                            Text("Dismiss", color = ElectricIndigo)
                        }
                    },
                    containerColor = CardBackground,
                    textContentColor = TextPrimary
                )
            }

            // ADD / EDIT Dynamic Dialogs
            if (showAddNoteDialog) {
                AddEditNoteDialog(
                    viewModel = viewModel,
                    note = null,
                    onDismiss = { showAddNoteDialog = false }
                )
            }

            if (editingNote != null) {
                AddEditNoteDialog(
                    viewModel = viewModel,
                    note = editingNote,
                    onDismiss = { editingNote = null }
                )
            }

            if (showAddPasswordDialog) {
                AddEditPasswordDialog(
                    viewModel = viewModel,
                    passwordEntry = null,
                    onDismiss = { showAddPasswordDialog = false }
                )
            }

            if (editingPassword != null) {
                AddEditPasswordDialog(
                    viewModel = viewModel,
                    passwordEntry = editingPassword!!,
                    onDismiss = { editingPassword = null }
                )
            }
        }
    }
}

// ------ 4A. NOTES VIEW TAB ------
@Composable
fun NotesViewTabContent(
    viewModel: VaultViewModel,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filterCategory: String,
    onFilterChange: (String) -> Unit,
    onEditNote: (Note) -> Unit
) {
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val categories = listOf("All", "General", "Personal", "Work", "Finance", "Ideas")

    val filteredNotes = notes.filter { note ->
        val matchesSearch = note.title.contains(searchQuery, ignoreCase = true) ||
                viewModel.decryptData(note.encryptedContent).contains(searchQuery, ignoreCase = true) ||
                note.tags.contains(searchQuery, ignoreCase = true)
        val matchesCategory = filterCategory == "All" || note.category.equals(filterCategory, ignoreCase = true)
        matchesSearch && matchesCategory
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // Search text inputs
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search title, tags, or contents...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, "Search Icon", tint = TextMuted) },
            modifier = Modifier.fillMaxWidth().testTag("notes_search_input"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricIndigo,
                unfocusedBorderColor = BorderColor,
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground
            )
        )

        // Horizontal visual chips list
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                val selected = filterCategory == category
                FilterChip(
                    selected = selected,
                    onClick = { onFilterChange(category) },
                    label = { Text(category, fontSize = 12.sp, color = if (selected) Color.White else TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricIndigo,
                        containerColor = CardBackground
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (selected) ElectricIndigo else BorderColor,
                        selectedBorderColor = ElectricIndigo,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp,
                        enabled = true,
                        selected = selected
                    )
                )
            }
        }

        if (filteredNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.NoteAdd, "No Items", tint = TextMuted, modifier = Modifier.size(48.dp))
                    Text("No secure notes match the criteria.", color = TextMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("notes_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredNotes, key = { it.id }) { note ->
                    SecureNoteCard(
                        note = note,
                        viewModel = viewModel,
                        onClick = { onEditNote(note) },
                        onPinToggle = { viewModel.togglePinNote(note) },
                        onSummarize = { viewModel.summarizeNote(note) }
                    )
                }
            }
        }
    }
}

@Composable
fun SecureNoteCard(
    note: Note,
    viewModel: VaultViewModel,
    onClick: () -> Unit,
    onPinToggle: () -> Unit,
    onSummarize: () -> Unit
) {
    val context = LocalContext.current
    var isRevealed by remember { mutableStateOf(false) }
    val decryptedBody = remember(note.encryptedContent, isRevealed) {
        if (isRevealed) viewModel.decryptData(note.encryptedContent) else "••••••••••••••••"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("note_card_${note.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (note.isPinned) ElectricIndigo else BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin Status",
                        tint = if (note.isPinned) ElectricIndigo else TextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onPinToggle() }
                    )
                    Text(
                        text = note.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { isRevealed = !isRevealed },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Reveal",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { onSummarize() },
                        modifier = Modifier.size(28.dp).testTag("note_ai_summarize_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Summarize",
                            tint = BrightPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = decryptedBody,
                fontSize = 13.sp,
                color = if (isRevealed) TextPrimary else TextMuted,
                maxLines = 3,
                fontFamily = if (isRevealed) FontFamily.Default else FontFamily.Monospace,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(ElectricIndigo.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(note.category, color = BrightPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                val df = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                Text(
                    text = df.format(Date(note.updatedAt)),
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}

// ------ 4B. PASSWORD VAULT TAB ------
@Composable
fun PasswordsViewTabContent(
    viewModel: VaultViewModel,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filterCategory: String,
    onFilterChange: (String) -> Unit,
    onEditPassword: (PasswordEntry) -> Unit
) {
    val passwords by viewModel.allPasswords.collectAsStateWithLifecycle()
    val categories = listOf("All", "Login", "Social", "Work", "Finance", "Custom")

    val filteredPasswords = passwords.filter { p ->
        val matchesSearch = p.platform.contains(searchQuery, ignoreCase = true) ||
                p.username.contains(searchQuery, ignoreCase = true) ||
                p.email.contains(searchQuery, ignoreCase = true)
        val matchesCategory = filterCategory == "All" || p.category.equals(filterCategory, ignoreCase = true)
        matchesSearch && matchesCategory
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search platforms, emails, accounts...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, "Search Icon", tint = TextMuted) },
            modifier = Modifier.fillMaxWidth().testTag("vault_search_input"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricIndigo,
                unfocusedBorderColor = BorderColor,
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground
            )
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                val selected = filterCategory == category
                FilterChip(
                    selected = selected,
                    onClick = { onFilterChange(category) },
                    label = { Text(category, fontSize = 12.sp, color = if (selected) Color.White else TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricIndigo,
                        containerColor = CardBackground
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (selected) ElectricIndigo else BorderColor,
                        selectedBorderColor = ElectricIndigo,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp,
                        enabled = true,
                        selected = selected
                    )
                )
            }
        }

        if (filteredPasswords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Key, "No Keys", tint = TextMuted, modifier = Modifier.size(48.dp))
                    Text("No credentials saved yet.", color = TextMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("passwords_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredPasswords, key = { it.id }) { password ->
                    SecurePasswordCard(
                        entry = password,
                        viewModel = viewModel,
                        onClick = { onEditPassword(password) },
                        onFavoriteToggle = { viewModel.toggleFavoritePassword(password) }
                    )
                }
            }
        }
    }
}

@Composable
fun SecurePasswordCard(
    entry: PasswordEntry,
    viewModel: VaultViewModel,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPasswordVisible by remember { mutableStateOf(false) }
    val decryptedPassword = remember(entry.encryptedPassword, isPasswordVisible) {
        if (isPasswordVisible) viewModel.decryptData(entry.encryptedPassword) else "••••••••••••••••"
    }

    val strength = remember(entry.encryptedPassword) {
        checkPasswordStrength(viewModel.decryptData(entry.encryptedPassword))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("password_card_${entry.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (entry.isFavorite) BrightPurple else BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (entry.isFavorite) BrightPurple else TextMuted,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onFavoriteToggle() }
                    )
                    Text(
                        text = entry.platform,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { isPasswordVisible = !isPasswordVisible },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Reveal Password",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Copy action button
                    IconButton(
                        onClick = {
                            val rawPass = viewModel.decryptData(entry.encryptedPassword)
                            copyToClipboard(
                                context = context,
                                text = rawPass,
                                label = "${entry.platform} Password",
                                autoClearSeconds = viewModel.securityManager.clipboardClearTime,
                                scope = scope
                            )
                        },
                        modifier = Modifier.size(28.dp).testTag("copy_password_button_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Password",
                            tint = ElectricIndigo,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Target credentials summary
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (entry.username.isNotEmpty()) {
                    Text("User: ${entry.username}", fontSize = 12.sp, color = TextMuted)
                }
                if (entry.email.isNotEmpty()) {
                    Text("Email: ${entry.email}", fontSize = 12.sp, color = TextMuted)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = decryptedPassword,
                        fontSize = 12.sp,
                        color = if (isPasswordVisible) TextPrimary else TextMuted,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )

                    // Strength indicator pill
                    if (strength != PasswordStrength.EMPTY) {
                        Box(
                            modifier = Modifier
                                .background(strength.color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, strength.color, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = strength.label,
                                color = strength.color,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------ 4C. FAVORITES VIEW TAB ------
@Composable
fun FavoritesViewTabContent(
    viewModel: VaultViewModel,
    onEditNote: (Note) -> Unit,
    onEditPassword: (PasswordEntry) -> Unit
) {
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val passwords by viewModel.allPasswords.collectAsStateWithLifecycle()

    val pinnedNotes = notes.filter { it.isPinned }
    val starredSecurities = passwords.filter { it.isFavorite }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Section: Pinned notes
        Text(
            text = "Pinned Notes (${pinnedNotes.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ElectricIndigo
        )

        if (pinnedNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No pinned notes. Select push-pin on a note.", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pinnedNotes.forEach { note ->
                    SecureNoteCard(
                        note = note,
                        viewModel = viewModel,
                        onClick = { onEditNote(note) },
                        onPinToggle = { viewModel.togglePinNote(note) },
                        onSummarize = { viewModel.summarizeNote(note) }
                    )
                }
            }
        }

        // Section: Starred accounts
        Text(
            text = "Starred Passwords (${starredSecurities.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrightPurple
        )

        if (starredSecurities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No starred entries. Star passwords in the vault.", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                starredSecurities.forEach { password ->
                    SecurePasswordCard(
                        entry = password,
                        viewModel = viewModel,
                        onClick = { onEditPassword(password) },
                        onFavoriteToggle = { viewModel.toggleFavoritePassword(password) }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ------ 4D. SETTINGS VIEW TAB ------
@Composable
fun SettingsViewTabContent(
    viewModel: VaultViewModel,
    onScreenshotProtectChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var isScreenshotProtect by remember { mutableStateOf(viewModel.securityManager.isScreenshotProtectionEnabled) }
    var autoLockVal by remember { mutableStateOf(viewModel.securityManager.autoLockTime) }
    var clipboardClearVal by remember { mutableStateOf(viewModel.securityManager.clipboardClearTime) }

    // Dialog flags
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }
    var showChangeMasterPass by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Security Policies",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ElectricIndigo
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Screenshot protection toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Prevent Screenshots", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Blocks screen records and multitasking snaps", fontSize = 12.sp, color = TextMuted)
                    }
                    Switch(
                        checked = isScreenshotProtect,
                        onCheckedChange = {
                            isScreenshotProtect = it
                            viewModel.securityManager.isScreenshotProtectionEnabled = it
                            onScreenshotProtectChange(it)
                            Toast.makeText(context, if (it) "Screenshots blocked" else "Screenshots allowed", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = ElectricIndigo, checkedTrackColor = ElectricIndigo.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(color = BorderColor)

                // Clipboard duration selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Auto Clear Copied Passwords", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Wipes clipboard memory after chosen seconds", fontSize = 12.sp, color = TextMuted)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        listOf(15, 30, 60).forEach { s ->
                            val selected = clipboardClearVal == s
                            Box(
                                modifier = Modifier
                                    .background(if (selected) ElectricIndigo else BorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        clipboardClearVal = s
                                        viewModel.securityManager.clipboardClearTime = s
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("$s s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Ecosystem Backups",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrightPurple
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { showBackupDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("export_backup_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                ) {
                    Icon(Icons.Default.CloudUpload, "Backup", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export Encrypted Backup (.vault)")
                }

                OutlinedButton(
                    onClick = { showRestoreDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("import_backup_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Icon(Icons.Default.CloudDownload, "Restore", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import Encrypted Backup")
                }
            }
        }

        Text(
            text = "App Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AccentPink
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Change master password
                Button(
                    onClick = { showChangeMasterPass = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Icon(Icons.Default.LockReset, "Lock reset", modifier = Modifier.size(18.dp), tint = TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Change Master Password", color = TextPrimary)
                }

                HorizontalDivider(color = BorderColor)

                // Wipe all data button
                Button(
                    onClick = { showWipeConfirm = true },
                    modifier = Modifier.fillMaxWidth().testTag("wipe_data_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, DangerRed)
                ) {
                    Icon(Icons.Default.DeleteForever, "Wipe", tint = DangerRed)
                    Spacer(Modifier.width(8.dp))
                    Text("Wipe All Vault Records", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Dialog realizations
        if (showBackupDialog) {
            val backupPayload = remember { viewModel.exportEncryptedBackup() }
            val clipboardScope = rememberCoroutineScope()
            Dialog(onDismissRequest = { showBackupDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Secured Export Key", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                        Text(
                            "This encrypted block contains all notes & passwords. Store it safely.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(DeepNavy, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(backupPayload, color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = {
                                copyToClipboard(context, backupPayload, "Vault Backup Key", 30, clipboardScope)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy")
                            Spacer(Modifier.width(8.dp))
                            Text("Copy Backup String")
                        }

                        TextButton(onClick = { showBackupDialog = false }) {
                            Text("Done", color = ElectricIndigo)
                        }
                    }
                }
            }
        }

        if (showRestoreDialog) {
            var restoreString by remember { mutableStateOf("") }
            Dialog(onDismissRequest = { showRestoreDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Restore Your Vault", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                        Text(
                            "Paste your generated encrypted backup string below. This will add its notes and passwords to your current local database.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        OutlinedTextField(
                            value = restoreString,
                            onValueChange = { restoreString = it },
                            placeholder = { Text("Paste string...", color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("import_backup_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricIndigo,
                                unfocusedBorderColor = BorderColor
                            )
                        )

                        Button(
                            onClick = {
                                if (viewModel.importEncryptedBackup(restoreString)) {
                                    Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                                    showRestoreDialog = false
                                } else {
                                    Toast.makeText(context, "Invalid backup string or decryption error.", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                            modifier = Modifier.fillMaxWidth().testTag("submit_import_button")
                        ) {
                            Text("Restore Database")
                        }

                        TextButton(onClick = { showRestoreDialog = false }) {
                            Text("Cancel", color = TextMuted)
                        }
                    }
                }
            }
        }

        if (showWipeConfirm) {
            AlertDialog(
                onDismissRequest = { showWipeConfirm = false },
                title = { Text("CRITICAL WARNING", color = DangerRed, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "This will permanently hard delete all secured database records of Notes and Password vault entries. This action CANNOT be undone.",
                        color = TextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteAllData()
                            showWipeConfirm = false
                            Toast.makeText(context, "Vault wiped entirely.", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text("Wipe Everything")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWipeConfirm = false }) {
                        Text("Cancel", color = TextPrimary)
                    }
                },
                containerColor = CardBackground
            )
        }

        if (showChangeMasterPass) {
            var oldPass by remember { mutableStateOf("") }
            var newPass by remember { mutableStateOf("") }
            var changeError by remember { mutableStateOf(false) }

            Dialog(onDismissRequest = { showChangeMasterPass = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Reset Master Password", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)

                        OutlinedTextField(
                            value = oldPass,
                            onValueChange = { oldPass = it },
                            label = { Text("Old Master Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                        )

                        OutlinedTextField(
                            value = newPass,
                            onValueChange = { newPass = it },
                            label = { Text("New Master Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                        )

                        if (changeError) {
                            Text("Incorrect old master password, match failed.", color = DangerRed, fontSize = 11.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { showChangeMasterPass = false }) {
                                Text("Cancel", color = TextMuted)
                            }
                            Button(
                                onClick = {
                                    if (viewModel.changeMasterPassword(oldPass, newPass)) {
                                        Toast.makeText(context, "Password updated!", Toast.LENGTH_SHORT).show()
                                        showChangeMasterPass = false
                                    } else {
                                        changeError = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                            ) {
                                Text("Update")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------ 5. ADD/EDIT NOTE DIALOG ------
@Composable
fun AddEditNoteDialog(
    viewModel: VaultViewModel,
    note: Note?,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.let { viewModel.decryptData(it.encryptedContent) } ?: "") }
    var category by remember { mutableStateOf(note?.category ?: "General") }
    var tags by remember { mutableStateOf(note?.tags ?: "") }
    var isPinned by remember { mutableStateOf(note?.isPinned ?: false) }

    val categories = listOf("General", "Personal", "Work", "Finance", "Ideas")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (note == null) "Create Note" else "Edit Note",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 18.sp
                    )

                    IconButton(onClick = { isPinned = !isPinned }) {
                        Icon(
                            imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (isPinned) ElectricIndigo else TextMuted
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth().testTag("note_title_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note content (AES-256 protected)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .testTag("note_content_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                )

                // Category grid chips selector
                Text("Category", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    categories.forEach { cat ->
                        AssistChip(
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp, color = if (category == cat) ElectricIndigo else TextMuted) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (category == cat) ElectricIndigo.copy(alpha = 0.1f) else Color.Transparent,
                            ),
                            border = BorderStroke(1.dp, if (category == cat) ElectricIndigo else BorderColor)
                        )
                    }
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated details)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note != null) {
                        TextButton(
                            onClick = {
                                viewModel.deleteNote(note)
                                onDismiss()
                            }
                        ) {
                            Icon(Icons.Default.Delete, "Delete", tint = DangerRed)
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = DangerRed)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = TextMuted)
                        }
                        Button(
                            onClick = {
                                if (title.isNotEmpty()) {
                                    if (note == null) {
                                        viewModel.createNote(title, content, category, tags, isPinned)
                                    } else {
                                        viewModel.updateNote(note, title, content, category, tags, isPinned)
                                    }
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                            modifier = Modifier.testTag("save_note_button")
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

// ------ 6. ADD/EDIT PASSWORD DIALOG ------
@Composable
fun AddEditPasswordDialog(
    viewModel: VaultViewModel,
    passwordEntry: PasswordEntry?,
    onDismiss: () -> Unit
) {
    var platform by remember { mutableStateOf(passwordEntry?.platform ?: "") }
    var username by remember { mutableStateOf(passwordEntry?.username ?: "") }
    var email by remember { mutableStateOf(passwordEntry?.email ?: "") }
    var phone by remember { mutableStateOf(passwordEntry?.phone ?: "") }
    var password by remember { mutableStateOf(passwordEntry?.let { viewModel.decryptData(it.encryptedPassword) } ?: "") }
    var websiteUrl by remember { mutableStateOf(passwordEntry?.websiteUrl ?: "") }
    var notes by remember { mutableStateOf(passwordEntry?.notes ?: "") }
    var category by remember { mutableStateOf(passwordEntry?.category ?: "Login") }
    var isFavorite by remember { mutableStateOf(passwordEntry?.isFavorite ?: false) }

    val categories = listOf("Login", "Social", "Work", "Finance", "Custom")

    // Password generator attributes
    var showGenerator by remember { mutableStateOf(false) }
    var genLength by remember { mutableStateOf(14) }

    val strength = remember(password) { checkPasswordStrength(password) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (passwordEntry == null) "Add Password" else "Edit Credentials",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 18.sp
                    )

                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Pin Favorite",
                            tint = if (isFavorite) BrightPurple else TextMuted
                        )
                    }
                }

                OutlinedTextField(
                    value = platform,
                    onValueChange = { platform = it },
                    label = { Text("Platform (e.g. Google, Discord)") },
                    modifier = Modifier.fillMaxWidth().testTag("password_platform_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth().testTag("password_email_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Reference") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                )

                // Password with side generator trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Platform Password") },
                        modifier = Modifier.weight(1f).testTag("password_raw_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                    )

                    IconButton(
                        onClick = { showGenerator = !showGenerator },
                        modifier = Modifier
                            .background(ElectricIndigo.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Autorenew, "Generate", tint = ElectricIndigo)
                    }
                }

                // Strengths rating visuals
                if (password.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Strength Assessment:", fontSize = 11.sp, color = TextMuted)
                            Text(strength.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = strength.color)
                        }
                        LinearProgressIndicator(
                            progress = { strength.progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = strength.color,
                            trackColor = BorderColor
                        )
                    }
                }

                // Interactive local or AI Generator tools
                if (showGenerator) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DeepNavy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Password Generator Settings", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ElectricIndigo)
                            Text("Preferred Length: $genLength characters", fontSize = 11.sp, color = TextPrimary)
                            Slider(
                                value = genLength.toFloat(),
                                onValueChange = { genLength = it.toInt() },
                                valueRange = 8f..24f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(thumbColor = ElectricIndigo, activeTrackColor = ElectricIndigo)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        // Quick secure local randomized generator
                                        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+"
                                        val localRand = (1..genLength).map { chars.random() }.joinToString("")
                                        password = localRand
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BorderColor),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Local Generation", fontSize = 11.sp, color = TextPrimary)
                                }

                                Button(
                                    onClick = {
                                        // Trigger Gemini AI generation
                                        viewModel.selectSuggestedPassword(platform) { resp ->
                                            if (resp.isNotEmpty()) {
                                                password = resp
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Gemini AI Shield", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = { websiteUrl = it },
                    label = { Text("Website Address url") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                )

                // Category assists
                Text("Category", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    categories.forEach { cat ->
                        AssistChip(
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp, color = if (category == cat) ElectricIndigo else TextMuted) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (category == cat) ElectricIndigo.copy(alpha = 0.1f) else Color.Transparent,
                            ),
                            border = BorderStroke(1.dp, if (category == cat) ElectricIndigo else BorderColor)
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Platform Reference Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricIndigo)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (passwordEntry != null) {
                        TextButton(
                            onClick = {
                                viewModel.deletePassword(passwordEntry)
                                onDismiss()
                            }
                        ) {
                            Icon(Icons.Default.Delete, "Delete", tint = DangerRed)
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = DangerRed)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = TextMuted)
                        }
                        Button(
                            onClick = {
                                if (platform.isNotEmpty()) {
                                    if (passwordEntry == null) {
                                        viewModel.createPassword(
                                            platform, username, email, phone, password, websiteUrl, notes, category
                                        )
                                    } else {
                                        viewModel.updatePassword(
                                            passwordEntry, platform, username, email, phone, password, websiteUrl, notes, category, isFavorite
                                        )
                                    }
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                            modifier = Modifier.testTag("save_password_button")
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
