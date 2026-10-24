package com.chefdesobras.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Convierte el texto de una receta en un PDF A4 con varias páginas si hace falta. */
object RecipePdf {

    private const val PAGE_WIDTH = 595 // A4 en puntos
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48
    private const val BRAND_GREEN = 0xFF1F5135.toInt()

    private val sectionLabels = listOf("Nombre:", "Tiempo:", "Raciones:", "Ingredientes:", "Pasos:", "Consejo:")

    /** El nombre de la receta («Nombre: …») o null si el modelo no lo puso. */
    fun recipeTitle(text: String): String? =
        text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Nombre:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun suggestedFileName(text: String): String {
        val base = recipeTitle(text)
            ?.lowercase(Locale("es"))
            ?.replace(Regex("[^a-z0-9áéíóúñü]+"), "-")
            ?.trim('-')
            ?.take(40)
            ?.takeIf { it.isNotEmpty() }
            ?: "receta"
        return "mi-chefcito-$base.pdf"
    }

    fun write(recipeText: String, out: OutputStream) {
        val contentWidth = PAGE_WIDTH - 2 * MARGIN

        val headerPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = BRAND_GREEN
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        val datePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10f
        }
        val bodyPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
        }
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es")))

        val text = styled(recipeText)
        val body = StaticLayout.Builder
            .obtain(text, 0, text.length, bodyPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1f)
            .build()

        val document = PdfDocument()
        try {
            var line = 0
            var pageNumber = 1
            while (line < body.lineCount) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                val canvas = page.canvas

                var top = MARGIN.toFloat()
                if (pageNumber == 1) {
                    canvas.drawText("Mi Chefcito", MARGIN.toFloat(), top + 18f, headerPaint)
                    canvas.drawText(date, MARGIN.toFloat(), top + 36f, datePaint)
                    canvas.drawRect(MARGIN.toFloat(), top + 46f, (PAGE_WIDTH - MARGIN).toFloat(), top + 48f, headerPaint)
                    top += 68f
                }
                val available = PAGE_HEIGHT - MARGIN - top

                // Cuántas líneas caben en esta página.
                val firstLineTop = body.getLineTop(line)
                var end = line
                while (end < body.lineCount && body.getLineBottom(end) - firstLineTop <= available) end++
                if (end == line) end = line + 1

                canvas.save()
                canvas.translate(MARGIN.toFloat(), top - firstLineTop)
                canvas.clipRect(0, firstLineTop, contentWidth, body.getLineBottom(end - 1))
                body.draw(canvas)
                canvas.restore()

                canvas.drawText("$pageNumber", (PAGE_WIDTH / 2).toFloat(), (PAGE_HEIGHT - 24).toFloat(), datePaint)
                document.finishPage(page)
                line = end
                pageNumber++
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    /** Crea el PDF en la caché y devuelve una Uri que otras apps pueden leer (para compartir). */
    fun createShareableFile(context: Context, recipeText: String): Uri {
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, suggestedFileName(recipeText))
        file.outputStream().use { write(recipeText, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Pone en negrita y en verde las etiquetas de sección (Nombre:, Pasos:, …). */
    private fun styled(text: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder(text)
        var offset = 0
        text.split('\n').forEach { rawLine ->
            val trimmed = rawLine.trimStart()
            val label = sectionLabels.firstOrNull { trimmed.startsWith(it, ignoreCase = true) }
            if (label != null) {
                val start = offset + (rawLine.length - trimmed.length)
                val end = start + label.length
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ForegroundColorSpan(BRAND_GREEN), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (label == "Nombre:") {
                    builder.setSpan(StyleSpan(Typeface.BOLD), end, offset + rawLine.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            offset += rawLine.length + 1
        }
        return builder
    }
}
