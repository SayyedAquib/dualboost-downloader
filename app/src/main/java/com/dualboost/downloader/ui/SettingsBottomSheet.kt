package com.dualboost.downloader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.unit.sp
import com.dualboost.downloader.data.SlicerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class WorkerTestStatus {
    object Idle : WorkerTestStatus()
    object Testing : WorkerTestStatus()
    data class Success(val message: String) : WorkerTestStatus()
    data class Error(val message: String) : WorkerTestStatus()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var workerUrl by remember { mutableStateOf(SlicerPreferences.getSlicerUrl(context)) }
    var secretKey by remember { mutableStateOf(SlicerPreferences.getSlicerSecret(context)) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<WorkerTestStatus>(WorkerTestStatus.Idle) }
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
            // Header Icon & Title
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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

            Spacer(modifier = Modifier.height(18.dp))

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
                    testStatus = WorkerTestStatus.Idle
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
                            testStatus = WorkerTestStatus.Idle
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
                    testStatus = WorkerTestStatus.Idle
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
                        testStatus = WorkerTestStatus.Testing
                        coroutineScope.launch {
                            val result = testWorkerConnectivity(workerUrl.trim(), secretKey.trim())
                            testStatus = result
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
                                    tint = MaterialTheme.colorScheme.error,
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
                                imageVector = Icons.Rounded.Help,
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

private suspend fun testWorkerConnectivity(rawUrl: String, secretKey: String): WorkerTestStatus {
    return withContext(Dispatchers.IO) {
        try {
            val cleanBase = rawUrl.trim().trimEnd('/')
            val healthUrl = if (cleanBase.endsWith("/health")) cleanBase else "$cleanBase/health"

            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            val requestBuilder = Request.Builder().url(healthUrl).get()
            if (secretKey.isNotBlank()) {
                requestBuilder.header("X-Slicer-Key", secretKey)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.contains("DualBoost Cloud Slicer")) {
                        WorkerTestStatus.Success("Worker is live and verified! Ready to slice streams.")
                    } else {
                        WorkerTestStatus.Success("Connected (HTTP ${response.code})")
                    }
                } else if (response.code == 401) {
                    WorkerTestStatus.Error("HTTP 401: Unauthorized. Please check Secret Key.")
                } else {
                    WorkerTestStatus.Error("Worker returned HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            WorkerTestStatus.Error("Connection failed: ${e.message ?: "Unknown error"}")
        }
    }
}

