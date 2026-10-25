package com.chefdesobras.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Forma de burbuja de diálogo: la esquina de arriba a la izquierda apunta a la mascota. */
val BubbleShape = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)

/** Vista A del bosquejo: la receta generada por la IA. */
@Composable
fun RecipeScreen(
    recipe: Recipe,
    portions: Int,
    checked: Set<Int>,
    pantryMatches: Set<Int>,
    isFavorite: Boolean,
    fromFavorites: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onChangePortions: (Int) -> Unit,
    onToggleChecked: (Int) -> Unit,
    onStartCooking: () -> Unit,
    onSharePdf: () -> Unit,
    onSavePdf: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundIconButton(ChefIcons.ArrowLeft, "Volver", onBack)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AiBadge(if (fromFavorites) "De tus favoritas" else "Generado por Chefcito IA")
            }
            RoundIconButton(
                icon = if (isFavorite) ChefIcons.HeartFilled else ChefIcons.Heart,
                label = if (isFavorite) "Quitar de favoritas" else "Guardar en favoritas",
                onClick = onToggleFavorite,
                tint = ChefColors.Cta,
                background = if (isFavorite) ChefColors.OrangeSoft else ChefColors.Card,
                borderColor = if (isFavorite) ChefColors.FavBorder else ChefColors.CardBorder,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroCard(recipe, pantryMatches.size)
            BentoGrid(recipe, portions, onChangePortions)
            IngredientsCard(recipe, portions, checked, pantryMatches, onToggleChecked)
            StepsCard(recipe)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(ChefIcons.Share, "Compartir PDF", onSharePdf, Modifier.weight(1f))
                ActionTile(ChefIcons.Download, "Guardar PDF", onSavePdf, Modifier.weight(1f))
                if (!fromFavorites) {
                    ActionTile(ChefIcons.Refresh, "Otra receta", onRegenerate, Modifier.weight(1f))
                }
            }
        }
        BottomBar {
            ChunkyButton(onClick = onStartCooking, modifier = Modifier.fillMaxWidth()) {
                Icon(ChefIcons.ChefHat, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(
                    "Comenzar a cocinar",
                    style = chefStyle(19f, FontWeight.Black, Color.White, lineHeight = 1.2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Pill("Modo guiado", Color.White, ChefColors.OrangeInk, horizontalPadding = 9.dp, verticalPadding = 4.dp)
            }
        }
    }
}

@Composable
fun AiBadge(text: String) {
    Row(
        modifier = Modifier
            .glow(ChefColors.Orange)
            .clip(CircleShape)
            .background(ChefColors.OrangeSoft)
            .border(1.dp, ChefColors.OrangeSoftBorder, CircleShape)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TwinkleIcon(ChefIcons.Sparkles, ChefColors.OrangeInk, 15.dp)
        Text(
            text,
            style = chefStyle(12f, FontWeight.ExtraBold, ChefColors.OrangeInk, lineHeight = 1.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroCard(recipe: Recipe, matchCount: Int) {
    ChefCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp),
        ) {
            RecipeIllustration(modifier = Modifier.matchParentSize())
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 12.dp, end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val kcal = recipe.kcalPerPortion
                if (kcal != null) {
                    HeroPill("$kcal kcal / porción", ChefColors.Text, ChefIcons.Flame)
                }
                if (matchCount > 0) {
                    HeroPill(if (matchCount == 1) "1 de tu despensa" else "$matchCount de tu despensa", ChefColors.GreenInk)
                } else {
                    HeroPill("${recipe.ingredients.size} ingredientes", ChefColors.GreenInk)
                }
            }
        }
        Column(modifier = Modifier.padding(18.dp)) {
            Text(recipe.title, style = chefStyle(25f, FontWeight.Black, lineHeight = 1.18f, letterSpacing = -0.3f))
            Text(
                recipe.description.ifBlank { "Una receta pensada con lo que tienes en tu despensa." },
                style = chefStyle(14f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.5f),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ChefMascot(size = 48.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .bob()
                        .clip(BubbleShape)
                        .background(ChefColors.Bubble)
                        .border(1.dp, ChefColors.BubbleBorder, BubbleShape)
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                ) {
                    Text(
                        "TIP PRO DE CHEFCITO",
                        style = chefStyle(11f, FontWeight.Black, ChefColors.OrangeInk, letterSpacing = 0.6f),
                    )
                    Text(
                        recipe.tip,
                        style = chefStyle(13.5f, FontWeight.SemiBold, lineHeight = 1.45f),
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPill(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .warmShadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ChefColors.OrangeText, modifier = Modifier.size(13.dp))
        }
        Text(text, style = chefStyle(12f, FontWeight.ExtraBold, color, lineHeight = 1.2f), maxLines = 1)
    }
}

@Composable
private fun BentoGrid(recipe: Recipe, portions: Int, onChangePortions: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PortionsCard(
            portions = portions,
            onChange = onChangePortions,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                icon = ChefIcons.Clock,
                tileColor = ChefColors.OrangeSoft,
                tint = ChefColors.OrangeText,
                label = "Tiempo total",
                value = recipe.totalMinutes?.let { minutesLabel(it) } ?: "Al gusto",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            StatCard(
                icon = ChefIcons.Flame,
                tileColor = ChefColors.GreenSoft,
                tint = ChefColors.Green,
                label = "Dificultad",
                value = recipe.difficulty,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PortionsCard(portions: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    ChefCard(modifier = modifier, shape = BentoShape, elevation = 6.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTile(ChefIcons.Users, ChefColors.GreenSoft, ChefColors.Green, size = 32.dp, iconSize = 17.dp)
                Text("Porciones", style = chefStyle(12f, FontWeight.ExtraBold, ChefColors.Muted))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = portions,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically { height -> if (up) height else -height } + fadeIn()) togetherWith
                            (slideOutVertically { height -> if (up) -height else height } + fadeOut())
                    },
                    label = "portions",
                ) { value ->
                    Text("$value", style = chefStyle(44f, FontWeight.Black, lineHeight = 1f, tabular = true))
                }
                Text(
                    portionsLabel(portions),
                    style = chefStyle(12f, FontWeight.Bold, ChefColors.Muted),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StepperButton(
                    icon = ChefIcons.Minus,
                    label = "Quitar una porción",
                    primary = false,
                    enabled = portions > ChefViewModel.MIN_PORTIONS,
                    onClick = { onChange(-1) },
                )
                StepperButton(
                    icon = ChefIcons.Plus,
                    label = "Agregar una porción",
                    primary = true,
                    enabled = portions < ChefViewModel.MAX_PORTIONS,
                    onClick = { onChange(1) },
                )
            }
        }
    }
}

@Composable
private fun StepperButton(icon: ImageVector, label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val face = if (primary) {
        Modifier
            .drawBehind {
                val radius = 14.dp.toPx()
                drawRoundRect(
                    ChefColors.OrangeDeep,
                    topLeft = Offset(0f, 3.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            .clip(shape)
            .background(ChefColors.Orange)
    } else {
        Modifier
            .clip(shape)
            .background(ChefColors.Bubble)
            .border(1.dp, ChefColors.CardBorder, shape)
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressable(enabled = enabled, label = label, onClick = onClick)
            .then(face),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (primary) Color.White else ChefColors.OrangeText, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    tileColor: Color,
    tint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ChefCard(modifier = modifier, shape = BentoShape, elevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconTile(icon, tileColor, tint)
            Column {
                Text(label, style = chefStyle(12f, FontWeight.ExtraBold, ChefColors.Muted))
                Text(
                    value,
                    style = chefStyle(19f, FontWeight.Black, lineHeight = 1.2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun IngredientsCard(
    recipe: Recipe,
    portions: Int,
    checked: Set<Int>,
    pantryMatches: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    val total = recipe.ingredients.size
    val checkedCount = checked.count { it in recipe.ingredients.indices }
    ChefCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 10.dp)) {
            Row(modifier = Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.Top) {
                SectionTitle(
                    title = "Ingredientes",
                    subtitle = "Para $portions ${portionsLabel(portions)} · marca lo que ya tienes",
                    modifier = Modifier.weight(1f),
                )
                Pill("$checkedCount/$total", ChefColors.GreenSoft, ChefColors.GreenInk)
            }
            ProgressTrack(
                fraction = if (total == 0) 0f else checkedCount / total.toFloat(),
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                recipe.ingredients.forEachIndexed { index, ingredient ->
                    IngredientCheckRow(
                        name = ingredient.name,
                        quantity = ingredient.quantityFor(portions, recipe.basePortions),
                        isChecked = index in checked,
                        inPantry = index in pantryMatches,
                        onClick = { onToggle(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientCheckRow(
    name: String,
    quantity: String,
    isChecked: Boolean,
    inPantry: Boolean,
    onClick: () -> Unit,
) {
    val rowColor by animateColorAsState(if (isChecked) ChefColors.GreenRow else Color.Transparent, label = "row")
    val boxColor by animateColorAsState(if (isChecked) ChefColors.Green else Color.White, label = "box")
    val boxBorder by animateColorAsState(if (isChecked) ChefColors.Green else ChefColors.CheckBorder, label = "border")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(label = name, role = Role.Checkbox, pressedScale = 0.98f, onClick = onClick)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(rowColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(boxColor)
                .border(2.dp, boxBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isChecked) {
                Icon(ChefIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                name,
                style = chefStyle(15f, FontWeight.Bold, if (isChecked) ChefColors.Muted else ChefColors.Text, lineHeight = 1.3f),
                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (inPantry) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ChefIcons.Sparkle, contentDescription = null, tint = ChefColors.GreenInk, modifier = Modifier.size(11.dp))
                    Text("Detectado en tu despensa", style = chefStyle(11f, FontWeight.ExtraBold, ChefColors.GreenInk, lineHeight = 1.25f))
                }
            }
        }
        if (quantity.isNotBlank()) {
            Text(
                quantity,
                style = chefStyle(14f, FontWeight.ExtraBold, lineHeight = 1.3f, tabular = true),
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.widthIn(max = 130.dp),
            )
        }
    }
}

@Composable
private fun StepsCard(recipe: Recipe) {
    val timed = recipe.steps.count { it.timerSeconds > 0 }
    ChefCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionTitle(
                title = "Preparación",
                subtitle = buildString {
                    append(if (recipe.steps.size == 1) "1 paso" else "${recipe.steps.size} pasos")
                    if (timed > 0) append(" · $timed con temporizador")
                },
            )
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                recipe.steps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(ChefColors.OrangeSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${index + 1}", style = chefStyle(13f, FontWeight.Black, ChefColors.OrangeInk, lineHeight = 1f))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(step.title, style = chefStyle(15f, FontWeight.ExtraBold, lineHeight = 1.3f))
                            Text(step.body, style = chefStyle(14f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.5f))
                            if (step.timerSeconds > 0) {
                                Pill(
                                    text = timerLabel(step.timerSeconds),
                                    background = ChefColors.Bubble,
                                    contentColor = ChefColors.OrangeText,
                                    icon = ChefIcons.Timer,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ChefCard(
        modifier = modifier.pressable(label = label, onClick = onClick),
        shape = BentoShape,
        elevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconTile(icon, ChefColors.OrangeSoft, ChefColors.OrangeText)
            Text(
                label,
                style = chefStyle(12.5f, FontWeight.ExtraBold, lineHeight = 1.25f),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** «4 min», «45 s». */
fun timerLabel(seconds: Int): String =
    if (seconds < 60) "$seconds s" else minutesLabel((seconds / 60f).roundToInt())
