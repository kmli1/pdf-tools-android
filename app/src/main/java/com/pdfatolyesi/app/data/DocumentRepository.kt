package com.pdfatolyesi.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.pdfatolyesi.app.core.FileRules
import com.pdfatolyesi.app.pdf.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.UUID

class DocumentRepository(private val context: Context, private val db: AppDatabase, private val engine: PdfEngine) {
    private val dao = db.documents()
    private val mutation = Mutex()
    private val directory = File(context.filesDir, "documents").apply { mkdirs() }
    val documents = dao.observe()
    fun file(id: String): File {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        return File(directory, "$id.pdf")
    }
    suspend fun get(id: String) = dao.get(id)
    // Only app-owned snapshots live here. Source content URIs are never modified.
    suspend fun import(uri: Uri, suppliedName: String? = null, progress: (Long) -> Unit): String = withContext(Dispatchers.IO) {
        mutation.withLock {
            val id = UUID.randomUUID().toString()
            val partial = File(directory, "$id.part")
            val destination = file(id)
            val name = suppliedName ?: context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "document.pdf"
            var committed = false
            try {
                val job = currentCoroutineContext()
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    partial.outputStream().use { output ->
                        FileRules.copyBounded(input, output) { copied -> job.ensureActive(); progress(copied) }
                    }
                } ?: throw IOException()
                val prefix = partial.inputStream().use { input ->
                    val buffer = ByteArray(1024)
                    val count = input.read(buffer)
                    buffer.copyOf(count.coerceAtLeast(0))
                }
                if (!FileRules.isPdf(prefix)) throw InvalidPdf()
                val session = engine.createSession()
                val pages = try {
                    session.open(partial)
                    session.pageCount
                } catch (_: PasswordRequired) { 0 } catch (_: PasswordUnavailable) { 0 }
                finally { session.close() }
                job.ensureActive()
                if (!partial.renameTo(destination)) throw IOException()
                // Keep disk and DB commit together even when the importing screen closes.
                withContext(NonCancellable) {
                    val now = System.currentTimeMillis()
                    db.withTransaction {
                        dao.insert(Document(id, FileRules.displayName(name), bytes, pages, now, now))
                        dao.record(History(documentId = id, action = "import", timestamp = now))
                    }
                    committed = true
                }
                id
            } finally {
                partial.delete()
                if (!committed) destination.delete()
            }
        }
    }
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        mutation.withLock {
            val ids = dao.ids().toSet()
            directory.listFiles()?.forEach { if (it.extension == "part" || it.nameWithoutExtension !in ids) it.delete() }
        }
    }
    suspend fun favorite(id: String) = dao.favorite(id)
    suspend fun rename(id: String, name: String) = dao.rename(id, FileRules.displayName(name))
    suspend fun position(id: String, page: Int) = dao.position(id, page)
    suspend fun opened(id: String, pages: Int) = db.withTransaction {
        val now = System.currentTimeMillis()
        dao.opened(id, now, pages)
        dao.record(History(documentId = id, action = "open", timestamp = now))
    }
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        mutation.withLock {
            db.withTransaction { dao.deleteHistory(id); dao.delete(id) }
            file(id).delete() // Orphan cleanup retries this if the filesystem refuses deletion.
        }
    }
    suspend fun export(id: String, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val job = currentCoroutineContext()
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                file(id).inputStream().use { input -> FileRules.copyBounded(input, output) { job.ensureActive() } }
            } ?: throw IOException()
        } catch (e: Exception) {
            // SAF providers are not universally transactional. Delete failed output when permitted.
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw e
        }
    }
}
