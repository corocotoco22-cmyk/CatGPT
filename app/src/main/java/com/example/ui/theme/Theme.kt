package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CatDarkColorScheme = darkColorScheme(
    primary = TerracottaDark,
    onPrimary = TerracottaOnDark,
    primaryContainer = TerracottaContainerDark,
    onPrimaryContainer = TerracottaOnContainerDark,
    secondary = SageDark,
    onSecondary = SageOnDark,
    secondaryContainer = SageContainerDark,
    onSecondaryContainer = SageOnContainerDark,
    tertiary = WhiskerDark,
    onTertiary = WhiskerOnDark,
    tertiaryContainer = WhiskerContainerDark,
    onTertiaryContainer = WhiskerOnContainerDark,
    background = VelvetNightBackground,
    onBackground = WarmIvoryOnBackground,
    surface = VelvetSurface,
    onSurface = WarmIvoryOnBackground,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OutlineDark,
    outline = OutlineDark,
    error = ErrorCoralDark
)

private val CatLightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = TerracottaOnPrimary,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = TerracottaOnContainer,
    secondary = SageSecondary,
    onSecondary = SageOnSecondary,
    secondaryContainer = SageContainer,
    onSecondaryContainer = SageOnContainer,
    tertiary = WhiskerTertiary,
    onTertiary = WhiskerOnTertiary,
    tertiaryContainer = WhiskerContainer,
    onTertiaryContainer = WhiskerOnContainer,
    background = ParchmentBackground,
    onBackground = EspressoOnBackground,
    surface = WarmSandSurface,
    onSurface = EspressoOnBackground,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OutlineLight,
    outline = OutlineLight,
    error = ErrorCoral
)

val CatShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun CatGptTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CatDarkColorScheme else CatLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CatShapes,
        content = content
    )
}
