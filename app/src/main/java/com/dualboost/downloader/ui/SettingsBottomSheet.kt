package com.dualboost.downloader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.sp
import com.dualboost.downloader.data.ChunkConfig
import com.dualboost.downloader.data.ChunkPreferences
import com.dualboost.downloader.data.ConcurrencyConfig
import com.dualboost.downloader.data.ConcurrencyPreferences
import com.dualboost.downloader.data.StreamPalette
import com.dualboost.downloader.data.StreamPalettePreferences
import com.dualboost.downloader.data.StorageDestination
import com.dualboost.downloader.data.StoragePreferences
import com.dualboost.downloader.data.UserAgentConfig
import com.dualboost.downloader.data.UserAgentPreset
import com.dualboost.downloader.data.UserAgentPreferences
import com.dualboost.downloader.security.SecurityPreferences
import com.dualboost.downloader.data.SlicerPreferences
import com.dualboost.downloader.data.WorkerTestStatus
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    onDismiss: () -> Unit,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var localStreamPalette by remember { mutableStateOf(StreamPalettePreferences(context).getStreamPalette()) }
    val streamPaletteState = viewModel?.streamPalette?.collectAsState()
    val selectedPalette = streamPaletteState?.value ?: localStreamPalette

    fun updatePalette(palette: StreamPalette) {
        if (viewModel != null) {
            viewModel.updateStreamPalette(palette)
        } else {
            StreamPalettePreferences(context).setStreamPalette(palette)
            localStreamPalette = StreamPalettePreferences(context).getStreamPalette()
        }
    }

    fun resetPalette() {
        if (viewModel != null) {
            viewModel.resetStreamPaletteToDefault()
        } else {
            StreamPalettePreferences(context).resetToDefault()
            localStreamPalette = StreamPalettePreferences(context).getStreamPalette()
        }
    }

    var localConcurrencyConfig by remember { mutableStateOf(ConcurrencyPreferences(context).getConcurrencyConfig()) }
    val concurrencyState = viewModel?.concurrencyConfig?.collectAsState()
    val config = concurrencyState?.value ?: localConcurrencyConfig

    fun updateWifi(count: Int) {
        if (viewModel != null) {
            viewModel.updateWifiWorkers(count)
        } else {
            ConcurrencyPreferences(context).setWifiWorkers(count)
            localConcurrencyConfig = ConcurrencyPreferences(context).getConcurrencyConfig()
        }
    }

    fun updateCellular(count: Int) {
        if (viewModel != null) {
            viewModel.updateCellularWorkers(count)
        } else {
            ConcurrencyPreferences(context).setCellularWorkers(count)
            localConcurrencyConfig = ConcurrencyPreferences(context).getConcurrencyConfig()
        }
    }

    fun toggleAutoScale(enabled: Boolean) {
        if (viewModel != null) {
            viewModel.toggleAutoScale(enabled)
        } else {
            ConcurrencyPreferences(context).setAutoScaleEnabled(enabled)
            localConcurrencyConfig = ConcurrencyPreferences(context).getConcurrencyConfig()
        }
    }

    fun resetConcurrency() {
        if (viewModel != null) {
            viewModel.resetConcurrencyToDefault()
        } else {
            ConcurrencyPreferences(context).resetToDefault()
            localConcurrencyConfig = ConcurrencyPreferences(context).getConcurrencyConfig()
        }
    }

    var localStorageDestination by remember { mutableStateOf(StoragePreferences(context).getStorageDestination()) }
    val storageDestinationState = viewModel?.storageDestination?.collectAsState()
    val selectedDestination = storageDestinationState?.value ?: localStorageDestination

    fun updateDestination(dest: StorageDestination) {
        if (viewModel != null) {
            viewModel.updateStorageDestination(dest)
        } else {
            StoragePreferences(context).setStorageDestination(dest)
            localStorageDestination = StoragePreferences(context).getStorageDestination()
        }
    }

    fun resetDestination() {
        if (viewModel != null) {
            viewModel.resetStorageDestinationToDefault()
        } else {
            StoragePreferences(context).resetToDefault()
            localStorageDestination = StoragePreferences(context).getStorageDestination()
        }
    }

    var localChunkConfig by remember { mutableStateOf(ChunkPreferences(context).getChunkConfig()) }
    val chunkConfigState = viewModel?.chunkConfig?.collectAsState()
    val currentChunkConfig = chunkConfigState?.value ?: localChunkConfig

    fun updateManualChunk(sizeBytes: Long) {
        if (viewModel != null) {
            viewModel.updateManualChunkSize(sizeBytes)
        } else {
            ChunkPreferences(context).setManualChunkSizeBytes(sizeBytes)
            localChunkConfig = ChunkPreferences(context).getChunkConfig()
        }
    }

    fun toggleAutoAdaptiveChunk(enabled: Boolean) {
        if (viewModel != null) {
            viewModel.toggleAutoAdaptive(enabled)
        } else {
            ChunkPreferences(context).setAutoAdaptive(enabled)
            localChunkConfig = ChunkPreferences(context).getChunkConfig()
        }
    }

    fun resetChunkConfig() {
        if (viewModel != null) {
            viewModel.resetChunkConfigToDefault()
        } else {
            ChunkPreferences(context).resetToDefault()
            localChunkConfig = ChunkPreferences(context).getChunkConfig()
        }
    }

    var localUserAgentConfig by remember { mutableStateOf(UserAgentPreferences(context).getUserAgentConfig()) }
    val userAgentConfigState = viewModel?.userAgentConfig?.collectAsState()
    val currentUserAgentConfig = userAgentConfigState?.value ?: localUserAgentConfig

    var customUaInput by remember(currentUserAgentConfig.customUserAgentString) {
        mutableStateOf(currentUserAgentConfig.customUserAgentString)
    }

    fun updateUaPreset(preset: UserAgentPreset) {
        if (viewModel != null) {
            viewModel.updateUserAgentPreset(preset)
        } else {
            UserAgentPreferences(context).setPreset(preset)
            localUserAgentConfig = UserAgentPreferences(context).getUserAgentConfig()
        }
    }

    fun updateCustomUa(customUa: String) {
        if (viewModel != null) {
            viewModel.setCustomUserAgent(customUa)
        } else {
            UserAgentPreferences(context).setCustomUserAgent(customUa)
            localUserAgentConfig = UserAgentPreferences(context).getUserAgentConfig()
        }
    }

    fun resetUserAgent() {
        if (viewModel != null) {
            viewModel.resetUserAgentToDefault()
        } else {
            UserAgentPreferences(context).resetToDefault()
            localUserAgentConfig = UserAgentPreferences(context).getUserAgentConfig()
        }
        customUaInput = ""
    }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}

            val rawDocId = try {
                android.provider.DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Throwable) {
                null
            }

            val folderName = when {
                rawDocId != null -> rawDocId.substringAfterLast(':').substringAfterLast('/')
                else -> uri.lastPathSegment ?: "Custom Folder"
            }

            val physicalPath = when {
                rawDocId != null && rawDocId.startsWith("primary:") -> {
                    val rel = rawDocId.removePrefix("primary:")
                    android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + rel
                }
                else -> null
            }

            val customDest = StorageDestination(
                id = StorageDestination.CUSTOM_ID,
                displayName = folderName.ifBlank { "Custom Folder" },
                relativePath = "",
                customPath = physicalPath,
                customUriString = uri.toString()
            )
            updateDestination(customDest)
        }
    }

    var workerUrl by remember { mutableStateOf(SlicerPreferences.getSlicerUrl(context)) }
    var secretKey by remember { mutableStateOf(SlicerPreferences.getSlicerSecret(context)) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val vmTestStatus = viewModel?.workerTestStatus?.collectAsState()?.value
    var localTestStatus by remember { mutableStateOf<WorkerTestStatus>(WorkerTestStatus.Idle) }
    val testStatus = vmTestStatus ?: localTestStatus
    fun resetTestStatus() {
        if (viewModel != null) {
            viewModel.resetWorkerTestStatus()
        } else {
            localTestStatus = WorkerTestStatus.Idle
        }
    }
    var showSetupGuide by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = isSystemInDarkTheme()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Icon & Title (Sleek Circular Hero Badge with Translucent Brand Tint)
            val isConfigured = workerUrl.isNotBlank()
            val isDark = isSystemInDarkTheme()
            val brandColor = MaterialTheme.colorScheme.primary
            val activeGreen = if (isDark) Color(0xFF69F0AE) else Color(0xFF008940)

            val infiniteTransition = rememberInfiniteTransition(label = "SlicerDotPulse")
            val dotAlpha by if (isConfigured) {
                infiniteTransition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "DotAlpha"
                )
            } else {
                remember { mutableFloatStateOf(1f) }
            }

            Surface(
                shape = CircleShape,
                color = if (isConfigured) brandColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                border = BorderStroke(
                    1.5.dp,
                    if (isConfigured) brandColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isConfigured) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                        contentDescription = "Personal Cloud Slicer",
                        tint = if (isConfigured) brandColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Personal Cloud Slicer",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Turn single-stream dynamic links into dual-channel HTTP 206 byte ranges",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Elegant Status Pill Badge
            Surface(
                shape = CircleShape,
                color = if (isConfigured) activeGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(
                    1.dp,
                    if (isConfigured) activeGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConfigured) activeGreen.copy(alpha = dotAlpha) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConfigured) "Worker Active & Ready" else "Worker Not Configured",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isConfigured) activeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Explanatory Banner Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Your free Cloudflare Worker acts as an on-the-fly byte stream slicer when servers reject byte ranges. 100% free and private.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Worker URL Input Field
            OutlinedTextField(
                value = workerUrl,
                onValueChange = {
                    workerUrl = it
                    resetTestStatus()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cloudflare Worker URL") },
                placeholder = { Text("https://dualboost-slicer.subdomain.workers.dev") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (workerUrl.isNotBlank()) {
                        IconButton(onClick = {
                            workerUrl = ""
                            resetTestStatus()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear URL"
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // SEC-AUDIT-03: Secret Key Input Field with Hardware-Grade UI Masking & Eye Toggle
            OutlinedTextField(
                value = secretKey,
                onValueChange = {
                    secretKey = it
                    resetTestStatus()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Secret Key (Optional)") },
                placeholder = { Text("Leave blank if SECRET_KEY not configured") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide Secret Key" else "Show Secret Key",
                            tint = if (isPasswordVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Test Connection & Copy Worker Script
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Test Worker Connection Button
                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        if (workerUrl.isBlank()) {
                            Toast.makeText(context, "Please enter a Worker URL first", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        if (viewModel != null) {
                            viewModel.testWorkerConnectivity(workerUrl.trim(), secretKey.trim())
                        } else {
                            localTestStatus = WorkerTestStatus.Error("ViewModel not available")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    enabled = testStatus !is WorkerTestStatus.Testing && workerUrl.isNotBlank()
                ) {
                    if (testStatus is WorkerTestStatus.Testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...", fontSize = 13.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Worker", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Copy Worker Script Button
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Cloud Slicer Worker Script", SlicerPreferences.WORKER_SCRIPT_SOURCE)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Worker script copied to clipboard! Ready to paste into Cloudflare.", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Script", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Test Worker Feedback Banner
            AnimatedVisibility(
                visible = testStatus is WorkerTestStatus.Success || testStatus is WorkerTestStatus.Error,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                when (val status = testStatus) {
                    is WorkerTestStatus.Success -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1B5E20).copy(alpha = if (isDark) 0.35f else 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status.message,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
                                )
                            }
                        }
                    }
                    is WorkerTestStatus.Error -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expandable 60-Second Setup Guide
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Help,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "60-Second Cloudflare Setup",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        IconButton(
                            onClick = { showSetupGuide = !showSetupGuide },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (showSetupGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = "Toggle Setup Guide"
                            )
                        }
                    }

                    AnimatedVisibility(visible = showSetupGuide) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            SetupStepItem(
                                stepNumber = "1",
                                title = "Sign up free",
                                desc = "Create a free account at dash.cloudflare.com (no credit card needed)."
                            )
                            SetupStepItem(
                                stepNumber = "2",
                                title = "Create Worker",
                                desc = "Navigate to Workers & Pages → Create application → Create Worker."
                            )
                            SetupStepItem(
                                stepNumber = "3",
                                title = "Paste & Deploy",
                                desc = "Tap 'Copy Script' above, replace the default code in Cloudflare, and click Deploy."
                            )
                            SetupStepItem(
                                stepNumber = "4",
                                title = "Paste URL Here",
                                desc = "Copy your *.workers.dev link, paste it into the Worker URL box above, and Save!"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Antivirus & Threat Guard Section
            var threatGuardActive by remember {
                mutableStateOf(SecurityPreferences.isThreatGuardEnabled(context))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (threatGuardActive) Color(0xFF1B5E20).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Security,
                                        contentDescription = "Threat Guard",
                                        tint = if (threatGuardActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Antivirus & Threat Guard",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (threatGuardActive) "Active • Blocking malware & fake APKs" else "Disabled • Downloads uninspected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (threatGuardActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = threatGuardActive,
                            onCheckedChange = { checked ->
                                threatGuardActive = checked
                                SecurityPreferences.setThreatGuardEnabled(context, checked)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Real-time 3-stage inspection blocks disguised APKs, double extensions (.mkv.apk), executable payloads, and fake MIME types before saving bytes to disk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-Thread Concurrency Card (ADR-042)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Speed,
                                        contentDescription = "Multi-Thread Concurrency",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Multi-Thread Concurrency",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${config.totalWorkers} Streams (${config.wifiWorkers} Wi-Fi + ${config.cellularWorkers} Cellular)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                resetConcurrency()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Wi-Fi Stream Stepper
                    ConcurrencyStepper(
                        label = "Wi-Fi Streams",
                        value = config.wifiWorkers,
                        onValueChange = { updateWifi(it) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Cellular Stream Stepper
                    ConcurrencyStepper(
                        label = "Cellular Streams",
                        value = config.cellularWorkers,
                        onValueChange = { updateCellular(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-Scale Mode Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Scale High-Throughput",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (config.isAutoScaleEnabled) {
                                    "Expands streams on >25 MB/s under cool thermals"
                                } else {
                                    "Keep fixed worker counts across all links"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = config.isAutoScaleEnabled,
                            onCheckedChange = { toggleAutoScale(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Thermal Fail-Safe Notice
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Thermostat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Fail-safe thermal rules take priority: MODERATE caps cellular to 1 stream; SEVERE offloads completely to Wi-Fi.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Appearance & Stream Visualizer Theme Card (ADR-043)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = "Stream Visualizer Theme",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Stream Visualizer Theme",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = selectedPalette.displayName + " • " + selectedPalette.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                resetPalette()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StreamPalette.entries.forEach { palette ->
                            val isSelected = palette == selectedPalette
                            val wifiPreview = palette.resolveWifiColor(isDark, MaterialTheme.colorScheme.primary)
                            val cellPreview = palette.resolveCellularColor(isDark, MaterialTheme.colorScheme.tertiary)

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        updatePalette(palette)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                                .padding(3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 16.dp, height = 14.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(wifiPreview)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 16.dp, height = 14.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(cellPreview)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = palette.displayName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = palette.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Download Storage & Destination Card (ADR-044)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Download Storage",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Target directory & folder selection",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (selectedDestination.id != StorageDestination.DEFAULT_ID) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    resetDestination()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Path Preview Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selectedDestination.isCustom()) Icons.Rounded.FolderSpecial else Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedDestination.displayName,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val pathPreview = when {
                                    !selectedDestination.customPath.isNullOrBlank() -> selectedDestination.customPath
                                    selectedDestination.relativePath.isNotBlank() -> "Downloads/${selectedDestination.relativePath}"
                                    else -> "Downloads (Default)"
                                }
                                Text(
                                    text = pathPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Presets Row
                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StorageDestination.PRESETS.forEach { preset ->
                            val isSelected = selectedDestination.id == preset.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    updateDestination(preset)
                                },
                                label = {
                                    Text(
                                        text = preset.displayName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Choose Custom Folder Button (SAF)
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            try {
                                directoryPickerLauncher.launch(null)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedDestination.isCustom()) "Change Custom Folder..." else "Choose Custom Folder (SAF)...",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Safe Fallback Caption
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Auto-falls back to standard Downloads if storage is unmounted or unwritable.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Chunk Slicing Strategy (ADR-045)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Chunk Slicing Strategy",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (currentChunkConfig.isAutoAdaptive) {
                                        "Auto-Adaptive (1 MB - 64 MB based on link & payload)"
                                    } else {
                                        val mb = currentChunkConfig.manualChunkSizeBytes / (1024 * 1024L)
                                        "Manual ($mb MB fixed segments)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!currentChunkConfig.isAutoAdaptive || currentChunkConfig.manualChunkSizeBytes != ChunkConfig.DEFAULT_CHUNK_SIZE_BYTES) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    resetChunkConfig()
                                }
                            ) {
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto-Adaptive Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                toggleAutoAdaptiveChunk(!currentChunkConfig.isAutoAdaptive)
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Adaptive Sizing",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Scales dynamically (1 MB small/spotty, up to 64 MB Wi-Fi 7/Gigabit)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = currentChunkConfig.isAutoAdaptive,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                toggleAutoAdaptiveChunk(it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Manual Chunk Size Chips
                    Text(
                        text = "Chunk Segment Size Tiers",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChunkConfig.SUPPORTED_TIERS.forEach { tierBytes ->
                            val mb = tierBytes / (1024 * 1024L)
                            val isSelected = !currentChunkConfig.isAutoAdaptive && currentChunkConfig.manualChunkSizeBytes == tierBytes
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    toggleAutoAdaptiveChunk(false)
                                    updateManualChunk(tierBytes)
                                },
                                label = {
                                    Text(
                                        text = "$mb MB",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Resumption Notice Caption
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Resumed downloads strictly preserve original segment size to prevent file corruption.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Browser Identity & User-Agent (ADR-046)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Browser Identity (User-Agent)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentUserAgentConfig.preset.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (currentUserAgentConfig.preset != UserAgentPreset.NATIVE || currentUserAgentConfig.customUserAgentString.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    resetUserAgent()
                                }
                            ) {
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Controls how download servers identify your phone model. Bypasses mobile throttling and desktop-only range blocks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preset Chips
                    Text(
                        text = "Device Profile Presets",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserAgentPreset.entries.forEach { preset ->
                            val isSelected = currentUserAgentConfig.preset == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    updateUaPreset(preset)
                                },
                                label = {
                                    Text(
                                        text = preset.displayName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }

                    // Custom UA input field if CUSTOM preset is selected
                    AnimatedVisibility(visible = currentUserAgentConfig.preset == UserAgentPreset.CUSTOM) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            OutlinedTextField(
                                value = customUaInput,
                                onValueChange = {
                                    customUaInput = it
                                    updateCustomUa(it)
                                },
                                label = { Text("Custom User-Agent String") },
                                placeholder = { Text("e.g. Mozilla/5.0 ...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                                maxLines = 3,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    if (customUaInput.isNotBlank()) {
                                        IconButton(onClick = {
                                            customUaInput = ""
                                            updateCustomUa("")
                                        }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Clear",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live UA String Preview Box with 1-tap Copy
                    val activeUaString = currentUserAgentConfig.resolveUserAgentString()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Active Header Preview",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("User-Agent", activeUaString)
                                        clipboard.setPrimaryClip(clip)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        Toast.makeText(context, "User-Agent copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy User-Agent",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeUaString,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Resumption Session Invariance Caption
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Session Resumption Invariance: Resumed downloads preserve their original User-Agent to prevent CDN 403 Forbidden errors.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save and Clear Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (SlicerPreferences.getSlicerUrl(context).isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            SlicerPreferences.setSlicerUrl(context, "")
                            SlicerPreferences.setSlicerSecret(context, "")
                            workerUrl = ""
                            secretKey = ""
                            Toast.makeText(context, "Cloud Slicer disabled", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disable", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        val trimmedUrl = workerUrl.trim()
                        val trimmedSecret = secretKey.trim()
                        SlicerPreferences.setSlicerUrl(context, trimmedUrl)
                        SlicerPreferences.setSlicerSecret(context, trimmedSecret)
                        SecurityPreferences.setThreatGuardEnabled(context, threatGuardActive)
                        Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(if (SlicerPreferences.getSlicerUrl(context).isNotBlank()) 1.4f else 1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SetupStepItem(
    stepNumber: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stepNumber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ConcurrencyStepper(
    label: String,
    value: Int,
    range: IntRange = ConcurrencyConfig.MIN_WORKERS..ConcurrencyConfig.MAX_WORKERS,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$value parallel streams",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = { if (value > range.first) onValueChange(value - 1) },
                enabled = value > range.first,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label", modifier = Modifier.size(18.dp))
            }

            Text(
                text = "$value",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center
            )

            FilledTonalIconButton(
                onClick = { if (value < range.last) onValueChange(value + 1) },
                enabled = value < range.last,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Increase $label", modifier = Modifier.size(18.dp))
            }
        }
    }
}


