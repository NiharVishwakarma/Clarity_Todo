package com.example.darktodo

import java.io.File
import javax.imageio.ImageIO
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
        assertEquals("60dp", item.attr("width"))
        assertEquals("60dp", item.attr("height"))
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

    @Test fun adaptiveBackgroundDoesNotAddAnOpaqueBackplate() {
        val paths = xml("res/drawable/ic_clarity_vault_background.xml").getElementsByTagName("path")
        assertEquals(1, paths.length)
        assertEquals("#00000000", (paths.item(0) as Element).attr("fillColor"))
    }

    @Test fun sharedArtworkHasTransparentPaddingAndRoundedCorners() {
        val image = ImageIO.read(File(main, "res/drawable/clarity_suite_icon.png"))
        assertEquals(1024, image.width)
        assertEquals(1024, image.height)
        assertTrue(image.colorModel.hasAlpha())
        fun alpha(x: Int, y: Int) = image.getRGB(x, y).ushr(24)
        for (i in 0 until 1024) {
            assertEquals(0, alpha(i, 0))
            assertEquals(0, alpha(i, 1023))
            assertEquals(0, alpha(0, i))
            assertEquals(0, alpha(1023, i))
        }
        listOf(80 to 90, 943 to 90, 80 to 933, 943 to 933).forEach { (x, y) ->
            assertEquals("Rounded corner must remain transparent", 0, alpha(x, y))
        }
        assertTrue("Central artwork must remain opaque", alpha(512, 512) >= 250)
        var left = image.width
        var right = -1
        var top = image.height
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (alpha(x, y) > 0) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        assertTrue("Artwork must be horizontally centered", kotlin.math.abs(left - (1023 - right)) <= 1)
        assertTrue("Artwork must be vertically centered", kotlin.math.abs(top - (1023 - bottom)) <= 1)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (alpha(x, y) > 0) {
                    val dx = (x - 511.5) * 60.0 / 1024
                    val dy = (y - 511.5) * 60.0 / 1024
                    assertTrue("Artwork must fit the adaptive 66dp safe circle", dx * dx + dy * dy <= 33.0 * 33.0)
                }
            }
        }
    }
}
