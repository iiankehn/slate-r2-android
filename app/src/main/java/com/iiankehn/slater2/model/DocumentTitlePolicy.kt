package com.iiankehn.slater2.model

object DocumentTitlePolicy {
    fun displayTitle(title: String, body: String): String =
        title.trim().ifEmpty {
            body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(48).orEmpty()
        }.ifEmpty { "Untitled" }
}
