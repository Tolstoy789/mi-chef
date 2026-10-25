package com.chefdesobras.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

val CardShape = RoundedCornerShape(28.dp)
val BentoShape = RoundedCornerShape(24.dp)

/** Estilo de texto en Nunito con interlineado proporcional, como en el bosquejo. */
fun chefStyle(
    size: Float,
    weight: FontWeight,
    color: Color = ChefColors.Text,
    lineHeight: Float = 1.35f,
    letterSpacing: Float = 0f,
    tabular: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = Nunito,
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/** Sombra cálida y difusa de las tarjetas. */
fun Modifier.warmShadow(elevation: Dp, shape: Shape): Modifier =
    shadow(elevation = elevation, shape = shape, clip = false, ambientColor = ChefColors.Shadow, spotColor = ChefColors.Shadow)

/** Al tocar, el elemento se encoge un poco (scale .96), como los botones del bosquejo. */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    label: String? = null,
    role: Role = Role.Button,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) pressedScale else 1f, tween(140), label = "press")
    val semanticsModifier = if (label != null) Modifier.semantics { contentDescription = label } else Modifier
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (enabled) 1f else 0.5f
        }
        .then(semanticsModifier)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

@Composable
fun ChefCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    elevation: Dp = 8.dp,
    color: Color = ChefColors.Card,
    borderColor: Color = ChefColors.CardBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .warmShadow(elevation, shape)
            .clip(shape)
            .background(color)
            .border(1.dp, borderColor, shape),
        content = content,
    )
}

/** Botón redondo de 44 dp (volver, cerrar, favorito). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ChefColors.Text,
    background: Color = ChefColors.Card,
    borderColor: Color = ChefColors.CardBorder,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .pressable(enabled = enabled, label = label, onClick = onClick)
            .warmShadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** El botón naranja «con relieve»: una base más oscura asoma 4 dp por debajo. */
@Composable
fun ChunkyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = ChefColors.Cta,
    baseColor: Color = ChefColors.CtaShadow,
    corner: Dp = 20.dp,
    minHeight: Dp = 60.dp,
    depth: Dp = 4.dp,
    enabled: Boolean = true,
    glow: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val glowModifier = if (glow && enabled) {
        Modifier.shadow(14.dp, shape, clip = false, ambientColor = color, spotColor = color)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .padding(bottom = depth)
            .pressable(enabled = enabled, onClick = onClick, pressedScale = 0.97f)
            .then(glowModifier)
            .drawBehind {
                val radius = corner.toPx()
                drawRoundRect(
                    color = baseColor,
                    topLeft = Offset(0f, depth.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            .clip(shape)
            .background(color)
            .heightIn(min = minHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Botón blanco con borde, para las acciones secundarias. */
@Composable
fun SoftButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 52.dp,
    corner: Dp = 20.dp,
    enabled: Boolean = true,
    background: Color = ChefColors.Card,
    borderColor: Color = ChefColors.ButtonBorder,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Row(
        modifier = modifier
            .pressable(enabled = enabled, onClick = onClick)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .heightIn(min = minHeight)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun Pill(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fontSize: Float = 12f,
    weight: FontWeight = FontWeight.Black,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 5.dp,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size((fontSize + 1).dp))
        }
        Text(text, style = chefStyle(fontSize, weight, contentColor, lineHeight = 1.2f), maxLines = 1)
    }
}

/** Cuadradito redondeado con un icono, como los de las tarjetas bento. */
@Composable
fun IconTile(
    icon: ImageVector,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    corner: Dp = 12.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** La mascota: círculo naranja con gorro de chef y una insignia verde. */
@Composable
fun ChefMascot(modifier: Modifier = Modifier, size: Dp = 48.dp, showCheck: Boolean = true) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .shadow(8.dp, CircleShape, clip = false, ambientColor = ChefColors.Orange, spotColor = ChefColors.Orange)
                .clip(CircleShape)
                .background(ChefColors.Orange)
                .border(size * 0.0625f, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ChefIcons.ChefHat, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        }
        if (showCheck) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = size * 0.0625f, y = size * 0.0625f)
                    .size(size * 0.375f)
                    .clip(CircleShape)
                    .background(ChefColors.GreenBright)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ChefIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.21f))
            }
        }
    }
}

/** Icono que «titila» (gira y crece un poco), como las chispas del bosquejo. */
@Composable
fun TwinkleIcon(icon: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "twinkle")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "twinkleT",
    )
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = 18f * t
                scaleX = 1f + 0.18f * t
                scaleY = 1f + 0.18f * t
            },
    )
}

/** Sube y baja 3 dp, despacio. */
@Composable
fun Modifier.bob(): Modifier {
    val transition = rememberInfiniteTransition(label = "bob")
    val y by transition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bobY",
    )
    return graphicsLayer { translationY = y * density }
}

/** Halo que se expande y se apaga alrededor de una píldora. */
@Composable
fun Modifier.glow(color: Color): Modifier {
    val transition = rememberInfiniteTransition(label = "glow")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowT",
    )
    return drawBehind {
        val spread = 6.dp.toPx() * t
        drawRoundRect(
            color = color.copy(alpha = 0.16f * t),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + spread * 2, size.height + spread * 2),
            cornerRadius = CornerRadius(size.height / 2 + spread, size.height / 2 + spread),
        )
    }
}

/** Barra de progreso redondeada (ingredientes, temporizador, pasos). */
@Composable
fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = ChefColors.GreenBright,
    trackColor: Color = ChefColors.CardBorder,
    height: Dp = 6.dp,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(450, easing = FastOutSlowInEasing), label = "progress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Encabezado de sección dentro de una tarjeta. */
@Composable
fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = chefStyle(19f, FontWeight.Black, lineHeight = 1.2f))
        if (subtitle != null) {
            Text(
                subtitle,
                style = chefStyle(13f, FontWeight.Bold, ChefColors.Muted),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Tres puntitos que laten mientras Chefcito piensa. */
@Composable
fun ThinkingDots(color: Color = ChefColors.OrangeText, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "dotsPhase",
    )
    Canvas(modifier = modifier.size(width = 34.dp, height = 10.dp)) {
        val radius = size.height / 2.6f
        for (i in 0 until 3) {
            val local = ((phase - i * 0.18f) % 1f + 1f) % 1f
            val wave = (0.5f - 0.5f * cos(2f * PI.toFloat() * local))
            drawCircle(
                color = color.copy(alpha = 0.3f + 0.7f * wave),
                radius = radius * (0.75f + 0.25f * wave),
                center = Offset(radius + i * (size.width - 2 * radius) / 2f, size.height / 2f),
            )
        }
    }
}

/** El tazón humeante del bosquejo, dibujado a mano (viewBox 350×176, recortado para cubrir). */
@Composable
fun RecipeIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "steam")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "steamPhase",
    )
    val paths = remember { IllustrationPaths() }
    Canvas(modifier = modifier) {
        drawRect(ChefColors.Peach)
        val factor = max(size.width / 350f, size.height / 176f)
        val dx = (size.width - 350f * factor) / 2f
        val dy = (size.height - 176f * factor) / 2f
        translate(dx, dy) {
            scale(scale = factor, pivot = Offset.Zero) {
                drawCircle(ChefColors.PeachDot, radius = 5f, center = Offset(34f, 30f))
                drawCircle(ChefColors.PeachDot, radius = 7f, center = Offset(318f, 140f))
                drawCircle(ChefColors.PeachDot, radius = 4f, center = Offset(56f, 128f))
                drawCircle(ChefColors.PeachDot, radius = 4f, center = Offset(300f, 34f))

                // Vapor: tres volutas desfasadas medio segundo.
                paths.steam.forEachIndexed { i, path ->
                    val local = ((phase - i * 0.5f / 2.6f) % 1f + 1f) % 1f
                    val wave = 0.5f - 0.5f * cos(2f * PI.toFloat() * local)
                    translate(0f, 4f - 7f * wave) {
                        drawPath(
                            path,
                            color = Color.White.copy(alpha = 0.25f + 0.7f * wave),
                            style = Stroke(width = 4f, cap = StrokeCap.Round),
                        )
                    }
                }

                drawOval(Color(0xFFF6C9B1), topLeft = Offset(103f, 162f), size = Size(144f, 12f))
                drawRoundRect(Color(0xFFD9481F), topLeft = Offset(143f, 152f), size = Size(64f, 12f), cornerRadius = CornerRadius(6f, 6f))
                drawLine(Color(0xFF8B5A3C), Offset(214f, 36f), Offset(304f, 100f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(Color(0xFFA86F4A), Offset(226f, 30f), Offset(310f, 92f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawOval(Color(0xFFE8552B), topLeft = Offset(83f, 74f), size = Size(184f, 36f))
                drawOval(Color(0xFFFFF1D6), topLeft = Offset(91f, 78f), size = Size(168f, 28f))
                drawPath(paths.noodlesBack, Color(0xFFF2B544), style = Stroke(width = 4f, cap = StrokeCap.Round))
                drawPath(paths.noodlesFront, Color(0xFFF7C963), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                rotate(-12f, pivot = Offset(134f, 84f)) {
                    drawRoundRect(Color(0xFFE39A5B), topLeft = Offset(124f, 78f), size = Size(20f, 13f), cornerRadius = CornerRadius(5f, 5f))
                }
                rotate(8f, pivot = Offset(157f, 88f)) {
                    drawRoundRect(Color(0xFFD98A4A), topLeft = Offset(148f, 82f), size = Size(18f, 12f), cornerRadius = CornerRadius(5f, 5f))
                }
                rotate(-6f, pivot = Offset(241f, 88f)) {
                    drawRoundRect(Color(0xFFE39A5B), topLeft = Offset(232f, 82f), size = Size(18f, 12f), cornerRadius = CornerRadius(5f, 5f))
                }
                drawOval(Color.White, topLeft = Offset(186f, 74f), size = Size(34f, 24f))
                drawCircle(Color(0xFFFFB020), radius = 7f, center = Offset(203f, 86f))
                drawCircle(ChefColors.GreenBright, radius = 4f, center = Offset(178f, 82f), style = Stroke(width = 3f))
                drawCircle(ChefColors.GreenBright, radius = 4f, center = Offset(222f, 96f), style = Stroke(width = 3f))
                drawCircle(ChefColors.GreenBright, radius = 3.5f, center = Offset(120f, 96f), style = Stroke(width = 3f))
                drawPath(paths.bowl, ChefColors.Orange)
                drawPath(paths.rimTop, ChefColors.Peach.copy(alpha = 0.75f), style = Stroke(width = 4f, cap = StrokeCap.Round))
                drawPath(paths.rimBottom, ChefColors.Peach.copy(alpha = 0.5f), style = Stroke(width = 3f, cap = StrokeCap.Round))
            }
        }
    }
}

private class IllustrationPaths {
    val steam: List<Path> = listOf(
        "M150 62 c-9 -10 9 -18 0 -30",
        "M175 58 c-9 -10 9 -18 0 -30",
        "M200 62 c-9 -10 9 -18 0 -30",
    ).map { svgPath(it) }
    val noodlesBack = svgPath("M104 94 q8 -8 16 0 t16 0 t16 0 t16 0 t16 0 t16 0 t16 0 t16 0 t16 0")
    val noodlesFront = svgPath("M112 88 q8 -7 16 0 t16 0 t16 0 t16 0 t16 0 t16 0 t16 0 t16 0")
    val bowl = svgPath("M83 92 a92 68 0 0 0 184 0 z")
    val rimTop = svgPath("M98 112 q77 20 154 0")
    val rimBottom = svgPath("M112 128 q63 14 126 0")
}

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

/** Texto centrado de estado vacío, con su icono. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconTile(icon, ChefColors.OrangeSoft, ChefColors.OrangeText, size = 56.dp, iconSize = 26.dp, corner = 20.dp)
        Text(title, style = chefStyle(16f, FontWeight.Black), textAlign = TextAlign.Center)
        Text(body, style = chefStyle(13.5f, FontWeight.SemiBold, ChefColors.Muted, lineHeight = 1.45f), textAlign = TextAlign.Center)
    }
}

/** «20 min», «1 h 15 min». */
fun minutesLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** 05:00 para el temporizador. */
fun clockLabel(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
}

fun portionsLabel(portions: Int): String = if (portions == 1) "porción" else "porciones"
