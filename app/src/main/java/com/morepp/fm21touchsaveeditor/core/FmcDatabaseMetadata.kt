package com.morepp.fm21touchsaveeditor.core

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parsed metadata from the FM21 Touch FMC database's 2140_fmc.xml file.
 *
 * This identifies the selected database build only. It does not establish
 * the layout or semantics of any binary .dat records.
 */
data class FmcDatabaseMetadata(
    val name: String?,
    val author: String?,
    val version: String,
    val requiredVersion: String,
    val isFmc: Boolean,
    val isFmm: Boolean
) {
    /** The sole database build currently selected and supported by this project. */
    val isSupportedTarget: Boolean
        get() = version == SUPPORTED_DATABASE_VERSION && isFmc && !isFmm

    companion object {
        const val SUPPORTED_DATABASE_VERSION = "21.4.0"
    }
}

object FmcDatabaseMetadataParser {
    fun parse(xml: String): FmcDatabaseMetadata {
        require(xml.isNotBlank()) { "Metadata XML is empty" }

        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val root = document.documentElement
        require(root != null && root.tagName == "properties") {
            "Unsupported FMC metadata root element"
        }

        fun value(tag: String, id: String): String? {
            val nodes = root.childNodes
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                if (node is Element && node.tagName == tag && node.getAttribute("id") == id) {
                    return node.getAttribute("value").takeIf { it.isNotBlank() }
                }
            }
            return null
        }

        val version = value("string", "version")
            ?: throw IllegalArgumentException("FMC metadata is missing database version")
        val requiredVersion = value("string", "required_version")
            ?: throw IllegalArgumentException("FMC metadata is missing required game version")
        val fmc = value("boolean", "fmc")
            ?: throw IllegalArgumentException("FMC metadata is missing the fmc flag")
        val fmm = value("boolean", "fmm")
            ?: throw IllegalArgumentException("FMC metadata is missing the fmm flag")

        require(fmc == "true" || fmc == "false") { "Invalid fmc boolean value" }
        require(fmm == "true" || fmm == "false") { "Invalid fmm boolean value" }

        return FmcDatabaseMetadata(
            name = value("string", "name"),
            author = value("string", "author"),
            version = version,
            requiredVersion = requiredVersion,
            isFmc = fmc.toBoolean(),
            isFmm = fmm.toBoolean()
        )
    }
}
