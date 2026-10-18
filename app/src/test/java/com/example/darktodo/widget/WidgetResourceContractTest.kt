package com.example.darktodo.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class WidgetResourceContractTest {
    private val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
    private val android = "http://schemas.android.com/apk/res/android"

    private fun xml(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File(main, path)).documentElement

    private fun Element.attr(name: String): String = getAttributeNS(android, name)

    private fun Element.elements(): List<Element> {
        val nodes = getElementsByTagName("*")
        return listOf(this) + (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.withId(id: String): Element = elements().single { it.attr("id") == "@+id/$id" }

    @Test fun notesLiveAndPreviewLayoutsOnlyUseRemoteViewsSupportedClasses() {
        val supported = setOf("FrameLayout", "LinearLayout", "ImageView", "TextView")
        listOf("sticker_widget", "sticker_widget_preview").forEach { name ->
            xml("res/layout/$name.xml").elements().forEach {
                assertTrue("$name contains unsupported ${it.tagName}", it.tagName in supported)
            }
        }
        val preview = xml("res/layout/sticker_widget_preview.xml")
        val lines = preview.elements().filter { it.tagName == "TextView" }
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.attr("maxLines") == "1" })
        assertTrue(lines.all { it.attr("text").startsWith("@string/notes_widget_preview_") })
        assertEquals("ImageView", xml("res/layout/sticker_widget.xml").withId("sticker_widget_image").tagName)
    }

    @Test fun notesProviderHasConfigurationAndCompactDefaults() {
        val provider = xml("res/xml/sticker_widget_info.xml")
        assertEquals("@layout/sticker_widget", provider.attr("initialLayout"))
        assertEquals("@layout/sticker_widget_preview", provider.attr("previewLayout"))
        assertEquals("@drawable/widget_preview_sticker", provider.attr("previewImage"))
        assertEquals("4", provider.attr("targetCellWidth"))
        assertEquals("1", provider.attr("targetCellHeight"))
        assertEquals("300dp", provider.attr("minWidth"))
        assertEquals("70dp", provider.attr("minHeight"))
        assertEquals("horizontal|vertical", provider.attr("resizeMode"))
        val manifest = xml("AndroidManifest.xml").elements()
        val receiver = manifest.single { it.tagName == "receiver" && it.attr("name") == ".widget.StickerWidgetReceiver" }
        assertEquals("true", receiver.attr("exported"))
        assertTrue(receiver.elements().any { it.tagName == "meta-data" && it.attr("resource") == "@xml/sticker_widget_info" })
        assertTrue(manifest.any { it.tagName == "activity" && "com.example.darktodo${it.attr("name")}" == provider.attr("configure") })
    }

    @Test fun everyTodoFontLayoutKeepsBackgroundBehindAnUnfadedCollection() {
        val names = listOf("todo_widget", "todo_widget_imported_font", "todo_widget_sans_serif",
            "todo_widget_sans_serif_condensed", "todo_widget_sans_serif_light", "todo_widget_serif", "todo_widget_monospace")
        names.forEach { name ->
            val root = xml("res/layout/$name.xml")
            val list = root.withId("widget_task_rows")
            assertEquals("@android:color/transparent", list.attr("cacheColorHint"))
            assertEquals("false", list.attr("scrollingCache"))
            assertEquals("none", list.attr("requiresFadingEdge"))
            assertEquals("never", list.attr("overScrollMode"))
            val card = root.withId("widget_task_card")
            val children = (0 until card.childNodes.length).map { card.childNodes.item(it) }.filterIsInstance<Element>()
            assertEquals("@+id/widget_task_card_background", children.first().attr("id"))
            val elements = root.elements()
            assertTrue(elements.indexOf(root.withId("widget_root_background")) < elements.indexOf(list))
            assertTrue(elements.indexOf(root.withId("widget_task_card_background")) < elements.indexOf(list))
        }
    }

    @Test fun vaultUsesTheSameLargerArtworkInLiveAndPickerResources() {
        val icon = xml("res/layout/vault_widget.xml").withId("vault_widget_icon")
        assertEquals("62dp", icon.attr("layout_height"))
        assertEquals("wrap_content", icon.attr("layout_width"))
        assertEquals("true", icon.attr("adjustViewBounds"))
        assertEquals("fitCenter", icon.attr("scaleType"))
        assertEquals("@drawable/vault_icon", icon.attr("src"))
        val previewIcon = xml("res/drawable/vault_widget_preview.xml").elements().single { it.attr("drawable") == icon.attr("src") }
        assertEquals("62dp", previewIcon.attr("height"))
        assertEquals("62dp", previewIcon.attr("width"))
        assertEquals("start|center_vertical", previewIcon.attr("gravity"))
        assertEquals("@layout/vault_widget", xml("res/xml/vault_widget_info.xml").attr("previewLayout"))
    }
}
