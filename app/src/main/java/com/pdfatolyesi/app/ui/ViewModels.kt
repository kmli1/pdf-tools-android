package com.pdfatolyesi.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfatolyesi.app.PdfApplication
import com.pdfatolyesi.app.R
import com.pdfatolyesi.app.core.FileTooLarge
import com.pdfatolyesi.app.data.*
import com.pdfatolyesi.app.pdf.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun errorText(error: Throwable): Int = when (error) {
    is FileTooLarge -> R.string.error_too_large
    is PasswordUnavailable -> R.string.error_old_password
    is SecurityException -> R.string.error_access
    is InvalidPdf, is IllegalArgumentException -> R.string.error_pdf
    else -> R.string.error_operation
}
data class ImportState(val busy: Boolean = false, val bytes: Long = 0, val openId: String? = null, val error: Int? = null)
class LibraryViewModel(private val app: PdfApplication) : ViewModel() {
    val documents = app.documents.documents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val theme = app.settings.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
    private val mutable = MutableStateFlow(ImportState())
    val state = mutable.asStateFlow()
    private var importJob: Job? = null
    fun import(uri: Uri) {
        if (mutable.value.busy) return
        importJob = viewModelScope.launch {
            mutable.value = ImportState(busy = true)
            try {
                val id = app.documents.import(uri) { bytes -> mutable.update { it.copy(bytes = bytes) } }
                mutable.value = ImportState(openId = id)
            } catch (e: CancellationException) { mutable.value = ImportState(); throw e }
            catch (e: Exception) { mutable.value = ImportState(error = errorText(e)) }
        }
    }
    fun cancelImport() { importJob?.cancel() }
    fun clearEvent() { mutable.update { it.copy(openId = null, error = null) } }
    private fun act(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(error = errorText(e)) } }
    } }
    fun favorite(id: String) = act { app.documents.favorite(id) }
    fun remove(id: String) = act { app.documents.remove(id) }
    fun rename(id: String, value: String) = act { app.documents.rename(id, value) }
    fun theme(value: ThemeMode) = act { app.settings.setTheme(value) }
}
data class ViewerState(val loading: Boolean = true, val document: Document? = null, val pages: Int = 0,
    val password: Boolean = false, val error: Int? = null, val initialPage: Int = 0,
    val exporting: Boolean = false, val exported: Boolean = false)
class ViewerViewModel(private val app: PdfApplication, private val id: String) : ViewModel() {
    private val session = app.engine.createSession()
    private val mutable = MutableStateFlow(ViewerState())
    val state = mutable.asStateFlow()
    private var openJob: Job? = null
    private var exportJob: Job? = null
    init { open() }
    fun open(password: String? = null) {
        openJob?.cancel()
        openJob = viewModelScope.launch {
            mutable.update { it.copy(loading = true, password = false, error = null) }
            try {
                val doc = app.documents.get(id) ?: throw InvalidPdf()
                mutable.update { it.copy(document = doc) }
                session.open(app.documents.file(id), password)
                app.documents.opened(id, session.pageCount)
                mutable.value = ViewerState(loading = false, document = doc, pages = session.pageCount,
                    initialPage = doc.lastPage.coerceIn(0, session.pageCount - 1))
            } catch (e: CancellationException) { throw e }
            catch (_: PasswordRequired) { mutable.update { it.copy(loading = false, password = true) } }
            catch (e: Exception) { mutable.update { it.copy(loading = false, error = errorText(e)) } }
        }
    }
    suspend fun render(page: Int, width: Int): Bitmap = session.render(page, width)
    fun position(page: Int) { viewModelScope.launch { runCatching { app.documents.position(id, page) } } }
    fun export(uri: Uri) {
        if (mutable.value.exporting) return
        exportJob = viewModelScope.launch {
            mutable.update { it.copy(exporting = true, exported = false) }
            try { app.documents.export(id, uri); mutable.update { it.copy(exporting = false, exported = true) } }
            catch (e: CancellationException) { mutable.update { it.copy(exporting = false) }; throw e }
            catch (e: Exception) { mutable.update { it.copy(exporting = false, error = errorText(e)) } }
        }
    }
    fun clearMessage() { mutable.update { it.copy(exported = false, error = null) } }
    fun cancelExport() { exportJob?.cancel() }
    override fun onCleared() {
        super.onCleared()
        CoroutineScope(Dispatchers.IO).launch { session.close() }
    }
}
