package com.morepp.fm21touchsaveeditor

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvEscapeTest {
    @Test
    fun quotesAndEscapesOrdinaryCsvValues() {
        assertEquals("\"plain\"", csvEscape("plain"))
        assertEquals("\"a,b\"", csvEscape("a,b"))
        assertEquals("\"a\"\"b\"", csvEscape("a\"b"))
    }

    @Test
    fun neutralizesSpreadsheetFormulaPrefixes() {
        assertEquals("\"'=1+1\"", csvEscape("=1+1"))
        assertEquals("\"'  @SUM(A1:A2)\"", csvEscape("  @SUM(A1:A2)"))
        assertEquals("\"'-42\"", csvEscape("-42"))
        assertEquals("\"'+cmd\"", csvEscape("+cmd"))
    }

    @Test
    fun neutralizesLeadingControlPrefixesAndPreservesUnicodeText() {
        assertEquals("\"'\t=1+1\"", csvEscape("\t=1+1"))
        assertEquals("\"Nama pemain\"", csvEscape("Nama pemain"))
        assertEquals("\"'\uFEFF=1+1\"", csvEscape("\uFEFF=1+1"))
    }
}
