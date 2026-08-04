/**
 * @Author Osetrov.V.V.
 * © 2026 Osetrov V.V. Все права защищены.
 */
package com.rosseti.cardata.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Locale

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
     * Сохраняет последнюю максимальную скорость.
     */
    fun saveMaxSpeed(speed: String) {
        sharedPrefs.edit { putString(KEY_MAX_SPEED, speed) }
    }

    /**
     * Извлекает максимальную скорость.
     */
    fun getMaxSpeed(): String = sharedPrefs.getString(KEY_MAX_SPEED, "0") ?: "0"


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

    /**
     * Сохраняет выбранный режим темы (0 - Система, 1 - Светлая, 2 - Темная).
     */
    fun saveThemeMode(mode: Int) {
        sharedPrefs.edit { putInt(KEY_THEME_MODE, mode) }
    }

    /**
     * Извлекает текущий режим темы.
     */
    fun getThemeMode(): Int = sharedPrefs.getInt(KEY_THEME_MODE, 0)

    /**
     * Сохраняет выбранный режим языка (0 - Система, 1 - Русский, 2 - English).
     */
    fun saveLanguageMode(mode: Int) {
        sharedPrefs.edit { putInt(KEY_LANGUAGE_MODE, mode) }
    }

    /**
     * Извлекает текущий режим языка.
     */
    fun getLanguageMode(): Int = sharedPrefs.getInt(KEY_LANGUAGE_MODE, 0)

    /**
     * Сохраняет выбранный язык (true - русский).
     * @deprecated Используйте saveLanguageMode
     */
    fun saveIsRussian(isRussian: Boolean) {
        saveLanguageMode(if (isRussian) 1 else 2)
    }

    /**
     * Извлекает текущий выбор языка.
     * @deprecated Используйте getLanguageMode
     */
    fun getIsRussian(): Boolean {
        val mode = getLanguageMode()
        if (mode == 0) {
            return Locale.getDefault().language == "ru"
        }
        return mode == 1
    }

    /**
     * Извлекает пройденное расстояние по городу (до 40 км/ч) в метрах.
     */
    fun getCityDistance(): Float = sharedPrefs.getFloat(KEY_CITY_DISTANCE, 0f)

    /**
     * Сохраняет пройденное расстояние по городу.
     */
    fun saveCityDistance(distance: Float) {
        sharedPrefs.edit { putFloat(KEY_CITY_DISTANCE, distance) }
    }

    /**
     * Извлекает пройденное расстояние по межгороду (от 40 км/ч) в метрах.
     */
    fun getIntercityDistance(): Float = sharedPrefs.getFloat(KEY_INTERCITY_DISTANCE, 0f)

    /**
     * Сохраняет пройденное расстояние по межгороду.
     */
    fun saveIntercityDistance(distance: Float) {
        sharedPrefs.edit { putFloat(KEY_INTERCITY_DISTANCE, distance) }
    }

    /**
     * Сохраняет запись о поездке в историю.
     */
    fun saveTripRecord(recordJson: String) {
        val currentHistory = sharedPrefs.getString(KEY_TRIP_HISTORY, "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(currentHistory)
            jsonArray.put(org.json.JSONObject(recordJson))
            sharedPrefs.edit { putString(KEY_TRIP_HISTORY, jsonArray.toString()) }
        } catch (e: Exception) {
            val newArray = org.json.JSONArray().put(org.json.JSONObject(recordJson))
            sharedPrefs.edit { putString(KEY_TRIP_HISTORY, newArray.toString()) }
        }
    }

    /**
     * Возвращает историю поездок в формате JSON.
     */
    fun getTripHistoryJson(): String = sharedPrefs.getString(KEY_TRIP_HISTORY, "[]") ?: "[]"

    /**
     * Обновляет конкретную запись в истории.
     */
    fun updateTripRecord(updatedRecordJson: String) {
        val currentHistory = getTripHistoryJson()
        try {
            val jsonArray = org.json.JSONArray(currentHistory)
            val updatedObject = org.json.JSONObject(updatedRecordJson)
            val id = updatedObject.getString("id")
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.has("id") && obj.getString("id") == id) {
                    jsonArray.put(i, updatedObject)
                    break
                }
            }
            sharedPrefs.edit { putString(KEY_TRIP_HISTORY, jsonArray.toString()) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Удаляет конкретную запись из истории по ID.
     */
    fun deleteTripRecordById(id: String) {
        val currentHistory = getTripHistoryJson()
        try {
            val jsonArray = org.json.JSONArray(currentHistory)
            val newArray = org.json.JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.has("id") && obj.getString("id") == id) continue
                newArray.put(obj)
            }
            sharedPrefs.edit { putString(KEY_TRIP_HISTORY, newArray.toString()) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Очищает историю поездок.
     */
    fun clearTripHistory() {
        sharedPrefs.edit { remove(KEY_TRIP_HISTORY) }
    }

    fun saveEquipment(id: Int, checked: Boolean) {
        val key = when(id) {
            1 -> KEY_EQUIPMENT_1
            2 -> KEY_EQUIPMENT_2
            3 -> KEY_EQUIPMENT_3
            else -> return
        }
        sharedPrefs.edit { putBoolean(key, checked) }
    }

    fun getEquipment(id: Int): Boolean {
        val key = when(id) {
            1 -> KEY_EQUIPMENT_1
            2 -> KEY_EQUIPMENT_2
            3 -> KEY_EQUIPMENT_3
            else -> return false
        }
        return sharedPrefs.getBoolean(key, false)
    }

    fun saveWarmup(checked: Boolean) {
        sharedPrefs.edit { putBoolean(KEY_WARMUP, checked) }
    }

    fun getWarmup(): Boolean = sharedPrefs.getBoolean(KEY_WARMUP, false)

    companion object {
        private const val KEY_TOTAL_DISTANCE = "total_distance"
        private const val KEY_CITY_DISTANCE = "city_distance"
        private const val KEY_INTERCITY_DISTANCE = "intercity_distance"
        private const val KEY_START_TIME = "trip_start_time"
        private const val KEY_IS_TRIP_STARTED = "is_trip_started"
        private const val KEY_MAX_SPEED = "last_max_speed"
        private const val KEY_CURRENT_SPEED = "current_speed"
        private const val KEY_IS_RUSSIAN = "is_russian"
        private const val KEY_LANGUAGE_MODE = "language_mode"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_TRIP_HISTORY = "trip_history_json"
        const val KEY_EQUIPMENT_1 = "equip_1"
        const val KEY_EQUIPMENT_2 = "equip_2"
        const val KEY_EQUIPMENT_3 = "equip_3"
        const val KEY_WARMUP = "warmup"
    }
}
