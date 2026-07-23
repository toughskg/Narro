package com.narro.app.feature

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.narro.app.R
import com.narro.app.domain.model.Bookmark
import com.narro.app.domain.model.Document
import com.narro.app.domain.model.DocumentSegment
import com.narro.app.domain.model.ImportStage
import com.narro.app.domain.model.ReadingPosition
import com.narro.app.playback.model.PlaybackStatus
import com.narro.app.playback.service.TtsPlaybackService
import com.narro.app.security.PinVerification
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Destination { DOCUMENTS, READER, BOOKMARKS, SETTINGS }

@Composable
fun NarroApp(
    viewModel: AppViewModel,
    authenticateBiometric: ((Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val reader by viewModel.reader.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val duplicate by viewModel.duplicate.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var destination by rememberSaveable { mutableStateOf(Destination.DOCUMENTS) }
    var lockState by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showPinRegistration by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lockState = viewModel.initialLockRequired()
        viewModel.messages.collect { message ->
            val text = message.argument?.let { resources.getString(message.resourceId, it) }
                ?: resources.getString(message.resourceId)
            snackbar.showSnackbar(text)
        }
    }

    if (lockState == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (lockState == true) {
        LockScreen(
            biometricEnabled = settings.biometricEnabled,
            verifyPin = viewModel::verifyPin,
            authenticateBiometric = authenticateBiometric,
            onUnlocked = { lockState = false },
        )
        return
    }

    BackHandler(destination != Destination.DOCUMENTS) {
        if (destination == Destination.READER) {
            TtsPlaybackService.command(context, TtsPlaybackService.ACTION_STOP_SAVE)
        }
        destination = Destination.DOCUMENTS
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { outerPadding ->
        Box(Modifier.padding(outerPadding)) {
            when (destination) {
                Destination.DOCUMENTS -> DocumentsScreen(
                    documents = documents,
                    importProgress = importProgress,
                    onImport = viewModel::import,
                    onCancelImport = viewModel::cancelImport,
                    onOpen = {
                        viewModel.openDocument(it.id)
                        destination = Destination.READER
                    },
                    onDelete = viewModel::deleteDocument,
                    onBookmarks = { destination = Destination.BOOKMARKS },
                    onSettings = { destination = Destination.SETTINGS },
                )
                Destination.READER -> ReaderScreen(
                    state = reader,
                    playbackStatus = playback.status,
                    playbackPosition = if (playback.documentId == reader.document?.id) {
                        playback.position
                    } else {
                        reader.position
                    },
                    speechRate = settings.speechRate,
                    onBack = {
                        TtsPlaybackService.command(context, TtsPlaybackService.ACTION_STOP_SAVE)
                        destination = Destination.DOCUMENTS
                    },
                    onPlayPause = {
                        val document = reader.document ?: return@ReaderScreen
                        if (playback.documentId == document.id &&
                            playback.status in setOf(
                                PlaybackStatus.PAUSED_BY_USER,
                                PlaybackStatus.PAUSED_BY_FOCUS,
                            )
                        ) {
                            TtsPlaybackService.command(context, TtsPlaybackService.ACTION_RESUME)
                        } else if (playback.documentId == document.id &&
                            playback.status == PlaybackStatus.PLAYING
                        ) {
                            TtsPlaybackService.command(context, TtsPlaybackService.ACTION_PAUSE)
                        } else {
                            TtsPlaybackService.play(context, document.id, reader.position, settings.speechRate)
                        }
                    },
                    onBookmark = viewModel::addBookmark,
                    onPreviousSegment = { viewModel.loadSegment(reader.centerSegmentIndex - 1) },
                    onNextSegment = { viewModel.loadSegment(reader.centerSegmentIndex + 1) },
                )
                Destination.BOOKMARKS -> BookmarksScreen(
                    bookmarks = bookmarks,
                    onBack = { destination = Destination.DOCUMENTS },
                    onOpen = {
                        viewModel.openDocument(
                            it.documentId,
                            ReadingPosition(it.sentenceIndex, it.characterOffset),
                        )
                        destination = Destination.READER
                    },
                    onDelete = viewModel::deleteBookmark,
                )
                Destination.SETTINGS -> SettingsScreen(
                    speechRate = settings.speechRate,
                    lockEnabled = settings.lockEnabled,
                    biometricEnabled = settings.biometricEnabled,
                    onBack = { destination = Destination.DOCUMENTS },
                    onSpeechRate = viewModel::setSpeechRate,
                    onLockToggle = { enabled ->
                        if (enabled && !viewModel.hasPin()) showPinRegistration = true
                        else viewModel.setLockEnabled(enabled)
                    },
                    onBiometricToggle = { enabled ->
                        if (!enabled) viewModel.setBiometricEnabled(false)
                        else authenticateBiometric { success ->
                            if (success) viewModel.setBiometricEnabled(true)
                        }
                    },
                )
            }
        }
    }

    if (showPinRegistration) {
        PinRegistrationDialog(
            onDismiss = { showPinRegistration = false },
            onRegister = { pin ->
                val success = viewModel.registerPin(pin)
                if (success) viewModel.setLockEnabled(true)
                success
            },
        )
    }

    duplicate?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.alt_007)) },
            text = { Text(it.existingName) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDuplicate) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDuplicate) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentsScreen(
    documents: List<Document>,
    importProgress: com.narro.app.domain.model.ImportProgress?,
    onImport: (android.net.Uri) -> Unit,
    onCancelImport: () -> Unit,
    onOpen: (Document) -> Unit,
    onDelete: (String) -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<Document?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.documents_title)) },
                actions = {
                    IconButton(onClick = onBookmarks) {
                        Icon(Icons.Default.Bookmark, stringResource(R.string.bookmarks_title))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { picker.launch(arrayOf("text/plain", "text/*")) }) {
                Icon(Icons.Default.Add, stringResource(R.string.add_document))
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val contentModifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = if (maxWidth >= 840.dp) 760.dp else maxWidth)
            if (documents.isEmpty()) {
                Column(
                    contentModifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Description, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.no_documents), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.no_documents_description),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = contentModifier,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(documents, key = { it.id }) { document ->
                        DocumentCard(document, { onOpen(document) }, { deleteTarget = document })
                    }
                }
            }
        }
    }

    if (importProgress != null) {
        AlertDialog(
            onDismissRequest = onCancelImport,
            confirmButton = {
                TextButton(onClick = onCancelImport) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.importing)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(importStageLabel(importProgress.stage))
                    if (importProgress.percent != null) {
                        LinearProgressIndicator(
                            progress = { importProgress.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
        )
    }

    deleteTarget?.let { document ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_document_title)) },
            text = { Text(stringResource(R.string.delete_document_message, document.displayName)) },
            confirmButton = {
                TextButton(onClick = { onDelete(document.id); deleteTarget = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun DocumentCard(document: Document, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(document.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { document.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${Formatter.formatFileSize(LocalContext.current, document.fileSize)} · " +
                        stringResource(R.string.file_progress, document.progressPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    playbackStatus: PlaybackStatus,
    playbackPosition: ReadingPosition,
    speechRate: Float,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onBookmark: () -> Unit,
    onPreviousSegment: () -> Unit,
    onNextSegment: () -> Unit,
) {
    val document = state.document
    val listState = rememberLazyListState()
    LaunchedEffect(state.centerSegmentIndex, state.segments) {
        val item = state.segments.indexOfFirst { it.index == state.centerSegmentIndex }
        if (item >= 0) listState.scrollToItem(item)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.displayName ?: stringResource(R.string.reader_title), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onBookmark, enabled = document != null) {
                        Icon(Icons.Default.BookmarkAdd, stringResource(R.string.add_bookmark))
                    }
                },
            )
        },
        bottomBar = {
            ReaderControls(
                canGoPrevious = state.centerSegmentIndex > 0,
                canGoNext = state.centerSegmentIndex < ((document?.totalSegmentCount ?: 1) - 1),
                playing = playbackStatus == PlaybackStatus.PLAYING,
                progress = document?.let {
                    (playbackPosition.sentenceIndex.toFloat() / it.totalSentenceCount.coerceAtLeast(1)).coerceIn(0f, 1f)
                } ?: 0f,
                speechRate = speechRate,
                onPlayPause = onPlayPause,
                onPrevious = onPreviousSegment,
                onNext = onNextSegment,
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(state.segments, key = { it.index }) { segment ->
                        Text(
                            text = highlightedSegment(segment, playbackPosition.sentenceIndex),
                            fontSize = 19.sp,
                            lineHeight = 30.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderControls(
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    playing: Boolean,
    progress: Float,
    speechRate: Float,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = canGoPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.back))
                }
                Button(onClick = onPlayPause, modifier = Modifier.height(56.dp)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (playing) R.string.pause else R.string.play))
                }
                IconButton(onClick = onNext, enabled = canGoNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.reader_title))
                }
            }
            Text(
                stringResource(R.string.speech_rate_value, speechRate),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    onBack: () -> Unit,
    onOpen: (Bookmark) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.bookmarks_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            },
        )
    }) { padding ->
        if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.bookmark_empty))
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Card(onClick = { onOpen(bookmark) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bookmark.documentName, style = MaterialTheme.typography.titleSmall)
                                Text(bookmark.previewText, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(
                                    DateFormat.getDateTimeInstance().format(Date(bookmark.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { onDelete(bookmark.id) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    speechRate: Float,
    lockEnabled: Boolean,
    biometricEnabled: Boolean,
    onBack: () -> Unit,
    onSpeechRate: (Float) -> Unit,
    onLockToggle: (Boolean) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            },
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Text(stringResource(R.string.speech_rate), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.speech_rate_value, speechRate))
                Slider(value = speechRate, onValueChange = onSpeechRate, valueRange = 0.5f..2f, steps = 14)
            }
            item {
                SettingSwitch(
                    title = stringResource(R.string.app_lock),
                    summary = stringResource(R.string.app_lock_summary),
                    checked = lockEnabled,
                    onCheckedChange = onLockToggle,
                )
            }
            item {
                SettingSwitch(
                    title = stringResource(R.string.biometric_unlock),
                    summary = stringResource(R.string.biometric_summary),
                    checked = biometricEnabled,
                    enabled = lockEnabled,
                    onCheckedChange = onBiometricToggle,
                )
            }
            item {
                Card(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            })
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.app_language), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.app_language_summary))
                    }
                }
            }
            item { Text(stringResource(R.string.backup_summary), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun LockScreen(
    biometricEnabled: Boolean,
    verifyPin: suspend (String) -> PinVerification,
    authenticateBiometric: ((Boolean) -> Unit) -> Unit,
    onUnlocked: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricRequested by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val failedText = stringResource(R.string.pin_failed)
    val unavailableText = stringResource(R.string.unknown_error)

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && !biometricRequested) {
            biometricRequested = true
            authenticateBiometric { if (it) onUnlocked() }
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.pin_title), style = MaterialTheme.typography.titleLarge)
        TextField(
            value = pin,
            onValueChange = { value -> pin = value.filter(Char::isDigit).take(4); error = null },
            label = { Text(stringResource(R.string.pin_hint)) },
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    when (val result = verifyPin(pin)) {
                        PinVerification.Success -> onUnlocked()
                        is PinVerification.Failed -> error = failedText
                        is PinVerification.Locked -> error = resources.getQuantityString(
                            R.plurals.pin_locked_seconds,
                            result.remainingSeconds.toInt(),
                            result.remainingSeconds,
                        )
                        PinVerification.Unavailable -> error = unavailableText
                    }
                    pin = ""
                }
            },
            enabled = pin.length == 4,
        ) { Text(stringResource(R.string.pin_unlock)) }
    }
}

@Composable
private fun PinRegistrationDialog(
    onDismiss: () -> Unit,
    onRegister: suspend (String) -> Boolean,
) {
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (confirming) R.string.pin_confirm_title else R.string.pin_create_title)) },
        text = {
            TextField(
                value = if (confirming) second else first,
                onValueChange = { value ->
                    val filtered = value.filter(Char::isDigit).take(4)
                    if (confirming) second = filtered else first = filtered
                    error = null
                },
                label = { Text(stringResource(R.string.pin_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error != null,
                supportingText = { error?.let { Text(stringResource(it)) } },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (!confirming) {
                    if (first.length == 4) confirming = true else error = R.string.pin_invalid
                } else if (first != second) {
                    error = R.string.pin_mismatch
                } else {
                    scope.launch { if (onRegister(first)) onDismiss() else error = R.string.unknown_error }
                }
            }) { Text(stringResource(if (confirming) R.string.done else R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun importStageLabel(stage: ImportStage): String = stringResource(
    when (stage) {
        ImportStage.CHECKING -> R.string.msg_012
        ImportStage.DETECTING_ENCODING -> R.string.msg_013
        ImportStage.CONVERTING -> R.string.msg_014
        ImportStage.PARSING -> R.string.msg_015
        ImportStage.SAVING -> R.string.importing
    },
)

private fun highlightedSegment(segment: DocumentSegment, sentenceIndex: Int): AnnotatedString {
    if (sentenceIndex !in segment.startSentenceIndex..segment.endSentenceIndex) {
        return AnnotatedString(segment.text)
    }
    val target = sentenceIndex - segment.startSentenceIndex
    var current = 0
    var start = 0
    var end = segment.text.length
    var index = 0
    while (index < segment.text.length) {
        val cp = segment.text.codePointAt(index)
        val next = index + Character.charCount(cp)
        val boundary = cp == '.'.code || cp == '!'.code || cp == '?'.code || cp == '\n'.code ||
            cp == 0x3002 || cp == 0xFF01 || cp == 0xFF1F ||
            segment.text.codePointCount(start, next) >= 3_000
        if (current == target && start == 0 && current > 0) start = index
        if (boundary) {
            if (current == target) {
                end = next
                break
            }
            current++
            start = next
        }
        index = next
    }
    return buildAnnotatedString {
        append(segment.text)
        if (start in 0 until end && end <= segment.text.length) {
            addStyle(SpanStyle(background = Color(0x66FFE082)), start, end)
        }
    }
}
