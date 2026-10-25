package com.chefdesobras.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class StageState { DONE, CURRENT, PENDING }

/** Mientras el modelo escribe: qué está haciendo Chefcito y el texto que va saliendo. */
@Composable
fun GeneratingScreen(generation: GenerationState, onCancel: () -> Unit, onRetry: () -> Unit) {
    val preview = remember(generation.text) { RecipeParser.preview(generation.text) }
    val error = generation.error
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundIconButton(ChefIcons.Close, if (error == null) "Cancelar" else "Volver", onCancel)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Mi Chefcito", style = chefStyle(12f, FontWeight.ExtraBold, ChefColors.Muted))
                Text(
                    if (error == null) "Creando tu receta" else "Algo salió mal",
                    style = chefStyle(18f, FontWeight.Black, lineHeight = 1.25f),
                )
            }
            Spacer(modifier = Modifier.size(44.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChefCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp),
                ) {
                    RecipeIllustration(modifier = Modifier.matchParentSize())
                    if (error == null) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 14.dp, bottom = 12.dp)
                                .warmShadow(3.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TwinkleIcon(ChefIcons.Sparkles, ChefColors.OrangeText, 14.dp)
                            Text("Chefcito está cocinando…", style = chefStyle(12f, FontWeight.ExtraBold, lineHeight = 1.2f))
                        }
                    }
                }
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        preview.title ?: if (error == null) "Pensando el plato perfecto…" else "No salió la receta",
                        style = chefStyle(23f, FontWeight.Black, lineHeight = 1.2f, letterSpacing = -0.3f),
                    )
                    val names = generation.ingredientNames
                    if (names.isNotEmpty()) {
                        val shown = names.take(5).joinToString(", ") { it.lowercase() }
                        val more = if (names.size > 5) " y ${names.size - 5} más" else ""
                        Text(
                            "Con $shown$more.",
                            style = chefStyle(14f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.5f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            if (error != null) {
                ChefCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BentoShape,
                    color = ChefColors.DangerSoft,
                    borderColor = ChefColors.DangerBorder,
                    elevation = 2.dp,
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(ChefIcons.Alert, Color.White, ChefColors.Danger)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chefcito se quedó sin ideas", style = chefStyle(15f, FontWeight.Black))
                            Text(error, style = chefStyle(13f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.4f))
                        }
                    }
                }
            } else {
                ChefCard(modifier = Modifier.fillMaxWidth(), shape = BentoShape, elevation = 6.dp) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (generation.queued) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ThinkingDots()
                                Text(
                                    "Terminando una respuesta anterior…",
                                    style = chefStyle(13f, FontWeight.Bold, ChefColors.Muted),
                                )
                            }
                        }
                        val stage = when {
                            preview.writingSteps -> 2
                            preview.title != null -> 1
                            else -> 0
                        }
                        StageRow(
                            label = "Elegir el plato",
                            detail = null,
                            state = if (stage > 0) StageState.DONE else StageState.CURRENT,
                        )
                        StageRow(
                            label = "Calcular los ingredientes",
                            detail = preview.ingredientCount.takeIf { it > 0 }?.let { "$it" },
                            state = when {
                                stage > 1 -> StageState.DONE
                                stage == 1 -> StageState.CURRENT
                                else -> StageState.PENDING
                            },
                        )
                        StageRow(
                            label = "Escribir los pasos",
                            detail = preview.stepCount.takeIf { it > 0 }?.let { "$it" },
                            state = if (stage == 2) StageState.CURRENT else StageState.PENDING,
                        )
                    }
                }
                if (generation.text.isNotBlank()) {
                    ChefCard(modifier = Modifier.fillMaxWidth(), shape = BentoShape, elevation = 4.dp, color = ChefColors.Bubble, borderColor = ChefColors.BubbleBorder) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "LO QUE VA ESCRIBIENDO",
                                style = chefStyle(11f, FontWeight.Black, ChefColors.OrangeInk, letterSpacing = 0.6f),
                            )
                            val text = generation.text.trim()
                            Text(
                                if (text.length > 420) "…" + text.takeLast(420) else text,
                                style = chefStyle(13f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.5f),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        if (error != null) {
            BottomBar {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton(onClick = onCancel, modifier = Modifier.weight(1f), minHeight = 60.dp) {
                        Text("Volver", style = chefStyle(16f, FontWeight.ExtraBold, lineHeight = 1.2f))
                    }
                    ChunkyButton(onClick = onRetry, modifier = Modifier.weight(1.7f)) {
                        Icon(ChefIcons.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Reintentar", style = chefStyle(19f, FontWeight.Black, Color.White, lineHeight = 1.2f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRow(label: String, detail: String?, state: StageState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when (state) {
                StageState.DONE -> Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(ChefColors.Green),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ChefIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                StageState.CURRENT -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = ChefColors.Orange,
                    trackColor = ChefColors.OrangeSoft,
                    strokeWidth = 3.dp,
                )
                StageState.PENDING -> Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(2.dp, ChefColors.CheckBorder, CircleShape),
                )
            }
        }
        Text(
            label,
            style = chefStyle(15f, FontWeight.ExtraBold, if (state == StageState.PENDING) ChefColors.Muted else ChefColors.Text),
            modifier = Modifier.weight(1f),
        )
        if (detail != null) {
            Pill(detail, ChefColors.GreenSoft, ChefColors.GreenInk)
        }
    }
}
