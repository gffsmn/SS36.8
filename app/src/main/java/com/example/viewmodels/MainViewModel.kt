package com.example.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.models.EventType
import com.example.models.HighwaySegment
import com.example.models.PredictionEvent
import com.example.models.RouteDetails
import com.example.models.RouteDirection
import com.example.models.RouteSummary
import com.example.models.StatusLevel
import com.example.models.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class AppState(
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastUpdated: String = "--:--",
    val summaries: List<RouteSummary> = emptyList(),
    val southRoute: RouteDetails? = null,
    val northRoute: RouteDetails? = null,
    val predictions: List<PredictionEvent> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.AUTO
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        loadCachedData()
        startRealTimeUpdates()
    }

    fun setTheme(mode: ThemeMode) {
        _state.update { it.copy(themeMode = mode) }
    }
    
    fun forceRefresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            delay(1500)
            _state.update { it.copy(isRefreshing = false, lastUpdated = getCurrentTime()) }
        }
    }

    private fun getFormattedDate(offsetDays: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, offsetDays)
        // Correct date format for Italian locale showing long Day Month string. e.g. "Lunedì 24 Maggio"
        val formatter = SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN)
        return formatter.format(calendar.time).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }
    }
    
    private fun getCurrentTime(): String {
        val calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("HH:mm", Locale.ITALIAN)
        return formatter.format(calendar.time)
    }

    private fun loadCachedData() {
        val exitsSouth = listOf("Chiavenna", "Colico", "Bellano", "Lecco", "Civate", "Giussano", "Seregno", "Monza", "Milano")
        val exitsNorth = exitsSouth.reversed()

        val mockSegmentsSouth = exitsSouth.map { exit ->
            when (exit) {
                "Bellano" -> HighwaySegment(exit, EventType.WORK, "Lavori notturni (22:00-05:00) | Fonte: Ordinanza ANAS")
                "Lecco" -> HighwaySegment(exit, EventType.TRAFFIC, "Code a tratti verso Milano | Segnalazione: LeccoNotizie")
                "Giussano" -> HighwaySegment(exit, EventType.TRAFFIC, "Cantiere mobile in corsia destra | Fonte: CCISS")
                "Monza" -> HighwaySegment(exit, EventType.ACCIDENT, "Incidente al km 12 | Segnalazione: Gruppo Telegram Viabilità SS36")
                else -> HighwaySegment(exit, EventType.CLEAR, "Strada libera")
            }
        }

        val mockSegmentsNorth = exitsNorth.map { exit ->
            when (exit) {
                "Monza" -> HighwaySegment(exit, EventType.ACCIDENT, "Incidente in corsia di sorpasso | Fonte: CCISS")
                "Civate" -> HighwaySegment(exit, EventType.TRAFFIC, "Traffico intenso per rientri | Segnalazione: X/Twitter")
                "Colico" -> HighwaySegment(exit, EventType.WORK, "Chiusura svincolo per lavori programmati | Fonte: ANAS")
                else -> HighwaySegment(exit, EventType.CLEAR, "Strada libera")
            }
        }

        val summaries = listOf(
            RouteSummary(RouteDirection.SUD, "Direzione Sud", StatusLevel.RED, "Incidente (Monza) e code a tratti (Lecco)"),
            RouteSummary(RouteDirection.NORD, "Direzione Nord", StatusLevel.YELLOW, "Rallentamenti e cantiere (Colico)")
        )
        
        val predictionsList = listOf(
            PredictionEvent(1, getFormattedDate(1), "Chiusura Galleria Monte Piazzo", "Ordinanza ANAS 24/2026", "Chiusura notturna (22:00 - 05:00) per manutenzione impianti. Deviazioni in loco.", StatusLevel.RED),
            PredictionEvent(2, getFormattedDate(2), "Lavori Svincolo Bellano", "CCISS Viaggiare Informati", "Chiusura rampa di uscita svincolo Bellano in direzione Nord. Traffico deviato.", StatusLevel.YELLOW),
            PredictionEvent(4, getFormattedDate(4), "Traffico Intenso (Esodi)", "Analisi Storica IA", "Forti rallentamenti previsti in direzione Nord dal tardo pomeriggio per esodi festivi.", StatusLevel.YELLOW)
        )

        _state.update { 
            it.copy(
                summaries = summaries,
                southRoute = RouteDetails(RouteDirection.SUD, mockSegmentsSouth),
                northRoute = RouteDetails(RouteDirection.NORD, mockSegmentsNorth),
                predictions = predictionsList,
                lastUpdated = getCurrentTime()
            )
        }
    }

    private fun startRealTimeUpdates() {
        viewModelScope.launch {
            delay(1500)
            _state.update { 
                it.copy(
                    isOffline = true,
                    lastUpdated = getCurrentTime()
                )
            }
            delay(4000)
            _state.update {
                it.copy(
                    isOffline = false,
                    lastUpdated = getCurrentTime()
                )
            }
        }
    }
}
