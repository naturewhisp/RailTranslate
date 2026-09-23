package com.example.translation
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.GenerationConfig
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.generationConfig

fun test() {
    val genConfig = generationConfig {
        temperature = 0.15f
    }
    val downloadConfig = DownloadConfig()
    val model = GenerativeModel(genConfig, downloadConfig)
}
