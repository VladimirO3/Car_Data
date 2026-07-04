/**
 * @Author Osetrov.V.V.
 */
package com.rosseti.cardata.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Репозиторий для управления параметрами постоянного приложения с помощью [SharedPreferences].
 * Управление сохранением и извлечением значений расстояния и полей.
 *
 * @param context Контекст приложения, используемый для доступа к SharedPreferences.
 */
class SettingsRepository(context: Context) {
    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

    /**
     * Извлекает общее расстояние, пройденное с места хранения.
     *
     * @return Общее хранимое расстояние как [Float].
     */
    fun getTotalDistance(): Float = sharedPrefs.getFloat(KEY_TOTAL_DISTANCE, 0f)

    /**
     * Сохраняет общее расстояние, пройденное для хранения.
     *
     * @param distance Общее расстояние для сохранения.
     */
    fun saveTotalDistance(distance: Float) {
        sharedPrefs.edit { putFloat(KEY_TOTAL_DISTANCE, distance) }
    }

    /**
     * Сохраняет время начала поездки.
     * @param time Время в миллисекундах.
     */
    fun saveStartTime(time: Long) {
        sharedPrefs.edit { putLong(KEY_START_TIME, time) }
    }

    /**
     * Извлекает время начала поездки.
     * @return Время в миллисекундах.
     */
    fun getStartTime(): Long = sharedPrefs.getLong(KEY_START_TIME, 0L)

    /**
     * Сохраняет состояние активности поездки.
     */
    fun saveTripStarted(isStarted: Boolean) {
        sharedPrefs.edit { putBoolean(KEY_IS_TRIP_STARTED, isStarted) }
    }

    /**
     * Возвращает, запущена ли поездка.
     */
    fun isTripStarted(): Boolean = sharedPrefs.getBoolean(KEY_IS_TRIP_STARTED, false)

    /**
     * Извлекает сохраненное значение поля по его идентификатору.
     *
     * @param id Уникальный идентификатор поля.
     * @return Сохраненное значение поля как [Float].
     */
    fun getFieldValue(id: String): Float = sharedPrefs.getFloat(id, 0f)

    /**
     * Сохраняет значение поля в хранилище.
     *
     * @param id Уникальный идентификатор поля.
     * @param value Значение для сохранения.
     */
    fun saveFieldValue(id: String, value: Float) {
        sharedPrefs.edit { putFloat(id, value) }
    }

    /**
     * Сохраняет последнюю среднюю скорость.
     */
    fun saveAvgSpeed(speed: String) {
        sharedPrefs.edit { putString(KEY_AVG_SPEED, speed) }
    }

    /**
     * Извлекает последнюю сохраненную среднюю скорость.
     */
    fun getAvgSpeed(): String = sharedPrefs.getString(KEY_AVG_SPEED, "0.00") ?: "0.00"

    /**
     * Сохраняет текущую мгновенную скорость.
     */
    fun saveCurrentSpeed(speed: Float) {
        sharedPrefs.edit { putFloat(KEY_CURRENT_SPEED, speed) }
    }

    /**
     * Извлекает текущую мгновенную скорость.
     */
    fun getCurrentSpeed(): Float = sharedPrefs.getFloat(KEY_CURRENT_SPEED, 0f)

    companion object {
        private const val KEY_TOTAL_DISTANCE = "total_distance"
        private const val KEY_START_TIME = "trip_start_time"
        private const val KEY_IS_TRIP_STARTED = "is_trip_started"
        private const val KEY_AVG_SPEED = "last_avg_speed"
        private const val KEY_CURRENT_SPEED = "current_speed"
    }
}
