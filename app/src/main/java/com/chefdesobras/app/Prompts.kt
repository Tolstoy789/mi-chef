package com.chefdesobras.app

/** Las instrucciones que recibe el modelo. Son cortas porque entrada y salida comparten 1024 tokens. */
object Prompts {

    fun recipe(
        ingredients: List<Ingredient>,
        preferences: Set<RecipePreference>,
        today: Long,
    ): String = buildString {
        appendLine("Eres Chefcito, un chef casero experto en cocina de aprovechamiento.")
        appendLine("Crea UNA receta con estos ingredientes que tengo en casa:")
        ingredients.forEach { ingredient ->
            val days = ingredient.daysLeft(today)
            if (ingredient.isUrgent(today) && days != null) {
                appendLine("- ${ingredient.name} (caduca ${daysLabel(days)}, es obligatorio)")
            } else {
                appendLine("- ${ingredient.name}")
            }
        }
        appendLine()
        appendLine("Reglas:")
        val rules = buildList {
            add("Usa todos los ingredientes de la lista que combinen bien.")
            add(
                "Solo puedes añadir básicos de despensa: sal, pimienta, aceite, agua, ajo, cebolla, " +
                    "especias, harina, azúcar o vinagre."
            )
            preferences.forEach { add(it.promptRule) }
            add("Escribe en español, sin markdown, siguiendo EXACTAMENTE este formato:")
        }
        rules.forEachIndexed { i, rule -> appendLine("${i + 1}. $rule") }
        appendLine()
        appendLine("NOMBRE: <nombre corto y apetitoso>")
        appendLine("DESCRIPCION: <una frase que dé hambre>")
        appendLine("TIEMPO: <minutos totales, solo el número>")
        appendLine("DIFICULTAD: <Fácil, Media o Difícil>")
        appendLine("PORCIONES: <número>")
        appendLine("CALORIAS: <kcal por porción, solo el número>")
        appendLine("TIP: <un consejo de chef para esta receta>")
        appendLine("INGREDIENTES:")
        appendLine("- <cantidad> | <ingrediente>")
        appendLine("PASOS:")
        append("1. <título corto> | <explicación clara del paso> | <minutos de espera o 0>")
    }

    fun why(recipe: Recipe, stepIndex: Int): String {
        val step = recipe.steps[stepIndex]
        return buildString {
            appendLine("Eres Chefcito, un chef amable que enseña a cocinar.")
            appendLine("Receta: ${recipe.title}.")
            appendLine("Paso ${stepIndex + 1}: ${step.title}. ${step.body}")
            appendLine()
            append(
                "Explica en 2 o 3 frases sencillas por qué se hace este paso así: la técnica o la razón de cocina. " +
                    "Responde solo con la explicación, en español y sin markdown."
            )
        }
    }

    fun substitute(recipe: Recipe, stepIndex: Int): String {
        val step = recipe.steps[stepIndex]
        val ingredients = recipe.ingredients.joinToString(", ") { it.name }
        return buildString {
            appendLine("Eres Chefcito, un chef amable que enseña a cocinar.")
            appendLine("Receta: ${recipe.title}. Ingredientes: $ingredients.")
            appendLine("Paso ${stepIndex + 1}: ${step.title}. ${step.body}")
            appendLine()
            append(
                "Si no tengo el ingrediente principal de este paso, ¿con qué lo sustituyo? " +
                    "Da una o dos alternativas caseras con la cantidad, en 2 o 3 frases. " +
                    "Responde solo con la sugerencia, en español y sin markdown."
            )
        }
    }

    private fun daysLabel(days: Int): String = when (days) {
        0 -> "hoy"
        1 -> "mañana"
        else -> "en $days días"
    }

    fun photo(): String = """
        Eres un chef. Mira la foto y enumera los alimentos e ingredientes de cocina que se ven (frutas, verduras, carnes, lácteos, paquetes, latas, especias…).
        Responde solo con la lista, un ingrediente por línea empezando con "- ", con nombres cortos en español y sin cantidades ni marcas.
        Si no se ve ningún alimento, responde solo: NINGUNO
    """.trimIndent()
}
