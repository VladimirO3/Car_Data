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
     * Список числовых полей.
     */
    val fields = mutableStateListOf<NumericField>()
    
    /**
     * Состояние режима "Зима/Лето".
     */
    var isWinter = mutableStateOf(false)

    /**
     * Переключение режима зима/лето с немедленным обновлением расчетов.
     */
    fun toggleWinter(winter: Boolean) {
        isWinter.value = winter
        if (isTripStarted.value) {
            refreshDistance()
        } else {
            loadFields()
        }
    }

    private var baseKm: Float = 0f
    private var baseFuel: Float = 0f

    init {
        loadFields()
    }

    /**
     * Загружает сохраненные параметры из репозитория и инициализирует список полей
     */
    fun loadFields() {
        baseKm = repository.getFieldValue("km")
        baseFuel = repository.getFieldValue("fuel")
        val fuelStd = repository.getFieldValue("fuelStandart")
        
        val savedTripDistance = repository.getTotalDistance()
        totalDistance.floatValue = savedTripDistance
        
        val traveledKm = savedTripDistance / 1000f
        val currentTotalKm = baseKm + traveledKm
        
        val winterFactor = if (isWinter.value) 1.1f else 1.0f
        val remainingFuel = if (fuelStd > 0) {
            (baseFuel - (traveledKm * (fuelStd * winterFactor) / 100f)).coerceAtLeast(0f)
        } else baseFuel

        val kmStr = if (currentTotalKm == 0f) "" else String.format(Locale.US, "%.2f", currentTotalKm)
        val fuelStr = if (remainingFuel == 0f) "" else String.format(Locale.US, "%.2f", remainingFuel)
        val fuelStdStr = if (fuelStd == 0f) "" else String.format(Locale.US, "%.2f", fuelStd)

        if (fields.isEmpty()) {
            fields.add(NumericField("km", "Километраж", kmStr))
            fields.add(NumericField("fuel", "Остаток топлива", fuelStr))
            fields.add(NumericField("fuelStandart", "Норма расхода топлива", fuelStdStr))
        } else {
            updateFieldIfChanged(0, kmStr)
            updateFieldIfChanged(1, fuelStr)
            updateFieldIfChanged(2, fuelStdStr)
        }
        
        if (savedTripDistance > 0f) {
            isTripStarted.value = true
        }
    }

    /**
     * Обновляет поле по указанному индексу только в том случае, если новое значение отличается от текущего.
     * Это предотвращает лишние обновления состояния и перерисовки UI.
     *
     * @param index Индекс поля в списке [fields].
     * @param newValue Новое строковое значение для установки.
     */
    private fun updateFieldIfChanged(index: Int, newValue: String) {
        if (fields[index].value != newValue) {
            fields[index] = fields[index].copy(value = newValue)
        }
    }

    /**
     * Обрабатывает ввод пользователя для числовых полей, выполняет валидацию и сохраняет данные.
     *
     * Метод выполняет нормализацию разделителей (заменяет запятую на точку), проверяет ввод
     * на соответствие формату десятичного числа (до 7 знаков до запятой и до 2 после) и
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
     * Обновляет состояние данных о поездке на основе текущей дистанции из репозитория.
     *
     * Метод выполняет следующие действия:
     * 1. Синхронизирует [totalDistance] с данными из [repository].
     */
    fun refreshDistance() {
        if (!isTripStarted.value) return
        updateStateFromDistance(repository.getTotalDistance())
    }

    /**
     * Добавляет пройденное расстояние в метрах к текущей поездке.
     * Сохраняет новое значение в репозиторий и обновляет состояние.
     *
     * @param meters Расстояние в метрах для добавления.
     */
    fun addDistance(meters: Float) {
        if (!isTripStarted.value) return
        val newDist = totalDistance.floatValue + meters
        repository.saveTotalDistance(newDist)
        updateStateFromDistance(newDist)
    }

    /**
     * Обновляет внутреннее состояние и поля на основе переданного расстояния.
     *
     * @param currentDist Текущее общее расстояние поездки в метрах.
     */
    private fun updateStateFromDistance(currentDist: Float) {
        totalDistance.floatValue = currentDist
        
        val traveledKm = currentDist / 1000f
        val currentTotal = baseKm + traveledKm
        
        updateFieldIfChanged(0, String.format(Locale.US, "%.2f", currentTotal))
        
        val fuelStd = fields.getOrNull(2)?.value?.toFloatOrNull() ?: 0f
        if (fuelStd > 0) {
            val winterFactor = if (isWinter.value) 1.1f else 1.0f
            val fuelConsumed = (traveledKm * (fuelStd * winterFactor)) / 100f
            val remainingFuel = (baseFuel - fuelConsumed).coerceAtLeast(0f)
            updateFieldIfChanged(1, String.format(Locale.US, "%.2f", remainingFuel))
        }
    }

    /**
     * Запускает процесс отслеживания поездки.
     * Устанавливает флаг начала поездки, фиксирует текущие введённые значения километража
     * и остатка топлива как базовые для последующих расчетов и сохраняет их в репозитории.
     */
    fun onStartTrip() {
        isTripStarted.value = true
        totalDistance.floatValue = 0f
        repository.saveTotalDistance(0f)

        baseKm = fields.getOrNull(0)?.value?.toFloatOrNull() ?: 0f
        baseFuel = fields.getOrNull(1)?.value?.toFloatOrNull() ?: 0f
        repository.saveFieldValue("km", baseKm)
        repository.saveFieldValue("fuel", baseFuel)
    }

    fun onStopTrip() {
        val traveledKm = totalDistance.floatValue / 1000f
        val fuelStd = fields.getOrNull(2)?.value?.toFloatOrNull() ?: 0f
        val winterFactor = if (isWinter.value) 1.1f else 1.0f
        val fuelConsumed = (traveledKm * (fuelStd * winterFactor)) / 100f

        val finalKm = baseKm + traveledKm
        val remainingFuel = (baseFuel - fuelConsumed).coerceAtLeast(0f)

        repository.saveFieldValue("km", finalKm)
        repository.saveFieldValue("fuel", remainingFuel)
        
        baseKm = finalKm
        baseFuel = remainingFuel

        totalDistance.floatValue = 0f
        repository.saveTotalDistance(0f)
        isTripStarted.value = false

        updateFieldIfChanged(0, String.format(Locale.US, "%.2f", finalKm))
        updateFieldIfChanged(1, String.format(Locale.US, "%.2f", remainingFuel))
    }
}
