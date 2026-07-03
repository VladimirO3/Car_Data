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

    companion object {
        private const val SEASONAL_FACTOR = 1.1f
        private const val METERS_PER_KM = 1000f
        private const val FUEL_CONSUMPTION_BASE_DISTANCE = 100f
        private const val STABILIZATION_DURATION_MS = 5000L
        private const val MAX_PLAUSIBLE_SPEED_KMH = 250f

        private const val ID_KM = "km"
        private const val ID_FUEL = "fuel"
        private const val ID_FUEL_STD = "fuelStandart"
        private const val ID_AVG_SPEED = "avg_speed"

        private val DECIMAL_REGEX = Regex("""^\d{0,7}(\.\d{0,2})?$""")
    }

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
     * Состояние режима "Весна".
     */
    var isSpring = mutableStateOf(false)

    /**
     * Состояние режима "Зима".
     */
    var isWinter = mutableStateOf(false)

    private var baseKm: Float = 0f
    private var baseFuel: Float = 0f

    init {
        loadFields()
    }

    /**
     * Переключение режима "Весна" с обновлением расчетов.
     */
    fun toggleSpring(spring: Boolean) {
        isSpring.value = spring
        updateCalculations()
    }

    /**
     * Переключение режима "Зима" с обновлением расчетов.
     */
    fun toggleWinter(winter: Boolean) {
        isWinter.value = winter
        updateCalculations()
    }

    private fun updateCalculations() {
        if (isTripStarted.value) {
            refreshDistance()
        } else {
            loadFields()
        }
    }

    /**
     * Загружает сохраненные параметры из репозитория и инициализирует список полей.
     */
    fun loadFields() {
        baseKm = repository.getFieldValue(ID_KM)
        baseFuel = repository.getFieldValue(ID_FUEL)
        val fuelStd = repository.getFieldValue(ID_FUEL_STD)
        
        val savedTripDistance = repository.getTotalDistance()
        totalDistance.floatValue = savedTripDistance
        
        isTripStarted.value = repository.isTripStarted() || savedTripDistance > 0f
        
        val traveledKm = savedTripDistance / METERS_PER_KM
        val currentTotalKm = baseKm + traveledKm
        
        val avgSpeedStr = if (isTripStarted.value) {
            calculateAverageSpeed(savedTripDistance)
        } else {
            repository.getAvgSpeed()
        }
        val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd)

        val kmStr = formatDecimal(currentTotalKm)
        val fuelStr = formatDecimal(remainingFuel)
        val fuelStdStr = formatDecimal(fuelStd)

        if (fields.isEmpty()) {
            fields.add(NumericField(ID_KM, "Километраж", kmStr))
            fields.add(NumericField(ID_FUEL, "Остаток топлива", fuelStr))
            fields.add(NumericField(ID_FUEL_STD, "Норма расхода топлива", fuelStdStr))
            fields.add(NumericField(ID_AVG_SPEED, "Ср. скорость (км/ч)", avgSpeedStr))
        } else {
            updateField(ID_KM, kmStr)
            updateField(ID_FUEL, fuelStr)
            updateField(ID_FUEL_STD, fuelStdStr)
            updateField(ID_AVG_SPEED, avgSpeedStr)
        }
    }

    /**
     * Обновляет поле по его ID, если значение изменилось.
     */
    private fun updateField(id: String, newValue: String) {
        val index = fields.indexOfFirst { it.id == id }
        if (index != -1 && fields[index].value != newValue) {
            fields[index] = fields[index].copy(value = newValue)
        }
    }

    /**
     * Форматирует число с плавающей запятой в строку с двумя знаками после запятой.
     */
    private fun formatDecimal(value: Float): String {
        return if (value == 0f) "" else String.format(Locale.US, "%.2f", value)
    }

    /**
     * Вычисляет остаток топлива на основе пройденного расстояния и нормы расхода.
     */
    private fun calculateRemainingFuel(traveledKm: Float, fuelStd: Float): Float {
        if (fuelStd <= 0) return baseFuel
        val totalFactor = getConsumptionFactor()
        val fuelConsumed = (traveledKm * (fuelStd * totalFactor)) / FUEL_CONSUMPTION_BASE_DISTANCE
        return (baseFuel - fuelConsumed).coerceAtLeast(0f)
    }

    /**
     * Возвращает итоговый коэффициент расхода топлива с учетом сезонных факторов.
     */
    private fun getConsumptionFactor(): Float {
        var factor = 1.0f
        if (isSpring.value) factor *= SEASONAL_FACTOR
        if (isWinter.value) factor *= SEASONAL_FACTOR
        return factor
    }

    /**
     * Обрабатывает ввод пользователя для числовых полей.
     */
    fun onFieldChange(index: Int, input: String) {
        if (index !in fields.indices) return

        val field = fields[index]
        val formattedInput = input.replace(',', '.')
        
        val isValidInput = formattedInput.isEmpty() || 
                          (formattedInput.matches(DECIMAL_REGEX) && formattedInput.length <= 10)
        
        if (isValidInput) {
            fields[index] = field.copy(value = formattedInput)
            val floatValue = formattedInput.toFloatOrNull() ?: 0f
            
            when (field.id) {
                ID_KM -> {
                    baseKm = floatValue
                    totalDistance.floatValue = 0f
                    repository.saveTotalDistance(0f)
                }
                ID_FUEL -> {
                    baseFuel = floatValue
                }
            }
            repository.saveFieldValue(field.id, floatValue)
        }
    }

    /**
     * Синхронизирует состояние с текущей дистанцией.
     */
    fun refreshDistance() {
        if (!isTripStarted.value) return
        updateStateFromDistance(repository.getTotalDistance())
    }

    /**
     * Добавляет пройденное расстояние и обновляет состояние.
     */
    fun addDistance(meters: Float) {
        if (!isTripStarted.value) return
        val newDist = totalDistance.floatValue + meters
        repository.saveTotalDistance(newDist)
        updateStateFromDistance(newDist)
    }

    /**
     * Обновляет внутреннее состояние на основе пройденного расстояния.
     */
    private fun updateStateFromDistance(currentDist: Float) {
        totalDistance.floatValue = currentDist
        
        val traveledKm = currentDist / METERS_PER_KM
        val currentTotal = baseKm + traveledKm
        
        updateField(ID_KM, formatDecimal(currentTotal))
        updateField(ID_AVG_SPEED, calculateAverageSpeed(currentDist))
        
        val fuelStd = fields.find { it.id == ID_FUEL_STD }?.value?.toFloatOrNull() ?: 0f
        val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd)
        updateField(ID_FUEL, formatDecimal(remainingFuel))
    }

    /**
     * Вычисляет среднюю скорость поездки.
     */
    private fun calculateAverageSpeed(distanceMeters: Float): String {
        val startTime = repository.getStartTime()
        if (startTime == 0L || distanceMeters <= 0f) return "0.00"
        
        val durationMillis = System.currentTimeMillis() - startTime
        if (durationMillis < STABILIZATION_DURATION_MS) return "0.00"
        
        val durationHours = durationMillis / (1000f * 60f * 60f)
        val distanceKm = distanceMeters / METERS_PER_KM
        val avgSpeed = distanceKm / durationHours
        
        return if (avgSpeed > MAX_PLAUSIBLE_SPEED_KMH) "0.00" else String.format(Locale.US, "%.2f", avgSpeed)
    }

    /**
     * Запускает поездку.
     */
    fun onStartTrip() {
        isTripStarted.value = true
        repository.saveTripStarted(true)
        totalDistance.floatValue = 0f
        repository.saveTotalDistance(0f)
        
        repository.saveStartTime(System.currentTimeMillis())

        baseKm = fields.find { it.id == ID_KM }?.value?.toFloatOrNull() ?: 0f
        baseFuel = fields.find { it.id == ID_FUEL }?.value?.toFloatOrNull() ?: 0f
        
        repository.saveFieldValue(ID_KM, baseKm)
        repository.saveFieldValue(ID_FUEL, baseFuel)
        
        updateField(ID_AVG_SPEED, "0.00")
    }

    /**
     * Останавливает поездку и сохраняет финальные данные.
     */
    fun onStopTrip() {
        val currentTripDist = totalDistance.floatValue
        val traveledKm = currentTripDist / METERS_PER_KM
        val fuelStd = fields.find { it.id == ID_FUEL_STD }?.value?.toFloatOrNull() ?: 0f

        val finalKm = baseKm + traveledKm
        val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd)
        val avgSpeed = calculateAverageSpeed(currentTripDist)

        repository.saveFieldValue(ID_KM, finalKm)
        repository.saveFieldValue(ID_FUEL, remainingFuel)
        repository.saveAvgSpeed(avgSpeed)
        repository.saveStartTime(0L)
        repository.saveTripStarted(false)

        baseKm = finalKm
        baseFuel = remainingFuel

        totalDistance.floatValue = 0f
        repository.saveTotalDistance(0f)
        isTripStarted.value = false

        updateField(ID_KM, formatDecimal(finalKm))
        updateField(ID_FUEL, formatDecimal(remainingFuel))
        updateField(ID_AVG_SPEED, avgSpeed)
    }
}
