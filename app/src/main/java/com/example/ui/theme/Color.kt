package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class ExpressivePalette(
    val id: String,
    val displayName: String,
    val tagline: String,
    val previewPrimary: Color,
    val previewSecondary: Color,
    val previewTertiary: Color,
    val previewContainer: Color
) {
    TERRACOTTA(
        id = "terracotta",
        displayName = "Terracotta Sun",
        tagline = "Warm clay & sunlit sand",
        previewPrimary = Color(0xFFB84323),
        previewSecondary = Color(0xFF8D5137),
        previewTertiary = Color(0xFFD68D2E),
        previewContainer = Color(0xFFFFDBD0)
    ),
    MATCHA(
        id = "matcha",
        displayName = "Matcha Grove",
        tagline = "Botanical sage & crisp lime",
        previewPrimary = Color(0xFF2B6B46),
        previewSecondary = Color(0xFF53634F),
        previewTertiary = Color(0xFF829927),
        previewContainer = Color(0xFFC5F0D2)
    ),
    COBALT(
        id = "cobalt",
        displayName = "Cobalt Pulse",
        tagline = "Electric indigo & soft lilac",
        previewPrimary = Color(0xFF2E52D9),
        previewSecondary = Color(0xFF575D7A),
        previewTertiary = Color(0xFF9C4E9B),
        previewContainer = Color(0xFFDDE1FF)
    ),
    BERRY(
        id = "berry",
        displayName = "Berry Velvet",
        tagline = "Plum rose & blush porcelain",
        previewPrimary = Color(0xFFA62962),
        previewSecondary = Color(0xFF775662),
        previewTertiary = Color(0xFFC66B3B),
        previewContainer = Color(0xFFFFD8E6)
    ),
    CITRUS(
        id = "citrus",
        displayName = "Citrus Pop",
        tagline = "Saffron zest & cool teal",
        previewPrimary = Color(0xFFC25E00),
        previewSecondary = Color(0xFF765943),
        previewTertiary = Color(0xFF0E7C74),
        previewContainer = Color(0xFFFFDCC2)
    ),
    DYNAMIC(
        id = "dynamic",
        displayName = "System",
        tagline = "Material You wallpaper",
        previewPrimary = Color(0xFF4F5D75),
        previewSecondary = Color(0xFF5D636E),
        previewTertiary = Color(0xFF6B5B7B),
        previewContainer = Color(0xFFDCE3F0)
    );

    companion object {
        fun fromId(id: String?): ExpressivePalette = when (id?.lowercase()) {
            "system", "dynamic" -> DYNAMIC
            else -> entries.firstOrNull { it.id == id } ?: TERRACOTTA
        }
    }
}

enum class AppearanceMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): AppearanceMode = when (id?.lowercase()) {
            "auto", "system" -> SYSTEM
            "light" -> LIGHT
            "dark" -> DARK
            else -> entries.firstOrNull { it.id == id } ?: SYSTEM
        }
    }
}

// 1. Terracotta Sun — Warm clay, cream paper & golden ochre
val TerracottaLightScheme = lightColorScheme(
    primary = Color(0xFFAE3D1E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3C0B00),
    secondary = Color(0xFF7B5748),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBD0),
    onSecondaryContainer = Color(0xFF2E150A),
    tertiary = Color(0xFF8C6214),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE0A8),
    onTertiaryContainer = Color(0xFF2D1C00),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF231916),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF231916),
    surfaceVariant = Color(0xFFF5DDD5),
    onSurfaceVariant = Color(0xFF54433D),
    outline = Color(0xFF86726C),
    outlineVariant = Color(0xFFD9C2BA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFAEAE4),
    surfaceContainerHigh = Color(0xFFF4E3DC),
    surfaceContainerHighest = Color(0xFFEEDDD6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val TerracottaDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB59D),
    onPrimary = Color(0xFF601602),
    primaryContainer = Color(0xFF882A0E),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFEBBCA9),
    onSecondary = Color(0xFF46291D),
    secondaryContainer = Color(0xFF603F32),
    onSecondaryContainer = Color(0xFFFFDBD0),
    tertiary = Color(0xFFF2C46D),
    onTertiary = Color(0xFF473000),
    tertiaryContainer = Color(0xFF674700),
    onTertiaryContainer = Color(0xFFFFE0A8),
    background = Color(0xFF1A120F),
    onBackground = Color(0xFFF3E2DC),
    surface = Color(0xFF1A120F),
    onSurface = Color(0xFFF3E2DC),
    surfaceVariant = Color(0xFF54433D),
    onSurfaceVariant = Color(0xFFD9C2BA),
    outline = Color(0xFFA18C85),
    outlineVariant = Color(0xFF54433D),
    surfaceContainerLowest = Color(0xFF140D0B),
    surfaceContainerLow = Color(0xFF231916),
    surfaceContainer = Color(0xFF281D1A),
    surfaceContainerHigh = Color(0xFF332723),
    surfaceContainerHighest = Color(0xFF3E312D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 2. Matcha Grove — Vibrant botanical emerald & chartreuse accents
val MatchaLightScheme = lightColorScheme(
    primary = Color(0xFF246944),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB5F2CC),
    onPrimaryContainer = Color(0xFF002110),
    secondary = Color(0xFF4E6354),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8D5),
    onSecondaryContainer = Color(0xFF0C1F14),
    tertiary = Color(0xFF677815),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE7F992),
    onTertiaryContainer = Color(0xFF1D2400),
    background = Color(0xFFF6FBF5),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FBF5),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF404942),
    outline = Color(0xFF707971),
    outlineVariant = Color(0xFFC0C9C0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F6EF),
    surfaceContainer = Color(0xFFEAF0E9),
    surfaceContainerHigh = Color(0xFFE4EBE3),
    surfaceContainerHighest = Color(0xFFDFE5DD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val MatchaDarkScheme = darkColorScheme(
    primary = Color(0xFF94D7AF),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF07512E),
    onPrimaryContainer = Color(0xFFB5F2CC),
    secondary = Color(0xFFB6CCB9),
    onSecondary = Color(0xFF213527),
    secondaryContainer = Color(0xFF374B3D),
    onSecondaryContainer = Color(0xFFD2E8D5),
    tertiary = Color(0xFFCBDD78),
    onTertiary = Color(0xFF323C00),
    tertiaryContainer = Color(0xFF4B5900),
    onTertiaryContainer = Color(0xFFE7F992),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDFE5DD),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDFE5DD),
    surfaceVariant = Color(0xFF404942),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8A938B),
    outlineVariant = Color(0xFF404942),
    surfaceContainerLowest = Color(0xFF0A100C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B221D),
    surfaceContainerHigh = Color(0xFF252C27),
    surfaceContainerHighest = Color(0xFF303732),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 3. Cobalt Pulse — Luminous ultramarine & frosted lilac
val CobaltLightScheme = lightColorScheme(
    primary = Color(0xFF2C4FD4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001453),
    secondary = Color(0xFF565D7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE0FF),
    onSecondaryContainer = Color(0xFF131A34),
    tertiary = Color(0xFF8D458E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD6FB),
    onTertiaryContainer = Color(0xFF38003C),
    background = Color(0xFFFAF9FF),
    onBackground = Color(0xFF1A1B24),
    surface = Color(0xFFFAF9FF),
    onSurface = Color(0xFF1A1B24),
    surfaceVariant = Color(0xFFE2E2F0),
    onSurfaceVariant = Color(0xFF454654),
    outline = Color(0xFF757685),
    outlineVariant = Color(0xFFC6C6D6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FD),
    surfaceContainer = Color(0xFFEDEDFA),
    surfaceContainerHigh = Color(0xFFE7E7F4),
    surfaceContainerHighest = Color(0xFFE1E1EE),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val CobaltDarkScheme = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF002585),
    primaryContainer = Color(0xFF1137B8),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFBEC5E6),
    onSecondary = Color(0xFF282F4A),
    secondaryContainer = Color(0xFF3F4562),
    onSecondaryContainer = Color(0xFFDAE0FF),
    tertiary = Color(0xFFF6AEF5),
    onTertiary = Color(0xFF561359),
    tertiaryContainer = Color(0xFF712C73),
    onTertiaryContainer = Color(0xFFFFD6FB),
    background = Color(0xFF11131C),
    onBackground = Color(0xFFE3E2EE),
    surface = Color(0xFF11131C),
    onSurface = Color(0xFFE3E2EE),
    surfaceVariant = Color(0xFF454654),
    onSurfaceVariant = Color(0xFFC6C6D6),
    outline = Color(0xFF9090A0),
    outlineVariant = Color(0xFF454654),
    surfaceContainerLowest = Color(0xFF0C0E16),
    surfaceContainerLow = Color(0xFF191B26),
    surfaceContainer = Color(0xFF1E202C),
    surfaceContainerHigh = Color(0xFF282A37),
    surfaceContainerHighest = Color(0xFF333543),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 4. Berry Velvet — Expressive magenta plum & warm peach ceramic
val BerryLightScheme = lightColorScheme(
    primary = Color(0xFFA0245D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD8E6),
    onPrimaryContainer = Color(0xFF3D001E),
    secondary = Color(0xFF745660),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD8E4),
    onSecondaryContainer = Color(0xFF2B151D),
    tertiary = Color(0xFF9C512D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCC),
    onTertiaryContainer = Color(0xFF371000),
    background = Color(0xFFFFF8F9),
    onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F9),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF3DDE3),
    onSurfaceVariant = Color(0xFF514348),
    outline = Color(0xFF837378),
    outlineVariant = Color(0xFFD6C2C7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F3),
    surfaceContainer = Color(0xFFFAEAF0),
    surfaceContainerHigh = Color(0xFFF4E4EA),
    surfaceContainerHighest = Color(0xFFEEDEE4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val BerryDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB0CE),
    onPrimary = Color(0xFF620033),
    primaryContainer = Color(0xFF820846),
    onPrimaryContainer = Color(0xFFFFD8E6),
    secondary = Color(0xFFE3BDC9),
    onSecondary = Color(0xFF422932),
    secondaryContainer = Color(0xFF5B3F49),
    onSecondaryContainer = Color(0xFFFFD8E4),
    tertiary = Color(0xFFFFB694),
    onTertiary = Color(0xFF5B2204),
    tertiaryContainer = Color(0xFF7B3A18),
    onTertiaryContainer = Color(0xFFFFDBCC),
    background = Color(0xFF191114),
    onBackground = Color(0xFFF0DFE4),
    surface = Color(0xFF191114),
    onSurface = Color(0xFFF0DFE4),
    surfaceVariant = Color(0xFF514348),
    onSurfaceVariant = Color(0xFFD6C2C7),
    outline = Color(0xFF9E8C92),
    outlineVariant = Color(0xFF514348),
    surfaceContainerLowest = Color(0xFF130C0F),
    surfaceContainerLow = Color(0xFF22191C),
    surfaceContainer = Color(0xFF271D21),
    surfaceContainerHigh = Color(0xFF32272B),
    surfaceContainerHighest = Color(0xFF3D3236),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 5. Citrus Pop — Joyful saffron-tangerine & fresh eucalyptus teal
val CitrusLightScheme = lightColorScheme(
    primary = Color(0xFFB55600),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF391700),
    secondary = Color(0xFF755844),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC4),
    onSecondaryContainer = Color(0xFF2B1707),
    tertiary = Color(0xFF006B64),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9CF2EA),
    onTertiaryContainer = Color(0xFF00201E),
    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F3),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF4DFD0),
    onSurfaceVariant = Color(0xFF534439),
    outline = Color(0xFF857467),
    outlineVariant = Color(0xFFD7C3B4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF2E8),
    surfaceContainer = Color(0xFFFAECE1),
    surfaceContainerHigh = Color(0xFFF4E6DA),
    surfaceContainerHighest = Color(0xFFEFE0D5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val CitrusDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB780),
    onPrimary = Color(0xFF5E2B00),
    primaryContainer = Color(0xFF884000),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE4BFA7),
    onSecondary = Color(0xFF422B1A),
    secondaryContainer = Color(0xFF5B412E),
    onSecondaryContainer = Color(0xFFFFDCC4),
    tertiary = Color(0xFF7FD5CE),
    onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF00504B),
    onTertiaryContainer = Color(0xFF9CF2EA),
    background = Color(0xFF19120D),
    onBackground = Color(0xFFF0E0D5),
    surface = Color(0xFF19120D),
    onSurface = Color(0xFFF0E0D5),
    surfaceVariant = Color(0xFF534439),
    onSurfaceVariant = Color(0xFFD7C3B4),
    outline = Color(0xFFA08D80),
    outlineVariant = Color(0xFF534439),
    surfaceContainerLowest = Color(0xFF130D08),
    surfaceContainerLow = Color(0xFF221A14),
    surfaceContainer = Color(0xFF261E18),
    surfaceContainerHigh = Color(0xFF312822),
    surfaceContainerHighest = Color(0xFF3C332C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 6. Neutral M3 Tonal Fallback — Used by "System" palette on Android < 12 (API < 31)
val NeutralFallbackLightScheme = lightColorScheme(
    primary = Color(0xFF4A5C72),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3F5),
    onPrimaryContainer = Color(0xFF0A1D31),
    secondary = Color(0xFF58606B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE4F0),
    onSecondaryContainer = Color(0xFF151C26),
    tertiary = Color(0xFF675878),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDDCFF),
    onTertiaryContainer = Color(0xFF221631),
    background = Color(0xFFF9F9FC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF9F9FC),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2E9),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4F8),
    surfaceContainer = Color(0xFFEDEFF3),
    surfaceContainerHigh = Color(0xFFE7E9EE),
    surfaceContainerHighest = Color(0xFFE2E4E9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val NeutralFallbackDarkScheme = darkColorScheme(
    primary = Color(0xFFB3C7E0),
    onPrimary = Color(0xFF1D3044),
    primaryContainer = Color(0xFF33465B),
    onPrimaryContainer = Color(0xFFD6E3F5),
    secondary = Color(0xFFC0C8D4),
    onSecondary = Color(0xFF2A313C),
    secondaryContainer = Color(0xFF404853),
    onSecondaryContainer = Color(0xFFDCE4F0),
    tertiary = Color(0xFFD2C0E4),
    onTertiary = Color(0xFF382B47),
    tertiaryContainer = Color(0xFF4F415F),
    onTertiaryContainer = Color(0xFFEDDCFF),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6CE),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFF0D0E11),
    surfaceContainerLow = Color(0xFF1A1C1F),
    surfaceContainer = Color(0xFF1E2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

fun ExpressivePalette.colorScheme(darkTheme: Boolean): ColorScheme = when (this) {
    ExpressivePalette.TERRACOTTA -> if (darkTheme) TerracottaDarkScheme else TerracottaLightScheme
    ExpressivePalette.MATCHA -> if (darkTheme) MatchaDarkScheme else MatchaLightScheme
    ExpressivePalette.COBALT -> if (darkTheme) CobaltDarkScheme else CobaltLightScheme
    ExpressivePalette.BERRY -> if (darkTheme) BerryDarkScheme else BerryLightScheme
    ExpressivePalette.CITRUS -> if (darkTheme) CitrusDarkScheme else CitrusLightScheme
    ExpressivePalette.DYNAMIC -> if (darkTheme) NeutralFallbackDarkScheme else NeutralFallbackLightScheme
}
