package com.example.darktodo.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class VaultAppearanceResourceTest {
    private val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
    private fun source(path: String) = File(main, "java/com/example/darktodo/$path").readText()

    @Test fun backgroundIsSeparateAndArtworkNeverFades() {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(main, "res/layout/vault_widget.xml"))
        val root = document.documentElement
        val namespace = "http://schemas.android.com/apk/res/android"
        assertEquals("FrameLayout", root.tagName)
        assertEquals("", root.getAttributeNS(namespace, "background"))
        val elements = root.getElementsByTagName("*")
        val first = elements.item(0) as Element
        assertEquals("@+id/vault_widget_background", first.getAttributeNS(namespace, "id"))
        (0 until elements.length).map { elements.item(it) as Element }.forEach {
            assertTrue(it.tagName in setOf("ImageView", "LinearLayout", "TextView"))
            assertEquals("", it.getAttributeNS(namespace, "alpha"))
        }
        val renderer = source("widget/VaultWidget.kt")
        assertTrue(renderer.contains("LocalWidgetFonts.typeface(context, style.fontFileId)"))
        assertTrue(renderer.contains("Typeface.create(base, weight, false)"))
        assertTrue(renderer.contains("TextUtils.ellipsize"))
        assertTrue(renderer.contains("if (naturalHeight > availableHeight)"))
        assertTrue(renderer.contains("maxHeightDp.coerceIn(1f, 200f)"))
        assertTrue(renderer.contains("widthDp.coerceIn(1, 600)"))
        assertTrue(renderer.contains("heightDp.coerceIn(1, 400)"))
        assertTrue(renderer.contains("Bitmap.createBitmap"))
        assertFalse(renderer.contains("setImageAlpha"))
        assertFalse(renderer.contains("setColorFilter"))
        assertTrue(renderer.contains("paint.color = style.outlineArgb()"))
        assertFalse(renderer.contains("loadEntries"))
    }

    @Test fun resetAndSavesUseOnlyDedicatedGlobalPreferencesAndRefreshAllInstances() {
        val prefs = source("data/VaultAppearance.kt")
        assertTrue(prefs.contains("const val FILE = \"vault_widget_appearance\""))
        assertTrue(prefs.contains("getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()"))
        assertEquals(2, Regex("VaultWidget.updateAll\\(context\\)").findAll(prefs).count())
        listOf("todo_store", "calendar_widget_settings", "vault_auth", "sticker_store", "LocalVaultStore")
            .forEach { assertFalse(prefs.contains(it)) }
        val widget = source("widget/VaultWidget.kt")
        assertTrue(widget.contains("getAppWidgetIds(ComponentName(context, VaultWidgetReceiver::class.java))"))
        assertTrue(widget.contains("override fun onAppWidgetOptionsChanged"))
        assertTrue(widget.contains("MainActivity.ACTION_OPEN_VAULT"))
    }
}
