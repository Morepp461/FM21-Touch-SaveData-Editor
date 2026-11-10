package com.morepp.fm21touchsaveeditor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FmcDatabaseMetadataParserTest {
    private val selectedDatabaseXml = """
        <properties version="1.0">
            <string id="author" value="Sports Interactive"/>
            <boolean id="fmc" value="true"/>
            <boolean id="fmm" value="false"/>
            <string id="version" value="21.4.0" translate="false"/>
            <string id="required_version" value="21.3.1" translate="false"/>
        </properties>
    """.trimIndent()

    @Test
    fun parsesSelected2140DatabaseMetadata() {
        val metadata = FmcDatabaseMetadataParser.parse(selectedDatabaseXml)

        assertEquals("21.4.0", metadata.version)
        assertEquals("21.3.1", metadata.requiredVersion)
        assertEquals("Sports Interactive", metadata.author)
        assertTrue(metadata.isFmc)
        assertFalse(metadata.isFmm)
        assertTrue(metadata.isSupportedTarget)
        assertNull(metadata.name)
    }

    @Test
    fun rejectsDifferentDatabaseBuildAsUnsupportedTarget() {
        val xml = selectedDatabaseXml.replace("21.4.0", "21.4.1")

        assertFalse(FmcDatabaseMetadataParser.parse(xml).isSupportedTarget)
    }

    @Test
    fun rejectsMetadataForNonFmcDatabase() {
        val xml = selectedDatabaseXml.replace("""id="fmc" value="true"""", """id="fmc" value="false"""")

        assertFalse(FmcDatabaseMetadataParser.parse(xml).isSupportedTarget)
    }

    @Test
    fun rejectsExternalEntityDeclarations() {
        val xml = """<!DOCTYPE properties [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>""" +
            selectedDatabaseXml

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            FmcDatabaseMetadataParser.parse(xml)
        }
    }

    @Test
    fun rejectsMissingRequiredVersion() {
        val xml = selectedDatabaseXml.replace(
            """<string id="required_version" value="21.3.1" translate="false"/>""",
            ""
        )

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            FmcDatabaseMetadataParser.parse(xml)
        }
    }
}
