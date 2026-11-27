package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DraftRigDarkColorScheme = darkColorScheme(
    primary = ElectricIndigo,
    onPrimary = TextPrimary,
    primaryContainer = ElectricIndigoContainer,
    onPrimaryContainer = TextPrimary,
    secondary = StudioCyan,
    onSecondary = CharcoalCanvas,
    secondaryContainer = StudioCyanContainer,
    onSecondaryContainer = StudioCyan,
    tertiary = KeyframeAmber,
    onTertiary = CharcoalCanvas,
    tertiaryContainer = KeyframeAmberContainer,
    onTertiaryContainer = KeyframeAmber,
    background = CharcoalCanvas,
    onBackground = TextPrimary,
    surface = PanelNearBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = CharcoalCanvas,
    surfaceContainerLow = PanelNearBlack,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceElevated,
    outline = HairlineBorder,
    outlineVariant = HairlineBorderActive,
    error = DangerRed,
    onError = TextPrimary
)

val DraftRigShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun DraftRigTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DraftRigDarkColorScheme,
        typography = DraftRigTypography,
        shapes = DraftRigShapes,
        content = content
    )
}
