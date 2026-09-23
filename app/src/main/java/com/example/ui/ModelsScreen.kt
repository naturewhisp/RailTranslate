package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.litert.DownloadableModel
import com.example.litert.LocalModelInfo
import com.example.litert.ModelType
import com.example.lora.LoraDatasetFormat
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.TranslationEngineType

@Composable
fun ModelsScreen(viewModel: MainViewModel) {
    val selectedEngine by viewModel.selectedEngine.collectAsStateWithLifecycle()
    val discoveredModels by viewModel.discoveredModels.collectAsStateWithLifecycle()
    val activeBaseModel by viewModel.activeBaseModel.collectAsStateWithLifecycle()
    val activeLoraAdapter by viewModel.activeLoraAdapter.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val loraStats by viewModel.loraDatasetStats.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatusMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var customUrl by remember { mutableStateOf("") }
    var customFileName by remember { mutableStateOf("") }
    var showCustomDialog by remember { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf(LoraDatasetFormat.MESSAGES_JSONL) }
    var exportAllLanguages by remember { mutableStateOf(true) }
    var includeAuthoritativeCorpus by remember { mutableStateOf(true) }
    var lastExportedFile by remember { mutableStateOf<java.io.File?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_model.bin"
            viewModel.importModel(uri, fileName)
            Toast.makeText(context, "Importazione modello avviata: $fileName", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Selettore Motore (Hybrid Architecture)
        item {
            Text(
                text = "Architettura Motore AI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Opzione AICore
                OutlinedCard(
                    onClick = { viewModel.setEngine(TranslationEngineType.AICORE_NPU) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("engine_aicore_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (selectedEngine == TranslationEngineType.AICORE_NPU)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(selectedEngine == TranslationEngineType.AICORE_NPU)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AICore NPU",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pixel 10 Pro nativo\nZero download",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Opzione LiteRT
                OutlinedCard(
                    onClick = { viewModel.setEngine(TranslationEngineType.LITERT_LORA) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("engine_litert_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (selectedEngine == TranslationEngineType.LITERT_LORA)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(selectedEngine == TranslationEngineType.LITERT_LORA)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LiteRT + LoRA",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pesi aperti Gemma\nSupporto LoRA TSI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Stato Configurazione Attiva LiteRT
        if (selectedEngine == TranslationEngineType.LITERT_LORA) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Configurazione LiteRT Attiva",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Modello Base: ${activeBaseModel?.name ?: "Nessuno (Seleziona sotto)"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Adapter LoRA: ${activeLoraAdapter?.name ?: "Nessuno (Opzionale)"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // 3. Download in corso (se attivo)
        if (downloadProgress != null) {
            val dp = downloadProgress!!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Download HuggingFace: ${dp.fileName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (dp.isCompleted) {
                                Text("Completato", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("${(dp.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (dp.isCompleted) {
                            Button(
                                onClick = { viewModel.clearDownloadProgress() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Chiudi")
                            }
                        } else {
                            LinearProgressIndicator(
                                progress = { dp.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (dp.error != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Errore: ${dp.error}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Modelli Rilevati a Sistema (Auto-Discovery)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Modelli Rilevati a Sistema (${discoveredModels.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { viewModel.scanDiscoveredModels() },
                        modifier = Modifier.size(32.dp).testTag("refresh_models_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Ricarica", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.size(32.dp).testTag("import_model_file_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Importa da memoria", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (discoveredModels.isEmpty()) {
            item {
                Text(
                    text = "Nessun modello .bin o .task trovato nelle cartelle dell'app o in Download.\nUsa i link HuggingFace sotto o tocca la cartella per importare un file già presente sul dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(discoveredModels, key = { it.path }) { model ->
                LocalModelCard(
                    model = model,
                    isActiveBase = activeBaseModel?.path == model.path,
                    isActiveLora = activeLoraAdapter?.path == model.path,
                    onSetBase = { viewModel.setActiveBaseModel(model) },
                    onSetLora = { viewModel.setActiveLoraAdapter(model) },
                    onDelete = { viewModel.deleteLocalModel(model) }
                )
            }
        }

        // 5. Download Diretto da Fonti Riconosciute (HuggingFace)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fonti Ufficiali HuggingFace",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { showCustomDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("URL Custom", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        items(viewModel.recognizedCatalog, key = { it.id }) { item ->
            HuggingFaceModelCard(
                model = item,
                isDownloading = downloadProgress?.modelId == item.fileName && !(downloadProgress?.isCompleted ?: false),
                isAlreadyDownloaded = discoveredModels.any { it.name == item.fileName },
                onDownload = { viewModel.downloadModel(item) }
            )
        }

        // 6. Generatore Dati di Addestramento LoRA (JSONL)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lora_dataset_generator_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.DataObject,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Generatore Dataset LoRA (JSONL)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Struttura termini e contesto per Unsloth / Hugging Face",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Badge Fonti Autorevoli
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Fonti: ERA STI (Loc&Pas, CCS, INF, ENE) • EN 50126 • UIC RailLexic",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Statistiche Dataset
                    if (loraStats != null) {
                        val stats = loraStats!!
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "${stats.totalAuthoritativeTerms}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(text = "Termini UE (7L)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "${stats.totalAuthoritativeStatements}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(text = "Clausole STI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "~${stats.estimatedGeneratedSamples}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(text = "Campioni LoRA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "~${stats.estimatedTokens / 1000}k", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(text = "Token Stimati", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }

                    // Selettore Ambito Linguistico
                    Text(
                        text = "Ambito Linguistico:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = exportAllLanguages,
                            onClick = { exportAllLanguages = true },
                            label = { Text("7 Lingue UE (Multilingue)", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !exportAllLanguages,
                            onClick = { exportAllLanguages = false },
                            label = { Text("Coppia EN ↔ IT", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Selettore Formato
                    Text(
                        text = "Formato di Esportazione:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedFormat == LoraDatasetFormat.MESSAGES_JSONL,
                            onClick = { selectedFormat = LoraDatasetFormat.MESSAGES_JSONL },
                            label = { Text("Messages (HF)", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedFormat == LoraDatasetFormat.GEMMA_RAW_JSONL,
                            onClick = { selectedFormat = LoraDatasetFormat.GEMMA_RAW_JSONL },
                            label = { Text("Gemma Raw", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedFormat == LoraDatasetFormat.ALPACA_JSONL,
                            onClick = { selectedFormat = LoraDatasetFormat.ALPACA_JSONL },
                            label = { Text("Alpaca", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Pulsanti Azione
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val config = com.example.lora.LoraExportConfig(
                                    sourceLanguage = "en",
                                    targetLanguage = "it",
                                    allLanguagePairs = exportAllLanguages,
                                    includeAuthoritativeTerms = includeAuthoritativeCorpus,
                                    includeAuthoritativeStatements = includeAuthoritativeCorpus,
                                    format = selectedFormat
                                )
                                viewModel.exportLoraDataset(config) { file ->
                                    lastExportedFile = file
                                    if (file != null) {
                                        Toast.makeText(context, "Dataset generato: ${file.name}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_lora_dataset_button")
                        ) {
                            Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Esporta JSONL")
                        }

                        if (lastExportedFile != null && lastExportedFile!!.exists()) {
                            OutlinedButton(
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Railway LoRA Dataset (${lastExportedFile!!.name})")
                                        val sampleText = lastExportedFile!!.readLines().take(50).joinToString("\n")
                                        putExtra(Intent.EXTRA_TEXT, "Dataset Ferroviario JSONL:\n$sampleText")
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Condividi Dataset JSONL")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.testTag("share_lora_dataset_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Condividi", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Stato esportazione
                    if (exportStatus != null) {
                        Text(
                            text = exportStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Download Diretto da HuggingFace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Inserisci l'URL diretto al file .bin su HuggingFace (es. https://huggingface.co/.../resolve/main/model.bin):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("URL HuggingFace") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customFileName,
                        onValueChange = { customFileName = it },
                        label = { Text("Nome file destinazione (.bin)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customUrl.isNotBlank() && customFileName.isNotBlank()) {
                            viewModel.downloadCustomUrl(customUrl, customFileName)
                            showCustomDialog = false
                        }
                    }
                ) {
                    Text("Avvia Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
fun LocalModelCard(
    model: LocalModelInfo,
    isActiveBase: Boolean,
    isActiveLora: Boolean,
    onSetBase: () -> Unit,
    onSetLora: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${model.formattedSize} • ${if (model.type == ModelType.LORA_ADAPTER) "LoRA Adapter" else "Modello Base"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (model.type == ModelType.BASE_MODEL) {
                    FilledTonalButton(
                        onClick = onSetBase,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        enabled = !isActiveBase
                    ) {
                        Text(if (isActiveBase) "Base Attivo" else "Usa come Base", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onSetLora,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        enabled = !isActiveLora
                    ) {
                        Text(if (isActiveLora) "LoRA Attivo" else "Usa come LoRA", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun HuggingFaceModelCard(
    model: DownloadableModel,
    isDownloading: Boolean,
    isAlreadyDownloaded: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (model.isRecommended) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Consigliato",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "${model.huggingFaceRepo} • ${model.estimatedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isAlreadyDownloaded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Installato",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Installato", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Button(
                    onClick = onDownload,
                    enabled = !isDownloading,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isDownloading) "..." else "Scarica", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
