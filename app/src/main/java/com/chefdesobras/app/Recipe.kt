package com.chefdesobras.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

data class RecipeIngredient(
    val name: String,
    /** La cantidad tal como la escribió el modelo («200 g», «al gusto»). */
    val quantity: String,
    /** La cantidad para las porciones base, si se pudo leer como número. */
    val amount: Double? = null,
    /** Extremo superior de un rango, como en «2-3 dientes». */
    val amountMax: Double? = null,
    /** Lo que va detrás del número: «g», «dientes», «taza»… */
    val unit: String = "",
) {
    /** La cantidad recalculada para [portions] porciones. */
    fun quantityFor(portions: Int, basePortions: Int): String {
        val base = amount ?: return quantity
        val factor = portions.toDouble() / basePortions.coerceAtLeast(1)
        val scaled = base * factor
        val scaledMax = amountMax?.times(factor)
        val number = formatAmount(scaled, unit) + (scaledMax?.let { "-" + formatAmount(it, unit) } ?: "")
        val unitText = adjustUnit(unit, scaledMax ?: scaled)
        return if (unitText.isEmpty()) number else "$number $unitText"
    }
}

data class RecipeStep(
    val title: String,
    val body: String,
    /** Segundos del temporizador del paso; 0 si el paso no tiene espera. */
    val timerSeconds: Int = 0,
)

data class Recipe(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val totalMinutes: Int?,
    val difficulty: String,
    val basePortions: Int,
    val kcalPerPortion: Int?,
    val tip: String,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
    val rawText: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("description", description)
        .put("totalMinutes", totalMinutes ?: JSONObject.NULL)
        .put("difficulty", difficulty)
        .put("basePortions", basePortions)
        .put("kcalPerPortion", kcalPerPortion ?: JSONObject.NULL)
        .put("tip", tip)
        .put("rawText", rawText)
        .put("createdAt", createdAt)
        .put("ingredients", JSONArray().apply {
            ingredients.forEach { ing ->
                put(
                    JSONObject()
                        .put("name", ing.name)
                        .put("quantity", ing.quantity)
                        .put("amount", ing.amount ?: JSONObject.NULL)
                        .put("amountMax", ing.amountMax ?: JSONObject.NULL)
                        .put("unit", ing.unit)
                )
            }
        })
        .put("steps", JSONArray().apply {
            steps.forEach { step ->
                put(
                    JSONObject()
                        .put("title", step.title)
                        .put("body", step.body)
                        .put("timerSeconds", step.timerSeconds)
                )
            }
        })

    companion object {
        fun fromJson(obj: JSONObject): Recipe {
            val ingredients = obj.optJSONArray("ingredients") ?: JSONArray()
            val steps = obj.optJSONArray("steps") ?: JSONArray()
            return Recipe(
                id = obj.optString("id").ifEmpty { UUID.randomUUID().toString() },
                title = obj.optString("title").ifEmpty { RecipeParser.DEFAULT_TITLE },
                description = obj.optString("description"),
                totalMinutes = if (obj.isNull("totalMinutes")) null else obj.optInt("totalMinutes"),
                difficulty = obj.optString("difficulty").ifEmpty { "Fácil" },
                basePortions = obj.optInt("basePortions", 2).coerceIn(1, RecipeParser.MAX_PORTIONS),
                kcalPerPortion = if (obj.isNull("kcalPerPortion")) null else obj.optInt("kcalPerPortion"),
                tip = obj.optString("tip"),
                rawText = obj.optString("rawText"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                ingredients = (0 until ingredients.length()).map { i ->
                    val ing = ingredients.getJSONObject(i)
                    RecipeIngredient(
                        name = ing.optString("name"),
                        quantity = ing.optString("quantity"),
                        amount = if (ing.isNull("amount")) null else ing.optDouble("amount"),
                        amountMax = if (ing.isNull("amountMax")) null else ing.optDouble("amountMax"),
                        unit = ing.optString("unit"),
                    )
                },
                steps = (0 until steps.length()).map { i ->
                    val step = steps.getJSONObject(i)
                    RecipeStep(
                        title = step.optString("title"),
                        body = step.optString("body"),
                        timerSeconds = step.optInt("timerSeconds", 0),
                    )
                },
            )
        }
    }
}

/** Lo que se sabe de una receta mientras el modelo todavía la está escribiendo. */
data class RecipePreview(
    val title: String?,
    val ingredientCount: Int,
    val stepCount: Int,
    val writingSteps: Boolean,
)

/**
 * Lee la respuesta del modelo. Se pide un formato por líneas («NOMBRE: …», «- cantidad | ingrediente»,
 * «1. título | explicación | minutos»), pero los modelos pequeños no siempre lo respetan,
 * así que el lector es tolerante y rellena lo que falte.
 */
object RecipeParser {

    const val DEFAULT_TITLE = "Receta de Chefcito"
    const val MAX_PORTIONS = 12

    private enum class Field { TITLE, DESCRIPTION, TIME, DIFFICULTY, PORTIONS, KCAL, TIP, INGREDIENTS, STEPS }

    private enum class Section { META, INGREDIENTS, STEPS }

    private class StepDraft(val text: String) {
        val extra = mutableListOf<String>()
    }

    private val headerRegex = Regex("^([\\p{L} ]{3,24}?)\\s*:\\s*(.*)$")
    private val stepStartRegex = Regex("^(?:paso\\s*)?(\\d{1,2})\\s*[.):-]\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val pasoRegex = Regex("^paso\\s*\\d", RegexOption.IGNORE_CASE)
    private val bulletRegex = Regex("^[-•*·–]\\s*(.*)$")
    private val enumerationRegex = Regex("^\\d{1,2}[.)]\\s+(.*)$")

    private val sectionNames = mapOf(
        "ingredientes" to Field.INGREDIENTS,
        "ingredientes necesarios" to Field.INGREDIENTS,
        "pasos" to Field.STEPS,
        "preparacion" to Field.STEPS,
        "instrucciones" to Field.STEPS,
        "elaboracion" to Field.STEPS,
        "procedimiento" to Field.STEPS,
        "modo de preparacion" to Field.STEPS,
    )

    fun parse(raw: String, fallbackIngredients: List<String> = emptyList()): Recipe {
        var title: String? = null
        var description = ""
        var time: Int? = null
        var difficulty: String? = null
        var portions: Int? = null
        var kcal: Int? = null
        var tip = ""
        val ingredients = mutableListOf<RecipeIngredient>()
        val steps = mutableListOf<StepDraft>()
        val loose = mutableListOf<String>()
        var section = Section.META
        var lastText: Field? = null

        for (line in cleanLines(raw)) {
            val found = if (startsLikeItem(line)) null else parseHeader(line)
            if (found != null) {
                val field = found.first
                val value = found.second.trim()
                when (field) {
                    Field.TITLE -> if (title == null && value.isNotEmpty()) title = value.trimEnd('.')
                    Field.DESCRIPTION -> description = value
                    Field.TIME -> time = parseMinutes(value)
                    Field.DIFFICULTY -> difficulty = normalizeDifficulty(value)
                    Field.PORTIONS -> portions = firstInt(value)?.coerceIn(1, MAX_PORTIONS)
                    Field.KCAL -> kcal = firstInt(value)?.takeIf { it in 30..3000 }
                    Field.TIP -> tip = value
                    Field.INGREDIENTS -> {
                        section = Section.INGREDIENTS
                        if (value.isNotEmpty()) {
                            for (item in value.split(',', ';')) {
                                parseIngredient(item)?.let { ingredients.add(it) }
                            }
                        }
                    }
                    Field.STEPS -> {
                        section = Section.STEPS
                        if (value.isNotEmpty()) steps.add(StepDraft(value))
                    }
                }
                lastText = if (field == Field.DESCRIPTION || field == Field.TIP) field else null
                continue
            }
            if (section != Section.STEPS && pasoRegex.containsMatchIn(line)) {
                // «Paso 1: …» sin la sección «PASOS:» delante.
                section = Section.STEPS
            }
            if (section == Section.META) {
                if (lastText == Field.DESCRIPTION) {
                    description = "$description $line".trim()
                } else if (lastText == Field.TIP) {
                    tip = "$tip $line".trim()
                } else {
                    loose.add(line)
                }
            } else if (section == Section.INGREDIENTS) {
                val step = stepStartRegex.matchEntire(line)
                if (step != null && line.count { it == '|' } >= 2) {
                    // El modelo empezó los pasos sin escribir «PASOS:».
                    section = Section.STEPS
                    steps.add(StepDraft(step.groupValues[2]))
                } else {
                    parseIngredient(line)?.let { ingredients.add(it) }
                }
            } else {
                val numbered = stepStartRegex.matchEntire(line)
                val bullet = bulletRegex.matchEntire(line)
                if (numbered != null) {
                    steps.add(StepDraft(numbered.groupValues[2]))
                } else if (bullet != null) {
                    steps.add(StepDraft(bullet.groupValues[1]))
                } else if (line.contains('|') || steps.isEmpty()) {
                    steps.add(StepDraft(line))
                } else {
                    steps.last().extra.add(line)
                }
            }
        }

        if (steps.isEmpty()) {
            // Sin sección de pasos: cada línea suelta que parezca una instrucción es un paso.
            loose.filter { it.length > 12 }.forEach { steps.add(StepDraft(it)) }
        }
        val finalSteps = steps.mapIndexedNotNull { index, draft -> toStep(draft, index) }
        val finalIngredients: List<RecipeIngredient> = if (ingredients.isNotEmpty()) {
            ingredients.toList()
        } else {
            fallbackIngredients.map { RecipeIngredient(name = capitalize(it), quantity = "") }
        }
        val finalTitle = title?.takeIf { it.isNotBlank() }?.let { capitalize(it) } ?: DEFAULT_TITLE
        val stepMinutes = finalSteps.sumOf { it.timerSeconds } / 60
        return Recipe(
            title = finalTitle,
            description = description,
            totalMinutes = time ?: if (stepMinutes > 0) stepMinutes + 10 else null,
            difficulty = difficulty ?: "Fácil",
            basePortions = portions ?: 2,
            kcalPerPortion = kcal,
            tip = tip.ifBlank { defaultTip(finalTitle) },
            ingredients = finalIngredients,
            steps = finalSteps.ifEmpty { listOf(RecipeStep("Prepara la receta", raw.trim().take(800))) },
            rawText = raw.trim(),
        )
    }

    /** Lectura rápida de una respuesta a medio escribir, para mostrar el avance. */
    fun preview(partial: String): RecipePreview {
        var title: String? = null
        var ingredients = 0
        var steps = 0
        var section = Section.META
        val lines = cleanLines(partial)
        for (index in lines.indices) {
            val line = lines[index]
            // La última línea puede estar a medias: el nombre solo cuenta si ya hay salto de línea.
            val complete = index < lines.lastIndex || partial.endsWith("\n")
            val field = if (startsLikeItem(line)) null else parseHeader(line)?.first
            if (field == Field.TITLE) {
                if (complete) title = parseHeader(line)?.second?.trim()?.ifEmpty { null }
            } else if (field == Field.INGREDIENTS) {
                section = Section.INGREDIENTS
            } else if (field == Field.STEPS) {
                section = Section.STEPS
            } else if (field == null && section == Section.INGREDIENTS) {
                ingredients++
            } else if (field == null && section == Section.STEPS) {
                if (stepStartRegex.matches(line) || bulletRegex.matches(line)) steps++
            }
        }
        return RecipePreview(title?.let { capitalize(it) }, ingredients, steps, section == Section.STEPS)
    }

    private fun toStep(draft: StepDraft, index: Int): RecipeStep? {
        val full = (listOf(draft.text) + draft.extra).joinToString(" ").trim()
        if (full.isEmpty()) return null
        val parts = full.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        var title: String
        var body: String
        var minutesField: String? = null
        if (parts.size >= 3) {
            title = parts[0]
            body = parts.subList(1, parts.size - 1).joinToString(" ")
            if (firstInt(parts.last()) != null) {
                minutesField = parts.last()
            } else {
                body = body + " " + parts.last()
            }
        } else if (parts.size == 2 && firstInt(parts[1]) != null && parts[1].length <= 14) {
            body = parts[0]
            title = deriveTitle(body)
            minutesField = parts[1]
        } else if (parts.size == 2) {
            title = parts[0]
            body = parts[1]
        } else {
            val colon = full.indexOf(':')
            if (colon in 3..40) {
                title = full.substring(0, colon)
                val after = full.substring(colon + 1).trim()
                // «Bate los huevos: mezcla con…» sigue la frase; «Dorar: Calienta…» es título y texto.
                body = if (after.firstOrNull()?.isLowerCase() == true) full else after
            } else {
                title = deriveTitle(full)
                body = full
            }
        }
        if (body.isBlank()) {
            body = title
            title = deriveTitle(body)
        }
        val fieldSeconds = minutesField?.let { parseMinutes(it) }?.let { it * 60 } ?: 0
        val timer = if (fieldSeconds > 0) fieldSeconds else detectTimerSeconds(body)
        return RecipeStep(
            title = capitalize(title.trimEnd('.', ':')).ifEmpty { "Paso ${index + 1}" },
            body = capitalize(body),
            timerSeconds = timer.coerceIn(0, 4 * 3600),
        )
    }

    private fun cleanLines(raw: String): List<String> =
        raw.replace("\r", "")
            .lines()
            .map { line ->
                line.replace("**", "")
                    .replace("__", "")
                    .trim()
                    .trimStart('#')
                    .trim()
            }
            .filter { it.isNotEmpty() }

    private fun startsLikeItem(line: String): Boolean =
        line.first().isDigit() || bulletRegex.matches(line)

    private fun parseHeader(line: String): Pair<Field, String>? {
        val match = headerRegex.matchEntire(line)
        if (match == null) {
            // Sin «:», solo cuentan los nombres de sección («Ingredientes», «Preparación»).
            val field = sectionNames[normalize(line.trimEnd(':', '.')).trim()] ?: return null
            return field to ""
        }
        val key = normalize(match.groupValues[1]).trim()
        val value = match.groupValues[2]
        val field = sectionNames[key] ?: when {
            key == "nombre" || key.startsWith("nombre de") || key == "titulo" || key == "receta" || key == "plato" -> Field.TITLE
            key.startsWith("descripci") || key == "resumen" -> Field.DESCRIPTION
            key.startsWith("tiempo") || key.startsWith("duraci") -> Field.TIME
            key.startsWith("dificultad") || key == "nivel" -> Field.DIFFICULTY
            key.startsWith("porcion") || key.startsWith("racion") || key == "personas" || key.startsWith("rinde") -> Field.PORTIONS
            key.startsWith("calori") || key == "kcal" || key == "energia" -> Field.KCAL
            key.startsWith("tip") || key.startsWith("consejo") || key.startsWith("truco") -> Field.TIP
            key.startsWith("ingrediente") -> Field.INGREDIENTS
            key == "paso" || key == "pasos" || key.startsWith("preparaci") || key.startsWith("instruccion") -> Field.STEPS
            else -> null
        } ?: return null
        return field to value
    }

    private fun parseIngredient(line: String): RecipeIngredient? {
        var text = bulletRegex.matchEntire(line.trim())?.groupValues?.get(1) ?: line.trim()
        enumerationRegex.matchEntire(text)?.let { text = it.groupValues[1] }
        text = text.trim().trimEnd('.', ',', ';')
        if (text.isEmpty()) return null
        val parts = text.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            var quantity = parts[0]
            var name = parts.drop(1).joinToString(" ")
            if (leadingAmount(quantity) == null && leadingAmount(name) != null) {
                val swap = quantity
                quantity = name
                name = swap
            }
            return fromQuantity(quantity, name)
        }
        val amount = leadingAmount(text)
        if (amount != null) {
            val rest = amount.third
            val words = rest.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            val unitWord = words.first().trimEnd('.', ',')
            if (isUnit(unitWord) && words.size > 1) {
                val name = words.drop(1).joinToString(" ").removePrefix("de ").removePrefix("del ").trim()
                val quantity = text.removeSuffix(name).trim().removeSuffix(" de").removeSuffix(" del").trim()
                return RecipeIngredient(capitalize(name), quantity, amount.first, amount.second, unitWord)
            }
            val name = rest.removePrefix("de ").trim()
            return RecipeIngredient(capitalize(name), text.removeSuffix(rest).trim(), amount.first, amount.second, "")
        }
        val dash = Regex("^(.+?)\\s+[-–]\\s+(.+)$").matchEntire(text)
        if (dash != null && leadingAmount(dash.groupValues[2]) != null) {
            return fromQuantity(dash.groupValues[2], dash.groupValues[1])
        }
        val colon = text.indexOf(':')
        if (colon in 2 until text.length - 1) {
            return fromQuantity(text.substring(colon + 1).trim(), text.substring(0, colon).trim())
        }
        val parenthesis = Regex("^(.+?)\\s*\\((.+)\\)$").matchEntire(text)
        if (parenthesis != null) {
            return fromQuantity(parenthesis.groupValues[2], parenthesis.groupValues[1])
        }
        val toTaste = Regex("^(.+?)\\s+(al gusto|c/n|cantidad necesaria|a gusto)$", RegexOption.IGNORE_CASE).matchEntire(text)
        if (toTaste != null) {
            return RecipeIngredient(capitalize(toTaste.groupValues[1]), toTaste.groupValues[2].lowercase())
        }
        return RecipeIngredient(capitalize(text), "")
    }

    private fun fromQuantity(quantity: String, name: String): RecipeIngredient {
        val cleanName = capitalize(name.trim().trimEnd('.'))
        val parsed = leadingAmount(quantity) ?: return RecipeIngredient(cleanName, quantity.trim())
        return RecipeIngredient(cleanName, quantity.trim(), parsed.first, parsed.second, parsed.third.trim())
    }

    private val unitWords = setOf(
        "g", "gr", "grs", "gramo", "gramos", "kg", "kilo", "kilos", "ml", "l", "lt", "litro", "litros",
        "cc", "taza", "tazas", "cda", "cdas", "cucharada", "cucharadas", "cdta", "cdtas", "cdita", "cditas",
        "cucharadita", "cucharaditas", "diente", "dientes", "unidad", "unidades", "u", "un", "lata", "latas",
        "rebanada", "rebanadas", "rodaja", "rodajas", "hoja", "hojas", "pizca", "pizcas", "puñado", "puñados",
        "tallo", "tallos", "ramita", "ramitas", "rama", "ramas", "filete", "filetes", "pieza", "piezas",
        "vaso", "vasos", "sobre", "sobres", "paquete", "paquetes", "trozo", "trozos", "chorrito", "chorro",
        "oz", "onza", "onzas", "lb", "libra", "libras", "manojo", "manojos", "cabeza", "cabezas",
    )

    private fun isUnit(word: String): Boolean = word.lowercase(Locale.ROOT) in unitWords

    private const val NUMBER = "(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d*\\s?[½¼¾⅓⅔⅛]|\\d+(?:[.,]\\d+)?)"
    private val amountRegex = Regex("^\\s*" + NUMBER + "(?:(?:\\s*[-–]\\s*|\\s+a\\s+)" + NUMBER + ")?\\s*(.*)$")

    /** El número al principio del texto, el final del rango si lo hay, y lo que queda detrás. */
    private fun leadingAmount(text: String): Triple<Double, Double?, String>? {
        val match = amountRegex.matchEntire(text.trim()) ?: return null
        val amount = numberValue(match.groupValues[1]) ?: return null
        if (amount <= 0.0) return null
        val max = match.groupValues[2].takeIf { it.isNotBlank() }?.let { numberValue(it) }?.takeIf { it > amount }
        return Triple(amount, max, match.groupValues[3].trim())
    }

    private val vulgarFractions = mapOf(
        '½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125,
    )

    private fun numberValue(token: String): Double? {
        val t = token.trim()
        val mixed = Regex("^(\\d+)\\s+(\\d+)/(\\d+)$").matchEntire(t)
        if (mixed != null) {
            val den = mixed.groupValues[3].toDouble()
            return if (den == 0.0) null else mixed.groupValues[1].toDouble() + mixed.groupValues[2].toDouble() / den
        }
        val simple = Regex("^(\\d+)/(\\d+)$").matchEntire(t)
        if (simple != null) {
            val den = simple.groupValues[2].toDouble()
            return if (den == 0.0) null else simple.groupValues[1].toDouble() / den
        }
        val fraction = t.lastOrNull()?.let { vulgarFractions[it] }
        if (fraction != null) {
            val whole = t.dropLast(1).trim()
            if (whole.isEmpty()) return fraction
            val wholeNumber = whole.toIntOrNull() ?: return null
            return wholeNumber + fraction
        }
        return t.replace(',', '.').toDoubleOrNull()
    }

    private fun parseMinutes(value: String): Int? {
        val text = normalize(value)
        val hours = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:h\\b|hr|hora)").find(text)?.groupValues?.get(1)
            ?.replace(',', '.')?.toDoubleOrNull()
        val minutes = Regex("(\\d+)\\s*(?:min|m\\b)").find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (hours != null || minutes != null) {
            return ((hours ?: 0.0) * 60).roundToInt() + (minutes ?: 0)
        }
        return firstInt(text)
    }

    private val timerRegex = Regex(
        "(\\d+(?:[.,]\\d+)?)(?:\\s*(?:-|a)\\s*(\\d+(?:[.,]\\d+)?))?\\s*(minutos?|mins?\\b|horas?|segundos?|seg\\b)"
    )

    /** Busca una espera en el texto del paso: «8 minutos», «1 hora», «30 segundos». */
    private fun detectTimerSeconds(text: String): Int {
        var best = 0
        for (match in timerRegex.findAll(normalize(text))) {
            val number = match.groupValues[2].ifEmpty { match.groupValues[1] }
            val value = number.replace(',', '.').toDoubleOrNull() ?: continue
            val unit = match.groupValues[3]
            val seconds = when {
                unit.startsWith("hora") -> value * 3600
                unit.startsWith("seg") -> value
                else -> value * 60
            }.roundToInt()
            if (seconds > best) best = seconds
        }
        return best
    }

    private fun normalizeDifficulty(value: String): String {
        val v = normalize(value)
        return when {
            "dific" in v || "alta" in v -> "Difícil"
            "med" in v || "interm" in v || "moder" in v -> "Media"
            "facil" in v || "senc" in v || "baja" in v -> "Fácil"
            else -> capitalize(value.trim().substringBefore(' ')).ifEmpty { "Fácil" }
        }
    }

    private fun firstInt(value: String): Int? = Regex("\\d+").find(value)?.value?.toIntOrNull()

    private fun deriveTitle(text: String): String {
        val clause = text.split('.', ',', ';', ':').first().trim()
        if (clause.length <= 34) return clause
        val cut = clause.take(34)
        val space = cut.lastIndexOf(' ')
        return (if (space > 10) cut.substring(0, space) else cut).trim()
    }

    private val tips = listOf(
        "Prueba y ajusta la sal al final: así controlas mejor el sabor.",
        "Ten todo picado antes de prender el fuego y cocinarás sin apuros.",
        "Un chorrito de limón al servir despierta todos los sabores.",
        "Deja reposar la preparación un par de minutos antes de servir.",
    )

    private fun defaultTip(title: String): String = tips[Math.floorMod(title.hashCode(), tips.size)]

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale("es")), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    fun capitalize(text: String): String =
        text.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es")) else it.toString() }
}

/** Reconoce qué ingredientes de la receta ya están en la despensa. */
object PantryMatcher {

    private val stopWords = setOf(
        "de", "del", "la", "las", "el", "los", "en", "con", "y", "o", "a", "al", "para", "sin", "un", "una",
        "unos", "unas", "fresco", "fresca", "frescos", "frescas", "picado", "picada", "rallado", "rallada",
        "cocido", "cocida", "grande", "grandes", "pequeno", "pequena", "mediano", "mediana", "entero", "entera",
        "gusto", "trozos", "cubos", "rodajas", "tiras",
    )

    private fun tokens(text: String): Set<String> =
        RecipeParser.normalize(text)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in stopWords }
            .map(::singular)
            .toSet()

    private fun singular(word: String): String = when {
        word.length > 4 && word.endsWith("ces") -> word.dropLast(3) + "z"
        word.length > 4 && word.endsWith("es") && word[word.length - 3] in "lnrdj" -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") -> word.dropLast(1)
        else -> word
    }

    fun matches(a: String, b: String): Boolean {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return false
        return tb.containsAll(ta) || ta.containsAll(tb)
    }
}

private val metricUnits = setOf("g", "gr", "grs", "gramo", "gramos", "ml", "cc", "mililitros")

/** 0,5 → «½», 1,5 → «1½», 225 g → «225». */
private fun formatAmount(value: Double, unit: String): String {
    if (unit.lowercase(Locale.ROOT).trimEnd('.') in metricUnits) {
        val rounded = if (value >= 20) (value / 5).roundToInt() * 5 else value.roundToInt()
        return rounded.coerceAtLeast(1).toString()
    }
    val whole = floor(value).toInt()
    val fraction = value - whole
    val fractions = listOf(0.0 to "", 0.25 to "¼", 1.0 / 3 to "⅓", 0.5 to "½", 2.0 / 3 to "⅔", 0.75 to "¾", 1.0 to "+")
    val (target, symbol) = fractions.minByOrNull { abs(it.first - fraction) }!!
    if (abs(target - fraction) <= 0.06) {
        return when {
            symbol == "+" -> (whole + 1).toString()
            symbol.isEmpty() -> whole.coerceAtLeast(0).toString()
            whole == 0 -> symbol
            else -> "$whole$symbol"
        }
    }
    val oneDecimal = (value * 10).roundToInt() / 10.0
    return if (oneDecimal % 1.0 == 0.0) oneDecimal.toInt().toString() else oneDecimal.toString().replace('.', ',')
}

private val unitPlurals = mapOf(
    "taza" to "tazas", "cda" to "cdas", "cucharada" to "cucharadas", "cdta" to "cdtas", "cdita" to "cditas",
    "cucharadita" to "cucharaditas", "diente" to "dientes", "unidad" to "unidades", "lata" to "latas",
    "rebanada" to "rebanadas", "rodaja" to "rodajas", "hoja" to "hojas", "pizca" to "pizcas", "puñado" to "puñados",
    "tallo" to "tallos", "ramita" to "ramitas", "rama" to "ramas", "filete" to "filetes", "pieza" to "piezas",
    "vaso" to "vasos", "sobre" to "sobres", "paquete" to "paquetes", "trozo" to "trozos", "litro" to "litros",
    "kilo" to "kilos", "gramo" to "gramos", "onza" to "onzas", "libra" to "libras", "manojo" to "manojos",
    "cabeza" to "cabezas",
)
private val unitSingulars = unitPlurals.entries.associate { (singular, plural) -> plural to singular }

/** «1 dientes» → «1 diente» y «3 taza» → «3 tazas» al cambiar las porciones. */
private fun adjustUnit(unit: String, value: Double): String {
    if (unit.isEmpty()) return unit
    val first = unit.substringBefore(' ')
    val rest = unit.substring(first.length)
    val lower = first.lowercase(Locale("es"))
    val replaced = if (value <= 1.0) unitSingulars[lower] else unitPlurals[lower]
    return if (replaced == null) unit else replaced + rest
}
