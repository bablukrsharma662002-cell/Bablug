package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StageCraftDarkColorScheme = darkColorScheme(
    primary = CinemaGold,
    onPrimary = OnCinemaGold,
    primaryContainer = CinemaGoldContainer,
    onPrimaryContainer = OnCinemaGoldContainer,
    secondary = RigCyan,
    onSecondary = OnRigCyan,
    secondaryContainer = RigCyanContainer,
    onSecondaryContainer = OnRigCyanContainer,
    tertiary = VoiceCoral,
    onTertiary = OnVoiceCoral,
    tertiaryContainer = VoiceCoralContainer,
    onTertiaryContainer = OnVoiceCoralContainer,
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceContainer,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLowest = StudioObsidian,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceContainer,
    surfaceContainerHigh = StudioSurfaceHigh,
    surfaceContainerHighest = StudioSurfaceHighest,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = StudioError,
    onError = StudioObsidian,
)

@Composable
fun StageCraftTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StageCraftDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
