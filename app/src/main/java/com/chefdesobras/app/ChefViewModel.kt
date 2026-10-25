package com.chefdesobras.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil

/**
 * La fecha de caducidad es opcional. Cuando se indica, se guarda la fecha (no los días)
 * para que la cuenta se actualice sola cada día.
 */
data class Ingredient(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val expiryEpochDay: Long? = null,
) {
    fun daysLeft(todayEpochDay: Long): Int? = expiryEpochDay?.let { (it - todayEpochDay).toInt() }

    fun isExpired(todayEpochDay: Long): Boolean = (daysLeft(todayEpochDay) ?: 0) < 0

    fun isUrgent(todayEpochDay: Long): Boolean =
        daysLeft(todayEpochDay)?.let { it in 0..ChefViewModel.URGENT_DAYS } == true
}

enum class RecipePreference(val label: String, val promptRule: String) {
    QUICK("Rápida", "La receta debe estar lista en 20 minutos o menos."),
    VEGETARIAN("Vegetariana", "No añadas carne ni pescado aparte de los que ya están en la lista."),
    GLUTEN_FREE("Sin gluten", "No uses harina de trigo, pan, pasta ni otros ingredientes con gluten."),
}

sealed interface ModelStatus {
    data object Loading : ModelStatus
    /** No hay ningún modelo: la app pide elegir el archivo descargado. */
    data object NotSelected : ModelStatus
    /** Copiando el archivo elegido al almacenamiento de la app; progress va de 0 a 1 (null si no se sabe el tamaño). */
    data class Importing(val fileName: String, val progress: Float?) : ModelStatus
    data class Ready(val modelName: String) : ModelStatus
    data class Error(val message: String) : ModelStatus
}

enum class Screen { Home, Generating, Recipe, Cooking, Done, Favorites }

enum class AssistKind { WHY, SUBSTITUTE }

data class GenerationState(
    val text: String = "",
    val ingredientNames: List<String> = emptyList(),
    /** Otra respuesta todavía ocupa el modelo; esta empieza cuando termine. */
    val queued: Boolean = false,
    val error: String? = null,
)

data class CookingState(
    val stepIndex: Int = 0,
    val timerTotal: Int = 0,
    val timerRemaining: Int = 0,
    val timerRunning: Boolean = false,
    val assist: AssistKind? = null,
    val assistText: String = "",
    val assistLoading: Boolean = false,
    /** Sube cada vez que un temporizador llega a cero, para que la interfaz pueda avisar. */
    val alarmCount: Int = 0,
)

/** Ingredientes que el modelo vio en una foto, antes de agregarlos a la despensa. */
data class PhotoScan(
    val loading: Boolean = true,
    val items: List<String> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val error: String? = null,
)

data class ChefUiState(
    val screen: Screen = Screen.Home,
    val ingredients: List<Ingredient> = emptyList(),
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
    val preferences: Set<RecipePreference> = emptySet(),
    val modelStatus: ModelStatus = ModelStatus.Loading,
    val generation: GenerationState = GenerationState(),
    val recipe: Recipe? = null,
    val recipeFromFavorites: Boolean = false,
    val portions: Int = 2,
    /** Índices de los ingredientes de la receta que el usuario marcó como «ya lo tengo». */
    val checked: Set<Int> = emptySet(),
    /** Índices de los ingredientes de la receta que están en la despensa. */
    val pantryMatches: Set<Int> = emptySet(),
    val cooking: CookingState = CookingState(),
    val favorites: List<Recipe> = emptyList(),
    /** El modelo cargado entiende imágenes (Gemma 3n). */
    val canReadPhotos: Boolean = false,
    val photoScan: PhotoScan? = null,
    val message: String? = null,
    val messageCanUndo: Boolean = false,
) {
    val isFavorite: Boolean
        get() = recipe?.let { current -> favorites.any { it.id == current.id } } == true
}

class ChefViewModel(application: Application) : AndroidViewModel(application) {

    private val store = IngredientStore(application)
    private val favoritesStore = FavoritesStore(application)
    private val modelPrefs = application.getSharedPreferences("modelo", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        ChefUiState(ingredients = sorted(store.load()), favorites = favoritesStore.load())
    )
    val uiState: StateFlow<ChefUiState> = _uiState.asStateFlow()

    private var llmInference: LlmInference? = null

    // LlmInference no admite peticiones concurrentes ni cerrarse a mitad de una.
    private val inferenceMutex = Mutex()

    // Cada petición lleva un número: si el usuario cancela o pide otra, la respuesta vieja se ignora.
    @Volatile private var generationId = 0
    @Volatile private var assistId = 0
    @Volatile private var photoId = 0
    private val assistCache = mutableMapOf<Triple<String, Int, AssistKind>, String>()

    private var timerJob: Job? = null
    private var undoAction: (() -> Unit)? = null

    init {
        loadModel()
    }

    // ---------- Modelo ----------

    fun loadModel() {
        _uiState.update { it.copy(modelStatus = ModelStatus.Loading) }
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                inferenceMutex.withLock {
                    try {
                        val modelFile = findModelFile() ?: return@withLock ModelStatus.NotSelected
                        llmInference?.close()
                        llmInference = null
                        // Gemma 3n también entiende fotos; si no se puede cargar con visión, se carga solo con texto.
                        var images = modelFile.name.contains("3n", ignoreCase = true)
                        llmInference = try {
                            createEngine(modelFile, images)
                        } catch (e: Exception) {
                            if (!images) throw e
                            Log.w(TAG, "El modelo no cargó con visión; se usa solo texto", e)
                            images = false
                            createEngine(modelFile, false)
                        }
                        _uiState.update { it.copy(canReadPhotos = images) }
                        ModelStatus.Ready(modelFile.name)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al cargar el modelo", e)
                        ModelStatus.Error("No se pudo cargar el modelo: ${e.message}")
                    }
                }
            }
            _uiState.update { it.copy(modelStatus = status) }
        }
    }

    private fun createEngine(file: File, images: Boolean): LlmInference {
        val builder = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(file.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setMaxTopK(TOP_K)
        if (images) builder.setMaxNumImages(1)
        return LlmInference.createFromOptions(getApplication(), builder.build())
    }

    /**
     * Busca el modelo, en este orden:
     * 0. El archivo que el usuario eligió con el selector (copiado a filesDir/models).
     * 1. /data/local/tmp/llm/model.task (adb push, cómodo en desarrollo).
     * 2. Almacenamiento externo de la app: Android/data/<paquete>/files/model.task.
     * 3. assets/model.task, copiado una vez a filesDir (solo para modelos pequeños).
     */
    private fun findModelFile(): File? {
        modelPrefs.getString(KEY_MODEL_PATH, null)?.let(::File)?.takeIf { it.canRead() }?.let { return it }
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

    /**
     * Importa el modelo que el usuario eligió con el selector de archivos.
     * MediaPipe necesita una ruta de archivo real, así que se copia a filesDir/models
     * (una sola vez) y se borra el modelo anterior para no ocupar el doble de espacio.
     */
    fun importModel(uri: Uri) {
        val previous = _uiState.value.modelStatus
        if (_uiState.value.screen == Screen.Generating || previous is ModelStatus.Importing) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            var partFile: File? = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val (name, size) = queryNameAndSize(app, uri)
                    require(name.endsWith(".task", ignoreCase = true) || name.endsWith(".bin", ignoreCase = true)) {
                        "«$name» no parece un modelo. Elige un archivo .task."
                    }
                    val dir = File(app.filesDir, MODELS_DIR).apply { mkdirs() }
                    if (size > 0 && dir.usableSpace < size + SPACE_MARGIN_BYTES) {
                        error("No hay espacio suficiente: el modelo ocupa ${size / 1_000_000} MB.")
                    }
                    _uiState.update { it.copy(modelStatus = ModelStatus.Importing(name, if (size > 0) 0f else null)) }
                    val part = File(dir, "$name.part").also { partFile = it }
                    val input = app.contentResolver.openInputStream(uri) ?: error("No se pudo abrir el archivo.")
                    input.use { stream ->
                        part.outputStream().use { out ->
                            val buffer = ByteArray(1 shl 20)
                            var copied = 0L
                            var lastPercent = -1
                            while (true) {
                                val read = stream.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                copied += read
                                if (size > 0) {
                                    val percent = (copied * 100 / size).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        _uiState.update { it.copy(modelStatus = ModelStatus.Importing(name, percent / 100f)) }
                                    }
                                }
                            }
                        }
                    }
                    // Libera el modelo anterior antes de borrar su archivo.
                    inferenceMutex.withLock {
                        llmInference?.close()
                        llmInference = null
                    }
                    dir.listFiles()?.filter { it != part }?.forEach { it.delete() }
                    val target = File(dir, name)
                    if (!part.renameTo(target)) error("No se pudo guardar el modelo.")
                    modelPrefs.edit().putString(KEY_MODEL_PATH, target.absolutePath).apply()
                }
            }
            result.fold(
                onSuccess = { loadModel() },
                onFailure = { e ->
                    Log.e(TAG, "Error al importar el modelo", e)
                    partFile?.delete()
                    val restored = if (previous is ModelStatus.Ready && llmInference == null) ModelStatus.NotSelected else previous
                    _uiState.update {
                        it.copy(modelStatus = restored, message = e.message ?: "No se pudo importar el modelo.", messageCanUndo = false)
                    }
                },
            )
        }
    }

    private fun queryNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = "model.task"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return name.replace('/', '_') to size
    }

    // ---------- Despensa ----------

    /** Se llama al volver a la app: si cambió el día, se recalculan los días restantes. */
    fun refreshToday() {
        val today = LocalDate.now().toEpochDay()
        if (today != _uiState.value.todayEpochDay) {
            _uiState.update { it.copy(todayEpochDay = today) }
        }
    }

    fun addIngredient(name: String, daysText: String): Boolean {
        val cleanName = name.trim().replaceFirstChar { it.uppercase() }
        // Los días son opcionales: vacío = ingrediente sin fecha de caducidad.
        val daysInput = daysText.trim()
        val days = daysInput.toIntOrNull()
        val error = when {
            cleanName.isEmpty() -> "Escribe el nombre del ingrediente."
            daysInput.isNotEmpty() && (days == null || days < 0) -> "Los días para caducar deben ser un número de 0 o más."
            days != null && days > MAX_DAYS -> "Usa un valor de hasta $MAX_DAYS días."
            else -> null
        }
        if (error != null) {
            showMessage(error)
            return false
        }
        val today = _uiState.value.todayEpochDay
        updateIngredients { list ->
            // Si el ingrediente ya existe, se actualiza su fecha en vez de duplicarlo.
            list.filterNot { it.name.equals(cleanName, ignoreCase = true) } +
                Ingredient(name = cleanName, expiryEpochDay = days?.let { today + it })
        }
        return true
    }

    fun removeIngredient(id: String) {
        val removed = _uiState.value.ingredients.find { it.id == id } ?: return
        updateIngredients { list -> list.filterNot { it.id == id } }
        undoAction = { restoreIngredients(listOf(removed)) }
        showMessage("${removed.name} eliminado", canUndo = true)
    }

    fun clearExpired() {
        val today = _uiState.value.todayEpochDay
        val expired = _uiState.value.ingredients.filter { it.isExpired(today) }
        if (expired.isEmpty()) return
        updateIngredients { list -> list.filterNot { it in expired } }
        undoAction = { restoreIngredients(expired) }
        showMessage("Se quitaron ${expired.size} ingredientes caducados", canUndo = true)
    }

    /** «Ya lo cociné»: quita de la despensa los ingredientes que aparecen en la receta. */
    fun removeUsedFromPantry() {
        val state = _uiState.value
        val recipe = state.recipe ?: return
        val used = usedPantryItems(recipe, state.ingredients)
        if (used.isEmpty()) {
            showMessage("Ninguno de tus ingredientes aparece en esta receta.")
            return
        }
        val ids = used.map { it.id }.toSet()
        updateIngredients { list -> list.filterNot { it.id in ids } }
        undoAction = { restoreIngredients(used) }
        showMessage("Se quitaron ${used.size} ingredientes de tu despensa", canUndo = true)
    }

    private fun restoreIngredients(restore: List<Ingredient>) {
        updateIngredients { list -> list + restore.filter { r -> list.none { it.id == r.id } } }
    }

    fun togglePreference(preference: RecipePreference) {
        _uiState.update { state ->
            val prefs = if (preference in state.preferences) state.preferences - preference
            else state.preferences + preference
            state.copy(preferences = prefs)
        }
    }

    // ---------- Generar la receta ----------

    fun generateRecipe() {
        val state = _uiState.value
        val inference = llmInference
        if (state.modelStatus !is ModelStatus.Ready || inference == null) {
            showMessage("El modelo todavía no está listo.")
            return
        }
        val today = state.todayEpochDay
        // La receta se hace con los ingredientes de la lista; los caducados se dejan fuera.
        val usable = state.ingredients.filterNot { it.isExpired(today) }
        if (usable.isEmpty()) {
            showMessage(
                if (state.ingredients.isNotEmpty()) "Solo quedan ingredientes caducados. Revísalos antes de cocinarlos."
                else "Agrega al menos un ingrediente para crear la receta."
            )
            return
        }
        stopTimer()
        val prompt = Prompts.recipe(usable, state.preferences, today)
        val names = usable.map { it.name }
        val id = ++generationId
        _uiState.update {
            it.copy(
                screen = Screen.Generating,
                generation = GenerationState(ingredientNames = names, queued = inferenceMutex.isLocked),
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                inferenceMutex.withLock {
                    runCatching {
                        runInference(inference, prompt) { partial ->
                            if (id == generationId) {
                                _uiState.update { s -> s.copy(generation = s.generation.copy(text = partial, queued = false)) }
                            }
                        }
                    }
                }
            }
            if (id != generationId) return@launch
            val text = result.getOrNull()
            if (text != null && text.isNotBlank()) {
                showRecipe(RecipeParser.parse(text, names), fromFavorites = false)
            } else {
                val error = result.exceptionOrNull()
                if (error != null) Log.e(TAG, "Error en la inferencia", error)
                val reason = if (error == null) "el modelo no escribió nada" else error.message ?: "error desconocido"
                _uiState.update {
                    it.copy(generation = it.generation.copy(queued = false, error = "No se pudo crear la receta: $reason."))
                }
            }
        }
    }

    /** Sale de la pantalla de espera; si el modelo sigue escribiendo, su respuesta se descarta. */
    fun cancelGeneration() {
        generationId++
        _uiState.update { it.copy(screen = Screen.Home, generation = GenerationState()) }
    }

    // Una sesión nueva por petición, para que no arrastre el contexto de la anterior.
    // El texto llega por partes y se va mostrando mientras el modelo escribe.
    private suspend fun runInference(
        inference: LlmInference,
        prompt: String,
        image: Bitmap? = null,
        onPartial: (String) -> Unit,
    ): String {
        val sessionBuilder = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(TOP_K)
            .setTemperature(TEMPERATURE)
        if (image != null) {
            sessionBuilder.setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
        }
        val session = LlmInferenceSession.createFromOptions(inference, sessionBuilder.build())
        try {
            session.addQueryChunk(prompt)
            if (image != null) session.addImage(BitmapImageBuilder(image).build())
            val finished = CompletableDeferred<String>()
            val text = StringBuilder()
            session.generateResponseAsync { partial, done ->
                val soFar = synchronized(text) {
                    text.append(partial)
                    text.toString()
                }
                onPartial(soFar)
                if (done) finished.complete(soFar)
            }
            // No se interrumpe a medias: cerrar la sesión mientras escribe puede cerrar la app.
            val result = withContext(NonCancellable) {
                withTimeoutOrNull(GENERATION_TIMEOUT_MS) { finished.await() }
            }
            return result ?: error("el modelo tardó demasiado")
        } finally {
            session.close()
        }
    }

    private fun showRecipe(recipe: Recipe, fromFavorites: Boolean) {
        stopTimer()
        assistId++
        _uiState.update { s ->
            val matches = pantryMatches(recipe, s.ingredients)
            s.copy(
                screen = Screen.Recipe,
                recipe = recipe,
                recipeFromFavorites = fromFavorites,
                portions = recipe.basePortions.coerceIn(MIN_PORTIONS, MAX_PORTIONS),
                checked = matches,
                pantryMatches = matches,
                cooking = CookingState(alarmCount = s.cooking.alarmCount),
                generation = GenerationState(),
            )
        }
    }

    private fun pantryMatches(recipe: Recipe, pantry: List<Ingredient>): Set<Int> =
        recipe.ingredients.indices.filter { i ->
            pantry.any { PantryMatcher.matches(it.name, recipe.ingredients[i].name) }
        }.toSet()

    private fun usedPantryItems(recipe: Recipe, pantry: List<Ingredient>): List<Ingredient> =
        pantry.filter { item -> recipe.ingredients.any { PantryMatcher.matches(item.name, it.name) } }

    // ---------- Foto de los productos ----------

    /** El modelo mira la foto y propone los ingredientes que ve; el usuario elige cuáles agregar. */
    fun scanPhoto(bitmap: Bitmap) {
        val state = _uiState.value
        val inference = llmInference
        if (state.modelStatus !is ModelStatus.Ready || inference == null) {
            showMessage("El modelo todavía no está listo.")
            return
        }
        if (!state.canReadPhotos) {
            showMessage(NO_VISION_MESSAGE)
            return
        }
        val id = ++photoId
        _uiState.update { it.copy(photoScan = PhotoScan()) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                inferenceMutex.withLock {
                    runCatching { runInference(inference, Prompts.photo(), image = bitmap) { } }
                }
            }
            if (id != photoId) return@launch
            val error = result.exceptionOrNull()
            if (error != null) Log.e(TAG, "Error al leer la foto", error)
            val items = result.getOrNull()?.let { parsePhotoItems(it) }.orEmpty()
            _uiState.update {
                it.copy(
                    photoScan = when {
                        error != null -> PhotoScan(loading = false, error = "No pude leer la foto: ${error.message ?: "error desconocido"}.")
                        items.isEmpty() -> PhotoScan(
                            loading = false,
                            error = "No vi ingredientes en la foto. Prueba con más luz y los productos de frente.",
                        )
                        else -> PhotoScan(loading = false, items = items, selected = items.indices.toSet())
                    }
                )
            }
        }
    }

    private fun parsePhotoItems(text: String): List<String> {
        val seen = mutableSetOf<String>()
        return text.lines()
            .map { line ->
                line.trim()
                    .replace(Regex("^([-*•·]|\\d+[.)])\\s*"), "")
                    .replace("*", "")
                    .substringBefore(":")
                    .substringBefore("(")
                    .trim()
                    .trimEnd('.', ',', ';')
            }
            .filter { it.length in 2..40 && RecipeParser.normalize(it) != "ninguno" && !it.contains("foto", ignoreCase = true) }
            .map { RecipeParser.capitalize(it) }
            .filter { seen.add(RecipeParser.normalize(it)) }
            .take(15)
    }

    fun togglePhotoItem(index: Int) {
        _uiState.update { s ->
            val scan = s.photoScan ?: return@update s
            val selected = if (index in scan.selected) scan.selected - index else scan.selected + index
            s.copy(photoScan = scan.copy(selected = selected))
        }
    }

    /** Agrega a la despensa lo elegido de la foto y, si se pide, crea la receta en seguida. */
    fun addPhotoItems(createRecipe: Boolean) {
        val scan = _uiState.value.photoScan ?: return
        val names = scan.items.filterIndexed { i, _ -> i in scan.selected }
        _uiState.update { it.copy(photoScan = null) }
        if (names.isNotEmpty()) {
            updateIngredients { list ->
                list + names.filter { name -> list.none { it.name.equals(name, ignoreCase = true) } }.map { Ingredient(name = it) }
            }
        }
        if (createRecipe) {
            generateRecipe()
        } else if (names.isNotEmpty()) {
            showMessage(if (names.size == 1) "Se agregó ${names[0]}" else "Se agregaron ${names.size} ingredientes")
        }
    }

    fun dismissPhotoScan() {
        photoId++
        _uiState.update { it.copy(photoScan = null) }
    }

    // ---------- Receta ----------

    fun changePortions(delta: Int) {
        _uiState.update { it.copy(portions = (it.portions + delta).coerceIn(MIN_PORTIONS, MAX_PORTIONS)) }
    }

    fun toggleChecked(index: Int) {
        _uiState.update { s ->
            s.copy(checked = if (index in s.checked) s.checked - index else s.checked + index)
        }
    }

    fun toggleFavorite() {
        val state = _uiState.value
        val recipe = state.recipe ?: return
        val wasFavorite = state.isFavorite
        val updated = if (wasFavorite) state.favorites.filterNot { it.id == recipe.id } else listOf(recipe) + state.favorites
        _uiState.update { it.copy(favorites = updated) }
        saveFavorites(updated)
        showMessage(if (wasFavorite) "Quitada de tus favoritas" else "Guardada en tus favoritas")
    }

    fun removeFavorite(id: String) {
        val favorites = _uiState.value.favorites
        val index = favorites.indexOfFirst { it.id == id }
        if (index < 0) return
        val removed = favorites[index]
        val updated = favorites.filterNot { it.id == id }
        _uiState.update { it.copy(favorites = updated) }
        saveFavorites(updated)
        undoAction = {
            val current = _uiState.value.favorites
            if (current.none { it.id == removed.id }) {
                val restored = current.toMutableList().apply { add(index.coerceAtMost(size), removed) }
                _uiState.update { it.copy(favorites = restored) }
                saveFavorites(restored)
            }
        }
        showMessage("«${removed.title}» quitada de favoritas", canUndo = true)
    }

    fun openFavorite(recipe: Recipe) = showRecipe(recipe, fromFavorites = true)

    private fun saveFavorites(favorites: List<Recipe>) {
        viewModelScope.launch(Dispatchers.IO) { favoritesStore.save(favorites) }
    }

    // ---------- Modo cocina ----------

    fun startCooking() {
        val recipe = _uiState.value.recipe ?: return
        stopTimer()
        assistId++
        _uiState.update { it.copy(screen = Screen.Cooking, cooking = stepState(recipe, 0, it.cooking.alarmCount)) }
    }

    private fun stepState(recipe: Recipe, index: Int, alarmCount: Int): CookingState {
        val seconds = recipe.steps.getOrNull(index)?.timerSeconds ?: 0
        return CookingState(stepIndex = index, timerTotal = seconds, timerRemaining = seconds, alarmCount = alarmCount)
    }

    fun goToStep(index: Int) {
        val recipe = _uiState.value.recipe ?: return
        if (index !in recipe.steps.indices) return
        stopTimer()
        assistId++
        _uiState.update { it.copy(cooking = stepState(recipe, index, it.cooking.alarmCount)) }
    }

    fun nextStep() {
        val state = _uiState.value
        val recipe = state.recipe ?: return
        if (state.cooking.stepIndex < recipe.steps.lastIndex) {
            goToStep(state.cooking.stepIndex + 1)
        } else {
            stopTimer()
            assistId++
            _uiState.update {
                it.copy(screen = Screen.Done, cooking = it.cooking.copy(timerRunning = false, assist = null, assistLoading = false))
            }
        }
    }

    fun previousStep() = goToStep(_uiState.value.cooking.stepIndex - 1)

    fun exitCooking() {
        stopTimer()
        assistId++
        _uiState.update {
            it.copy(screen = Screen.Recipe, cooking = it.cooking.copy(timerRunning = false, assist = null, assistLoading = false))
        }
    }

    fun toggleTimer() {
        val cooking = _uiState.value.cooking
        if (cooking.timerTotal <= 0) return
        if (cooking.timerRunning) {
            pauseTimer()
        } else {
            startTimer(if (cooking.timerRemaining <= 0) cooking.timerTotal else cooking.timerRemaining)
        }
    }

    fun resumeTimer() {
        val cooking = _uiState.value.cooking
        if (!cooking.timerRunning && cooking.timerTotal > 0) toggleTimer()
    }

    fun pauseTimer() {
        stopTimer()
        _uiState.update { it.copy(cooking = it.cooking.copy(timerRunning = false)) }
    }

    fun resetTimer() {
        stopTimer()
        _uiState.update { it.copy(cooking = it.cooking.copy(timerRunning = false, timerRemaining = it.cooking.timerTotal)) }
    }

    private fun startTimer(seconds: Int) {
        stopTimer()
        // Se calcula con el reloj del sistema para que no se atrase aunque la app vaya lenta.
        val endAt = SystemClock.elapsedRealtime() + seconds * 1000L
        _uiState.update { it.copy(cooking = it.cooking.copy(timerRunning = true, timerRemaining = seconds)) }
        timerJob = viewModelScope.launch {
            while (isActive) {
                val left = ceil((endAt - SystemClock.elapsedRealtime()) / 1000.0).toInt()
                if (left <= 0) {
                    _uiState.update {
                        it.copy(
                            cooking = it.cooking.copy(
                                timerRunning = false,
                                timerRemaining = 0,
                                alarmCount = it.cooking.alarmCount + 1,
                            )
                        )
                    }
                    playAlarm()
                    break
                }
                if (left != _uiState.value.cooking.timerRemaining) {
                    _uiState.update { it.copy(cooking = it.cooking.copy(timerRemaining = left)) }
                }
                delay(200)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun playAlarm() {
        val app = getApplication<Application>()
        runCatching {
            RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        }
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 180, 350, 180, 600), -1))
        }
    }

    /** «¿Por qué hacemos esto?» y «Sustituir ingrediente»: el modelo responde sobre el paso actual. */
    fun askAssist(kind: AssistKind) {
        val state = _uiState.value
        val recipe = state.recipe ?: return
        if (state.cooking.assist == kind) {
            closeAssist()
            return
        }
        val step = state.cooking.stepIndex
        if (step !in recipe.steps.indices) return
        val id = ++assistId
        val key = Triple(recipe.id, step, kind)
        val cached = assistCache[key]
        if (cached != null) {
            _uiState.update { it.copy(cooking = it.cooking.copy(assist = kind, assistText = cached, assistLoading = false)) }
            return
        }
        val inference = llmInference
        if (state.modelStatus !is ModelStatus.Ready || inference == null) {
            _uiState.update {
                it.copy(
                    cooking = it.cooking.copy(
                        assist = kind,
                        assistText = "Necesito el modelo cargado para responderte.",
                        assistLoading = false,
                    )
                )
            }
            return
        }
        val prompt = if (kind == AssistKind.WHY) Prompts.why(recipe, step) else Prompts.substitute(recipe, step)
        _uiState.update { it.copy(cooking = it.cooking.copy(assist = kind, assistText = "", assistLoading = true)) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                inferenceMutex.withLock {
                    runCatching {
                        runInference(inference, prompt) { partial ->
                            if (id == assistId) {
                                _uiState.update { s -> s.copy(cooking = s.cooking.copy(assistText = cleanAnswer(partial))) }
                            }
                        }
                    }
                }
            }
            val answer = result.getOrNull()?.let { cleanAnswer(it) }?.takeIf { it.isNotBlank() }
            if (answer != null) assistCache[key] = answer
            if (id != assistId) return@launch
            _uiState.update {
                it.copy(
                    cooking = it.cooking.copy(
                        assistLoading = false,
                        assistText = answer ?: "No pude responder esta vez. Inténtalo otra vez.",
                    )
                )
            }
        }
    }

    fun closeAssist() {
        assistId++
        _uiState.update { it.copy(cooking = it.cooking.copy(assist = null, assistText = "", assistLoading = false)) }
    }

    private fun cleanAnswer(text: String): String =
        text.replace("**", "").replace("##", "").replace("* ", "").trim()

    // ---------- Navegación ----------

    fun back() {
        when (_uiState.value.screen) {
            Screen.Home -> Unit
            Screen.Generating -> cancelGeneration()
            Screen.Recipe -> closeRecipe()
            Screen.Cooking -> exitCooking()
            Screen.Done -> backToRecipe()
            Screen.Favorites -> goHome()
        }
    }

    fun closeRecipe() {
        stopTimer()
        _uiState.update { it.copy(screen = if (it.recipeFromFavorites) Screen.Favorites else Screen.Home) }
    }

    fun backToRecipe() {
        stopTimer()
        _uiState.update { it.copy(screen = Screen.Recipe) }
    }

    fun openFavorites() = _uiState.update { it.copy(screen = Screen.Favorites) }

    fun goHome() = _uiState.update { it.copy(screen = Screen.Home) }

    // ---------- Mensajes ----------

    fun showMessage(text: String, canUndo: Boolean = false) {
        if (!canUndo) undoAction = null
        _uiState.update { it.copy(message = text, messageCanUndo = canUndo) }
    }

    fun undo() {
        val action = undoAction ?: return
        undoAction = null
        action()
    }

    fun messageShown() = _uiState.update { it.copy(message = null, messageCanUndo = false) }

    private fun updateIngredients(transform: (List<Ingredient>) -> List<Ingredient>) {
        _uiState.update { it.copy(ingredients = sorted(transform(it.ingredients))) }
        val snapshot = _uiState.value.ingredients
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    private fun sorted(list: List<Ingredient>): List<Ingredient> =
        list.sortedWith(compareBy<Ingredient> { it.expiryEpochDay ?: Long.MAX_VALUE }.thenBy { it.name.lowercase() })

    override fun onCleared() {
        stopTimer()
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
        const val NO_VISION_MESSAGE =
            "Para leer fotos hace falta un modelo con visión, como Gemma 3n E2B (.task). Con el modelo actual, escribe los ingredientes."
        const val MIN_PORTIONS = 1
        const val MAX_PORTIONS = 12
        private const val MAX_DAYS = 365
        private const val MAX_TOKENS = 1024
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.7f
        private const val GENERATION_TIMEOUT_MS = 180_000L
        private const val MODELS_DIR = "models"
        private const val KEY_MODEL_PATH = "model_path"
        private const val SPACE_MARGIN_BYTES = 50_000_000L
    }
}
