package com.chefdesobras.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Vista C del bosquejo: la receta está lista. */
@Composable
fun DoneScreen(
    recipe: Recipe,
    portions: Int,
    usedCount: Int,
    onBackToRecipe: () -> Unit,
    onRepeat: () -> Unit,
    onRemoveUsed: () -> Unit,
    onHome: () -> Unit,
) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .clip(CircleShape)
                    .background(ChefColors.GreenSoft),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(10.dp, CircleShape, clip = false, ambientColor = ChefColors.Green, spotColor = ChefColors.Green)
                        .clip(CircleShape)
                        .background(ChefColors.Green),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ChefIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            Text(
                "¡Buen provecho!",
                style = chefStyle(32f, FontWeight.Black, lineHeight = 1.2f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "Preparaste $portions ${portionsLabel(portions)} de ${recipe.title.replaceFirstChar { it.lowercase() }}. " +
                    "Chefcito está orgulloso de ti.",
                style = chefStyle(17f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            ChunkyButton(onClick = onBackToRecipe, modifier = Modifier.fillMaxWidth(), glow = false) {
                Text("Volver a la receta", style = chefStyle(19f, FontWeight.Black, Color.White, lineHeight = 1.2f))
            }
            SoftButton(onClick = onRepeat, modifier = Modifier.fillMaxWidth()) {
                Text("Repetir modo guiado", style = chefStyle(16f, FontWeight.ExtraBold, lineHeight = 1.2f))
            }
            if (usedCount > 0) {
                SoftButton(
                    onClick = onRemoveUsed,
                    modifier = Modifier.fillMaxWidth(),
                    background = ChefColors.GreenSoft,
                    borderColor = ChefColors.GreenSoftBorder,
                ) {
                    Icon(ChefIcons.CircleCheck, contentDescription = null, tint = ChefColors.Green, modifier = Modifier.size(18.dp))
                    Text(
                        if (usedCount == 1) "Quitar de mi despensa lo que usé (1)" else "Quitar de mi despensa lo que usé ($usedCount)",
                        style = chefStyle(14.5f, FontWeight.ExtraBold, ChefColors.GreenDeep, lineHeight = 1.25f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(
                "Ir al inicio",
                style = chefStyle(14f, FontWeight.ExtraBold, ChefColors.Muted),
                modifier = Modifier
                    .pressable(label = "Ir al inicio", onClick = onHome)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** Las recetas guardadas con el corazón. */
@Composable
fun FavoritesScreen(
    favorites: List<Recipe>,
    onBack: () -> Unit,
    onOpen: (Recipe) -> Unit,
    onRemove: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundIconButton(ChefIcons.ArrowLeft, "Volver", onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text("Mis favoritas", style = chefStyle(20f, FontWeight.Black, lineHeight = 1.2f))
                Text(
                    "Recetas para repetir sin volver a generarlas",
                    style = chefStyle(12.5f, FontWeight.Bold, ChefColors.Muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (favorites.isNotEmpty()) {
                Pill("${favorites.size}", ChefColors.OrangeSoft, ChefColors.OrangeInk)
            }
        }
        if (favorites.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    icon = ChefIcons.Heart,
                    title = "Aún no tienes favoritas",
                    body = "Toca el corazón de una receta para guardarla aquí y cocinarla cuando quieras.",
                )
                Spacer(modifier = Modifier.height(12.dp))
                SoftButton(onClick = onCreate) {
                    Icon(ChefIcons.Sparkles, contentDescription = null, tint = ChefColors.OrangeText, modifier = Modifier.size(18.dp))
                    Text("Crear una receta", style = chefStyle(15f, FontWeight.ExtraBold, lineHeight = 1.2f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(favorites, key = { it.id }) { recipe ->
                    FavoriteRow(recipe = recipe, onOpen = { onOpen(recipe) }, onRemove = { onRemove(recipe.id) })
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(recipe: Recipe, onOpen: () -> Unit, onRemove: () -> Unit) {
    val meta = listOfNotNull(
        recipe.totalMinutes?.let { minutesLabel(it) },
        recipe.difficulty,
        "${recipe.basePortions} ${portionsLabel(recipe.basePortions)}",
    ).joinToString(" · ")
    ChefCard(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(label = recipe.title, pressedScale = 0.98f, onClick = onOpen),
        shape = BentoShape,
        elevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ChefColors.Peach),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ChefIcons.Soup, contentDescription = null, tint = ChefColors.OrangeText, modifier = Modifier.size(30.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    recipe.title,
                    style = chefStyle(16f, FontWeight.Black, lineHeight = 1.25f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(meta, style = chefStyle(12.5f, FontWeight.Bold, ChefColors.Muted))
            }
            RoundIconButton(
                icon = ChefIcons.HeartFilled,
                label = "Quitar «${recipe.title}» de favoritas",
                onClick = onRemove,
                tint = ChefColors.Cta,
                background = ChefColors.OrangeSoft,
                borderColor = ChefColors.FavBorder,
                size = 40.dp,
                iconSize = 18.dp,
            )
        }
    }
}
