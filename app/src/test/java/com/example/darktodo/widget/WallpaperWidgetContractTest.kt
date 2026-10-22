package com.example.darktodo.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class WallpaperWidgetContractTest {
    private val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
    private val android = "http://schemas.android.com/apk/res/android"
    private fun xml(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File(main, path)).documentElement
    private fun Element.attr(name: String) = getAttributeNS(android, name)

    @Test fun wallpaperWidgetIsRegisteredAndUsesPrivateActionReceiver() {
        val manifest = xml("AndroidManifest.xml")
        val receivers = manifest.getElementsByTagName("receiver")
        val provider = (0 until receivers.length).map { receivers.item(it) as Element }.single {
            it.attr("name") == ".widget.WallpaperWidgetReceiver"
        }
        assertEquals("true", provider.attr("exported"))
        assertTrue(provider.getElementsByTagName("meta-data").let { metadata ->
            (0 until metadata.length).any { (metadata.item(it) as Element).attr("resource") == "@xml/wallpaper_widget_info" }
        })
        val actionReceiver = (0 until receivers.length).map { receivers.item(it) as Element }.single {
            it.attr("name") == ".widget.WallpaperWidgetActionReceiver"
        }
        assertEquals("false", actionReceiver.attr("exported"))

        val providerInfo = xml("res/xml/wallpaper_widget_info.xml")
        assertEquals("@layout/wallpaper_widget", providerInfo.attr("initialLayout"))
        assertEquals("0", providerInfo.attr("updatePeriodMillis"))
        assertEquals("home_screen", providerInfo.attr("widgetCategory"))
        val layout = xml("res/layout/wallpaper_widget.xml")
        val actionButton = layout.getElementsByTagName("Button").item(0) as? Element
        assertEquals("@string/wallpaper_widget_action", actionButton?.attr("text"))
    }

    @Test fun wallpaperApplicationPermissionIsNarrowAndWorkerUsesSharedEngine() {
        val manifest = xml("AndroidManifest.xml")
        val permissions = manifest.getElementsByTagName("uses-permission")
        assertTrue((0 until permissions.length).map { (permissions.item(it) as Element).attr("name") }
            .contains("android.permission.SET_WALLPAPER"))
        val worker = File(main, "java/com/example/darktodo/widget/WallpaperWidget.kt").readText()
        assertTrue(worker.contains("WallpaperEngine.render"))
        assertTrue(worker.contains("WallpaperManager.getInstance"))
        assertTrue(worker.contains("enqueueUniqueWork"))
    }
}
