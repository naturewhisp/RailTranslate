package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class DictionaryRepository(private val dictionaryDao: DictionaryDao) {
    val allTerms: Flow<List<DictionaryTerm>> = dictionaryDao.getAllTerms()

    suspend fun insert(term: DictionaryTerm) {
        dictionaryDao.insertTerm(term)
    }

    suspend fun delete(id: Int) {
        dictionaryDao.deleteTermById(id)
    }
    
    suspend fun initDefaultTerms() {
        val initialList = listOf(
            DictionaryTerm(term = "Wheelset", translation = "Sala montata", category = "Meccanica"),
            DictionaryTerm(term = "Axle box", translation = "Boccola", category = "Meccanica"),
            DictionaryTerm(term = "Bogie", translation = "Carrello", category = "Meccanica"),
            DictionaryTerm(term = "Pantograph", translation = "Pantografo", category = "Trazione Elettrica"),
            DictionaryTerm(term = "Contact strip", translation = "Strisciante", category = "Trazione Elettrica"),
            DictionaryTerm(term = "Catenary", translation = "Catenaria", category = "Infrastruttura"),
            DictionaryTerm(term = "Traction Motor", translation = "Motore di trazione", category = "Trazione Elettrica"),
            DictionaryTerm(term = "Air Brake", translation = "Freno pneumatico", category = "Frenatura"),
            DictionaryTerm(term = "Disc Brake", translation = "Freno a disco", category = "Frenatura"),
            DictionaryTerm(term = "Dynamic Brake", translation = "Freno dinamico", category = "Frenatura"),
            DictionaryTerm(term = "Coupler", translation = "Accoppiatore", category = "Meccanica"),
            DictionaryTerm(term = "Gauge", translation = "Scartamento", category = "Infrastruttura"),
            DictionaryTerm(term = "Hunting damper", translation = "Smorzatore antiserpeggio", category = "Sospensioni"),
            DictionaryTerm(term = "Interlocking", translation = "Apparato centrale", category = "Segnalamento"),
            DictionaryTerm(term = "Cab signalling", translation = "Ripetizione segnali a bordo", category = "Segnalamento")
        )

        if (dictionaryDao.getCount() == 0) {
            initialList.forEach { dictionaryDao.insertTerm(it) }
        } else {
            val currentTerms = dictionaryDao.getAllTerms().first()
            for (defaultTerm in initialList) {
                if (currentTerms.none { it.term.equals(defaultTerm.term, ignoreCase = true) }) {
                    dictionaryDao.insertTerm(defaultTerm)
                }
            }
        }
    }
}
