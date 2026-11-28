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
        tagline = "Jade & pistachio",
        previewPrimary = Color(0xFF5CD685),
        previewSecondary = Color(0xFF15803D),
        previewTertiary = Color(0xFFBDF4C2),
        previewContainer = Color(0xFFC8F7D6)
    ),
    COBALT(
        id = "cobalt",
        displayName = "Cobalt Pulse",
        tagline = "Cobalt & aurora sky",
        previewPrimary = Color(0xFF1D4ED8),
        previewSecondary = Color(0xFF60A5FA),
        previewTertiary = Color(0xFF93C5FD),
        previewContainer = Color(0xFFDBEAFE)
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
    ARCTIC_CYAN(
        id = "arctic_cyan",
        displayName = "Arctic Cyan",
        tagline = "Ice cyan & cool sky",
        previewPrimary = Color(0xFF67E8F9),
        previewSecondary = Color(0xFF006874),
        previewTertiary = Color(0xFFA5B4FC),
        previewContainer = Color(0xFFA6EEFF)
    ),
    PEACH_BLOSSOM(
        id = "peach_blossom",
        displayName = "Peach Blossom",
        tagline = "Coral peach & sakura",
        previewPrimary = Color(0xFFFF9E80),
        previewSecondary = Color(0xFFB94324),
        previewTertiary = Color(0xFFF9A8D4),
        previewContainer = Color(0xFFFFDBD0)
    ),
    WISTERIA_MIST(
        id = "wisteria_mist",
        displayName = "Wisteria Mist",
        tagline = "Pastel lilac & sky",
        previewPrimary = Color(0xFFD8B4FE),
        previewSecondary = Color(0xFF6D42B5),
        previewTertiary = Color(0xFF7DD3FC),
        previewContainer = Color(0xFFEFE0FF)
    ),
    SPEARMINT_FIZZ(
        id = "spearmint_fizz",
        displayName = "Spearmint Fizz",
        tagline = "Fresh mint & lemon",
        previewPrimary = Color(0xFF5EEAD4),
        previewSecondary = Color(0xFF0F766E),
        previewTertiary = Color(0xFFFDE68A),
        previewContainer = Color(0xFFB5F9EC)
    ),
    OBSIDIAN_MONO(
        id = "obsidian_mono",
        displayName = "Obsidian Mono",
        tagline = "Onyx & platinum",
        previewPrimary = Color(0xFF14161A),
        previewSecondary = Color(0xFF9BA2B0),
        previewTertiary = Color(0xFFF1F3F5),
        previewContainer = Color(0xFFE3E6EC)
    );

    companion object {
        val DEFAULT: ExpressivePalette = BERRY
        val LEGACY_FALLBACK: ExpressivePalette = MATCHA

        fun fromId(id: String?): ExpressivePalette = runCatching {
            when (val normalized = id?.trim()?.lowercase()) {
                null, "" -> DEFAULT
                "system", "dynamic" -> DYNAMIC
                else -> entries.firstOrNull { it.id == normalized } ?: LEGACY_FALLBACK
            }
        }.getOrDefault(LEGACY_FALLBACK)
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

// 3. Matcha Grove — Crisp botanical jade surfaces with vibrant emerald & pistachio accents
val MatchaLightScheme = lightColorScheme(
    primary = Color(0xFF15803D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F7D6),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF4E6655),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1ECD8),
    onSecondaryContainer = Color(0xFF0C2014),
    tertiary = Color(0xFF4D7C0F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9F99D),
    onTertiaryContainer = Color(0xFF152700),
    background = Color(0xFFF4FAF5),
    onBackground = Color(0xFF131E16),
    surface = Color(0xFFF4FAF5),
    onSurface = Color(0xFF131E16),
    surfaceVariant = Color(0xFFD7E6DB),
    onSurfaceVariant = Color(0xFF3B4A40),
    outline = Color(0xFF6B7B70),
    outlineVariant = Color(0xFFBACBC0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEDF6EF),
    surfaceContainer = Color(0xFFE6F1E9),
    surfaceContainerHigh = Color(0xFFDFECE2),
    surfaceContainerHighest = Color(0xFFD8E7DC),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val MatchaDarkScheme = darkColorScheme(
    primary = Color(0xFF5CD685),
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFC8F7D6),
    secondary = Color(0xFF88BA9A),
    onSecondary = Color(0xFF0E341D),
    secondaryContainer = Color(0xFF214A31),
    onSecondaryContainer = Color(0xFFD2F3DC),
    tertiary = Color(0xFFBDF4C2),
    onTertiary = Color(0xFF14381A),
    tertiaryContainer = Color(0xFF295130),
    onTertiaryContainer = Color(0xFFDCFBE0),
    background = Color(0xFF0B140E),
    onBackground = Color(0xFFDDE8E0),
    surface = Color(0xFF0B140E),
    onSurface = Color(0xFFDDE8E0),
    surfaceVariant = Color(0xFF394B3F),
    onSurfaceVariant = Color(0xFFB6C9BC),
    outline = Color(0xFF819588),
    outlineVariant = Color(0xFF394B3F),
    surfaceContainerLowest = Color(0xFF070E09),
    surfaceContainerLow = Color(0xFF111D15),
    surfaceContainer = Color(0xFF16261B),
    surfaceContainerHigh = Color(0xFF1F3326),
    surfaceContainerHighest = Color(0xFF294131),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 4. Cobalt Pulse — Deep midnight-navy surfaces with pure cobalt & aurora-sky accents (zero violet)
val CobaltLightScheme = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF00194A),
    secondary = Color(0xFF4E607A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4FA),
    onSecondaryContainer = Color(0xFF0A1D35),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDEEFF),
    onTertiaryContainer = Color(0xFF001F33),
    background = Color(0xFFF5F8FF),
    onBackground = Color(0xFF131A29),
    surface = Color(0xFFF5F8FF),
    onSurface = Color(0xFF131A29),
    surfaceVariant = Color(0xFFDCE3F4),
    onSurfaceVariant = Color(0xFF3D475C),
    outline = Color(0xFF6D788F),
    outlineVariant = Color(0xFFBCC6DC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF3FE),
    surfaceContainer = Color(0xFFE7EEFA),
    surfaceContainerHigh = Color(0xFFDFE7F6),
    surfaceContainerHighest = Color(0xFFD8E1F2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val CobaltDarkScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF1D4ED8),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF93B4DC),
    onSecondary = Color(0xFF102E4F),
    secondaryContainer = Color(0xFF244369),
    onSecondaryContainer = Color(0xFFD8E9FF),
    tertiary = Color(0xFF93C5FD),
    onTertiary = Color(0xFF00325A),
    tertiaryContainer = Color(0xFF0D4A82),
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF0B0F1A),
    onBackground = Color(0xFFDEE4F3),
    surface = Color(0xFF0B0F1A),
    onSurface = Color(0xFFDEE4F3),
    surfaceVariant = Color(0xFF3C465C),
    onSurfaceVariant = Color(0xFFBAC6DE),
    outline = Color(0xFF8491AB),
    outlineVariant = Color(0xFF3C465C),
    surfaceContainerLowest = Color(0xFF070A12),
    surfaceContainerLow = Color(0xFF111726),
    surfaceContainer = Color(0xFF171F33),
    surfaceContainerHigh = Color(0xFF202A42),
    surfaceContainerHighest = Color(0xFF2A3654),
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

// 6. Arctic Cyan — Glacial slate-cyan surfaces with ice cyan & cool periwinkle-sky dual accents
val ArcticCyanLightScheme = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6EEFF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF4A626B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE7F0),
    onSecondaryContainer = Color(0xFF061E26),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF120C6E),
    background = Color(0xFFF4FAFC),
    onBackground = Color(0xFF141D20),
    surface = Color(0xFFF4FAFC),
    onSurface = Color(0xFF141D20),
    surfaceVariant = Color(0xFFD7E6EB),
    onSurfaceVariant = Color(0xFF3C4A4F),
    outline = Color(0xFF6B7B80),
    outlineVariant = Color(0xFFBACBCE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEDF5F8),
    surfaceContainer = Color(0xFFE6F0F3),
    surfaceContainerHigh = Color(0xFFDFEAEE),
    surfaceContainerHighest = Color(0xFFD8E4E9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val ArcticCyanDarkScheme = darkColorScheme(
    primary = Color(0xFF67E8F9),
    onPrimary = Color(0xFF00363E),
    primaryContainer = Color(0xFF004F59),
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = Color(0xFF94B8C4),
    onSecondary = Color(0xFF0D333D),
    secondaryContainer = Color(0xFF234A55),
    onSecondaryContainer = Color(0xFFCBEAF4),
    tertiary = Color(0xFFA5B4FC),
    onTertiary = Color(0xFF1C2366),
    tertiaryContainer = Color(0xFF323C8C),
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF0E1518),
    onBackground = Color(0xFFDBE6EA),
    surface = Color(0xFF0E1518),
    onSurface = Color(0xFFDBE6EA),
    surfaceVariant = Color(0xFF3B4A50),
    onSurfaceVariant = Color(0xFFB8CBD1),
    outline = Color(0xFF82959B),
    outlineVariant = Color(0xFF3B4A50),
    surfaceContainerLowest = Color(0xFF091012),
    surfaceContainerLow = Color(0xFF141D21),
    surfaceContainer = Color(0xFF192429),
    surfaceContainerHigh = Color(0xFF222F35),
    surfaceContainerHighest = Color(0xFF2B3B42),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 7. Peach Blossom — Warm rosewood-blush surfaces with coral peach & sakura dual accents
val PeachBlossomLightScheme = lightColorScheme(
    primary = Color(0xFFB94324),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3C0E02),
    secondary = Color(0xFF77564E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDD4),
    onSecondaryContainer = Color(0xFF2C150F),
    tertiary = Color(0xFFB83276),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8EC),
    onTertiaryContainer = Color(0xFF3E0022),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231918),
    surfaceVariant = Color(0xFFF6DFDA),
    onSurfaceVariant = Color(0xFF544340),
    outline = Color(0xFF86726F),
    outlineVariant = Color(0xFFD9C2BE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EC),
    surfaceContainer = Color(0xFFFAE8E4),
    surfaceContainerHigh = Color(0xFFF4E1DC),
    surfaceContainerHighest = Color(0xFFEEDBD6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val PeachBlossomDarkScheme = darkColorScheme(
    primary = Color(0xFFFF9E80),
    onPrimary = Color(0xFF541D0B),
    primaryContainer = Color(0xFF7A2E17),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFE6B5A8),
    onSecondary = Color(0xFF44251D),
    secondaryContainer = Color(0xFF5D3A31),
    onSecondaryContainer = Color(0xFFFFDDD4),
    tertiary = Color(0xFFF9A8D4),
    onTertiary = Color(0xFF531538),
    tertiaryContainer = Color(0xFF702A51),
    onTertiaryContainer = Color(0xFFFFD8EC),
    background = Color(0xFF1A1213),
    onBackground = Color(0xFFF3E0DF),
    surface = Color(0xFF1A1213),
    onSurface = Color(0xFFF3E0DF),
    surfaceVariant = Color(0xFF534242),
    onSurfaceVariant = Color(0xFFD8C2C1),
    outline = Color(0xFFA18C8B),
    outlineVariant = Color(0xFF534242),
    surfaceContainerLowest = Color(0xFF140D0E),
    surfaceContainerLow = Color(0xFF221819),
    surfaceContainer = Color(0xFF291E20),
    surfaceContainerHigh = Color(0xFF342628),
    surfaceContainerHighest = Color(0xFF403032),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 8. Wisteria Mist — Soft twilight-lavender surfaces with pastel lilac & airy sky dual accents
val WisteriaMistLightScheme = lightColorScheme(
    primary = Color(0xFF6D42B5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEFE0FF),
    onPrimaryContainer = Color(0xFF23005A),
    secondary = Color(0xFF635B70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DEF8),
    onSecondaryContainer = Color(0xFF1F182A),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCBEFFF),
    onTertiaryContainer = Color(0xFF001E2C),
    background = Color(0xFFFAF7FF),
    onBackground = Color(0xFF1B1823),
    surface = Color(0xFFFAF7FF),
    onSurface = Color(0xFF1B1823),
    surfaceVariant = Color(0xFFE8E0F2),
    onSurfaceVariant = Color(0xFF484353),
    outline = Color(0xFF797385),
    outlineVariant = Color(0xFFCBC3D6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4EFFC),
    surfaceContainer = Color(0xFFEEE8F7),
    surfaceContainerHigh = Color(0xFFE8E1F2),
    surfaceContainerHighest = Color(0xFFE2DBEC),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val WisteriaMistDarkScheme = darkColorScheme(
    primary = Color(0xFFD8B4FE),
    onPrimary = Color(0xFF3B1A66),
    primaryContainer = Color(0xFF532B88),
    onPrimaryContainer = Color(0xFFEFE0FF),
    secondary = Color(0xFFC4B7D6),
    onSecondary = Color(0xFF2E243D),
    secondaryContainer = Color(0xFF453956),
    onSecondaryContainer = Color(0xFFE7DCF7),
    tertiary = Color(0xFF7DD3FC),
    onTertiary = Color(0xFF003549),
    tertiaryContainer = Color(0xFF004D69),
    onTertiaryContainer = Color(0xFFCBEFFF),
    background = Color(0xFF14121B),
    onBackground = Color(0xFFE7E2F0),
    surface = Color(0xFF14121B),
    onSurface = Color(0xFFE7E2F0),
    surfaceVariant = Color(0xFF484354),
    onSurfaceVariant = Color(0xFFCAC3D6),
    outline = Color(0xFF938DA0),
    outlineVariant = Color(0xFF484354),
    surfaceContainerLowest = Color(0xFF0E0C14),
    surfaceContainerLow = Color(0xFF1C1925),
    surfaceContainer = Color(0xFF221F2D),
    surfaceContainerHigh = Color(0xFF2D293A),
    surfaceContainerHighest = Color(0xFF383347),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 9. Spearmint Fizz — Crisp sea-mint surfaces with fresh spearmint & warm lemon dual accents
val SpearmintFizzLightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB5F9EC),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF06201B),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF08A),
    onTertiaryContainer = Color(0xFF2F1500),
    background = Color(0xFFF3FBF9),
    onBackground = Color(0xFF141E1C),
    surface = Color(0xFFF3FBF9),
    onSurface = Color(0xFF141E1C),
    surfaceVariant = Color(0xFFD8E7E4),
    onSurfaceVariant = Color(0xFF3C4B48),
    outline = Color(0xFF6B7C78),
    outlineVariant = Color(0xFFBACCC8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFECF6F4),
    surfaceContainer = Color(0xFFE5F0EE),
    surfaceContainerHigh = Color(0xFFDFEBE8),
    surfaceContainerHighest = Color(0xFFD8E5E2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val SpearmintFizzDarkScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF003830),
    primaryContainer = Color(0xFF005147),
    onPrimaryContainer = Color(0xFFB5F9EC),
    secondary = Color(0xFF98C9BF),
    onSecondary = Color(0xFF0F3630),
    secondaryContainer = Color(0xFF244D46),
    onSecondaryContainer = Color(0xFFCDEEE7),
    tertiary = Color(0xFFFDE68A),
    onTertiary = Color(0xFF3E2D00),
    tertiaryContainer = Color(0xFF5A4300),
    onTertiaryContainer = Color(0xFFFFF1BA),
    background = Color(0xFF0E1716),
    onBackground = Color(0xFFDCE8E5),
    surface = Color(0xFF0E1716),
    onSurface = Color(0xFFDCE8E5),
    surfaceVariant = Color(0xFF3B4C49),
    onSurfaceVariant = Color(0xFFB9CCC8),
    outline = Color(0xFF839692),
    outlineVariant = Color(0xFF3B4C49),
    surfaceContainerLowest = Color(0xFF091110),
    surfaceContainerLow = Color(0xFF14201E),
    surfaceContainer = Color(0xFF1A2826),
    surfaceContainerHigh = Color(0xFF233532),
    surfaceContainerHighest = Color(0xFF2D423F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// 10. Obsidian Mono — High-contrast onyx & platinum studio monochrome with subtle cool silver depth
val ObsidianMonoLightScheme = lightColorScheme(
    primary = Color(0xFF14161A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3E6EC),
    onPrimaryContainer = Color(0xFF14161A),
    secondary = Color(0xFF525866),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E9EF),
    onSecondaryContainer = Color(0xFF1A1D24),
    tertiary = Color(0xFF383E4A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDFE3EA),
    onTertiaryContainer = Color(0xFF15181F),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF14161A),
    surface = Color(0xFFF8F9FA),
    onSurface = Color(0xFF14161A),
    surfaceVariant = Color(0xFFE2E5EB),
    onSurfaceVariant = Color(0xFF444954),
    outline = Color(0xFF757B87),
    outlineVariant = Color(0xFFC5CAD3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F7),
    surfaceContainer = Color(0xFFECEEF2),
    surfaceContainerHigh = Color(0xFFE6E9EE),
    surfaceContainerHighest = Color(0xFFE0E3E9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

val ObsidianMonoDarkScheme = darkColorScheme(
    primary = Color(0xFFF1F3F5),
    onPrimary = Color(0xFF111318),
    primaryContainer = Color(0xFF2B303A),
    onPrimaryContainer = Color(0xFFF1F3F5),
    secondary = Color(0xFF9BA2B0),
    onSecondary = Color(0xFF1B1F27),
    secondaryContainer = Color(0xFF343A46),
    onSecondaryContainer = Color(0xFFE4E7ED),
    tertiary = Color(0xFFCFD4DE),
    onTertiary = Color(0xFF21252E),
    tertiaryContainer = Color(0xFF3B414E),
    onTertiaryContainer = Color(0xFFECEFF4),
    background = Color(0xFF0C0D10),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF0C0D10),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF424752),
    onSurfaceVariant = Color(0xFFC2C7D0),
    outline = Color(0xFF8B919E),
    outlineVariant = Color(0xFF424752),
    surfaceContainerLowest = Color(0xFF07080A),
    surfaceContainerLow = Color(0xFF13151A),
    surfaceContainer = Color(0xFF1A1D24),
    surfaceContainerHigh = Color(0xFF232730),
    surfaceContainerHighest = Color(0xFF2D323D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

fun ExpressivePalette.colorScheme(darkTheme: Boolean): ColorScheme = when (this) {
    ExpressivePalette.DYNAMIC -> if (darkTheme) NeutralFallbackDarkScheme else NeutralFallbackLightScheme
    ExpressivePalette.BERRY -> if (darkTheme) BerryDarkScheme else BerryLightScheme
    ExpressivePalette.MATCHA -> if (darkTheme) MatchaDarkScheme else MatchaLightScheme
    ExpressivePalette.COBALT -> if (darkTheme) CobaltDarkScheme else CobaltLightScheme
    ExpressivePalette.CITRUS -> if (darkTheme) CitrusDarkScheme else CitrusLightScheme
    ExpressivePalette.ARCTIC_CYAN -> if (darkTheme) ArcticCyanDarkScheme else ArcticCyanLightScheme
    ExpressivePalette.PEACH_BLOSSOM -> if (darkTheme) PeachBlossomDarkScheme else PeachBlossomLightScheme
    ExpressivePalette.WISTERIA_MIST -> if (darkTheme) WisteriaMistDarkScheme else WisteriaMistLightScheme
    ExpressivePalette.SPEARMINT_FIZZ -> if (darkTheme) SpearmintFizzDarkScheme else SpearmintFizzLightScheme
    ExpressivePalette.OBSIDIAN_MONO -> if (darkTheme) ObsidianMonoDarkScheme else ObsidianMonoLightScheme
}
