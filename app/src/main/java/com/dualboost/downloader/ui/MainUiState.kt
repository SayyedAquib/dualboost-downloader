package com.dualboost.downloader.ui

import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.engine.DualDownloaderEngine

/**
 * Explicit clipboard suggestion state replacing paired `hasClipboardContent: Boolean`
 * and `detectedClipboardUrl: String?` flows.
 */
sealed class ClipboardSuggestionState {
    object Hidden : ClipboardSuggestionState()
    object TextAvailable : ClipboardSuggestionState()
    data class UrlDetected(val url: String) : ClipboardSuggestionState()

    val isVisible: Boolean
        get() = this !is Hidden
}

/**
 * Explicit tri-state mode for Watch While Downloading (sequential video streaming),
 * replacing nullable `Boolean?` (`null` = Auto, `true` = On, `false` = Off).
 */
enum class SequentialStreamMode(val legacyValue: Boolean?) {
    AUTO(null),
    FORCE_ON(true),
    FORCE_OFF(false);

    companion object {
        fun fromLegacy(value: Boolean?): SequentialStreamMode {
            return when (value) {
                true -> FORCE_ON
                false -> FORCE_OFF
                null -> AUTO
            }
        }
    }
}

/**
 * Resolved presentation state for the "Watch While Downloading" toggle row.
 */
data class SequentialStreamUiState(
    val mode: SequentialStreamMode,
    val isAutoVideoUrl: Boolean,
    val isEffectiveSequential: Boolean,
    val statusText: String
) {
    val isManualOverride: Boolean
        get() = mode != SequentialStreamMode.AUTO

    companion object {
        fun resolve(mode: SequentialStreamMode, urlInput: String): SequentialStreamUiState {
            val isAutoVideo = mode == SequentialStreamMode.AUTO && DualDownloaderEngine.isVideoFile(urlInput)
            val isActive = mode == SequentialStreamMode.FORCE_ON || isAutoVideo
            val statusText = when (mode) {
                SequentialStreamMode.FORCE_ON -> "On • Downloads in order to preview early"
                SequentialStreamMode.FORCE_OFF -> "Off • Maximum speed for files & games"
                SequentialStreamMode.AUTO -> {
                    if (isAutoVideo) "Video detected • Ready to watch while downloading"
                    else "Auto • Activates automatically for video links"
                }
            }
            return SequentialStreamUiState(
                mode = mode,
                isAutoVideoUrl = isAutoVideo,
                isEffectiveSequential = isActive,
                statusText = statusText
            )
        }
    }
}

/**
 * Explicit OEM battery exemption guidance lifecycle.
 */
enum class BatteryGuidanceState {
    NOT_REQUIRED,
    ACTION_REQUIRED,
    EXEMPTED,
    DISMISSED;

    val shouldShowBanner: Boolean
        get() = this == ACTION_REQUIRED

    fun canTransitionTo(next: BatteryGuidanceState): Boolean {
        if (this == next) return true
        return when (this) {
            NOT_REQUIRED -> false
            ACTION_REQUIRED -> next == EXEMPTED || next == DISMISSED
            EXEMPTED -> next == ACTION_REQUIRED
            DISMISSED -> next == EXEMPTED
        }
    }
}

/**
 * Explicit modal dialog coordinator preventing overlapping confirmation dialogs in [MainScreen].
 */
sealed class MainDialogState {
    object None : MainDialogState()

    data class ConfirmCancelOrDiscard(
        val fileName: String?,
        val isPaused: Boolean
    ) : MainDialogState()

    object ConfirmBackgroundExit : MainDialogState()
}

/**
 * Unified state machine for the primary download CTA button.
 * Combines enablement rules and button labeling into a single source of truth.
 */
sealed class PrimaryActionUiState(
    val label: String,
    val isEnabled: Boolean,
    val isResumeStyle: Boolean = false
) {
    data class ReadyToBoost(val url: String) : PrimaryActionUiState("Boost Download", isEnabled = true)
    data class ReadyToResume(val url: String) : PrimaryActionUiState("Resume Download", isEnabled = true, isResumeStyle = true)
    object Probing : PrimaryActionUiState("Probing Server...", isEnabled = false)
    object Downloading : PrimaryActionUiState("Boost Download", isEnabled = false)
    data class DisabledInvalidUrl(val isResume: Boolean) : PrimaryActionUiState(
        label = if (isResume) "Resume Download" else "Boost Download",
        isEnabled = false,
        isResumeStyle = isResume
    )

    companion object {
        fun resolve(state: DownloadState, rawInput: String): PrimaryActionUiState {
            val trimmed = rawInput.trim()
            val isValidHttp = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)

            return when (state) {
                is DownloadState.Probing -> Probing
                is DownloadState.Downloading -> Downloading
                is DownloadState.Paused -> {
                    val matchesPausedUrl = state.url.isBlank() || trimmed.equals(state.url.trim(), ignoreCase = false)
                    if (!isValidHttp) {
                        DisabledInvalidUrl(isResume = matchesPausedUrl)
                    } else if (matchesPausedUrl) {
                        ReadyToResume(trimmed)
                    } else {
                        ReadyToBoost(trimmed)
                    }
                }
                else -> {
                    if (isValidHttp) ReadyToBoost(trimmed) else DisabledInvalidUrl(isResume = false)
                }
            }
        }
    }
}
