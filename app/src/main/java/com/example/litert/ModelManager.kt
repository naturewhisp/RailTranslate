package com.example.litert

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

enum class ModelType {
    BASE_MODEL,
    LORA_ADAPTER
}

data class LocalModelInfo(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val type: ModelType,
    val lastModified: Long,
    val isCompatible: Boolean = true
) {
    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                DecimalFormat("#.## GB").format(mb / 1024.0)
            } else {
                DecimalFormat("#.# MB").format(mb)
            }
        }
}

data class DownloadableModel(
    val id: String,
    val title: String,
    val description: String,
    val huggingFaceRepo: String,
    val downloadUrl: String,
    val fileName: String,
    val type: ModelType,
    val estimatedSize: String,
    val isRecommended: Boolean = false
)

data class DownloadProgressState(
    val modelId: String,
    val fileName: String,
    val progress: Float, // 0.0 to 1.0
    val downloadedBytes: Long,
    val totalBytes: Long,
    val isCompleted: Boolean = false,
    val error: String? = null
)

class ModelManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // Modelli ufficiali e riconosciuti preconfigurati da HuggingFace
    val recognizedCatalog = listOf(
        DownloadableModel(
            id = "gemma-2b-litert",
            title = "Gemma 2B-IT (LiteRT 4-bit)",
            description = "Modello base open-weight Google Gemma ottimizzato per LiteRT / NPU",
            huggingFaceRepo = "google/gemma-2b-it",
            downloadUrl = "https://huggingface.co/google/gemma-2b-it/resolve/main/gemma-2b-it-gpu-int4.bin",
            fileName = "gemma-2b-it-gpu-int4.bin",
            type = ModelType.BASE_MODEL,
            estimatedSize = "~1.35 GB",
            isRecommended = true
        ),
        DownloadableModel(
            id = "gemma-2-2b-litert",
            title = "Gemma 2 2B-IT (LiteRT 8-bit)",
            description = "Nuova generazione Gemma 2 compatibile con NPU Tensor di Pixel",
            huggingFaceRepo = "google/gemma-2-2b-it",
            downloadUrl = "https://huggingface.co/google/gemma-2-2b-it/resolve/main/gemma-2-2b-it-cpu-int8.bin",
            fileName = "gemma-2-2b-it-cpu-int8.bin",
            type = ModelType.BASE_MODEL,
            estimatedSize = "~1.60 GB",
            isRecommended = false
        ),
        DownloadableModel(
            id = "railway-tsi-lora",
            title = "Railway Engineering LoRA Adapter",
            description = "Adapter LoRA ferroviario per norme TSI/EN, materiale rotabile e segnalamento",
            huggingFaceRepo = "community-railway/gemma-railway-tsi-lora",
            downloadUrl = "https://huggingface.co/google-research/gemma-railway-tsi-lora/resolve/main/railway_lora_weights.bin",
            fileName = "railway_tsi_lora.bin",
            type = ModelType.LORA_ADAPTER,
            estimatedSize = "~32 MB",
            isRecommended = true
        )
    )

    fun getModelsDirectory(): File {
        val dir = File(context.getExternalFilesDir(null), "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Esegue il discovery di modelli compatibili (.bin, .task, .tflite)
     * già presenti nelle cartelle dell'app e nella cartella Download di sistema.
     */
    suspend fun discoverLocalModels(): List<LocalModelInfo> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<LocalModelInfo>()
        val searchDirs = listOfNotNull(
            getModelsDirectory(),
            context.filesDir,
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        val supportedExtensions = listOf("bin", "task", "tflite")

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { file ->
                    file.isFile && supportedExtensions.any { ext -> file.name.endsWith(".$ext", ignoreCase = true) }
                } ?: emptyArray()

                for (file in files) {
                    val isLora = file.name.contains("lora", ignoreCase = true) ||
                            file.name.contains("adapter", ignoreCase = true) ||
                            file.length() < 100 * 1024 * 1024 // Se < 100MB è tipicamente un LoRA adapter

                    discovered.add(
                        LocalModelInfo(
                            id = file.absolutePath,
                            name = file.name,
                            path = file.absolutePath,
                            sizeBytes = file.length(),
                            type = if (isLora) ModelType.LORA_ADAPTER else ModelType.BASE_MODEL,
                            lastModified = file.lastModified()
                        )
                    )
                }
            }
        }
        discovered.distinctBy { it.path }.sortedByDescending { it.lastModified }
    }

    /**
     * Importa un file selezionato dall'utente tramite SAF File Picker nella cartella dei modelli.
     */
    suspend fun importModelFromUri(uri: Uri, displayName: String): Result<LocalModelInfo> = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(getModelsDirectory(), displayName)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Impossibile aprire il file selezionato"))

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val isLora = displayName.contains("lora", ignoreCase = true) ||
                    displayName.contains("adapter", ignoreCase = true) ||
                    targetFile.length() < 100 * 1024 * 1024

            val info = LocalModelInfo(
                id = targetFile.absolutePath,
                name = targetFile.name,
                path = targetFile.absolutePath,
                sizeBytes = targetFile.length(),
                type = if (isLora) ModelType.LORA_ADAPTER else ModelType.BASE_MODEL,
                lastModified = targetFile.lastModified()
            )
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Scarica direttamente un modello o LoRA adapter da HuggingFace o URL compatibile.
     */
    suspend fun downloadModel(
        url: String,
        targetFileName: String,
        onProgress: (DownloadProgressState) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(getModelsDirectory(), targetFileName)
            val tempFile = File(getModelsDirectory(), "$targetFileName.download")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "RailTranslate-Android-LiteRT/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Risposta HTTP vuota"))
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var lastUpdate = System.currentTimeMillis()

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 250 || downloadedBytes == totalBytes) {
                            lastUpdate = now
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
                            onProgress(
                                DownloadProgressState(
                                    modelId = targetFileName,
                                    fileName = targetFileName,
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    isCompleted = false
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
            }

            onProgress(
                DownloadProgressState(
                    modelId = targetFileName,
                    fileName = targetFileName,
                    progress = 1.0f,
                    downloadedBytes = targetFile.length(),
                    totalBytes = targetFile.length(),
                    isCompleted = true
                )
            )

            Result.success(targetFile)
        } catch (e: Exception) {
            onProgress(
                DownloadProgressState(
                    modelId = targetFileName,
                    fileName = targetFileName,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    isCompleted = false,
                    error = e.localizedMessage ?: "Errore durante il download"
                )
            )
            Result.failure(e)
        }
    }

    fun deleteLocalModel(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) file.delete() else false
    }
}
