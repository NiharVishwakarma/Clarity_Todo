package com.example.darktodo

import java.io.DataInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LauncherIconResourceContractTest {
    private val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
    private val android = "http://schemas.android.com/apk/res/android"

    private fun xml(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File(main, path)).documentElement

    private fun Element.attr(name: String): String = getAttributeNS(android, name)

    @Test fun manifestUsesMatchingAdaptiveIcons() {
        val application = xml("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        assertEquals("@mipmap/ic_clarity_suite", application.attr("icon"))
        assertEquals("@mipmap/ic_clarity_suite_round", application.attr("roundIcon"))
        listOf("ic_clarity_suite", "ic_clarity_suite_round").forEach { name ->
            val icon = xml("res/mipmap-anydpi-v26/$name.xml")
            assertEquals("adaptive-icon", icon.tagName)
            val background = icon.getElementsByTagName("background").item(0) as Element
            val foreground = icon.getElementsByTagName("foreground").item(0) as Element
            assertEquals("@drawable/ic_clarity_vault_background", background.attr("drawable"))
            assertEquals("@drawable/ic_clarity_suite_foreground", foreground.attr("drawable"))
        }
        assertEquals("vector", xml("res/drawable/ic_clarity_vault_background.xml").tagName)
    }

    @Test fun foregroundUsesUploadedArtwork() {
        val foreground = xml("res/drawable/ic_clarity_suite_foreground.xml")
        assertEquals("layer-list", foreground.tagName)
        val item = foreground.getElementsByTagName("item").item(0) as Element
        assertEquals("@drawable/clarity_suite_icon", item.attr("drawable"))
        assertEquals("center", item.attr("gravity"))
        assertEquals("90dp", item.attr("width"))
        assertEquals("90dp", item.attr("height"))
        val image = File(main, "res/drawable/clarity_suite_icon.png")
        assertTrue("Uploaded launcher artwork must exist", image.isFile)
        assertTrue("Uploaded launcher artwork must not be empty", image.length() > 0)
    }

    @Test fun uploadedArtworkHasNoCompetingResourceVariants() {
        val variants = File(main, "res").listFiles().orEmpty()
            .filter { it.isDirectory && (it.name == "drawable" || it.name.startsWith("drawable-")) }
            .flatMap { it.listFiles().orEmpty().toList() }
            .filter { it.name.substringBefore('.') == "clarity_suite_icon" }
            .map { it.relativeTo(main).invariantSeparatorsPath }
        assertEquals(listOf("res/drawable/clarity_suite_icon.png"), variants)
    }

    @Test fun adaptiveBackgroundUsesAnOpaqueFullBleedSurface() {
        val paths = xml("res/drawable/ic_clarity_vault_background.xml").getElementsByTagName("path")
        assertEquals(1, paths.length)
        assertEquals("#17171D", (paths.item(0) as Element).attr("fillColor"))
    }

    @Test fun sharedArtworkIsA1024PixelRgbaPng() {
        DataInputStream(File(main, "res/drawable/clarity_suite_icon.png").inputStream()).use { png ->
            assertEquals(0x89504E47.toInt(), png.readInt())
            assertEquals(0x0D0A1A0A, png.readInt())
            assertEquals(13, png.readInt())
            assertEquals(0x49484452, png.readInt()) // IHDR
            assertEquals(1024, png.readInt())
            assertEquals(1024, png.readInt())
            assertEquals(8, png.readUnsignedByte())
            assertEquals("PNG must retain an alpha channel (RGBA)", 6, png.readUnsignedByte())
            assertEquals(0, png.readUnsignedByte())
            assertEquals(0, png.readUnsignedByte())
            assertEquals(0, png.readUnsignedByte())
        }
    }
}
