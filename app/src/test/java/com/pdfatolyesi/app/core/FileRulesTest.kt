package com.pdfatolyesi.app.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class FileRulesTest {
    @Test fun copiesExactBytesInOrder() {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()
        assertEquals(bytes.size.toLong(), FileRules.copyBounded(bytes.inputStream(), output, bytes.size.toLong()) { progress += it })
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(0L, progress.first())
        assertEquals(bytes.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
    }
    @Test fun emptyStreamIsCopiedWithoutInventingBytes() {
        val out = ByteArrayOutputStream()
        assertEquals(0L, FileRules.copyBounded(ByteArrayInputStream(byteArrayOf()), out, 0))
        assertEquals(0, out.size())
    }
    @Test fun oversizedStreamNeverWritesBeyondLimit() {
        val out = ByteArrayOutputStream()
        try {
            FileRules.copyBounded(ByteArray(150_000).inputStream(), out, 70_000)
            fail("Oversize accepted")
        } catch (_: FileTooLarge) { assertTrue(out.size() <= 70_000) }
    }
    @Test fun cancellationStopsFurtherWrites() {
        val out = ByteArrayOutputStream()
        try {
            FileRules.copyBounded(ByteArray(200_000).inputStream(), out) { if (it > 0) throw java.util.concurrent.CancellationException() }
            fail("Cancellation swallowed")
        } catch (_: java.util.concurrent.CancellationException) { assertEquals(65_536, out.size()) }
    }
    @Test fun writeFailureIsPropagated() {
        val sink = object : java.io.OutputStream() { override fun write(value: Int) { throw IOException("fixture") } }
        try { FileRules.copyBounded(byteArrayOf(1).inputStream(), sink); fail("Failure swallowed") }
        catch (_: IOException) { }
    }
    @Test fun turkishFileNamesArePreserved() { assertEquals("İğde ŞÖÇÜ ı.pdf", FileRules.displayName("İğde ŞÖÇÜ ı.pdf")) }
    @Test fun directoryCharactersAndControlsAreRemoved() {
        val result = FileRules.displayName("../private\\secret\n.pdf")
        assertFalse(result.contains('/')); assertFalse(result.contains('\\')); assertFalse(result.contains('\n'))
    }
    @Test fun emptyFileNameHasSafeDefault() { assertEquals("document.pdf", FileRules.displayName("  ")) }
    @Test fun extensionIsNotDuplicated() { assertEquals("Belge.PDF", FileRules.displayName("Belge.PDF")) }
    @Test fun arbitrarySuffixGetsPdfExtension() { assertEquals("belge.txt.pdf", FileRules.displayName("belge.txt")) }
    @Test fun longNameIsBounded() { assertTrue(FileRules.displayName("a".repeat(1000)).length <= 164) }
    @Test fun headerAllowsPrefixBytesButRejectsHtml() {
        assertTrue(FileRules.isPdf("\uFEFF%PDF-1.7".toByteArray()))
        assertFalse(FileRules.isPdf("<html>error</html>".toByteArray()))
        assertFalse(FileRules.isPdf(byteArrayOf()))
    }
}
