package com.example.translation

data class SupportedLanguage(
    val displayName: String,
    val code: String,
    val speechLocale: String
)

val EuropeanLanguages = listOf(
    SupportedLanguage("English", "en", "en-US"),
    SupportedLanguage("Italian", "it", "it-IT"),
    SupportedLanguage("Bulgarian", "bg", "bg-BG"),
    SupportedLanguage("Romanian", "ro", "ro-RO"),
    SupportedLanguage("French", "fr", "fr-FR"),
    SupportedLanguage("German", "de", "de-DE"),
    SupportedLanguage("Spanish", "es", "es-ES")
)
