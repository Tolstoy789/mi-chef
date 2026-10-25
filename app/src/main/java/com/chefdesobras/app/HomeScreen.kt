package com.chefdesobras.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val expiryFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("es"))

private val quickIngredients = listOf("Huevos", "Arroz", "Tomate", "Cebolla", "Pollo", "Pasta", "Papas", "Queso", "Zanahoria", "Leche")

@Composable
fun HomeScreen(
    state: ChefUiState,
    onPickModel: () -> Unit,
    onRetryModel: () -> Unit,
    onAddIngredient: (String, String) -> Boolean,
    onRemoveIngredient: (String) -> Unit,
    onClearExpired: () -> Unit,
    onTogglePreference: (RecipePreference) -> Unit,
    onGenerate: () -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val status = state.modelStatus
    if (status is ModelStatus.NotSelected || status is ModelStatus.Importing) {
        ModelSetupScreen(status, onPickModel)
        return
    }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val today = state.todayEpochDay
    val usableCount = state.ingredients.count { !it.isExpired(today) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        HomeTopBar(state.favorites.size, onOpenFavorites)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (status is ModelStatus.Loading || status is ModelStatus.Error) {
                ModelStatusCard(status, onPickModel, onRetryModel)
            }
            AddIngredientCard(
                modelReady = status is ModelStatus.Ready,
                existing = state.ingredients,
                onPickModel = onPickModel,
                onAdd = onAddIngredient,
            )
            PreferencesCard(state.preferences, onTogglePreference)
            PantrySummary(state.ingredients, today, onClearExpired)
            PantryList(state.ingredients, today, onRemoveIngredient)
        }
        if (!imeVisible) {
            BottomBar {
                ChunkyButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                    Icon(ChefIcons.Sparkles, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("Crear receta con IA", style = chefStyle(19f, FontWeight.Black, Color.White, lineHeight = 1.2f))
                    if (usableCount > 0) {
                        Pill(
                            text = if (usableCount == 1) "1 ingr." else "$usableCount ingr.",
                            background = Color.White,
                            contentColor = ChefColors.OrangeInk,
                        )
                    }
                }
            }
        }
    }
}

/** Barra inferior fija, con la línea de arriba y el espacio de la barra de navegación. */
@Composable
fun BottomBar(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChefColors.Bg)
            .drawBehind {
                drawLine(ChefColors.CardBorder, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
    ) {
        content()
    }
}

@Composable
private fun HomeTopBar(favoritesCount: Int, onOpenFavorites: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChefMascot(size = 46.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text("¡Hola! Soy Chefcito", style = chefStyle(20f, FontWeight.Black, lineHeight = 1.2f))
            Text("¿Qué cocinamos hoy?", style = chefStyle(13f, FontWeight.Bold, ChefColors.Muted))
        }
        Box {
            RoundIconButton(
                icon = if (favoritesCount > 0) ChefIcons.HeartFilled else ChefIcons.Heart,
                label = "Mis recetas favoritas",
                onClick = onOpenFavorites,
                tint = ChefColors.Cta,
            )
            if (favoritesCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-3).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(ChefColors.GreenBright)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (favoritesCount > 9) "9+" else "$favoritesCount",
                        style = chefStyle(9.5f, FontWeight.Black, Color.White, lineHeight = 1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStatusCard(status: ModelStatus, onPickModel: () -> Unit, onRetry: () -> Unit) {
    if (status is ModelStatus.Error) {
        ChefCard(color = ChefColors.DangerSoft, borderColor = ChefColors.DangerBorder, shape = BentoShape, elevation = 2.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconTile(ChefIcons.Alert, Color.White, ChefColors.Danger)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chefcito no pudo arrancar", style = chefStyle(15f, FontWeight.Black))
                        Text(status.message, style = chefStyle(13f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.4f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton(onClick = onRetry, modifier = Modifier.weight(1f), minHeight = 46.dp, corner = 16.dp) {
                        Icon(ChefIcons.Refresh, contentDescription = null, tint = ChefColors.Text, modifier = Modifier.size(17.dp))
                        Text("Reintentar", style = chefStyle(14f, FontWeight.ExtraBold))
                    }
                    SoftButton(onClick = onPickModel, modifier = Modifier.weight(1f), minHeight = 46.dp, corner = 16.dp) {
                        Icon(ChefIcons.FolderOpen, contentDescription = null, tint = ChefColors.OrangeText, modifier = Modifier.size(17.dp))
                        Text("Otro archivo", style = chefStyle(14f, FontWeight.ExtraBold))
                    }
                }
            }
        }
    } else {
        ChefCard(shape = BentoShape, elevation = 4.dp) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = ChefColors.Orange,
                        trackColor = ChefColors.OrangeSoft,
                        strokeWidth = 3.dp,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Preparando a Chefcito…", style = chefStyle(15f, FontWeight.Black))
                    Text(
                        "Cargando el modelo de IA en tu teléfono. Puedes ir anotando ingredientes.",
                        style = chefStyle(12.5f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddIngredientCard(
    modelReady: Boolean,
    existing: List<Ingredient>,
    onPickModel: () -> Unit,
    onAdd: (String, String) -> Boolean,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("") }
    val submit: () -> Unit = {
        if (onAdd(name, days)) {
            name = ""
            days = ""
        }
    }
    ChefCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChefColors.Peach)
                .padding(18.dp),
        ) {
            Column(
                modifier = Modifier.padding(end = 72.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (modelReady) {
                    Pill("Chefcito listo", Color.White, ChefColors.GreenInk, icon = ChefIcons.CircleCheck)
                }
                Text(
                    "¿Qué hay en tu cocina?",
                    style = chefStyle(22f, FontWeight.Black, lineHeight = 1.18f, letterSpacing = -0.3f),
                )
                Text(
                    "Anota lo que tienes y Chefcito crea una receta con eso.",
                    style = chefStyle(13.5f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.45f),
                )
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).bob()) {
                IconTile(ChefIcons.Basket, Color.White, ChefColors.OrangeText, size = 58.dp, iconSize = 28.dp, corner = 20.dp)
            }
            Icon(
                ChefIcons.Carrot,
                contentDescription = null,
                tint = ChefColors.Orange,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp)
                    .size(26.dp)
                    .graphicsLayer { rotationZ = -18f },
            )
        }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChefTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                placeholder = "Ingrediente (ej. pollo, arroz)",
                icon = ChefIcons.Utensils,
                onDone = submit,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ChefTextField(
                    value = days,
                    onValueChange = { value -> days = value.filter { it.isDigit() }.take(3) },
                    placeholder = "Días para caducar (opcional)",
                    icon = ChefIcons.CalendarClock,
                    keyboardType = KeyboardType.Number,
                    onDone = submit,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .padding(bottom = 3.dp)
                        .size(56.dp)
                        .pressable(label = "Agregar ingrediente", onClick = submit)
                        .drawBehind {
                            val radius = 16.dp.toPx()
                            drawRoundRect(
                                ChefColors.OrangeDeep,
                                topLeft = Offset(0f, 3.dp.toPx()),
                                size = size,
                                cornerRadius = CornerRadius(radius, radius),
                            )
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(ChefColors.Orange),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ChefIcons.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            val suggestions = quickIngredients.filter { quick -> existing.none { it.name.equals(quick, ignoreCase = true) } }
            if (suggestions.isNotEmpty()) {
                Text("Agregar rápido", style = chefStyle(12f, FontWeight.ExtraBold, ChefColors.Muted))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { quick ->
                        Row(
                            modifier = Modifier
                                .pressable(label = "Agregar $quick", onClick = { onAdd(quick, "") })
                                .clip(CircleShape)
                                .background(ChefColors.Bg)
                                .border(1.dp, ChefColors.CardBorder, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(ChefIcons.Plus, contentDescription = null, tint = ChefColors.OrangeText, modifier = Modifier.size(13.dp))
                            Text(quick, style = chefStyle(13f, FontWeight.ExtraBold, lineHeight = 1.2f))
                        }
                    }
                }
            }
            if (modelReady) {
                Row(
                    modifier = Modifier
                        .pressable(label = "Cambiar el modelo de IA", onClick = onPickModel)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ChefIcons.FolderOpen, contentDescription = null, tint = ChefColors.Muted, modifier = Modifier.size(14.dp))
                    Text("Cambiar el modelo de IA", style = chefStyle(12f, FontWeight.ExtraBold, ChefColors.Muted))
                }
            }
        }
    }
}

@Composable
fun ChefTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        placeholder = {
            Text(
                placeholder,
                style = chefStyle(14.5f, FontWeight.SemiBold, ChefColors.Muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = if (icon != null) {
            { Icon(icon, contentDescription = null, tint = ChefColors.OrangeText, modifier = Modifier.size(20.dp)) }
        } else {
            null
        },
        singleLine = true,
        textStyle = chefStyle(16f, FontWeight.Bold),
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = keyboardType,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ChefColors.Orange,
            unfocusedBorderColor = ChefColors.ButtonBorder,
            focusedContainerColor = ChefColors.Bubble,
            unfocusedContainerColor = Color.White,
            cursorColor = ChefColors.Cta,
            focusedTextColor = ChefColors.Text,
            unfocusedTextColor = ChefColors.Text,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferencesCard(selected: Set<RecipePreference>, onToggle: (RecipePreference) -> Unit) {
    ChefCard(shape = BentoShape, elevation = 6.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle("Preferencias", "Chefcito las tendrá en cuenta")
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecipePreference.entries.forEach { preference ->
                    val isOn = preference in selected
                    val icon = when (preference) {
                        RecipePreference.QUICK -> ChefIcons.Zap
                        RecipePreference.VEGETARIAN -> ChefIcons.Leaf
                        RecipePreference.GLUTEN_FREE -> ChefIcons.WheatOff
                    }
                    Row(
                        modifier = Modifier
                            .pressable(label = preference.label, role = androidx.compose.ui.semantics.Role.Checkbox, onClick = { onToggle(preference) })
                            .clip(CircleShape)
                            .background(if (isOn) ChefColors.OrangeSoft else ChefColors.Bg)
                            .border(if (isOn) 1.5.dp else 1.dp, if (isOn) ChefColors.Orange else ChefColors.CardBorder, CircleShape)
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isOn) ChefIcons.Check else icon,
                            contentDescription = null,
                            tint = if (isOn) ChefColors.OrangeText else ChefColors.Green,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            preference.label,
                            style = chefStyle(14f, FontWeight.ExtraBold, if (isOn) ChefColors.OrangeInk else ChefColors.Text, lineHeight = 1.2f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PantrySummary(ingredients: List<Ingredient>, today: Long, onClearExpired: () -> Unit) {
    val urgent = ingredients.count { it.isUrgent(today) }
    val expired = ingredients.count { it.isExpired(today) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryTile(
            icon = ChefIcons.Refrigerator,
            tileColor = ChefColors.GreenSoft,
            tint = ChefColors.Green,
            value = ingredients.size,
            label = "En tu despensa",
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            icon = ChefIcons.CalendarClock,
            tileColor = ChefColors.OrangeSoft,
            tint = ChefColors.OrangeText,
            value = urgent,
            label = "Vencen pronto",
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            icon = ChefIcons.Trash,
            tileColor = if (expired > 0) ChefColors.DangerSoft else ChefColors.Bubble,
            tint = if (expired > 0) ChefColors.Danger else ChefColors.Muted,
            value = expired,
            label = if (expired > 0) "Caducados · quitar" else "Caducados",
            modifier = Modifier.weight(1f),
            onClick = if (expired > 0) onClearExpired else null,
        )
    }
}

@Composable
private fun SummaryTile(
    icon: ImageVector,
    tileColor: Color,
    tint: Color,
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.pressable(label = label, onClick = onClick) else Modifier
    ChefCard(modifier = modifier.then(clickModifier), shape = BentoShape, elevation = 4.dp) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconTile(icon, tileColor, tint, size = 32.dp, iconSize = 17.dp, corner = 11.dp)
            Text("$value", style = chefStyle(22f, FontWeight.Black, lineHeight = 1.1f, tabular = true))
            Text(label, style = chefStyle(11.5f, FontWeight.ExtraBold, ChefColors.Muted, lineHeight = 1.25f))
        }
    }
}

@Composable
private fun PantryList(ingredients: List<Ingredient>, today: Long, onRemove: (String) -> Unit) {
    ChefCard {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 10.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SectionTitle(
                    title = "Tu despensa",
                    subtitle = "Los que caducan antes van primero",
                    modifier = Modifier.weight(1f),
                )
                Pill("${ingredients.size}", ChefColors.GreenSoft, ChefColors.GreenInk)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (ingredients.isEmpty()) {
                EmptyState(
                    icon = ChefIcons.Basket,
                    title = "Tu despensa está vacía",
                    body = "Agrega arriba lo que tienes en casa y Chefcito te dirá qué cocinar.",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ingredients.forEach { ingredient ->
                        IngredientRow(ingredient, today, onRemove)
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(ingredient: Ingredient, today: Long, onRemove: (String) -> Unit) {
    val days = ingredient.daysLeft(today)
    val expired = ingredient.isExpired(today)
    val urgent = ingredient.isUrgent(today)
    val (tileColor, letterColor) = when {
        expired -> ChefColors.DangerSoft to ChefColors.Danger
        urgent -> ChefColors.OrangeSoft to ChefColors.OrangeText
        days != null -> ChefColors.GreenSoft to ChefColors.GreenInk
        else -> ChefColors.Bubble to ChefColors.Muted
    }
    val label = when {
        days == null -> "Sin fecha"
        days < -1 -> "Caducó hace ${-days} días"
        days == -1 -> "Caducó ayer"
        days == 0 -> "Caduca hoy"
        days == 1 -> "Caduca mañana"
        days <= ChefViewModel.URGENT_DAYS -> "Caduca en $days días"
        else -> "Caduca el " + LocalDate.ofEpochDay(today + days).format(expiryFormatter)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (expired) ChefColors.DangerSoft.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tileColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                ingredient.name.take(1).uppercase(),
                style = chefStyle(15f, FontWeight.Black, letterColor, lineHeight = 1f),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                ingredient.name,
                style = chefStyle(15f, FontWeight.ExtraBold, lineHeight = 1.25f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(label, style = chefStyle(12f, FontWeight.Bold, if (days == null) ChefColors.Muted else letterColor))
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .pressable(label = "Quitar ${ingredient.name}", onClick = { onRemove(ingredient.id) }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ChefIcons.Trash, contentDescription = null, tint = ChefColors.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

/** Primera vez: explicar qué modelo hace falta y dejar elegir el archivo descargado. */
@Composable
fun ModelSetupScreen(status: ModelStatus, onPickModel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ChefCard(modifier = Modifier.fillMaxWidth()) {
                RecipeIllustration(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp),
                )
            }
            ChefMascot(
                size = 72.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 36.dp),
            )
        }
        Spacer(modifier = Modifier.height(50.dp))
        Text(
            "¡Hola! Soy Chefcito",
            style = chefStyle(28f, FontWeight.Black, lineHeight = 1.18f, letterSpacing = -0.3f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Creo recetas con lo que tienes en casa, sin internet. Para eso necesito un modelo de IA guardado en tu teléfono.",
            style = chefStyle(15f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.5f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        ChefCard(modifier = Modifier.fillMaxWidth(), shape = BentoShape, elevation = 6.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SetupStep(1, "Descarga un modelo Gemma en formato .task, por ejemplo Gemma 3 1B.")
                SetupStep(2, "Toca «Elegir archivo» y búscalo en tu carpeta de Descargas.")
                SetupStep(3, "Lo copio una sola vez y listo: funciona sin conexión.")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        if (status is ModelStatus.Importing) {
            ChefCard(modifier = Modifier.fillMaxWidth(), shape = BentoShape, elevation = 6.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(ChefIcons.FolderOpen, ChefColors.OrangeSoft, ChefColors.OrangeText)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                status.fileName,
                                style = chefStyle(14f, FontWeight.ExtraBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val progress = status.progress
                            Text(
                                if (progress != null) "Copiando… ${(progress * 100).toInt()} %" else "Copiando…",
                                style = chefStyle(12.5f, FontWeight.Bold, ChefColors.Muted),
                            )
                        }
                        if (status.progress == null) ThinkingDots()
                    }
                    ProgressTrack(
                        fraction = status.progress ?: 0f,
                        color = ChefColors.Orange,
                        trackColor = ChefColors.TimerTrack,
                        height = 8.dp,
                    )
                    Text(
                        "No cierres la app: el archivo es grande y puede tardar un par de minutos.",
                        style = chefStyle(12f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.4f),
                    )
                }
            }
        } else {
            ChunkyButton(onClick = onPickModel, modifier = Modifier.fillMaxWidth()) {
                Icon(ChefIcons.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text("Elegir archivo del modelo", style = chefStyle(18f, FontWeight.Black, Color.White, lineHeight = 1.2f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Busca un archivo que termine en .task (por ejemplo gemma3-1b-it-int4.task).",
                style = chefStyle(12.5f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.4f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.width(1.dp).height(12.dp))
    }
}

@Composable
private fun SetupStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(ChefColors.OrangeSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = chefStyle(14f, FontWeight.Black, ChefColors.OrangeInk, lineHeight = 1f))
        }
        Text(text, style = chefStyle(14f, FontWeight.SemiBold, lineHeight = 1.45f), modifier = Modifier.weight(1f))
    }
}
