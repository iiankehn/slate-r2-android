package com.iiankehn.slater2.model

data class Document(
    val id: String,
    val title: String,
    val body: RichTextDocument,
    val updatedLabel: String,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val folder: String = "",
    val tags: Set<String> = emptySet(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)
