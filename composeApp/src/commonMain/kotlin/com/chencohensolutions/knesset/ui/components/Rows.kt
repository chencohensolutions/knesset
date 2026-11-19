package com.chencohensolutions.knesset.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chencohensolutions.knesset.core.platform.UrlOpener
import com.chencohensolutions.knesset.domain.Doc
import com.chencohensolutions.knesset.domain.StatusStyles
import com.chencohensolutions.knesset.domain.StatusTone
import com.chencohensolutions.knesset.ui.LocalNav
import com.chencohensolutions.knesset.ui.theme.Semantic
import org.koin.compose.koinInject

/** Hebrew data text: direction follows the content, aligned to start (plan 2.9). */
@Composable
fun DataText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = Color.Unspecified,
) {
    Text(
        text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = style.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Start),
    )
}

@Composable
fun StatusChip(text: String?) {
    if (text.isNullOrBlank()) return
    val color = when (StatusStyles.tone(text)) {
        StatusTone.PASSED -> Semantic.positive
        StatusTone.STOPPED -> Semantic.negative
        StatusTone.IN_PROGRESS -> Semantic.progress
        StatusTone.NEUTRAL -> Semantic.neutral
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            text, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun InitialsAvatar(initials: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleSmall)
    }
}

/** A thin for/against bar. */
@Composable
fun VoteBar(forCount: Int, againstCount: Int, abstain: Int = 0, modifier: Modifier = Modifier) {
    val total = forCount + againstCount + abstain
    if (total == 0) return
    Row(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
        if (forCount > 0) Box(Modifier.weight(forCount.toFloat()).height(6.dp).background(Semantic.positive))
        if (againstCount > 0) Box(Modifier.weight(againstCount.toFloat()).height(6.dp).background(Semantic.negative))
        if (abstain > 0) Box(Modifier.weight(abstain.toFloat()).height(6.dp).background(Semantic.neutral))
    }
}

/** The one row pattern used by lists and detail sections. */
@Composable
fun ItemRow(
    title: String,
    subtitle: String? = null,
    status: String? = null,
    date: String? = null,
    initials: String? = null,
    route: Any? = null,
    url: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val nav = LocalNav.current
    val opener = koinInject<UrlOpener>()
    val click: (() -> Unit)? = when {
        route != null -> { { nav.navigate(route) } }
        url != null -> { { opener.open(url) } }
        else -> null
    }
    Row(
        Modifier.fillMaxWidth()
            .let { if (click != null) it.clickable(role = Role.Button, onClick = click) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (initials != null) InitialsAvatar(initials)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DataText(title, maxLines = 3, style = MaterialTheme.typography.bodyLarge)
            if (status != null || date != null || subtitle != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status)
                    if (date != null) Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (subtitle != null) DataText(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            below?.invoke()
        }
        when {
            trailing != null -> trailing()
            url != null && route == null -> Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            click != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun DocumentRow(doc: Doc) {
    val opener = koinInject<UrlOpener>()
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { opener.open(doc.url) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
        DataText(doc.name ?: doc.meaning ?: doc.url.substringAfterLast('/'), Modifier.weight(1f), maxLines = 2)
        if (doc.format != null) {
            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(doc.format, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        Box(Modifier.width(0.dp))
    }
}
