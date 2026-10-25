package com.chefdesobras.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Paleta «Warm Bento»: crema, naranja y verde, tomada del bosquejo de diseño. */
object ChefColors {
    val Bg = Color(0xFFFFFDF8)
    val Text = Color(0xFF2D3748)
    val Muted = Color(0xFF5A6577)
    val Card = Color(0xFFFFFFFF)
    val CardBorder = Color(0xFFF3EDE2)
    val ButtonBorder = Color(0xFFE9DFD0)
    val CheckBorder = Color(0xFFD9CFBF)

    val Orange = Color(0xFFFF6B4A)
    val OrangeDeep = Color(0xFFD9481F)
    val Cta = Color(0xFFE8552B)
    val CtaShadow = Color(0xFFB83E1A)
    val OrangeText = Color(0xFFC2410C)
    val OrangeTextDark = Color(0xFF9A3412)
    val OrangeInk = Color(0xFFB8401A)
    val OrangeSoft = Color(0xFFFFF1EA)
    val OrangeSoftBorder = Color(0xFFFFD9C7)
    val FavBorder = Color(0xFFFFC9B3)
    val Peach = Color(0xFFFFE6D6)
    val PeachDot = Color(0xFFFFD2BC)
    val Bubble = Color(0xFFFFF8F0)
    val BubbleBorder = Color(0xFFF6DCC8)
    val TimerTrack = Color(0xFFF3E2D3)

    val Green = Color(0xFF2F855A)
    val GreenBright = Color(0xFF38A169)
    val GreenSoft = Color(0xFFEAF6EF)
    val GreenSoftBorder = Color(0xFFCDEBD9)
    val GreenInk = Color(0xFF276749)
    val GreenDeep = Color(0xFF22543D)
    val GreenRow = Color(0xFFF3FAF6)

    val Danger = Color(0xFFC53030)
    val DangerSoft = Color(0xFFFFF0EE)
    val DangerBorder = Color(0xFFF8C9C2)

    /** Sombra cálida y suave de las tarjetas. */
    val Shadow = Color(0xFF8A5A2B)
}

// Nunito variable (OFL): una sola fuente para todos los pesos, del 500 al 900.
@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    resId = R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Nunito = FontFamily(
    nunito(400),
    nunito(500),
    nunito(600),
    nunito(700),
    nunito(800),
    nunito(900),
)

private val base = Typography()

private val ChefTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Black),
    displayMedium = base.displayMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Black),
    displaySmall = base.displaySmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Black),
    headlineLarge = base.headlineLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Black),
    headlineMedium = base.headlineMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Black),
    headlineSmall = base.headlineSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Black),
    titleLarge = base.titleLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
    titleMedium = base.titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
    titleSmall = base.titleSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
    bodyMedium = base.bodyMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
    bodySmall = base.bodySmall.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
    labelMedium = base.labelMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
    labelSmall = base.labelSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
)

private val ChefColorScheme = lightColorScheme(
    primary = ChefColors.Cta,
    onPrimary = Color.White,
    primaryContainer = ChefColors.OrangeSoft,
    onPrimaryContainer = ChefColors.OrangeInk,
    secondary = ChefColors.Green,
    onSecondary = Color.White,
    secondaryContainer = ChefColors.GreenSoft,
    onSecondaryContainer = ChefColors.GreenDeep,
    tertiary = ChefColors.OrangeText,
    onTertiary = Color.White,
    background = ChefColors.Bg,
    onBackground = ChefColors.Text,
    surface = ChefColors.Bg,
    onSurface = ChefColors.Text,
    surfaceVariant = ChefColors.Bubble,
    onSurfaceVariant = ChefColors.Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = ChefColors.Bubble,
    inverseSurface = ChefColors.Text,
    inverseOnSurface = Color.White,
    inversePrimary = ChefColors.Peach,
    outline = ChefColors.ButtonBorder,
    outlineVariant = ChefColors.CardBorder,
    error = ChefColors.Danger,
    onError = Color.White,
)

/** El diseño es siempre claro y cálido, como en el bosquejo. */
@Composable
fun ChefTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ChefColorScheme, typography = ChefTypography, content = content)
}
