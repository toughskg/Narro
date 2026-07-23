package com.narro.app.playback.model

import com.narro.app.domain.model.ReadingPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackStatus {
    IDLE,
    INITIALIZING,
    PLAYING,
    PAUSED_BY_USER,
    PAUSED_BY_FOCUS,
    STOPPED,
    COMPLETED,
    ERROR,
}

data class PlaybackSnapshot(
    val sessionId: String? = null,
    val documentId: String? = null,
    val documentName: String = "",
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val position: ReadingPosition = ReadingPosition(0, 0),
    val error: String? = null,
)

object PlaybackStateStore {
    private val mutableState = MutableStateFlow(PlaybackSnapshot())
    val state = mutableState.asStateFlow()

    fun update(transform: (PlaybackSnapshot) -> PlaybackSnapshot) {
        mutableState.value = transform(mutableState.value)
    }

    fun set(snapshot: PlaybackSnapshot) {
        mutableState.value = snapshot
    }
}
