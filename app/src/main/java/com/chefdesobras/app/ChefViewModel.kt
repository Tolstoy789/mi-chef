package com.chefdesobras.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.resume

/** Se guarda la fecha de caducidad, no los días, para que la cuenta se actualice sola cada día. */
data class Ingredient(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val expiryEpochDay: Long,
) {
    fun daysLeft(todayEpochDay: Long): Int = (expiryEpochDay - todayEpochDay).toInt()
}

enum class RecipePreference(val label: String, val promptRule: String) {
    QUICK("Rápida", "La receta debe estar lista en 20 minutos o menos."),
    VEGETARIAN("Vegetariana", "No añadas carne ni pescado aparte de los que ya están en la lista."),
    GLUTEN_FREE("Sin gluten", "No uses harina de trigo, pan, pasta ni otros ingredientes con gluten."),
}

sealed interface ModelStatus {
    data object Loading : ModelStatus
    data object Ready : ModelStatus
    data class Error(val message: String) : ModelStatus
}

data class RecipeState(
    val text: String = "",
    val isGenerating: Boolean = true,
    val usedIngredientIds: Set<String> = emptySet(),
)

data class ChefUiState(
    val ingredients: List<Ingredient> = emptyList(),
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
    val preferences: Set<RecipePreference> = emptySet(),
    val modelStatus: ModelStatus = ModelStatus.Loading,
    val recipe: RecipeState? = null,
    val message: String? = null,
    val messageCanUndo: Boolean = false,
) {
    val isGenerating: Boolean get() = recipe?.isGenerating == true
}

class ChefViewModel(application: Application) : AndroidViewModel(application) {

    private val store = IngredientStore(application)

    private val _uiState = MutableStateFlow(ChefUiState(ingredients = sorted(store.load())))
    val uiState: StateFlow<ChefUiState> = _uiState.asStateFlow()

    private var llmInference: LlmInference? = null

    // LlmInference no admite peticiones concurrentes ni cerrarse a mitad de una.
    private val inferenceMutex = Mutex()

    init {
        loadModel()
    }

    fun loadModel() {
        _uiState.update { it.copy(modelStatus = ModelStatus.Loading) }
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                inferenceMutex.withLock {
                    try {
                        val modelFile = findModelFile()
                            ?: return@withLock ModelStatus.Error(
                                "No se encontró el modelo. Copia model.task en " +
                                    "$DEVICE_MODEL_PATH o en ${externalModelFile()?.absolutePath}."
                            )
                        val options = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelFile.absolutePath)
                            .setMaxTokens(MAX_TOKENS)
                            .setMaxTopK(TOP_K)
                            .build()
                        llmInference?.close()
                        llmInference = LlmInference.createFromOptions(getApplication(), options)
                        ModelStatus.Ready
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al cargar el modelo", e)
                        ModelStatus.Error("No se pudo cargar el modelo: ${e.message}")
                    }
                }
            }
            _uiState.update { it.copy(modelStatus = status) }
        }
    }

    /**
     * Busca el modelo, en este orden:
     * 1. /data/local/tmp/llm/model.task (adb push, cómodo en desarrollo).
     * 2. Almacenamiento externo de la app: Android/data/<paquete>/files/model.task.
     * 3. assets/model.task, copiado una vez a filesDir (solo para modelos pequeños).
     */
    private fun findModelFile(): File? {
        File(DEVICE_MODEL_PATH).takeIf { it.canRead() }?.let { return it }
        externalModelFile()?.takeIf { it.canRead() }?.let { return it }
        return copyModelFromAssets()
    }

    private fun externalModelFile(): File? =
        getApplication<Application>().getExternalFilesDir(null)?.let { File(it, MODEL_FILE_NAME) }

    private fun copyModelFromAssets(): File? {
        val app = getApplication<Application>()
        val target = File(app.filesDir, MODEL_FILE_NAME)
        if (target.exists() && target.length() > 0) return target
        return try {
            val tmp = File(app.filesDir, "$MODEL_FILE_NAME.tmp")
            app.assets.open(MODEL_FILE_NAME).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(target)
            target
        } catch (e: Exception) {
            null
        }
    }

    /** Se llama al volver a la app: si cambió el día, se recalculan los días restantes. */
    fun refreshToday() {
        val today = LocalDate.now().toEpochDay()
        if (today != _uiState.value.todayEpochDay) {
            _uiState.update { it.copy(todayEpochDay = today) }
        }
    }

    fun addIngredient(name: String, daysText: String): Boolean {
        val cleanName = name.trim().replaceFirstChar { it.uppercase() }
        val days = daysText.trim().toIntOrNull()
        val error = when {
            cleanName.isEmpty() -> "Escribe el nombre del ingrediente."
            days == null || days < 0 -> "Los días para caducar deben ser un número de 0 o más."
            days > MAX_DAYS -> "Usa un valor de hasta $MAX_DAYS días."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(message = error) }
            return false
        }
        val today = _uiState.value.todayEpochDay
        updateIngredients { list ->
            // Si el ingrediente ya existe, se actualiza su fecha en vez de duplicarlo.
            list.filterNot { it.name.equals(cleanName, ignoreCase = true) } +
                Ingredient(name = cleanName, expiryEpochDay = today + days!!)
        }
        return true
    }

    fun removeIngredient(id: String) {
        val removed = _uiState.value.ingredients.find { it.id == id } ?: return
        updateIngredients { list -> list.filterNot { it.id == id } }
        lastRemoved = listOf(removed)
        _uiState.update { it.copy(message = "${removed.name} eliminado", messageCanUndo = true) }
    }

    private var lastRemoved: List<Ingredient> = emptyList()

    fun undoRemove() {
        val restore = lastRemoved
        if (restore.isEmpty()) return
        lastRemoved = emptyList()
        updateIngredients { list -> list + restore.filter { r -> list.none { it.id == r.id } } }
    }

    fun clearExpired() {
        val today = _uiState.value.todayEpochDay
        val expired = _uiState.value.ingredients.filter { it.daysLeft(today) < 0 }
        if (expired.isEmpty()) return
        lastRemoved = expired
        updateIngredients { list -> list.filterNot { it in expired } }
        _uiState.update { it.copy(message = "Se quitaron ${expired.size} ingredientes caducados", messageCanUndo = true) }
    }

    fun togglePreference(preference: RecipePreference) {
        _uiState.update { state ->
            val prefs = if (preference in state.preferences) state.preferences - preference
            else state.preferences + preference
            state.copy(preferences = prefs)
        }
    }

    fun generateRecipe() {
        val state = _uiState.value
        if (state.isGenerating) return
        val inference = llmInference
        if (state.modelStatus != ModelStatus.Ready || inference == null) {
            _uiState.update { it.copy(message = "El modelo todavía no está listo.") }
            return
        }
        val today = state.todayEpochDay
        val urgent = state.ingredients.filter { it.daysLeft(today) in 0..URGENT_DAYS }
        val pantry = state.ingredients.filter { it.daysLeft(today) > URGENT_DAYS }
        if (urgent.isEmpty()) {
            val hasExpired = state.ingredients.any { it.daysLeft(today) < 0 }
            _uiState.update {
                it.copy(
                    message = if (hasExpired) "Solo quedan ingredientes caducados. Revísalos antes de cocinarlos."
                    else "No hay ingredientes que caduquen en $URGENT_DAYS días o menos."
                )
            }
            return
        }

        val prompt = buildPrompt(urgent, pantry, state.preferences, today)
        _uiState.update { it.copy(recipe = RecipeState(usedIngredientIds = urgent.map(Ingredient::id).toSet())) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                inferenceMutex.withLock {
                    runCatching {
                        withTimeout(GENERATION_TIMEOUT_MS) {
                            runInference(inference, prompt) { partial ->
                                _uiState.update { s -> s.copy(recipe = s.recipe?.copy(text = cleanText(partial))) }
                            }
                        }
                    }
                }
            }
            _uiState.update { current ->
                val recipe = current.recipe ?: return@update current
                result.fold(
                    onSuccess = { text ->
                        val finalText = cleanText(text).ifEmpty { "El modelo no devolvió ninguna receta. Prueba con «Otra receta»." }
                        current.copy(recipe = recipe.copy(text = finalText, isGenerating = false))
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error en la inferencia", e)
                        current.copy(
                            recipe = null,
                            message = "No se pudo generar la receta: ${e.message ?: "tiempo agotado"}",
                        )
                    },
                )
            }
        }
    }

    // Una sesión nueva por receta, para que no arrastre el contexto de la anterior.
    // El texto llega por partes y se va mostrando mientras el modelo escribe.
    private suspend fun runInference(
        inference: LlmInference,
        prompt: String,
        onPartial: (String) -> Unit,
    ): String {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(TOP_K)
            .setTemperature(TEMPERATURE)
            .build()
        val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
        try {
            session.addQueryChunk(prompt)
            return suspendCancellableCoroutine { cont ->
                val text = StringBuilder()
                session.generateResponseAsync { partial, done ->
                    text.append(partial)
                    onPartial(text.toString())
                    if (done && cont.isActive) cont.resume(text.toString())
                }
            }
        } finally {
            session.close()
        }
    }

    private fun cleanText(text: String): String = text.replace("**", "").replace("##", "").trim()

    private fun buildPrompt(
        urgent: List<Ingredient>,
        pantry: List<Ingredient>,
        preferences: Set<RecipePreference>,
        today: Long,
    ): String {
        val rules = buildList {
            add("Usa todos los ingredientes de la primera lista, sin omitir ninguno.")
            add(
                "Además de esos, solo puedes añadir ingredientes básicos de despensa: sal, pimienta, " +
                    "aceite, agua, ajo, cebolla, especias secas, harina, azúcar o vinagre."
            )
            add("No inventes otros ingredientes principales.")
            preferences.forEach { add(it.promptRule) }
            add("Responde en español, sin usar markdown, con el formato exacto de abajo.")
        }
        return buildString {
            appendLine("Eres un chef profesional especializado en cocina de aprovechamiento.")
            appendLine("Crea UNA receta que use OBLIGATORIAMENTE todos estos ingredientes, que están a punto de caducar:")
            urgent.forEach { appendLine("- ${it.name} (caduca ${daysLabel(it.daysLeft(today))})") }
            if (pantry.isNotEmpty()) {
                appendLine()
                appendLine(
                    "Si lo necesitas, también puedes usar estos ingredientes que tengo en casa: " +
                        pantry.joinToString(", ") { it.name } + "."
                )
            }
            appendLine()
            appendLine("Reglas estrictas:")
            rules.forEachIndexed { i, rule -> appendLine("${i + 1}. $rule") }
            appendLine()
            appendLine("Nombre: <nombre de la receta>")
            appendLine("Tiempo: <minutos totales>")
            appendLine("Raciones: <número>")
            appendLine("Ingredientes:")
            appendLine("- <ingrediente y cantidad>")
            appendLine("Pasos:")
            appendLine("1. <paso>")
            append("Consejo: <una frase para aprovechar las sobras>")
        }
    }

    private fun daysLabel(days: Int): String = when (days) {
        0 -> "hoy"
        1 -> "mañana"
        else -> "en $days días"
    }

    /** «Ya lo cociné»: quita de la despensa los ingredientes que usó la receta. */
    fun markRecipeCooked() {
        val used = _uiState.value.recipe?.usedIngredientIds ?: return
        lastRemoved = _uiState.value.ingredients.filter { it.id in used }
        updateIngredients { list -> list.filterNot { it.id in used } }
        _uiState.update { it.copy(recipe = null, message = "¡Buen provecho! Se quitaron ${used.size} ingredientes de la despensa", messageCanUndo = true) }
    }

    fun dismissRecipe() {
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(recipe = null) }
    }

    fun messageShown() = _uiState.update { it.copy(message = null, messageCanUndo = false) }

    private fun updateIngredients(transform: (List<Ingredient>) -> List<Ingredient>) {
        _uiState.update { it.copy(ingredients = sorted(transform(it.ingredients))) }
        val snapshot = _uiState.value.ingredients
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    private fun sorted(list: List<Ingredient>): List<Ingredient> =
        list.sortedWith(compareBy<Ingredient> { it.expiryEpochDay }.thenBy { it.name.lowercase() })

    override fun onCleared() {
        // Se cierra tras la inferencia en curso, si la hay, para no liberar memoria nativa en uso.
        val inference = llmInference
        llmInference = null
        if (inference != null) {
            Thread {
                kotlinx.coroutines.runBlocking { inferenceMutex.withLock { inference.close() } }
            }.start()
        }
        super.onCleared()
    }

    companion object {
        private const val TAG = "ChefViewModel"
        const val DEVICE_MODEL_PATH = "/data/local/tmp/llm/model.task"
        const val MODEL_FILE_NAME = "model.task"
        const val URGENT_DAYS = 3
        private const val MAX_DAYS = 365
        private const val MAX_TOKENS = 1024
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.7f
        private const val GENERATION_TIMEOUT_MS = 180_000L
    }
}
