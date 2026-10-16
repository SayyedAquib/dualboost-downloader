package com.dualboost.downloader

import android.content.ContextWrapper
import java.io.File

class DummyTestContext(private val baseDir: File) : ContextWrapper(null) {
    override fun getExternalFilesDir(type: String?): File = baseDir
    override fun getFilesDir(): File = baseDir
}

