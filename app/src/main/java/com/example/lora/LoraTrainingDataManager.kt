package com.example.lora

import android.content.Context
import com.example.data.DictionaryDao
import com.example.data.DictionaryTerm
import com.example.data.HistoryDao
import com.example.data.TranslationHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Formati supportati per l'addestramento LoRA:
 * - MESSAGES_JSONL: Formato chat standard Hugging Face (TRL SFTTrainer, Unsloth, Axolotl)
 * - GEMMA_RAW_JSONL: Formato token nativi Gemma (<start_of_turn>user / <start_of_turn>model)
 * - ALPACA_JSONL: Formato classico Instruction / Input / Output
 */
enum class LoraDatasetFormat {
    MESSAGES_JSONL,
    GEMMA_RAW_JSONL,
    ALPACA_JSONL
}

internal fun escapeJson(value: String): String {
    val sb = StringBuilder()
    for (c in value) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> {
                if (c < ' ') {
                    val hex = Integer.toHexString(c.code)
                    sb.append("\\u").append("0".repeat(4 - hex.length)).append(hex)
                } else {
                    sb.append(c)
                }
            }
        }
    }
    return sb.toString()
}

data class LoraTrainingSample(
    val instruction: String,
    val input: String = "",
    val output: String,
    val category: String = "Ferroviario",
    val sourceTerm: String? = null
) {
    /**
     * Serializza il singolo record nel formato JSON richiesto
     */
    fun toJson(format: LoraDatasetFormat): String {
        val userPrompt = if (input.isNotBlank()) "$instruction\n$input" else instruction
        return when (format) {
            LoraDatasetFormat.MESSAGES_JSONL -> {
                "{\"messages\":[{\"role\":\"user\",\"content\":\"${escapeJson(userPrompt)}\"},{\"role\":\"model\",\"content\":\"${escapeJson(output)}\"}]}"
            }

            LoraDatasetFormat.GEMMA_RAW_JSONL -> {
                val rawText = "<start_of_turn>user\n$userPrompt<end_of_turn>\n<start_of_turn>model\n$output<end_of_turn>"
                "{\"text\":\"${escapeJson(rawText)}\"}"
            }

            LoraDatasetFormat.ALPACA_JSONL -> {
                "{\"instruction\":\"${escapeJson(instruction)}\",\"input\":\"${escapeJson(input)}\",\"output\":\"${escapeJson(output)}\"}"
            }
        }
    }
}

data class LoraExportConfig(
    val sourceLanguage: String = "en",
    val targetLanguage: String = "it",
    val allLanguagePairs: Boolean = false,
    val includeAuthoritativeTerms: Boolean = true,
    val includeAuthoritativeStatements: Boolean = true,
    val includeDirectTerms: Boolean = true,
    val includeReverseTerms: Boolean = true,
    val includeSyntheticSentences: Boolean = true,
    val includeHardNegatives: Boolean = true,
    val includeValidatedHistory: Boolean = true,
    val format: LoraDatasetFormat = LoraDatasetFormat.MESSAGES_JSONL
)

data class LoraDatasetStats(
    val totalTermsInGlossary: Int,
    val totalAuthoritativeTerms: Int,
    val totalAuthoritativeStatements: Int,
    val totalHistoryRecords: Int,
    val estimatedGeneratedSamples: Int,
    val estimatedTokens: Int
)

/**
 * Data Manager per la strutturazione, validazione ed esportazione
 * del dataset di addestramento LoRA in formato JSONL.
 */
class LoraTrainingDataManager(
    private val dictionaryDao: DictionaryDao,
    private val historyDao: HistoryDao
) {

    private val supportedLanguagesMap = mapOf(
        "en" to "Inglese",
        "it" to "Italiano",
        "de" to "Tedesco",
        "fr" to "Francese",
        "es" to "Spagnolo",
        "ro" to "Rumeno",
        "bg" to "Bulgaro"
    )

    // Template sintetici per generare frasi contestuali verosimili su norme TSI / EN
    private val enContextTemplates = listOf(
        Pair(
            "The %s must be inspected in compliance with TSI Loc&Pas requirements.",
            "Il componente %s deve essere ispezionato in conformità con i requisiti della TSI Loc&Pas."
        ),
        Pair(
            "Routine maintenance and visual check of the %s showed no critical wear.",
            "La manutenzione ordinaria e il controllo visivo del/della %s non hanno evidenziato usura critica."
        ),
        Pair(
            "Ensure the %s is correctly aligned before torqueing the mounting bolts according to EN standards.",
            "Assicurarsi che il/la %s sia correttamente allineato/a prima del serraggio dei bulloni di fissaggio secondo le norme EN."
        ),
        Pair(
            "Immediate replacement of the %s is required due to thermal stress exceeding admissible safety limits.",
            "È richiesta la sostituzione immediata del/della %s a causa di sollecitazioni termiche superiori ai limiti ammissibili di sicurezza."
        ),
        Pair(
            "During bogie overhaul, the %s must undergo magnetic particle or ultrasonic non-destructive testing.",
            "Durante la revisione del carrello, il/la %s deve essere sottoposto/a a controlli non distruttivi con particelle magnetiche o ultrasuoni."
        )
    )

    // Esempi contrastivi (Hard Negatives) per forzare il modello a non usare falsi amici comuni
    private val hardNegativeGuidance = mapOf(
        "Wheelset" to "Evita traduzioni letterali o generiche come 'set di ruote' o 'coppia di ruote': usa esclusivamente 'sala montata'.",
        "Axlebox" to "Non tradurre come 'scatola dell'asse': il termine tecnico normativo ferroviario è 'boccola'.",
        "Bogie" to "Attenzione al contesto: in materiale rotabile è 'carrello', non confondere con carrello portabagagli.",
        "Dead man's switch" to "In segnalamento e condotta ferroviaria si traduce con 'dispositivo vigilante' o 'VACMA', mai 'interruttore dell'uomo morto'.",
        "Track circuit" to "Termine del segnalamento ferroviario: si traduce 'circuito di binario' (CdB), non 'circuito della pista'.",
        "Buffer" to "In ingegneria dei rotabili si traduce 'respingente', non 'tampone' o 'buffer di memoria'."
    )

    /**
     * Raccoglie tutti i dati e genera la lista di campioni LoRA strutturati
     */
    suspend fun generateDataset(config: LoraExportConfig): List<LoraTrainingSample> = withContext(Dispatchers.IO) {
        val samples = mutableListOf<LoraTrainingSample>()
        val terms = dictionaryDao.getAllTermsSync()
        val history = historyDao.getAllHistorySync()

        // 1. Elaborazione del Corpus Autorevole Europeo (ERA TSI / EN Standards / UIC)
        if (config.includeAuthoritativeTerms) {
            val allCodes = listOf("en", "it", "de", "fr", "es", "ro", "bg")
            val languagePairsToProcess: List<Pair<String, String>> = if (config.allLanguagePairs) {
                val pairs = mutableListOf<Pair<String, String>>()
                for (src in allCodes) {
                    for (tgt in allCodes) {
                        if (src != tgt) pairs.add(Pair(src, tgt))
                    }
                }
                pairs
            } else {
                listOf(
                    Pair(config.sourceLanguage, config.targetLanguage),
                    Pair(config.targetLanguage, config.sourceLanguage)
                )
            }

            for (term in AuthoritativeRailwayCorpus.TERMS) {
                for ((srcCode, tgtCode) in languagePairsToProcess) {
                    val srcTerm = term.getTranslation(srcCode)
                    val tgtTerm = term.getTranslation(tgtCode)
                    val srcName = supportedLanguagesMap[srcCode] ?: srcCode.uppercase()
                    val tgtName = supportedLanguagesMap[tgtCode] ?: tgtCode.uppercase()

                    // Sample terminologico standard
                    samples.add(
                        LoraTrainingSample(
                            instruction = "[${term.standardRef}] Traduci il termine tecnico ferroviario da $srcName a $tgtName:",
                            input = srcTerm,
                            output = tgtTerm,
                            category = term.category,
                            sourceTerm = srcTerm
                        )
                    )

                    // Se presente un avviso per falsi amici (Hard Negative)
                    if (config.includeHardNegatives && term.falseFriendWarning.containsKey(tgtCode)) {
                        val warning = term.falseFriendWarning[tgtCode]!!
                        samples.add(
                            LoraTrainingSample(
                                instruction = "Traduci in ambito ferroviario (${term.category}). Attenzione: $warning Traduci da $srcName a $tgtName:",
                                input = srcTerm,
                                output = tgtTerm,
                                category = term.category,
                                sourceTerm = srcTerm
                            )
                        )
                    }
                }
            }
        }

        // 2. Enunciati Normativi Autentici dalle Specifiche Tecniche di Interoperabilità (TSI / EN)
        if (config.includeAuthoritativeStatements) {
            val allCodes = listOf("en", "it", "de", "fr", "es", "ro", "bg")
            val statementPairs: List<Pair<String, String>> = if (config.allLanguagePairs) {
                val pairs = mutableListOf<Pair<String, String>>()
                for (src in allCodes) {
                    for (tgt in allCodes) {
                        if (src != tgt) pairs.add(Pair(src, tgt))
                    }
                }
                pairs
            } else {
                listOf(
                    Pair(config.sourceLanguage, config.targetLanguage),
                    Pair(config.targetLanguage, config.sourceLanguage)
                )
            }

            for (stmt in AuthoritativeRailwayCorpus.STATEMENTS) {
                for ((srcCode, tgtCode) in statementPairs) {
                    val srcText = stmt.translations[srcCode]
                    val tgtText = stmt.translations[tgtCode]
                    if (!srcText.isNullOrBlank() && !tgtText.isNullOrBlank()) {
                        val srcName = supportedLanguagesMap[srcCode] ?: srcCode.uppercase()
                        val tgtName = supportedLanguagesMap[tgtCode] ?: tgtCode.uppercase()

                        samples.add(
                            LoraTrainingSample(
                                instruction = "Traduci la seguente clausola normativa ufficiale (${stmt.standard} - ${stmt.clause}) da $srcName a $tgtName:",
                                input = srcText,
                                output = tgtText,
                                category = stmt.topic,
                                sourceTerm = stmt.standard
                            )
                        )
                    }
                }
            }
        }

        // 3. Termini del dizionario locale dell'app (Room Database)
        for (term in terms) {
            val enTerm = term.term.trim()
            val itTerm = term.translation.trim()
            val category = term.category

            // Traduzione diretta EN -> IT
            if (config.includeDirectTerms) {
                samples.add(
                    LoraTrainingSample(
                        instruction = "Traduci il seguente termine tecnico ferroviario secondo le norme europee TSI/EN da Inglese a Italiano:",
                        input = enTerm,
                        output = itTerm,
                        category = category,
                        sourceTerm = enTerm
                    )
                )
            }

            // Traduzione inversa IT -> EN
            if (config.includeReverseTerms) {
                samples.add(
                    LoraTrainingSample(
                        instruction = "Traduci il seguente termine tecnico ferroviario secondo le norme europee TSI/EN da Italiano a Inglese:",
                        input = itTerm,
                        output = enTerm,
                        category = category,
                        sourceTerm = itTerm
                    )
                )
            }

            // Frasi sintetiche contestualizzate
            if (config.includeSyntheticSentences) {
                for (template in enContextTemplates) {
                    val enSentence = String.format(template.first, enTerm.lowercase())
                    val itSentence = String.format(template.second, itTerm.lowercase())

                    samples.add(
                        LoraTrainingSample(
                            instruction = "Traduci la seguente frase di ingegneria e manutenzione ferroviaria (TSI/EN) da Inglese a Italiano:",
                            input = enSentence,
                            output = itSentence,
                            category = category,
                            sourceTerm = enTerm
                        )
                    )
                    samples.add(
                        LoraTrainingSample(
                            instruction = "Traduci la seguente frase di ingegneria e manutenzione ferroviaria (TSI/EN) da Italiano a Inglese:",
                            input = itSentence,
                            output = enSentence,
                            category = category,
                            sourceTerm = itTerm
                        )
                    )
                }
            }

            // Hard Negatives
            if (config.includeHardNegatives && hardNegativeGuidance.containsKey(enTerm)) {
                val warning = hardNegativeGuidance[enTerm]
                samples.add(
                    LoraTrainingSample(
                        instruction = "Traduci in ambito ferroviario applicando rigorosamente la terminologia tecnica. $warning",
                        input = "Technical requirement: Inspect the $enTerm thoroughly before revenue service.",
                        output = "Requisito tecnico: Ispezionare accuratamente il/la $itTerm prima dell'immissione in servizio commerciale.",
                        category = category,
                        sourceTerm = enTerm
                    )
                )
            }
        }

        // 4. Traduzioni storiche validate registrate nell'app
        if (config.includeValidatedHistory) {
            for (record in history) {
                if (record.sourceText.isNotBlank() && record.translatedText.isNotBlank()) {
                    samples.add(
                        LoraTrainingSample(
                            instruction = "Traduci la seguente comunicazione ferroviaria da ${record.sourceLang} a ${record.targetLang}:",
                            input = record.sourceText,
                            output = record.translatedText,
                            category = "Storico Validato"
                        )
                    )
                }
            }
        }

        samples
    }

    /**
     * Converte la lista di campioni in una stringa JSONL pronta per l'addestramento
     */
    fun convertToJsonl(samples: List<LoraTrainingSample>, format: LoraDatasetFormat): String {
        val sb = StringBuilder()
        for (sample in samples) {
            sb.append(sample.toJson(format))
            sb.append("\n")
        }
        return sb.toString()
    }

    /**
     * Esporta il dataset generato direttamente in un file locale del dispositivo
     */
    suspend fun exportDatasetToFile(
        context: Context,
        config: LoraExportConfig = LoraExportConfig(),
        targetFile: File? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val samples = generateDataset(config)
            val jsonlContent = convertToJsonl(samples, config.format)

            val destFile = targetFile ?: run {
                val exportDir = File(context.getExternalFilesDir(null), "datasets")
                if (!exportDir.exists()) exportDir.mkdirs()
                val extension = when (config.format) {
                    LoraDatasetFormat.MESSAGES_JSONL -> "messages.jsonl"
                    LoraDatasetFormat.GEMMA_RAW_JSONL -> "gemma_raw.jsonl"
                    LoraDatasetFormat.ALPACA_JSONL -> "alpaca.jsonl"
                }
                File(exportDir, "railway_lora_dataset_$extension")
            }

            OutputStreamWriter(FileOutputStream(destFile), StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonlContent)
                writer.flush()
            }

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calcola metriche e statistiche sul dataset estraibile
     */
    suspend fun getDatasetStatistics(): LoraDatasetStats = withContext(Dispatchers.IO) {
        val termsCount = dictionaryDao.getCount()
        val historyCount = historyDao.getCount()

        val authoritativeTermsCount = AuthoritativeRailwayCorpus.TERMS.size
        val authoritativeStatementsCount = AuthoritativeRailwayCorpus.STATEMENTS.size

        // Calcolo stimato campioni:
        // - Termini del dizionario locale: (termsCount * 12) + 6 + historyCount
        // - Termini autorevoli per coppia linguistica base (diretto, inverso, hard negative): ~50
        // - Se allLanguagePairs è attivo: 7x6 = 42 permutazioni per ciascun termine (~1000 campioni)
        // - Enunciati normativi TSI/EN: 4 x 2 = 8 campioni (o 4 x 42 = 168 per tutte le lingue)
        val estimatedSamples = (termsCount * 12) + (authoritativeTermsCount * 3) + (authoritativeStatementsCount * 2) + 6 + historyCount
        // Media 55 token per campione (gli enunciati normativi TSI sono più lunghi e ricchi di token)
        val estimatedTokens = estimatedSamples * 55

        LoraDatasetStats(
            totalTermsInGlossary = termsCount,
            totalAuthoritativeTerms = authoritativeTermsCount,
            totalAuthoritativeStatements = authoritativeStatementsCount,
            totalHistoryRecords = historyCount,
            estimatedGeneratedSamples = estimatedSamples,
            estimatedTokens = estimatedTokens
        )
    }
}
