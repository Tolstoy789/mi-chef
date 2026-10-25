package com.chefdesobras.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlin.math.PI
import kotlin.math.cos

/** Vista B del bosquejo: modo cocina guiado, paso a paso. */
@Composable
fun CookingScreen(
    recipe: Recipe,
    portions: Int,
    cooking: CookingState,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onAsk: (AssistKind) -> Unit,
    onCloseAssist: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val stepCount = recipe.steps.size
    val index = cooking.stepIndex.coerceIn(0, (stepCount - 1).coerceAtLeast(0))
    val isLast = index >= stepCount - 1

    // La pantalla no se apaga mientras se cocina.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val handsFree = remember { HandsFreeController(context) }
    DisposableEffect(handsFree) {
        onDispose { handsFree.release() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { handsFree.stop() }

    fun stepSpeech(i: Int): String {
        val step = recipe.steps.getOrNull(i) ?: return ""
        return "Paso ${i + 1}. ${step.title}. ${step.body}"
    }

    fun handleVoice(command: VoiceCommand) {
        when (command) {
            VoiceCommand.NEXT -> onNext()
            VoiceCommand.PREVIOUS -> if (index > 0) onPrevious() else handsFree.speak("Ya estás en el primer paso.")
            VoiceCommand.REPEAT -> handsFree.speak(stepSpeech(index))
            VoiceCommand.PAUSE -> if (cooking.timerRunning) onPauseTimer() else handsFree.speak("El temporizador no está en marcha.")
            VoiceCommand.START -> if (cooking.timerTotal > 0) onResumeTimer() else handsFree.speak("Este paso no tiene temporizador.")
            VoiceCommand.WHY, VoiceCommand.SUBSTITUTE -> {
                val kind = if (command == VoiceCommand.WHY) AssistKind.WHY else AssistKind.SUBSTITUTE
                if (cooking.assist != kind) {
                    onAsk(kind)
                } else if (!cooking.assistLoading) {
                    handsFree.speak(cooking.assistText)
                }
            }
        }
    }

    SideEffect {
        handsFree.onCommand = { handleVoice(it) }
        handsFree.onProblem = onMessage
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            onMessage("Sin permiso de micrófono no puedo escucharte.")
        } else if (!handsFree.start()) {
            onMessage("Tu teléfono no tiene reconocimiento de voz disponible.")
        }
    }
    val toggleHandsFree: () -> Unit = {
        if (handsFree.active) {
            handsFree.stop()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (!handsFree.start()) onMessage("Tu teléfono no tiene reconocimiento de voz disponible.")
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Con el modo manos libres, cada paso nuevo se lee en voz alta.
    LaunchedEffect(handsFree.active, index) {
        if (handsFree.active) handsFree.speak(stepSpeech(index))
    }
    // Aviso hablado cuando termina un temporizador (el sonido y la vibración los pone el ViewModel).
    var lastAlarm by remember { mutableIntStateOf(cooking.alarmCount) }
    LaunchedEffect(cooking.alarmCount) {
        if (cooking.alarmCount != lastAlarm) {
            lastAlarm = cooking.alarmCount
            if (handsFree.active) handsFree.speak("¡Tiempo! Terminó el temporizador.")
        }
    }
    // Las respuestas de «Pregúntale a Chefcito» también se leen, cuando terminan de escribirse.
    var speakAssist by remember { mutableStateOf(false) }
    LaunchedEffect(cooking.assist) {
        speakAssist = cooking.assist != null && handsFree.active
    }
    LaunchedEffect(speakAssist, cooking.assistLoading, cooking.assistText) {
        if (speakAssist && !cooking.assistLoading && cooking.assistText.isNotBlank()) {
            speakAssist = false
            handsFree.speak(cooking.assistText)
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(index) { scrollState.animateScrollTo(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundIconButton(ChefIcons.Close, "Salir del modo cocina", onClose)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    recipe.title,
                    style = chefStyle(12f, FontWeight.ExtraBold, ChefColors.Muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Paso ${index + 1} de $stepCount", style = chefStyle(18f, FontWeight.Black, lineHeight = 1.25f))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ChefColors.OrangeSoft)
                    .semantics { contentDescription = "$portions ${portionsLabel(portions)}" },
                contentAlignment = Alignment.Center,
            ) {
                Text("×$portions", style = chefStyle(13f, FontWeight.Black, ChefColors.OrangeText, lineHeight = 1f))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (k in 0 until stepCount) {
                StepSegment(done = k < index, current = k == index, modifier = Modifier.weight(1f))
            }
        }
        HandsFreeBanner(
            active = handsFree.active,
            listening = handsFree.listening,
            lastHeard = handsFree.lastHeard,
            onToggle = toggleHandsFree,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { width -> if (forward) width / 20 else -width / 20 } +
                        fadeIn(tween(380))) togetherWith fadeOut(tween(140))
                },
                label = "step",
            ) { shown ->
                val step = recipe.steps.getOrNull(shown)
                if (step != null) {
                    // La tarjeta que se va muestra su temporizador en reposo; solo la actual lleva la cuenta real.
                    val live = shown == cooking.stepIndex
                    StepCard(
                        number = shown + 1,
                        step = step,
                        timerTotal = if (live) cooking.timerTotal else step.timerSeconds,
                        timerRemaining = if (live) cooking.timerRemaining else step.timerSeconds,
                        timerRunning = live && cooking.timerRunning,
                        onToggleTimer = onToggleTimer,
                        onResetTimer = onResetTimer,
                    )
                }
            }
            AskChefcitoCard(cooking = cooking, onAsk = onAsk, onClose = onCloseAssist)
        }
        BottomBar {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton(
                    onClick = onPrevious,
                    modifier = Modifier.weight(1f),
                    minHeight = 60.dp,
                    enabled = index > 0,
                ) {
                    Icon(ChefIcons.ChevronLeft, contentDescription = null, tint = ChefColors.Text, modifier = Modifier.size(20.dp))
                    Text("Anterior", style = chefStyle(16f, FontWeight.ExtraBold, lineHeight = 1.2f), maxLines = 1)
                }
                ChunkyButton(onClick = onNext, modifier = Modifier.weight(1.7f)) {
                    Text(
                        if (isLast) "¡Terminar!" else "Siguiente paso",
                        style = chefStyle(19f, FontWeight.Black, Color.White, lineHeight = 1.2f),
                        maxLines = 1,
                    )
                    Icon(ChefIcons.ArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun StepSegment(done: Boolean, current: Boolean, modifier: Modifier = Modifier) {
    val fill by animateFloatAsState(if (done || current) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "segment")
    val color by animateColorAsState(if (done) ChefColors.GreenBright else ChefColors.Orange, label = "segmentColor")
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(CircleShape)
            .background(ChefColors.CardBorder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fill)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun HandsFreeBanner(
    active: Boolean,
    listening: Boolean,
    lastHeard: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(
                label = if (active) "Desactivar el modo manos libres" else "Activar el modo manos libres",
                pressedScale = 0.98f,
                onClick = onToggle,
            )
            .clip(shape)
            .background(if (active) ChefColors.GreenSoft else ChefColors.Card)
            .border(1.dp, if (active) ChefColors.GreenSoftBorder else ChefColors.CardBorder, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            if (active) PulseRing(ChefColors.Green, 34.dp)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (active) ChefColors.Green else ChefColors.OrangeSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (active) ChefIcons.Mic else ChefIcons.MicOff,
                    contentDescription = null,
                    tint = if (active) Color.White else ChefColors.OrangeText,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (active) "Modo manos libres activo" else "Modo manos libres",
                style = chefStyle(14f, FontWeight.Black, if (active) ChefColors.GreenDeep else ChefColors.Text, lineHeight = 1.25f),
            )
            val hint = when {
                !active -> "Toca para guiar la receta con tu voz"
                lastHeard != null -> "Escuché: «$lastHeard»"
                else -> "Di «siguiente», «repite» o «pausa»"
            }
            Text(
                hint,
                style = chefStyle(12f, FontWeight.Bold, if (active) ChefColors.GreenInk else ChefColors.Muted, lineHeight = 1.3f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (active) {
            EqualizerBars(ChefColors.Green, animate = listening)
        } else {
            Pill("Activar", ChefColors.Orange, Color.White)
        }
    }
}

@Composable
private fun PulseRing(color: Color, size: Dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulseT",
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = 1f + 0.9f * t
                scaleY = 1f + 0.9f * t
                alpha = 0.45f * (1f - t)
            }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun EqualizerBars(color: Color, animate: Boolean) {
    val transition = rememberInfiniteTransition(label = "eq")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "eqPhase",
    )
    Canvas(modifier = Modifier.size(width = 25.dp, height = 20.dp)) {
        val barWidth = 4.dp.toPx()
        val gap = 3.dp.toPx()
        for (i in 0 until 4) {
            val local = ((phase - i * 0.2f) % 1f + 1f) % 1f
            val wave = if (animate) 0.5f - 0.5f * cos(2f * PI.toFloat() * local) else 0.15f
            val height = (6f + 12f * wave).dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    step: RecipeStep,
    timerTotal: Int,
    timerRemaining: Int,
    timerRunning: Boolean,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
) {
    ChefCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Pill("PASO $number", ChefColors.OrangeSoft, ChefColors.OrangeInk, horizontalPadding = 11.dp)
            Text(
                step.title,
                style = chefStyle(28f, FontWeight.Black, lineHeight = 1.18f, letterSpacing = -0.3f),
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
            )
            Text(step.body, style = chefStyle(18f, FontWeight.SemiBold, lineHeight = 1.55f))
            if (timerTotal > 0) {
                TimerBlock(
                    total = timerTotal,
                    remaining = timerRemaining,
                    running = timerRunning,
                    onToggle = onToggleTimer,
                    onReset = onResetTimer,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun TimerBlock(
    total: Int,
    remaining: Int,
    running: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finished = remaining <= 0
    val (status, statusBackground, statusColor) = when {
        running -> Triple("En marcha", ChefColors.GreenSoft, ChefColors.GreenInk)
        finished -> Triple("¡Tiempo!", ChefColors.Green, Color.White)
        remaining < total -> Triple("En pausa", ChefColors.OrangeSoft, ChefColors.OrangeInk)
        else -> Triple("Listo para iniciar", Color.White, ChefColors.Muted)
    }
    val toggleLabel = when {
        running -> "Pausar"
        finished -> "Otra vez"
        remaining < total -> "Reanudar"
        else -> "Iniciar"
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ChefColors.Bubble)
            .border(1.dp, ChefColors.BubbleBorder, shape)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ChefIcons.Timer, contentDescription = null, tint = ChefColors.OrangeText, modifier = Modifier.size(16.dp))
                Text("Temporizador · ${timerLabel(total)}", style = chefStyle(13f, FontWeight.Black, ChefColors.OrangeText))
            }
            Pill(status, statusBackground, statusColor, horizontalPadding = 9.dp, verticalPadding = 4.dp)
        }
        Text(
            clockLabel(remaining),
            style = chefStyle(
                54f,
                FontWeight.Black,
                if (finished) ChefColors.GreenInk else ChefColors.Text,
                lineHeight = 1.05f,
                letterSpacing = -1f,
                tabular = true,
            ),
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        )
        ProgressTrack(
            fraction = if (total > 0) (total - remaining) / total.toFloat() else 0f,
            color = ChefColors.Orange,
            trackColor = ChefColors.TimerTrack,
            height = 8.dp,
        )
        Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChunkyButton(
                onClick = onToggle,
                modifier = Modifier.weight(1f),
                color = if (running) ChefColors.Green else ChefColors.OrangeText,
                baseColor = if (running) ChefColors.GreenDeep else ChefColors.OrangeTextDark,
                corner = 16.dp,
                minHeight = 50.dp,
                depth = 3.dp,
                glow = false,
            ) {
                Icon(
                    if (running) ChefIcons.PauseFilled else ChefIcons.PlayFilled,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Text(toggleLabel, style = chefStyle(16f, FontWeight.Black, Color.White, lineHeight = 1.2f))
            }
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .pressable(label = "Reiniciar temporizador", onClick = onReset)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, ChefColors.CardBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ChefIcons.RotateCcw, contentDescription = null, tint = ChefColors.Text, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun AskChefcitoCard(cooking: CookingState, onAsk: (AssistKind) -> Unit, onClose: () -> Unit) {
    ChefCard(modifier = Modifier.fillMaxWidth(), shape = BentoShape, elevation = 6.dp) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ChefColors.Orange),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ChefIcons.ChefHat, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Text("Pregúntale a Chefcito", style = chefStyle(15f, FontWeight.Black), modifier = Modifier.weight(1f))
                TwinkleIcon(ChefIcons.Sparkles, ChefColors.OrangeText, 18.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistButton(
                    text = "¿Por qué hacemos esto?",
                    icon = ChefIcons.CircleHelp,
                    iconTint = ChefColors.OrangeText,
                    selected = cooking.assist == AssistKind.WHY,
                    selectedBackground = ChefColors.OrangeSoft,
                    selectedBorder = ChefColors.Orange,
                    onClick = { onAsk(AssistKind.WHY) },
                    modifier = Modifier.weight(1f),
                )
                AssistButton(
                    text = "Sustituir ingrediente",
                    icon = ChefIcons.Swap,
                    iconTint = ChefColors.Green,
                    selected = cooking.assist == AssistKind.SUBSTITUTE,
                    selectedBackground = ChefColors.GreenSoft,
                    selectedBorder = ChefColors.GreenBright,
                    onClick = { onAsk(AssistKind.SUBSTITUTE) },
                    modifier = Modifier.weight(1f),
                )
            }
            val assist = cooking.assist
            if (assist != null) {
                key(assist) {
                    val appear = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { appear.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
                    AssistBubble(
                        title = if (assist == AssistKind.WHY) "Chefcito explica" else "Sustitución sugerida",
                        text = cooking.assistText,
                        loading = cooking.assistLoading,
                        onClose = onClose,
                        modifier = Modifier.graphicsLayer {
                            alpha = appear.value
                            translationY = (1f - appear.value) * 8.dp.toPx()
                            scaleX = 0.97f + 0.03f * appear.value
                            scaleY = 0.97f + 0.03f * appear.value
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistButton(
    text: String,
    icon: ImageVector,
    iconTint: Color,
    selected: Boolean,
    selectedBackground: Color,
    selectedBorder: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .pressable(label = text, onClick = onClick)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) selectedBackground else ChefColors.Bg)
            .border(1.5.dp, if (selected) selectedBorder else ChefColors.CardBorder, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Text(text, style = chefStyle(13f, FontWeight.ExtraBold, lineHeight = 1.2f))
    }
}

@Composable
private fun AssistBubble(
    title: String,
    text: String,
    loading: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(BubbleShape)
            .background(ChefColors.Bubble)
            .border(1.dp, ChefColors.BubbleBorder, BubbleShape)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                style = chefStyle(12f, FontWeight.Black, ChefColors.OrangeInk, letterSpacing = 0.5f),
                modifier = Modifier.weight(1f),
            )
            if (loading) ThinkingDots()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pressable(label = "Cerrar respuesta", onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ChefIcons.Close, contentDescription = null, tint = ChefColors.Muted, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            text.ifBlank { "Chefcito está pensando…" },
            style = chefStyle(15f, FontWeight.SemiBold, if (text.isBlank()) ChefColors.Muted else ChefColors.Text, lineHeight = 1.5f),
            modifier = Modifier.padding(top = 2.dp, end = 8.dp),
        )
    }
}
