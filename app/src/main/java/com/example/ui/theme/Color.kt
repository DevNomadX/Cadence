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
    DYNAMIC(
        id = "dynamic",
        displayName = "System",
        tagline = "Dynamic wallpaper colors",
        previewPrimary = Color(0xFF4F5D75),
        previewSecondary = Color(0xFF5D636E),
        previewTertiary = Color(0xFF6B5B7B),
        previewContainer = Color(0xFFDCE3F0)
    ),
    BERRY(
        id = "berry",
        displayName = "Berry Velvet",
        tagline = "Plum rose & blush",
        previewPrimary = Color(0xFFA62962),
        previewSecondary = Color(0xFF775662),
        previewTertiary = Color(0xFFC9688E),
        previewContainer = Color(0xFFFFD8E6)
    ),
    MATCHA(
        id = "matcha",
        displayName = "Matcha Grove",
        tagline = "Botanical sage & moss",
        previewPrimary = Color(0xFF2B6B46),
        previewSecondary = Color(0xFF52634F),
        previewTertiary = Color(0xFF7E9626),
        previewContainer = Color(0xFFC5F0D2)
    ),
    COBALT(
        id = "cobalt",
        displayName = "Cobalt Pulse",
        tagline = "Electric sapphire & sky",
        previewPrimary = Color(0xFF0052CC),
        previewSecondary = Color(0xFF4B607A),
        previewTertiary = Color(0xFF0084C7),
        previewContainer = Color(0xFFD6E4FF)
    ),
    CITRUS(
        id = "citrus",
        displayName = "Citrus Pop",
        tagline = "Saffron gold & amber",
        previewPrimary = Color(0xFFA86A00),
        previewSecondary = Color(0xFF755D37),
        previewTertiary = Color(0xFF5A7222),
        previewContainer = Color(0xFFFFE0A3)
    ),
    ABYSSAL_TEAL(
        id = "abyssal_teal",
        displayName = "Abyssal Teal",
        tagline = "Deep ocean & aqua",
        previewPrimary = Color(0xFF006876),
        previewSecondary = Color(0xFF436368),
        previewTertiary = Color(0xFF0E7C6B),
        previewContainer = Color(0xFF9EEFFD)
    ),
    CRIMSON_EMBER(
        id = "crimson_ember",
        displayName = "Crimson Ember",
        tagline = "Scarlet ruby & coral",
        previewPrimary = Color(0xFFB81D24),
        previewSecondary = Color(0xFF7C5450),
        previewTertiary = Color(0xFFD15B3E),
        previewContainer = Color(0xFFFFDAD6)
    ),
    CYBER_VIOLET(
        id = "cyber_violet",
        displayName = "Cyber Violet",
        tagline = "Neon iris & ultraviolet",
        previewPrimary = Color(0xFF6E38E0),
        previewSecondary = Color(0xFF5F567A),
        previewTertiary = Color(0xFFA33E9B),
        previewContainer = Color(0xFFEADDFF)
    ),
    DESERT_COPPER(
        id = "desert_copper",
        displayName = "Desert Copper",
        tagline = "Burnished bronze & sand",
        previewPrimary = Color(0xFF9E4720),
        previewSecondary = Color(0xFF745848),
        previewTertiary = Color(0xFF6C6234),
        previewContainer = Color(0xFFFFDBCA)
    ),
    OBSIDIAN_MONO(
        id = "obsidian_mono",
        displayName = "Obsidian Mono",
        tagline = "Pure graphite & silver",
        previewPrimary = Color(0xFF262626),
        previewSecondary = Color(0xFF666666),
        previewTertiary = Color(0xFFA3A3A3),
        previewContainer = Color(0xFFE3E3E3)
    );

    companion object {
        val DEFAULT: ExpressivePalette = BERRY

        fun fromId(id: String?): ExpressivePalette = runCatching {
            when (val normalized = id?.trim()?.lowercase()) {
                null, "" -> DEFAULT
                "system", "dynamic" -> DYNAMIC
                else -> entries.firstOrNull { it.id == normalized } ?: DEFAULT
            }
        }.getOrDefault(DEFAULT)
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

// 1. System Fallback — Neutral M3 tonal fallback on Android < 12 (API < 31)
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

// 2. Berry Velvet — Cool wine-plum surfaces with soft magenta-rose & blush accents
val BerryLightScheme = lightColorScheme(
    primary = Color(0xFFA0245D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD8E6),
    onPrimaryContainer = Color(0xFF3D001E),
    secondary = Color(0xFF745562),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFCD9E7),
    onSecondaryContainer = Color(0xFF2B141E),
    tertiary = Color(0xFF8C4A6E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8EC),
    onTertiaryContainer = Color(0xFF370A25),
    background = Color(0xFFFFF7FA),
    onBackground = Color(0xFF22181D),
    surface = Color(0xFFFFF7FA),
    onSurface = Color(0xFF22181D),
    surfaceVariant = Color(0xFFF3DCE5),
    onSurfaceVariant = Color(0xFF524249),
    outline = Color(0xFF84717A),
    outlineVariant = Color(0xFFD7C0C9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFEFF5),
    surfaceContainer = Color(0xFFF9E8EF),
    surfaceContainerHigh = Color(0xFFF3E2E9),
    surfaceContainerHighest = Color(0xFFEDDCE3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val BerryDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB0CE),
    onPrimary = Color(0xFF620033),
    primaryContainer = Color(0xFF820846),
    onPrimaryContainer = Color(0xFFFFD8E6),
    secondary = Color(0xFFE3BCCB),
    onSecondary = Color(0xFF422833),
    secondaryContainer = Color(0xFF5B3E4A),
    onSecondaryContainer = Color(0xFFFCD9E7),
    tertiary = Color(0xFFF8B2D8),
    onTertiary = Color(0xFF511C3B),
    tertiaryContainer = Color(0xFF6E3354),
    onTertiaryContainer = Color(0xFFFFD8EC),
    background = Color(0xFF191016),
    onBackground = Color(0xFFF0DFE6),
    surface = Color(0xFF191016),
    onSurface = Color(0xFFF0DFE6),
    surfaceVariant = Color(0xFF524249),
    onSurfaceVariant = Color(0xFFD7C0C9),
    outline = Color(0xFF9F8B94),
    outlineVariant = Color(0xFF524249),
    surfaceContainerLowest = Color(0xFF130B10),
    surfaceContainerLow = Color(0xFF22181E),
    surfaceContainer = Color(0xFF281D24),
    surfaceContainerHigh = Color(0xFF33272E),
    surfaceContainerHighest = Color(0xFF3E3139),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 3. Matcha Grove — Deep forest-moss surfaces with warm botanical olive-sage accents
val MatchaLightScheme = lightColorScheme(
    primary = Color(0xFF246944),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB5F2CC),
    onPrimaryContainer = Color(0xFF002110),
    secondary = Color(0xFF4E6352),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8D3),
    onSecondaryContainer = Color(0xFF0C1F13),
    tertiary = Color(0xFF677815),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE7F992),
    onTertiaryContainer = Color(0xFF1D2400),
    background = Color(0xFFF5FBF4),
    onBackground = Color(0xFF161E18),
    surface = Color(0xFFF5FBF4),
    onSurface = Color(0xFF161E18),
    surfaceVariant = Color(0xFFDBE5DA),
    onSurfaceVariant = Color(0xFF3F4A41),
    outline = Color(0xFF6F7A70),
    outlineVariant = Color(0xFFBFCABF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF6EE),
    surfaceContainer = Color(0xFFE9F0E7),
    surfaceContainerHigh = Color(0xFFE3EBE1),
    surfaceContainerHighest = Color(0xFFDDE5DB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val MatchaDarkScheme = darkColorScheme(
    primary = Color(0xFF94D7AF),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF07512E),
    onPrimaryContainer = Color(0xFFB5F2CC),
    secondary = Color(0xFFB5CCB8),
    onSecondary = Color(0xFF203526),
    secondaryContainer = Color(0xFF374C3B),
    onSecondaryContainer = Color(0xFFD1E8D3),
    tertiary = Color(0xFFCBDD78),
    onTertiary = Color(0xFF323C00),
    tertiaryContainer = Color(0xFF4B5900),
    onTertiaryContainer = Color(0xFFE7F992),
    background = Color(0xFF0E1510),
    onBackground = Color(0xFFDDE5DB),
    surface = Color(0xFF0E1510),
    onSurface = Color(0xFFDDE5DB),
    surfaceVariant = Color(0xFF3F4A41),
    onSurfaceVariant = Color(0xFFBFCABF),
    outline = Color(0xFF89948A),
    outlineVariant = Color(0xFF3F4A41),
    surfaceContainerLowest = Color(0xFF09100B),
    surfaceContainerLow = Color(0xFF161E18),
    surfaceContainer = Color(0xFF1B231D),
    surfaceContainerHigh = Color(0xFF252D27),
    surfaceContainerHighest = Color(0xFF303832),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 4. Cobalt Pulse — Deep midnight-navy surfaces with pure royal sapphire & sky-blue accents (zero violet)
val CobaltLightScheme = lightColorScheme(
    primary = Color(0xFF0052CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001944),
    secondary = Color(0xFF4A607A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E4FA),
    onSecondaryContainer = Color(0xFF071D34),
    tertiary = Color(0xFF0077B6),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC5EBFF),
    onTertiaryContainer = Color(0xFF002033),
    background = Color(0xFFF5F9FF),
    onBackground = Color(0xFF141C26),
    surface = Color(0xFFF5F9FF),
    onSurface = Color(0xFF141C26),
    surfaceVariant = Color(0xFFDCE4F2),
    onSurfaceVariant = Color(0xFF3E4859),
    outline = Color(0xFF6E798B),
    outlineVariant = Color(0xFFBDC8DA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF4FE),
    surfaceContainer = Color(0xFFE7EEF9),
    surfaceContainerHigh = Color(0xFFE0E8F5),
    surfaceContainerHighest = Color(0xFFD9E2F0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val CobaltDarkScheme = darkColorScheme(
    primary = Color(0xFF8AB8FF),
    onPrimary = Color(0xFF002B6D),
    primaryContainer = Color(0xFF003FA0),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB3C9E6),
    onSecondary = Color(0xFF1D324A),
    secondaryContainer = Color(0xFF344962),
    onSecondaryContainer = Color(0xFFD3E4FA),
    tertiary = Color(0xFF6FD0FF),
    onTertiary = Color(0xFF003552),
    tertiaryContainer = Color(0xFF004D75),
    onTertiaryContainer = Color(0xFFC5EBFF),
    background = Color(0xFF0B121E),
    onBackground = Color(0xFFDCE4F2),
    surface = Color(0xFF0B121E),
    onSurface = Color(0xFFDCE4F2),
    surfaceVariant = Color(0xFF3E4859),
    onSurfaceVariant = Color(0xFFBDC8DA),
    outline = Color(0xFF8793A6),
    outlineVariant = Color(0xFF3E4859),
    surfaceContainerLowest = Color(0xFF070D17),
    surfaceContainerLow = Color(0xFF121A28),
    surfaceContainer = Color(0xFF17202F),
    surfaceContainerHigh = Color(0xFF212B3B),
    surfaceContainerHighest = Color(0xFF2B3648),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 5. Citrus Pop — Rich roasted-amber surfaces with bright golden marigold accents
val CitrusLightScheme = lightColorScheme(
    primary = Color(0xFF9C6200),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDFA0),
    onPrimaryContainer = Color(0xFF321D00),
    secondary = Color(0xFF715C38),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFCE0B2),
    onSecondaryContainer = Color(0xFF271A02),
    tertiary = Color(0xFF536923),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6F19A),
    onTertiaryContainer = Color(0xFF152000),
    background = Color(0xFFFFFBF2),
    onBackground = Color(0xFF201B11),
    surface = Color(0xFFFFFBF2),
    onSurface = Color(0xFF201B11),
    surfaceVariant = Color(0xFFF0E4CA),
    onSurfaceVariant = Color(0xFF4F4634),
    outline = Color(0xFF817661),
    outlineVariant = Color(0xFFD3C7AE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF4E6),
    surfaceContainer = Color(0xFFF4EEDE),
    surfaceContainerHigh = Color(0xFFEEE8D6),
    surfaceContainerHighest = Color(0xFFE8E1CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val CitrusDarkScheme = darkColorScheme(
    primary = Color(0xFFFFCA5C),
    onPrimary = Color(0xFF503000),
    primaryContainer = Color(0xFF744800),
    onPrimaryContainer = Color(0xFFFFDFA0),
    secondary = Color(0xFFDFC498),
    onSecondary = Color(0xFF3F2E0E),
    secondaryContainer = Color(0xFF574423),
    onSecondaryContainer = Color(0xFFFCE0B2),
    tertiary = Color(0xFFBAD480),
    onTertiary = Color(0xFF263600),
    tertiaryContainer = Color(0xFF3C500C),
    onTertiaryContainer = Color(0xFFD6F19A),
    background = Color(0xFF18140B),
    onBackground = Color(0xFFEDE4D2),
    surface = Color(0xFF18140B),
    onSurface = Color(0xFFEDE4D2),
    surfaceVariant = Color(0xFF4F4634),
    onSurfaceVariant = Color(0xFFD3C7AE),
    outline = Color(0xFF9C907A),
    outlineVariant = Color(0xFF4F4634),
    surfaceContainerLowest = Color(0xFF120F07),
    surfaceContainerLow = Color(0xFF211C11),
    surfaceContainer = Color(0xFF262115),
    surfaceContainerHigh = Color(0xFF312B1E),
    surfaceContainerHighest = Color(0xFF3C3628),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 6. Abyssal Teal — Deep pelagic ocean-teal surfaces with crisp cyan-aqua & seafoam accents
val AbyssalTealLightScheme = lightColorScheme(
    primary = Color(0xFF006876),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EEFFD),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = Color(0xFF436368),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC6E8EE),
    onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFF0E7A69),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA2F3E0),
    onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFF1FAFB),
    onBackground = Color(0xFF131D1F),
    surface = Color(0xFFF1FAFB),
    onSurface = Color(0xFF131D1F),
    surfaceVariant = Color(0xFFD5E5E8),
    onSurfaceVariant = Color(0xFF3B494C),
    outline = Color(0xFF6B7A7D),
    outlineVariant = Color(0xFFB9C9CC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEBF5F7),
    surfaceContainer = Color(0xFFE4EFF1),
    surfaceContainerHigh = Color(0xFFDDE9EC),
    surfaceContainerHighest = Color(0xFFD6E3E6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val AbyssalTealDarkScheme = darkColorScheme(
    primary = Color(0xFF55D6E6),
    onPrimary = Color(0xFF00363E),
    primaryContainer = Color(0xFF004F5A),
    onPrimaryContainer = Color(0xFF9EEFFD),
    secondary = Color(0xFFAACCCF),
    onSecondary = Color(0xFF153439),
    secondaryContainer = Color(0xFF2C4B50),
    onSecondaryContainer = Color(0xFFC6E8EE),
    tertiary = Color(0xFF7FD9C6),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005145),
    onTertiaryContainer = Color(0xFFA2F3E0),
    background = Color(0xFF071618),
    onBackground = Color(0xFFD7E5E8),
    surface = Color(0xFF071618),
    onSurface = Color(0xFFD7E5E8),
    surfaceVariant = Color(0xFF3B494C),
    onSurfaceVariant = Color(0xFFB9C9CC),
    outline = Color(0xFF839396),
    outlineVariant = Color(0xFF3B494C),
    surfaceContainerLowest = Color(0xFF041012),
    surfaceContainerLow = Color(0xFF0E1E21),
    surfaceContainer = Color(0xFF132427),
    surfaceContainerHigh = Color(0xFF1D2F33),
    surfaceContainerHighest = Color(0xFF273A3E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 7. Crimson Ember — Deep garnet-charcoal surfaces with vivid scarlet-ruby & warm coral accents
val CrimsonEmberLightScheme = lightColorScheme(
    primary = Color(0xFFB81D24),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410004),
    secondary = Color(0xFF7C534E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF30130F),
    tertiary = Color(0xFFB5492B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBD0),
    onTertiaryContainer = Color(0xFF3B0D00),
    background = Color(0xFFFFF7F6),
    onBackground = Color(0xFF231818),
    surface = Color(0xFFFFF7F6),
    onSurface = Color(0xFF231818),
    surfaceVariant = Color(0xFFF6DCDA),
    onSurfaceVariant = Color(0xFF554240),
    outline = Color(0xFF88716F),
    outlineVariant = Color(0xFFDBC0BE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFEFEE),
    surfaceContainer = Color(0xFFFBE8E6),
    surfaceContainerHigh = Color(0xFFF5E1DF),
    surfaceContainerHighest = Color(0xFFEFDBD9),
    error = Color(0xFF93000A),
    onError = Color(0xFFFFFFFF)
)

val CrimsonEmberDarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A8E),
    onPrimary = Color(0xFF68000A),
    primaryContainer = Color(0xFF910914),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFECB9B3),
    onSecondary = Color(0xFF482622),
    secondaryContainer = Color(0xFF623C38),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFFFB59E),
    onTertiary = Color(0xFF611C06),
    tertiaryContainer = Color(0xFF873217),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = Color(0xFF1B0F10),
    onBackground = Color(0xFFF3DDD9),
    surface = Color(0xFF1B0F10),
    onSurface = Color(0xFFF3DDD9),
    surfaceVariant = Color(0xFF554240),
    onSurfaceVariant = Color(0xFFDBC0BE),
    outline = Color(0xFFA38B89),
    outlineVariant = Color(0xFF554240),
    surfaceContainerLowest = Color(0xFF14090A),
    surfaceContainerLow = Color(0xFF241617),
    surfaceContainer = Color(0xFF2A1B1C),
    surfaceContainerHigh = Color(0xFF352526),
    surfaceContainerHighest = Color(0xFF403031),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 8. Cyber Violet — Deep midnight-indigo surfaces with vivid electric ultraviolet accents
val CyberVioletLightScheme = lightColorScheme(
    primary = Color(0xFF6E38E0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF5E5579),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5DEFF),
    onSecondaryContainer = Color(0xFF1A1332),
    tertiary = Color(0xFF9B3895),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD6F9),
    onTertiaryContainer = Color(0xFF3A0038),
    background = Color(0xFFFAF7FF),
    onBackground = Color(0xFF1B1824),
    surface = Color(0xFFFAF7FF),
    onSurface = Color(0xFF1B1824),
    surfaceVariant = Color(0xFFE6DFF2),
    onSurfaceVariant = Color(0xFF474254),
    outline = Color(0xFF787286),
    outlineVariant = Color(0xFFC9C2D6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4EFFC),
    surfaceContainer = Color(0xFFEEE8F7),
    surfaceContainerHigh = Color(0xFFE8E2F2),
    surfaceContainerHighest = Color(0xFFE2DBEC),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val CyberVioletDarkScheme = darkColorScheme(
    primary = Color(0xFFCBB2FF),
    onPrimary = Color(0xFF390093),
    primaryContainer = Color(0xFF5318C4),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFC8BEE6),
    onSecondary = Color(0xFF302849),
    secondaryContainer = Color(0xFF473E61),
    onSecondaryContainer = Color(0xFFE5DEFF),
    tertiary = Color(0xFFFFA9F6),
    onTertiary = Color(0xFF5E055B),
    tertiaryContainer = Color(0xFF7E2079),
    onTertiaryContainer = Color(0xFFFFD6F9),
    background = Color(0xFF120F1D),
    onBackground = Color(0xFFE6E0F0),
    surface = Color(0xFF120F1D),
    onSurface = Color(0xFFE6E0F0),
    surfaceVariant = Color(0xFF474254),
    onSurfaceVariant = Color(0xFFC9C2D6),
    outline = Color(0xFF928CA0),
    outlineVariant = Color(0xFF474254),
    surfaceContainerLowest = Color(0xFF0C0A16),
    surfaceContainerLow = Color(0xFF1A1627),
    surfaceContainer = Color(0xFF201B2E),
    surfaceContainerHigh = Color(0xFF2A253A),
    surfaceContainerHighest = Color(0xFF352F46),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 9. Desert Copper — Warm dark espresso-bronze surfaces with metallic copper-terracotta & sand accents
val DesertCopperLightScheme = lightColorScheme(
    primary = Color(0xFF9E4720),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCA),
    onPrimaryContainer = Color(0xFF371100),
    secondary = Color(0xFF745848),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC9),
    onSecondaryContainer = Color(0xFF2B160B),
    tertiary = Color(0xFF6B6233),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4E7AC),
    onTertiaryContainer = Color(0xFF221D00),
    background = Color(0xFFFBF6F1),
    onBackground = Color(0xFF211A16),
    surface = Color(0xFFFBF6F1),
    onSurface = Color(0xFF211A16),
    surfaceVariant = Color(0xFFF0DFD4),
    onSurfaceVariant = Color(0xFF51443C),
    outline = Color(0xFF83746B),
    outlineVariant = Color(0xFFD5C3B8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6EFE8),
    surfaceContainer = Color(0xFFF0E8E0),
    surfaceContainerHigh = Color(0xFFEAE1D9),
    surfaceContainerHighest = Color(0xFFE4DBD2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val DesertCopperDarkScheme = darkColorScheme(
    primary = Color(0xFFF2A57E),
    onPrimary = Color(0xFF581F03),
    primaryContainer = Color(0xFF7E3311),
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFE4BFA9),
    onSecondary = Color(0xFF422B1E),
    secondaryContainer = Color(0xFF5A4132),
    onSecondaryContainer = Color(0xFFFFDCC9),
    tertiary = Color(0xFFD7CB92),
    onTertiary = Color(0xFF3A3309),
    tertiaryContainer = Color(0xFF524A1E),
    onTertiaryContainer = Color(0xFFF4E7AC),
    background = Color(0xFF18120E),
    onBackground = Color(0xFFECE0D8),
    surface = Color(0xFF18120E),
    onSurface = Color(0xFFECE0D8),
    surfaceVariant = Color(0xFF51443C),
    onSurfaceVariant = Color(0xFFD5C3B8),
    outline = Color(0xFF9E8D83),
    outlineVariant = Color(0xFF51443C),
    surfaceContainerLowest = Color(0xFF120D0A),
    surfaceContainerLow = Color(0xFF211914),
    surfaceContainer = Color(0xFF271E19),
    surfaceContainerHigh = Color(0xFF322822),
    surfaceContainerHighest = Color(0xFF3D332D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 10. Obsidian Mono — Strictly achromatic carbon-black & silver studio scheme (R == G == B)
val ObsidianMonoLightScheme = lightColorScheme(
    primary = Color(0xFF222222),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF595959),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color(0xFF1C1C1C),
    tertiary = Color(0xFF737373),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDEDED),
    onTertiaryContainer = Color(0xFF242424),
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF161616),
    surface = Color(0xFFF7F7F7),
    onSurface = Color(0xFF161616),
    surfaceVariant = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFF474747),
    outline = Color(0xFF787878),
    outlineVariant = Color(0xFFC9C9C9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F2),
    surfaceContainer = Color(0xFFECECEC),
    surfaceContainerHigh = Color(0xFFE6E6E6),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val ObsidianMonoDarkScheme = darkColorScheme(
    primary = Color(0xFFE4E4E4),
    onPrimary = Color(0xFF141414),
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF262626),
    secondaryContainer = Color(0xFF3D3D3D),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFFA6A6A6),
    onTertiary = Color(0xFF242424),
    tertiaryContainer = Color(0xFF424242),
    onTertiaryContainer = Color(0xFFE6E6E6),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF0E0E0E),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF454545),
    onSurfaceVariant = Color(0xFFC4C4C4),
    outline = Color(0xFF8C8C8C),
    outlineVariant = Color(0xFF454545),
    surfaceContainerLowest = Color(0xFF080808),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF323232),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

fun ExpressivePalette.colorScheme(darkTheme: Boolean): ColorScheme = when (this) {
    ExpressivePalette.DYNAMIC -> if (darkTheme) NeutralFallbackDarkScheme else NeutralFallbackLightScheme
    ExpressivePalette.BERRY -> if (darkTheme) BerryDarkScheme else BerryLightScheme
    ExpressivePalette.MATCHA -> if (darkTheme) MatchaDarkScheme else MatchaLightScheme
    ExpressivePalette.COBALT -> if (darkTheme) CobaltDarkScheme else CobaltLightScheme
    ExpressivePalette.CITRUS -> if (darkTheme) CitrusDarkScheme else CitrusLightScheme
    ExpressivePalette.ABYSSAL_TEAL -> if (darkTheme) AbyssalTealDarkScheme else AbyssalTealLightScheme
    ExpressivePalette.CRIMSON_EMBER -> if (darkTheme) CrimsonEmberDarkScheme else CrimsonEmberLightScheme
    ExpressivePalette.CYBER_VIOLET -> if (darkTheme) CyberVioletDarkScheme else CyberVioletLightScheme
    ExpressivePalette.DESERT_COPPER -> if (darkTheme) DesertCopperDarkScheme else DesertCopperLightScheme
    ExpressivePalette.OBSIDIAN_MONO -> if (darkTheme) ObsidianMonoDarkScheme else ObsidianMonoLightScheme
}
