package com.chencohensolutions.knesset.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.empty
import com.chencohensolutions.knesset.resources.error_blocked
import com.chencohensolutions.knesset.resources.error_not_found
import com.chencohensolutions.knesset.resources.error_offline
import com.chencohensolutions.knesset.resources.error_parse
import com.chencohensolutions.knesset.resources.error_rate
import com.chencohensolutions.knesset.resources.error_server
import com.chencohensolutions.knesset.resources.offline_banner
import com.chencohensolutions.knesset.resources.retry
import org.jetbrains.compose.resources.stringResource

@Composable
fun errorText(error: KnessetError): String = when (error) {
    is KnessetError.Offline -> stringResource(Res.string.error_offline)
    is KnessetError.BlockedOrUnavailable -> stringResource(Res.string.error_blocked)
    is KnessetError.RateLimited -> stringResource(Res.string.error_rate)
    is KnessetError.NotFound -> stringResource(Res.string.error_not_found)
    is KnessetError.Server -> stringResource(Res.string.error_server, error.code)
    is KnessetError.Parse -> stringResource(Res.string.error_parse)
}

@Composable
fun ErrorState(error: KnessetError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier.fillMaxSize()) =
    ErrorIllustration(error, onRetry, modifier.semantics { liveRegion = LiveRegionMode.Polite })

@Composable
fun EmptyState(modifier: Modifier = Modifier.fillMaxSize()) = EmptyIllustration(modifier)

/** First loads show skeleton cards with real row heights; spinners are only for paging. */
@Composable
fun LoadingState(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier) { SkeletonList() }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val c = com.chencohensolutions.knesset.ui.theme.AppTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .background(c.caution.copy(alpha = 0.16f), com.chencohensolutions.knesset.ui.theme.Radius.pill)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp), tint = c.caution)
        Text(stringResource(Res.string.offline_banner), style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
    }
}
