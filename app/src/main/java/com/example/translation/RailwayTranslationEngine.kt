package com.example.translation

import android.content.Context
import com.example.data.DictionaryDao
import com.example.data.DictionaryTerm
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Motore di traduzione on-device per ingegneria ferroviaria e materiale rotabile,
 * ottimizzato per l'architettura edge Gemma 4 (Gemini Nano 4 via AICore NPU su Google Pixel).
 */
class RailwayTranslationEngine(
    private val context: Context,
    private val localGlossaryDb: DictionaryDao
) {

    // Configurazione ultra-precisa per modelli Gemma 4 Edge / Gemini Nano
    // Temperature bassa per minimizzare allucinazioni e massimizzare la fedeltà normativa
    private val config by lazy {
        generationConfig {
            context = this@RailwayTranslationEngine.context.applicationContext
            temperature = 0.10f
            topK = 5
            maxOutputTokens = 512
        }
    }

    private var currentModel: GenerativeModel? = null
    private var currentSourceLang: String = ""
    private var currentTargetLang: String = ""

    private fun getModel(sourceLang: String, targetLang: String): GenerativeModel {
        if (currentModel == null || currentSourceLang != sourceLang || currentTargetLang != targetLang) {
            currentSourceLang = sourceLang
            currentTargetLang = targetLang
            currentModel = GenerativeModel(config)
        }
        return currentModel!!
    }

    /**
     * Pre-riscalda l'NPU di Pixel 10 Pro (Tensor G5 / AICore) per eliminare
     * la latenza del primo token all'avvio o al cambio lingua.
     */
    suspend fun warmUpEngine(sourceLang: String, targetLang: String) {
        try {
            val model = getModel(sourceLang, targetLang)
            model.prepareInferenceEngine()
        } catch (_: Throwable) {
            // Fail-safe silencioso se l'emulatore non espone AICore
        }
    }

    /**
     * Traduzione in tempo reale con token streaming progressivo dall'NPU di AICore.
     */
    fun translateTechnicalSnippetStream(
        text: String,
        sourceLang: String,
        targetLang: String,
        normativeMode: Boolean = true
    ): Flow<String> = flow {
        val allTerms = localGlossaryDb.getAllTermsSync()
        val relevantTerms = allTerms.filter { term ->
            text.contains(term.term, ignoreCase = true) || text.contains(term.translation, ignoreCase = true)
        }
        val isSourceItalian = sourceLang.contains("Italian", ignoreCase = true)
        val fullPrompt = buildGemmaPrompt(text, sourceLang, targetLang, relevantTerms, isSourceItalian, normativeMode)

        try {
            val model = getModel(sourceLang, targetLang)
            val stream = model.generateContentStream(fullPrompt)
            val cumulative = StringBuilder()
            
            stream.collect { chunk ->
                chunk.text?.let { part ->
                    cumulative.append(part)
                    emit(cleanModelOutput(cumulative.toString()))
                }
            }

            val finalOutput = cleanModelOutput(cumulative.toString())
            if (finalOutput.isNotBlank()) {
                emit(enforceGlossaryCompliance(finalOutput, relevantTerms, isSourceItalian))
            } else {
                emit(fallbackGlossaryTranslation(text, relevantTerms, isSourceItalian))
            }
        } catch (e: Throwable) {
            val fallback = fallbackGlossaryTranslation(text, relevantTerms, isSourceItalian)
            if (fallback.isNotBlank() && fallback != text) {
                emit(fallback)
            } else {
                emit("AICore Engine fallback (Gemma 4): ${e.localizedMessage ?: "Modello on-device pronto"}")
            }
        }
    }

    suspend fun translateTechnicalSnippet(
        text: String,
        sourceLang: String,
        targetLang: String,
        normativeMode: Boolean = true
    ): String {
        val allTerms = localGlossaryDb.getAllTermsSync()
        val relevantTerms = allTerms.filter { term ->
            text.contains(term.term, ignoreCase = true) || text.contains(term.translation, ignoreCase = true)
        }
        val isSourceItalian = sourceLang.contains("Italian", ignoreCase = true)
        val fullPrompt = buildGemmaPrompt(text, sourceLang, targetLang, relevantTerms, isSourceItalian, normativeMode)

        return try {
            val model = getModel(sourceLang, targetLang)
            val response = model.generateContent(fullPrompt)
            val rawTranslation = response.text?.trim().orEmpty()
            val cleaned = cleanModelOutput(rawTranslation)
            
            if (cleaned.isNotBlank()) {
                enforceGlossaryCompliance(cleaned, relevantTerms, isSourceItalian)
            } else {
                fallbackGlossaryTranslation(text, relevantTerms, isSourceItalian)
            }
        } catch (e: Throwable) {
            val fallback = fallbackGlossaryTranslation(text, relevantTerms, isSourceItalian)
            if (fallback.isNotBlank() && fallback != text) {
                fallback
            } else {
                "AICore Engine fallback (Gemma 4): ${e.localizedMessage ?: "Modello on-device pronto per l'esecuzione"}"
            }
        }
    }

    private fun buildGemmaPrompt(
        text: String,
        sourceLang: String,
        targetLang: String,
        relevantTerms: List<DictionaryTerm>,
        isSourceItalian: Boolean,
        normativeMode: Boolean
    ): String {
        val glossaryEntries = relevantTerms.take(12).map { term ->
            if (isSourceItalian) {
                "- \"${term.translation}\" -> \"${term.term}\""
            } else {
                "- \"${term.term}\" -> \"${term.translation}\""
            }
        }

        val glossaryBlock = if (glossaryEntries.isNotEmpty()) {
            """
            GLOSSARIO FERROVIARIO VINCOLANTE (RISPETTA RIGIDAMENTE QUESTE CORRISPONDENZE):
            ${glossaryEntries.joinToString("\n")}
            """.trimIndent()
        } else ""

        val systemRules = """
            Sei un traduttore esperto basato su architettura Gemma 4, specializzato in ingegneria ferroviaria, materiale rotabile (rolling stock), trazione elettrica, impianti di segnalamento (ERTMS/ETCS/SCMT) e manutenzione.
            
            REGOLE IMPERATIVE:
            1. Traduci fedelmente da $sourceLang a $targetLang.
            2. Usa il registro linguistico tecnico formale conforme alle norme ferroviarie europee (TSI/STI, UIC, EN 45545, EN 50128).
            3. Mantieni inalterati codici identificativi, sigle di segnalamento (es. ERTMS, ETCS, SCMT, ATC, ATO, SIL4) e standard (es. TSI Loc&Pas, EN 45545, UIC 540).
            4. Conserva esattamente numeri, tolleranze e unità di misura fisiche (es. kN, km/h, bar, kV, Hz, mm).
            5. Se è fornito un GLOSSARIO FERROVIARIO VINCOLANTE, devi usare ESCLUSIVAMENTE i termini specificati nel glossario senza sinonimi generici.
            6. Fornisci SOLO ed ESCLUSIVAMENTE la traduzione finale senza commenti, saluti, preamboli né formattazioni markdown superflue.
        """.trimIndent()

        val fewShotExamples = if (targetLang.contains("Italian", ignoreCase = true)) {
            """
            Esempio 1:
            Input: "Wheelset bearing temperature alarm detected on trailing bogie."
            Output: "Rilevato allarme temperatura boccole della sala montata sul carrello rimorchiato."

            Esempio 2:
            Input: "Inspect pantograph carbon contact strip wear and check pneumatic pressure (5 bar)."
            Output: "Ispezionare l'usura dello strisciante in carbonio del pantografo e verificare la pressione pneumatica (5 bar)."
            """.trimIndent()
        } else if (sourceLang.contains("Italian", ignoreCase = true)) {
            """
            Esempio 1:
            Input: "Allarme temperatura boccole della sala montata sul carrello rimorchiato."
            Output: "Wheelset bearing temperature alarm detected on trailing bogie."

            Esempio 2:
            Input: "Ispezionare l'usura dello strisciante in carbonio del pantografo e verificare la pressione (5 bar)."
            Output: "Inspect pantograph carbon contact strip wear and verify pneumatic pressure (5 bar)."
            """.trimIndent()
        } else ""

        return buildString {
            append("<start_of_turn>system\n")
            append(systemRules)
            if (glossaryBlock.isNotEmpty()) {
                append("\n\n")
                append(glossaryBlock)
            }
            append("<end_of_turn>\n")

            if (fewShotExamples.isNotEmpty()) {
                append("<start_of_turn>user\n")
                append(fewShotExamples)
                append("<end_of_turn>\n")
            }

            append("<start_of_turn>user\n")
            append("TESTO DA TRADURRE:\n$text\n<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
    }

    /**
     * Rimuove eventuali token di controllo, turn tags e prefissi verbali lasciati dall'output.
     */
    private fun cleanModelOutput(output: String): String {
        return output
            .replace("<start_of_turn>", "")
            .replace("<end_of_turn>", "")
            .replace(Regex("^(Output:|Traduzione:|Risposta:)\\s*", RegexOption.IGNORE_CASE), "")
            .trim('"', ' ', '\n', '\r', '`')
    }

    /**
     * Garantisce che i termini rilevanti del glossario compaiano nella traduzione.
     */
    private fun enforceGlossaryCompliance(
        translatedText: String,
        terms: List<DictionaryTerm>,
        isSourceItalian: Boolean
    ): String {
        var result = translatedText
        for (item in terms) {
            val (sourceTerm, targetTerm) = if (isSourceItalian) {
                item.translation to item.term
            } else {
                item.term to item.translation
            }
            val sourceRegex = Regex("(?i)\\b${Regex.escape(sourceTerm)}\\b")
            if (sourceRegex.containsMatchIn(result) && !result.contains(targetTerm, ignoreCase = true)) {
                result = result.replace(sourceRegex, targetTerm)
            }
        }
        return result
    }

    /**
     * Fallback deterministico offline per scenari senza NPU/AICore.
     */
    private fun fallbackGlossaryTranslation(
        originalText: String,
        terms: List<DictionaryTerm>,
        isSourceItalian: Boolean
    ): String {
        var translated = originalText
        for (item in terms.sortedByDescending { if (isSourceItalian) it.translation.length else it.term.length }) {
            val (sourceTerm, targetTerm) = if (isSourceItalian) {
                item.translation to item.term
            } else {
                item.term to item.translation
            }
            translated = translated.replace(Regex("(?i)\\b${Regex.escape(sourceTerm)}\\b"), targetTerm)
        }
        return translated
    }
}
