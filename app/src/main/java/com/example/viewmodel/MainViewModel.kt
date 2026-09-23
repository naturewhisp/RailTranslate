package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import com.example.data.AppDatabase
import com.example.data.DictionaryRepository
import com.example.data.DictionaryTerm
import com.example.data.HistoryDao
import com.example.data.TranslationHistory
import com.example.litert.DownloadProgressState
import com.example.litert.DownloadableModel
import com.example.litert.LiteRTEngine
import com.example.litert.LocalModelInfo
import com.example.litert.ModelManager
import com.example.litert.ModelType
import com.example.lora.LoraDatasetFormat
import com.example.lora.LoraDatasetStats
import com.example.lora.LoraExportConfig
import com.example.lora.LoraTrainingDataManager
import com.example.translation.EuropeanLanguages
import com.example.translation.RailwayTranslationEngine
import com.example.translation.SupportedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InferenceMetrics(
    val latencyMs: Long = 0,
    val acceleratorName: String = "Pixel 10 Pro Tensor NPU (Gemma 4)",
    val isStreaming: Boolean = false
)

enum class TranslationEngineType(val label: String, val badge: String) {
    AICORE_NPU("AICore NPU (Pixel 10 Pro)", "Tensor G5 NPU"),
    LITERT_LORA("LiteRT (Supporto Pesi LoRA)", "LiteRT + LoRA")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DictionaryRepository
    private val historyDao: HistoryDao
    val dictionaryTerms: StateFlow<List<DictionaryTerm>>
    val allHistory: StateFlow<List<TranslationHistory>>
    
    private val translatorClient: RailwayTranslationEngine
    val modelManager: ModelManager = ModelManager(application)
    val liteRTEngine: LiteRTEngine
    val loraTrainingDataManager: LoraTrainingDataManager
    
    private val _selectedEngine = MutableStateFlow(TranslationEngineType.AICORE_NPU)
    val selectedEngine: StateFlow<TranslationEngineType> = _selectedEngine.asStateFlow()

    private val _discoveredModels = MutableStateFlow<List<LocalModelInfo>>(emptyList())
    val discoveredModels: StateFlow<List<LocalModelInfo>> = _discoveredModels.asStateFlow()

    private val _activeBaseModel = MutableStateFlow<LocalModelInfo?>(null)
    val activeBaseModel: StateFlow<LocalModelInfo?> = _activeBaseModel.asStateFlow()

    private val _activeLoraAdapter = MutableStateFlow<LocalModelInfo?>(null)
    val activeLoraAdapter: StateFlow<LocalModelInfo?> = _activeLoraAdapter.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgressState?>(null)
    val downloadProgress: StateFlow<DownloadProgressState?> = _downloadProgress.asStateFlow()

    private val _loraDatasetStats = MutableStateFlow<LoraDatasetStats?>(null)
    val loraDatasetStats: StateFlow<LoraDatasetStats?> = _loraDatasetStats.asStateFlow()

    private val _exportStatusMessage = MutableStateFlow<String?>(null)
    val exportStatusMessage: StateFlow<String?> = _exportStatusMessage.asStateFlow()

    val recognizedCatalog: List<DownloadableModel> = modelManager.recognizedCatalog
    
    private val _sourceLanguage = MutableStateFlow(EuropeanLanguages[0])
    val sourceLanguage: StateFlow<SupportedLanguage> = _sourceLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow(EuropeanLanguages[1])
    val targetLanguage: StateFlow<SupportedLanguage> = _targetLanguage.asStateFlow()

    private val _translationResult = MutableStateFlow("")
    val translationResult: StateFlow<String> = _translationResult.asStateFlow()

    private val _isDownloadingModel = MutableStateFlow(false)
    val isDownloadingModel: StateFlow<Boolean> = _isDownloadingModel.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _relatedTerms = MutableStateFlow<List<DictionaryTerm>>(emptyList())
    val relatedTerms: StateFlow<List<DictionaryTerm>> = _relatedTerms.asStateFlow()

    private val _normativeMode = MutableStateFlow(true)
    val normativeMode: StateFlow<Boolean> = _normativeMode.asStateFlow()

    private val _inferenceMetrics = MutableStateFlow(InferenceMetrics())
    val inferenceMetrics: StateFlow<InferenceMetrics> = _inferenceMetrics.asStateFlow()

    val engineModelName = "Gemma 4 Edge (Gemini Nano 4 via AICore NPU)"

    init {
        val database = AppDatabase.getDatabase(application)
        repository = DictionaryRepository(database.dictionaryDao())
        historyDao = database.historyDao()
        
        translatorClient = RailwayTranslationEngine(application, database.dictionaryDao())
        liteRTEngine = LiteRTEngine(application, database.dictionaryDao())
        loraTrainingDataManager = LoraTrainingDataManager(database.dictionaryDao(), historyDao)
        
        dictionaryTerms = repository.allTerms.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allHistory = historyDao.getAllHistory().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
        
        viewModelScope.launch {
            repository.initDefaultTerms()
            prepareTranslationModel()
            scanDiscoveredModels()
            refreshLoraStats()
        }
    }

    fun setEngine(engine: TranslationEngineType) {
        _selectedEngine.value = engine
        val name = if (engine == TranslationEngineType.AICORE_NPU) {
            "Pixel 10 Pro Tensor NPU (Gemma 4)"
        } else {
            "LiteRT Local ${if (liteRTEngine.isLoraActive) "+ LoRA TSI" else "(Gemma)"}"
        }
        _inferenceMetrics.value = _inferenceMetrics.value.copy(acceleratorName = name)
    }

    fun scanDiscoveredModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val models = modelManager.discoverLocalModels()
            _discoveredModels.value = models
            // Seleziona automaticamente il primo modello base e il primo lora se non già impostati
            if (_activeBaseModel.value == null) {
                val base = models.firstOrNull { it.type == ModelType.BASE_MODEL }
                if (base != null) setActiveBaseModel(base)
            }
            if (_activeLoraAdapter.value == null) {
                val lora = models.firstOrNull { it.type == ModelType.LORA_ADAPTER }
                if (lora != null) setActiveLoraAdapter(lora)
            }
        }
    }

    fun setActiveBaseModel(model: LocalModelInfo?) {
        _activeBaseModel.value = model
        liteRTEngine.activeBaseModel = model
    }

    fun setActiveLoraAdapter(model: LocalModelInfo?) {
        _activeLoraAdapter.value = model
        liteRTEngine.activeLoraAdapter = model
    }

    fun downloadModel(model: DownloadableModel) {
        viewModelScope.launch {
            _downloadProgress.value = DownloadProgressState(
                modelId = model.id,
                fileName = model.fileName,
                progress = 0f,
                downloadedBytes = 0,
                totalBytes = 0
            )
            val result = modelManager.downloadModel(
                url = model.downloadUrl,
                targetFileName = model.fileName,
                onProgress = { progressState ->
                    _downloadProgress.value = progressState
                }
            )
            if (result.isSuccess) {
                scanDiscoveredModels()
            }
        }
    }

    fun downloadCustomUrl(url: String, fileName: String) {
        if (url.isBlank() || fileName.isBlank()) return
        viewModelScope.launch {
            _downloadProgress.value = DownloadProgressState(
                modelId = fileName,
                fileName = fileName,
                progress = 0f,
                downloadedBytes = 0,
                totalBytes = 0
            )
            val result = modelManager.downloadModel(
                url = url.trim(),
                targetFileName = fileName.trim(),
                onProgress = { progressState ->
                    _downloadProgress.value = progressState
                }
            )
            if (result.isSuccess) {
                scanDiscoveredModels()
            }
        }
    }

    fun importModel(uri: Uri, displayName: String) {
        viewModelScope.launch {
            val result = modelManager.importModelFromUri(uri, displayName)
            if (result.isSuccess) {
                scanDiscoveredModels()
            }
        }
    }

    fun deleteLocalModel(model: LocalModelInfo) {
        viewModelScope.launch {
            modelManager.deleteLocalModel(model.path)
            if (_activeBaseModel.value?.path == model.path) {
                setActiveBaseModel(null)
            }
            if (_activeLoraAdapter.value?.path == model.path) {
                setActiveLoraAdapter(null)
            }
            scanDiscoveredModels()
        }
    }

    fun clearDownloadProgress() {
        _downloadProgress.value = null
    }

    fun refreshLoraStats() {
        viewModelScope.launch {
            _loraDatasetStats.value = loraTrainingDataManager.getDatasetStatistics()
        }
    }

    fun exportLoraDataset(
        config: LoraExportConfig = LoraExportConfig(),
        onComplete: (File?) -> Unit = {}
    ) {
        viewModelScope.launch {
            _exportStatusMessage.value = "Generazione dataset LoRA da fonti autorevoli (ERA STI / CENELEC / UIC) in corso..."
            val result = loraTrainingDataManager.exportDatasetToFile(
                context = getApplication(),
                config = config
            )
            result.onSuccess { file ->
                _exportStatusMessage.value = "Dataset esportato con successo:\n${file.name} (${file.length() / 1024} KB)"
                refreshLoraStats()
                onComplete(file)
            }.onFailure { error ->
                _exportStatusMessage.value = "Errore durante l'esportazione: ${error.localizedMessage}"
                onComplete(null)
            }
        }
    }

    fun clearExportStatus() {
        _exportStatusMessage.value = null
    }

    fun setSourceLanguage(lang: SupportedLanguage) {
        if (lang.code == _targetLanguage.value.code) {
            swapLanguages()
            return
        }
        _sourceLanguage.value = lang
        prepareTranslationModel()
    }

    fun setTargetLanguage(lang: SupportedLanguage) {
        if (lang.code == _sourceLanguage.value.code) {
            swapLanguages()
            return
        }
        _targetLanguage.value = lang
        prepareTranslationModel()
    }

    fun swapLanguages() {
        val temp = _sourceLanguage.value
        _sourceLanguage.value = _targetLanguage.value
        _targetLanguage.value = temp
        prepareTranslationModel()
    }

    private fun prepareTranslationModel() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isDownloadingModel.value = true
            try {
                translatorClient.warmUpEngine(
                    sourceLang = _sourceLanguage.value.displayName,
                    targetLang = _targetLanguage.value.displayName
                )
            } catch (_: Exception) {
            } finally {
                _isDownloadingModel.value = false
            }
        }
    }

    fun setNormativeMode(enabled: Boolean) {
        _normativeMode.value = enabled
    }

    fun translate(text: String) {
        if (text.isBlank()) {
            _translationResult.value = ""
            _relatedTerms.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isTranslating.value = true
            val startTime = System.currentTimeMillis()
            val engine = _selectedEngine.value
            val acceleratorDesc = if (engine == TranslationEngineType.AICORE_NPU) {
                "Pixel 10 Pro Tensor NPU (Gemma 4)"
            } else {
                "LiteRT Local ${if (liteRTEngine.isLoraActive) "+ LoRA TSI" else "(Gemma)"}"
            }
            _inferenceMetrics.value = InferenceMetrics(
                latencyMs = 0,
                acceleratorName = acceleratorDesc,
                isStreaming = true
            )
            
            val terms = dictionaryTerms.first()
            
            // Rileva termini ferroviari presenti nel testo originale
            val foundTerms = terms.filter { dictTerm ->
                val termRegex = Regex("(?i)\\b${Regex.escape(dictTerm.term)}\\b")
                val translationRegex = Regex("(?i)\\b${Regex.escape(dictTerm.translation)}\\b")
                termRegex.containsMatchIn(text) || translationRegex.containsMatchIn(text)
            }
            _relatedTerms.value = foundTerms

            var finalResult = ""
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val stream = if (engine == TranslationEngineType.AICORE_NPU) {
                    translatorClient.translateTechnicalSnippetStream(
                        text = text,
                        sourceLang = _sourceLanguage.value.displayName,
                        targetLang = _targetLanguage.value.displayName,
                        normativeMode = _normativeMode.value
                    )
                } else {
                    liteRTEngine.translateStream(
                        text = text,
                        sourceLang = _sourceLanguage.value.displayName,
                        targetLang = _targetLanguage.value.displayName,
                        normativeMode = _normativeMode.value
                    )
                }

                stream.collect { partialResult ->
                    finalResult = partialResult
                    _translationResult.value = partialResult
                }
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            _inferenceMetrics.value = InferenceMetrics(
                latencyMs = elapsed,
                acceleratorName = acceleratorDesc,
                isStreaming = false
            )

            if (finalResult.isNotBlank() && !finalResult.startsWith("Error") && !finalResult.startsWith("Translation error")) {
                historyDao.insert(
                    TranslationHistory(
                        sourceText = text,
                        translatedText = finalResult,
                        sourceLang = _sourceLanguage.value.displayName,
                        targetLang = _targetLanguage.value.displayName
                    )
                )
            }
            _isTranslating.value = false
        }
    }

    fun addTerm(term: String, translation: String) {
        if (term.isNotBlank() && translation.isNotBlank()) {
            viewModelScope.launch {
                repository.insert(DictionaryTerm(term = term.trim(), translation = translation.trim()))
            }
        }
    }

    fun deleteTerm(id: Int) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }
}

