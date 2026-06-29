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

    companion object {
        private const val KEY_TOTAL_DISTANCE = "total_distance"
    }
}
