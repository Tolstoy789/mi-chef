package com.chefdesobras.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // La app siempre usa el tema claro, así que los iconos de las barras del sistema van oscuros.
        val bars = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            ChefTheme {
                ChefApp()
            }
        }
    }
}

/** Profundidad de cada pantalla: decide hacia qué lado se desliza la transición. */
private fun Screen.depth(): Int = when (this) {
    Screen.Home -> 0
    Screen.Generating, Screen.Favorites -> 1
    Screen.Recipe -> 2
    Screen.Cooking -> 3
    Screen.Done -> 4
}

@Composable
fun ChefApp(viewModel: ChefViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Al volver a la app otro día, los días restantes se recalculan.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    // El botón atrás del sistema sigue la misma navegación que las flechas de la app.
    BackHandler(enabled = state.screen != Screen.Home) { viewModel.back() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val canUndo = state.messageCanUndo
        viewModel.messageShown()
        // En su propio ámbito: marcar el mensaje como visto reinicia este efecto y no debe cerrar el aviso.
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (canUndo) "Deshacer" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo()
        }
    }

    // Selector de archivos del sistema: el modelo .task no tiene tipo MIME propio, así que se aceptan todos.
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importModel(uri)
    }

    // «Guardar PDF»: el usuario elige dónde guardarlo (Descargas, Drive…).
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val current = viewModel.uiState.value
        val recipe = current.recipe
        if (uri == null || recipe == null) return@rememberLauncherForActivityResult
        val portions = current.portions
        val appContext = context.applicationContext
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri)?.use { RecipePdf.write(appContext, recipe, portions, it) }
                        ?: error("No se pudo abrir el archivo")
                }.isSuccess
            }
            viewModel.showMessage(if (ok) "PDF guardado" else "No se pudo guardar el PDF.")
        }
    }

    fun sharePdf() {
        val current = viewModel.uiState.value
        val recipe = current.recipe ?: return
        val portions = current.portions
        val appContext = context.applicationContext
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching { RecipePdf.createShareableFile(appContext, recipe, portions) }.getOrNull()
            }
            if (uri == null) {
                viewModel.showMessage("No se pudo crear el PDF.")
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, recipe.title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(recipe.title, uri)
            try {
                context.startActivity(Intent.createChooser(send, "Compartir receta"))
            } catch (e: ActivityNotFoundException) {
                viewModel.showMessage("No hay ninguna app para compartir el PDF.")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChefColors.Bg),
    ) {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                val forward = targetState.depth() >= initialState.depth()
                (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { width -> if (forward) width / 5 else -width / 5 }) togetherWith
                    fadeOut(tween(160))
            },
            label = "screen",
        ) { screen ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChefColors.Bg),
            ) {
                val recipe = state.recipe
                when {
                    screen == Screen.Home -> HomeScreen(
                        state = state,
                        onPickModel = { modelPicker.launch(arrayOf("*/*")) },
                        onRetryModel = viewModel::loadModel,
                        onAddIngredient = viewModel::addIngredient,
                        onRemoveIngredient = viewModel::removeIngredient,
                        onClearExpired = viewModel::clearExpired,
                        onTogglePreference = viewModel::togglePreference,
                        onGenerate = viewModel::generateRecipe,
                        onOpenFavorites = viewModel::openFavorites,
                    )

                    screen == Screen.Generating -> GeneratingScreen(
                        generation = state.generation,
                        onCancel = viewModel::cancelGeneration,
                        onRetry = viewModel::generateRecipe,
                    )

                    screen == Screen.Favorites -> FavoritesScreen(
                        favorites = state.favorites,
                        onBack = viewModel::goHome,
                        onOpen = viewModel::openFavorite,
                        onRemove = viewModel::removeFavorite,
                        onCreate = viewModel::goHome,
                    )

                    recipe == null -> LaunchedEffect(Unit) { viewModel.goHome() }

                    screen == Screen.Recipe -> RecipeScreen(
                        recipe = recipe,
                        portions = state.portions,
                        checked = state.checked,
                        pantryMatches = state.pantryMatches,
                        isFavorite = state.isFavorite,
                        fromFavorites = state.recipeFromFavorites,
                        onBack = viewModel::closeRecipe,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onChangePortions = viewModel::changePortions,
                        onToggleChecked = viewModel::toggleChecked,
                        onStartCooking = viewModel::startCooking,
                        onSharePdf = { sharePdf() },
                        onSavePdf = { savePdf.launch(RecipePdf.suggestedFileName(recipe)) },
                        onRegenerate = viewModel::generateRecipe,
                    )

                    screen == Screen.Cooking -> CookingScreen(
                        recipe = recipe,
                        portions = state.portions,
                        cooking = state.cooking,
                        onClose = viewModel::exitCooking,
                        onPrevious = viewModel::previousStep,
                        onNext = viewModel::nextStep,
                        onToggleTimer = viewModel::toggleTimer,
                        onResumeTimer = viewModel::resumeTimer,
                        onPauseTimer = viewModel::pauseTimer,
                        onResetTimer = viewModel::resetTimer,
                        onAsk = viewModel::askAssist,
                        onCloseAssist = viewModel::closeAssist,
                        onMessage = { viewModel.showMessage(it) },
                    )

                    else -> {
                        val ingredients = state.ingredients
                        val usedCount = remember(recipe, ingredients) {
                            ingredients.count { item -> recipe.ingredients.any { PantryMatcher.matches(item.name, it.name) } }
                        }
                        DoneScreen(
                            recipe = recipe,
                            portions = state.portions,
                            usedCount = usedCount,
                            onBackToRecipe = viewModel::backToRecipe,
                            onRepeat = viewModel::startCooking,
                            onRemoveUsed = viewModel::removeUsedFromPantry,
                            onHome = viewModel::goHome,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 96.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(16.dp),
                containerColor = ChefColors.Text,
                contentColor = Color.White,
                actionColor = ChefColors.Peach,
            )
        }
    }
}
