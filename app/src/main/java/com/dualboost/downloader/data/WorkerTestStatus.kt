package com.dualboost.downloader.data

sealed class WorkerTestStatus {
    object Idle : WorkerTestStatus()
    object Testing : WorkerTestStatus()
    data class Success(val message: String) : WorkerTestStatus()
    data class Error(val message: String) : WorkerTestStatus()
}

