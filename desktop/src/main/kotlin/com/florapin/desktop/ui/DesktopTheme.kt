package com.florapin.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp
import com.florapin.app.ui.theme.Shapes
import com.florapin.app.ui.theme.md_dark_background
import com.florapin.app.ui.theme.md_dark_error
import com.florapin.app.ui.theme.md_dark_errorContainer
import com.florapin.app.ui.theme.md_dark_inverseOnSurface
import com.florapin.app.ui.theme.md_dark_inversePrimary
import com.florapin.app.ui.theme.md_dark_inverseSurface
import com.florapin.app.ui.theme.md_dark_onBackground
import com.florapin.app.ui.theme.md_dark_onError
import com.florapin.app.ui.theme.md_dark_onErrorContainer
import com.florapin.app.ui.theme.md_dark_onPrimary
import com.florapin.app.ui.theme.md_dark_onPrimaryContainer
import com.florapin.app.ui.theme.md_dark_onSecondary
import com.florapin.app.ui.theme.md_dark_onSecondaryContainer
import com.florapin.app.ui.theme.md_dark_onSurface
import com.florapin.app.ui.theme.md_dark_onSurfaceVariant
import com.florapin.app.ui.theme.md_dark_onTertiary
import com.florapin.app.ui.theme.md_dark_onTertiaryContainer
import com.florapin.app.ui.theme.md_dark_outline
import com.florapin.app.ui.theme.md_dark_outlineVariant
import com.florapin.app.ui.theme.md_dark_primary
import com.florapin.app.ui.theme.md_dark_primaryContainer
import com.florapin.app.ui.theme.md_dark_scrim
import com.florapin.app.ui.theme.md_dark_secondary
import com.florapin.app.ui.theme.md_dark_secondaryContainer
import com.florapin.app.ui.theme.md_dark_surface
import com.florapin.app.ui.theme.md_dark_surfaceVariant
import com.florapin.app.ui.theme.md_dark_tertiary
import com.florapin.app.ui.theme.md_dark_tertiaryContainer
import com.florapin.app.ui.theme.md_light_background
import com.florapin.app.ui.theme.md_light_error
import com.florapin.app.ui.theme.md_light_errorContainer
import com.florapin.app.ui.theme.md_light_inverseOnSurface
import com.florapin.app.ui.theme.md_light_inversePrimary
import com.florapin.app.ui.theme.md_light_inverseSurface
import com.florapin.app.ui.theme.md_light_onBackground
import com.florapin.app.ui.theme.md_light_onError
import com.florapin.app.ui.theme.md_light_onErrorContainer
import com.florapin.app.ui.theme.md_light_onPrimary
import com.florapin.app.ui.theme.md_light_onPrimaryContainer
import com.florapin.app.ui.theme.md_light_onSecondary
import com.florapin.app.ui.theme.md_light_onSecondaryContainer
import com.florapin.app.ui.theme.md_light_onSurface
import com.florapin.app.ui.theme.md_light_onSurfaceVariant
import com.florapin.app.ui.theme.md_light_onTertiary
import com.florapin.app.ui.theme.md_light_onTertiaryContainer
import com.florapin.app.ui.theme.md_light_outline
import com.florapin.app.ui.theme.md_light_outlineVariant
import com.florapin.app.ui.theme.md_light_primary
import com.florapin.app.ui.theme.md_light_primaryContainer
import com.florapin.app.ui.theme.md_light_scrim
import com.florapin.app.ui.theme.md_light_secondary
import com.florapin.app.ui.theme.md_light_secondaryContainer
import com.florapin.app.ui.theme.md_light_surface
import com.florapin.app.ui.theme.md_light_surfaceVariant
import com.florapin.app.ui.theme.md_light_tertiary
import com.florapin.app.ui.theme.md_light_tertiaryContainer
import java.io.File

/**
 * Thème du compagnon : palette et formes reprises telles quelles du module
 * Android (fichiers partagés), typographie réadaptée au poste de travail.
 *
 * L'app Android télécharge Lora et Inter via Google Fonts, mécanisme propre à
 * Android. Sur Windows on vise le même contraste — un serif pour les titres,
 * un sans-serif pour le corps — avec les polices déjà installées par le
 * système : Georgia et Segoe UI. Le rendu reste dans l'esprit de la marque
 * tout en paraissant natif, sans embarquer de fichiers de police.
 */
private val DarkColorScheme = darkColorScheme(
    primary = md_dark_primary,
    onPrimary = md_dark_onPrimary,
    primaryContainer = md_dark_primaryContainer,
    onPrimaryContainer = md_dark_onPrimaryContainer,
    secondary = md_dark_secondary,
    onSecondary = md_dark_onSecondary,
    secondaryContainer = md_dark_secondaryContainer,
    onSecondaryContainer = md_dark_onSecondaryContainer,
    tertiary = md_dark_tertiary,
    onTertiary = md_dark_onTertiary,
    tertiaryContainer = md_dark_tertiaryContainer,
    onTertiaryContainer = md_dark_onTertiaryContainer,
    error = md_dark_error,
    onError = md_dark_onError,
    errorContainer = md_dark_errorContainer,
    onErrorContainer = md_dark_onErrorContainer,
    background = md_dark_background,
    onBackground = md_dark_onBackground,
    surface = md_dark_surface,
    onSurface = md_dark_onSurface,
    surfaceVariant = md_dark_surfaceVariant,
    onSurfaceVariant = md_dark_onSurfaceVariant,
    outline = md_dark_outline,
    outlineVariant = md_dark_outlineVariant,
    inverseSurface = md_dark_inverseSurface,
    inverseOnSurface = md_dark_inverseOnSurface,
    inversePrimary = md_dark_inversePrimary,
    scrim = md_dark_scrim,
    // Voir la note sur les rôles `surfaceContainer*` plus bas.
    surfaceDim = Color(0xFF131513),
    surfaceBright = Color(0xFF393B38),
    surfaceContainerLowest = Color(0xFF0E100E),
    surfaceContainerLow = Color(0xFF1B1C1A),
    surfaceContainer = Color(0xFF1F211E),
    surfaceContainerHigh = Color(0xFF2A2C29),
    surfaceContainerHighest = Color(0xFF353734),
)

private val LightColorScheme = lightColorScheme(
    primary = md_light_primary,
    onPrimary = md_light_onPrimary,
    primaryContainer = md_light_primaryContainer,
    onPrimaryContainer = md_light_onPrimaryContainer,
    secondary = md_light_secondary,
    onSecondary = md_light_onSecondary,
    secondaryContainer = md_light_secondaryContainer,
    onSecondaryContainer = md_light_onSecondaryContainer,
    tertiary = md_light_tertiary,
    onTertiary = md_light_onTertiary,
    tertiaryContainer = md_light_tertiaryContainer,
    onTertiaryContainer = md_light_onTertiaryContainer,
    error = md_light_error,
    onError = md_light_onError,
    errorContainer = md_light_errorContainer,
    onErrorContainer = md_light_onErrorContainer,
    background = md_light_background,
    onBackground = md_light_onBackground,
    surface = md_light_surface,
    onSurface = md_light_onSurface,
    surfaceVariant = md_light_surfaceVariant,
    onSurfaceVariant = md_light_onSurfaceVariant,
    outline = md_light_outline,
    outlineVariant = md_light_outlineVariant,
    inverseSurface = md_light_inverseSurface,
    inverseOnSurface = md_light_inverseOnSurface,
    inversePrimary = md_light_inversePrimary,
    scrim = md_light_scrim,
    // Rôles `surfaceContainer*`, ajoutés par Material 3 après l'écriture du
    // thème Android et non mappés là-bas. Sans valeur explicite, Material les
    // remplit avec sa palette « baseline » violette : cartes, dialogues et
    // menus s'affichaient donc en mauve au milieu d'une charte verte. On les
    // dérive ici du blanc cassé botanique.
    surfaceDim = Color(0xFFDBE5DC),
    surfaceBright = Color(0xFFFBFDFB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F8F4),
    surfaceContainer = Color(0xFFEFF3EE),
    surfaceContainerHigh = Color(0xFFE9EEE9),
    surfaceContainerHighest = Color(0xFFE3E9E3),
)

/**
 * Charge une famille depuis les polices installées de Windows. Repli sur la
 * famille générique si les fichiers sont absents (autre système, installation
 * minimale) : l'application démarre toujours, avec un rendu légèrement
 * différent plutôt qu'une erreur.
 */
private fun systemFontFamily(
    fallback: FontFamily,
    vararg files: Pair<String, FontWeight>,
): FontFamily {
    val fontsDir = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
    val fonts = files.mapNotNull { (name, weight) ->
        val file = File(fontsDir, name)
        if (file.isFile) runCatching { Font(file, weight) }.getOrNull() else null
    }
    return if (fonts.isEmpty()) fallback else FontFamily(fonts)
}

private val DisplayFontFamily: FontFamily by lazy {
    systemFontFamily(
        FontFamily.Serif,
        "georgia.ttf" to FontWeight.Normal,
        "georgiab.ttf" to FontWeight.Bold,
    )
}

private val BodyFontFamily: FontFamily by lazy {
    systemFontFamily(
        FontFamily.SansSerif,
        "segoeui.ttf" to FontWeight.Normal,
        "seguisb.ttf" to FontWeight.SemiBold,
        "segoeuib.ttf" to FontWeight.Bold,
    )
}

/**
 * Échelle typographique resserrée par rapport à Material 3, dont les valeurs
 * par défaut visent un écran tenu à bout de bras. Sur un moniteur, à 60 cm,
 * ces tailles paraissent surdimensionnées et réduisent la densité utile — un
 * compagnon de tri photo doit au contraire en afficher beaucoup.
 */
private val DesktopTypography: Typography by lazy {
    Typography(
        displaySmall = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 38.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            lineHeight = 25.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            lineHeight = 23.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = BodyFontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = BodyFontFamily,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = BodyFontFamily,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        ),
    )
}

@Composable
fun FloraPinDesktopTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = DesktopTypography,
        shapes = Shapes,
        content = content,
    )
}
