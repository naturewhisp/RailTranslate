package com.example.lora

import com.example.data.DictionaryDao
import com.example.data.DictionaryTerm
import com.example.data.HistoryDao
import com.example.data.TranslationHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDictionaryDao : DictionaryDao {
    private val terms = mutableListOf(
        DictionaryTerm(id = 1, term = "Wheelset", translation = "Sala montata", category = "Materiale Rotabile"),
        DictionaryTerm(id = 2, term = "Axlebox", translation = "Boccola", category = "Materiale Rotabile"),
        DictionaryTerm(id = 3, term = "Bogie", translation = "Carrello", category = "Materiale Rotabile")
    )

    override fun getAllTerms(): Flow<List<DictionaryTerm>> = flowOf(terms)
    override suspend fun getAllTermsSync(): List<DictionaryTerm> = terms
    override suspend fun insertTerm(term: DictionaryTerm) { terms.add(term) }
    override suspend fun deleteTermById(id: Int) { terms.removeIf { it.id == id } }
    override suspend fun getCount(): Int = terms.size
}

class FakeHistoryDao : HistoryDao {
    private val history = mutableListOf(
        TranslationHistory(
            id = 1,
            sourceText = "The train is approaching the signal at danger.",
            translatedText = "Il treno si sta avvicinando al segnale a via impedita.",
            sourceLang = "en",
            targetLang = "it"
        )
    )

    override fun getAllHistory(): Flow<List<TranslationHistory>> = flowOf(history)
    override suspend fun getAllHistorySync(): List<TranslationHistory> = history
    override suspend fun insert(history: TranslationHistory) { this.history.add(history) }
    override suspend fun clearHistory() { history.clear() }
    override suspend fun getCount(): Int = history.size
}

class LoraTrainingDataManagerTest {

    @Test
    fun testDatasetGenerationAndJsonlSerialization() = runBlocking {
        val fakeDictionary = FakeDictionaryDao()
        val fakeHistory = FakeHistoryDao()
        val manager = LoraTrainingDataManager(fakeDictionary, fakeHistory)

        val config = LoraExportConfig(
            includeDirectTerms = true,
            includeReverseTerms = true,
            includeSyntheticSentences = true,
            includeHardNegatives = true,
            includeValidatedHistory = true,
            format = LoraDatasetFormat.MESSAGES_JSONL
        )

        val samples = manager.generateDataset(config)
        assertTrue("Dovrebbero essere generati campioni di addestramento", samples.isNotEmpty())

        val jsonl = manager.convertToJsonl(samples, LoraDatasetFormat.MESSAGES_JSONL)
        val lines = jsonl.trim().split("\n")
        assertEquals(samples.size, lines.size)

        // Verifica formato messages per Hugging Face SFTTrainer
        val firstLine = lines.first()
        assertTrue(firstLine.contains("\"role\":\"user\""))
        assertTrue(firstLine.contains("\"role\":\"model\""))
        assertTrue(firstLine.contains("messages"))

        // Verifica formato Gemma raw
        val gemmaJsonl = manager.convertToJsonl(samples.take(1), LoraDatasetFormat.GEMMA_RAW_JSONL)
        assertTrue(gemmaJsonl.contains("<start_of_turn>user"))
        assertTrue(gemmaJsonl.contains("<end_of_turn>"))
        assertTrue(gemmaJsonl.contains("<start_of_turn>model"))

        // Verifica calcolo statistiche
        val stats = manager.getDatasetStatistics()
        assertEquals(3, stats.totalTermsInGlossary)
        assertEquals(1, stats.totalHistoryRecords)
        assertTrue(stats.totalAuthoritativeTerms >= 20)
        assertTrue(stats.totalAuthoritativeStatements >= 4)
        assertTrue(stats.estimatedGeneratedSamples > 30)
    }

    @Test
    fun testMultilingualAuthoritativeCorpusAcrossAllLanguages() = runBlocking {
        val fakeDictionary = FakeDictionaryDao()
        val fakeHistory = FakeHistoryDao()
        val manager = LoraTrainingDataManager(fakeDictionary, fakeHistory)

        // Configurazione per tutte le 7 lingue europee
        val config = LoraExportConfig(
            allLanguagePairs = true,
            includeAuthoritativeTerms = true,
            includeAuthoritativeStatements = true,
            includeDirectTerms = false,
            includeReverseTerms = false,
            includeSyntheticSentences = false,
            includeHardNegatives = false,
            includeValidatedHistory = false,
            format = LoraDatasetFormat.MESSAGES_JSONL
        )

        val samples = manager.generateDataset(config)
        assertTrue("Devono essere generati centinaia di campioni multilingue", samples.size >= 500)

        // Verifichiamo che siano presenti campioni per le diverse lingue europee
        val hasGerman = samples.any { it.instruction.contains("Tedesco") }
        val hasFrench = samples.any { it.instruction.contains("Francese") }
        val hasSpanish = samples.any { it.instruction.contains("Spagnolo") }
        val hasRomanian = samples.any { it.instruction.contains("Rumeno") }
        val hasBulgarian = samples.any { it.instruction.contains("Bulgaro") }

        assertTrue(hasGerman)
        assertTrue(hasFrench)
        assertTrue(hasSpanish)
        assertTrue(hasRomanian)
        assertTrue(hasBulgarian)
    }
}
