package com.chencohensolutions.knesset.ui

import kotlinx.serialization.Serializable

// Root tabs
@Serializable data object HomeRoute
@Serializable data object LegislationRoute
@Serializable data object PlenumRoute
@Serializable data object CommitteesRoute
@Serializable data object PeopleRoute

// Overflow destinations
@Serializable data object SettingsRoute
