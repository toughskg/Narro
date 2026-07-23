package com.narro.app.feature

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narro.app.AppGraph
import com.narro.app.R
import com.narro.app.data.local.datastore.AppSettings
import com.narro.app.domain.model.Bookmark
import com.narro.app.domain.model.Document
import com.narro.app.domain.model.DocumentSegment
import com.narro.app.domain.model.ImportFailure
import com.narro.app.domain.model.ImportProgress
import com.narro.app.domain.model.ReadingPosition
import com.narro.app.playback.model.PlaybackStateStore
import com.narro.app.playback.model.PlaybackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val document: Document? = null,
    val segments: List<DocumentSegment> = emptyList(),
    val centerSegmentIndex: Int = 0,
    val position: ReadingPosition = ReadingPosition(0, 0),
    val loading: Boolean = false,
)

data class UiMessage(@get:StringRes val resourceId: Int, val argument: String? = null)

data class DuplicateImport(val uri: Uri, val existingName: String)

class AppViewModel(private val graph: AppGraph) : ViewModel() {
    val documents: StateFlow<List<Document>> = graph.documents.observeDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = graph.documents.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<AppSettings> = graph.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val playback = PlaybackStateStore.state

    private val mutableReader = MutableStateFlow(ReaderUiState())
    val reader = mutableReader.asStateFlow()
    private val mutableImportProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress = mutableImportProgress.asStateFlow()
    private val mutableDuplicate = MutableStateFlow<DuplicateImport?>(null)
    val duplicate = mutableDuplicate.asStateFlow()
    private val mutableMessages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = mutableMessages.asSharedFlow()
    private var importJob: Job? = null

    init {
        viewModelScope.launch {
            playback.collect { snapshot ->
                val state = mutableReader.value
                if (snapshot.documentId == state.document?.id) {
                    mutableReader.value = state.copy(position = snapshot.position)
                    val currentSegment = state.segments.firstOrNull {
                        snapshot.position.sentenceIndex in it.startSentenceIndex..it.endSentenceIndex
                    }
                    if (currentSegment == null && snapshot.status == PlaybackStatus.PLAYING) {
                        loadWindowForSentence(snapshot.position.sentenceIndex)
                    }
                }
            }
        }
    }

    fun import(uri: Uri, allowDuplicate: Boolean = false) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            mutableImportProgress.value = ImportProgress(com.narro.app.domain.model.ImportStage.CHECKING)
            try {
                val document = graph.documents.importDocument(uri, allowDuplicate) {
                    mutableImportProgress.value = it
                }
                mutableMessages.emit(UiMessage(R.string.msg_020, document.displayName))
            } catch (duplicate: ImportFailure.Duplicate) {
                mutableDuplicate.value = DuplicateImport(uri, duplicate.existingName)
            } catch (failure: ImportFailure) {
                mutableMessages.emit(UiMessage(failure.messageResource()))
            } finally {
                mutableImportProgress.value = null
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        mutableImportProgress.value = null
    }

    fun confirmDuplicate() {
        val request = mutableDuplicate.value ?: return
        mutableDuplicate.value = null
        import(request.uri, allowDuplicate = true)
    }

    fun dismissDuplicate() {
        mutableDuplicate.value = null
    }

    fun openDocument(documentId: String, requested: ReadingPosition? = null) {
        viewModelScope.launch {
            mutableReader.value = ReaderUiState(loading = true)
            val document = graph.documents.getDocument(documentId) ?: run {
                mutableMessages.emit(UiMessage(R.string.err_002))
                mutableReader.value = ReaderUiState()
                return@launch
            }
            val storedPosition = ReadingPosition(
                document.lastConfirmedSentenceIndex,
                document.lastConfirmedCharacterOffset,
            )
            val position = requested ?: if (storedPosition.sentenceIndex >= document.totalSentenceCount) {
                ReadingPosition(0, 0)
            } else {
                storedPosition
            }
            val center = graph.documents.findSegmentIndex(documentId, position.sentenceIndex)
            val segments = graph.documents.loadSegmentWindow(documentId, center)
            mutableReader.value = ReaderUiState(document, segments, center, position, loading = false)
        }
    }

    fun loadSegment(index: Int) {
        val document = mutableReader.value.document ?: return
        viewModelScope.launch {
            val center = index.coerceIn(0, (document.totalSegmentCount - 1).coerceAtLeast(0))
            val segments = graph.documents.loadSegmentWindow(document.id, center)
            mutableReader.value = mutableReader.value.copy(
                segments = segments,
                centerSegmentIndex = center,
            )
        }
    }

    private suspend fun loadWindowForSentence(sentenceIndex: Int) {
        val document = mutableReader.value.document ?: return
        val center = graph.documents.findSegmentIndex(document.id, sentenceIndex)
        if (center == mutableReader.value.centerSegmentIndex) return
        val segments = graph.documents.loadSegmentWindow(document.id, center)
        mutableReader.value = mutableReader.value.copy(segments = segments, centerSegmentIndex = center)
    }

    fun addBookmark() {
        val state = mutableReader.value
        val document = state.document ?: return
        val preview = state.segments.firstOrNull {
            state.position.sentenceIndex in it.startSentenceIndex..it.endSentenceIndex
        }?.text?.replace(Regex("\\s+"), " ")?.take(160).orEmpty()
        viewModelScope.launch {
            graph.documents.addBookmark(document.id, state.position, preview)
            mutableMessages.emit(UiMessage(R.string.bookmark_saved))
        }
    }

    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            try {
                graph.documents.deleteDocument(documentId)
            } catch (_: Exception) {
                mutableMessages.emit(UiMessage(R.string.err_017))
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch { graph.documents.deleteBookmark(bookmarkId) }
    }

    fun setSpeechRate(value: Float) {
        viewModelScope.launch { graph.settings.setSpeechRate(value) }
    }

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch { graph.settings.setLockEnabled(enabled) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { graph.settings.setBiometricEnabled(enabled) }
    }

    suspend fun registerPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        if (pin.length != 4 || !pin.all(Char::isDigit)) return@withContext false
        graph.pinStore.register(pin.toCharArray())
        true
    }

    suspend fun verifyPin(pin: String) = withContext(Dispatchers.Default) {
        graph.pinStore.verify(pin.toCharArray())
    }

    fun hasPin(): Boolean = graph.pinStore.hasPin()

    suspend fun initialLockRequired(): Boolean {
        val lockEnabled = graph.settings.settings.first().lockEnabled
        if (lockEnabled && !graph.pinStore.hasPin()) {
            graph.settings.setLockEnabled(false)
            return false
        }
        return lockEnabled
    }

    fun clearPin() {
        graph.pinStore.clear()
        setLockEnabled(false)
    }
}

private fun ImportFailure.messageResource(): Int = when (this) {
    ImportFailure.EmptyFile -> R.string.alt_003
    ImportFailure.BinaryFile -> R.string.alt_004
    ImportFailure.UnsupportedEncoding -> R.string.alt_001
    ImportFailure.FileTooLarge -> R.string.alt_005
    ImportFailure.StorageLimit -> R.string.alt_006
    is ImportFailure.Duplicate -> R.string.alt_007
    ImportFailure.CannotOpen -> R.string.err_002
    ImportFailure.CannotSave -> R.string.err_004
}
