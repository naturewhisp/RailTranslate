package com.example.lora

/**
 * Corpus Tecnico Ferroviario Autorevole Multilingue
 * 
 * Basato su fonti ufficiali e normative europee:
 * - ERA (European Union Agency for Railways) - Specifiche Tecniche di Interoperabilità (STI / TSI):
 *     - TSI Loc&Pas (Regolamento UE 1302/2014) - Materiale rotabile locomotive e passeggeri
 *     - TSI CCS (Regolamento di esecuzione UE 2023/1695) - Controllo-comando e segnalamento (ERTMS/ETCS)
 *     - TSI WAG (Regolamento UE 321/2013) - Carri merci
 *     - TSI ENE (Regolamento UE 1299/2014) - Energia e catenaria
 *     - TSI INF (Regolamento UE 1299/2014) - Infrastruttura e binario
 * - Norme CENELEC / Comitato Europeo di Normazione (CEN):
 *     - EN 50126 / EN 50128 / EN 50129 (RAMS, Software e Sistemi Elettronici SIL 4)
 *     - EN 45545-2 (Protezione antincendio sui veicoli ferroviari)
 *     - EN 13103 / EN 13104 / EN 13260 (Assili, ruote e sale montate)
 *     - EN 14363 (Dinamica di marcia e prove di accettazione dei veicoli)
 * - UIC (Union Internationale des Chemins de fer):
 *     - Glossario multilingue RailLexic e schede UIC 540/541 (Frenatura)
 */

data class MultilingualTerm(
    val english: String,
    val italian: String,
    val german: String,
    val french: String,
    val spanish: String,
    val romanian: String,
    val bulgarian: String,
    val category: String,
    val standardRef: String,
    val falseFriendWarning: Map<String, String> = emptyMap()
) {
    fun getTranslation(langCode: String): String = when (langCode.lowercase()) {
        "en" -> english
        "it" -> italian
        "de" -> german
        "fr" -> french
        "es" -> spanish
        "ro" -> romanian
        "bg" -> bulgarian
        else -> english
    }
}

data class AuthoritativeNormativeStatement(
    val standard: String,
    val clause: String,
    val topic: String,
    val translations: Map<String, String>
)

object AuthoritativeRailwayCorpus {

    val TERMS: List<MultilingualTerm> = listOf(
        // --- 1. MATERIALE ROTABILE & MECCANICA (TSI Loc&Pas / EN 13103 / EN 13715) ---
        MultilingualTerm(
            english = "Wheelset",
            italian = "Sala montata",
            german = "Radsatz",
            french = "Essieu monté",
            spanish = "Eje montado",
            romanian = "Osie montată",
            bulgarian = "Колоос",
            category = "Meccanica & Rotabili",
            standardRef = "TSI Loc&Pas §4.2.3.5 / EN 13260",
            falseFriendWarning = mapOf(
                "it" to "Mai 'coppia di ruote' o 'set di ruote'.",
                "de" to "Mai 'Rädersatz'. Der bahntechnische Begriff ist 'Radsatz'.",
                "fr" to "Terme normatif 'essieu monté', jamais 'ensemble de roues'."
            )
        ),
        MultilingualTerm(
            english = "Axlebox",
            italian = "Boccola",
            german = "Achslager",
            french = "Boîte d'essieu",
            spanish = "Caja de grasa",
            romanian = "Cutie de osie",
            bulgarian = "Букса",
            category = "Meccanica & Rotabili",
            standardRef = "EN 12080 / TSI Loc&Pas §4.2.3.5.2",
            falseFriendWarning = mapOf(
                "it" to "Non tradurre 'scatola dell'asse': il termine tecnico è 'boccola'.",
                "es" to "En ferrocarriles se denomina 'caja de grasa', no 'caja de eje'."
            )
        ),
        MultilingualTerm(
            english = "Bogie",
            italian = "Carrello",
            german = "Drehgestell",
            french = "Bogie",
            spanish = "Bogie",
            romanian = "Boghiu",
            bulgarian = "Талига",
            category = "Meccanica & Rotabili",
            standardRef = "EN 15827 / TSI Loc&Pas §4.2.3.5",
            falseFriendWarning = mapOf(
                "it" to "In ambito rotabili è 'carrello' (della vettura o locomotiva)."
            )
        ),
        MultilingualTerm(
            english = "Primary suspension",
            italian = "Sospensione primaria",
            german = "Primärfederung",
            french = "Suspension primaire",
            spanish = "Suspensión primaria",
            romanian = "Suspensie primară",
            bulgarian = "Първично окачване",
            category = "Meccanica & Rotabili",
            standardRef = "EN 13913 / TSI Loc&Pas"
        ),
        MultilingualTerm(
            english = "Hunting damper",
            italian = "Smorzatore antiserpeggio",
            german = "Schlingerdämpfer",
            french = "Amortisseur antilacet",
            spanish = "Amortiguador antilazo",
            romanian = "Amortizor antiserpuire",
            bulgarian = "Амортисьор против лъкатушене",
            category = "Meccanica & Rotabili",
            standardRef = "EN 13802 / EN 14363"
        ),
        MultilingualTerm(
            english = "Automatic coupler",
            italian = "Accoppiatore automatico",
            german = "Automatische Mittelpufferkupplung",
            french = "Attelage automatique",
            spanish = "Enganche automático",
            romanian = "Cuplă automată",
            bulgarian = "Автоматичен сцепник",
            category = "Meccanica & Rotabili",
            standardRef = "TSI Loc&Pas §4.2.2.2.3 / EN 16019"
        ),
        MultilingualTerm(
            english = "Buffer",
            italian = "Respingente",
            german = "Puffer",
            french = "Tampon",
            spanish = "Tope",
            romanian = "Tampon",
            bulgarian = "Буфер",
            category = "Meccanica & Rotabili",
            standardRef = "EN 15551 / TSI WAG §4.2.2.1.2",
            falseFriendWarning = mapOf(
                "it" to "In meccanica rotabili si dice 'respingente', mai 'tampone' o 'buffer'."
            )
        ),

        // --- 2. TRAZIONE & CATENARIA (TSI ENE / EN 50163 / EN 50206) ---
        MultilingualTerm(
            english = "Pantograph",
            italian = "Pantografo",
            german = "Stromabnehmer",
            french = "Pantographe",
            spanish = "Pantógrafo",
            romanian = "Pantograf",
            bulgarian = "Пантограф",
            category = "Trazione Elettrica",
            standardRef = "TSI Loc&Pas §4.2.8.2.9 / EN 50206-1"
        ),
        MultilingualTerm(
            english = "Contact strip",
            italian = "Strisciante del pantografo",
            german = "Schleifleiste",
            french = "Bande de frottement",
            spanish = "Banda de frotamiento",
            romanian = "Bandă de frecare",
            bulgarian = "Плъзгач на пантографа",
            category = "Trazione Elettrica",
            standardRef = "EN 50408 / TSI ENE §4.2.14"
        ),
        MultilingualTerm(
            english = "Overhead contact line",
            italian = "Linea aerea di contatto",
            german = "Oberleitung",
            french = "Ligne aérienne de contact",
            spanish = "Línea aérea de contacto",
            romanian = "Linie de contact aeriană",
            bulgarian = "Контактна мрежа",
            category = "Infrastruttura Elettrica",
            standardRef = "TSI ENE §4.2.3 / EN 50119"
        ),
        MultilingualTerm(
            english = "Traction inverter",
            italian = "Inverter di trazione",
            german = "Traktionsstromrichter",
            french = "Onduleur de traction",
            spanish = "Ondulador de tracción",
            romanian = "Invertor de tracțiune",
            bulgarian = "Тягов инвертор",
            category = "Trazione Elettrica",
            standardRef = "EN 50207"
        ),

        // --- 3. SEGNALAMENTO, ERTMS/ETCS & INTERLOCKING (TSI CCS / EN 50128 / Subset-026) ---
        MultilingualTerm(
            english = "Interlocking",
            italian = "Apparato centrale",
            german = "Stellwerk",
            french = "Poste d'aiguillage",
            spanish = "Enclavamiento",
            romanian = "Instalație de centralizare",
            bulgarian = "Осигурителна инсталация",
            category = "Segnalamento & ERTMS",
            standardRef = "TSI CCS / EN 50128 SIL 4"
        ),
        MultilingualTerm(
            english = "Track circuit",
            italian = "Circuito di binario",
            german = "Gleisstromkreis",
            french = "Circuit de voie",
            spanish = "Circuito de vía",
            romanian = "Circuit de cale",
            bulgarian = "Релсова верига",
            category = "Segnalamento & ERTMS",
            standardRef = "EN 50125-3 / TSI CCS"
        ),
        MultilingualTerm(
            english = "Axle counter",
            italian = "Conta-assi",
            german = "Achszähler",
            french = "Compteur d'essieux",
            spanish = "Contador de ejes",
            romanian = "Numărător de osii",
            bulgarian = "Брояч на оси",
            category = "Segnalamento & ERTMS",
            standardRef = "TSI CCS §4.2.1 / EN 50617-2"
        ),
        MultilingualTerm(
            english = "Eurobalise",
            italian = "Eurobalise",
            german = "Eurobalise",
            french = "Eurobalise",
            spanish = "Eurobaliza",
            romanian = "Eurobaliză",
            bulgarian = "Евробализа",
            category = "Segnalamento & ERTMS",
            standardRef = "UNISIG Subset-036 / TSI CCS"
        ),
        MultilingualTerm(
            english = "Radio Block Centre",
            italian = "Centro di blocco radio",
            german = "Funkblockzentrale",
            french = "Centre Radio Bloc",
            spanish = "Centro de Bloqueo por Radio",
            romanian = "Centru de bloc radio",
            bulgarian = "Радио блок център",
            category = "Segnalamento & ERTMS",
            standardRef = "UNISIG Subset-026 §2.6 / TSI CCS"
        ),
        MultilingualTerm(
            english = "Movement Authority",
            italian = "Autorizzazione al movimento",
            german = "Fahrterlaubnis",
            french = "Autorisation de mouvement",
            spanish = "Autorización de movimiento",
            romanian = "Autorizație de mișcare",
            bulgarian = "Разрешение за движение",
            category = "Segnalamento & ERTMS",
            standardRef = "UNISIG Subset-026 §3.8 (ETCS Level 2)"
        ),
        MultilingualTerm(
            english = "Driver Machine Interface",
            italian = "Interfaccia uomo-macchina di bordo",
            german = "Führerraum-Anzeigegerät",
            french = "Interface homme-machine",
            spanish = "Interfaz hombre-máquina",
            romanian = "Interfață om-mașină",
            bulgarian = "Интерфейс човек-машина",
            category = "Segnalamento & ERTMS",
            standardRef = "ERA ERA_ERTMS_015560 (ETCS DMI)"
        ),
        MultilingualTerm(
            english = "Driver's vigilance device",
            italian = "Dispositivo vigilante",
            german = "Sicherheitsfahrschaltung",
            french = "Dispositif de veille automatique",
            spanish = "Dispositivo de hombre muerto",
            romanian = "Instalație de vigilență",
            bulgarian = "Устройство за бдителност",
            category = "Sicurezza & Condotta",
            standardRef = "TSI Loc&Pas §4.2.9.3.1 / UIC 641",
            falseFriendWarning = mapOf(
                "it" to "Mai 'interruttore dell'uomo morto'. Termine normativo 'vigilante' o 'VACMA'.",
                "de" to "Termine universale: 'Sifa' (Sicherheitsfahrschaltung)."
            )
        ),

        // --- 4. SISTEMI FRENANTI & PNEUMATICA (TSI Loc&Pas / UIC 540 / EN 14198) ---
        MultilingualTerm(
            english = "Compressed air brake",
            italian = "Freno continuo automatico ad aria compressa",
            german = "Druckluftbremse",
            french = "Frein à air comprimé",
            spanish = "Freno de aire comprimido",
            romanian = "Frână cu aer comprimat",
            bulgarian = "Автоматична въздушна спирачка",
            category = "Frenatura Ferroviaria",
            standardRef = "TSI Loc&Pas §4.2.4 / UIC 540"
        ),
        MultilingualTerm(
            english = "Brake distributor valve",
            italian = "Distributore del freno",
            german = "Steuerventil",
            french = "Distributeur de frein",
            spanish = "Distribuidor de freno",
            romanian = "Distribuitor de frână",
            bulgarian = "Въздухоразпределител",
            category = "Frenatura Ferroviaria",
            standardRef = "UIC 541-1 / EN 15355"
        ),
        MultilingualTerm(
            english = "Wheel slide protection",
            italian = "Dispositivo antislittamento",
            german = "Gleitschutz",
            french = "Dispositif d'anti-enrayage",
            spanish = "Sistema antibloqueo",
            romanian = "Dispozitiv antipatinare",
            bulgarian = "Противобуксуващо устройство",
            category = "Frenatura Ferroviaria",
            standardRef = "EN 15595 / TSI Loc&Pas §4.2.4.6.2"
        ),
        MultilingualTerm(
            english = "Emergency braking distance",
            italian = "Spazio di frenatura d'emergenza",
            german = "Notbremsweg",
            french = "Distance de freinage d'urgence",
            spanish = "Distancia de frenado de emergencia",
            romanian = "Distanță de frânare de urgență",
            bulgarian = "Път на екстремно спиране",
            category = "Frenatura Ferroviaria",
            standardRef = "EN 14531-1 / TSI Loc&Pas §4.2.4.5"
        ),

        // --- 5. INFRASTRUTTURA & ARMAMENTO (TSI INF / EN 13848) ---
        MultilingualTerm(
            english = "Track gauge",
            italian = "Scartamento del binario",
            german = "Spurweite",
            french = "Écartement de la voie",
            spanish = "Ancho de vía",
            romanian = "Ecartament al căii",
            bulgarian = "Междурелсие",
            category = "Infrastruttura Ferroviaria",
            standardRef = "TSI INF §4.2.4.1 (Standard UIC 1435 mm)"
        ),
        MultilingualTerm(
            english = "Turnout",
            italian = "Deviatoio",
            german = "Weiche",
            french = "Appareil de voie",
            spanish = "Desvío",
            romanian = "Schimbător de cale",
            bulgarian = "Стрелка",
            category = "Infrastruttura Ferroviaria",
            standardRef = "EN 13232 / TSI INF §4.2.8.6",
            falseFriendWarning = mapOf(
                "it" to "Il termine ingegneristico è 'deviatoio', comunemente detto 'scambio'."
            )
        ),
        MultilingualTerm(
            english = "Cant deficiency",
            italian = "Insufficienza di sopraelevazione",
            german = "Überhöhungsfehlbetrag",
            french = "Insuffisance de dévers",
            spanish = "Insuficiencia de peralte",
            romanian = "Insuficiență de supraînălțare",
            bulgarian = "Недостиг на надвишение",
            category = "Infrastruttura Ferroviaria",
            standardRef = "EN 13803 / TSI INF §4.2.4.3"
        ),

        // --- 6. SICUREZZA, RAMS & ANTINCENDIO (EN 50126 / EN 45545) ---
        MultilingualTerm(
            english = "Safety Integrity Level",
            italian = "Livello di integrità della sicurezza",
            german = "Sicherheitsanforderungsstufe",
            french = "Niveau d'intégrité de sécurité",
            spanish = "Nivel de integridad de la seguridad",
            romanian = "Nivel de integritate a siguranței",
            bulgarian = "Ниво на сигурност и цялост",
            category = "RAMS & Certificazione",
            standardRef = "EN 50126 / EN 50128 / EN 50129 (SIL 1 - SIL 4)"
        ),
        MultilingualTerm(
            english = "Fire barrier",
            italian = "Barriera tagliafuoco",
            german = "Brandschutzbarriere",
            french = "Cloison coupe-feu",
            spanish = "Barrera cortafuegos",
            romanian = "Barieră antifoc",
            bulgarian = "Огнезащитна преграда",
            category = "Sicurezza Antincendio",
            standardRef = "EN 45545-2 / EN 45545-3 (E15 / E30)"
        )
    )

    /**
     * Enunciati normativi autentici allineati estratti dai testi ufficiali delle TSI europee
     */
    val STATEMENTS: List<AuthoritativeNormativeStatement> = listOf(
        AuthoritativeNormativeStatement(
            standard = "TSI Loc&Pas (Regolamento UE 1302/2014)",
            clause = "§4.2.3.5.2.2 - Integrità strutturale dell'assile",
            topic = "Assili e Sale Montate",
            translations = mapOf(
                "en" to "The mechanical characteristics of the axles shall guarantee the transmission of forces and torques according to the design criteria defined in EN 13103 and EN 13104.",
                "it" to "Le caratteristiche meccaniche degli assili devono garantire la trasmissione di forze e coppie secondo i criteri di dimensionamento definiti nelle norme EN 13103 e EN 13104.",
                "de" to "Die mechanischen Eigenschaften der Radsatzwellen müssen die Übertragung von Kräften und Drehmomenten gemäß den in EN 13103 und EN 13104 festgelegten Auslegungskriterien gewährleisten.",
                "fr" to "Les caractéristiques mécaniques des essieux-axes doivent garantir la transmission des forces et des couples conformément aux critères de dimensionnement définis dans l'EN 13103 et l'EN 13104.",
                "es" to "Las características mecánicas de los ejes deben garantizar la transmisión de fuerzas y pares de acuerdo con los criterios de diseño definidos en las normas EN 13103 y EN 13104.",
                "ro" to "Caracteristicile mecanice ale osiilor trebuie să garanteze transmiterea forțelor și cuplurilor conform criteriilor de proiectare definite în EN 13103 și EN 13104.",
                "bg" to "Механичните характеристики на осите трябва да гарантират предаването на сили и въртящи моменти в съответствие с критериите за проектиране, определени в EN 13103 и EN 13104."
            )
        ),
        AuthoritativeNormativeStatement(
            standard = "TSI CCS (Regolamento UE 2023/1695)",
            clause = "§4.2.2 - Trasmissione Balise e Supervisione ETCS",
            topic = "Controllo Marcia Treno",
            translations = mapOf(
                "en" to "The on-board ETCS subsystem shall receive Eurobalise telegrams via the Balise Transmission Module and compute the emergency braking supervision curve.",
                "it" to "Il sottosistema di bordo ETCS deve ricevere i telegrammi Eurobalise mediante il modulo BTM e calcolare la curva di supervisione della frenatura d'emergenza.",
                "de" to "Das fahrzeugseitige ETCS-Untersystem muss Eurobalisen-Telegramme über das Balise Transmission Module empfangen und die Notbrems-Überwachungskurve berechnen.",
                "fr" to "Le sous-système bord ETCS doit recevoir les télégrammes Eurobalise via le module de transmission balise et calculer la courbe de contrôle du freinage d'urgence.",
                "es" to "El subsistema de a bordo ETCS deberá recibir los telegramas de las eurobalizas a través del módulo BTM y calcular la curva de supervisión del frenado de emergencia.",
                "ro" to "Subsistemul de bord ETCS trebuie să primească telegramele Eurobalizei prin intermediul modulului BTM și să calculeze curba de supraveghere a frânării de urgență.",
                "bg" to "Бордовата подсистема ETCS приема телеграми от евробализите чрез BTM модула и изчислява кривата за контрол на екстремното спиране."
            )
        ),
        AuthoritativeNormativeStatement(
            standard = "TSI Loc&Pas (Regolamento UE 1302/2014)",
            clause = "§4.2.4.6.2 - Protezione antislittamento ruote",
            topic = "Frenatura & Aderenza",
            translations = mapOf(
                "en" to "The wheel slide protection system shall prevent wheel locking during braking under reduced rail-wheel adhesion conditions in accordance with EN 15595.",
                "it" to "Il dispositivo antislittamento deve prevenire il bloccaggio delle ruote durante la frenatura in condizioni di aderenza ridotta secondo la norma EN 15595.",
                "de" to "Die Gleitschutzanlage muss das Blockieren der Räder beim Bremsen unter verminderten Kraftschlussbedingungen gemäß EN 15595 verhindern.",
                "fr" to "Le système anti-enrayeur doit empêcher le blocage des roues lors du freinage dans des conditions d'adhérence dégradée conformément à l'EN 15595.",
                "es" to "El sistema antibloqueo deberá impedir el bloqueo de las ruedas durante el frenado en condiciones de adherencia reducida de conformidad con la norma EN 15595.",
                "ro" to "Sistemul antipatinare trebuie să prevină blocarea roților în timpul frânării în condiții de aderență redusă, în conformitate cu EN 15595.",
                "bg" to "Противобуксуващата система трябва да предотвратява блокирането на колелата при спиране в условия на намалено сцепление в съответствие с EN 15595."
            )
        ),
        AuthoritativeNormativeStatement(
            standard = "EN 50126-1:2017",
            clause = "§6.4 - Specifiche RAMS e Gestione dei Rischi",
            topic = "Affidabilità & Sicurezza Ferroviaria",
            translations = mapOf(
                "en" to "The railway duty holder shall define the safety integrity requirements and demonstrate that catastrophic hazards meet the tolerable hazard rate under CSM-RA.",
                "it" to "L'esercente ferroviario deve definire i requisiti di integrità della sicurezza e dimostrare che i pericoli catastrofici rispettano il tasso di rischio tollerabile secondo il Regolamento CSM-RA.",
                "de" to "Der Eisenbahnbetreiber muss die Sicherheitsanforderungen festlegen und nachweisen, dass katastrophale Gefährdungen die tolerierbare Gefährdungsrate gemäß CSM-RA einhalten.",
                "fr" to "L'exploitant ferroviaire doit définir les exigences d'intégrité de sécurité et démontrer que les dangers catastrophiques respectent le taux de risque tolérable selon le CSM-RA.",
                "es" to "El agente ferroviario deberá definir los requisitos de integridad de la seguridad y demostrar que los peligros catastróficos cumplen la tasa de peligro tolerable según el CSM-RA.",
                "ro" to "Operatorul feroviar trebuie să definească cerințele de integritate a siguranței și să demonstreze că pericolele catastrofale respectă rata de risc tolerabilă conform CSM-RA.",
                "bg" to "Железопътният оператор определя изискванията за ниво на безопасност и доказва, че катастрофалните опасности съответстват на допустимото ниво на риск съгласно CSM-RA."
            )
        )
    )
}
