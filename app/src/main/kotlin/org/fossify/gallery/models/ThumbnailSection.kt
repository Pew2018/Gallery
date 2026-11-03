package org.fossify.gallery.models

data class ThumbnailSection(val title: String, val key: String = title) : ThumbnailItem()
