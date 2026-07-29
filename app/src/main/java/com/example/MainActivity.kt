package com.example

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var webViewRef by remember { mutableStateOf<WebView?>(null) }
            
            // Gestione nativa del tasto Back per la WebView Android
            BackHandler(enabled = true) {
                if (webViewRef?.canGoBack() == true) {
                    webViewRef?.goBack()
                } else {
                    finish()
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    color = Color.Black // Evita flash bianchi dietro la WebView
                ) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                
                                // Intercetta i link esterni (LinkedIn, PayPal) e li apre nel browser di sistema
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                                        if (request?.url.toString().contains("tuo-server.com/api/ss36")) {
                                            if (request?.method?.uppercase() == "OPTIONS") {
                                                val emptyStream = java.io.ByteArrayInputStream(ByteArray(0))
                                                val response = android.webkit.WebResourceResponse("text/plain", "UTF-8", emptyStream)
                                                response.responseHeaders = mapOf(
                                                    "Access-Control-Allow-Origin" to "*",
                                                    "Access-Control-Allow-Methods" to "GET, OPTIONS",
                                                    "Access-Control-Allow-Headers" to "Content-Type"
                                                )
                                                return response
                                            }

                                            return kotlinx.coroutines.runBlocking {
                                                val jsonResult = aggiornaDatiViabilita()
                                                val inputStream = java.io.ByteArrayInputStream(jsonResult.toByteArray(Charsets.UTF_8))
                                                val response = android.webkit.WebResourceResponse("application/json", "UTF-8", inputStream)
                                                response.responseHeaders = mapOf(
                                                    "Access-Control-Allow-Origin" to "*",
                                                    "Access-Control-Allow-Methods" to "GET, OPTIONS",
                                                    "Access-Control-Allow-Headers" to "Content-Type"
                                                )
                                                response
                                            }
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url.toString()
                                        if (url.startsWith("http://") || url.startsWith("https://")) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            return true // Link gestito dal browser nativo, la webview non cambia pagina
                                        }
                                        return false
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                loadUrl("file:///android_asset/index.html")
                                webViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    suspend fun aggiornaDatiViabilita(): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d("TEST_SS36", "Inizio download dai siti...")
                val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                
                var testoGrezzoSito = "FONTE PRIMARIA (Tempo reale):\n"
                try {
                    val doc1 = org.jsoup.Jsoup.connect("https://www.gallerieleccocolico.it/").userAgent(userAgent).get()
                    testoGrezzoSito += doc1.text() + "\n\n"
                } catch (e: Exception) {
                    testoGrezzoSito += "Non disponibile.\n\n"
                }

                testoGrezzoSito += "FONTE SECONDARIA (Lavori programmati):\n"
                try {
                    val doc2 = org.jsoup.Jsoup.connect("https://www.provincia.lecco.it/documento/anas-ss-36-date-e-orari-chiusure-al-traffico/").userAgent(userAgent).get()
                    testoGrezzoSito += doc2.text() + "\n\n"
                } catch (e: Exception) {
                    testoGrezzoSito += "Non disponibile.\n\n"
                }

                testoGrezzoSito += "FONTE TERZIARIA (Comunicati Anas):\n"
                try {
                    val doc3 = org.jsoup.Jsoup.connect("https://www.stradeanas.it/it/ss36-del-lago-di-como-dello-spluga").userAgent(userAgent).get()
                    testoGrezzoSito += doc3.text() + "\n\n"
                } catch (e: Exception) {
                    testoGrezzoSito += "Non disponibile.\n\n"
                }
                
                Log.d("TEST_SS36", "Testo scaricato con successo. Lunghezza: ${testoGrezzoSito.length} caratteri")

                val generativeModel = com.google.ai.client.generativeai.GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = com.example.BuildConfig.GEMINI_API_KEY,
                    systemInstruction = com.google.ai.client.generativeai.type.content {
                        text("""agisci come il miglior programmatore al mondo, il motore della app ha tuo compito è analizzare, incrociare e riassumere le informazioni sulla viabilità in modo rigoroso, comportandoti come un sistema di allerta per la sicurezza stradale al 100% affidabile.

GERARCHIA DELLE FONTI AUTORIZZATE (Utilizza ESCLUSIVAMENTE queste):
1. FONTE PRIMARIA (Tempo reale e urgenze): [https://www.gallerieleccocolico.it/](https://www.gallerieleccocolico.it/) -> Usa questa fonte come verità assoluta per lo stato attuale del traffico, i semafori delle gallerie e le emergenze istantanee.
2. FONTE SECONDARIA (Lavori e chiusure programmate): [https://www.provincia.lecco.it/documento/anas-ss-36-date-e-orari-chiusure-al-traffico/](https://www.provincia.lecco.it/documento/anas-ss-36-date-e-orari-chiusure-al-traffico/) -> Usa questa fonte per estrarre con precisione millimetrica le date, gli orari notturni e le tratte esatte chiuse per manutenzione ANAS.
3. FONTE TERZIARIA (Comunicati ufficiali generali): [https://www.stradeanas.it/it/ss36-del-lago-di-como-dello-spluga](https://www.stradeanas.it/it/ss36-del-lago-di-como-dello-spluga) -> Usa questa fonte per conferme incrociate o deviazioni a lungo termine.

LOGICA DI ELABORAZIONE E REGOLE RIGOROSE:

Zero Invenzioni: Estrai solo dati esplicitamente presenti nei testi forniti. Se nessuna fonte riporta problemi, dichiara che non ci sono segnalazioni e la viabilità è regolare.

Risoluzione dei conflitti: Se le fonti sembrano discordare, la Fonte 1 vince per tutto ciò che accade "ora", mentre la Fonte 2 vince per le "date future" dei cantieri.

Priorità Visiva: Qualsiasi allerta emergenziale, chiusura imminente o incidente deve avere la massima priorità nel riassunto.

Formato Output: Restituisci la risposta unicamente nel formato JSON richiesto dall'app, strutturando in modo netto i dati in: traffico_live, chiusure_programmate e allerte. È TASSATIVO restituire la risposta ESCLUSIVAMENTE sotto forma di JSON puro (senza blocchi markdown) rispettando questa struttura:
{
    "realtime": {
        "sud": { "status": "orange|green|red", "text": "Traffico congestionato in avvicinamento a Monza al km..." },
        "nord": { "status": "green", "text": "Traffico regolare." }
    },
    "uscite": {
        "sud": [
            { "name": "Chiavenna", "status": "green", "info": "Regolare" },
            { "name": "Colico", "status": "green", "info": "Regolare" },
            { "name": "Bellano", "status": "green", "info": "Regolare" },
            { "name": "Lecco", "status": "green", "info": "Regolare" },
            { "name": "Civate", "status": "green", "info": "Regolare" },
            { "name": "Giussano", "status": "green", "info": "Regolare" },
            { "name": "Seregno", "status": "green", "info": "Regolare" },
            { "name": "Monza", "status": "green", "info": "Regolare" },
            { "name": "Milano (Viale Zara)", "status": "green", "info": "Regolare" }
        ],
        "nord": [
            { "name": "Milano (Viale Zara)", "status": "green", "info": "Regolare" },
            { "name": "Monza", "status": "green", "info": "Regolare" },
            { "name": "Seregno", "status": "green", "info": "Regolare" },
            { "name": "Giussano", "status": "green", "info": "Regolare" },
            { "name": "Civate", "status": "green", "info": "Regolare" },
            { "name": "Lecco", "status": "green", "info": "Regolare" },
            { "name": "Bellano", "status": "green", "info": "Regolare" },
            { "name": "Colico", "status": "green", "info": "Regolare" },
            { "name": "Chiavenna", "status": "green", "info": "Regolare" }
        ]
    },
    "predictions": [
        { "giorniDaOggi": 1, "isCritica": true, "desc": "Avviso o chiusura, altrimenti isCritica false" }
    ]
}""")
                    }
                )

                val rispostaIA = generativeModel.generateContent(testoGrezzoSito)
                val text = rispostaIA.text ?: "{}"
                
                Log.d("TEST_SS36", "Risposta ricevuta da Gemini: $text")
                
                return@withContext text.replace("```json", "").replace("```", "").trim()
            } catch (e: Exception) {
                Log.e("TEST_SS36", "Errore critico: ${e.message}")
                e.printStackTrace()
                return@withContext "{}"
            }
        }
    }
}
