package com.dualboost.downloader.data

import java.util.UUID

enum class QueueItemStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    PAUSED,
    FAILED,
    THREAT_DETECTED
}

data class QueueItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val status: QueueItemStatus = QueueItemStatus.QUEUED,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progressPercentage: Float = 0f,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isSequentialMode: Boolean = true,
    val errorMessage: String? = null
) {
    fun toJsonString(): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"id\": \"").append(escape(id)).append("\",\n")
        sb.append("  \"url\": \"").append(escape(url)).append("\",\n")
        sb.append("  \"fileName\": \"").append(escape(fileName)).append("\",\n")
        sb.append("  \"status\": \"").append(status.name).append("\",\n")
        sb.append("  \"totalBytes\": ").append(totalBytes).append(",\n")
        sb.append("  \"downloadedBytes\": ").append(downloadedBytes).append(",\n")
        sb.append("  \"progressPercentage\": ").append(progressPercentage).append(",\n")
        sb.append("  \"addedTimestamp\": ").append(addedTimestamp).append(",\n")
        sb.append("  \"isSequentialMode\": ").append(isSequentialMode).append(",\n")
        if (errorMessage != null) {
            sb.append("  \"errorMessage\": \"").append(escape(errorMessage)).append("\"\n")
        } else {
            sb.append("  \"errorMessage\": null\n")
        }
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

        fun fromJsonString(jsonStr: String): QueueItem? {
            val map = parseJsonObject(jsonStr) ?: return null
            return QueueItem(
                id = map["id"] ?: UUID.randomUUID().toString(),
                url = map["url"] ?: "",
                fileName = map["fileName"] ?: "file",
                status = map["status"]?.let {
                    try {
                        QueueItemStatus.valueOf(it)
                    } catch (_: Exception) {
                        QueueItemStatus.QUEUED
                    }
                } ?: QueueItemStatus.QUEUED,
                totalBytes = map["totalBytes"]?.toLongOrNull() ?: 0L,
                downloadedBytes = map["downloadedBytes"]?.toLongOrNull() ?: 0L,
                progressPercentage = map["progressPercentage"]?.toFloatOrNull() ?: 0f,
                addedTimestamp = map["addedTimestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
                isSequentialMode = map["isSequentialMode"]?.toBooleanStrictOrNull() ?: true,
                errorMessage = map["errorMessage"]?.takeIf { it != "null" && it.isNotBlank() }
            )
        }

        fun serializeList(items: List<QueueItem>): String {
            if (items.isEmpty()) return "[]"
            return items.joinToString(
                prefix = "[\n",
                postfix = "\n]",
                separator = ",\n"
            ) { item ->
                item.toJsonString().prependIndent("  ")
            }
        }

        fun parseList(jsonContent: String): List<QueueItem> {
            val trimmed = jsonContent.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
            val items = mutableListOf<QueueItem>()

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
                    escapeNext = true
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
                            val jsonObj = trimmed.substring(objStart, i + 1)
                            fromJsonString(jsonObj)?.let { items.add(it) }
                            objStart = -1
                        }
                    }
                }
            }
            return items
        }

        private fun parseJsonObject(json: String): Map<String, String>? {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
            val map = mutableMapOf<String, String>()

            var inString = false
            var escapeNext = false
            var currentKey = ""
            val currentVal = java.lang.StringBuilder()
            var readingKey = true

            var i = 1
            while (i < trimmed.length - 1) {
                val c = trimmed[i]
                if (escapeNext) {
                    currentVal.append(c)
                    escapeNext = false
                    i++
                    continue
                }
                if (c == '\\') {
                    escapeNext = true
                    currentVal.append(c)
                    i++
                    continue
                }
                if (c == '"') {
                    inString = !inString
                    i++
                    continue
                }
                if (!inString) {
                    if (c == ':') {
                        currentKey = currentVal.toString().trim()
                        currentVal.setLength(0)
                        readingKey = false
                        i++
                        continue
                    } else if (c == ',') {
                        val v = currentVal.toString().trim()
                        if (currentKey.isNotBlank()) {
                            map[currentKey] = unescape(v)
                        }
                        currentKey = ""
                        currentVal.setLength(0)
                        readingKey = true
                        i++
                        continue
                    }
                }
                currentVal.append(c)
                i++
            }
            if (currentKey.isNotBlank()) {
                map[currentKey] = unescape(currentVal.toString().trim())
            }
            return map
        }
    }
}
