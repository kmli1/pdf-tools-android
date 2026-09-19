package com.pdfatolyesi.app.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.LoadParams
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.*

class AndroidPdfEngine : PdfEngine {
    override fun createSession(): PdfSession = AndroidPdfSession()
}
private class AndroidPdfSession : PdfSession {
    private val mutex = Mutex()
    private var renderer: PdfRenderer? = null
    override var pageCount: Int = 0
        private set
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        // Never recycle on eviction: Compose may still display the bitmap.
    }
    override suspend fun open(file: File, password: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            clear()
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                renderer = if (Build.VERSION.SDK_INT >= 35 && password != null) {
                    PdfRenderer(descriptor, LoadParams.Builder().setPassword(password).build())
                } else PdfRenderer(descriptor)
                pageCount = renderer!!.pageCount
                if (pageCount <= 0) throw InvalidPdf()
            } catch (e: Exception) {
                clear()
                runCatching { descriptor.close() }
                when (e) {
                    is SecurityException -> if (Build.VERSION.SDK_INT >= 35) throw PasswordRequired() else throw PasswordUnavailable()
                    else -> throw e
                }
            }
        }
    }
    override suspend fun render(page: Int, width: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureActive()
            val targetWidth = width.coerceIn(80, 2048)
            val key = "$page:$targetWidth"
            cache.get(key)?.let { return@withLock it }
            val engine = renderer ?: throw InvalidPdf()
            require(page in 0 until pageCount)
            engine.openPage(page).use { source ->
                val ratio = source.height.toDouble() / source.width.coerceAtLeast(1)
                // Bound pathological page dimensions to 4 MP and 8192 pixels/side.
                val w = min(targetWidth.toDouble(), min(sqrt(4_000_000.0 / ratio), 8192.0 / ratio)).toInt().coerceAtLeast(1)
                val h = (w * ratio).toInt().coerceIn(1, 8192)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    ensureActive()
                    cache.put(key, bitmap)
                    bitmap
                } catch (e: Exception) { bitmap.recycle(); throw e }
            }
        }
    }
    override suspend fun close() = withContext(Dispatchers.IO + NonCancellable) { mutex.withLock { clear() } }
    private fun clear() {
        cache.evictAll()
        renderer?.close()
        renderer = null
        pageCount = 0
    }
}
