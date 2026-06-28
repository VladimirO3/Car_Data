package com.rosseti.cardata.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsRepository(context: Context) {
    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

    fun getTotalDistance(): Float = sharedPrefs.getFloat(KEY_TOTAL_DISTANCE, 0f)

    fun saveTotalDistance(distance: Float) {
        sharedPrefs.edit { putFloat(KEY_TOTAL_DISTANCE, distance) }
    }

    fun getFieldValue(id: String): Float = sharedPrefs.getFloat(id, 0f)

    fun saveFieldValue(id: String, value: Float) {
        sharedPrefs.edit { putFloat(id, value) }
    }

    companion object {
        private const val KEY_TOTAL_DISTANCE = "total_distance"
    }
}
