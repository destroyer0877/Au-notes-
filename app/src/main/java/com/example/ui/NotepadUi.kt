package com.example.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import com.example.data.FolderEntity
import com.example.data.NoteEntity
import com.example.util.AutoSortDetector
import com.example.util.CopySoundPlayer
import com.example.util.NameStylizer
import com.example.util.NotepadExporter
import com.example.util.SyntaxHighlighter
import kotlinx.coroutines.launch

@Composable
fun NotepadApp(viewModel: NotepadViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // --- State Mappings ---
    val notes by viewModel.allNotes.collectAsState()
    val favorites by viewModel.favoriteNotes.collectAsState()
    val trashNotes by viewModel.deletedNotes.collectAsState()
    val folders by viewModel.customFolders.collectAsState()

    // Navigation Screens: "HOME", "EDITOR", "SETTINGS", "FONTS"
    var currentScreen by remember { mutableStateOf("HOME") }
    
    // Selection and Editor States
    var selectedNote by remember { mutableStateOf<NoteEntity?>(null) }
    var selectedFolderId by remember { mutableStateOf<Int>(-100) } // -100 means All Notes
    var selectedFolderTitle by remember { mutableStateOf("All Notes") }

    // Folder Security Locks
    var isPinUnlockDialogOpen by remember { mutableStateOf(false) }
    var pinEntered by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var pendingFolderUnlockId by remember { mutableStateOf<Int?>(null) }
    var unlockedFoldersList by remember { mutableStateOf(setOf<Int>()) } // temporarily unlocked ID session
    
    // Forgot PIN Recover states
    var isForgotPinOpen by remember { mutableStateOf(false) }
    var secretBypassInput by remember { mutableStateOf("") }

    // Dialogs
    var isAddFolderDialogOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderColorHex by remember { mutableStateOf("#34C759") } // green

    // Floating AI State
    var isAiPanelOpen by remember { mutableStateOf(false) }
    val homeAiTransition = rememberInfiniteTransition(label = "HomeAiFloatingAnim")
    val homeAiScale by homeAiTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HomeAiScale"
    )
    val homeAiRotate by homeAiTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HomeAiRotate"
    )
    val homeAiGlowOffset by homeAiTransition.animateFloat(
        initialValue = -50f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HomeAiGlow"
    )
    var selectedAiTab by remember { mutableStateOf("CHAT") } // "CHAT" or "HISTORY"
    var userAiPrompt by remember { mutableStateOf("") }
    val aiLogs by viewModel.aiChatHistory.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val oldSessions by viewModel.oldSessions.collectAsState()
    val aiSuggestions by viewModel.editorSuggestions.collectAsState()

    // Image Upload helper for AIOCR
    var selectedImageForAi by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bitmap = viewModel.decodeUriToBitmap(uri)
            if (bitmap != null) {
                selectedImageForAi = bitmap
                Toast.makeText(context, "Image Attachment Attached! Ready for AI extraction.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Background Ambient Mesh Gradient for Glassmorphism iOS feel ---
    val gradientBackground = if (viewModel.isDarkMode) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFF2F2F7), // iOS System Light Gray
                Color(0xFFFFFFFF), // Pure white shine
                Color(0xFFE5E5EA)  // iOS system secondary gray
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        // Edge elements to emphasize Glassmorphic overlays
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(y = (-50).dp, x = (-30).dp)
                .background(Color(0xFF2563EB).copy(alpha = 0.08f), circleShape())
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomEnd)
                .offset(y = 80.dp, x = 40.dp)
                .background(Color(0xFFEC4899).copy(alpha = 0.06f), circleShape())
        )

        // Backpress handler for natural screen transitions
        BackHandler(enabled = currentScreen != "HOME") {
            if (isAiPanelOpen) {
                isAiPanelOpen = false
            } else {
                currentScreen = "HOME"
            }
        }

        // --- View Navigation Switcher ---
        Column(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                "HOME" -> HomeScreenSection(
                    notes = notes,
                    favorites = favorites,
                    trashCount = trashNotes.size,
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    selectedFolderTitle = selectedFolderTitle,
                    onFolderSelected = { id, title ->
                        // APIs folder has virtual folder id: -3
                        if (id == -3 && viewModel.savedPin.isNotEmpty() && !unlockedFoldersList.contains(-3)) {
                            pendingFolderUnlockId = -3
                            isPinUnlockDialogOpen = true
                        } else {
                            selectedFolderId = id
                            selectedFolderTitle = title
                        }
                    },
                    onAddFolderClick = { isAddFolderDialogOpen = true },
                    onNoteSelect = { note ->
                        if (note.isLocked && viewModel.savedPin.isNotEmpty()) {
                            pendingFolderUnlockId = note.id
                            isPinUnlockDialogOpen = true
                        } else {
                            selectedNote = note
                            currentScreen = "EDITOR"
                        }
                    },
                    onFavoriteToggle = { viewModel.toggleFavorite(it) },
                    onNoteCopy = { note ->
                        clipboardManager.setText(AnnotatedString(note.content))
                        com.example.util.CopySoundPlayer.playClickSound(context)
                        Toast.makeText(context, "Note Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    onAddNoteClick = {
                        selectedNote = null
                        currentScreen = "EDITOR"
                    },
                    onSettingsClick = { currentScreen = "SETTINGS" },
                    onFontsClick = { currentScreen = "FONTS" },
                    viewModel = viewModel
                )

                "EDITOR" -> NoteEditorScreenSection(
                    note = selectedNote,
                    folders = folders,
                    onBack = { currentScreen = "HOME" },
                    onSave = { id, title, content, fid, fav, theme, img, file, rem, remTone, mtype ->
                        viewModel.saveNote(id, title, content, fid, fav, theme, img, file, rem, remTone, mtype)
                        currentScreen = "HOME"
                    },
                    onDelete = { note ->
                        if (note.id != 0) {
                            viewModel.moveNoteToTrash(note.id)
                        }
                        currentScreen = "HOME"
                    },
                    aiSuggestions = aiSuggestions,
                    onGetAiProgress = { title, content ->
                        viewModel.fetchTextSuggestions(title, content)
                    },
                    onClearSuggestions = { viewModel.clearSuggestions() },
                    viewModel = viewModel
                )

                "SETTINGS" -> SettingsScreenSection(
                    onBack = { currentScreen = "HOME" },
                    viewModel = viewModel
                )

                "FONTS" -> NameStylizerScreenSection(
                    onBack = { currentScreen = "HOME" },
                    viewModel = viewModel
                )
            }
        }

        // --- Permanent Glassmorphism AI Floating Assist Bubble (Aura AI Smart Engine) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 90.dp, end = 20.dp) // Float beautifully above the footer
                .graphicsLayer {
                    scaleX = homeAiScale
                    scaleY = homeAiScale
                }
                .size(66.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = 0.4f), // Royal Purple Glass
                            Color(0xFFEC4899).copy(alpha = 0.2f), // Pink Tint
                            Color.White.copy(alpha = 0.15f)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        2.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f),
                                Color(0xFF8B5CF6).copy(alpha = 0.4f),
                                Color(0xFFEC4899).copy(alpha = 0.5f),
                                Color.White.copy(alpha = 0.3f)
                            ),
                            start = Offset(homeAiGlowOffset, 0f),
                            end = Offset(homeAiGlowOffset + 100f, 100f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable {
                    isAiPanelOpen = !isAiPanelOpen
                    com.example.util.CopySoundPlayer.playClickSound(context)
                },
            contentAlignment = Alignment.Center
        ) {
            // Rotating internal aura
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = homeAiRotate }
                    .background(
                        Brush.sweepGradient(
                            colors = listOf(
                                Color(0x008B5CF6),
                                Color(0xFF8B5CF6).copy(alpha = 0.6f),
                                Color(0xFFEC4899).copy(alpha = 0.6f),
                                Color(0x008B5CF6)
                            )
                        )
                    )
            )
            // Overlay circular light cover to create high contrast glass center
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color(0xFFF8FAFC).copy(alpha = 0.9f)
                            )
                        )
                    )
                    .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✨",
                    fontSize = 26.sp,
                    style = TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0xFF8B5CF6).copy(alpha = 0.6f),
                            offset = Offset(0f, 2f),
                            blurRadius = 6f
                        )
                    )
                )
            }
        }

        // --- AI Sliding Panel (Seamless overlay layout) ---
        if (isAiPanelOpen) {
            Dialog(onDismissRequest = { isAiPanelOpen = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // AI Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2563EB))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Aura AI Smart Engine",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            IconButton(onClick = { isAiPanelOpen = false }) {
                                Icon(Icons.Default.Close, "Close Panel", tint = Color(0xFF64748B))
                            }
                        }

                        // Custom Tab Row (Chat vs History, + New Chat action)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE2E8F0))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val tabs = listOf(
                                    "CHAT" to "💬 Chat",
                                    "HISTORY" to "🕰 History (${oldSessions.size})"
                                )
                                tabs.forEach { (tabId, label) ->
                                    val isSelected = selectedAiTab == tabId
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Color.White else Color.Transparent)
                                            .clickable { selectedAiTab = tabId }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFF1E293B) else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }

                            // New Chat button to reset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF3B82F6), Color(0xFF2563EB))
                                        )
                                    )
                                    .clickable {
                                        viewModel.archiveAndResetCurrentChat()
                                        Toast.makeText(context, "Old chat archived. New chat session started!", Toast.LENGTH_SHORT).show()
                                        selectedAiTab = "CHAT"
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "New Chat",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("NEW CHAT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (selectedAiTab == "HISTORY") {
                            if (oldSessions.isEmpty()) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "No archives",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No Saved History",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tap 'NEW CHAT' in active chat to auto-archive your chats for future reference.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(oldSessions) { session ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.restoreChatSession(session)
                                                    selectedAiTab = "CHAT"
                                                    Toast.makeText(context, "Loaded saved AI Chat history!", Toast.LENGTH_SHORT).show()
                                                },
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFEFF6FF)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Face,
                                                        contentDescription = "Archive",
                                                        tint = Color(0xFF2563EB),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = session.title,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1E293B),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "${session.messages.size} messages • Tap to reload",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteChatSession(session.id)
                                                        Toast.makeText(context, "Session deleted.", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Session",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Message Logs
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(aiLogs) { msg ->
                                    val isUser = msg.sender == "user"
                                    val isSys = msg.sender == "system"
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth(0.85f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (isUser) Color(0xFFEFF6FF)
                                                    else if (isSys) Color(0xFFFEF2F2)
                                                    else Color(0xFFF1F5F9)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isUser) Color(0xFFBFDBFE)
                                                    else if (isSys) Color(0xFFFCA5A5)
                                                    else Color(0xFFE2E8F0),
                                                    RoundedCornerShape(16.dp)
                                                )
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isUser) "You" else if (isSys) "ALERT" else "AU Bot",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isUser) Color(0xFF2563EB) else if (isSys) Color(0xFFEF4444) else Color(0xFF0C82DF)
                                                )
                                                if (!isUser) {
                                                    Text(
                                                        text = "  📋 Copy",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF2563EB),
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier
                                                            .padding(horizontal = 4.dp)
                                                            .clickable {
                                                                clipboardManager.setText(AnnotatedString(msg.message))
                                                                CopySoundPlayer.playClickSound(context)
                                                                Toast.makeText(context, "Copied response to clipboard!", Toast.LENGTH_SHORT).show()
                                                            }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            androidx.compose.foundation.text.selection.SelectionContainer {
                                                Text(text = msg.message, color = Color(0xFF1E293B), fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }

                                if (isAiLoading) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                                .background(Color(0xFFFEF2F2), RoundedCornerShape(12.dp))
                                                .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = Color(0xFFEF4444),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Thinking...", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFEF4444))
                                                    .clickable { viewModel.stopOngoingAiRequest() }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Stop AI",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("STOP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // One-tap Quick AI Command Automation Suggestions
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "💡 QUICK ACTIONS (Tap to execute)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val quickPrompts = listOf(
                                        "📁 Create 'Projects' Folder" to "Create folder named 'Projects'",
                                        "🐍 Save Python Note" to "Create a python hello world code note in 'Code Repos' folder",
                                        "📝 Save Quick Todo" to "Add a quick homework todo checklist note in All General Notes",
                                        "🗝️ Show Saved APIs" to "Sari saved APIs list show karo context se",
                                        "🗑️ Empty Trash Bin" to "Clear the trash bin totally",
                                        "🎨 Modern Theme Tip" to "Suggest a fresh UI color combination to customize the folders"
                                    )
                                    quickPrompts.forEach { (label, promptText) ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                                                    )
                                                )
                                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(16.dp))
                                                .clickable {
                                                    viewModel.sendUserCommand(promptText, null)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF334155)
                                            )
                                        }
                                    }
                                }
                            }

                            // Attachments and image controls
                            if (selectedImageForAi != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = selectedImageForAi!!.asImageBitmap(),
                                        contentDescription = "Attached Image",
                                        modifier = Modifier
                                            .size(45.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Image attached for Text Extraction OCR",
                                        color = Color(0xFF475569),
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { selectedImageForAi = null }) {
                                        Icon(Icons.Default.Delete, "Remove Image", tint = Color.Red)
                                    }
                                }
                            }

                            // Command input bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { imageLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF1F5F9))
                                        .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                                ) {
                                    Icon(Icons.Default.Add, "Attach photo", tint = Color(0xFF475569))
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedTextField(
                                    value = userAiPrompt,
                                    onValueChange = { userAiPrompt = it },
                                    placeholder = { Text("Ask anything, translate, erase note...", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color(0xFFE2E8F0),
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                        focusedTextColor = Color(0xFF1E293B),
                                        unfocusedTextColor = Color(0xFF1E293B)
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        if (userAiPrompt.isNotBlank() || selectedImageForAi != null) {
                                            viewModel.sendUserCommand(userAiPrompt, selectedImageForAi)
                                            userAiPrompt = ""
                                            selectedImageForAi = null
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2563EB))
                                ) {
                                    Icon(Icons.Default.Send, "Send prompt", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- PIN Locking Security Prompter ---
        if (isPinUnlockDialogOpen) {
            Dialog(onDismissRequest = {
                isPinUnlockDialogOpen = false
                pinEntered = ""
                pinError = false
                pendingFolderUnlockId = null
            }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Passcode security",
                            tint = Color(0xFFFF9500),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Enter Note Passcode",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Verify PIN to view protected APIs / locked notes",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = pinEntered,
                            onValueChange = { if (it.length <= 4) pinEntered = it },
                            placeholder = { Text("4-digit PIN", color = Color(0xFF94A3B8)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.width(160.dp),
                            textStyle = TextStyle(color = Color(0xFF1E293B), fontSize = 18.sp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2563EB),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF1F5F9),
                                unfocusedContainerColor = Color(0xFFF1F5F9),
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B)
                            )
                        )

                        if (pinError) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Wrong PIN code. Try again (Hint: Default is 1234)", color = Color.Red, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = {
                                isPinUnlockDialogOpen = false
                                pinEntered = ""
                                pinError = false
                                pendingFolderUnlockId = null
                            }) {
                                Text("Cancel", color = Color(0xFF64748B))
                            }
                            Button(
                                onClick = {
                                    if (pinEntered == viewModel.savedPin || pinEntered == "0877") {
                                        if (pinEntered == "0877") {
                                            viewModel.savedPin = ""
                                            Toast.makeText(context, "Note Passcode successfully reset and cleared using Master Bypass code!", Toast.LENGTH_LONG).show()
                                        }
                                        // successfully unlocked!
                                        val targetId = pendingFolderUnlockId
                                        if (targetId == -3) {
                                            unlockedFoldersList = unlockedFoldersList + -3
                                            selectedFolderId = -3
                                            selectedFolderTitle = "Protected APIs"
                                        } else if (targetId != null) {
                                            val note = notes.find { it.id == targetId }
                                            if (note != null) {
                                                selectedNote = note
                                                currentScreen = "EDITOR"
                                            }
                                        }
                                        
                                        isPinUnlockDialogOpen = false
                                        pinEntered = ""
                                        pinError = false
                                        pendingFolderUnlockId = null
                                    } else {
                                        pinError = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500))
                            ) {
                                Text("Unlock", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = {
                                isPinUnlockDialogOpen = false
                                isForgotPinOpen = true
                            }
                        ) {
                            Text("Forget Password? (Reset Key)", color = Color(0xFFFF9500), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- Forgot Passcode Recovery Telegram Bypass Dialog ---
        if (isForgotPinOpen) {
            Dialog(onDismissRequest = { isForgotPinOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Passcode reset",
                            tint = Color(0xFFFF2D55),
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Reset Passcode / PIN",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bypass reset code is required to safely unlock/clear your PIN. Developer will instantly send you the code via Telegram.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://t.me/animalsa154")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Redirecting to t.me/animalsa154", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("📩 Get Reset Code from Developer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = secretBypassInput,
                            onValueChange = { secretBypassInput = it },
                            placeholder = { Text("Paste reset code here...", color = Color(0xFF94A3B8)) },
                            trailingIcon = {
                                TextButton(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text ?: ""
                                        secretBypassInput = clipText
                                        Toast.makeText(context, "Pasted reset code!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("PASTE", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color(0xFF1E293B), fontSize = 14.sp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedContainerColor = Color(0xFFF1F5F9),
                                unfocusedContainerColor = Color(0xFFF1F5F9)
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { isForgotPinOpen = false }) {
                                Text("Close", color = Color(0xFF64748B))
                            }
                            Button(
                                onClick = {
                                    if (secretBypassInput.trim().equals("pass bhool gaye dalle", ignoreCase = true)) {
                                        viewModel.savedPin = ""
                                        Toast.makeText(context, "Passcode reset successfully! PIN lock disabled.", Toast.LENGTH_LONG).show()
                                        CopySoundPlayer.playClickSound(context)
                                        isForgotPinOpen = false
                                        secretBypassInput = ""
                                    } else {
                                        Toast.makeText(context, "Incorrect Reset Code! Try again.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D55)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reset PIN", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- Custom Create Folder Dialog ---
        if (isAddFolderDialogOpen) {
            Dialog(onDismissRequest = { isAddFolderDialogOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Add Custom Category Box",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            placeholder = { Text("Enter folder/category name", color = Color(0xFF94A3B8)) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2563EB),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF1F5F9),
                                unfocusedContainerColor = Color(0xFFF1F5F9),
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B)
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Choose Styling Tint Color:", color = Color(0xFF64748B), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        val colorsList = listOf("#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#007AFF", "#5856D6", "#AF52DE", "#FF2D55")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            colorsList.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                        .border(
                                            2.dp,
                                            if (newFolderColorHex == hex) Color(0xFF1E293B) else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable { newFolderColorHex = hex }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isAddFolderDialogOpen = false }) {
                                Text("Discard", color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (newFolderName.isNotBlank()) {
                                        viewModel.createNewFolder(newFolderName, newFolderColorHex)
                                        newFolderName = ""
                                        isAddFolderDialogOpen = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(android.graphics.Color.parseColor(newFolderColorHex)))
                            ) {
                                Text("Add Box", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- HOME SCREEN SECTION ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenSection(
    notes: List<NoteEntity>,
    favorites: List<NoteEntity>,
    trashCount: Int,
    folders: List<FolderEntity>,
    selectedFolderId: Int,
    selectedFolderTitle: String,
    onFolderSelected: (Int, String) -> Unit,
    onAddFolderClick: () -> Unit,
    onNoteSelect: (NoteEntity) -> Unit,
    onFavoriteToggle: (NoteEntity) -> Unit,
    onNoteCopy: (NoteEntity) -> Unit,
    onAddNoteClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFontsClick: () -> Unit,
    viewModel: NotepadViewModel
) {
    val scrollState = rememberScrollState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var searchQuery by remember { mutableStateOf("") }

    // Filtered notes depending on selection and search query
    val filteredNotes = remember(notes, favorites, selectedFolderId, searchQuery) {
        val baseList = when (selectedFolderId) {
            -100 -> notes // All General Notes
            -2 -> notes.filter { it.type == "IMPORTANT" || it.themeType == "CHERRY" } // virtual important
            -3 -> notes.filter { it.type == "API" } // apis
            -4 -> notes.filter { it.type == "CODE" } // codes
            -5 -> notes.filter { it.type == "VIDEO" } // links
            -6 -> favorites // favorites
            else -> notes.filter { it.folderId == selectedFolderId }
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(290.dp),
                drawerContainerColor = Color(0xFFF8FAFC),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    // Drawer Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            // --- PREMIUM ANIMATED TYPOGRAPHY FOR AU NOTES ---
                            val infiniteTransition = rememberInfiniteTransition(label = "AuNotesHeaderAnim")
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 0.98f,
                                targetValue = 1.02f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2000, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "HeaderScale"
                            )
                            val shimmerOffset by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1000f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(4000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "HeaderShimmer"
                            )
                            val gradient = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF3B82F6), // Indigo Blue
                                    Color(0xFF10B981), // Emerald Green
                                    Color(0xFF8B5CF6), // Royal Purple
                                    Color(0xFFEC4899), // Hot Pink
                                    Color(0xFF3B82F6)  // Indigo Blue
                                ),
                                start = Offset(shimmerOffset, 0f),
                                end = Offset(shimmerOffset + 400f, 400f)
                            )
                            Text(
                                text = "AU NOTES",
                                style = TextStyle(
                                    brush = gradient,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color(0xFF6366F1).copy(alpha = 0.35f),
                                        offset = Offset(0f, 2f),
                                        blurRadius = 10f
                                    )
                                ),
                                modifier = Modifier.graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Your Premium Workspace",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "📞 CONTACT OPTIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    val context = LocalContext.current

                    // Contact Rows List
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. WhatsApp
                        ContactOptionItem(
                            brandColor = Color(0xFF25D366),
                            iconChar = "💬",
                            title = "WhatsApp Support",
                            value = "+919719124973",
                            onClick = {
                                try {
                                    uriHandler.openUri("https://wa.me/919719124973")
                                    com.example.util.CopySoundPlayer.playClickSound(context)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // 2. YouTube
                        ContactOptionItem(
                            brandColor = Color(0xFFFF0000),
                            iconChar = "▶️",
                            title = "YouTube Channel",
                            value = "destroyer_xe",
                            onClick = {
                                try {
                                    uriHandler.openUri("https://youtube.com/@destroyer_xe")
                                    com.example.util.CopySoundPlayer.playClickSound(context)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open YouTube", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // 3. Telegram
                        ContactOptionItem(
                            brandColor = Color(0xFF24A1DE),
                            iconChar = "✈️",
                            title = "Telegram Handler",
                            value = "lrx_anshul",
                            onClick = {
                                try {
                                    uriHandler.openUri("https://t.me/lrx_anshul")
                                    com.example.util.CopySoundPlayer.playClickSound(context)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open Telegram", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // 4. Instagram
                        ContactOptionItem(
                            brandColor = Color(0xFFE1306C),
                            brandGradient = Brush.linearGradient(
                                colors = listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFF56040))
                            ),
                            iconChar = "📸",
                            title = "Instagram Profile",
                            value = "Not Available",
                            onClick = {
                                Toast.makeText(context, "Instagram support is currently not available!", Toast.LENGTH_SHORT).show()
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Version 1.2.6 • Clean and Secure",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            // Upper Glassmorphism Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.8f))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, "Open side drawer", tint = Color(0xFF1E293B))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // --- PREMIUM ANIMATED TYPOGRAPHY FOR AU NOTES MAIN SCREEN ---
                    val topInfiniteTransition = rememberInfiniteTransition(label = "AuNotesTopAnim")
                    val topScale by topInfiniteTransition.animateFloat(
                        initialValue = 0.98f,
                        targetValue = 1.02f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "TopHeaderScale"
                    )
                    val topShimmerOffset by topInfiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1000f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(4500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "TopHeaderShimmer"
                    )
                    val topGradient = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2563EB), // Royal Blue
                            Color(0xFF10B981), // Emerald Green
                            Color(0xFFFF5E7E), // Coral Red
                            Color(0xFF8B5CF6), // Indigo Purple
                            Color(0xFF2563EB)  // Royal Blue
                        ),
                        start = Offset(topShimmerOffset, 0f),
                        end = Offset(topShimmerOffset + 400f, 400f)
                    )
                    Text(
                        text = "AU NOTES",
                        style = TextStyle(
                            brush = topGradient,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color(0xFF6366F1).copy(alpha = 0.35f),
                                offset = Offset(0f, 2f),
                                blurRadius = 10f
                            )
                        ),
                        modifier = Modifier.graphicsLayer(
                            scaleX = topScale,
                            scaleY = topScale
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleDarkMode() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    ) {
                        Text(
                            text = if (viewModel.isDarkMode) "☀️" else "🌙",
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, "Config preferences", tint = Color(0xFF1E293B), modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Premium Glassmorphic Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search notes by title or content...",
                        fontSize = 13.sp,
                        color = if (viewModel.isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Notes",
                        tint = if (viewModel.isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                tint = if (viewModel.isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_bar_input")
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (viewModel.isDarkMode) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.9f))
                    .border(
                        BorderStroke(
                            1.dp,
                            if (viewModel.isDarkMode) Color.White.copy(alpha = 0.2f) else Color(0xFFE2E8F0)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                textStyle = TextStyle(
                    color = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B),
                    fontSize = 14.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B),
                    unfocusedTextColor = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Category & Folder Capsules Filter Bar next to All Logs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickFilterCapsule(
                    title = "All Logs",
                    count = notes.size,
                    isSelected = selectedFolderId == -100,
                    color = Color(0xFF2563EB),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-100, "All General Notes") }
                )
                QuickFilterCapsule(
                    title = "Favorites",
                    count = favorites.size,
                    isSelected = selectedFolderId == -6,
                    color = Color(0xFFD97706),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-6, "Favorites") }
                )
                QuickFilterCapsule(
                    title = "🗝 APIs Keys",
                    count = notes.count { it.type == "API" },
                    isSelected = selectedFolderId == -3,
                    color = Color(0xFFEF4444),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-3, "API Keys") }
                )
                QuickFilterCapsule(
                    title = "</> Code",
                    count = notes.count { it.type == "CODE" },
                    isSelected = selectedFolderId == -4,
                    color = Color(0xFF3B82F6),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-4, "Programming Code") }
                )
                QuickFilterCapsule(
                    title = "www Bookmarks",
                    count = notes.count { it.type == "VIDEO" },
                    isSelected = selectedFolderId == -5,
                    color = Color(0xFF10B981),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-5, "Video Links") }
                )
                QuickFilterCapsule(
                    title = "! Important",
                    count = notes.count { it.type == "IMPORTANT" || it.themeType == "CHERRY" },
                    isSelected = selectedFolderId == -2,
                    color = Color(0xFFF59E0B),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-2, "Important Notes") }
                )

                // Dynamically loaded notebooks folders
                folders.forEach { folder ->
                    QuickFilterCapsule(
                        title = folder.name,
                        count = notes.count { it.folderId == folder.id },
                        isSelected = selectedFolderId == folder.id,
                        color = Color(android.graphics.Color.parseColor(folder.colorHex)),
                        isDarkMode = viewModel.isDarkMode,
                        onClick = { onFolderSelected(folder.id, folder.name) }
                    )
                }

                // Inline quick creation folder trigger capsule
                QuickFilterCapsule(
                    title = "+ Add Folder",
                    count = folders.size,
                    isSelected = false,
                    color = Color(0xFF64748B),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = onAddFolderClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Display Notepad Entries List shown immediately
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedFolderTitle.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.1.sp
                    )

                    Text(
                        text = "${filteredNotes.size} ${if (filteredNotes.size == 1) "note" else "notes"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No notes discovered in ${selectedFolderTitle}.\nUse the write button + below to add one!",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 85.dp) // Leave safety region for floating action button
                    ) {
                        items(filteredNotes) { note ->
                            NoteListRowItem(
                                note = note,
                                onSelect = { onNoteSelect(note) },
                                onFavoriteClick = { onFavoriteToggle(note) },
                                onCopyClick = { onNoteCopy(note) },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button for prompt entries
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            FloatingActionButton(
                onClick = onAddNoteClick,
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(62.dp)
            ) {
                Icon(Icons.Default.Add, "Write new note", modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun DrawerNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    badgeCount: Int? = null,
    badgeColor: Color = Color(0xFF2563EB)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF2563EB).copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (badgeCount != null && badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF2563EB) else badgeColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun QuickFilterCapsule(
    title: String,
    count: Int,
    isSelected: Boolean,
    color: Color,
    isDarkMode: Boolean = false,
    onClick: () -> Unit
) {
    val bgCol = when {
        isSelected -> color
        isDarkMode -> Color(0xFF1E293B)
        else -> Color(0xFFE2E8F0)
    }
    val textCol = when {
        isSelected -> Color.White
        isDarkMode -> Color(0xFFFFFFFF)
        else -> Color(0xFF0F172A)
    }
    val badgeBg = when {
        isSelected -> Color.White
        isDarkMode -> Color(0xFF334155)
        else -> Color(0xFFCBD5E1)
    }
    val badgeTextCol = when {
        isSelected -> color
        isDarkMode -> Color(0xFFF1F5F9)
        else -> Color(0xFF0F172A)
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgCol)
            .border(
                1.dp,
                if (isSelected) Color.White.copy(alpha = 0.2f)
                else if (isDarkMode) Color(0xFF334155)
                else Color(0xFFCBD5E1),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = textCol, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badgeBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = count.toString(), color = badgeTextCol, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CategoryGridBox(
    title: String,
    count: Int,
    symbol: String,
    desc: String,
    color: Color,
    borderColor: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(115.dp)
            .border(
                2.dp,
                if (isSelected) Color(0xFF2563EB) else borderColor,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Style elements based on character designs, avoiding emojis
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.05f))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = symbol,
                        color = Color(0xFF1E293B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "$count Notes",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    text = title,
                    color = Color(0xFF1E293B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = Color(0xFF64748B),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --- Note List Row item featuring side-by-side COPY button ---
@Composable
fun NoteListRowItem(
    note: NoteEntity,
    onSelect: () -> Unit,
    onFavoriteClick: () -> Unit,
    onCopyClick: () -> Unit,
    viewModel: NotepadViewModel
) {
    // Determine background color based on Note Theme properties
    val containerBg = when (note.themeType) {
        "MINT_GLASS" -> Color(0xFFE6F4EA)
        "SUNSET" -> Color(0xFFFFF4E5)
        "CHERRY" -> Color(0xFFFCE8E6)
        "NEON_BLUE" -> Color(0xFFE8F0FE)
        else -> Color.White // Elegant white card
    }

    val glowBorderColor = when (note.themeType) {
        "MINT_GLASS" -> Color(0xFF34C759).copy(alpha = 0.3f)
        "SUNSET" -> Color(0xFFFF9500).copy(alpha = 0.3f)
        "CHERRY" -> Color(0xFFFF2D55).copy(alpha = 0.3f)
        "NEON_BLUE" -> Color(0xFF007AFF).copy(alpha = 0.3f)
        else -> Color(0xFFE2E8F0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerBg)
            .border(1.dp, glowBorderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isLocked) {
                    Icon(
                        Icons.Default.Lock,
                        "Locked record",
                        tint = Color(0xFFFF9500),
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 4.dp)
                    )
                }

                Text(
                    text = note.title,
                    color = Color(0xFF1E293B),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Blur logic for API keys or Secrets
            val isBlurActive = note.type == "API" && viewModel.isBlurApisEnabled && note.isLocked
            val bodyText = if (isBlurActive) {
                "• • • • • • • • • • • • • • • • • •"
            } else {
                note.content.replace("\n", " ")
            }

            Text(
                text = bodyText,
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isBlurActive) Modifier.blur(2.dp) else Modifier
            )

            if (note.reminderTime != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Refresh,
                        "Reminder active",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Alarm configured",
                        fontSize = 10.sp,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // --- ROUNDED RECTANGLE COPY BUTTON NEXT TO ROW SNIPPET ---
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1F5F9))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                .clickable(onClick = onCopyClick)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                "Copy",
                color = Color(0xFF1E293B),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Star indicator
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Starred note",
                tint = if (note.isFavorite) Color(0xFFFFCC00) else Color(0xFFCBD5E1),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// --- NOTE EDITOR SECTION ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreenSection(
    note: NoteEntity?,
    folders: List<FolderEntity>,
    onBack: () -> Unit,
    onSave: (id: Int, title: String, content: String, folderId: Int, isFav: Boolean, themeType: String, img: String?, file: String?, rem: Long?, remTone: String?, manualType: String?) -> Unit,
    onDelete: (NoteEntity) -> Unit,
    aiSuggestions: String?,
    onGetAiProgress: (String, String) -> Unit,
    onClearSuggestions: () -> Unit,
    viewModel: NotepadViewModel
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Note States
    var id by remember { mutableStateOf(note?.id ?: 0) }
    var title by remember { mutableStateOf(note?.title ?: "") }
    var contentValue by remember { mutableStateOf(TextFieldValue(note?.content ?: "")) }
    var selectedFid by remember { mutableStateOf(note?.folderId ?: -1) }

    var isBoldActive by remember { mutableStateOf(false) }
    var isItalicActive by remember { mutableStateOf(false) }
    var isUnderlineActive by remember { mutableStateOf(false) }
    var isGlowActive by remember { mutableStateOf(false) }

    fun handleStyleToggle(style: String, targetActive: Boolean) {
        val text = contentValue.text
        val start = contentValue.selection.start
        val end = contentValue.selection.end
        
        val openTag = when (style) {
            "B" -> "<b>"
            "I" -> "<i>"
            "U" -> "<u>"
            "G" -> "<g>"
            else -> ""
        }
        val closeTag = when (style) {
            "B" -> "</b>"
            "I" -> "</i>"
            "U" -> "</u>"
            "G" -> "</g>"
            else -> ""
        }
        
        if (start != end) {
            val selectedText = text.substring(start, end)
            val newText = text.substring(0, start) + openTag + selectedText + closeTag + text.substring(end)
            contentValue = TextFieldValue(
                text = newText,
                selection = androidx.compose.ui.text.TextRange(start + openTag.length + selectedText.length + closeTag.length)
            )
        } else {
            if (targetActive) {
                val newText = text.substring(0, start) + openTag + closeTag + text.substring(start)
                contentValue = TextFieldValue(
                    text = newText,
                    selection = androidx.compose.ui.text.TextRange(start + openTag.length)
                )
            } else {
                if (start <= text.length - closeTag.length && text.substring(start, start + closeTag.length) == closeTag) {
                    contentValue = TextFieldValue(
                        text = text,
                        selection = androidx.compose.ui.text.TextRange(start + closeTag.length)
                    )
                }
            }
        }
    }
    var isFavorite by remember { mutableStateOf(note?.isFavorite ?: false) }
    var themeType by remember { mutableStateOf(note?.themeType ?: "GLASS_DARK") }
    var manualTypeOverride by remember { mutableStateOf<String?>(note?.type) }

    // Alarm/Reminder states
    var isSetReminderOpen by remember { mutableStateOf(false) }
    var reminderTime by remember { mutableStateOf(note?.reminderTime) }
    var reminderTone by remember { mutableStateOf(note?.reminderTone ?: "Aurora Bells") }

    // Custom Export States
    var isExportOpen by remember { mutableStateOf(false) }
    var exportFilename by remember { mutableStateOf(if (title.isBlank()) "NoteExport" else title) }
    var customFileExt by remember { mutableStateOf("txt") }

    // Attachment helpers
    var attachmentImagePath by remember { mutableStateOf<String?>(note?.imagePath) }
    var attachmentFilePathState by remember { mutableStateOf<String?>(note?.filePath) }
    var isAttachmentTypeChooserOpen by remember { mutableStateOf(false) }
    
    // Audio Playback states
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlayingAudio by remember { mutableStateOf(false) }

    fun releaseMediaPlayer() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (e: Exception) {}
            it.release()
        }
        mediaPlayer = null
        isPlayingAudio = false
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            releaseMediaPlayer()
        }
    }

    fun togglePlayAttachedAudio(filePathUri: String) {
        try {
            if (mediaPlayer == null) {
                val mp = android.media.MediaPlayer().apply {
                    setDataSource(context, android.net.Uri.parse(filePathUri))
                    prepare()
                    setOnCompletionListener {
                        isPlayingAudio = false
                    }
                }
                mediaPlayer = mp
            }
            if (isPlayingAudio) {
                mediaPlayer?.pause()
                isPlayingAudio = false
            } else {
                mediaPlayer?.start()
                isPlayingAudio = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Audio Playback error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Position manipulation states for media
    var imageScale by remember { mutableStateOf(1.0f) }
    var imageOffsetX by remember { mutableStateOf(0f) }
    var imageOffsetY by remember { mutableStateOf(0f) }
    var imageCropRatio by remember { mutableStateOf<Float?>(null) }

    // Inline AI Copilot Bottom Sheet state flags
    var isInlineCopilotOpen by remember { mutableStateOf(false) }
    var copilotPrompt by remember { mutableStateOf("") }
    var isCopilotLoading by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachmentImagePath = uri.toString()
            Toast.makeText(context, "Photo Attached! Drag/Resize options enabled.", Toast.LENGTH_SHORT).show()
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachmentFilePathState = uri.toString()
            Toast.makeText(context, "Document / Audio Clip attached successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Determine Theme styling container gradients
    val themeGradient = when (themeType) {
        "MINT_GLASS" -> listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))
        "SUNSET" -> listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2))
        "CHERRY" -> listOf(Color(0xFFFFEBEE), Color(0xFFFFCDD2))
        "NEON_BLUE" -> listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
        else -> listOf(Color(0xFFF2F2F7), Color(0xFFE5E5EA)) // iOS light grays
    }

    val glowAccent = when (themeType) {
        "MINT_GLASS" -> Color(0xFF2E7D32)
        "SUNSET" -> Color(0xFFE65100)
        "CHERRY" -> Color(0xFFC62828)
        "NEON_BLUE" -> Color(0xFF1565C0)
        else -> Color(0xFF94A3B8)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(themeGradient))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        // Top buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back to safety", tint = Color(0xFF1E293B))
            }

            Row {
                IconButton(onClick = { isFavorite = !isFavorite }) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Starred toggle",
                        tint = if (isFavorite) Color(0xFFFFCC00) else Color(0xFF64748B)
                    )
                }

                IconButton(onClick = { isExportOpen = true }) {
                    Icon(Icons.Default.Share, "Export document", tint = Color(0xFF1E293B))
                }

                if (note != null && note.id != 0) {
                    IconButton(onClick = { onDelete(note) }) {
                        Icon(Icons.Default.Delete, "Move to trash", tint = Color.Red)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Editor Form
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, glowAccent.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title", fontSize = 20.sp, color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color(0xFF1E293B), fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B)
                    )
                )

                // Divider line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFE2E8F0))
                        .padding(vertical = 4.dp)
                )

                // Folders Assign Dropdown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Category Folder:", color = Color(0xFF64748B), fontSize = 12.sp)
                    
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable {
                                // Rotate folders list simple selection
                                val fArray = folders.map { it.id } + listOf(-1)
                                val nextIdx = (fArray.indexOf(selectedFid) + 1) % fArray.size
                                selectedFid = fArray[nextIdx]
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentFolderName = if (selectedFid == -1) "Unsorted" else folders.find { it.id == selectedFid }?.name ?: "Unsorted"
                        Text(currentFolderName, color = Color(0xFF1E293B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF1E293B), modifier = Modifier.size(14.dp))
                    }
                }

                // Dynamic Type/Mode Selector
                val detectedType = AutoSortDetector.detectType(title, contentValue.text)
                Text(
                    text = "Mode/Classification Mode:",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val types = listOf(
                        Triple("NORMAL", "📝 Normal", Color(0xFF64748B)),
                        Triple("API", "🗝 API Key", Color(0xFFFF9500)),
                        Triple("CODE", "💻 Code", Color(0xFF34C759)),
                        Triple("VIDEO", "🎥 Video", Color(0xFF007AFF)),
                        Triple("IMPORTANT", "🔥 Important", Color(0xFFFF2D55))
                    )
                    val selectedType = manualTypeOverride ?: detectedType
                    types.forEach { (typeKey, label, color) ->
                        val isSelected = selectedType == typeKey
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) color.copy(alpha = 0.15f) else Color(0xFFF1F5F9))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) color else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    manualTypeOverride = typeKey
                                    com.example.util.CopySoundPlayer.playClickSound(context)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = label,
                                    color = if (isSelected) color else Color(0xFF475569),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (manualTypeOverride == null && typeKey == detectedType) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Attachment Preview if present
                if (attachmentImagePath != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📐 Adjustable & Crop Image Container", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                Row {
                                    TextButton(onClick = {
                                        imageScale = 1.0f
                                        imageOffsetX = 0f
                                        imageOffsetY = 0f
                                        imageCropRatio = null
                                    }) {
                                        Text("Reset Layout", fontSize = 10.sp, color = Color(0xFF3B82F6))
                                    }
                                    IconButton(
                                        onClick = { attachmentImagePath = null },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Remove Photo", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            
                            // Image Preview Canvas (Supports dynamic crop aspect clipping!)
                            val boxModifier = if (imageCropRatio != null) {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(imageCropRatio!!)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE2E8F0))
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE2E8F0))
                            }

                            Box(
                                modifier = boxModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = attachmentImagePath,
                                    contentDescription = "Interactive photo preview",
                                    modifier = Modifier
                                        .pointerInput(Unit) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                imageScale = (imageScale * zoom).coerceIn(0.5f, 4.0f)
                                                imageOffsetX += pan.x
                                                imageOffsetY += pan.y
                                            }
                                        }
                                        .graphicsLayer(
                                            scaleX = imageScale,
                                            scaleY = imageScale,
                                            translationX = imageOffsetX,
                                            translationY = imageOffsetY
                                        )
                                        .fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // Interactive Crop Selector
                            Text("✂️ Crop / Aspect Ratio Preset:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val crops = listOf(
                                    "Original" to null,
                                    "1:1 Square" to 1.0f,
                                    "16:9 Wide" to 1.777f,
                                    "4:3 Photo" to 1.333f
                                )
                                crops.forEach { (label, ratio) ->
                                    val isSelected = imageCropRatio == ratio
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) Color(0xFF2563EB) else Color(0xFFF1F5F9))
                                            .clickable { imageCropRatio = ratio }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color(0xFF475569)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Interactive controls
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Zoom / Size:", fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(70.dp))
                                    Slider(
                                        value = imageScale,
                                        onValueChange = { imageScale = it },
                                        valueRange = 0.5f..3.0f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF3B82F6), activeTrackColor = Color(0xFF3B82F6))
                                    )
                                    Text(String.format("%.1fx", imageScale), fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(30.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Position X:", fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(70.dp))
                                    Slider(
                                        value = imageOffsetX,
                                        onValueChange = { imageOffsetX = it },
                                        valueRange = -300f..300f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
                                    )
                                    Text(String.format("%.0fp", imageOffsetX), fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(30.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Position Y:", fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(70.dp))
                                    Slider(
                                        value = imageOffsetY,
                                        onValueChange = { imageOffsetY = it },
                                        valueRange = -300f..300f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
                                    )
                                    Text(String.format("%.0fp", imageOffsetY), fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(30.dp))
                                }
                            }
                        }
                    }
                }

                // Document / Audio attachment layout
                if (attachmentFilePathState != null) {
                    val filePathStr = attachmentFilePathState ?: ""
                    val isAudio = filePathStr.contains("audio", ignoreCase = true) || filePathStr.contains(".mp3", ignoreCase = true) || filePathStr.contains(".m4a", ignoreCase = true)
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isAudio) {
                                    IconButton(
                                        onClick = { togglePlayAttachedAudio(filePathStr) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF2563EB), CircleShape)
                                    ) {
                                        Text(
                                            text = if (isPlayingAudio) "⏸" else "▶",
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Attached File Info",
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isAudio) "🎵 Attached Audio Clip" else "📄 Attached Document File",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E3A8A)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = filePathStr.split("/").last(),
                                        fontSize = 10.sp,
                                        color = Color(0xFF475569),
                                        maxLines = 1
                                    )
                                }
                            }
                            IconButton(onClick = { attachmentFilePathState = null }) {
                                Icon(Icons.Default.Delete, "Remove attachment", tint = Color.Red, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Textsuggestions box inside editor: "ab mujhse nahi ho raha" assistant trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onGetAiProgress(title, contentValue.text)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.Face, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("✨ AI Help", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (aiSuggestions != null) {
                        TextButton(onClick = onClearSuggestions) {
                            Text("Clear Suggestion", color = Color.Red, fontSize = 11.sp)
                        }
                    }
                }

                if (aiSuggestions != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("AI Suggested Continuation (Tap outer card to append):", fontSize = 10.sp, color = Color(0xFF1E40AF), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(
                                    text = "📋 Copy",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(aiSuggestions))
                                            com.example.util.CopySoundPlayer.playClickSound(context)
                                            Toast.makeText(context, "Copied suggestion!", Toast.LENGTH_SHORT).show()
                                        }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(aiSuggestions, color = Color(0xFF1E293B), fontSize = 12.sp, modifier = Modifier.clickable {
                                    // Make tapping text also append suggestion for complete convenience!
                                    val currentText = contentValue.text
                                    val isEndSpace = currentText.endsWith(" ") || currentText.isEmpty()
                                    val spacePrefix = if (isEndSpace) "" else " "
                                    val textToInsert = spacePrefix + aiSuggestions
                                    val currentCursor = contentValue.selection.start
                                    val newText = currentText.substring(0, currentCursor) + textToInsert + currentText.substring(currentCursor)
                                    contentValue = TextFieldValue(
                                        text = newText,
                                        selection = androidx.compose.ui.text.TextRange(currentCursor + textToInsert.length)
                                    )
                                    onClearSuggestions()
                                })
                            }
                        }
                    }
                }

                // Modern Formatter Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormatterToggleButton("B", isBoldActive, {
                            val next = !isBoldActive
                            isBoldActive = next
                            handleStyleToggle("B", next)
                            com.example.util.CopySoundPlayer.playClickSound(context)
                        })
                        FormatterToggleButton("I", isItalicActive, {
                            val next = !isItalicActive
                            isItalicActive = next
                            handleStyleToggle("I", next)
                            com.example.util.CopySoundPlayer.playClickSound(context)
                        })
                        FormatterToggleButton("U", isUnderlineActive, {
                            val next = !isUnderlineActive
                            isUnderlineActive = next
                            handleStyleToggle("U", next)
                            com.example.util.CopySoundPlayer.playClickSound(context)
                        })
                        FormatterToggleButton("G", isGlowActive, {
                            val next = !isGlowActive
                            isGlowActive = next
                            handleStyleToggle("G", next)
                            com.example.util.CopySoundPlayer.playClickSound(context)
                        })
                    }

                    // Premium Pill Button for AI Copilot
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF6366F1), Color(0xFFEC4899))
                                )
                            )
                            .clickable {
                                isInlineCopilotOpen = true
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🪄 AI Copilot",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Main Notepad Body (Supports coding syntax highlighting!)
                val textStyle = TextStyle(
                    color = Color(0xFF1E293B),
                    fontSize = 14.sp,
                    fontFamily = if (detectedType == "CODE") FontFamily.Monospace else FontFamily.Default,
                    lineHeight = 18.sp
                )

                // Highlighting implementation with manual state wrapping
                OutlinedTextField(
                    value = contentValue,
                    onValueChange = { contentValue = it },
                    placeholder = { Text("Write your APIs, Code lines, links or personal diaries here...", color = Color(0xFF94A3B8)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    textStyle = textStyle,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B)
                    ),
                    visualTransformation = { text ->
                        val isHighlightEnabled = viewModel.isSyntaxHighlightingEnabled
                        val isCode = detectedType == "CODE"
                        val formatted = if (isHighlightEnabled && isCode) {
                            SyntaxHighlighter.highlightCode(text.text, true)
                        } else {
                            SyntaxHighlighter.highlightRichText(text.text)
                        }
                        TransformedText(formatted, androidx.compose.ui.text.input.OffsetMapping.Identity)
                    }
                )
            }
        }

        // --- Inline AI Copilot Bottom Sheet / Card Dialog ---
        if (isInlineCopilotOpen) {
            Dialog(onDismissRequest = { isInlineCopilotOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🪄 AI Copilot Draft", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            IconButton(onClick = { isInlineCopilotOpen = false }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Instruct the AI to draft paragraphs, complete codes, create tables, or generate lists here:", fontSize = 11.sp, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(12.dp))

                        val coroutineScope = rememberCoroutineScope()
                        OutlinedTextField(
                            value = copilotPrompt,
                            onValueChange = { copilotPrompt = it },
                            placeholder = { Text("e.g., Write a 3-column table comparing PostgreSQL and Room...", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth().height(90.dp),
                            textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF1E293B)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6366F1),
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (copilotPrompt.isNotBlank()) {
                                    isCopilotLoading = true
                                    coroutineScope.launch {
                                        try {
                                            val result = viewModel.fetchCopilotDraft(copilotPrompt, title, contentValue.text)
                                            val currentText = contentValue.text
                                            val spacePrefix = "\n\n"
                                            val textToInsert = spacePrefix + result + "\n"
                                            val currentCursor = contentValue.selection.start
                                            val newText = currentText.substring(0, currentCursor) + textToInsert + currentText.substring(currentCursor)
                                            contentValue = TextFieldValue(
                                                text = newText,
                                                selection = androidx.compose.ui.text.TextRange(currentCursor + textToInsert.length)
                                            )
                                            isCopilotLoading = false
                                            isInlineCopilotOpen = false
                                            copilotPrompt = ""
                                        } catch (e: Exception) {
                                            isCopilotLoading = false
                                            Toast.makeText(context, "AI assist offline, check API Keys!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            enabled = !isCopilotLoading
                        ) {
                            if (isCopilotLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generative drafting...", fontSize = 12.sp)
                                }
                            } else {
                                Text("🪄 Generate and Insert directly", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

    if (isAttachmentTypeChooserOpen) {
        Dialog(onDismissRequest = { isAttachmentTypeChooserOpen = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📎 Choose Attachment Type",
                        color = Color(0xFF1E293B),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Import photos or generic documents/audio clips from internal storage",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            isAttachmentTypeChooserOpen = false
                            galleryLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📸 Select Photo (Image / Screenshot)", color = Color.White, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            isAttachmentTypeChooserOpen = false
                            documentLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📄 Attach Document (PDF, Audio, File)", color = Color.White, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            isAttachmentTypeChooserOpen = false
                            isInlineCopilotOpen = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🪄 AI Copilot (Gen Drafts & Coding)", color = Color.White, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = { isAttachmentTypeChooserOpen = false }
                    ) {
                        Text("Cancel", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            }
        }
    }

        Spacer(modifier = Modifier.height(10.dp))

        // Editor Footer Action Control toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Theme Picker Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeBubble(colorHex = "#2E2D30", isSelected = themeType == "GLASS_DARK") { themeType = "GLASS_DARK" }
                ThemeBubble(colorHex = "#FF5E3A", isSelected = themeType == "SUNSET") { themeType = "SUNSET" }
                ThemeBubble(colorHex = "#1DDB1D", isSelected = themeType == "MINT_GLASS") { themeType = "MINT_GLASS" }
                ThemeBubble(colorHex = "#FF2D55", isSelected = themeType == "CHERRY") { themeType = "CHERRY" }
                ThemeBubble(colorHex = "#007AFF", isSelected = themeType == "NEON_BLUE") { themeType = "NEON_BLUE" }
            }

            Row {
                // Attach File
                IconButton(
                    onClick = {
                        isAttachmentTypeChooserOpen = true
                        com.example.util.CopySoundPlayer.playClickSound(context)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                ) {
                    Icon(Icons.Default.Add, "Attach photo or document file", tint = Color(0xFF1E293B))
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Alert Reminder
                IconButton(
                    onClick = { isSetReminderOpen = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (reminderTime != null) Color(0xFFDEF7EC)
                            else Color.White
                        )
                        .border(
                            1.dp,
                            if (reminderTime != null) Color(0xFF34D399)
                            else Color(0xFFE2E8F0),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        "Alarm clock settings",
                        tint = if (reminderTime != null) Color(0xFF065F46) else Color(0xFF1E293B)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // SAVE BUTTON
                Button(
                    onClick = {
                        onSave(id, title, contentValue.text, selectedFid, isFavorite, themeType, attachmentImagePath, attachmentFilePathState, reminderTime, reminderTone, manualTypeOverride)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(modifier = Modifier.height(64.dp))

        // Alarm reminder Setup sheet Dialog
        if (isSetReminderOpen) {
            Dialog(onDismissRequest = { isSetReminderOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text("Configure Local Alert Reminder", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Schedule alert timer:", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick intervals triggers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    reminderTime = System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes
                                    Toast.makeText(context, "Scheduled in 10 minutes!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("In 10m", color = Color.White, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    reminderTime = System.currentTimeMillis() + (60 * 60 * 1000) // 1 hour
                                    Toast.makeText(context, "Scheduled in 1 Hour!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("In 1 Hour", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Select Local Ringtone:", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        val tonesList = listOf("Aurora Amber", "Cyber Bells", "Synthwave Whistle", "Cosmic Wakeup")
                        tonesList.forEach { tone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { reminderTone = tone }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tone, color = if (reminderTone == tone) Color(0xFF00FFCC) else Color.White, fontSize = 13.sp)
                                if (reminderTone == tone) {
                                    Icon(Icons.Default.Refresh, null, tint = Color(0xFF00FFCC), modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = {
                                reminderTime = null
                                isSetReminderOpen = false
                            }) {
                                Text("Disable Alert", color = Color.Red)
                            }
                            Button(
                                onClick = { isSetReminderOpen = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC))
                            ) {
                                Text("Confirm Alarm", color = Color.Black)
                            }
                        }
                    }
                }
            }
        }

        // Export Document Dialog Panel
        if (isExportOpen) {
            Dialog(onDismissRequest = { isExportOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text("Export Document & Share", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = exportFilename,
                            onValueChange = { exportFilename = it },
                            label = { Text("Filename", color = Color.Gray) },
                            textStyle = TextStyle(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00FFCC))
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Export To Storage Directory:", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    NotepadExporter.saveAsTxt(context, exportFilename, contentValue.text)
                                    isExportOpen = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(".TXT", color = Color.White) }

                            Button(
                                onClick = {
                                    NotepadExporter.saveAsPdf(context, exportFilename, title, contentValue.text)
                                    isExportOpen = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(".PDF", color = Color.White) }

                            Button(
                                onClick = {
                                    NotepadExporter.saveAsHtml(context, exportFilename, title, contentValue.text)
                                    isExportOpen = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(".HTML", color = Color.White) }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("4. Custom File Extension:", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customFileExt,
                                onValueChange = { customFileExt = it },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(color = Color.White),
                                placeholder = { Text("e.g. css, py, js", color = Color.Gray) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00FFCC))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    NotepadExporter.saveCustom(context, exportFilename, customFileExt, contentValue.text)
                                    isExportOpen = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC))
                            ) {
                                Text("Save Ext", color = Color.Black)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Other Instant Share Option:", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                // Trigger standard Android Share Intent
                                val sendIntent: android.content.Intent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, "$title\n\n${contentValue.text}")
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "Share note via:")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                        ) {
                            Text("Share directly to Telegram / WhatsApp", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = { isExportOpen = false },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Close Dashboard", color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }


}
}

@Composable
fun ThemeBubble(
    colorHex: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(android.graphics.Color.parseColor(colorHex)))
            .border(
                2.dp,
                if (isSelected) Color.White else Color.Transparent,
                CircleShape
            )
            .clickable(onClick = onClick)
    )
}

// --- NAME STYLIZER SCREEN ---
@Composable
fun NameStylizerScreenSection(
    onBack: () -> Unit,
    viewModel: NotepadViewModel
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var inputName by remember { mutableStateOf(viewModel.customUserName) }

    val designsList = remember(inputName) {
        NameStylizer.getStylishFonts(inputName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF1E293B))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Insta / Bio Font Stylizer", color = Color(0xFF1E293B), fontSize = 20.sp, fontWeight = FontWeight.Black)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputName,
            onValueChange = { inputName = it },
            label = { Text("Type name to design", color = Color(0xFF94A3B8)) },
            textStyle = TextStyle(color = Color(0xFF1E293B), fontWeight = FontWeight.Bold),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = Color(0xFFE2E8F0),
                focusedContainerColor = Color(0xFFF1F5F9),
                unfocusedContainerColor = Color(0xFFF1F5F9)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(designsList) { design ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(design.first, color = Color(0xFF64748B), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(design.second, color = Color(0xFF1E293B), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(design.second))
                                com.example.util.CopySoundPlayer.playClickSound(context)
                                Toast.makeText(context, "Copied design to Bio paste board!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Copy", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- SETTINGS CONFIG DRAWER SCREEN ---
@Composable
fun SettingsScreenSection(
    onBack: () -> Unit,
    viewModel: NotepadViewModel
) {
    val context = LocalContext.current
    var inputKey by remember { mutableStateOf(viewModel.geminiApiKeyOverride) }
    var openAiKeyInput by remember { mutableStateOf(viewModel.openAiApiKey) }
    var claudeKeyInput by remember { mutableStateOf(viewModel.claudeApiKey) }
    var githubKeyInput by remember { mutableStateOf(viewModel.githubToken) }
    var customKeyInput by remember { mutableStateOf(viewModel.customToken) }
    var pinField by remember { mutableStateOf(viewModel.savedPin) }
    var userNameField by remember { mutableStateOf(viewModel.customUserName) }

    var isBlurState by remember { mutableStateOf(viewModel.isBlurApisEnabled) }
    var isSyntaxState by remember { mutableStateOf(viewModel.isSyntaxHighlightingEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF1E293B))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("AuraPad Preferences Dashboard", color = Color(0xFF1E293B), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("DATA INTEGRATION & AI", color = Color(0xFF2563EB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        // Custom API Keys config
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("API Key / Token Override:", color = Color(0xFF1E293B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Enter custom token / API key (Gemini, AI, or generic override):", color = Color(0xFF64748B), fontSize = 10.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = inputKey,
                    onValueChange = {
                        inputKey = it
                        viewModel.geminiApiKeyOverride = it
                    },
                    placeholder = { Text("Enter your API Key / secret token...", color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(color = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedContainerColor = Color(0xFFF1F5F9),
                        unfocusedContainerColor = Color(0xFFF1F5F9)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.geminiApiKeyOverride = inputKey
                        com.example.util.CopySoundPlayer.playClickSound(context)
                        Toast.makeText(context, "API Key saved successfully! 🗝️🚀", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("💾 Save API Preference", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("ORGANIZED PREFERENCES", color = Color(0xFF2563EB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        // Blur list option
        SettingsToggleRow(
            title = "Blur APIs on Note Lists",
            desc = "Hides/blurs long non-spaced credentials for privacy safety",
            checked = isBlurState,
            onCheckedChange = {
                isBlurState = it
                viewModel.isBlurApisEnabled = it
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Highlight syntax option
        SettingsToggleRow(
            title = "Code Syntax Highlight",
            desc = "Colorizes variables, tags and strings inside coding boxes",
            checked = isSyntaxState,
            onCheckedChange = {
                isSyntaxState = it
                viewModel.isSyntaxHighlightingEnabled = it
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("SECURITY LOCKS & PROFILE", color = Color(0xFF2563EB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        // PIN Setup
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("APIs Folder / Lock Passcode PIN", color = Color(0xFF1E293B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pinField,
                    onValueChange = {
                        if (it.length <= 4) {
                            pinField = it
                            viewModel.savedPin = it
                        }
                    },
                    label = { Text("4-digit PIN", color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(color = Color(0xFF1E293B)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedContainerColor = Color(0xFFF1F5F9),
                        unfocusedContainerColor = Color(0xFFF1F5F9)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Trash deletion helper
        Button(
            onClick = {
                viewModel.clearTrash()
                Toast.makeText(context, "Trash Bin completely wiped / emptied!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Hard Wipe Trash Bin Recycler", color = Color.White)
        }

        Spacer(modifier = Modifier.height(50.dp))
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color(0xFF1E293B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, color = Color(0xFF64748B), fontSize = 10.sp, lineHeight = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2563EB),
                    checkedTrackColor = Color(0xFFBFDBFE)
                )
            )
        }
    }
}

// Rounded custom box helper
fun circleShape(): RoundedCornerShape = RoundedCornerShape(percent = 50)

@Composable
fun ContactOptionItem(
    brandColor: Color,
    brandGradient: Brush? = null,
    iconChar: String,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .then(
                    if (brandGradient != null) Modifier.background(brandGradient)
                    else Modifier.background(brandColor)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconChar,
                fontSize = 20.sp,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (value == "Not Available") Color(0xFFEF4444) else Color(0xFF2563EB)
            )
        }
        
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Navigate Link",
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun FormatterToggleButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF6366F1).copy(alpha = 0.18f) else Color.Transparent)
            .border(
                1.dp,
                if (isActive) Color(0xFF6366F1) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (text == "U") {
            Text(
                text = "U",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                color = if (isActive) Color(0xFF4F46E5) else Color(0xFF475569)
            )
        } else if (text == "G") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "G",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color(0xFFEC4899) else Color(0xFF475569)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "✨",
                    fontSize = 10.sp
                )
            }
        } else {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = if (text == "B") FontWeight.Bold else FontWeight.Medium,
                style = if (text == "I") TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) else TextStyle.Default,
                color = if (isActive) Color(0xFF4F46E5) else Color(0xFF475569)
            )
        }
    }
}
