package com.narro.app.playback.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.narro.app.MainActivity
import com.narro.app.NarroApplication
import com.narro.app.R
import com.narro.app.domain.model.Document
import com.narro.app.domain.model.DocumentSegment
import com.narro.app.domain.model.ReadingPosition
import com.narro.app.playback.model.PlaybackSnapshot
import com.narro.app.playback.model.PlaybackStateStore
import com.narro.app.playback.model.PlaybackStatus
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TtsPlaybackService : Service(), TextToSpeech.OnInitListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { (application as NarroApplication).graph.documents }
    private val settings by lazy { (application as NarroApplication).graph.settings }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private lateinit var mediaSession: MediaSessionCompat
    private var tts: TextToSpeech? = null
    private var initialized = false
    private var pendingStart: StartRequest? = null
    private var activeDocument: Document? = null
    private var currentSessionId: String? = null
    private var currentPosition = ReadingPosition(0, 0)
    private var speechRate = 1.0f
    private var pauseReason: PlaybackStatus? = null
    private var queueJob: Job? = null
    private var highestQueuedSegment = -1
    private val utterances = ConcurrentHashMap<String, UtteranceMeta>()
    private val queueMutex = Mutex()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> if (pauseReason == PlaybackStatus.PAUSED_BY_FOCUS) resumeInternal()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pauseInternal(PlaybackStatus.PAUSED_BY_FOCUS)
            AudioManager.AUDIOFOCUS_LOSS -> stopAndSave()
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stopAndSave()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "NarroPlayback").apply {
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() = resumeInternal()
                    override fun onPause() = pauseInternal(PlaybackStatus.PAUSED_BY_USER)
                    override fun onStop() = stopAndSave()
                },
            )
            isActive = true
        }
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val documentId = intent.getStringExtra(EXTRA_DOCUMENT_ID) ?: return START_NOT_STICKY
                val request = StartRequest(
                    documentId = documentId,
                    sentenceIndex = intent.getIntExtra(EXTRA_SENTENCE_INDEX, 0),
                    characterOffset = intent.getLongExtra(EXTRA_CHARACTER_OFFSET, 0),
                    speechRate = intent.getFloatExtra(EXTRA_SPEECH_RATE, 1f),
                )
                startForeground(NOTIFICATION_ID, buildNotification(PlaybackStatus.INITIALIZING, "Narro"))
                if (initialized) startDocument(request) else pendingStart = request
            }
            ACTION_PAUSE -> pauseInternal(PlaybackStatus.PAUSED_BY_USER)
            ACTION_RESUME -> resumeInternal()
            ACTION_TOGGLE -> if (PlaybackStateStore.state.value.status == PlaybackStatus.PLAYING) {
                pauseInternal(PlaybackStatus.PAUSED_BY_USER)
            } else {
                resumeInternal()
            }
            ACTION_STOP_SAVE -> stopAndSave()
        }
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        initialized = status == TextToSpeech.SUCCESS
        if (!initialized) {
            PlaybackStateStore.update { it.copy(status = PlaybackStatus.ERROR, error = "tts_init") }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        tts?.setOnUtteranceProgressListener(listener)
        pendingStart?.also(::startDocument)
        pendingStart = null
    }

    private fun startDocument(request: StartRequest) {
        queueJob?.cancel()
        tts?.stop()
        utterances.clear()
        currentSessionId = UUID.randomUUID().toString()
        currentPosition = ReadingPosition(request.sentenceIndex, request.characterOffset)
        speechRate = request.speechRate.coerceIn(0.5f, 2f)
        pauseReason = null
        highestQueuedSegment = -1
        PlaybackStateStore.set(
            PlaybackSnapshot(
                sessionId = currentSessionId,
                documentId = request.documentId,
                status = PlaybackStatus.INITIALIZING,
                position = currentPosition,
            ),
        )
        queueJob = scope.launch {
            val document = repository.getDocument(request.documentId)
            if (document == null) {
                fail("document_missing")
                return@launch
            }
            activeDocument = document
            if (!configureLanguage(document.speechLocaleTag)) {
                fail("tts_language")
                return@launch
            }
            tts?.setSpeechRate(speechRate)
            if (!requestAudioFocus()) {
                fail("audio_focus")
                return@launch
            }
            val segmentIndex = repository.findSegmentIndex(document.id, request.sentenceIndex)
            highestQueuedSegment = segmentIndex - 1
            PlaybackStateStore.update {
                it.copy(documentName = document.displayName, status = PlaybackStatus.PLAYING)
            }
            updateNotification(PlaybackStatus.PLAYING)
            queueFrom(segmentIndex, request.sentenceIndex, firstQueue = true)
        }
    }

    private suspend fun queueFrom(segmentIndex: Int, sentenceIndex: Int, firstQueue: Boolean) {
        queueMutex.withLock {
            queueFromLocked(segmentIndex, sentenceIndex, firstQueue)
        }
    }

    private suspend fun queueFromLocked(segmentIndex: Int, sentenceIndex: Int, firstQueue: Boolean) {
        val document = activeDocument ?: return
        val window = repository.loadSegmentWindow(document.id, segmentIndex)
            .filter { it.index >= segmentIndex && it.index > highestQueuedSegment }
        if (window.isEmpty()) {
            if (utterances.isEmpty()) completeDocument()
            return
        }
        var queueMode = if (firstQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        for (segment in window) {
            val chunks = splitSegment(segment).filter { it.endSentenceIndex >= sentenceIndex }
            for (chunk in chunks) {
                val id = "${currentSessionId}:${segment.index}:${chunk.startSentenceIndex}:${UUID.randomUUID()}"
                utterances[id] = UtteranceMeta(
                    sessionId = currentSessionId.orEmpty(),
                    segmentIndex = segment.index,
                    endSentenceIndex = chunk.endSentenceIndex,
                    endCharacterOffset = chunk.endCharacterOffset,
                )
                val result = tts?.speak(chunk.text, queueMode, null, id)
                if (result == TextToSpeech.ERROR) {
                    utterances.remove(id)
                    fail("tts_speak")
                    return
                }
                queueMode = TextToSpeech.QUEUE_ADD
            }
            highestQueuedSegment = maxOf(highestQueuedSegment, segment.index)
        }
    }

    private fun splitSegment(segment: DocumentSegment): List<SpeechChunk> {
        val maxInput = minOf(MAX_UTTERANCE, TextToSpeech.getMaxSpeechInputLength())
        val chunks = mutableListOf<SpeechChunk>()
        val builder = StringBuilder()
        var sentence = segment.startSentenceIndex
        var chunkStartSentence = sentence
        var offset = segment.startCharacterOffset
        var chunkEndOffset = offset
        var index = 0

        fun emit(): Boolean {
            if (builder.isBlank()) {
                builder.clear()
                return false
            }
            chunks += SpeechChunk(builder.toString(), chunkStartSentence, sentence, chunkEndOffset)
            builder.clear()
            chunkStartSentence = sentence + 1
            return true
        }

        while (index < segment.text.length) {
            val cp = segment.text.codePointAt(index)
            val value = String(Character.toChars(cp))
            builder.append(value)
            offset++
            chunkEndOffset = offset
            val boundary = cp == '.'.code || cp == '!'.code || cp == '?'.code ||
                cp == '\n'.code || cp == 0x3002 || cp == 0xFF01 || cp == 0xFF1F
            if (boundary || builder.codePointCount(0, builder.length) >= maxInput) {
                if (emit()) sentence++
            }
            index += Character.charCount(cp)
        }
        if (builder.isNotEmpty()) emit()
        return chunks
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            val meta = utteranceId?.let(utterances::remove) ?: return
            if (meta.sessionId != currentSessionId) return
            currentPosition = ReadingPosition(meta.endSentenceIndex + 1, meta.endCharacterOffset)
            PlaybackStateStore.update { it.copy(position = currentPosition) }
            if (utterances.size <= PRELOAD_THRESHOLD) {
                scope.launch {
                    val next = highestQueuedSegment + 1
                    val document = activeDocument ?: return@launch
                    if (next < document.totalSegmentCount) {
                        queueFrom(next, currentPosition.sentenceIndex, firstQueue = false)
                    } else if (utterances.isEmpty()) {
                        completeDocument()
                    }
                }
            }
        }

        override fun onError(utteranceId: String?) {
            if (utteranceId?.let(utterances::get)?.sessionId == currentSessionId) fail("tts_playback")
        }

        override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
    }

    private fun pauseInternal(reason: PlaybackStatus) {
        if (PlaybackStateStore.state.value.status != PlaybackStatus.PLAYING) return
        tts?.stop()
        utterances.clear()
        pauseReason = reason
        PlaybackStateStore.update { it.copy(status = reason) }
        updateNotification(reason)
        updateMediaSession(reason)
    }

    private fun resumeInternal() {
        val document = activeDocument ?: return
        if (PlaybackStateStore.state.value.status !in setOf(
                PlaybackStatus.PAUSED_BY_USER,
                PlaybackStatus.PAUSED_BY_FOCUS,
            )
        ) return
        if (!requestAudioFocus()) return
        pauseReason = null
        currentSessionId = UUID.randomUUID().toString()
        highestQueuedSegment = -1
        PlaybackStateStore.update { it.copy(sessionId = currentSessionId, status = PlaybackStatus.PLAYING) }
        updateNotification(PlaybackStatus.PLAYING)
        updateMediaSession(PlaybackStatus.PLAYING)
        scope.launch {
            val segment = repository.findSegmentIndex(document.id, currentPosition.sentenceIndex)
            queueFrom(segment, currentPosition.sentenceIndex, firstQueue = true)
        }
    }

    private fun stopAndSave() {
        val document = activeDocument
        val position = currentPosition
        currentSessionId = null
        queueJob?.cancel()
        tts?.stop()
        utterances.clear()
        abandonAudioFocus()
        PlaybackStateStore.update { it.copy(sessionId = null, status = PlaybackStatus.STOPPED) }
        if (document == null) {
            stopServiceNow()
            return
        }
        scope.launch {
            repository.saveConfirmedPosition(document.id, position)
            stopServiceNow()
        }
    }

    private suspend fun completeDocument() {
        val document = activeDocument ?: return
        val completed = ReadingPosition(document.totalSentenceCount, document.totalCharacterCount)
        currentPosition = completed
        repository.saveConfirmedPosition(document.id, completed)
        abandonAudioFocus()
        PlaybackStateStore.update { it.copy(status = PlaybackStatus.COMPLETED, position = completed) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(error: String) {
        tts?.stop()
        utterances.clear()
        abandonAudioFocus()
        PlaybackStateStore.update { it.copy(status = PlaybackStatus.ERROR, error = error) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun configureLanguage(tag: String): Boolean {
        val result = tts?.setLanguage(Locale.forLanguageTag(tag)) ?: TextToSpeech.ERROR
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(audioFocusRequest)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    private val audioFocusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(true)
            .build()
    }

    private fun buildNotification(status: PlaybackStatus, documentName: String): Notification {
        val paused = status != PlaybackStatus.PLAYING
        val toggleAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val toggleIcon = if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val toggleLabel = getString(if (paused) R.string.play else R.string.pause)
        val toggle = PendingIntent.getService(
            this,
            1,
            Intent(this, TtsPlaybackService::class.java).setAction(toggleAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, TtsPlaybackService::class.java).setAction(ACTION_STOP_SAVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (paused) getString(R.string.notification_paused, documentName)
        else getString(R.string.notification_playing, documentName)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(getString(R.string.app_name))
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(toggleIcon, toggleLabel, toggle)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stop)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1),
            )
            .build()
    }

    private fun updateNotification(status: PlaybackStatus) {
        val name = activeDocument?.displayName ?: "Narro"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status, name))
        updateMediaSession(status)
    }

    private fun updateMediaSession(status: PlaybackStatus) {
        val playbackState = when (status) {
            PlaybackStatus.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            PlaybackStatus.PAUSED_BY_USER, PlaybackStatus.PAUSED_BY_FOCUS -> PlaybackStateCompat.STATE_PAUSED
            PlaybackStatus.COMPLETED -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_NONE
        }
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP,
                )
                .setState(playbackState, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun stopServiceNow() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopAndSave()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisyReceiver) }
        mediaSession.release()
        tts?.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class StartRequest(
        val documentId: String,
        val sentenceIndex: Int,
        val characterOffset: Long,
        val speechRate: Float,
    )

    private data class UtteranceMeta(
        val sessionId: String,
        val segmentIndex: Int,
        val endSentenceIndex: Int,
        val endCharacterOffset: Long,
    )

    private data class SpeechChunk(
        val text: String,
        val startSentenceIndex: Int,
        val endSentenceIndex: Int,
        val endCharacterOffset: Long,
    )

    companion object {
        private const val CHANNEL_ID = "narro_playback"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_UTTERANCE = 3_000
        private const val PRELOAD_THRESHOLD = 3
        const val ACTION_PLAY = "com.narro.app.action.PLAY"
        const val ACTION_PAUSE = "com.narro.app.action.PAUSE"
        const val ACTION_RESUME = "com.narro.app.action.RESUME"
        const val ACTION_TOGGLE = "com.narro.app.action.TOGGLE"
        const val ACTION_STOP_SAVE = "com.narro.app.action.STOP_SAVE"
        const val EXTRA_DOCUMENT_ID = "document_id"
        const val EXTRA_SENTENCE_INDEX = "sentence_index"
        const val EXTRA_CHARACTER_OFFSET = "character_offset"
        const val EXTRA_SPEECH_RATE = "speech_rate"

        fun play(
            context: Context,
            documentId: String,
            position: ReadingPosition,
            speechRate: Float,
        ) {
            val intent = Intent(context, TtsPlaybackService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_DOCUMENT_ID, documentId)
                .putExtra(EXTRA_SENTENCE_INDEX, position.sentenceIndex)
                .putExtra(EXTRA_CHARACTER_OFFSET, position.characterOffset)
                .putExtra(EXTRA_SPEECH_RATE, speechRate)
            ContextCompat.startForegroundService(context, intent)
        }

        fun command(context: Context, action: String) {
            context.startService(Intent(context, TtsPlaybackService::class.java).setAction(action))
        }
    }
}
