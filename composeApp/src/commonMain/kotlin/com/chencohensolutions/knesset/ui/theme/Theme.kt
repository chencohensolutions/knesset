package com.chencohensolutions.knesset.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chencohensolutions.knesset.core.ThemeMode
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.rubik
import com.chencohensolutions.knesset.ui.Kind
import org.jetbrains.compose.resources.Font

/** The five sections of the app, each with its own hue (docs/UX-REDESIGN.md 2.1). */
enum class Section(
    val gradientStart: Color,
    val gradientEnd: Color,
    val accentLight: Color,
    val accentDark: Color,
    val containerLight: Color,
) {
    HOME(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF4F46E5), Color(0xFFA5B4FC), Color(0xFFEEF2FF)),
    LEGISLATION(Color(0xFF2563EB), Color(0xFF06B6D4), Color(0xFF2563EB), Color(0xFF7DD3FC), Color(0xFFE0F2FE)),
    PLENUM(Color(0xFFDB2777), Color(0xFFF97316), Color(0xFFDB2777), Color(0xFFF9A8D4), Color(0xFFFCE7F3)),
    COMMITTEES(Color(0xFF0D9488), Color(0xFF22C55E), Color(0xFF0D9488), Color(0xFF5EEAD4), Color(0xFFCCFBF1)),
    PEOPLE(Color(0xFFEA580C), Color(0xFFF59E0B), Color(0xFFC2410C), Color(0xFFFDBA74), Color(0xFFFFEDD5));

    companion object {
        fun of(kind: Kind?): Section = when (kind) {
            Kind.BILL, Kind.LAW, Kind.SECONDARY_LAW -> LEGISLATION
            Kind.VOTE, Kind.SITTING, Kind.MOTION, Kind.QUESTION -> PLENUM
            Kind.COMMITTEE, Kind.COMMITTEE_SESSION -> COMMITTEES
            Kind.MEMBER, Kind.FACTION, Kind.MINISTRY, Kind.LOBBYIST -> PEOPLE
            null -> HOME
        }
    }
}

/** App colors beyond the Material scheme. Screens read colors only from here or the scheme. */
@Immutable
data class KnessetColors(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceMuted: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val positive: Color,
    val negative: Color,
    val caution: Color,
    val neutral: Color,
) {
    fun accent(section: Section) = if (dark) section.accentDark else section.accentLight

    /** The soft container: a fixed tint in light theme, the accent at 16% over the surface in dark theme. */
    fun container(section: Section) = if (dark) section.accentDark.copy(alpha = 0.16f).compositeOver(surface) else section.containerLight

    companion object {
        val Light = KnessetColors(
            dark = false,
            background = Color(0xFFF6F7FB), surface = Color.White, surfaceRaised = Color.White, surfaceMuted = Color(0xFFEEF0F7),
            outline = Color(0xFFE3E6F0), textPrimary = Color(0xFF14162B), textSecondary = Color(0xFF5B6078),
            positive = Color(0xFF16A34A), negative = Color(0xFFE11D48), caution = Color(0xFFD97706), neutral = Color(0xFF64748B),
        )
        val Dark = KnessetColors(
            dark = true,
            background = Color(0xFF0E0F1A), surface = Color(0xFF171929), surfaceRaised = Color(0xFF1F2236), surfaceMuted = Color(0xFF262A42),
            outline = Color(0xFF2E3350), textPrimary = Color(0xFFECEDF7), textSecondary = Color(0xFFA3A8C3),
            positive = Color(0xFF4ADE80), negative = Color(0xFFFB7185), caution = Color(0xFFFBBF24), neutral = Color(0xFF94A3B8),
        )
    }
}

val LocalKnessetColors = staticCompositionLocalOf { KnessetColors.Light }

/** The section whose hue the current screen uses. */
val LocalSection = staticCompositionLocalOf { Section.HOME }

object AppTheme {
    val colors: KnessetColors @Composable @ReadOnlyComposable get() = LocalKnessetColors.current
    val section: Section @Composable @ReadOnlyComposable get() = LocalSection.current
    val accent: Color @Composable @ReadOnlyComposable get() = LocalKnessetColors.current.accent(LocalSection.current)
    val container: Color @Composable @ReadOnlyComposable get() = LocalKnessetColors.current.container(LocalSection.current)
}

/** A section gradient drawn from the start edge to the end edge, so it flips in RTL. */
@Composable
@ReadOnlyComposable
fun sectionGradient(section: Section): Brush {
    val colors = listOf(section.gradientStart, section.gradientEnd)
    return Brush.horizontalGradient(if (LocalLayoutDirection.current == LayoutDirection.Rtl) colors.reversed() else colors)
}

/** Faction colors, stable across screens (factionId mod 12). */
object FactionColors {
    val palette = listOf(
        0xFF4F46E5, 0xFFDB2777, 0xFF0D9488, 0xFFEA580C, 0xFF2563EB, 0xFF7C3AED,
        0xFF16A34A, 0xFFE11D48, 0xFF0891B2, 0xFFCA8A04, 0xFF9333EA, 0xFF475569,
    ).map { Color(it) }

    fun of(factionId: Int?): Color = if (factionId == null) palette.last() else palette[((factionId % 12) + 12) % 12]
}

/** Spacing scale. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val gutter = 16.dp
}

object Radius {
    val card = RoundedCornerShape(20.dp)
    val tile = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(percent = 50)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

/** Durations and easings in one place (docs/UX-REDESIGN.md 5). */
object Motion {
    const val SHORT = 150
    const val MEDIUM = 300
    const val LONG = 500
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun <T> standardSpring() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
    @Suppress("unused") val springDefault = Spring.StiffnessMedium
}

@Composable
private fun rubik(): FontFamily {
    fun weight(w: Int) = FontVariation.Settings(FontVariation.weight(w))
    return FontFamily(
        Font(Res.font.rubik, FontWeight.Normal, variationSettings = weight(400)),
        Font(Res.font.rubik, FontWeight.Medium, variationSettings = weight(500)),
        Font(Res.font.rubik, FontWeight.SemiBold, variationSettings = weight(600)),
        Font(Res.font.rubik, FontWeight.Bold, variationSettings = weight(700)),
    )
}

/** Extra text styles the Material type scale has no slot for. */
object AppType {
    val number = TextStyle(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
}

@Composable
private fun typography(family: FontFamily): Typography {
    fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
        fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val display = style(30, 36, FontWeight.Bold)
    val title = style(22, 28, FontWeight.Bold)
    val headline = style(18, 24, FontWeight.SemiBold)
    val body = style(16, 24, FontWeight.Normal)
    val label = style(14, 20, FontWeight.Medium)
    val caption = style(12, 16, FontWeight.Medium)
    return Typography(
        displayLarge = display, displayMedium = display, displaySmall = display,
        headlineLarge = display, headlineMedium = title, headlineSmall = title,
        titleLarge = title, titleMedium = headline, titleSmall = label.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = body, bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 20.sp), bodySmall = caption.copy(fontWeight = FontWeight.Normal),
        labelLarge = label, labelMedium = caption, labelSmall = caption,
    )
}

private fun scheme(c: KnessetColors) = if (c.dark) {
    darkColorScheme(
        primary = Section.HOME.accentDark, onPrimary = Color(0xFF14162B),
        primaryContainer = c.container(Section.HOME), onPrimaryContainer = c.textPrimary,
        secondary = Section.LEGISLATION.accentDark, onSecondary = Color(0xFF14162B),
        secondaryContainer = c.container(Section.LEGISLATION), onSecondaryContainer = c.textPrimary,
        tertiary = Section.PLENUM.accentDark, tertiaryContainer = c.container(Section.PLENUM), onTertiaryContainer = c.textPrimary,
        background = c.background, onBackground = c.textPrimary,
        surface = c.surface, onSurface = c.textPrimary, surfaceVariant = c.surfaceMuted, onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.surfaceRaised, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surfaceRaised,
        surfaceContainerHighest = c.surfaceMuted, surfaceContainerLowest = c.background,
        outline = c.outline, outlineVariant = c.outline, error = c.negative,
    )
} else {
    lightColorScheme(
        primary = Section.HOME.accentLight, onPrimary = Color.White,
        primaryContainer = Section.HOME.containerLight, onPrimaryContainer = Color(0xFF1E1B4B),
        secondary = Section.LEGISLATION.accentLight, onSecondary = Color.White,
        secondaryContainer = Section.LEGISLATION.containerLight, onSecondaryContainer = Color(0xFF0C4A6E),
        tertiary = Section.PLENUM.accentLight, tertiaryContainer = Section.PLENUM.containerLight, onTertiaryContainer = Color(0xFF831843),
        background = c.background, onBackground = c.textPrimary,
        surface = c.surface, onSurface = c.textPrimary, surfaceVariant = c.surfaceMuted, onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.surfaceMuted, surfaceContainerLowest = c.background,
        outline = c.outline, outlineVariant = c.outline, error = c.negative,
    )
}

/** Kept for code that predates the token set. */
object Semantic {
    val positive = KnessetColors.Light.positive
    val negative = KnessetColors.Light.negative
    val neutral = KnessetColors.Light.neutral
    val progress = Section.LEGISLATION.accentLight
}

@Composable
fun KnessetTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) KnessetColors.Dark else KnessetColors.Light
    CompositionLocalProvider(LocalKnessetColors provides colors) {
        MaterialTheme(
            colorScheme = scheme(colors),
            typography = typography(rubik()),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = Radius.tile,
                large = Radius.card, extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
