package com.example.darktodo.data

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.security.MessageDigest

data class ImportedWidgetFont(
    val id: String,
    val name: String,
    val assetIds: Set<String> = setOf(id)
) {
    fun containsId(candidate: String?): Boolean = candidate in assetIds
}

/** Content identity is independent of the document provider's filename and URI. */
internal class ImportedFontCatalog(private val onReadError: (File, IOException) -> Unit) {
    private data class Stamp(val size: Long, val modified: Long)
    private data class Asset(val stamp: Stamp, val hash: String, val label: String?)
    private val assets = mutableMapOf<String, Asset>()

    fun invalidate() { assets.clear() }

    fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun list(root: File, entries: List<ImportedWidgetFont>): List<ImportedWidgetFont> {
        val fonts = linkedMapOf<String, ImportedWidgetFont>()
        for (entry in entries) {
            val file = File(root, entry.id)
            if (!file.isFile) continue
            val path = file.canonicalPath
            val stamp = Stamp(file.length(), file.lastModified())
            val asset = try {
                assets[path]?.takeIf { it.stamp == stamp } ?: run {
                    val hash = hash(file)
                    val label = try {
                        SfntFontName.read(file)
                    } catch (error: IOException) {
                        assets.remove(path)
                        onReadError(file, error)
                        null
                    }
                    Asset(stamp, hash, label).also { assets[path] = it }
                }
            } catch (error: IOException) {
                onReadError(file, error)
                null
            }
            // An unreadable file remains selectable; never guess its identity from its name.
            val identity = asset?.hash ?: path
            val existing = fonts[identity]
            fonts[identity] = if (existing == null) {
                entry.copy(name = asset?.label ?: entry.name)
            } else {
                existing.copy(assetIds = existing.assetIds + entry.assetIds)
            }
        }
        return fonts.values.toList()
    }

    fun find(root: File, entries: List<ImportedWidgetFont>, hash: String): ImportedWidgetFont? =
        list(root, entries).firstOrNull { assets[File(root, it.id).canonicalPath]?.hash == hash }
}

/** Reads bounded OpenType name records, including the first face Android uses in a TTC. */
internal object SfntFontName {
    fun read(file: File): String? = RandomAccessFile(file, "r").use { input ->
        if (input.length() < 12) return null
        val signature = input.readInt()
        val face = if (signature == 0x74746366) {
            input.skipBytes(4)
            if (input.readInt() <= 0 || input.length() < 16) return null
            input.readInt().toLong() and 0xffffffffL
        } else 0L
        if (face > input.length() - 12) return null
        input.seek(face)
        if (input.readInt() !in setOf(0x00010000, 0x4f54544f, 0x74727565)) return null
        val tables = input.readUnsignedShort()
        if (face + 12 + tables * 16L > input.length()) return null
        input.skipBytes(6)
        repeat(tables) {
            val tag = input.readInt()
            input.skipBytes(4)
            val offset = input.readInt().toLong() and 0xffffffffL
            val length = input.readInt().toLong() and 0xffffffffL
            if (tag == 0x6e616d65) {
                if (length < 6 || offset + length > input.length()) return null
                return readNames(input, offset, length)
            }
        }
        null
    }

    private fun readNames(input: RandomAccessFile, offset: Long, length: Long): String? {
        input.seek(offset)
        if (input.readUnsignedShort() !in 0..1) return null
        val count = input.readUnsignedShort()
        val strings = input.readUnsignedShort()
        if (strings < 6 + count * 12L || strings > length) return null
        data class Name(val text: String, val rank: Int)
        val names = mutableMapOf<Int, Name>()
        repeat(count) { index ->
            input.seek(offset + 6 + index * 12L)
            val platform = input.readUnsignedShort()
            val encoding = input.readUnsignedShort()
            val language = input.readUnsignedShort()
            val id = input.readUnsignedShort()
            val size = input.readUnsignedShort()
            val start = input.readUnsignedShort()
            val charset = when {
                platform == 0 || (platform == 3 && encoding in setOf(0, 1, 10)) -> Charsets.UTF_16BE
                platform == 1 && encoding == 0 && Charset.isSupported("x-MacRoman") -> charset("x-MacRoman")
                else -> return@repeat
            }
            if (id !in setOf(1, 2, 4, 16, 17) || size == 0 || size > 4096 ||
                strings + start + size.toLong() > length) return@repeat
            input.seek(offset + strings + start)
            val bytes = ByteArray(size)
            input.readFully(bytes)
            val text = String(bytes, charset).trim().takeIf {
                it.isNotEmpty() && it.length <= 200 && it.none { char -> char.isISOControl() || char == '\uFFFD' }
            } ?: return@repeat
            val rank = (if (language == 0x0409 || language == 0) 10 else 0) +
                (if (platform == 3) 2 else if (platform == 0) 1 else 0)
            if (rank > (names[id]?.rank ?: -1)) names[id] = Name(text, rank)
        }
        val family = names[16]?.text ?: names[1]?.text
        val style = names[17]?.text ?: names[2]?.text
        return names[4]?.text ?: family?.let {
            if (style == null || style.equals("Regular", true) || it.endsWith(style, true)) it else "$it $style"
        }
    }
}
