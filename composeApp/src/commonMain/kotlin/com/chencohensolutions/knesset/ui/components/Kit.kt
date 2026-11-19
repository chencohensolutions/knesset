package com.chencohensolutions.knesset.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.chencohensolutions.knesset.domain.BillStages
import com.chencohensolutions.knesset.domain.Hemicycle
import com.chencohensolutions.knesset.domain.StatusStyles
import com.chencohensolutions.knesset.domain.StatusTone
import com.chencohensolutions.knesset.domain.VoteTally
import com.chencohensolutions.knesset.ui.Kind
import com.chencohensolutions.knesset.ui.theme.AppTheme
import com.chencohensolutions.knesset.ui.theme.AppType
import com.chencohensolutions.knesset.ui.theme.FactionColors
import com.chencohensolutions.knesset.ui.theme.Motion
import com.chencohensolutions.knesset.ui.theme.Radius
import com.chencohensolutions.knesset.ui.theme.Section
import com.chencohensolutions.knesset.ui.theme.Space
import com.chencohensolutions.knesset.ui.theme.sectionGradient

fun kindIcon(kind: Kind?): ImageVector = when (kind) {
    Kind.BILL -> Icons.Outlined.Description
    Kind.LAW -> Icons.Outlined.MenuBook
    Kind.SECONDARY_LAW -> Icons.Outlined.Policy
    Kind.VOTE -> Icons.Outlined.HowToVote
    Kind.SITTING -> Icons.Outlined.Gavel
    Kind.MOTION -> Icons.Outlined.Campaign
    Kind.QUESTION -> Icons.Outlined.HelpOutline
    Kind.COMMITTEE -> Icons.Outlined.AccountBalance
    Kind.COMMITTEE_SESSION -> Icons.Outlined.CalendarMonth
    Kind.MEMBER -> Icons.Outlined.Person
    Kind.FACTION -> Icons.Outlined.Flag
    Kind.MINISTRY -> Icons.Outlined.Business
    Kind.LOBBYIST -> Icons.Outlined.Badge
    null -> Icons.Outlined.Today
}

/** A 40dp rounded square with the kind icon in the section accent on its soft container. */
@Composable
fun IconTile(icon: ImageVector, section: Section = AppTheme.section, size: Dp = 40.dp) {
    val c = AppTheme.colors
    Box(Modifier.size(size).clip(Radius.tile).background(c.container(section)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = c.accent(section))
    }
}

/** Initials on the faction color. */
@Composable
fun Avatar(initials: String, factionId: Int? = null, size: Dp = 40.dp, ring: Boolean = false) {
    val color = if (factionId != null) FactionColors.of(factionId) else AppTheme.accent
    Box(
        Modifier.size(size).let { if (ring) it.border(3.dp, Color.White, CircleShape) else it }.padding(if (ring) 3.dp else 0.dp)
            .clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials, color = Color.White, fontWeight = FontWeight.SemiBold,
            style = if (size >= 64.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
        )
    }
}

fun toneColor(tone: StatusTone, positive: Color, negative: Color, accent: Color, neutral: Color) = when (tone) {
    StatusTone.PASSED -> positive
    StatusTone.STOPPED -> negative
    StatusTone.IN_PROGRESS -> accent
    StatusTone.NEUTRAL -> neutral
}

/** Pill with a colored dot and the status text. */
@Composable
fun StatusPill(text: String?) {
    if (text.isNullOrBlank()) return
    val c = AppTheme.colors
    val color = toneColor(StatusStyles.tone(text), c.positive, c.negative, AppTheme.accent, c.neutral)
    Row(
        Modifier.clip(Radius.pill).background(color.copy(alpha = if (c.dark) 0.2f else 0.12f)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            text, color = if (c.dark) color else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Card scale on press (docs 5.10) with a ripple in the section accent. */
@Composable
fun Modifier.pressable(onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, Motion.standardSpring(), label = "press")
    return this.scale(scale).clip(Radius.card).clickable(interactionSource = source, indication = androidx.compose.material3.ripple(color = AppTheme.accent), role = Role.Button, onClick = onClick)
}

/** The white card every list row and section sits on. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Surface(
        modifier = modifier.pressable(onClick),
        shape = Radius.card,
        color = c.surface,
        border = if (c.dark) null else androidx.compose.foundation.BorderStroke(1.dp, c.outline),
    ) { content() }
}

/** Thin or thick split bar: for, against, abstained, in that order from the start side; grows in when shown. */
@Composable
fun VoteBar(forCount: Int, againstCount: Int, abstain: Int = 0, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val total = forCount + againstCount + abstain
    if (total == 0) return
    val c = AppTheme.colors
    val grow = remember { Animatable(0f) }
    LaunchedEffect(total) { grow.animateTo(1f, tween(Motion.LONG, easing = Motion.emphasized)) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.fillMaxWidth().height(height).clip(Radius.pill).background(c.surfaceMuted)) {
        val parts = listOf(forCount to c.positive, againstCount to c.negative, abstain to c.caution)
        var start = 0f
        val full = size.width * grow.value
        parts.forEach { (n, color) ->
            if (n == 0) return@forEach
            val w = full * n / total
            val x = if (rtl) size.width - start - w else start
            drawRect(color, Offset(x, 0f), Size(w, size.height))
            start += w
        }
    }
}

/** Big tabular numbers that count up, with labels, over a 12dp bar (docs 3.11). */
@Composable
fun VoteSummary(tally: VoteTally) {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        VoteBar(tally.forCount, tally.againstCount, tally.abstainCount, height = 12.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            tally.byResult.forEach { (label, n) ->
                val color = when (label) {
                    VoteTally.FOR -> c.positive
                    VoteTally.AGAINST -> c.negative
                    VoteTally.ABSTAIN -> c.caution
                    else -> c.neutral
                }
                val shown = remember { Animatable(0f) }
                LaunchedEffect(n) { shown.animateTo(n.toFloat(), tween(Motion.LONG, easing = Motion.emphasized)) }
                Column(Modifier.semantics { contentDescription = "$label $n" }) {
                    Text(shown.value.toInt().toString(), style = AppType.number, color = color)
                    DataText(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
        }
    }
}

/**
 * The half-circle chart: one dot per seat in [colors] (already ordered by group). The seat order follows the
 * reading direction, so in Hebrew the first group sits on the right.
 */
@Composable
fun HemicycleChart(colors: List<Color>, modifier: Modifier = Modifier) {
    if (colors.isEmpty()) return
    val seats = remember(colors.size) { Hemicycle.layout(colors.size) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fill = remember(colors) { Animatable(0f) }
    LaunchedEffect(colors) { fill.animateTo(colors.size.toFloat(), tween(colors.size * 2 + Motion.MEDIUM)) }
    Canvas(modifier.fillMaxWidth().aspectRatio(2f)) {
        val r = size.width / 2f
        val dot = r * 0.045f
        seats.forEachIndexed { i, s ->
            if (i > fill.value) return@forEachIndexed
            val x = if (rtl) -s.x else s.x
            drawCircle(colors[i], dot, Offset(r + x * (r - dot), size.height - s.y * (size.height - dot) - dot / 2))
        }
    }
}

/** Preliminary, first reading, committee, second and third reading, published. */
@Composable
fun StageTracker(labels: List<String>, reached: Int, stopped: Boolean) {
    val c = AppTheme.colors
    val accent = if (stopped) c.negative else AppTheme.accent
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(1f, 0.45f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(Radius.pill)
                        .background(if (i <= reached) accent.copy(alpha = if (i == reached && !stopped) alpha else 1f) else c.surfaceMuted),
                )
                Text(
                    label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                    color = if (i <= reached) c.textPrimary else c.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** A shimmering placeholder block. */
@Composable
fun Shimmer(modifier: Modifier) {
    val c = AppTheme.colors
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1200)), label = "x")
    BoxWithConstraints(modifier.clip(RoundedCornerShape(8.dp)).background(c.surfaceMuted)) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(Color.Transparent, c.surface.copy(alpha = 0.55f), Color.Transparent),
                    start = Offset(w * x - w / 2, 0f), end = Offset(w * x, 0f),
                ),
            ),
        )
    }
}

/** Skeleton shaped like an item card, with the real row height. */
@Composable
fun SkeletonCard() {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Space.l), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Shimmer(Modifier.size(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Shimmer(Modifier.fillMaxWidth(0.9f).height(16.dp))
                Shimmer(Modifier.fillMaxWidth(0.6f).height(16.dp))
                Shimmer(Modifier.fillMaxWidth(0.4f).height(12.dp))
            }
        }
    }
}

@Composable
fun SkeletonList(count: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = Space.gutter, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        repeat(count) { SkeletonCard() }
    }
}

/** 16:9 tile with the section gradient and a play glyph, for video lists. */
@Composable
fun VideoThumb(modifier: Modifier = Modifier, section: Section = Section.PLENUM) {
    Box(modifier.aspectRatio(16f / 9f).clip(Radius.tile).background(sectionGradient(section)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.PlayArrow, null, tint = Color.White)
        }
    }
}

@Composable
fun Gap(size: Dp = Space.m) = Spacer(Modifier.size(size))

