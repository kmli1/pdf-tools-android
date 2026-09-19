package com.pdfatolyesi.app

import android.app.Application
import androidx.room.Room
import com.pdfatolyesi.app.data.*
import com.pdfatolyesi.app.pdf.AndroidPdfEngine
import kotlinx.coroutines.*

class PdfApplication : Application() {
    val engine by lazy { AndroidPdfEngine() }
    private val database by lazy { Room.databaseBuilder(this, AppDatabase::class.java, "catalog.db").build() }
    val documents by lazy { DocumentRepository(this, database, engine) }
    val settings by lazy { SettingsRepository(this) }
    override fun onCreate() {
        super.onCreate()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { documents.cleanup() } }
    }
}
