package com.dualboost.downloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.security.ThreatSeverity
import com.dualboost.downloader.ui.theme.DualBoostIcons
import com.dualboost.downloader.ui.theme.expressiveButtonShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecurityThreatDialog(
    threatState: DownloadState.ThreatDetected,
    onSafeDiscard: () -> Unit,
    onOverrideDownload: (url: String) -> Unit,
    onDismiss: () -> Unit
) {
    val threat = threatState.threat
    val isCritical = threat.severity == ThreatSeverity.CRITICAL

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(DualBoostIcons.Security),
                        contentDescription = "Threat Detected",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Security Alert: Threat Blocked",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                ) {
                    Text(
                        text = "${threat.severity.name} RISK • ${threat.threatType?.name ?: "THREAT"}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isCritical) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = threat.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        threat.originalTarget?.let { orig ->
                            Text(
                                text = "Requested file:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = orig,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        threat.disguisedTarget?.let { disguised ->
                            Text(
                                text = "Actual payload / target:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = disguised,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Text(
                    text = "DualBoost blocked the connection and removed any unverified bytes from storage to protect your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSafeDiscard,
                shapes = expressiveButtonShapes(baseCorner = 12.dp, pressedCorner = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(painter = painterResource(DualBoostIcons.Shield), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Safe Discard (Recommended)", style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onOverrideDownload(threatState.url) },
                shapes = expressiveButtonShapes(baseCorner = 12.dp, pressedCorner = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(DualBoostIcons.Warning),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Download Anyway (At your own risk)",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    )
}
