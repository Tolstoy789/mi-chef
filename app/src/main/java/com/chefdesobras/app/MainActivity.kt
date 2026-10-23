package com.chefdesobras.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefScreen(viewModel: ChefViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chef de Sobras") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val enabled = state.modelStatus == ModelStatus.Ready && !state.isGenerating
            ExtendedFloatingActionButton(
                onClick = { if (enabled) viewModel.generateRecipe() },
                containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                icon = {
                    if (state.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                },
                text = { Text(if (state.isGenerating) "Generando…" else "Generar Receta") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            ModelStatusBanner(state.modelStatus, onRetry = viewModel::loadModel)
            IngredientForm(onAdd = viewModel::addIngredient)
            Spacer(Modifier.padding(top = 8.dp))
            IngredientList(
                ingredients = state.ingredients,
                onRemove = viewModel::removeIngredient,
                modifier = Modifier.weight(1f),
            )
        }

        if (state.isGenerating) {
            GeneratingOverlay()
        }
    }

    state.recipe?.let { recipe ->
        RecipeDialog(recipe = recipe, onDismiss = viewModel::dismissRecipe)
    }
}

@Composable
private fun ModelStatusBanner(status: ModelStatus, onRetry: () -> Unit) {
    when (status) {
        ModelStatus.Ready -> Unit
        ModelStatus.Loading -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Cargando el modelo Gemma…", style = MaterialTheme.typography.bodyMedium)
        }
        is ModelStatus.Error -> Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(status.message, color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
        }
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
private fun IngredientList(
    ingredients: List<Ingredient>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ingredients.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Agrega ingredientes para empezar.",
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
            IngredientRow(ingredient, onRemove = { onRemove(ingredient.id) })
        }
    }
}

@Composable
private fun IngredientRow(ingredient: Ingredient, onRemove: () -> Unit) {
    val urgent = ingredient.daysToExpire <= ChefViewModel.URGENT_DAYS
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (urgent) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ingredient.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when (ingredient.daysToExpire) {
                        0 -> "Caduca hoy"
                        1 -> "Caduca en 1 día"
                        else -> "Caduca en ${ingredient.daysToExpire} días"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (urgent) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (urgent) {
                Text(
                    "Urgente",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar ${ingredient.name}")
            }
        }
    }
}

@Composable
private fun GeneratingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.padding(top = 12.dp))
                Text("El chef está pensando tu receta…")
            }
        }
    }
}

@Composable
private fun RecipeDialog(recipe: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tu receta") },
        text = {
            Text(
                recipe,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
