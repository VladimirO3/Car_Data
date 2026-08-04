/**
 * © 2026 Osetrov V.V. Все права защищены.
 */
package com.rosseti.cardata.model

/**
 * Модель данных для записи о рейсе.
 */
data class TripRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val date: String,
    val startTime: String,
    val endTime: String,
    val distance: String,
    val totalKm: String,
    val remainingFuel: String,
    val cityDistance: String = "0.00",
    val intercityDistance: String = "0.00",
    val equipmentFuel: String = "0.00",
    val equipmentDetails: String = ""
)
