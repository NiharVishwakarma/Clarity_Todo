package com.example.darktodo.monitor

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object MonitorReports {
    suspend fun export(context: Context, uri: Uri, format: String, snapshot: MonitorSnapshot): Unit = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Destination cannot be opened")
        stream.use { output ->
            if (format == "pdf") {
                val pdf = PdfDocument()
                try {
                    val paint = Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK }
                    val lines = MonitorFormats.report(snapshot, false).lines().flatMap { line ->
                        // Native PDF has no layout engine: wrap by measured width, not character count.
                        buildList {
                            var remaining = line
                            if (remaining.isEmpty()) add("")
                            while (remaining.isNotEmpty()) {
                                val count = paint.breakText(remaining, true, 510f, null).coerceAtLeast(1)
                                add(remaining.take(count))
                                remaining = remaining.drop(count)
                            }
                        }
                    }
                    lines.chunked(47).forEachIndexed { index, pageLines ->
                        currentCoroutineContext().ensureActive()
                        val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                        pageLines.forEachIndexed { row, line -> page.canvas.drawText(line, 42f, 50f + row * 15f, paint) }
                        page.canvas.drawText("Clarity Info · page ${index + 1}", 42f, 810f, paint)
                        pdf.finishPage(page)
                    }
                    pdf.writeTo(output)
                } finally {
                    pdf.close()
                }
            } else {
                output.write(MonitorFormats.report(snapshot, format == "csv").toByteArray(Charsets.UTF_8))
            }
        }
    }
}
