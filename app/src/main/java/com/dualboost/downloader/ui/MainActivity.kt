package com.dualboost.downloader.ui

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.ui.theme.DualBoostIcons
import com.dualboost.downloader.ui.theme.DualBoostTheme
import com.dualboost.downloader.ui.theme.ExpressiveTooltipIconButton
import com.dualboost.downloader.ui.theme.expressiveButtonShapes
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request notification permission for Android 13+ / 14 / 15 / 17+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIncomingIntent(intent)

        setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { path, uri -> openDownloadedFile(path, uri) },
                    onShareFile = { path, uri -> shareDownloadedFile(path, uri) },
                    onOpenFileLocation = { path, uri -> openFileInFilesApp(path, uri) },
                    onPasteFromClipboard = { pasteFromClipboard() }
                )
            }
        }
        checkClipboardMetadata()
    }

    override fun onResume() {
        super.onResume()
        checkClipboardMetadata()
    }

    // SEC-AUDIT-02: Zero-toast privacy-preserving metadata check (does not access clipboard payload bytes)
    private fun checkClipboardMetadata() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val hasClip = clipboard?.hasPrimaryClip() == true
            val isText = clipboard?.primaryClipDescription?.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) == true
            viewModel.onClipboardStateChanged(hasClip && isText)
        } catch (_: Exception) {}
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val rawText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (rawText.isNullOrBlank()) {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                viewModel.onClipboardStateChanged(false)
                return
            }
            val extracted = extractUrl(rawText)
            if (extracted.isNotBlank() && (extracted.startsWith("http://") || extracted.startsWith("https://"))) {
                viewModel.onUrlChanged(extracted)
                viewModel.onClipboardStateChanged(false)
            } else {
                Toast.makeText(this, "Clipboard does not contain a web URL", Toast.LENGTH_SHORT).show()
                viewModel.onClipboardStateChanged(false)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot access clipboard: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val extracted = extractUrl(sharedText)
                if (extracted.isNotBlank()) {
                    viewModel.onUrlChanged(extracted)
                }
            }
        }
    }

    // SEC-AUDIT H-4: Hardened URL extraction — returns empty string instead of raw text
    // to prevent injection of non-HTTP URIs (file://, javascript:, etc.) via ACTION_SEND
    private fun extractUrl(text: String): String {
        val parts = text.split("\\s+".toRegex())
        val candidate = parts.firstOrNull {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        } ?: return ""
        // Validate the URL is structurally well-formed via OkHttp's strict parser
        return candidate.toHttpUrlOrNull()?.toString() ?: ""
    }

    private fun openDownloadedFile(filePath: String, contentUriString: String? = null) {
        try {
            val uri = contentUriString?.let { Uri.parse(it) } ?: run {
                val file = File(filePath)
                if (!file.exists()) {
                    Toast.makeText(this, "File does not exist: $filePath", Toast.LENGTH_SHORT).show()
                    return
                }
                FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )
            }
            val ext = filePath.substringAfterLast('.', "").lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: if (ext in listOf("mkv", "mp4", "webm", "mov", "avi", "ts", "m4v", "flv")) "video/*"
                else (contentResolver.getType(uri) ?: "*/*")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareDownloadedFile(filePath: String, contentUriString: String? = null) {
        try {
            val uri = contentUriString?.let { Uri.parse(it) } ?: run {
                val file = File(filePath)
                if (!file.exists()) {
                    Toast.makeText(this, "File does not exist: $filePath", Toast.LENGTH_SHORT).show()
                    return
                }
                FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Downloaded File"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot share file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openFileInFilesApp(filePath: String, contentUriString: String? = null) {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.files")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                } else {
                    val sysIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).toURI().toString()), "*/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(sysIntent)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open Files app: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenFile: (String, String?) -> Unit,
    onShareFile: (String, String?) -> Unit,
    onOpenFileLocation: (String, String?) -> Unit = { _, _ -> },
    onPasteFromClipboard: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var pendingCancelFileName by remember { mutableStateOf<String?>(null) }
    var showExitDownloadConfirmDialog by remember { mutableStateOf(false) }

    val downloadState by viewModel.downloadState.collectAsState()
    val socialIntakeState by viewModel.socialIntakeState.collectAsState()
    val isAnySheetOrDialogActive = socialIntakeState !is com.dualboost.downloader.social.SocialIntakeState.Idle ||
            showCancelConfirmDialog ||
            showExitDownloadConfirmDialog
    val isDownloadRunning = downloadState is DownloadState.Downloading || downloadState is DownloadState.Probing

    BackHandler(enabled = isAnySheetOrDialogActive || isDownloadRunning) {
        when {
            socialIntakeState !is com.dualboost.downloader.social.SocialIntakeState.Idle -> viewModel.dismissSocialIntake()
            showCancelConfirmDialog -> showCancelConfirmDialog = false
            showExitDownloadConfirmDialog -> showExitDownloadConfirmDialog = false
            isDownloadRunning -> showExitDownloadConfirmDialog = true
        }
    }
    val wifiAvailable by viewModel.isWifiAvailable.collectAsState()
    val cellularAvailable by viewModel.isCellularAvailable.collectAsState()
    val wifiSignalLevel by viewModel.wifiSignalLevel.collectAsState()
    val cellularSignalLevel by viewModel.cellularSignalLevel.collectAsState()
    val cellularGeneration by viewModel.cellularGeneration.collectAsState()
    val cellularNetworkConfig by viewModel.cellularNetworkConfig.collectAsState()
    val wifiDetails by viewModel.wifiDetails.collectAsState()
    val showBatteryGuidance by viewModel.showBatteryGuidance.collectAsState()
    val isVpnActive by viewModel.isVpnActive.collectAsState()
    val isRoamingActive by viewModel.isRoamingActive.collectAsState()
    val cellularCarrierName by viewModel.cellularCarrierName.collectAsState()
    val isModemStandbyDetected by viewModel.isModemStandbyDetected.collectAsState()

    val urlInput by viewModel.urlInput.collectAsState()
    val hasClipboardContent by viewModel.hasClipboardContent.collectAsState()
    val detectedClipboardUrl by viewModel.detectedClipboardUrl.collectAsState()
    val isSequentialRequested by viewModel.isSequentialStreamRequested.collectAsState()

    // Haptic feedback on completion
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Completed) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(DualBoostIcons.Bolt),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "DualBoost",
                            style = MaterialTheme.typography.titleLargeEmphasized
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {

            val isDark = isSystemInDarkTheme()
            val currentPalette by viewModel.streamPalette.collectAsState()
            val materialPrimary = MaterialTheme.colorScheme.primary
            val materialTertiary = MaterialTheme.colorScheme.tertiary
            val wifiColor = currentPalette.resolveWifiColor(isDark, materialPrimary)
            val cellularColor = currentPalette.resolveCellularColor(isDark, materialTertiary)

            // Advisory Root & Tamper Awareness Banner (SEC-AUDIT-11)
            if (viewModel.isDeviceRooted) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(DualBoostIcons.Security),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Root environment detected: OS sandboxing may be bypassed by privileged root processes.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // OEM Battery Optimization Guidance Banner (Slice 3)
            AnimatedVisibility(
                visible = showBatteryGuidance,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(DualBoostIcons.BatteryAlert),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Battery Optimization Warning",
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Aggressive OEM background restrictions may terminate downloads when your screen turns off. Allow battery exemption for uninterrupted multi-channel transfers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { viewModel.dismissBatteryGuidance() },
                                shapes = expressiveButtonShapes(baseCorner = 10.dp, pressedCorner = 6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Dismiss",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                        } catch (_: Exception) {}
                                    }
                                },
                                shapes = expressiveButtonShapes(baseCorner = 10.dp, pressedCorner = 6.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Exempt App",
                                    style = MaterialTheme.typography.labelMediumEmphasized
                                )
                            }
                        }
                    }
                }
            }

            // VPN Active Status Banner (Slice 2)
            AnimatedVisibility(
                visible = isVpnActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(DualBoostIcons.VpnKey),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "VPN Active: Secure Multi-Stream Mode",
                                style = MaterialTheme.typography.labelMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Downloads route through your encrypted VPN tunnel to preserve privacy.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Data Roaming Guard Banner (Slice 1)
            AnimatedVisibility(
                visible = isRoamingActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(DualBoostIcons.Warning),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Data Roaming Active: Mobile Data Paused",
                                style = MaterialTheme.typography.labelMediumEmphasized,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Cellular aggregation paused to safeguard against unexpected roaming charges.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // Network Aggregation Hero Cards
            val cellularCarrierSuffix = cellularCarrierName?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            val cellularTitle = "${cellularGeneration.detailLabel}$cellularCarrierSuffix"
            val cellularSubtitle = when {
                isRoamingActive -> "Roaming (Paused)"
                cellularAvailable -> viewModel.getSignalLabel(cellularSignalLevel)
                isModemStandbyDetected -> "Modem Standby"
                else -> "Standby"
            }
            val wifiTitle = if (wifiAvailable) wifiDetails.richHeroLabel else "Wi-Fi"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NetworkCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = wifiTitle,
                    subtitle = if (wifiAvailable) viewModel.getSignalLabel(wifiSignalLevel) else "Disconnected",
                    iconRes = if (wifiAvailable) DualBoostIcons.Wifi else DualBoostIcons.WifiOff,
                    isActive = wifiAvailable,
                    activeColor = wifiColor
                )
                NetworkCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = cellularTitle,
                    subtitle = cellularSubtitle,
                    iconRes = if (cellularAvailable) DualBoostIcons.SignalCellularAlt else DualBoostIcons.SignalCellularOff,
                    isActive = cellularAvailable,
                    activeColor = cellularColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Download Input Card
            Card(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "New Download",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // SEC-AUDIT-02: Smart Context Pill Banner (Privacy-Conscious Clipboard Suggestion)
                    AnimatedVisibility(
                        visible = (hasClipboardContent || !detectedClipboardUrl.isNullOrBlank()) && urlInput.isBlank(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        painter = painterResource(if (!detectedClipboardUrl.isNullOrBlank()) DualBoostIcons.Link else DualBoostIcons.ContentPaste),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (!detectedClipboardUrl.isNullOrBlank()) "Link detected in clipboard" else "Clipboard text available",
                                            style = MaterialTheme.typography.labelSmallEmphasized,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = detectedClipboardUrl ?: "Tap 'Paste' to import URL",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (!detectedClipboardUrl.isNullOrBlank()) {
                                                viewModel.acceptClipboardUrl()
                                            } else {
                                                onPasteFromClipboard()
                                            }
                                        },
                                        shapes = expressiveButtonShapes(baseCorner = 10.dp, pressedCorner = 6.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        )
                                    ) {
                                        Text("Paste", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    ExpressiveTooltipIconButton(
                                        tooltip = "Dismiss",
                                        onClick = { viewModel.dismissClipboardSuggestion() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(DualBoostIcons.Close),
                                            contentDescription = "Dismiss",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { viewModel.onUrlChanged(it) },
                        label = { Text("Download Link") },
                        placeholder = { Text("https://example.com/largefile.zip") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(DualBoostIcons.Link),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (urlInput.isNotBlank()) {
                                    ExpressiveTooltipIconButton(
                                        tooltip = "Clear link",
                                        onClick = { viewModel.clearUrl() }
                                    ) {
                                        Icon(
                                            painter = painterResource(DualBoostIcons.Close),
                                            contentDescription = "Clear"
                                        )
                                    }
                                }
                                ExpressiveTooltipIconButton(
                                    tooltip = "Paste from clipboard",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onPasteFromClipboard()
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(DualBoostIcons.ContentPaste),
                                        contentDescription = "Paste from clipboard"
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.tonalColors(),
                        shape = OutlinedTextFieldDefaults.roundedShape,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Watch While Downloading (Sequential Video Streaming via Expressive ListItem)
                    val isAutoVideoUrl = isSequentialRequested == null && com.dualboost.downloader.engine.DualDownloaderEngine.isVideoFile(urlInput)
                    val isSequentialActive = isSequentialRequested == true || (isSequentialRequested == null && isAutoVideoUrl)
                    val statusText = when {
                        isSequentialRequested == true -> "On • Downloads in order to preview early"
                        isSequentialRequested == false -> "Off • Maximum speed for files & games"
                        isAutoVideoUrl -> "Video detected • Ready to watch while downloading"
                        else -> "Auto • Activates automatically for video links"
                    }

                    ListItem(
                        checked = isSequentialActive,
                        onCheckedChange = { isChecked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setSequentialStreamMode(isChecked)
                        },
                        shapes = ListItemDefaults.shapes(
                            shape = MaterialTheme.shapes.medium,
                            selectedShape = MaterialTheme.shapes.medium
                        ),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = if (isSequentialActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(if (isSequentialActive) DualBoostIcons.PlayCircleFilled else DualBoostIcons.PlayCircle),
                                        contentDescription = null,
                                        tint = if (isSequentialActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        supportingContent = {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSequentialActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = isSequentialActive,
                                onCheckedChange = { isChecked ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setSequentialStreamMode(isChecked)
                                },
                                thumbContent = {
                                    if (isSequentialActive) {
                                        Icon(
                                            painter = painterResource(DualBoostIcons.PlayArrow),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    }
                                },
                                modifier = Modifier.scale(0.85f),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                    checkedIconColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isSequentialActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                MaterialTheme.shapes.medium
                            )
                    ) {
                        Text(
                            text = "Watch While Downloading",
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Button
                    val isEnabled = viewModel.isStartButtonEnabled(downloadState, urlInput)
                    val isPaused = downloadState is DownloadState.Paused
                    Button(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.startDownload()
                            }
                        },
                        shapes = expressiveButtonShapes(baseCorner = 16.dp, pressedCorner = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = isEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(if (isPaused) DualBoostIcons.PlayArrow else DualBoostIcons.Bolt),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (downloadState) {
                                    is DownloadState.Probing -> "Probing Server..."
                                    is DownloadState.Paused -> "Resume Download"
                                    else -> "Boost Download"
                                },
                                style = MaterialTheme.typography.titleMediumEmphasized
                            )
                        }
                    }
                }
            }


            Spacer(modifier = Modifier.height(20.dp))

            // Real-Time Download Status / Results Card
            AnimatedContent(
                targetState = downloadState,
                contentKey = { it::class },
                transitionSpec = {
                    fadeIn(animationSpec = tween(150, delayMillis = 50)) togetherWith fadeOut(animationSpec = tween(100))
                },
                label = "DownloadStateAnimation"
            ) { state ->
                when (state) {
                    is DownloadState.Idle -> {
                        // Clean idle state — no placeholder card
                    }

                    is DownloadState.Probing -> {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(30.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearWavyProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }

                    is DownloadState.Downloading -> {
                        val animatedProgress by animateFloatAsState(
                            targetValue = state.progressPercentage,
                            animationSpec = tween(150),
                            label = "ProgressAnimation"
                        )

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {

                                // File Header & Percentage
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cleanDisplayName(state.fileName),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val remainingBytes = kotlin.math.max(0L, state.totalBytes - state.downloadedBytes)
                                        val etaSubText = state.estimatedSecondsRemaining?.let {
                                            " • ${com.dualboost.downloader.data.formatEta(it)} left (${formatBytes(remainingBytes)} left)"
                                        } ?: if (remainingBytes > 0 && state.totalBytes > 0) " • ${formatBytes(remainingBytes)} left" else ""

                                        Text(
                                            text = "${formatBytes(state.downloadedBytes)} of ${formatBytes(state.totalBytes)}$etaSubText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (state.isCloudSlicerActive) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    text = "⚡ Cloud Slicer Active",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else if (state.isDesktopSpoofed) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    text = "⚡ Desktop Mode Active",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (state.isSequentialMode) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val isReady = state.isPlayable
                                            val badgeBgColor = if (isReady) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                            val badgeBorderColor = if (isReady) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                                            val badgeContentColor = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = badgeBgColor,
                                                border = BorderStroke(1.dp, badgeBorderColor)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(if (isReady) DualBoostIcons.PlayCircleFilled else DualBoostIcons.PlayArrow),
                                                        contentDescription = null,
                                                        tint = badgeContentColor,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isReady) "⚡ Stream Ready • ${formatBytes(state.contiguousPlayableBytes)} buffered"
                                                               else "⚡ Buffering Stream • ${formatBytes(state.contiguousPlayableBytes)} / 24 MB",
                                                        style = MaterialTheme.typography.labelSmallEmphasized,
                                                        color = badgeContentColor
                                                    )
                                                }
                                            }
                                        }

                                        // Compact Single-Stream Status Chip
                                        if (!state.supportsRanges) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(DualBoostIcons.Info),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Single-Stream • ${if (state.cellularDownloadedBytes > 0L && state.wifiDownloadedBytes == 0L) cellularGeneration.detailLabel else "Wi-Fi Only"}",
                                                        style = MaterialTheme.typography.labelSmallEmphasized,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${(state.progressPercentage * 100).toInt()}%",
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Integrated Hero Speed & Transfer Readout
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(DualBoostIcons.Speed),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = com.dualboost.downloader.data.formatSpeed(state.totalSpeedMBps),
                                            style = MaterialTheme.typography.headlineSmallEmphasized,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Text(
                                        text = "${formatBytes(state.downloadedBytes)} of ${formatBytes(state.totalBytes)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Dual-Color Contribution Progress Bar
                                val isDarkState = isSystemInDarkTheme()
                                val rowWifiColor = currentPalette.resolveWifiColor(isDarkState, materialPrimary)
                                val rowCellularColor = currentPalette.resolveCellularColor(isDarkState, materialTertiary)

                                val sessionBytes = (state.wifiDownloadedBytes + state.cellularDownloadedBytes).coerceAtLeast(1L).toFloat()
                                val wifiRatio = (state.wifiDownloadedBytes / sessionBytes).coerceIn(0f, 1f)
                                val cellularRatio = (state.cellularDownloadedBytes / sessionBytes).coerceIn(0f, 1f)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(animatedProgress)
                                    ) {
                                        if (wifiRatio > 0.01f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(wifiRatio)
                                                    .background(rowWifiColor)
                                            )
                                        }
                                        if (cellularRatio > 0.01f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(cellularRatio)
                                                    .background(rowCellularColor)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Compact Inline Network Chips (Wi-Fi & Cellular)
                                val isWifiInactiveInSingleStream = !state.supportsRanges && state.wifiDownloadedBytes == 0L && state.cellularDownloadedBytes > 0L
                                val isCellularInactiveInSingleStream = !state.supportsRanges && state.cellularDownloadedBytes == 0L
                                val cellTypeLabel = cellularGeneration.fullDisplayName

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Wi-Fi Telemetry Tag
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (isWifiInactiveInSingleStream) rowWifiColor.copy(alpha = 0.35f) else rowWifiColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            painter = painterResource(DualBoostIcons.Wifi),
                                            contentDescription = null,
                                            tint = if (isWifiInactiveInSingleStream) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isWifiInactiveInSingleStream) "${wifiDetails.shortLabel} (Inactive)" else "${wifiDetails.shortLabel} • ${com.dualboost.downloader.data.formatSpeed(state.wifiSpeedMBps)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = if (isWifiInactiveInSingleStream) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Cellular Telemetry Tag
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (isCellularInactiveInSingleStream) rowCellularColor.copy(alpha = 0.35f) else rowCellularColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            painter = painterResource(DualBoostIcons.SignalCellularAlt),
                                            contentDescription = null,
                                            tint = if (isCellularInactiveInSingleStream) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isCellularInactiveInSingleStream) "$cellTypeLabel (Inactive)" else "$cellTypeLabel • ${com.dualboost.downloader.data.formatSpeed(state.cellularSpeedMBps)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = if (isCellularInactiveInSingleStream) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Integrated Controls ButtonGroup (Preview + Pause + Cancel)
                                val previewInteraction = remember { MutableInteractionSource() }
                                val pauseInteraction = remember { MutableInteractionSource() }
                                val cancelInteraction = remember { MutableInteractionSource() }
                                ButtonGroup(
                                    overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (state.isPlayable) {
                                        customItem(
                                            buttonGroupContent = {
                                                Button(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onOpenFile(state.filePath, state.contentUriString)
                                                    },
                                                    shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                                    interactionSource = previewInteraction,
                                                    modifier = Modifier
                                                        .weight(1.3f)
                                                        .animateWidth(previewInteraction, compressionLimit = 8.dp)
                                                        .height(48.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(DualBoostIcons.PlayArrow),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Preview",
                                                        style = MaterialTheme.typography.titleSmallEmphasized,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            },
                                            menuContent = { menuState ->
                                                DropdownMenuItem(
                                                    text = { Text("Preview") },
                                                    onClick = {
                                                        menuState.dismiss()
                                                        onOpenFile(state.filePath, state.contentUriString)
                                                    }
                                                )
                                            }
                                        )
                                    }

                                    customItem(
                                        buttonGroupContent = {
                                            OutlinedButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    viewModel.pauseDownload()
                                                },
                                                shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                                interactionSource = pauseInteraction,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .animateWidth(pauseInteraction, compressionLimit = 8.dp)
                                                    .height(48.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Icon(painter = painterResource(DualBoostIcons.Pause), contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Pause", style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
                                            }
                                        },
                                        menuContent = { menuState ->
                                            DropdownMenuItem(
                                                text = { Text("Pause") },
                                                onClick = {
                                                    menuState.dismiss()
                                                    viewModel.pauseDownload()
                                                }
                                            )
                                        }
                                    )

                                    customItem(
                                        buttonGroupContent = {
                                            OutlinedButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    pendingCancelFileName = state.fileName
                                                    showCancelConfirmDialog = true
                                                },
                                                shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                                interactionSource = cancelInteraction,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .animateWidth(cancelInteraction, compressionLimit = 8.dp)
                                                    .height(48.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Icon(painter = painterResource(DualBoostIcons.Close), contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Cancel", style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
                                            }
                                        },
                                        menuContent = { menuState ->
                                            DropdownMenuItem(
                                                text = { Text("Cancel") },
                                                onClick = {
                                                    menuState.dismiss()
                                                    pendingCancelFileName = state.fileName
                                                    showCancelConfirmDialog = true
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is DownloadState.AutoRecovering -> {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(30.dp),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Auto-Recovering Network",
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${state.message} (Retry attempt #${state.retryAttempt})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearWavyProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.24f)
                                )
                            }
                        }
                    }

                    is DownloadState.Completed -> {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = MaterialShapes.Cookie9Sided.toShape(),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(DualBoostIcons.Check),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Download Complete!",
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        val avgSpeedText = if (state.averageSpeedMBps > 0.0) " at ${com.dualboost.downloader.data.formatSpeed(state.averageSpeedMBps)} avg" else ""
                                        val dualChannelText = if (state.isDualChannel) " • Aggregated ${wifiDetails.shortLabel} + ${cellularGeneration.shortLabel} Boost ⚡" else ""
                                        Text(
                                            text = "${formatBytes(state.totalBytes)} completed in ${formatDuration(state.durationSeconds)}$avgSpeedText$dualChannelText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = cleanDisplayName(state.fileName),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(DualBoostIcons.Folder),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Saved to Downloads folder",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                DownloadCompletedActions(
                                    filePath = state.filePath,
                                    contentUriString = state.contentUriString,
                                    onOpenFile = onOpenFile,
                                    onOpenFileLocation = onOpenFileLocation,
                                    onShareFile = onShareFile
                                )
                            }
                        }
                    }

                    is DownloadState.ThreatDetected -> {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(DualBoostIcons.Security),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "🛑 Threat Blocked",
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.threat.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                val safeInteraction = remember { MutableInteractionSource() }
                                val overrideInteraction = remember { MutableInteractionSource() }
                                ButtonGroup(
                                    overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    customItem(
                                        buttonGroupContent = {
                                            Button(
                                                onClick = { viewModel.resetToIdle() },
                                                shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                                interactionSource = safeInteraction,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .animateWidth(safeInteraction, compressionLimit = 8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error,
                                                    contentColor = MaterialTheme.colorScheme.onError
                                                )
                                            ) {
                                                Text("Safe Discard", maxLines = 1)
                                            }
                                        },
                                        menuContent = { menuState ->
                                            DropdownMenuItem(
                                                text = { Text("Safe Discard") },
                                                onClick = {
                                                    menuState.dismiss()
                                                    viewModel.resetToIdle()
                                                }
                                            )
                                        }
                                    )
                                    customItem(
                                        buttonGroupContent = {
                                            OutlinedButton(
                                                onClick = { viewModel.startDownloadWithBypass(state.url) },
                                                shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                                interactionSource = overrideInteraction,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .animateWidth(overrideInteraction, compressionLimit = 8.dp)
                                            ) {
                                                Text("Override & Download", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        },
                                        menuContent = { menuState ->
                                            DropdownMenuItem(
                                                text = { Text("Override & Download") },
                                                onClick = {
                                                    menuState.dismiss()
                                                    viewModel.startDownloadWithBypass(state.url)
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is DownloadState.Failed -> {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(DualBoostIcons.Error),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Download Interrupted",
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                val retryInteraction = remember { MutableInteractionSource() }
                                val dismissInteraction = remember { MutableInteractionSource() }
                                ButtonGroup(
                                    overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    customItem(
                                        buttonGroupContent = {
                                            Button(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.retryDownload()
                                                },
                                                shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                                interactionSource = retryInteraction,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .animateWidth(retryInteraction, compressionLimit = 8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error,
                                                    contentColor = MaterialTheme.colorScheme.onError
                                                )
                                            ) {
                                                Icon(painter = painterResource(DualBoostIcons.Refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Retry", style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
                                            }
                                        },
                                        menuContent = { menuState ->
                                            DropdownMenuItem(
                                                text = { Text("Retry") },
                                                onClick = {
                                                    menuState.dismiss()
                                                    viewModel.retryDownload()
                                                }
                                            )
                                        }
                                    )

                                    customItem(
                                        buttonGroupContent = {
                                            OutlinedButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    viewModel.resetToIdle()
                                                },
                                                shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                                interactionSource = dismissInteraction,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .animateWidth(dismissInteraction, compressionLimit = 8.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                            ) {
                                                Icon(painter = painterResource(DualBoostIcons.Close), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
                                            }
                                        },
                                        menuContent = { menuState ->
                                            DropdownMenuItem(
                                                text = { Text("Dismiss") },
                                                onClick = {
                                                    menuState.dismiss()
                                                    viewModel.resetToIdle()
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is DownloadState.Paused -> {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
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
                                                painter = painterResource(DualBoostIcons.Pause),
                                                contentDescription = "Paused",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Download Paused",
                                                style = MaterialTheme.typography.titleMediumEmphasized,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (state.fileName.isNotBlank()) {
                                                Text(
                                                    text = cleanDisplayName(state.fileName),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                             if (state.isCloudSlicerActive) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
                                                ) {
                                                    Text(
                                                        text = "⚡ Cloud Slicer",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else if (state.isDesktopSpoofed) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                                ) {
                                                    Text(
                                                        text = "⚡ Desktop Mode",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${(state.progressPercentage * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                LinearProgressIndicator(
                                    progress = { state.progressPercentage },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (state.totalBytes > 0) "${formatBytes(state.downloadedBytes)} of ${formatBytes(state.totalBytes)}" else "Chunks saved on disk",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Preserved on disk",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                val resumeInteraction = remember { MutableInteractionSource() }
                                val discardInteraction = remember { MutableInteractionSource() }
                                ButtonGroup(
                                    overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    customItem(
                                        buttonGroupContent = {
                                            Button(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.startDownload(if (state.url.isNotBlank()) state.url else viewModel.urlInput.value)
                                                },
                                                shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                                interactionSource = resumeInteraction,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .animateWidth(resumeInteraction, compressionLimit = 8.dp)
                                            ) {
                                                Icon(painter = painterResource(DualBoostIcons.PlayArrow), contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Resume", style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
                                            }
                                        },
                                        menuContent = { menuState ->
                                            DropdownMenuItem(
                                                text = { Text("Resume") },
                                                onClick = {
                                                    menuState.dismiss()
                                                    viewModel.startDownload(if (state.url.isNotBlank()) state.url else viewModel.urlInput.value)
                                                }
                                            )
                                        }
                                    )

                                    customItem(
                                        buttonGroupContent = {
                                            OutlinedButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    pendingCancelFileName = state.fileName
                                                    showCancelConfirmDialog = true
                                                },
                                                shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                                                interactionSource = discardInteraction,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .animateWidth(discardInteraction, compressionLimit = 8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Icon(painter = painterResource(DualBoostIcons.Delete), contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Discard", style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
                                            }
                                        },
                                        menuContent = { menuState ->
                                            DropdownMenuItem(
                                                text = { Text("Discard") },
                                                onClick = {
                                                    menuState.dismiss()
                                                    pendingCancelFileName = state.fileName
                                                    showCancelConfirmDialog = true
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }


            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showCancelConfirmDialog = false
                pendingCancelFileName = null
            },
            icon = {
                Icon(
                    painter = painterResource(DualBoostIcons.Warning),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Cancel Download?",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to cancel and delete this download? All downloaded data will be removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.discardDownload(pendingCancelFileName)
                        showCancelConfirmDialog = false
                        pendingCancelFileName = null
                    },
                    shapes = expressiveButtonShapes(baseCorner = 12.dp, pressedCorner = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Yes, Cancel", style = MaterialTheme.typography.labelLargeEmphasized)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmDialog = false
                        pendingCancelFileName = null
                    },
                    shapes = expressiveButtonShapes(baseCorner = 12.dp, pressedCorner = 8.dp)
                ) {
                    Text("Keep Downloading")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large
        )
    }

    val currentThreatState = downloadState as? DownloadState.ThreatDetected
    if (currentThreatState != null) {
        SecurityThreatDialog(
            threatState = currentThreatState,
            onSafeDiscard = { viewModel.resetToIdle() },
            onOverrideDownload = { url -> viewModel.startDownloadWithBypass(url) },
            onDismiss = { viewModel.resetToIdle() }
        )
    }

    if (showExitDownloadConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitDownloadConfirmDialog = false },
            icon = {
                Icon(
                    painter = painterResource(DualBoostIcons.Download),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Download In Progress",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "A transfer is actively downloading in the foreground. Would you like to minimize to background and keep downloading, or cancel the transfer?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDownloadConfirmDialog = false
                        (context as? android.app.Activity)?.moveTaskToBack(true)
                    },
                    shapes = expressiveButtonShapes(baseCorner = 12.dp, pressedCorner = 8.dp)
                ) {
                    Text("Minimize to Background")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showExitDownloadConfirmDialog = false
                        viewModel.discardDownload()
                    },
                    shapes = expressiveButtonShapes(baseCorner = 12.dp, pressedCorner = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Text("Cancel Download")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large
        )
    }

    if (socialIntakeState !is com.dualboost.downloader.social.SocialIntakeState.Idle) {
        SocialMediaModalSheet(
            state = socialIntakeState,
            cellularGeneration = cellularGeneration,
            wifiDetails = wifiDetails,
            onDismiss = { viewModel.dismissSocialIntake() },
            onDownload = { url, quality -> viewModel.startSocialDownload(url, quality) },
            onEnqueue = { url, quality -> viewModel.enqueueSocialDownload(url, quality) }
        )
    }
}

@Composable
fun NetworkCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    isActive: Boolean,
    activeColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "NetworkCardPulse")
    val dotAlpha by if (isActive) {
        infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "DotPulse"
        )
    } else {
        remember { mutableFloatStateOf(0.4f) }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) activeColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isActive) activeColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isActive) activeColor.copy(alpha = dotAlpha) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun PresetChip(
    label: String,
    isSelected: Boolean,
    isRangeEnabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRangeEnabled) {
                Icon(
                    painter = painterResource(DualBoostIcons.Bolt),
                    contentDescription = "Dual Channel Aggregation Supported",
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SpeedDetailRow(
    label: String,
    speedMBps: Double,
    downloadedBytes: Long,
    indicatorColor: Color,
    @DrawableRes iconRes: Int,
    isDimmed: Boolean = false
) {
    val isStreaming = speedMBps > 0.05 && !isDimmed
    val infiniteTransition = rememberInfiniteTransition(label = "SpeedRowPulse")
    val pulseAlpha by if (isStreaming) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "StreamingPulse"
        )
    } else {
        remember { mutableFloatStateOf(1.0f) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (isDimmed) Modifier.alpha(0.45f) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor.copy(alpha = pulseAlpha))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = indicatorColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "${com.dualboost.downloader.data.formatSpeed(speedMBps)} (${formatBytes(downloadedBytes)})",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SegmentedChunkStrip(
    chunkSnapshots: List<com.dualboost.downloader.data.ChunkSnapshot>,
    wifiColor: Color,
    cellularColor: Color,
    cellularGeneration: com.dualboost.downloader.network.CellularGeneration = com.dualboost.downloader.network.CellularGeneration.UNKNOWN,
    wifiDetails: com.dualboost.downloader.network.WifiDetails = com.dualboost.downloader.network.WifiDetails.DISCONNECTED,
    modifier: Modifier = Modifier
) {
    if (chunkSnapshots.isEmpty()) return

    val infiniteTransition = rememberInfiniteTransition(label = "ChunkShimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShimmerAlpha"
    )

    val completedCount = chunkSnapshots.count { it.status == com.dualboost.downloader.data.ChunkStatus.COMPLETED }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dynamic Chunk Aggregation",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$completedCount / ${chunkSnapshots.size} chunks complete",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val rows = chunkSnapshots.chunked(16)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            rows.forEach { rowChunks ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    rowChunks.forEach { chunk ->
                        val isCellular = chunk.networkType == com.dualboost.downloader.data.NetworkInterfaceType.CELLULAR
                        val assignedColor = if (isCellular) cellularColor else wifiColor

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when (chunk.status) {
                                        com.dualboost.downloader.data.ChunkStatus.COMPLETED -> assignedColor
                                        com.dualboost.downloader.data.ChunkStatus.DOWNLOADING -> assignedColor.copy(alpha = shimmerAlpha)
                                        com.dualboost.downloader.data.ChunkStatus.FAILED -> MaterialTheme.colorScheme.error
                                        com.dualboost.downloader.data.ChunkStatus.PENDING -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                    }
                                )
                                .border(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(wifiColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${wifiDetails.shortLabel} Chunk",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(cellularColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${cellularGeneration.shortLabel} Chunk",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun DownloadCompletedActions(
    filePath: String,
    contentUriString: String?,
    onOpenFile: (String, String?) -> Unit,
    onOpenFileLocation: (String, String?) -> Unit,
    onShareFile: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hero Primary CTA: Full-width Open File button
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenFile(filePath, contentUriString)
            },
            shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics(mergeDescendants = true) {},
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(painter = painterResource(DualBoostIcons.PlayArrow), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Open File",
                style = MaterialTheme.typography.labelLargeEmphasized
            )
        }

        // Split Secondary Action ButtonGroup: Show in Files & Share side-by-side
        val filesInteraction = remember { MutableInteractionSource() }
        val shareInteraction = remember { MutableInteractionSource() }
        ButtonGroup(
            overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            customItem(
                buttonGroupContent = {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenFileLocation(filePath, contentUriString)
                        },
                        shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                        interactionSource = filesInteraction,
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(filesInteraction, compressionLimit = 8.dp)
                            .height(44.dp)
                            .semantics(mergeDescendants = true) {},
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(painter = painterResource(DualBoostIcons.FolderOpen), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Show in Files",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMediumEmphasized
                        )
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text("Show in Files") },
                        onClick = {
                            menuState.dismiss()
                            onOpenFileLocation(filePath, contentUriString)
                        }
                    )
                }
            )

            customItem(
                buttonGroupContent = {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onShareFile(filePath, contentUriString)
                        },
                        shapes = expressiveButtonShapes(baseCorner = 14.dp, pressedCorner = 8.dp),
                        interactionSource = shareInteraction,
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(shareInteraction, compressionLimit = 8.dp)
                            .height(44.dp)
                            .semantics(mergeDescendants = true) {},
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(painter = painterResource(DualBoostIcons.Share), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMediumEmphasized
                        )
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            menuState.dismiss()
                            onShareFile(filePath, contentUriString)
                        }
                    )
                }
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1000) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

fun cleanDisplayName(name: String): String {
    val decoded = try {
        java.net.URLDecoder.decode(name, "UTF-8")
    } catch (_: Exception) {
        name
    }
    return decoded
        .replace("_20", " ")
        .replace("%20", " ")
        .replace("__", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
