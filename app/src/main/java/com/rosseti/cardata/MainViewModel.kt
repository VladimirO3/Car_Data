/**
 * @Author Osetrov.V.V.
 */
package com.rosseti.cardata

import android.annotation.SuppressLint
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
     * Текущая мгновенная скорость.
     */
    var currentSpeed = mutableFloatStateOf(0f)
        private set

    /**
     * Список числовых полей.
     */
    val fields = mutableStateListOf<NumericField>()
    
    /**
     * Состояние режима "Зима/Лето".
     */
    var isWinter = mutableStateOf(false)
    var isSpring = mutableStateOf(false)

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
    fun toggleSpring(spring: Boolean) {
        isSpring.value = spring
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
        
        val avgSpeedStr = if (repository.isTripStarted()) {
            calculateAverageSpeed(savedTripDistance)
        } else {
            repository.getAvgSpeed()
        }

        val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd)

        val kmStr = if (currentTotalKm == 0f) "" else String.format(Locale.US, "%.2f", currentTotalKm)
        val fuelStr = if (remainingFuel == 0f) "" else String.format(Locale.US, "%.2f", remainingFuel)
        val fuelStdStr = if (fuelStd == 0f) "" else String.format(Locale.US, "%.2f", fuelStd)

        if (fields.isEmpty()) {
            fields.add(NumericField("km", "Километраж", kmStr))
            fields.add(NumericField("fuel", "Остаток топлива", fuelStr))
            fields.add(NumericField("fuelStandart", "Норма расхода топлива", fuelStdStr))
            fields.add(NumericField("avg_speed", "Ср. скорость (км/ч)", avgSpeedStr))
        } else {
            updateFieldIfChanged(0, kmStr)
            updateFieldIfChanged(1, fuelStr)
            updateFieldIfChanged(2, fuelStdStr)
            updateFieldIfChanged(3, avgSpeedStr)
        }
        
        if (savedTripDistance > 0f || repository.isTripStarted()) {
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
        if (index in fields.indices && fields[index].value != newValue) {
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
        currentSpeed.floatValue = repository.getCurrentSpeed()
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
	 * Рассчитывает остаток топлива на основе пройденного расстояния и нормы расхода.
	 * Учитывает сезонные коэффициенты (зимний и весенний периоды), увеличивающие расход.
	 *
	 * @param traveledKm Пройденное расстояние в километрах.
	 * @param fuelStd Базовая норма расхода топлива на 100 км.
	 * @return Рассчитанный остаток топлива. Если норма расхода не задана, возвращает базовый остаток.
	 */
    private fun calculateRemainingFuel(traveledKm: Float, fuelStd: Float): Float {
        if (fuelStd <= 0) return baseFuel
        var factor = 1.0f
        if (isWinter.value) factor += 0.1f
        if (isSpring.value) factor += 0.1f
        // Расход = (дистанция * (норма * коэффициенты)) / 100
        val fuelConsumed = (traveledKm * (fuelStd * factor)) / 100f
        return (baseFuel - fuelConsumed).coerceAtLeast(0f)
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
        updateFieldIfChanged(3, calculateAverageSpeed(currentDist))
        
        val fuelStd = fields.getOrNull(2)?.value?.toFloatOrNull() ?: 0f
        val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd)
        updateFieldIfChanged(1, String.format(Locale.US, "%.2f", remainingFuel))
    }

    private fun calculateAverageSpeed(distanceMeters: Float): String {
        val startTime = repository.getStartTime()
        // Не считаем скорость, если пройдено меньше 10 метров (защита от дрейфа при старте)
        if (startTime == 0L || distanceMeters < 10f) return "0.00"
        
        val durationMillis = System.currentTimeMillis() - startTime
        // Минимум 2 секунды для корректного расчета
        if (durationMillis < 2000) return "0.00" 
        
        val durationHours = durationMillis / (1000f * 60f * 60f)
        val distanceKm = distanceMeters / 1000f
        val avgSpeed = distanceKm / durationHours
        
        // Ограничение максимальной скорости для отсечения ошибок GPS
        return String.format(Locale.US, "%.2f", if (avgSpeed > 220f) 0f else avgSpeed)
    }

    /**
     * Запускает процесс отслеживания поездки.
     * Устанавливает флаг начала поездки, фиксирует текущие введённые значения километража
     * и остатка топлива как базовые для последующих расчетов и сохраняет их в репозитории.
     */
    fun onStartTrip() {
        isTripStarted.value = true
        repository.saveTripStarted(true)
        
        // СБРОС ДИСТАНЦИИ ПЕРЕД НОВЫМ РЕЙСОМ
        totalDistance.floatValue = 0f
        repository.saveTotalDistance(0f)

        repository.saveStartTime(System.currentTimeMillis())
        
        baseKm = fields.getOrNull(0)?.value?.toFloatOrNull() ?: 0f
        baseFuel = fields.getOrNull(1)?.value?.toFloatOrNull() ?: 0f
        repository.saveFieldValue("km", baseKm)
        repository.saveFieldValue("fuel", baseFuel)
        
        updateFieldIfChanged(3, "0.00")
    }

    fun onStopTrip() {
        val currentDist = repository.getTotalDistance()
        val traveledKm = currentDist / 1000f
        val fuelStd = fields.getOrNull(2)?.value?.toFloatOrNull() ?: 0f
        
        val finalKm = baseKm + traveledKm
        val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd)
        val avgSpeed = calculateAverageSpeed(currentDist)

        repository.saveFieldValue("km", finalKm)
        repository.saveFieldValue("fuel", remainingFuel)
        repository.saveAvgSpeed(avgSpeed)
        repository.saveStartTime(0L)
        repository.saveTripStarted(false)
        
        baseKm = finalKm
        baseFuel = remainingFuel

        totalDistance.floatValue = 0f
        repository.saveTotalDistance(0f)
        isTripStarted.value = false

        updateFieldIfChanged(0, String.format(Locale.US, "%.2f", finalKm))
        updateFieldIfChanged(1, String.format(Locale.US, "%.2f", remainingFuel))
        updateFieldIfChanged(3, avgSpeed)
    }

    @SuppressLint("EmptySuperCall")
	override fun onCleared() {
        super.onCleared()
    }
}
