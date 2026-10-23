package com.chefdesobras.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChefTheme {
                ChefScreen()
            }
        }
    }
}

@Composable
fun ChefTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("es"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefScreen(viewModel: ChefViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Al volver a la app otro día, los días restantes se recalculan.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val canUndo = state.messageCanUndo
        viewModel.messageShown()
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (canUndo) "Deshacer" else null,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove()
    }

    // Selector de archivos del sistema: el modelo .task no tiene tipo MIME propio, así que se aceptan todos.
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importModel(uri)
    }
    val chooseModel = { modelPicker.launch(arrayOf("*/*")) }
    val showSetup = state.modelStatus is ModelStatus.NotSelected || state.modelStatus is ModelStatus.Importing

    val today = state.todayEpochDay
    val urgentCount = state.ingredients.count { it.daysLeft(today) in 0..ChefViewModel.URGENT_DAYS }
    val expiredCount = state.ingredients.count { it.daysLeft(today) < 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chef de Sobras") },
                actions = {
                    if (expiredCount > 0) {
                        TextButton(onClick = viewModel::clearExpired) { Text("Quitar caducados") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!showSetup) {
                val enabled = state.modelStatus is ModelStatus.Ready && !state.isGenerating
                ExtendedFloatingActionButton(
                    onClick = { if (enabled) viewModel.generateRecipe() },
                    containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    icon = {
                        if (state.isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        }
                    },
                    text = {
                        Text(
                            when {
                                state.isGenerating -> "Generando…"
                                urgentCount > 0 -> "Generar Receta ($urgentCount)"
                                else -> "Generar Receta"
                            }
                        )
                    },
                )
            }
        },
    ) { padding ->
        if (showSetup) {
            ModelSetupScreen(
                status = state.modelStatus,
                onChoose = chooseModel,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ModelStatusBanner(
                    status = state.modelStatus,
                    canChange = !state.isGenerating,
                    onRetry = viewModel::loadModel,
                    onChoose = chooseModel,
                )
                IngredientForm(onAdd = viewModel::addIngredient)
                PreferenceChips(selected = state.preferences, onToggle = viewModel::togglePreference)
                if (state.ingredients.isNotEmpty()) {
                    PantrySummary(total = state.ingredients.size, urgent = urgentCount, expired = expiredCount)
                }
                IngredientList(
                    ingredients = state.ingredients,
                    today = today,
                    onRemove = viewModel::removeIngredient,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    state.recipe?.let { recipe ->
        RecipeScreen(
            recipe = recipe,
            onDismiss = viewModel::dismissRecipe,
            onRegenerate = viewModel::generateRecipe,
            onCooked = viewModel::markRecipeCooked,
        )
    }
}

@Composable
private fun ModelSetupScreen(status: ModelStatus, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier.size(112.dp).clip(CircleShape).background(Color(0xFF1F5135)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
        }
        Text(
            "Elige el modelo de IA",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "Chef de Sobras usa Gemma dentro de tu teléfono, sin internet. Elige el archivo .task " +
                "que descargaste, por ejemplo gemma3-1b-it-int4.task. Se copia una sola vez dentro de la app.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (status is ModelStatus.Importing) {
            val progress = status.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("Copiando ${status.fileName}… ${(progress * 100).toInt()} %", textAlign = TextAlign.Center)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Copiando ${status.fileName}…", textAlign = TextAlign.Center)
            }
            Text(
                "Cuando termine puedes borrar el archivo original para liberar espacio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Button(onClick = onChoose, modifier = Modifier.heightIn(min = 52.dp)) {
                Text("Elegir archivo del modelo")
            }
        }
    }
}

@Composable
private fun ModelStatusBanner(
    status: ModelStatus,
    canChange: Boolean,
    onRetry: () -> Unit,
    onChoose: () -> Unit,
) {
    when (status) {
        is ModelStatus.Ready -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Modelo: ${status.modelName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onChoose, enabled = canChange) { Text("Cambiar") }
        }
        ModelStatus.Loading -> Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Cargando el modelo Gemma…", style = MaterialTheme.typography.bodyMedium)
        }
        is ModelStatus.Error -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(status.message, color = MaterialTheme.colorScheme.onErrorContainer)
                Row {
                    TextButton(onClick = onRetry) { Text("Reintentar") }
                    TextButton(onClick = onChoose) { Text("Elegir otro archivo") }
                }
            }
        }
        // Estos dos estados se muestran a pantalla completa con ModelSetupScreen.
        ModelStatus.NotSelected, is ModelStatus.Importing -> Unit
    }
}

@Composable
private fun IngredientForm(onAdd: (String, String) -> Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("") }

    fun submit() {
        if (onAdd(name, days)) {
            name = ""
            days = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Ingrediente") },
            placeholder = { Text("Ej: Salmón, Arroz") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = days,
                onValueChange = { input -> days = input.filter(Char::isDigit).take(3) },
                label = { Text("Días para caducar") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { submit() }, modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Agregar")
            }
        }
    }
}

@Composable
private fun PreferenceChips(selected: Set<RecipePreference>, onToggle: (RecipePreference) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RecipePreference.entries.forEach { preference ->
            val isSelected = preference in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(preference) },
                label = { Text(preference.label) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
            )
        }
    }
}

@Composable
private fun PantrySummary(total: Int, urgent: Int, expired: Int) {
    val parts = buildList {
        add(if (total == 1) "1 ingrediente" else "$total ingredientes")
        if (urgent > 0) add(if (urgent == 1) "1 urgente" else "$urgent urgentes")
        if (expired > 0) add(if (expired == 1) "1 caducado" else "$expired caducados")
    }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IngredientList(
    ingredients: List<Ingredient>,
    today: Long,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ingredients.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Agrega lo que tienes en la nevera para empezar.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // Deja espacio para que el botón flotante no tape el último elemento.
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(ingredients, key = { it.id }) { ingredient ->
            IngredientRow(ingredient, today, onRemove = { onRemove(ingredient.id) })
        }
    }
}

@Composable
private fun IngredientRow(ingredient: Ingredient, today: Long, onRemove: () -> Unit) {
    val days = ingredient.daysLeft(today)
    val expired = days < 0
    val urgent = days in 0..ChefViewModel.URGENT_DAYS
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        expired -> colors.errorContainer to colors.onErrorContainer
        urgent -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    val date = LocalDate.ofEpochDay(ingredient.expiryEpochDay).format(dateFormatter)
    Card(colors = CardDefaults.cardColors(containerColor = container, contentColor = content)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ingredient.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        days < -1 -> "Caducó hace ${-days} días ($date)"
                        days == -1 -> "Caducó ayer ($date)"
                        days == 0 -> "Caduca hoy"
                        days == 1 -> "Caduca mañana"
                        else -> "Caduca en $days días ($date)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (urgent || expired) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (urgent || expired) {
                Text(
                    if (expired) "Caducado" else "Urgente",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar ${ingredient.name}")
            }
        }
    }
}

@Composable
private fun RecipeScreen(
    recipe: RecipeState,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit,
    onCooked: () -> Unit,
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    // Mientras el modelo escribe, la vista sigue el texto hacia abajo.
    LaunchedEffect(recipe.text.length) {
        if (recipe.isGenerating) scroll.scrollTo(scroll.maxValue)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !recipe.isGenerating,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Tu receta",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, enabled = !recipe.isGenerating) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
                if (recipe.isGenerating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = recipe.text.ifEmpty { "El chef está pensando tu receta…" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (recipe.text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scroll),
                )
                if (!recipe.isGenerating) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, recipe.text)
                                context.startActivity(Intent.createChooser(send, "Compartir receta"))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Compartir")
                        }
                        OutlinedButton(onClick = onRegenerate, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Otra receta")
                        }
                    }
                    Button(onClick = onCooked, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ya lo cociné: quitar ingredientes usados")
                    }
                }
            }
        }
    }
}
