package org.fossify.gallery.helpers.backup

/** Only explicit portable preference values; absence means platform/app default in REPLACE mode. */
data class OxygenSettingsSnapshot(val version: Int = 1, val values: Map<String, Any> = emptyMap())
