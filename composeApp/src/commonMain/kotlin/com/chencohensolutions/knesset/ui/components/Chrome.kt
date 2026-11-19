package com.chencohensolutions.knesset.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.back
import com.chencohensolutions.knesset.resources.clear_all
import com.chencohensolutions.knesset.resources.empty
import com.chencohensolutions.knesset.resources.empty_hint
import com.chencohensolutions.knesset.resources.retry
import com.chencohensolutions.knesset.ui.LocalNav
import com.chencohensolutions.knesset.ui.theme.AppTheme
import com.chencohensolutions.knesset.ui.theme.LocalSection
import com.chencohensolutions.knesset.ui.theme.Radius
import com.chencohensolutions.knesset.ui.theme.Section
import com.chencohensolutions.knesset.ui.theme.Space
import com.chencohensolutions.knesset.ui.theme.sectionGradient
import org.jetbrains.compose.resources.stringResource

/**
 * Gradient header that collapses with scroll (docs 3.1, 3.2). Root tabs pass [onBack] = null; detail
 * screens pass a back action and may show up to [expandedLines] lines of title when expanded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradientHeader(
    title: String,
    section: Section,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
    expandedLines: Int = 1,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val collapsed = scrollBehavior.state.collapsedFraction > 0.5f
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
        Box(Modifier.background(sectionGradient(section)).background(com.chencohensolutions.knesset.ui.theme.Contrast.headerScrim)) {
            LargeTopAppBar(
                title = {
                    Text(
                        title,
                        maxLines = if (collapsed) 1 else expandedLines,
                        overflow = TextOverflow.Ellipsis,
                        style = if (collapsed) MaterialTheme.typography.titleMedium
                        else if (onBack == null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back), tint = Color.White) }
                },
                actions = actions,
                expandedHeight = if (expandedLines > 2) 196.dp else 136.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
                ),
                scrollBehavior = scrollBehavior,
            )
        }
    }
}

/** The content sheet with 28dp top corners that sits under a gradient header. */
@Composable
fun ContentSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxSize(), shape = Radius.sheet, color = AppTheme.colors.background) {
        Column(Modifier.fillMaxSize().padding(top = Space.s), content = content)
    }
}

data class NavItem(val label: String, val icon: ImageVector, val section: Section, val selected: Boolean, val onClick: () -> Unit)

/** A pill bar floating above the bottom edge; the selected item shows its label in an accent pill (docs 3.3). */
@Composable
fun FloatingNavBar(items: List<NavItem>) {
    val c = AppTheme.colors
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.m), contentAlignment = Alignment.Center) {
        Row(
            Modifier.shadow(12.dp, Radius.pill).clip(Radius.pill).background(c.surfaceRaised).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val accent = c.accent(item.section)
                val bg by animateColorAsState(if (item.selected) c.container(item.section) else Color.Transparent, label = "navbg")
                Row(
                    Modifier.clip(Radius.pill).background(bg)
                        .clickable(role = Role.Tab, onClick = item.onClick)
                        .semantics { selected = item.selected }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(item.icon, if (item.selected) null else item.label, tint = if (item.selected) accent else c.textSecondary)
                    AnimatedVisibility(item.selected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Text(item.label, style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Scrollable pill tabs; nothing is truncated (docs 3.4). */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = AppTheme.colors
    val accent = AppTheme.accent
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) accent else Color.Transparent, label = "tab")
            Text(
                label,
                Modifier.clip(Radius.pill).background(bg).clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = if (on) Color.White else c.textSecondary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

/** Filled, pill shaped search field (docs 3.5). */
@Composable
fun SearchPill(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xs).heightIn(min = 48.dp),
        placeholder = { Text(placeholder, color = c.textSecondary) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = c.textSecondary) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Clear, stringResource(Res.string.clear_all)) }
        },
        singleLine = true,
        shape = Radius.pill,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content, color = c.textPrimary),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.surfaceMuted, unfocusedContainerColor = c.surfaceMuted,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = AppTheme.accent,
        ),
    )
}

/** A modal bottom sheet with a title, used by the filter sheet and the Knesset picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = Radius.sheet, containerColor = AppTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter).padding(bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

/** A selectable pill used in sheets and filter bars. */
@Composable
fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val accent = AppTheme.accent
    Text(
        label,
        modifier.clip(Radius.pill)
            .background(if (selected) accent else c.surfaceMuted)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) Color.White else c.textPrimary,
        style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content),
        maxLines = 2,
    )
}

/** The Knesset picker: number tiles, 4 per row, newest first, plus "all" (docs 3.16). */
@Composable
fun KnessetGrid(terms: List<Int>, current: Int?, selected: Int?, allLabel: String, onPick: (Int?) -> Unit) {
    val c = AppTheme.colors
    val accent = AppTheme.accent
    ChoicePill(allLabel, selected == null, { onPick(null) })
    LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 420.dp), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        items(terms) { n ->
            val on = n == selected
            Box(
                Modifier.height(56.dp).clip(Radius.tile).background(if (on) accent else c.surfaceMuted).clickable(role = Role.RadioButton) { onPick(n) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n.toString(), style = MaterialTheme.typography.titleMedium, color = if (on) Color.White else c.textPrimary)
                    if (n == current) Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) Color.White else accent))
                }
            }
        }
    }
}

/** Two-tone illustration from simple shapes in the section colors. */
@Composable
private fun Illustration(icon: ImageVector, section: Section, error: Boolean) {
    val c = AppTheme.colors
    val accent = if (error) c.negative else c.accent(section)
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(c.container(section)))
        Box(Modifier.size(72.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)))
        Icon(icon, null, Modifier.size(40.dp), tint = accent)
    }
}

@Composable
fun IllustratedState(
    icon: ImageVector,
    title: String,
    line: String?,
    action: String?,
    onAction: (() -> Unit)?,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    val section = LocalSection.current
    Column(modifier.padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Illustration(icon, section, error)
        Gap(Space.l)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (line != null) Text(line, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.xs))
        if (action != null && onAction != null) {
            Gap(Space.l)
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun EmptyIllustration(modifier: Modifier = Modifier, title: String? = null, line: String? = null, action: String? = null, onAction: (() -> Unit)? = null) =
    IllustratedState(Icons.Outlined.SearchOff, title ?: stringResource(Res.string.empty), line ?: stringResource(Res.string.empty_hint), action, onAction, false, modifier)

@Composable
fun ErrorIllustration(error: KnessetError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) =
    IllustratedState(
        if (error is KnessetError.Offline) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
        errorText(error), null, if (onRetry != null) stringResource(Res.string.retry) else null, onRetry, true, modifier,
    )

/** The back action of the current nav controller. */
@Composable
fun rememberBack(): () -> Unit {
    val nav = LocalNav.current
    return { nav.popBackStack() }
}

