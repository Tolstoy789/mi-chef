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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class Ingredient(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val daysToExpire: Int,
)

sealed interface ModelStatus {
    data object Loading : ModelStatus
    data object Ready : ModelStatus
    data class Error(val message: String) : ModelStatus
}

data class ChefUiState(
    val ingredients: List<Ingredient> = emptyList(),
    val modelStatus: ModelStatus = ModelStatus.Loading,
    val isGenerating: Boolean = false,
    val recipe: String? = null,
    val message: String? = null,
)

class ChefViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChefUiState())
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

    fun addIngredient(name: String, daysText: String): Boolean {
        val cleanName = name.trim()
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
        _uiState.update { state ->
            state.copy(
                ingredients = (state.ingredients + Ingredient(name = cleanName, daysToExpire = days!!))
                    .sortedWith(compareBy<Ingredient> { it.daysToExpire }.thenBy { it.name.lowercase() }),
            )
        }
        return true
    }

    fun removeIngredient(id: String) {
        _uiState.update { state -> state.copy(ingredients = state.ingredients.filterNot { it.id == id }) }
    }

    fun generateRecipe() {
        val state = _uiState.value
        if (state.isGenerating) return
        val inference = llmInference
        if (state.modelStatus != ModelStatus.Ready || inference == null) {
            _uiState.update { it.copy(message = "El modelo todavía no está listo.") }
            return
        }
        val urgent = state.ingredients.filter { it.daysToExpire <= URGENT_DAYS }
        if (urgent.isEmpty()) {
            _uiState.update {
                it.copy(message = "No hay ingredientes que caduquen en $URGENT_DAYS días o menos.")
            }
            return
        }

        _uiState.update { it.copy(isGenerating = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                inferenceMutex.withLock {
                    runCatching { runInference(inference, buildPrompt(urgent)) }
                }
            }
            _uiState.update { current ->
                result.fold(
                    onSuccess = { text ->
                        current.copy(
                            isGenerating = false,
                            recipe = text.trim().ifEmpty { "El modelo no devolvió ninguna receta. Inténtalo de nuevo." },
                        )
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error en la inferencia", e)
                        current.copy(isGenerating = false, message = "No se pudo generar la receta: ${e.message}")
                    },
                )
            }
        }
    }

    // Una sesión nueva por receta, para que no arrastre el contexto de la anterior.
    private fun runInference(inference: LlmInference, prompt: String): String {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(TOP_K)
            .setTemperature(TEMPERATURE)
            .build()
        val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
        try {
            session.addQueryChunk(prompt)
            return session.generateResponse()
        } finally {
            session.close()
        }
    }

    private fun buildPrompt(urgent: List<Ingredient>): String {
        val list = urgent.joinToString("\n") { "- ${it.name} (caduca en ${daysLabel(it.daysToExpire)})" }
        return """
            Eres un chef profesional especializado en cocina de aprovechamiento.
            Crea UNA receta que use OBLIGATORIAMENTE todos estos ingredientes, que están a punto de caducar:
            $list

            Reglas estrictas:
            1. Usa todos los ingredientes de la lista, sin omitir ninguno.
            2. Solo puedes añadir ingredientes básicos de despensa: sal, pimienta, aceite, agua, ajo, cebolla, especias secas, harina, azúcar o vinagre.
            3. No inventes otros ingredientes principales.
            4. Responde en español y con este formato exacto:

            Nombre: <nombre de la receta>
            Tiempo: <minutos totales>
            Ingredientes:
            - <ingrediente y cantidad>
            Pasos:
            1. <paso>
            Consejo: <una frase para aprovechar las sobras>
        """.trimIndent()
    }

    private fun daysLabel(days: Int): String = when (days) {
        0 -> "hoy"
        1 -> "1 día"
        else -> "$days días"
    }

    fun dismissRecipe() = _uiState.update { it.copy(recipe = null) }

    fun messageShown() = _uiState.update { it.copy(message = null) }

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
    }
}
