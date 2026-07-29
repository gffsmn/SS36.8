/**
 * SS36 AI Monitor - Backend Server Data Scraper & Aggregator
 * 
 * Requisiti Node.js:
 * npm install express axios cors dotenv @google/generative-ai
 * 
 * Avvio:
 * node backend_server.js
 */

const express = require('express');
const axios = require('axios');
const cors = require('cors');
const { GoogleGenerativeAI } = require('@google/generative-ai');
require('dotenv').config();

const app = express();
app.use(cors());

const PORT = process.env.PORT || 3000;

// Configurazione Google Gemini API
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);

// Fonti dati fittizie per l'esempio (nella realtà andrebbero estratti tramite scraping RSS/HTML/API)
const SOURCES = {
    istituzionali: [
        "Feed ANAS", 
        "CCISS Viaggiare Informati", 
        "ACI Luceverde Milano",
        "Albo Pretorio Comune Lecco",
        "Albo Pretorio Comune Monza",
        "Polizia di Stato (Autovelox)"
    ],
    locali: [
        "LeccoNotizie.com",
        "La Provincia di Lecco",
        "La Provincia di Sondrio",
        "Valsassinanews.com",
        "Valtellinanews.it",
        "MonzaToday",
        "ResegoneOnline.it"
    ],
    social: [
        "Gruppo Facebook Sei di Lecco se...",
        "Hashtag #SS36 Twitter/X",
        "Canali Telegram viabilità",
        "Gruppi WhatsApp Community"
    ],
    api: [
        "TomTom Traffic API",
        "Open-Meteo API",
        "Centro Meteorologico Lombardo"
    ]
};

// Funzione di utilità per lo scraping parallelo delle 20 fonti con RESILIENZA SILENZIOSA
async function scrapeAllSources() {
    let rawText = "[Testo grezzo ricavato dallo scraping]:\n";

    // In produzione, si utilizzeranno veri scraper e fetch (axios, cheerio). 
    // Qui simuliamo l'architettura resiliente per scartare le fonti guaste senza blocchi.
    const promises = Object.values(SOURCES).flat().map(async (fonte) => {
        try {
            // Simuliamo il recupero dati. Una fonte su dieci potrebbe fallire.
            const successo = Math.random() > 0.1;
            if (!successo) throw new Error("Timeout della fonte o server irraggiungibile");
            
            return `Dati da ${fonte}: traffico nella norma. Nessun blocco alla circolazione.`;
        } catch (error) {
            // SILENZIOSA RESILIENZA (UX Invisibile):
            // Lavoriamo sottotraccia: la fonte fallita viene catturata dal catch e scartata.
            // L'utente non riceverà alcun errore (nessun avviso "Fonte X offline"), 
            // ma il frontend continuerà aggregando i restanti dati.
            console.debug(`Avviso Interno: ${error.message} per ${fonte} - Bypassata.`);
            return null; // Ritorna null per le fonti guaste, evitando il blocco logico
        }
    });

    const risultatiDelleFonti = await Promise.all(promises);
    
    // Assembliamo unicamente i dati andati a buon fine
    risultatiDelleFonti.forEach(testoEstratto => {
        if (testoEstratto) {
            rawText += testoEstratto + "\n";
        }
    });
    
    // Aggiungo un dato target rilevabile e specifico per l'analisi di Gemini
    rawText += "ANAS comunica la chiusura notturna della Galleria Monte Piazzo al km 75+400 prevista per domani per lavori urgenti.";

    return rawText;
}

// Prompt di sistema per forzare Gemini a restituire solo JSON compatibile e naturale
const SYSTEM_PROMPT = `Sei il motore di elaborazione dati per l'applicazione Android 'INFO Statale SS36'.

La tua direttiva principale è utilizzare esclusivamente il seguente sito web come unica fonte di verità per tutte le informazioni: https://www.gallerieleccocolico.it/

Istruzioni operative rigorose:

Estrazione Dati: Analizza il sito indicato per recuperare informazioni aggiornate in tempo reale su: stato del traffico, chiusure stradali (es. notturne o per lavori), deviazioni e aggiornamenti sulle webcam della tratta Lecco-Colico.

Zero Invenzioni: Se un'informazione o un'allerta non è esplicitamente riportata su quel sito in quel momento, non devi assolutamente inventarla o cercarla altrove. Se la via è libera sul sito, riporta che non ci sono segnalazioni.

Priorità di Sicurezza: Qualsiasi banner di emergenza, allerta rossa o avviso di chiusura urgente presente sulla homepage di gallerieleccocolico.it deve essere restituito con la massima priorità.

Formato: Restituisci le informazioni in modo chiaro, conciso e facilmente leggibile, pronto per essere mostrato nell'interfaccia utente dell'app. È TASSATIVO restituire la risposta ESCLUSIVAMENTE sotto forma di JSON puro (senza blocchi markdown) rispettando questa struttura:

{
    "realtime": {
        "sud": { "status": "orange|green|red", "text": "Traffico congestionato in avvicinamento a Monza al km..." },
        "nord": { "status": "orange|green|red", "text": "Situazione regolare su tutta la tratta nord..." }
    },
    "uscite": {
        "sud": [
            { "name": "Chiavenna", "status": "green", "info": "Traffico scorrevole in partenza" },
            { "name": "Milano", "status": "green", "info": "Traffico in smaltimento sull'immissione in Viale Zara" }
        ],
        "nord": [
             // Ordine inverso
        ]
    },
    "predictions": [
        { "giorniDaOggi": 1, "isCritica": true, "desc": "Avviso o chiusura, altrimenti isCritica false" }
    ]
}`;

// Cache interna del server (per non colpire l'API Gemini ad ogni richiesta utente, es. reset ogni 5 min)
let cachedTrafficData = null;
let lastUpdateTimestamp = 0;
const CACHE_TTL = 5 * 60 * 1000; // 5 minuti

// Endpoint GET /api/ss36 
app.get('/api/ss36', async (req, res) => {
    try {
        const now = Date.now();
        if (cachedTrafficData && (now - lastUpdateTimestamp < CACHE_TTL)) {
            console.log("Serving cached data...");
            return res.json(cachedTrafficData);
        }

        console.log("Variabili scadute, avvio scraping delle 20 fonti...");
        const rawText = await scrapeAllSources();

        console.log("Invocazione Google Gemini API...");
        const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });
        const result = await model.generateContent(`${SYSTEM_PROMPT}\n\nTESTO GREZZO:\n${rawText}`);
        const responseText = result.response.text();
        
        // Pulizia eventuale del JSON restituito da Gemini in caso producesse markdown indesiderato
        const cleanJSON = responseText.replace(/```json/g, "").replace(/```/g, "").trim();
        const parsedData = JSON.parse(cleanJSON);

        cachedTrafficData = parsedData;
        lastUpdateTimestamp = now;

        res.json(parsedData);
    } catch (error) {
        console.error("Errore durante il recupero dei dati o parsing AI:", error);
        
        // Fallback robusto
        if (cachedTrafficData) {
            res.json(cachedTrafficData);
        } else {
            res.status(500).json({ error: "Errore interno server, dati non disponibili" });
        }
    }
});

app.listen(PORT, () => {
    console.log(`SS36 AI Monitor Backend in esecuzione sulla porta ${PORT}`);
});
