package com.dualboost.downloader.data

import java.util.UUID

/**
 * Immutable data representation of a completed download record in DualBoost Downloader history.
 * Includes a pure-Kotlin JSON serializer and parser to ensure 100% JVM testability
 * and zero Android framework stub dependencies.
 */
data class DownloadHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val filePath: String,
    val contentUriString: String? = null,
    val fileSize: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val sha256Checksum: String? = null,
    val durationSeconds: Long = 0L,
    val averageSpeedMBps: Double = 0.0,
    val isDualChannel: Boolean = true
) {
    /**
     * Serializes this item into a clean, formatted JSON object string.
     */
    fun toJsonString(): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"id\": \"").append(escape(id)).append("\",\n")
        sb.append("  \"fileName\": \"").append(escape(fileName)).append("\",\n")
        sb.append("  \"filePath\": \"").append(escape(filePath)).append("\",\n")
        if (contentUriString != null) {
            sb.append("  \"contentUriString\": \"").append(escape(contentUriString)).append("\",\n")
        } else {
            sb.append("  \"contentUriString\": null,\n")
        }
        sb.append("  \"fileSize\": ").append(fileSize).append(",\n")
        sb.append("  \"timestamp\": ").append(timestamp).append(",\n")
        if (sha256Checksum != null) {
            sb.append("  \"sha256Checksum\": \"").append(escape(sha256Checksum)).append("\",\n")
        } else {
            sb.append("  \"sha256Checksum\": null,\n")
        }
        sb.append("  \"durationSeconds\": ").append(durationSeconds).append(",\n")
        sb.append("  \"averageSpeedMBps\": ").append(averageSpeedMBps).append(",\n")
        sb.append("  \"isDualChannel\": ").append(isDualChannel).append("\n")
        sb.append("}")
        return sb.toString()
    }

    companion object {
        fun escape(s: String): String {
            return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }

        fun unescape(s: String): String {
            return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        }

        /**
         * Parses a JSON object string into a DownloadHistoryItem.
         */
        fun fromJsonString(jsonStr: String): DownloadHistoryItem? {
            val map = parseJsonObject(jsonStr) ?: return null
            return DownloadHistoryItem(
                id = map["id"] ?: UUID.randomUUID().toString(),
                fileName = map["fileName"] ?: "download",
                filePath = map["filePath"] ?: "",
                contentUriString = map["contentUriString"]?.takeIf { it != "null" && it.isNotBlank() },
                fileSize = map["fileSize"]?.toLongOrNull() ?: 0L,
                timestamp = map["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
                sha256Checksum = map["sha256Checksum"]?.takeIf { it != "null" && it.isNotBlank() },
                durationSeconds = map["durationSeconds"]?.toLongOrNull() ?: 0L,
                averageSpeedMBps = map["averageSpeedMBps"]?.toDoubleOrNull() ?: 0.0,
                isDualChannel = map["isDualChannel"]?.toBooleanStrictOrNull() ?: true
            )
        }

        /**
         * Serializes a list of items into a JSON array string.
         */
        fun serializeList(items: List<DownloadHistoryItem>): String {
            if (items.isEmpty()) return "[]"
            return items.joinToString(
                prefix = "[\n",
                postfix = "\n]",
                separator = ",\n"
            ) { item ->
                item.toJsonString().prependIndent("  ")
            }
        }

        /**
         * Parses a JSON array string into a list of DownloadHistoryItems.
         */
        fun parseList(jsonContent: String): List<DownloadHistoryItem> {
            val trimmed = jsonContent.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
            val items = mutableListOf<DownloadHistoryItem>()

            var depth = 0
            var inString = false
            var escapeNext = false
            var objStart = -1

            for (i in trimmed.indices) {
                val c = trimmed[i]
                if (escapeNext) {
                    escapeNext = false
                    continue
                }
                if (c == '\\') {
                    if (inString) escapeNext = true
                    continue
                }
                if (c == '"') {
                    inString = !inString
                    continue
                }
                if (!inString) {
                    if (c == '{') {
                        if (depth == 0) objStart = i
                        depth++
                    } else if (c == '}') {
                        depth--
                        if (depth == 0 && objStart != -1) {
                            val objStr = trimmed.substring(objStart, i + 1)
                            fromJsonString(objStr)?.let { items.add(it) }
                            objStart = -1
                        }
                    }
                }
            }
            return items
        }

        private fun parseJsonObject(str: String): Map<String, String>? {
            val trimmed = str.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
            val result = mutableMapOf<String, String>()
            val regex = Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"\s*:\s*(?:"([^"\\]*(?:\\.[^"\\]*)*)"|([^,\r\n}]+))""")
            for (match in regex.findAll(trimmed)) {
                val key = unescape(match.groupValues[1])
                val strVal = match.groups[2]?.value
                val rawVal = match.groups[3]?.value
                val value = if (strVal != null) {
                    unescape(strVal)
                } else {
                    rawVal?.trim() ?: ""
                }
                result[key] = value
            }
            return result
        }
    }
}
