package com.chefdesobras.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Dibuja la receta en un PDF A4 con el mismo estilo cálido de la app. */
object RecipePdf {

    @Volatile
    private var fontsCache: PdfFonts? = null

    fun suggestedFileName(recipe: Recipe): String {
        val slug = RecipeParser.normalize(recipe.title)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .trim('-')
            .ifEmpty { "receta" }
        return "mi-chefcito-$slug.pdf"
    }

    /** Crea el PDF en la caché y devuelve una Uri que otras apps pueden leer (para compartir). */
    fun createShareableFile(context: Context, recipe: Recipe, portions: Int): Uri {
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, suggestedFileName(recipe))
        file.outputStream().use { write(context, recipe, portions, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun write(context: Context, recipe: Recipe, portions: Int, out: OutputStream) {
        val fonts = fontsCache ?: loadFonts(context).also { fontsCache = it }
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es")))
        val document = PdfDocument()
        try {
            val writer = PdfWriter(document, fonts, recipe.title, "Hecho con Mi Chefcito · $date")
            writer.newPage()
            drawHeader(writer, recipe)
            drawStats(writer, recipe, portions)
            drawTip(writer, recipe)
            drawIngredients(writer, recipe, portions)
            drawSteps(writer, recipe)
            drawClosing(writer)
            writer.finishPage()
            document.writeTo(out)
        } finally {
            document.close()
        }
    }
}

// ---------- Estilo ----------

private const val TAG = "RecipePdf"
private const val PAGE_WIDTH = 595 // A4 en puntos
private const val PAGE_HEIGHT = 842
private const val MARGIN = 44f
private const val FOOTER_HEIGHT = 34f

/** Los colores de ChefColors, en el formato de android.graphics. */
private object PdfColors {
    const val BG = 0xFFFFFDF8.toInt()
    const val TEXT = 0xFF2D3748.toInt()
    const val MUTED = 0xFF5A6577.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val CARD_BORDER = 0xFFF3EDE2.toInt()
    const val CHECK_BORDER = 0xFFD9CFBF.toInt()
    const val ORANGE = 0xFFFF6B4A.toInt()
    const val ORANGE_DEEP = 0xFFD9481F.toInt()
    const val ORANGE_TEXT = 0xFFC2410C.toInt()
    const val ORANGE_INK = 0xFFB8401A.toInt()
    const val ORANGE_SOFT = 0xFFFFF1EA.toInt()
    const val ORANGE_SOFT_BORDER = 0xFFFFD9C7.toInt()
    const val PEACH = 0xFFFFE6D6.toInt()
    const val BUBBLE = 0xFFFFF8F0.toInt()
    const val BUBBLE_BORDER = 0xFFF6DCC8.toInt()
    const val GREEN = 0xFF2F855A.toInt()
    const val GREEN_SOFT = 0xFFEAF6EF.toInt()
}

/** Trazos de Lucide (los mismos de ChefIcons) para dibujarlos con android.graphics. */
private object PdfIcons {
    val chefHat by lazy {
        icon(
            "M 17 21 a 1 1 0 0 0 1 -1 v -5.35 c 0 -0.457 0.316 -0.844 0.727 -1.041 a 4 4 0 0 0 -2.134 -7.589 a 5 5 0 0 0 -9.186 0 a 4 4 0 0 0 -2.134 7.588 c 0.411 0.198 0.727 0.585 0.727 1.041 V 20 a 1 1 0 0 0 1 1 Z",
            "M 6 17 h 12",
        )
    }
    val clock by lazy {
        icon("M 2 12 A 10 10 0 1 0 22 12 A 10 10 0 1 0 2 12 Z", "M 12 6 L 12 12 L 16 14")
    }
    val flame by lazy {
        icon(
            "M 8.5 14.5 A 2.5 2.5 0 0 0 11 12 c 0 -1.38 -0.5 -2 -1 -3 c -1.072 -2.143 -0.224 -4.054 2 -6 c 0.5 2.5 2 4.9 4 6.5 c 2 1.6 3 3.5 3 5.5 a 7 7 0 1 1 -14 0 c 0 -1.153 0.433 -2.294 1 -3 a 2.5 2.5 0 0 0 2.5 2.5 z",
        )
    }
    val users by lazy {
        icon(
            "M 16 21 v -2 a 4 4 0 0 0 -4 -4 H 6 a 4 4 0 0 0 -4 4 v 2",
            "M 5 7 A 4 4 0 1 0 13 7 A 4 4 0 1 0 5 7 Z",
            "M 22 21 v -2 a 4 4 0 0 0 -3 -3.87",
            "M 16 3.13 a 4 4 0 0 1 0 7.75",
        )
    }
    val leaf by lazy {
        icon(
            "M 11 20 A 7 7 0 0 1 9.8 6.1 C 15.5 5 17 4.48 19 2 c 1 2 2 4.18 2 8 c 0 5.5 -4.78 10 -10 10 Z",
            "M 2 21 c 0 -3 1.85 -5.36 5.08 -6 C 9.5 14.52 12 13 13 12",
        )
    }
    val lightbulb by lazy {
        icon(
            "M 15 14 c 0.2 -1 0.7 -1.7 1.5 -2.5 c 1 -0.9 1.5 -2.2 1.5 -3.5 A 6 6 0 0 0 6 8 c 0 1 0.2 2.2 1.5 3.5 c 0.7 0.7 1.3 1.5 1.5 2.5",
            "M 9 18 h 6",
            "M 10 22 h 4",
        )
    }
    val basket by lazy {
        icon(
            "m 15 11 l -1 9",
            "m 19 11 l -4 -7",
            "M 2 11 h 20",
            "m 3.5 11 l 1.6 7.4 a 2 2 0 0 0 2 1.6 h 9.8 a 2 2 0 0 0 2 -1.6 l 1.7 -7.4",
            "M 4.5 15.5 h 15",
            "m 5 11 l 4 -7",
            "m 9 11 l 1 9",
        )
    }
    val cookingPot by lazy {
        icon(
            "M 2 12 h 20",
            "M 20 12 v 8 a 2 2 0 0 1 -2 2 H 6 a 2 2 0 0 1 -2 -2 v -8",
            "m 4 8 l 16 -4",
            "m 8.86 6.78 l -0.45 -1.81 a 2 2 0 0 1 1.45 -2.43 l 1.94 -0.48 a 2 2 0 0 1 2.43 1.46 l 0.45 1.8",
        )
    }
    val timer by lazy {
        icon("M 10 2 L 14 2", "M 12 14 L 15 11", "M 4 14 A 8 8 0 1 0 20 14 A 8 8 0 1 0 4 14 Z")
    }
    val sparkle by lazy {
        icon(
            "M 9.937 15.5 A 2 2 0 0 0 8.5 14.063 l -6.135 -1.582 a 0.5 0.5 0 0 1 0 -0.962 L 8.5 9.936 A 2 2 0 0 0 9.937 8.5 l 1.582 -6.135 a 0.5 0.5 0 0 1 0.963 0 L 14.063 8.5 A 2 2 0 0 0 15.5 9.937 l 6.135 1.581 a 0.5 0.5 0 0 1 0 0.964 L 15.5 14.063 a 2 2 0 0 0 -1.437 1.437 l -1.582 6.135 a 0.5 0.5 0 0 1 -0.963 0 z",
        )
    }

    private fun icon(vararg data: String): Path = Path().apply {
        data.forEach { addPath(PathParser().parsePathString(it).toPath().asAndroidPath()) }
    }
}

private class PdfFonts(val semi: Typeface, val bold: Typeface, val extra: Typeface, val black: Typeface)

private fun loadFonts(context: Context): PdfFonts {
    val file = runCatching { nunitoFile(context) }
        .onFailure { Log.w(TAG, "No se pudo preparar Nunito para el PDF", it) }
        .getOrNull()

    fun weight(value: Int, fallback: Typeface): Typeface =
        file?.let { runCatching { Typeface.Builder(it).setFontVariationSettings("'wght' $value").build() }.getOrNull() }
            ?: fallback

    return PdfFonts(
        semi = weight(600, Typeface.DEFAULT),
        bold = weight(700, Typeface.DEFAULT_BOLD),
        extra = weight(800, Typeface.DEFAULT_BOLD),
        black = weight(900, Typeface.DEFAULT_BOLD),
    )
}

/** Typeface.Builder necesita un archivo: la fuente variable de res/font se copia una vez a la caché. */
private fun nunitoFile(context: Context): File {
    val dir = File(context.cacheDir, "fonts").apply { mkdirs() }
    val file = File(dir, "nunito.ttf")
    if (file.length() > 0L) return file
    val tmp = File.createTempFile("nunito", ".tmp", dir)
    context.resources.openRawResource(R.font.nunito).use { input -> tmp.outputStream().use { input.copyTo(it) } }
    if (!tmp.renameTo(file)) tmp.delete()
    return file
}

private fun textPaint(typeface: Typeface, size: Float, color: Int, letterSpacing: Float = 0f) =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        this.color = color
        this.letterSpacing = letterSpacing
    }

private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.FILL
}

private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = width
}

private fun textLayout(
    text: CharSequence,
    paint: TextPaint,
    width: Float,
    spacing: Float,
    alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
        .setAlignment(alignment)
        .setLineSpacing(0f, spacing)
        .setIncludePad(false)
        .build()

private fun Canvas.drawTextLayout(layout: StaticLayout, x: Float, y: Float) {
    save()
    translate(x, y)
    layout.draw(this)
    restore()
}

/** Dibuja un icono de 24×24 escalado a [size] puntos, con el trazo redondeado de Lucide. */
private fun Canvas.drawIcon(icon: Path, x: Float, y: Float, size: Float, color: Int) {
    save()
    translate(x, y)
    scale(size / 24f, size / 24f)
    drawPath(icon, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    })
    restore()
}

private fun Canvas.drawEllipsized(text: String, paint: TextPaint, x: Float, baseline: Float, width: Float) {
    val shown = TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END)
    drawText(shown, 0, shown.length, x, baseline, paint)
}

// ---------- Páginas ----------

private class PdfWriter(
    private val document: PdfDocument,
    val fonts: PdfFonts,
    private val title: String,
    private val footer: String,
) {
    private var page: PdfDocument.Page? = null
    private var number = 0
    lateinit var canvas: Canvas
        private set
    var y = 0f

    val left = MARGIN
    val right = PAGE_WIDTH - MARGIN
    val width = right - left
    private val limit = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT

    fun newPage() {
        finishPage()
        number++
        val started = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create())
        page = started
        canvas = started.canvas
        canvas.drawColor(PdfColors.BG)
        y = MARGIN
        if (number > 1) {
            // En las páginas siguientes basta con una cabecera discreta.
            canvas.drawEllipsized("Mi Chefcito · $title", textPaint(fonts.extra, 9f, PdfColors.MUTED), left, y + 9f, width)
            canvas.drawRect(left, y + 18f, right, y + 19f, fillPaint(PdfColors.CARD_BORDER))
            y += 34f
        }
    }

    fun finishPage() {
        val current = page ?: return
        val paint = textPaint(fonts.bold, 8.5f, PdfColors.MUTED)
        val baseline = PAGE_HEIGHT - MARGIN + 4f
        canvas.drawRect(left, baseline - 18f, right, baseline - 17f, fillPaint(PdfColors.CARD_BORDER))
        canvas.drawText(footer, left, baseline, paint)
        val pageText = "Página $number"
        canvas.drawText(pageText, right - paint.measureText(pageText), baseline, paint)
        document.finishPage(current)
        page = null
    }

    /** Salta de página si lo que sigue no cabe (salvo que ya estemos arriba del todo). */
    fun ensureSpace(height: Float) {
        if (y + height > limit && y > MARGIN + 40f) newPage()
    }
}

// ---------- Secciones ----------

private fun drawHeader(w: PdfWriter, recipe: Recipe) {
    val fonts = w.fonts
    val pad = 20f
    val badge = 46f
    val innerWidth = w.width - pad * 2 - badge - 12f
    val title = textLayout(recipe.title, textPaint(fonts.black, 24f, PdfColors.TEXT, -0.01f), innerWidth, 1.08f)
    val description = recipe.description.takeIf { it.isNotBlank() }
        ?.let { textLayout(it, textPaint(fonts.semi, 11f, PdfColors.MUTED), innerWidth, 1.35f) }
    val height = pad + 11f + 9f + title.height + (description?.let { 8f + it.height } ?: 0f) + pad
    val top = w.y
    val rect = RectF(w.left, top, w.right, top + height)
    val canvas = w.canvas

    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(rect, 22f, 22f, Path.Direction.CW) })
    canvas.drawRect(rect, fillPaint(PdfColors.ORANGE_SOFT))
    // El círculo melocotón de la ilustración de la app, asomando por la esquina.
    canvas.drawCircle(w.right - 30f, top + 30f, 70f, fillPaint(PdfColors.PEACH))
    canvas.restore()
    canvas.drawRoundRect(rect, 22f, 22f, strokePaint(PdfColors.ORANGE_SOFT_BORDER, 1f))

    val cx = w.right - pad - badge / 2
    val cy = top + pad + badge / 2
    canvas.drawCircle(cx, cy, badge / 2, fillPaint(PdfColors.WHITE))
    canvas.drawIcon(PdfIcons.chefHat, cx - 13f, cy - 14f, 26f, PdfColors.ORANGE)

    var y = top + pad
    canvas.drawText("MI CHEFCITO · RECETA", w.left + pad, y + 9f, textPaint(fonts.black, 8.5f, PdfColors.ORANGE_INK, 0.12f))
    y += 11f + 9f
    canvas.drawTextLayout(title, w.left + pad, y)
    y += title.height
    if (description != null) {
        y += 8f
        canvas.drawTextLayout(description, w.left + pad, y)
    }
    w.y = top + height + 14f
}

private class Stat(val icon: Path, val label: String, val value: String, val tile: Int, val tint: Int)

private fun drawStats(w: PdfWriter, recipe: Recipe, portions: Int) {
    val fonts = w.fonts
    val stats = buildList {
        add(Stat(PdfIcons.clock, "TIEMPO", recipe.totalMinutes?.let { minutesLabel(it) } ?: "Al gusto", PdfColors.ORANGE_SOFT, PdfColors.ORANGE_TEXT))
        add(Stat(PdfIcons.flame, "DIFICULTAD", recipe.difficulty, PdfColors.GREEN_SOFT, PdfColors.GREEN))
        add(Stat(PdfIcons.users, "PORCIONES", "$portions", PdfColors.GREEN_SOFT, PdfColors.GREEN))
        recipe.kcalPerPortion?.let { add(Stat(PdfIcons.leaf, "POR PORCIÓN", "$it kcal", PdfColors.ORANGE_SOFT, PdfColors.ORANGE_TEXT)) }
    }
    val gap = 10f
    val boxWidth = (w.width - gap * (stats.size - 1)) / stats.size
    val height = 58f
    w.ensureSpace(height)
    val top = w.y
    val labelPaint = textPaint(fonts.extra, 7.5f, PdfColors.MUTED, 0.08f)
    val valuePaint = textPaint(fonts.black, 13f, PdfColors.TEXT)
    stats.forEachIndexed { index, stat ->
        val x = w.left + index * (boxWidth + gap)
        val rect = RectF(x, top, x + boxWidth, top + height)
        w.canvas.drawRoundRect(rect, 16f, 16f, fillPaint(PdfColors.WHITE))
        w.canvas.drawRoundRect(rect, 16f, 16f, strokePaint(PdfColors.CARD_BORDER, 1f))
        val tile = RectF(x + 10f, top + 10f, x + 34f, top + 34f)
        w.canvas.drawRoundRect(tile, 8f, 8f, fillPaint(stat.tile))
        w.canvas.drawIcon(stat.icon, tile.left + 5f, tile.top + 5f, 14f, stat.tint)
        val labelX = tile.right + 8f
        w.canvas.drawEllipsized(stat.label, labelPaint, labelX, top + 25f, rect.right - 8f - labelX)
        w.canvas.drawEllipsized(stat.value, valuePaint, x + 10f, top + 50f, boxWidth - 20f)
    }
    w.y = top + height + 14f
}

private fun drawTip(w: PdfWriter, recipe: Recipe) {
    val tip = recipe.tip.trim()
    if (tip.isEmpty()) return
    val fonts = w.fonts
    val pad = 14f
    val bubble = 30f
    val textX = w.left + pad + bubble + 10f
    val body = textLayout(tip, textPaint(fonts.semi, 10.5f, PdfColors.TEXT), w.right - pad - textX, 1.35f)
    val height = pad + 10f + 5f + body.height + pad
    w.ensureSpace(height)
    val top = w.y
    val rect = RectF(w.left, top, w.right, top + height)
    w.canvas.drawRoundRect(rect, 18f, 18f, fillPaint(PdfColors.BUBBLE))
    w.canvas.drawRoundRect(rect, 18f, 18f, strokePaint(PdfColors.BUBBLE_BORDER, 1f))
    val cx = w.left + pad + bubble / 2
    val cy = top + pad + bubble / 2
    w.canvas.drawCircle(cx, cy, bubble / 2, fillPaint(PdfColors.PEACH))
    w.canvas.drawIcon(PdfIcons.lightbulb, cx - 8f, cy - 8f, 16f, PdfColors.ORANGE_TEXT)
    w.canvas.drawText("TIP PRO DE CHEFCITO", textX, top + pad + 8f, textPaint(fonts.black, 8f, PdfColors.ORANGE_INK, 0.12f))
    w.canvas.drawTextLayout(body, textX, top + pad + 15f)
    w.y = top + height + 22f
}

/** Título de sección; [keepWith] reserva sitio para que no quede solo al final de una página. */
private fun drawSectionTitle(w: PdfWriter, icon: Path, title: String, detail: String?, keepWith: Float) {
    val fonts = w.fonts
    val height = 28f
    w.ensureSpace(height + keepWith)
    val top = w.y
    val tile = RectF(w.left, top, w.left + 26f, top + 26f)
    w.canvas.drawRoundRect(tile, 8f, 8f, fillPaint(PdfColors.ORANGE_SOFT))
    w.canvas.drawIcon(icon, tile.left + 5f, tile.top + 5f, 16f, PdfColors.ORANGE_TEXT)
    w.canvas.drawText(title, tile.right + 10f, top + 18.5f, textPaint(fonts.black, 15f, PdfColors.TEXT))
    if (detail != null) {
        val paint = textPaint(fonts.bold, 10f, PdfColors.MUTED)
        w.canvas.drawText(detail, w.right - paint.measureText(detail), top + 18f, paint)
    }
    w.y = top + height + 6f
}

private fun drawIngredients(w: PdfWriter, recipe: Recipe, portions: Int) {
    if (recipe.ingredients.isEmpty()) return
    val fonts = w.fonts
    drawSectionTitle(w, PdfIcons.basket, "Ingredientes", "Para $portions ${portionsLabel(portions)}", keepWith = 30f)
    val namePaint = textPaint(fonts.bold, 11f, PdfColors.TEXT)
    val quantityPaint = textPaint(fonts.extra, 11f, PdfColors.ORANGE_TEXT)
    val nameX = w.left + 22f
    recipe.ingredients.forEachIndexed { index, ingredient ->
        val quantity = ingredient.quantityFor(portions, recipe.basePortions).trim()
        val quantityWidth = min(quantityPaint.measureText(quantity), w.width * 0.42f)
        val quantityLayout = textLayout(quantity, quantityPaint, quantityWidth + 1f, 1.2f, Layout.Alignment.ALIGN_OPPOSITE)
        val name = textLayout(ingredient.name, namePaint, w.right - nameX - quantityWidth - 14f, 1.2f)
        val rowHeight = max(name.height, quantityLayout.height) + 14f
        w.ensureSpace(rowHeight)
        val top = w.y
        // Una casilla vacía para ir tachando al comprar o cocinar.
        val centerY = top + 7f + name.getLineBaseline(0) - namePaint.textSize * 0.35f
        w.canvas.drawRoundRect(
            RectF(w.left + 1f, centerY - 6f, w.left + 13f, centerY + 6f),
            3.5f,
            3.5f,
            strokePaint(PdfColors.CHECK_BORDER, 1.3f),
        )
        w.canvas.drawTextLayout(name, nameX, top + 7f)
        w.canvas.drawTextLayout(quantityLayout, w.right - quantityWidth - 1f, top + 7f)
        if (index < recipe.ingredients.lastIndex) {
            w.canvas.drawRect(nameX, top + rowHeight - 0.5f, w.right, top + rowHeight + 0.5f, fillPaint(PdfColors.CARD_BORDER))
        }
        w.y = top + rowHeight
    }
    w.y += 22f
}

private fun drawSteps(w: PdfWriter, recipe: Recipe) {
    if (recipe.steps.isEmpty()) return
    val fonts = w.fonts
    val count = if (recipe.steps.size == 1) "1 paso" else "${recipe.steps.size} pasos"
    drawSectionTitle(w, PdfIcons.cookingPot, "Preparación", count, keepWith = 60f)
    val circle = 24f
    val textX = w.left + circle + 12f
    val textWidth = w.right - textX
    val titlePaint = textPaint(fonts.black, 12f, PdfColors.TEXT)
    val bodyPaint = textPaint(fonts.semi, 10.5f, PdfColors.MUTED)
    val numberPaint = textPaint(fonts.black, 11f, PdfColors.WHITE).apply { textAlign = Paint.Align.CENTER }
    val timerPaint = textPaint(fonts.extra, 9f, PdfColors.ORANGE_INK)
    val titleOffset = ((circle - (titlePaint.descent() - titlePaint.ascent())) / 2).coerceAtLeast(0f)
    recipe.steps.forEachIndexed { index, step ->
        val title = textLayout(step.title, titlePaint, textWidth, 1.2f)
        val body = step.body.takeIf { it.isNotBlank() }?.let { textLayout(it, bodyPaint, textWidth, 1.4f) }
        val timerText = if (step.timerSeconds > 0) "Temporizador: ${timerLabel(step.timerSeconds)}" else null
        val contentHeight = titleOffset + title.height + (body?.let { 4f + it.height } ?: 0f) + (if (timerText != null) 28f else 0f)
        val height = max(circle, contentHeight) + 18f
        w.ensureSpace(height)
        val top = w.y
        val cx = w.left + circle / 2
        val cy = top + circle / 2
        if (index < recipe.steps.lastIndex) {
            // La línea que une los pasos, como una pequeña ruta.
            w.canvas.drawRect(cx - 1f, top + circle + 6f, cx + 1f, top + height - 4f, fillPaint(PdfColors.PEACH))
        }
        w.canvas.drawCircle(cx, cy + 1.5f, circle / 2, fillPaint(PdfColors.ORANGE_DEEP))
        w.canvas.drawCircle(cx, cy, circle / 2, fillPaint(PdfColors.ORANGE))
        w.canvas.drawText("${index + 1}", cx, cy - (numberPaint.descent() + numberPaint.ascent()) / 2, numberPaint)

        var y = top + titleOffset
        w.canvas.drawTextLayout(title, textX, y)
        y += title.height
        if (body != null) {
            y += 4f
            w.canvas.drawTextLayout(body, textX, y)
            y += body.height
        }
        if (timerText != null) {
            y += 8f
            val pill = RectF(textX, y, textX + 9f + 12f + 6f + timerPaint.measureText(timerText) + 10f, y + 20f)
            w.canvas.drawRoundRect(pill, 10f, 10f, fillPaint(PdfColors.ORANGE_SOFT))
            w.canvas.drawIcon(PdfIcons.timer, pill.left + 9f, pill.top + 4f, 12f, PdfColors.ORANGE_TEXT)
            w.canvas.drawText(timerText, pill.left + 27f, pill.top + 13.5f, timerPaint)
        }
        w.y = top + height
    }
}

private fun drawClosing(w: PdfWriter) {
    w.ensureSpace(44f)
    val paint = textPaint(w.fonts.black, 15f, PdfColors.ORANGE_TEXT)
    val text = "¡Buen provecho!"
    val textWidth = paint.measureText(text)
    val total = 16f + 8f + textWidth
    val x = w.left + (w.width - total) / 2
    val baseline = w.y + 26f
    w.canvas.drawIcon(PdfIcons.sparkle, x, baseline - 13f, 16f, PdfColors.ORANGE)
    w.canvas.drawText(text, x + 24f, baseline, paint)
    w.y = baseline + 16f
}
