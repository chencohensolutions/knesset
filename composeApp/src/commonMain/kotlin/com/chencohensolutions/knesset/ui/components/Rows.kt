package com.chencohensolutions.knesset.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chencohensolutions.knesset.core.platform.UrlOpener
import com.chencohensolutions.knesset.domain.Doc
import com.chencohensolutions.knesset.ui.BillRoute
import com.chencohensolutions.knesset.ui.CommitteeRoute
import com.chencohensolutions.knesset.ui.CommitteeSessionRoute
import com.chencohensolutions.knesset.ui.ExplorerItemRoute
import com.chencohensolutions.knesset.ui.FactionRoute
import com.chencohensolutions.knesset.ui.Kind
import com.chencohensolutions.knesset.ui.LawRoute
import com.chencohensolutions.knesset.ui.LocalNav
import com.chencohensolutions.knesset.ui.MemberRoute
import com.chencohensolutions.knesset.ui.MinistryRoute
import com.chencohensolutions.knesset.ui.MotionRoute
import com.chencohensolutions.knesset.ui.QuestionRoute
import com.chencohensolutions.knesset.ui.SecondaryLawRoute
import com.chencohensolutions.knesset.ui.SittingRoute
import com.chencohensolutions.knesset.ui.VoteRoute
import com.chencohensolutions.knesset.ui.theme.AppTheme
import com.chencohensolutions.knesset.ui.theme.Radius
import com.chencohensolutions.knesset.ui.theme.Section
import com.chencohensolutions.knesset.ui.theme.Space
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
fun StatusChip(text: String?) = StatusPill(text)

@Composable
fun InitialsAvatar(initials: String, modifier: Modifier = Modifier, factionId: Int? = null) {
    Box(modifier) { Avatar(initials, factionId) }
}

/** Which kind of item a route opens, for the icon and hue of a row. */
fun kindOfRoute(route: Any?): Kind? = when (route) {
    is BillRoute -> Kind.BILL
    is LawRoute -> Kind.LAW
    is SecondaryLawRoute -> Kind.SECONDARY_LAW
    is VoteRoute -> Kind.VOTE
    is SittingRoute -> Kind.SITTING
    is MotionRoute -> Kind.MOTION
    is QuestionRoute -> Kind.QUESTION
    is CommitteeRoute -> Kind.COMMITTEE
    is CommitteeSessionRoute -> Kind.COMMITTEE_SESSION
    is MemberRoute -> Kind.MEMBER
    is FactionRoute -> Kind.FACTION
    is MinistryRoute -> Kind.MINISTRY
    is ExplorerItemRoute -> null
    else -> null
}

/**
 * The one row pattern (docs 3.7). With [card] it stands alone on its own white card (lists); without it,
 * it sits inside a section card. No chevrons and no dividers: the whole row is the touch target.
 */
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
    onClick: (() -> Unit)? = null,
    kind: Kind? = kindOfRoute(route),
    factionId: Int? = null,
    card: Boolean = false,
    showIcon: Boolean = true,
) {
    val nav = LocalNav.current
    val opener = koinInject<UrlOpener>()
    val c = AppTheme.colors
    val click: (() -> Unit)? = when {
        onClick != null -> onClick
        route != null -> { { nav.navigate(route) } }
        url != null -> { { opener.open(url) } }
        else -> null
    }
    val section = if (kind != null) Section.of(kind) else AppTheme.section
    val body: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (card) Space.l else Space.m, vertical = Space.m),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            when {
                initials != null -> Avatar(initials, factionId)
                showIcon && kind != null -> IconTile(kindIcon(kind), section)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DataText(title, maxLines = 3, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.textPrimary)
                if (status != null || date != null || subtitle != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        StatusPill(status)
                        if (date != null) Text(date, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        if (subtitle != null) DataText(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = c.textSecondary)
                    }
                }
                below?.invoke()
            }
            when {
                trailing != null -> trailing()
                url != null && route == null && onClick == null -> Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp), tint = c.textSecondary)
            }
        }
    }
    if (card) {
        AppCard(Modifier.fillMaxWidth().padding(horizontal = Space.gutter), click) { body() }
    } else {
        Box(Modifier.fillMaxWidth().clip(Radius.tile).pressableRow(click)) { body() }
    }
}

@Composable
private fun Modifier.pressableRow(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this else this.then(Modifier.pressable(onClick))

/** A document tile with a colored format badge (docs 3.17). */
@Composable
fun DocumentRow(doc: Doc) {
    val opener = koinInject<UrlOpener>()
    val c = AppTheme.colors
    val badge = when (doc.format) {
        "PDF" -> c.negative
        "DOC", "DOCX" -> Section.LEGISLATION.accentLight
        else -> c.neutral
    }
    Row(
        Modifier.fillMaxWidth().clip(Radius.tile).pressable { opener.open(doc.url) }.padding(horizontal = Space.m, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Box(Modifier.clip(Radius.tile).background(badge.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 10.dp)) {
            Text(doc.format ?: "FILE", color = badge, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        DataText(doc.name ?: doc.meaning ?: doc.url.substringAfterLast('/'), Modifier.weight(1f), maxLines = 2)
        Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp), tint = c.textSecondary)
    }
}
