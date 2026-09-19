package com.pdfatolyesi.app.core

import java.io.InputStream
import java.io.OutputStream
import java.io.IOException

class FileTooLarge : IOException()
object FileRules {
    const val MAX_IMPORT_BYTES = 150L * 1024 * 1024
    fun displayName(raw: String): String = raw.replace(Regex("[\\p{Cntrl}/\\\\]"), "_")
        .trim().take(160).ifBlank { "document.pdf" }.let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
    fun isPdf(prefix: ByteArray): Boolean = prefix.toString(Charsets.ISO_8859_1).contains("%PDF-")
    fun copyBounded(input: InputStream, output: OutputStream, limit: Long = MAX_IMPORT_BYTES,
                    checkpoint: (Long) -> Unit = {}): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            checkpoint(total)
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw FileTooLarge()
            output.write(buffer, 0, count)
        }
        checkpoint(total)
        return total
    }
}
object PageInput {
    fun index(raw: String, count: Int): Int? = raw.trim().toIntOrNull()
        ?.takeIf { it in 1..count }?.minus(1)
}
