/**
 * @Author Osetrov.V.V.
 */
package com.rosseti.cardata

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.rosseti.cardata.data.SettingsRepository
import com.rosseti.cardata.model.NumericField
import java.util.Locale

/**
 * ViewModel для главного экрана приложения.
 * Обрабатывает логику данных, постоянство состояния и вычисления расстояний.
 *
 * @property repository Репозиторий, используемый для сохранения данных.
 */
class MainViewModel(private val repository: SettingsRepository) : ViewModel() {

    /**
     * Общее расстояние, пройденное в текущей поездке, в метрах.
     */
    var totalDistance = mutableFloatStateOf(0f)
        private set

    /**
     * Флажок, указывающий на активность поездки.
     */
    var isTripStarted = mutableStateOf(false)
        private set

    /**
     * Список числовых полей, представляющих различные точки данных (например, пробег, топливо).
     */
    val fields = mutableStateListOf<NumericField>()
    
    private var baseKm: Float = 0f
    private var baseFuel: Float = 0f

    init {
        loadFields()
    }

    /**
     * Загружает сохраненные значения полей и общее расстояние от репозитория.
     * Инициализирует поля пользовательского интерфейса на основе загруженных данных.
     */
    private fun loadFields() {
        fields.clear()
        baseKm = repository.getFieldValue("km")
        baseFuel = repository.getFieldValue("fuel")
        val fuelStd = repository.getFieldValue("fuelStandart")
        
        val savedTripDistance = repository.getTotalDistance()
        totalDistance.floatValue = savedTripDistance
        
        val traveledKm = savedTripDistance / 1000f
        val currentTotalKm = baseKm + traveledKm
        
        val remainingFuel = if (fuelStd > 0) {
            (baseFuel - (traveledKm * fuelStd / 100f)).coerceAtLeast(0f)
        } else baseFuel

        fields.add(NumericField("km", "Километраж", if (currentTotalKm == 0f) "" else String.format(Locale.US, "%.2f", currentTotalKm)))
        fields.add(NumericField("fuel", "Остаток топлива", if (remainingFuel == 0f) "" else String.format(Locale.US, "%.2f", remainingFuel)))
        fields.add(NumericField("fuelStandart", "Норма расхода топлива", if (fuelStd == 0f) "" else String.format(Locale.US, "%.2f", fuelStd)))
        
        if (savedTripDistance > 0f) {
            isTripStarted.value = true
        }
    }

    /**
     * Обрабатывает изменения значения числового поля.
     * Проверяет входные данные и обновляет состояние и репозиторий.
     *
     * @param index Индекс поля в списке [fields].
     * @param input Новое значение строки, введенное пользователем.
     */
    fun onFieldChange(index: Int, input: String) {
        if (index !in fields.indices) return

        val field = fields[index]
        val formattedInput = input.replace(',', '.')
        
        val isValidDecimal = formattedInput.isEmpty() || 
                           formattedInput.matches(Regex("""^\d{0,7}(\.\d{0,2})?$"""))
        
        if (isValidDecimal && formattedInput.length <= 10) {
            fields[index] = field.copy(value = formattedInput)
            
            val floatValue = formattedInput.toFloatOrNull() ?: 0f
            
            if (field.id == "km") {
                baseKm = floatValue
                totalDistance.floatValue = 0f
                repository.saveTotalDistance(0f)
            } else if (field.id == "fuel") {
                baseFuel = floatValue
            }
            
            repository.saveFieldValue(field.id, floatValue)
        }
    }

    /**
     * Добавляет указанное расстояние к общему расстоянию поездки.
     * Обновление полей интерфейса на основе нового общего расстояния и расхода топлива.
     *
     * @param distanceMeters Расстояние, пройденное в метрах.
     */
    fun addDistance(distanceMeters: Float) {
        if (!isTripStarted.value) return
        
        if (distanceMeters > 0.5f) {
            totalDistance.floatValue += distanceMeters
            repository.saveTotalDistance(totalDistance.floatValue)
            
            val traveledKm = totalDistance.floatValue / 1000f
            val currentTotal = baseKm + traveledKm
            
            fields[0] = fields[0].copy(value = String.format(Locale.US, "%.2f", currentTotal))
            
            val fuelStd = fields.getOrNull(2)?.value?.toFloatOrNull() ?: 0f
            if (fuelStd > 0) {
                val fuelConsumed = (traveledKm * fuelStd) / 100f
                val remainingFuel = (baseFuel - fuelConsumed).coerceAtLeast(0f)
                fields[1] = fields[1].copy(value = String.format(Locale.US, "%.2f", remainingFuel))
            }
        }
    }

    /**
     * Начинает новую поездку.
     * Устанавливает начальные базовые значения для пробега и топлива.
     */
    fun onStartTrip() {
        isTripStarted.value = true
        baseKm = fields.getOrNull(0)?.value?.toFloatOrNull() ?: 0f
        baseFuel = fields.getOrNull(1)?.value?.toFloatOrNull() ?: 0f
        repository.saveFieldValue("km", baseKm)
        repository.saveFieldValue("fuel", baseFuel)
    }

    /**
     * Останавливает текущую поездку.
     * Сбрасывает расстояние поездки и сохраняет итоговые пробег и топливные значения в качестве новой базы.
     */
    fun onStopTrip() {
        val traveledKm = totalDistance.floatValue / 1000f
        val fuelStd = fields.getOrNull(2)?.value?.toFloatOrNull() ?: 0f
        val fuelConsumed = (traveledKm * fuelStd) / 100f

        val finalKm = baseKm + traveledKm
        val remainingFuel = (baseFuel - fuelConsumed).coerceAtLeast(0f)

        repository.saveFieldValue("km", finalKm)
        repository.saveFieldValue("fuel", remainingFuel)
        
        baseKm = finalKm
        baseFuel = remainingFuel

        totalDistance.floatValue = 0f
        repository.saveTotalDistance(0f)
        isTripStarted.value = false

        fields[0] = fields[0].copy(value = String.format(Locale.US, "%.2f", finalKm))
        fields[1] = fields[1].copy(value = String.format(Locale.US, "%.2f", remainingFuel))
    }
}
