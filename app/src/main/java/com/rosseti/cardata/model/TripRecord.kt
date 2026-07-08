package com.rosseti.cardata.model

/**
 * Модель данных для записи о рейсе.
 */
data class TripRecord(
    val date: String,
    val startTime: String,
    val endTime: String,
    val distance: String,
    val totalKm: String,
    val remainingFuel: String
)
