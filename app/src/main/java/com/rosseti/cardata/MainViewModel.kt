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
        private const val IDLE_FUEL_CONSUMPTION_L_H = 1.0f // Расход на холостом ходу (л/час)

        private const val ID_KM = "km"
        private const val ID_FUEL = "fuel"
        private const val ID_FUEL_STD = "fuelStandart"
        private const val ID_MAX_SPEED = "max_speed"

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
        
        val startTime = repository.getStartTime()
        val durationMillis = if (isTripStarted.value && startTime > 0) System.currentTimeMillis() - startTime else 0L

        val maxSpeedStr = repository.getMaxSpeed()
        val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd, durationMillis)

        val kmStr = formatDecimal(currentTotalKm)
        val fuelStr = formatDecimal(remainingFuel)
        val fuelStdStr = formatDecimal(fuelStd)

        if (fields.isEmpty()) {
            fields.add(NumericField(ID_KM, "Спидометр(км)", kmStr))
            fields.add(NumericField(ID_FUEL, "Остаток топлива", fuelStr))
            fields.add(NumericField(ID_FUEL_STD, "Норма расхода топлива", fuelStdStr))
            fields.add(NumericField(ID_MAX_SPEED, "Макс. скорость (км/ч)", maxSpeedStr))
        } else {
            updateField(ID_KM, kmStr)
            updateField(ID_FUEL, fuelStr)
            updateField(ID_FUEL_STD, fuelStdStr)
            updateField(ID_MAX_SPEED, maxSpeedStr)
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
     * Форматирует число с плавающей запятой в строку без знаков после запятой (целое).
     */
    private fun formatInteger(value: Float): String {
        return if (value == 0f) "0" else String.format(Locale.US, "%.0f", value)
    }

    /**
     * Вычисляет остаток топлива на основе пройденного расстояния, нормы расхода и времени работы двигателя.
     */
    private fun calculateRemainingFuel(traveledKm: Float, fuelStd: Float, durationMillis: Long): Float {
        if (fuelStd <= 0) return baseFuel
        
        val totalFactor = getConsumptionFactor()
        
        // Расход в движении (л/100км)
        val fuelConsumedMoving = (traveledKm * (fuelStd * totalFactor)) / FUEL_CONSUMPTION_BASE_DISTANCE
        
        // Расход на холостом ходу (л/час)
        val durationHours = durationMillis / (1000f * 60f * 60f)
        val fuelConsumedIdle = durationHours * IDLE_FUEL_CONSUMPTION_L_H
        
        return (baseFuel - (fuelConsumedMoving + fuelConsumedIdle)).coerceAtLeast(0f)
    }

    /**
     * Возвращает итоговый коэффициент расхода топлива с учетом сезонных факторов (аддитивно).
     */
    private fun getConsumptionFactor(): Float {
        var additionalFactor = 0.0f
        if (isSpring.value) additionalFactor += (SEASONAL_FACTOR - 1.0f)
        if (isWinter.value) additionalFactor += (SEASONAL_FACTOR - 1.0f)
        return 1.0f + additionalFactor
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
        
        val currentSpeed = repository.getCurrentSpeed()
        val savedMaxSpeed = repository.getMaxSpeed().toFloatOrNull() ?: 0f
        
        if (currentSpeed > savedMaxSpeed) {
            repository.saveMaxSpeed(formatInteger(currentSpeed))
        }
        
        updateField(ID_MAX_SPEED, repository.getMaxSpeed())

        val fuelStdField = fields.find { it.id == ID_FUEL_STD }
        val fuelStdValue = fuelStdField?.value ?: ""
        
        // Если норма расхода топлива не пустая, выполняем расчет остатка топлива
        if (fuelStdValue.isNotEmpty()) {
            val fuelStd = fuelStdValue.toFloatOrNull() ?: 0f
            val startTime = repository.getStartTime()
            val durationMillis = if (startTime > 0) System.currentTimeMillis() - startTime else 0L
            val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd, durationMillis)
            updateField(ID_FUEL, formatDecimal(remainingFuel))
        }
    }

    /**
     * Запускает поездку.
     */
    fun onStartTrip() {
        isTripStarted.value = true
        repository.saveTripStarted(true)
        totalDistance.floatValue = 0f
        repository.saveTotalDistance(0f)
        repository.saveMaxSpeed("0")
        
        repository.saveStartTime(System.currentTimeMillis())

        baseKm = fields.find { it.id == ID_KM }?.value?.toFloatOrNull() ?: 0f
        baseFuel = fields.find { it.id == ID_FUEL }?.value?.toFloatOrNull() ?: 0f
        
        repository.saveFieldValue(ID_KM, baseKm)
        repository.saveFieldValue(ID_FUEL, baseFuel)
        
        updateField(ID_MAX_SPEED, "0")
    }

    /**
     * Останавливает поездку и сохраняет финальные данные.
     */
    fun onStopTrip() {
        val currentTripDist = totalDistance.floatValue
        val traveledKm = currentTripDist / METERS_PER_KM
        val fuelStd = fields.find { it.id == ID_FUEL_STD }?.value?.toFloatOrNull() ?: 0f

        val startTime = repository.getStartTime()
        val durationMillis = if (startTime > 0) System.currentTimeMillis() - startTime else 0L

        val finalKm = baseKm + traveledKm
        val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd, durationMillis)
        val maxSpeed = repository.getMaxSpeed()

        repository.saveFieldValue(ID_KM, finalKm)
        repository.saveFieldValue(ID_FUEL, remainingFuel)
        repository.saveMaxSpeed(maxSpeed)
        repository.saveStartTime(0L)
        repository.saveTripStarted(false)

        baseKm = finalKm
        baseFuel = remainingFuel

        totalDistance.floatValue = 0f
        repository.saveTotalDistance(0f)
        isTripStarted.value = false

        updateField(ID_KM, formatDecimal(finalKm))
        updateField(ID_FUEL, formatDecimal(remainingFuel))
        updateField(ID_MAX_SPEED, maxSpeed)
    }
}
