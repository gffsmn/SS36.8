package com.example.models

enum class RouteDirection { SUD, NORD }

enum class StatusLevel { GREEN, YELLOW, RED }

enum class EventType { CLEAR, TRAFFIC, ACCIDENT, CLOSURE, WORK }

enum class ThemeMode { AUTO, LIGHT, DARK }

data class RouteSummary(
    val direction: RouteDirection,
    val title: String,
    val status: StatusLevel,
    val summary: String
)

data class HighwaySegment(
    val exitName: String,
    val eventType: EventType,
    val message: String
)

data class RouteDetails(
    val direction: RouteDirection,
    val segments: List<HighwaySegment>
)

data class PredictionEvent(
    val dayOffset: Int,
    val dateString: String,
    val title: String,
    val source: String,
    val description: String,
    val impact: StatusLevel
)
