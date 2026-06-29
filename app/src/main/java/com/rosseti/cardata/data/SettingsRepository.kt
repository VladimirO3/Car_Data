/**
 * @Author Osetrov.V.V.
 */
package com.rosseti.cardata.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Repository for managing persistent application settings using [SharedPreferences].
 * Handles saving and retrieving distance and field values.
 *
 * @param context The application context used to access SharedPreferences.
 */
class SettingsRepository(context: Context) {
    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

    /**
     * Retrieves the total distance traveled from storage.
     *
     * @return The stored total distance as a [Float].
     */
    fun getTotalDistance(): Float = sharedPrefs.getFloat(KEY_TOTAL_DISTANCE, 0f)

    /**
     * Saves the total distance traveled to storage.
     *
     * @param distance The total distance to save.
     */
    fun saveTotalDistance(distance: Float) {
        sharedPrefs.edit { putFloat(KEY_TOTAL_DISTANCE, distance) }
    }

    /**
     * Retrieves a stored field value by its ID.
     *
     * @param id The unique identifier of the field.
     * @return The stored value of the field as a [Float].
     */
    fun getFieldValue(id: String): Float = sharedPrefs.getFloat(id, 0f)

    /**
     * Saves a field value to storage.
     *
     * @param id The unique identifier of the field.
     * @param value The value to save.
     */
    fun saveFieldValue(id: String, value: Float) {
        sharedPrefs.edit { putFloat(id, value) }
    }

    companion object {
        private const val KEY_TOTAL_DISTANCE = "total_distance"
    }
}
