package com.pdfatolyesi.app.core

import org.junit.Assert.*
import org.junit.Test

class PageInputTest {
    @Test fun humanPageNumbersBecomeZeroBased() {
        assertEquals(0, PageInput.index("1", 10))
        assertEquals(9, PageInput.index("10", 10))
        assertEquals(4, PageInput.index(" 5 ", 10))
    }
    @Test fun invalidPagesDoNotReachRenderer() {
        listOf("0", "-1", "11", "abc", "1.5", "", "99999999999999999").forEach { assertNull(PageInput.index(it, 10)) }
        assertNull(PageInput.index("1", 0))
    }
}
