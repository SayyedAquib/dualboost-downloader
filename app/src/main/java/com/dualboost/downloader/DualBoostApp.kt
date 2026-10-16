package com.dualboost.downloader

import android.app.Application
import com.dualboost.downloader.data.DownloadRepository

class DualBoostApp : Application() {

    lateinit var repository: DownloadRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = DownloadRepository(this)
    }

    companion object {
        lateinit var instance: DualBoostApp
            private set
    }
}

