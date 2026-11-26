package com.dualboost.downloader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dualboost.downloader.social.MediaQuality
import com.dualboost.downloader.social.SocialIntakeState
import com.dualboost.downloader.social.SocialPlatform
import com.dualboost.downloader.ui.theme.DualBoostIcons
import com.dualboost.downloader.ui.theme.expressiveButtonShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SocialMediaModalSheet(
    state: SocialIntakeState,
    cellularGeneration: com.dualboost.downloader.network.CellularGeneration = com.dualboost.downloader.network.CellularGeneration.UNKNOWN,
    wifiDetails: com.dualboost.downloader.network.WifiDetails = com.dualboost.downloader.network.WifiDetails.DISCONNECTED,
    onDismiss: () -> Unit,
    onDownload: (url: String, quality: MediaQuality) -> Unit,
    onEnqueue: (url: String, quality: MediaQuality) -> Unit,
    onRetry: (url: String, quality: MediaQuality) -> Unit = { _, _ -> }
) {
    if (state is SocialIntakeState.Idle) return

    val haptic = LocalHapticFeedback.current
    var selectedQuality by remember { mutableStateOf(MediaQuality.MAX) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is SocialIntakeState.Ready -> {
                    val platform = state.platform
                    val brandColor = Color(platform.brandColorHex)

                    // Platform Header Badge
                    Surface(
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                        color = brandColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.5.dp, brandColor.copy(alpha = 0.4f)),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(
                                    when (platform) {
                                        SocialPlatform.YOUTUBE -> DualBoostIcons.PlayArrow
                                        SocialPlatform.INSTAGRAM -> DualBoostIcons.PhotoCamera
                                        SocialPlatform.SOUNDCLOUD -> DualBoostIcons.MusicNote
                                        else -> DualBoostIcons.Share
                                    }
                                ),
                                contentDescription = platform.displayName,
                                tint = brandColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${platform.displayName} Media Detected",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Choose quality to accelerate download over ${wifiDetails.shortLabel} + ${cellularGeneration.shortLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // URL Preview
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(DualBoostIcons.Link),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.originalUrl,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Quality Selection Segmented List (Material 3 1.5.0 SegmentedListItem)
                    Text(
                        text = "Select Download Quality",
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        state.availableQualities.forEachIndexed { index, quality ->
                            val isSelected = selectedQuality == quality
                            SegmentedListItem(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedQuality = quality
                                },
                                shapes = ListItemDefaults.segmentedShapes(
                                    index = index,
                                    count = state.availableQualities.size
                                ),
                                colors = ListItemDefaults.segmentedColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedLeadingContentColor = MaterialTheme.colorScheme.primary,
                                    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                ),
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(
                                            when (quality) {
                                                MediaQuality.MAX -> DualBoostIcons.HighQuality
                                                MediaQuality.HD_720 -> DualBoostIcons.Hd
                                                MediaQuality.AUDIO_ONLY -> DualBoostIcons.Audiotrack
                                            }
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = when (quality) {
                                            MediaQuality.MAX -> "Best resolution with pristine audio track"
                                            MediaQuality.HD_720 -> "Faster download, perfect for mobile screen"
                                            MediaQuality.AUDIO_ONLY -> "Extract MP3 audio track only"
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                trailingContent = {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedQuality = quality
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = quality.label,
                                    style = MaterialTheme.typography.titleSmallEmphasized
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Expressive ButtonGroup Action Bar
                    val queueInteraction = remember { MutableInteractionSource() }
                    val boostInteraction = remember { MutableInteractionSource() }
                    ButtonGroup(
                        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        customItem(
                            buttonGroupContent = {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onEnqueue(state.originalUrl, selectedQuality)
                                    },
                                    shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                    interactionSource = queueInteraction,
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(queueInteraction, compressionLimit = 8.dp)
                                ) {
                                    Icon(painter = painterResource(DualBoostIcons.PlaylistAdd), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add to Queue", style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
                                }
                            },
                            menuContent = { menuState ->
                                DropdownMenuItem(
                                    text = { Text("Add to Queue") },
                                    onClick = {
                                        menuState.dismiss()
                                        onEnqueue(state.originalUrl, selectedQuality)
                                    }
                                )
                            }
                        )

                        customItem(
                            buttonGroupContent = {
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDownload(state.originalUrl, selectedQuality)
                                    },
                                    shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                    interactionSource = boostInteraction,
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .animateWidth(boostInteraction, compressionLimit = 8.dp)
                                ) {
                                    Icon(painter = painterResource(DualBoostIcons.Bolt), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Boost Download", style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
                                }
                            },
                            menuContent = { menuState ->
                                DropdownMenuItem(
                                    text = { Text("Boost Download") },
                                    onClick = {
                                        menuState.dismiss()
                                        onDownload(state.originalUrl, selectedQuality)
                                    }
                                )
                            }
                        )
                    }
                }

                is SocialIntakeState.Resolving -> {
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Extracting High-Quality Stream...",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Connecting to high-speed resolver for ${state.platform.displayName}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                is SocialIntakeState.Error -> {
                    Surface(
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(DualBoostIcons.Error),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Could Not Extract Stream",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    if (state.platform != null) {
                        val dismissInteraction = remember { MutableInteractionSource() }
                        val retryInteraction = remember { MutableInteractionSource() }
                        ButtonGroup(
                            overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            customItem(
                                buttonGroupContent = {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                        interactionSource = dismissInteraction,
                                        modifier = Modifier
                                            .weight(1f)
                                            .animateWidth(dismissInteraction, compressionLimit = 8.dp)
                                    ) {
                                        Text("Dismiss", maxLines = 1)
                                    }
                                },
                                menuContent = { menuState ->
                                    DropdownMenuItem(
                                        text = { Text("Dismiss") },
                                        onClick = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                }
                            )
                            customItem(
                                buttonGroupContent = {
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onRetry(state.url, state.attemptedQuality)
                                        },
                                        shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                        interactionSource = retryInteraction,
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .animateWidth(retryInteraction, compressionLimit = 8.dp)
                                    ) {
                                        Icon(painter = painterResource(DualBoostIcons.Refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry", maxLines = 1)
                                    }
                                },
                                menuContent = { menuState ->
                                    DropdownMenuItem(
                                        text = { Text("Retry") },
                                        onClick = {
                                            menuState.dismiss()
                                            onRetry(state.url, state.attemptedQuality)
                                        }
                                    )
                                }
                            )
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dismiss")
                        }
                    }
                }

                else -> {}
            }
        }
    }
}
