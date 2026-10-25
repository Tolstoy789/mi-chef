package com.chefdesobras.app

import android.content.Context
import org.json.JSONArray

/** Guarda las recetas favoritas completas, para abrirlas sin volver a generarlas. */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("favoritos", Context.MODE_PRIVATE)

    fun load(): List<Recipe> {
        val raw = prefs.getString(KEY_RECIPES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                runCatching { Recipe.fromJson(array.getJSONObject(i)) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(recipes: List<Recipe>) {
        val array = JSONArray()
        recipes.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_RECIPES, array.toString()).apply()
    }

    private companion object {
        const val KEY_RECIPES = "recipes"
    }
}
