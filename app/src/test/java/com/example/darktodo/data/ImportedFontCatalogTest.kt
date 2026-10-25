package com.example.darktodo.data

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportedFontCatalogTest {
    @get:Rule val folder = TemporaryFolder()
    private fun catalog() = ImportedFontCatalog { _, error -> throw AssertionError(error) }
    private fun asset(id: String, bytes: ByteArray, name: String = "Same.ttf"): ImportedWidgetFont {
        File(folder.root, id).writeBytes(bytes)
        return ImportedWidgetFont(id, name)
    }

    @Test fun identityUsesTheWholeFileWithAStableSha256Digest() {
        val file = folder.newFile().apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            catalog().hash(file))
    }

    @Test fun oneImportAndReimportResolveToOneAssetForEverySupportedExtension() {
        for (extension in listOf("ttf", "otf", "ttc")) {
            val catalog = catalog()
            val bytes = sfnt(mapOf(4 to "Example SemiBold"))
            val first = asset("a.$extension", bytes)
            val incoming = folder.newFile("incoming-$extension.tmp").apply { writeBytes(bytes) }
            val entries = listOf(first)
            assertEquals(first.id, catalog.find(folder.root, entries, catalog.hash(incoming))?.id)
            assertEquals(1, catalog.list(folder.root, entries).size)
            assertFalse(File(folder.root, "b.$extension").exists())
        }
    }

    @Test fun duplicateCopiesAndRepeatedMetadataProduceOneEntryWithEveryLegacyId() {
        val bytes = sfnt(mapOf(4 to "Example SemiBold"))
        val first = asset("a.ttf", bytes)
        val copy = asset("b.otf", bytes, "Renamed.otf")
        val entries = listOf(first, copy, first.copy(name = "Older name"))
        val result = catalog().list(folder.root, entries).single()
        assertEquals(first.id, result.id)
        assertEquals(setOf("a.ttf", "b.otf"), result.assetIds)
        assertTrue(result.containsId("b.otf"))
        assertFalse(result.containsId(null))
        assertTrue(File(folder.root, copy.id).isFile)
        assertEquals(3, entries.size)
    }

    @Test fun differentContentsWithSameFilenameAndEmbeddedNameStayDistinct() {
        val bytes = sfnt(mapOf(4 to "Same Family"))
        val first = asset("a.ttf", bytes)
        val second = asset("b.ttf", bytes + byteArrayOf(1))
        val catalog = catalog()
        assertEquals(2, catalog.list(folder.root, listOf(first, second)).size)
        assertNull(catalog.find(folder.root, listOf(first), catalog.hash(File(folder.root, second.id))))
    }

    @Test fun repeatedDiscoveryAndProcessRestartDoNotAppendEntriesOrChangeIdentity() {
        val bytes = sfnt(mapOf(4 to "Example Regular"))
        val entries = listOf(asset("a.ttf", bytes), asset("b.ttf", bytes))
        val catalog = catalog()
        val expected = catalog.list(folder.root, entries)
        repeat(5) { assertEquals(expected, catalog.list(folder.root, entries)) }
        assertEquals(expected, catalog().list(folder.root, entries))
        assertEquals(setOf("a.ttf", "b.ttf"), folder.root.listFiles()!!.map { it.name }.toSet())
    }

    @Test fun missingRepresentativeDoesNotHideAnAvailableLegacyCopy() {
        val bytes = sfnt(mapOf(4 to "Example"))
        val first = asset("a.ttf", bytes)
        val second = asset("b.ttf", bytes)
        val entries = listOf(first, second)
        val catalog = catalog()
        assertEquals(first.id, catalog.list(folder.root, entries).single().id)
        assertTrue(File(folder.root, first.id).delete())
        assertEquals(second.id, catalog.list(folder.root, entries).single().id)
    }

    @Test fun changedPrivateFileInvalidatesFingerprintCache() {
        val bytes = sfnt(mapOf(4 to "Example"))
        val entries = listOf(asset("a.ttf", bytes), asset("b.ttf", bytes))
        val catalog = catalog()
        assertEquals(1, catalog.list(folder.root, entries).size)
        File(folder.root, "b.ttf").appendBytes(byteArrayOf(1))
        assertEquals(2, catalog.list(folder.root, entries).size)
    }

    @Test fun fullNameAndFamilyStyleFallbackAreReadableAndKeepWeightsDistinct() {
        val regular = asset("a.ttf", sfnt(mapOf(1 to "Example", 2 to "Regular")))
        val semibold = asset("b.otf", sfnt(mapOf(16 to "Example", 17 to "SemiBold"), otf = true))
        val italic = asset("c.ttf", sfnt(mapOf(1 to "Example", 2 to "Italic", 4 to "Example Italic")))
        assertEquals(listOf("Example", "Example SemiBold", "Example Italic"),
            catalog().list(folder.root, listOf(regular, semibold, italic)).map { it.name })
    }

    @Test fun collectionUsesFirstFaceWithAbsoluteTableOffsets() {
        val font = sfnt(mapOf(4 to "Collection SemiBold"))
        val firstOffset = 20
        ByteBuffer.wrap(font).putInt(20, 28 + firstOffset)
        val collection = ByteArrayOutputStream()
        DataOutputStream(collection).use {
            it.writeInt(0x74746366)
            it.writeInt(0x00010000)
            it.writeInt(2)
            it.writeInt(firstOffset)
            it.writeInt(firstOffset + font.size)
            it.write(font)
            it.write(sfnt(mapOf(4 to "Second Face")))
        }
        val entry = asset("a.ttc", collection.toByteArray())
        assertEquals("Collection SemiBold", catalog().list(folder.root, listOf(entry)).single().name)
    }

    @Test fun missingOrMalformedNameMetadataFallsBackWithoutDroppingTheFont() {
        val entries = listOf(
            asset("a.ttf", byteArrayOf(1, 2, 3), "Fallback.ttf"),
            asset("b.otf", sfnt(mapOf(4 to "Bad\nName")), "Fallback.otf"),
            asset("c.ttf", sfnt(mapOf(4 to "Out of bounds")).also {
                ByteBuffer.wrap(it).putInt(20, Int.MAX_VALUE)
            }, "Other.ttf")
        )
        assertEquals(entries.map { it.name }, catalog().list(folder.root, entries).map { it.name })
    }

    @Test fun truncatedNameRecordsAreNotReadPastTheirTable() {
        val font = sfnt(mapOf(4 to "Example"))
        ByteBuffer.wrap(font).putShort(32, 1)
        assertNull(SfntFontName.read(folder.newFile().apply { writeBytes(font) }))
    }

    @Test fun malformedNameTableStillDeduplicatesByContent() {
        val malformed = sfnt(mapOf(4 to "Example")).also {
            ByteBuffer.wrap(it).putShort(30, 100)
        }
        val entries = listOf(asset("a.ttf", malformed), asset("b.ttf", malformed))
        assertEquals(1, catalog().list(folder.root, entries).size)
    }

    @Test fun importChecksContentBeforeSavingAndRetainsTheOriginalRegistry() {
        val main = File("app/src/main").takeIf { it.isDirectory } ?: File("src/main")
        val source = File(main, "java/com/example/darktodo/data/LocalWidgetFonts.kt")
            .readText()
            .replace("\r\n", "\n")
        val duplicateCheck = source.indexOf("catalog.find(directory, entries, hash)?.let { return it }")
        assertTrue(duplicateCheck > source.indexOf("Typeface.Builder(temporary).build()"))
        assertTrue(duplicateCheck < source.indexOf("temporary.renameTo(destination)"))
        assertTrue(source.contains("JSONArray(prefs.getString(KEY_FONTS"))
        assertTrue(source.contains("array.put(JSONObject().put(\"id\", id).put(\"name\", displayName))"))
        assertTrue(source.contains("@Synchronized\n    fun import"))
        assertFalse(source.contains("destination.delete"))
    }

    @Test fun everySelectionSurfaceRecognizesLegacyAliasesAndRenderingKeepsOriginalIds() {
        val main = File("app/src/main").takeIf { it.isDirectory } ?: File("src/main")
        fun source(path: String) = File(main, "java/com/example/darktodo/$path").readText()
        listOf("data/AppTypographySettings.kt", "data/WidgetPrefs.kt", "data/CalendarWidgetPrefs.kt",
            "data/VaultAppearance.kt", "MainActivity.kt", "ui/NoteTextAppearance.kt", "ui/VaultAppearanceScreen.kt")
            .forEach { assertTrue(it, source(it).contains(".containsId(")) }
        listOf("widget/StickerWidget.kt", "widget/TodoWidget.kt", "widget/CalendarWidget.kt",
            "widget/VaultWidget.kt").forEach { assertTrue(it, source(it).contains("LocalWidgetFonts.")) }
        assertTrue(source("data/LocalWidgetFonts.kt").contains("Typeface.createFromFile(File(root(context), fontId))"))
    }

    private fun sfnt(names: Map<Int, String>, otf: Boolean = false): ByteArray {
        val strings = ByteArrayOutputStream()
        val records = ByteArrayOutputStream()
        DataOutputStream(records).use { output ->
            names.forEach { (id, text) ->
                val bytes = text.toByteArray(Charsets.UTF_16BE)
                output.writeShort(3)
                output.writeShort(1)
                output.writeShort(0x0409)
                output.writeShort(id)
                output.writeShort(bytes.size)
                output.writeShort(strings.size())
                strings.write(bytes)
            }
        }
        val tableLength = 6 + records.size() + strings.size()
        val result = ByteArrayOutputStream()
        DataOutputStream(result).use {
            it.writeInt(if (otf) 0x4f54544f else 0x00010000)
            it.writeShort(1)
            it.writeShort(0); it.writeShort(0); it.writeShort(0)
            it.writeInt(0x6e616d65)
            it.writeInt(0)
            it.writeInt(28)
            it.writeInt(tableLength)
            it.writeShort(0)
            it.writeShort(names.size)
            it.writeShort(6 + records.size())
            it.write(records.toByteArray())
            it.write(strings.toByteArray())
        }
        return result.toByteArray()
    }
}
