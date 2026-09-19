package com.pdfatolyesi.app.pdf

import android.graphics.Bitmap
import java.io.File

interface PdfSession {
    val pageCount: Int
    suspend fun open(file: File, password: String? = null)
    suspend fun render(page: Int, width: Int): Bitmap
    suspend fun close()
}
fun interface PdfEngine { fun createSession(): PdfSession }
class PasswordRequired : Exception()
class PasswordUnavailable : Exception()
class InvalidPdf : Exception()
