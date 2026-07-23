package com.narro.app.feature

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.narro.app.BuildConfig
import com.narro.app.R
import com.narro.app.domain.model.Bookmark
import com.narro.app.domain.model.Document
import com.narro.app.domain.model.DocumentSegment
import com.narro.app.domain.model.ImportStage
import com.narro.app.domain.model.ReadingPosition
import com.narro.app.playback.model.PlaybackStatus
import com.narro.app.playback.service.TtsPlaybackService
import com.narro.app.security.PinVerification
import java.net.URI
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Destination { DOCUMENTS, READER, BOOKMARKS, SETTINGS, SUPPORT }

internal enum class LockToggleAction {
    REGISTER_NEW_PIN,
    CLEAR_PIN,
}

internal fun lockToggleAction(enabled: Boolean): LockToggleAction =
    if (enabled) LockToggleAction.REGISTER_NEW_PIN else LockToggleAction.CLEAR_PIN

internal fun shouldReturnToDocuments(
    playbackStatus: PlaybackStatus,
    playbackDocumentId: String?,
    readerDocumentId: String?,
): Boolean = playbackStatus == PlaybackStatus.COMPLETED &&
    playbackDocumentId != null &&
    playbackDocumentId == readerDocumentId

internal fun canSelectReadingPosition(status: PlaybackStatus): Boolean = status !in setOf(
    PlaybackStatus.INITIALIZING,
    PlaybackStatus.PLAYING,
    PlaybackStatus.PAUSED_BY_FOCUS,
)

internal fun playbackStartPosition(
    selectedPosition: ReadingPosition?,
    currentPosition: ReadingPosition,
): ReadingPosition = selectedPosition ?: currentPosition

internal fun isTrustedKoFiUrl(rawUrl: String): Boolean {
    if (rawUrl.isBlank()) return false
    val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        (host == "ko-fi.com" || host.endsWith(".ko-fi.com"))
}

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
    val appScope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(Destination.DOCUMENTS) }
    var lockState by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showPinRegistration by rememberSaveable { mutableStateOf(false) }
    var selectedStartPosition by remember { mutableStateOf<ReadingPosition?>(null) }

    LaunchedEffect(Unit) {
        lockState = viewModel.initialLockRequired()
        viewModel.messages.collect { message ->
            val text = message.argument?.let { resources.getString(message.resourceId, it) }
                ?: resources.getString(message.resourceId)
            snackbar.showSnackbar(text)
        }
    }

    LaunchedEffect(playback.status, playback.documentId, reader.document?.id) {
        if (shouldReturnToDocuments(playback.status, playback.documentId, reader.document?.id)) {
            selectedStartPosition = null
            destination = Destination.DOCUMENTS
            viewModel.acknowledgePlaybackCompletion()
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
        when (destination) {
            Destination.READER -> {
                TtsPlaybackService.command(context, TtsPlaybackService.ACTION_STOP_SAVE)
                destination = Destination.DOCUMENTS
            }
            Destination.SUPPORT -> destination = Destination.SETTINGS
            else -> destination = Destination.DOCUMENTS
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.navigationBarsPadding().imePadding(),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { outerPadding ->
        Box(Modifier.fillMaxSize().padding(outerPadding)) {
            when (destination) {
                Destination.DOCUMENTS -> DocumentsScreen(
                    documents = documents,
                    importProgress = importProgress,
                    onImport = viewModel::import,
                    onCancelImport = viewModel::cancelImport,
                    onOpen = {
                        selectedStartPosition = null
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
                    playbackPosition = selectedStartPosition ?: if (playback.documentId == reader.document?.id) {
                        playback.position
                    } else {
                        reader.position
                    },
                    onBack = {
                        selectedStartPosition = null
                        TtsPlaybackService.command(context, TtsPlaybackService.ACTION_STOP_SAVE)
                        destination = Destination.DOCUMENTS
                    },
                    onPlayPause = {
                        val document = reader.document ?: return@ReaderScreen
                        val selectedPosition = selectedStartPosition
                        if (selectedPosition != null) {
                            selectedStartPosition = null
                            TtsPlaybackService.play(
                                context,
                                document.id,
                                playbackStartPosition(selectedPosition, reader.position),
                                settings.speechRate,
                            )
                        } else if (playback.documentId == document.id &&
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
                    canSelectPosition = canSelectReadingPosition(playback.status),
                    onSelectPosition = { position ->
                        selectedStartPosition = position
                        viewModel.selectReadingPosition(position)
                    },
                    onBookmark = viewModel::addBookmark,
                    onPreviousSegment = { viewModel.loadSegment(reader.centerSegmentIndex - 1) },
                    onNextSegment = { viewModel.loadSegment(reader.centerSegmentIndex + 1) },
                )
                Destination.BOOKMARKS -> BookmarksScreen(
                    bookmarks = bookmarks,
                    onBack = { destination = Destination.DOCUMENTS },
                    onOpen = {
                        selectedStartPosition = null
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
                    onSupport = { destination = Destination.SUPPORT },
                    onSpeechRate = viewModel::setSpeechRate,
                    onLockToggle = { enabled ->
                        when (lockToggleAction(enabled)) {
                            LockToggleAction.REGISTER_NEW_PIN -> showPinRegistration = true
                            LockToggleAction.CLEAR_PIN -> viewModel.clearPin()
                        }
                    },
                    onBiometricToggle = { enabled ->
                        if (!enabled) viewModel.setBiometricEnabled(false)
                        else authenticateBiometric { success ->
                            if (success) viewModel.setBiometricEnabled(true)
                        }
                    },
                )
                Destination.SUPPORT -> SupportScreen(
                    onBack = { destination = Destination.SETTINGS },
                    onOpenKoFi = { _ ->
                        val koFiUrl = BuildConfig.KO_FI_URL
                        if (koFiUrl.isBlank()) {
                            appScope.launch {
                                snackbar.showSnackbar(resources.getString(R.string.msg_056))
                            }
                        } else if (!isTrustedKoFiUrl(koFiUrl)) {
                            appScope.launch {
                                snackbar.showSnackbar(resources.getString(R.string.err_061))
                            }
                        } else {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, koFiUrl.toUri()))
                            } catch (_: ActivityNotFoundException) {
                                appScope.launch {
                                    snackbar.showSnackbar(resources.getString(R.string.err_061))
                                }
                            }
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
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    canSelectPosition: Boolean,
    onSelectPosition: (ReadingPosition) -> Unit,
    onBookmark: () -> Unit,
    onPreviousSegment: () -> Unit,
    onNextSegment: () -> Unit,
) {
    val document = state.document
    val listState = rememberLazyListState()
    val displayItems = remember(state.segments) { state.segments.flatMap(::displaySentences) }
    var locateRequest by remember { mutableStateOf(0) }
    LaunchedEffect(
        state.centerSegmentIndex,
        state.segments,
        playbackPosition.sentenceIndex,
        locateRequest,
    ) {
        val item = displayItems.indexOfFirst {
            playbackPosition.sentenceIndex == it.sentenceIndex
        }.takeIf { it >= 0 } ?: displayItems.indexOfFirst {
            it.segmentIndex == state.centerSegmentIndex
        }
        val targetKey = displayItems.getOrNull(item)?.key
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo.map {
            ReadingItemViewport(
                key = it.key,
                offset = it.offset,
                size = it.size,
            )
        }
        if (targetKey != null && shouldScrollToReadingItem(
                targetKey = targetKey,
                visibleItems = visibleItems,
                viewportStartOffset = layoutInfo.viewportStartOffset,
                viewportEndOffset = layoutInfo.viewportEndOffset,
            )
        ) {
            listState.scrollToItem(item)
        }
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
                    verticalArrangement = Arrangement.Top,
                ) {
                    items(displayItems, key = DisplaySentence::key) { item ->
                        Text(
                            text = if (item.sentenceIndex == playbackPosition.sentenceIndex) {
                                buildAnnotatedString {
                                    append(item.text)
                                    addStyle(
                                        SpanStyle(background = Color(0x66FFE082)),
                                        0,
                                        item.text.length,
                                    )
                                }
                            } else {
                                AnnotatedString(item.text)
                            },
                            modifier = if (canSelectPosition) {
                                Modifier.pointerInput(item.key) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            onSelectPosition(item.position)
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },
                            fontSize = 19.sp,
                            lineHeight = 30.sp,
                        )
                    }
                }
                SmallFloatingActionButton(
                    onClick = { locateRequest++ },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        stringResource(R.string.current_reading_position),
                    )
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
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (playing) R.string.pause else R.string.play),
                    )
                }
                IconButton(onClick = onNext, enabled = canGoNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.reader_title))
                }
            }
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
    onSupport: () -> Unit,
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
            item { HorizontalDivider() }
            item {
                Text(
                    stringResource(R.string.information_and_support),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.app_version), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.app_version_value,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Card(
                    onClick = onSupport,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.support_narro),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.support_narro_summary),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportScreen(
    onBack: () -> Unit,
    onOpenKoFi: (Int) -> Unit,
) {
    var selectedAmount by rememberSaveable { mutableStateOf<Int?>(null) }
    val options = listOf(
        Triple(1, R.string.support_light_title, R.string.support_light_summary),
        Triple(2, R.string.support_warm_title, R.string.support_warm_summary),
        Triple(3, R.string.support_strong_title, R.string.support_strong_summary),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.support_narro)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.support_headline),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.support_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.support_emotional_copy),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(options, key = { it.first }) { (amount, title, summary) ->
                SupportOptionCard(
                    amount = amount,
                    title = stringResource(title),
                    summary = stringResource(summary),
                    emphasized = amount == 3,
                    onClick = { selectedAmount = amount },
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.support_equal_features))
                        Text(stringResource(R.string.support_no_benefits))
                        Text(stringResource(R.string.support_external_payment))
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.support_optional),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    selectedAmount?.let { amount ->
        AlertDialog(
            onDismissRequest = { selectedAmount = null },
            title = { Text(stringResource(R.string.support_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.alt_041, amount))
                    Text(
                        stringResource(R.string.alt_042),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedAmount = null
                        onOpenKoFi(amount)
                    },
                ) {
                    Text(stringResource(R.string.open_ko_fi))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAmount = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SupportOptionCard(
    amount: Int,
    title: String,
    summary: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (emphasized) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    summary,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                "$$amount",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
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
    var verifying by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableStateOf(0) }
    var biometricRequested by rememberSaveable { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val failedText = stringResource(R.string.pin_failed)
    val unavailableText = stringResource(R.string.unknown_error)

    fun requestBiometric() {
        authenticateBiometric { if (it) onUnlocked() }
    }

    fun submitPin(value: String) {
        if (value.length != 4 || verifying) return
        verifying = true
        scope.launch {
            when (val result = verifyPin(value)) {
                PinVerification.Success -> onUnlocked()
                is PinVerification.Failed -> {
                    error = failedText
                    shakeTrigger++
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }
                is PinVerification.Locked -> {
                    error = resources.getQuantityString(
                        R.plurals.pin_locked_seconds,
                        result.remainingSeconds.toInt(),
                        result.remainingSeconds,
                    )
                    shakeTrigger++
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }
                PinVerification.Unavailable -> error = unavailableText
            }
            pin = ""
            verifying = false
        }
    }

    fun enterDigit(digit: Int) {
        if (verifying) return
        error = null
        val next = appendPinDigit(pin, digit)
        pin = next
        if (next.length == 4) submitPin(next)
    }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && !biometricRequested) {
            biometricRequested = true
            requestBiometric()
        }
    }

    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger == 0) return@LaunchedEffect
        shakeOffset.snapTo(0f)
        shakeOffset.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 450
                -20f at 45
                20f at 90
                -14f at 145
                14f at 200
                -8f at 275
                8f at 350
                0f at 450
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.app_name),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.graphicsLayer { translationX = shakeOffset.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { index ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .then(
                                if (index < pin.length) {
                                    Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier.border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        CircleShape,
                                    )
                                },
                            ),
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                error?.let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier.width(220.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                listOf(1, 2, 3),
                listOf(4, 5, 6),
                listOf(7, 8, 9),
            ).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    row.forEach { digit ->
                        PinNumberButton(
                            digit = digit,
                            enabled = !verifying,
                            onClick = { enterDigit(digit) },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                if (biometricEnabled) {
                    FilledIconButton(
                        onClick = ::requestBiometric,
                        modifier = Modifier.size(58.dp),
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            stringResource(R.string.biometric_unlock),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.size(58.dp))
                }
                PinNumberButton(
                    digit = 0,
                    enabled = !verifying,
                    onClick = { enterDigit(0) },
                )
                OutlinedIconButton(
                    onClick = {
                        error = null
                        pin = deletePinDigit(pin)
                    },
                    enabled = pin.isNotEmpty() && !verifying,
                    modifier = Modifier.size(58.dp),
                    border = null,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        stringResource(R.string.delete_pin_digit),
                    )
                }
            }
        }
    }
}

@Composable
private fun PinNumberButton(
    digit: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(58.dp),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
            },
        ),
    ) {
        Text(
            digit.toString(),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

internal fun appendPinDigit(pin: String, digit: Int): String {
    if (digit !in 0..9 || pin.length >= 4) return pin
    return pin + digit
}

internal fun deletePinDigit(pin: String): String = pin.dropLast(1)

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

internal data class DisplaySentence(
    val segmentIndex: Int,
    val sentenceIndex: Int,
    val characterOffset: Long,
    val text: String,
) {
    val key: String
        get() = "$segmentIndex:$sentenceIndex"

    val position: ReadingPosition
        get() = ReadingPosition(sentenceIndex, characterOffset)
}

internal data class ReadingItemViewport(
    val key: Any,
    val offset: Int,
    val size: Int,
)

internal fun shouldScrollToReadingItem(
    targetKey: String,
    visibleItems: Collection<ReadingItemViewport>,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
): Boolean {
    val target = visibleItems.firstOrNull { it.key == targetKey } ?: return true
    val targetEndOffset = target.offset + target.size
    return target.offset < viewportStartOffset || targetEndOffset > viewportEndOffset
}

internal fun displaySentences(segment: DocumentSegment): List<DisplaySentence> {
    val result = mutableListOf<DisplaySentence>()
    val text = StringBuilder()
    var sentenceIndex = segment.startSentenceIndex
    var sentenceStartOffset = segment.startCharacterOffset
    var characterOffset = segment.startCharacterOffset
    var hasContent = false
    var codePoints = 0
    var index = 0

    fun emit() {
        if (!hasContent) return
        result += DisplaySentence(
            segment.index,
            sentenceIndex,
            sentenceStartOffset,
            text.toString(),
        )
        text.clear()
        sentenceIndex++
        sentenceStartOffset = characterOffset
        hasContent = false
        codePoints = 0
    }

    while (index < segment.text.length) {
        val cp = segment.text.codePointAt(index)
        text.appendCodePoint(cp)
        characterOffset++
        codePoints++
        if (!Character.isWhitespace(cp)) hasContent = true
        val boundary = cp == '.'.code || cp == '!'.code || cp == '?'.code || cp == '\n'.code ||
            cp == 0x3002 || cp == 0xFF01 || cp == 0xFF1F || codePoints >= 3_000
        if (boundary) emit()
        index += Character.charCount(cp)
    }
    emit()
    if (result.isEmpty() && text.isNotEmpty()) {
        result += DisplaySentence(
            segment.index,
            segment.startSentenceIndex,
            segment.startCharacterOffset,
            text.toString(),
        )
    }
    return result
}
