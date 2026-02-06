package ai.musicconverter.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ai.musicconverter.data.ConversionStatus
import ai.musicconverter.data.MusicFile
import ai.musicconverter.ui.components.DigitalDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicConverterScreen(
    viewModel: MusicConverterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val autoConvertEnabled by viewModel.autoConvertEnabled.collectAsState()
    val keepOriginalEnabled by viewModel.keepOriginalEnabled.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showConvertConfirmDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState()

    val isConverting = uiState.isBatchConverting || uiState.musicFiles.any { it.isConverting }
    val displayFiles = uiState.filteredFiles

    // Selection state - all files selected by default
    var selectedIds by remember(uiState.musicFiles) {
        mutableStateOf(uiState.musicFiles.map { it.id }.toSet())
    }
    val selectedCount = selectedIds.size

    // Track manage storage permission with mutableState so it refreshes on resume
    var hasManageStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true
        )
    }

    // Permission launcher
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.setStoragePermissionGranted(true)
        }
    }

    // Re-check permissions when app resumes (e.g., returning from settings)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasPermission = Environment.isExternalStorageManager()
            hasManageStoragePermission = hasPermission
            if (hasPermission && !uiState.hasStoragePermission) {
                viewModel.setStoragePermissionGranted(true)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                viewModel.setStoragePermissionGranted(true)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            storagePermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
        } else {
            storagePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Search field
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = {
                            Text("Search...", style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (autoConvertEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            ControlBar(
                isConverting = isConverting,
                songsInQueue = selectedCount,
                onPlayClick = { if (selectedCount > 0) showConvertConfirmDialog = true },
                onPauseClick = { viewModel.cancelAllConversions() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !hasManageStoragePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    PermissionRequestContent(
                        onRequestPermission = {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
                !uiState.hasStoragePermission -> {
                    PermissionRequestContent(
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                storagePermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
                            } else {
                                storagePermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            }
                        }
                    )
                }
                uiState.status == ConversionStatus.SCANNING -> {
                    LoadingContent(message = "Scanning for music files...")
                }
                uiState.musicFiles.isEmpty() -> {
                    EmptyContent(
                        message = "No music files need conversion",
                        onRefresh = { viewModel.scanForFiles() }
                    )
                }
                else -> {
                    CondensedMediaList(
                        files = displayFiles,
                        selectedIds = selectedIds,
                        onToggleSelection = { id ->
                            selectedIds = if (selectedIds.contains(id)) {
                                selectedIds - id
                            } else {
                                selectedIds + id
                            }
                        },
                        onSelectAll = {
                            selectedIds = displayFiles.map { it.id }.toSet()
                        },
                        onDeselectAll = {
                            selectedIds = emptySet()
                        }
                    )
                }
            }
        }
    }

    // Convert confirmation dialog
    if (showConvertConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConvertConfirmDialog = false },
            title = { Text("Convert $selectedCount ${if (selectedCount == 1) "File" else "Files"}?") },
            text = {
                Text(
                    if (keepOriginalEnabled) "Original files will be kept."
                    else "Original files will be replaced.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showConvertConfirmDialog = false
                    viewModel.convertSelectedFiles(selectedIds.toList())
                }) { Text("Convert") }
            },
            dismissButton = {
                TextButton(onClick = { showConvertConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Settings bottom sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = settingsSheetState
        ) {
            SettingsSheetContent(
                autoConvertEnabled = autoConvertEnabled,
                keepOriginalEnabled = keepOriginalEnabled,
                onToggleAutoConvert = { viewModel.toggleAutoConvert() },
                onToggleKeepOriginal = { viewModel.toggleKeepOriginal() },
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Bottom Control Bar: Play/Pause | Digital Display | Portal/Nuke
// ──────────────────────────────────────────────────────────────

@Composable
private fun ControlBar(
    isConverting: Boolean,
    songsInQueue: Int,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF111111),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Play / Pause button
            PlayPauseButton(
                isConverting = isConverting,
                onPlayClick = onPlayClick,
                onPauseClick = onPauseClick
            )

            // Digital Display (center, fills available space)
            DigitalDisplay(
                songsInQueue = songsInQueue,
                isConverting = isConverting,
                modifier = Modifier.weight(1f)
            )

            // Portal / Nuke indicator
            PortalIndicator(isConverting = isConverting)
        }
    }
}

@Composable
private fun PlayPauseButton(
    isConverting: Boolean,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isConverting) Color(0xFFCC3333) else Color(0xFF2A2A2A),
        animationSpec = tween(300),
        label = "playBg"
    )

    Surface(
        onClick = { if (isConverting) onPauseClick() else onPlayClick() },
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = bgColor,
        tonalElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isConverting) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isConverting) "Stop conversion" else "Start conversion",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun PortalIndicator(isConverting: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "portal")
    val portalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val portalPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isConverting) {
            // Nuke / radioactive symbol when converting
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .scale(portalPulse)
                    .rotate(portalRotation)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFDD00),
                                Color(0xFFFF8800),
                                Color(0xFFCC4400)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Radioactive trefoil
                Text(
                    text = "\u2622",
                    fontSize = 24.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.rotate(-portalRotation)
                )
            }
        } else {
            // Closed portal: dark inert circle
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A))
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3A3A))
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Condensed Media Table List
// ──────────────────────────────────────────────────────────────

@Composable
private fun CondensedMediaList(
    files: List<MusicFile>,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Table header with select all
        MediaTableHeader(
            allSelected = selectedIds.size == files.size && files.isNotEmpty(),
            onToggleAll = { if (selectedIds.size == files.size) onDeselectAll() else onSelectAll() }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // File rows - use weight(1f) to allow scrolling
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(0.dp)
        ) {
            items(files, key = { it.id }) { file ->
                MediaTableRow(
                    file = file,
                    isSelected = selectedIds.contains(file.id),
                    onToggleSelection = { onToggleSelection(file.id) }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun MediaTableHeader(
    allSelected: Boolean,
    onToggleAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = allSelected,
            onCheckedChange = { onToggleAll() },
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = "Name",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Time",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Artist",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MediaTableRow(
    file: MusicFile,
    isSelected: Boolean,
    onToggleSelection: () -> Unit
) {
    val bgColor = when {
        file.isConverting -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelection() },
            modifier = Modifier.size(32.dp)
        )

        // Name + converting indicator
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (file.isConverting) {
                Spacer(modifier = Modifier.width(6.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Time
        Text(
            text = file.displayDuration,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Artist
        Text(
            text = file.artist ?: "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Settings Sheet
// ──────────────────────────────────────────────────────────────

@Composable
private fun SettingsSheetContent(
    autoConvertEnabled: Boolean,
    keepOriginalEnabled: Boolean,
    onToggleAutoConvert: () -> Unit,
    onToggleKeepOriginal: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Auto-convert toggle
        SettingsToggleCard(
            title = "Auto-Convert Service",
            description = if (autoConvertEnabled) "Automatically converts new audio files"
            else "Manual conversion only",
            checked = autoConvertEnabled,
            onToggle = onToggleAutoConvert
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Keep original files toggle
        SettingsToggleCard(
            title = "Keep Original Files",
            description = if (keepOriginalEnabled) "Converted files saved next to originals"
            else "Originals deleted, saved to Music/Converted",
            checked = keepOriginalEnabled,
            onToggle = onToggleKeepOriginal
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "When auto-convert is enabled, the app monitors for new audio files and converts them to AAC format in the background.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Utility screens
// ──────────────────────────────────────────────────────────────

@Composable
private fun PermissionRequestContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Storage Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "This app needs access to your storage to find and convert music files.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) { Text("Grant Permission") }
    }
}

@Composable
private fun LoadingContent(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(message)
    }
}

@Composable
private fun EmptyContent(message: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh")
        }
    }
}
