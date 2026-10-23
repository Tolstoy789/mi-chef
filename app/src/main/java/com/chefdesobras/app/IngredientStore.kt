package com.chefdesobras.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Guarda la despensa en SharedPreferences como JSON, para no perderla al cerrar la app. */
class IngredientStore(context: Context) {

    private val prefs = context.getSharedPreferences("despensa", Context.MODE_PRIVATE)

    fun load(): List<Ingredient> {
        val raw = prefs.getString(KEY_INGREDIENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Ingredient(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    expiryEpochDay = obj.getLong("expiryEpochDay"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(ingredients: List<Ingredient>) {
        val array = JSONArray()
        ingredients.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("expiryEpochDay", it.expiryEpochDay)
            )
        }
        prefs.edit().putString(KEY_INGREDIENTS, array.toString()).apply()
    }

    private companion object {
        const val KEY_INGREDIENTS = "ingredients"
    }
}
