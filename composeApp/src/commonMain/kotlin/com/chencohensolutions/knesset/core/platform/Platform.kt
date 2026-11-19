package com.chencohensolutions.knesset.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal

/** Opens a URL with the platform handler (browser or a viewer app). */
interface UrlOpener {
    fun open(url: String): Boolean
}

/** Shares plain text through the platform share sheet. */
interface Sharer {
    fun share(text: String)
}

/** App version and similar facts that only the platform knows. */
interface AppInfo {
    val versionName: String
}

/**
 * Overrides the locale used by Compose resources, so the UI language can differ from the device language.
 * Pass null to follow the device.
 */
expect object LocalAppLocale {
    val current: String @Composable get
    @Composable
    infix fun provides(value: String?): androidx.compose.runtime.ProvidedValue<*>
}
