/**
 * @Author Osetrov.V.V.
 * © 2026 Osetrov V.V. Все права защищены.
 */
package com.rosseti.cardata

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.rosseti.cardata.data.SettingsRepository
import com.rosseti.cardata.model.NumericField
import com.rosseti.cardata.model.TripRecord
import java.text.SimpleDateFormat
import java.util.Date
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

        private const val ID_KM = "km"
        private const val ID_FUEL = "fuel"
        private const val ID_FUEL_STD = "fuelStandart"
        private const val ID_CURRENT_SPEED = "current_speed_field"
        private const val ID_TRIP_KM = "trip_km"

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
     * Текущий режим темы (0 - Система, 1 - Светлая, 2 - Темная).
     */
    var themeMode = mutableStateOf(repository.getThemeMode())
        private set

    /**
     * Текущий режим языка (0 - Система, 1 - RU, 2 - EN).
     */
    var languageMode = mutableStateOf(repository.getLanguageMode())
        private set

    /**
     * Состояние языка интерфейса (true - русский, false - английский).
     */
    var isRussian = mutableStateOf<Boolean>(
        if (repository.getLanguageMode() == 0) {
            Locale.getDefault().language == "ru"
        } else {
            repository.getLanguageMode() == 1
        }
    )

    /**
     * Состояние режима "Весна".
     */
    var isSpring = mutableStateOf(false)

    /**
     * Состояние режима "Зима".
     */
    var isWinter = mutableStateOf(false)

    /**
     * Состояние чекбоксов спецоборудования.
     */
    var equip1 = mutableStateOf(repository.getEquipment(1))
    var equip2 = mutableStateOf(repository.getEquipment(2))
    var equip3 = mutableStateOf(repository.getEquipment(3))
    var isWarmup = mutableStateOf(repository.getWarmup())

    /**
     * Список записей истории поездок в виде объектов.
     */
    val tripRecords = mutableStateListOf<TripRecord>()

    /**
     * Список строковых представлений для отображения (для совместимости).
     */
    var tripHistory = mutableStateListOf<String>()
        private set

    /**
     * Текущее направление компаса (азимут).
     */
    var compassHeading = mutableStateOf(0f)

    private var baseKm: Float = 0f
    private var baseFuel: Float = 0f

    init {
        loadFields()
    }

    /**
     * Переключение режима темы (циклично: Система -> Светлая -> Темная).
     */
    fun toggleTheme() {
        themeMode.value = (themeMode.value + 1) % 3
        repository.saveThemeMode(themeMode.value)
    }

    /**
     * Переключение языка интерфейса (циклично: Система -> RU -> EN).
     * Автоматически обновляет состояние [isRussian] и перезагружает метки полей.
     */
    fun toggleLanguage() {
        languageMode.value = (languageMode.value + 1) % 3
        repository.saveLanguageMode(languageMode.value)
        
        isRussian.value = if (languageMode.value == 0) {
            Locale.getDefault().language == "ru"
        } else {
            languageMode.value == 1
        }

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
        
        loadHistory()
        
        val savedTripDistance = repository.getTotalDistance()
        totalDistance.floatValue = savedTripDistance
        
        isTripStarted.value = repository.isTripStarted() || savedTripDistance > 0f
        
        val traveledKm = savedTripDistance / METERS_PER_KM
        val currentTotalKm = baseKm + traveledKm
        
        val currentSpeed = repository.getCurrentSpeed()
        val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd)

        val kmStr = formatDecimal(currentTotalKm)
        val tripKmStr = formatDecimal(traveledKm)
        val fuelStr = formatDecimal(remainingFuel)
        val fuelStdStr = formatDecimal(fuelStd)
        val currentSpeedStr = formatInteger(currentSpeed)

        val labels = if (isRussian.value) {
            listOf("Одометр (км)", "Дистанция (км)", "Остаток топлива", "Норма расхода", "Текущая скорость (км/ч)")
        } else {
            listOf("Odometer (km)", "Trip Distance (km)", "Remaining Fuel", "Fuel Consumption Rate", "Current Speed (km/h)")
        }

        if (fields.isEmpty()) {
            fields.add(NumericField(ID_KM, labels[0], kmStr))
            fields.add(NumericField(ID_TRIP_KM, labels[1], tripKmStr))
            fields.add(NumericField(ID_FUEL, labels[2], fuelStr))
            fields.add(NumericField(ID_FUEL_STD, labels[3], fuelStdStr))
            fields.add(NumericField(ID_CURRENT_SPEED, labels[4], currentSpeedStr))
        } else {
            updateField(ID_KM, kmStr, labels[0])
            updateField(ID_TRIP_KM, tripKmStr, labels[1])
            updateField(ID_FUEL, fuelStr, labels[2])
            updateField(ID_FUEL_STD, fuelStdStr, labels[3])
            updateField(ID_CURRENT_SPEED, currentSpeedStr, labels[4])
        }
    }

    fun loadHistory() {
        tripHistory.clear()
        tripRecords.clear()
        val historyJson = repository.getTripHistoryJson()
        try {
            val jsonArray = org.json.JSONArray(historyJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val record = TripRecord(
                    id = if (obj.has("id")) obj.getString("id") else java.util.UUID.randomUUID().toString(),
                    date = obj.getString("date"),
                    startTime = obj.getString("startTime"),
                    endTime = obj.getString("endTime"),
                    distance = obj.getString("distance"),
                    totalKm = obj.getString("totalKm"),
                    remainingFuel = obj.getString("remainingFuel"),
                    cityDistance = if (obj.has("cityDistance")) obj.getString("cityDistance") else "0.00",
                    intercityDistance = if (obj.has("intercityDistance")) obj.getString("intercityDistance") else "0.00",
                    equipmentFuel = if (obj.has("equipmentFuel")) obj.getString("equipmentFuel") else "0.00",
                    equipmentDetails = if (obj.has("equipmentDetails")) obj.getString("equipmentDetails") else ""
                )
                tripRecords.add(record)
                tripHistory.add(formatRecordForDisplay(record))
            }
            tripHistory.reverse()
            tripRecords.reverse()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatRecordForDisplay(record: TripRecord): String {
        return if (isRussian.value) {
            var base = String.format(Locale.US, "%s | %s-%s | Путь: %s км | Общий: %s км | Топливо: %s л",
                record.date, record.startTime, record.endTime, record.distance, record.totalKm, record.remainingFuel)
            if (record.cityDistance != "0.00") base += " | Город: ${record.cityDistance} км"
            if (record.intercityDistance != "0.00") base += " | Межгород: ${record.intercityDistance} км"
            if (record.equipmentFuel != "0.00") {
                val details = if (record.equipmentDetails.isNotEmpty()) " (${record.equipmentDetails})" else ""
                base += " | Доп. расход: ${record.equipmentFuel} л$details"
            }
            base
        } else {
            var base = String.format(Locale.US, "%s | %s-%s | Trip: %s km | Total: %s km | Fuel: %s L",
                record.date, record.startTime, record.endTime, record.distance, record.totalKm, record.remainingFuel)
            if (record.cityDistance != "0.00") base += " | City: ${record.cityDistance} km"
            if (record.intercityDistance != "0.00") base += " | Intercity: ${record.intercityDistance} km"
            if (record.equipmentFuel != "0.00") {
                val details = if (record.equipmentDetails.isNotEmpty()) " (${record.equipmentDetails})" else ""
                base += " | Extra Fuel: ${record.equipmentFuel} L$details"
            }
            base
        }
    }

    fun deleteHistoryItem(recordStr: String) {
        // Находим ID записи по строке (не очень надежно, но для текущей логики пойдет)
        // Лучше передавать ID напрямую в UI
        val index = tripHistory.indexOf(recordStr)
        if (index != -1) {
            val record = tripRecords[index]
            repository.deleteTripRecordById(record.id)
            loadHistory()
        }
    }

    fun deleteHistoryItemById(id: String) {
        repository.deleteTripRecordById(id)
        loadHistory()
    }

    fun updateHistoryItem(updatedRecord: TripRecord) {
        val obj = org.json.JSONObject().apply {
            put("id", updatedRecord.id)
            put("date", updatedRecord.date)
            put("startTime", updatedRecord.startTime)
            put("endTime", updatedRecord.endTime)
            put("distance", updatedRecord.distance)
            put("totalKm", updatedRecord.totalKm)
            put("remainingFuel", updatedRecord.remainingFuel)
            put("cityDistance", updatedRecord.cityDistance)
            put("intercityDistance", updatedRecord.intercityDistance)
            put("equipmentFuel", updatedRecord.equipmentFuel)
            put("equipmentDetails", updatedRecord.equipmentDetails)
        }
        repository.updateTripRecord(obj.toString())
        loadHistory()
    }

    fun toggleEquipment(id: Int, checked: Boolean) {
        when(id) {
            1 -> equip1.value = checked
            2 -> equip2.value = checked
            3 -> equip3.value = checked
        }
        repository.saveEquipment(id, checked)
        updateCalculations()
    }

    fun toggleWarmup(checked: Boolean) {
        isWarmup.value = checked
        repository.saveWarmup(checked)
        updateCalculations()
    }

    fun clearHistory() {
        repository.clearTripHistory()
        tripHistory.clear()
    }

    /**
     * Обновляет поле по его ID, если значение изменилось.
     */
    private fun updateField(id: String, newValue: String, newLabel: String? = null) {
        val index = fields.indexOfFirst { it.id == id }
        if (index != -1) {
            val currentField = fields[index]
            if (currentField.value != newValue || (newLabel != null && currentField.label != newLabel)) {
                fields[index] = currentField.copy(
                    value = newValue,
                    label = newLabel ?: currentField.label
                )
            }
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
     * Вычисляет остаток топлива на основе пройденного расстояния и нормы расхода.
     */
    private fun calculateRemainingFuel(traveledKm: Float, fuelStd: Float): Float {
        // Расход в движении (л/100км)
        val fuelConsumedMoving = if (fuelStd > 0) {
            val totalFactor = getConsumptionFactor()
            (traveledKm * (fuelStd * totalFactor)) / FUEL_CONSUMPTION_BASE_DISTANCE
        } else 0f
        
        // Доп. расход от оборудования
        val equipmentFuel = getEquipmentConsumption()
        
        return (baseFuel - fuelConsumedMoving - equipmentFuel).coerceAtLeast(0f)
    }

    private fun getEquipmentConsumption(): Float {
        var total = 0f
        if (equip1.value) total += 9f
        if (equip2.value) total += 9f
        if (equip3.value) total += 9f
        if (isWarmup.value) total += 4.5f
        return total
    }

    private fun getEquipmentDetails(): String {
        val details = mutableListOf<String>()
        var hours = 0
        if (equip1.value) hours++
        if (equip2.value) hours++
        if (equip3.value) hours++
        
        if (hours > 0) {
            val label = if (isRussian.value) {
                if (hours == 1) "1 час спец." else "$hours часа спец."
            } else {
                if (hours == 1) "1h equip." else "$hours h equip."
            }
            details.add("${hours * 9}л $label")
        }
        
        if (isWarmup.value) {
            details.add(if (isRussian.value) "4.5л прогрев" else "4.5L warmup")
        }
        
        return details.joinToString("; ")
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
     * Также обновляет текущую скорость и сохраняет рекорд максимальной скорости.
     */
    private fun updateStateFromDistance(currentDist: Float) {
        totalDistance.floatValue = currentDist
        
        val traveledKm = currentDist / METERS_PER_KM
        val currentTotal = baseKm + traveledKm
        
        updateField(ID_KM, formatDecimal(currentTotal))
        updateField(ID_TRIP_KM, formatDecimal(traveledKm))
        
        val currentSpeed = repository.getCurrentSpeed()
        val savedMaxSpeed = repository.getMaxSpeed().toFloatOrNull() ?: 0f
        
        if (currentSpeed > savedMaxSpeed) {
            repository.saveMaxSpeed(formatInteger(currentSpeed))
        }
        
        updateField(ID_CURRENT_SPEED, formatInteger(currentSpeed))

        val fuelStdField = fields.find { it.id == ID_FUEL_STD }
        val fuelStdValue = fuelStdField?.value ?: ""
        
        // Если норма расхода топлива не пустая, выполняем расчет остатка топлива
        if (fuelStdValue.isNotEmpty()) {
            val fuelStd = fuelStdValue.toFloatOrNull() ?: 0f
            val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd)
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
        repository.saveCityDistance(0f)
        repository.saveIntercityDistance(0f)
        repository.saveMaxSpeed("0")
        
        repository.saveStartTime(System.currentTimeMillis())

        baseKm = fields.find { it.id == ID_KM }?.value?.toFloatOrNull() ?: 0f
        baseFuel = fields.find { it.id == ID_FUEL }?.value?.toFloatOrNull() ?: 0f
        
        repository.saveFieldValue(ID_KM, baseKm)
        repository.saveFieldValue(ID_FUEL, baseFuel)
        
        updateField(ID_TRIP_KM, "0.00")
        updateField(ID_CURRENT_SPEED, "0")
    }

    /**
     * Останавливает поездку и сохраняет финальные данные.
     */
    fun onStopTrip() {
        val currentTripDist = totalDistance.floatValue
        val traveledKm = currentTripDist / METERS_PER_KM
        
        val cityDistKm = repository.getCityDistance() / METERS_PER_KM
        val intercityDistKm = repository.getIntercityDistance() / METERS_PER_KM
        
        val fuelStd = fields.find { it.id == ID_FUEL_STD }?.value?.toFloatOrNull() ?: 0f

        val finalKm = baseKm + traveledKm
        val remainingFuel = calculateRemainingFuel(traveledKm, fuelStd)

        // Сохранение записи о рейсе
        val startTimeMillis = repository.getStartTime()
        val endTimeMillis = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val dateStr = dateFormat.format(Date(endTimeMillis))
        val startStr = if (startTimeMillis > 0) timeFormat.format(Date(startTimeMillis)) else "--:--"
        val endStr = timeFormat.format(Date(endTimeMillis))
        val equipmentFuelVal = getEquipmentConsumption()
        val equipmentDetailsVal = getEquipmentDetails()

        val tripRecord = TripRecord(
            date = dateStr,
            startTime = startStr,
            endTime = endStr,
            distance = String.format(Locale.US, "%.2f", traveledKm),
            totalKm = String.format(Locale.US, "%.2f", finalKm),
            remainingFuel = String.format(Locale.US, "%.2f", remainingFuel),
            cityDistance = String.format(Locale.US, "%.2f", cityDistKm),
            intercityDistance = String.format(Locale.US, "%.2f", intercityDistKm),
            equipmentFuel = String.format(Locale.US, "%.2f", equipmentFuelVal),
            equipmentDetails = equipmentDetailsVal
        )

        val obj = org.json.JSONObject().apply {
            put("id", tripRecord.id)
            put("date", tripRecord.date)
            put("startTime", tripRecord.startTime)
            put("endTime", tripRecord.endTime)
            put("distance", tripRecord.distance)
            put("totalKm", tripRecord.totalKm)
            put("remainingFuel", tripRecord.remainingFuel)
            put("cityDistance", tripRecord.cityDistance)
            put("intercityDistance", tripRecord.intercityDistance)
            put("equipmentFuel", tripRecord.equipmentFuel)
            put("equipmentDetails", tripRecord.equipmentDetails)
        }

        repository.saveTripRecord(obj.toString())
        loadHistory()

        repository.saveFieldValue(ID_KM, finalKm)
        repository.saveFieldValue(ID_FUEL, remainingFuel)
        repository.saveStartTime(0L)
        repository.saveTripStarted(false)
        repository.saveCityDistance(0f)
        repository.saveIntercityDistance(0f)

        baseKm = finalKm
        baseFuel = remainingFuel

        totalDistance.floatValue = 0f
        repository.saveTotalDistance(0f)
        isTripStarted.value = false

        updateField(ID_KM, formatDecimal(finalKm))
        updateField(ID_FUEL, formatDecimal(remainingFuel))
        updateField(ID_CURRENT_SPEED, "0")
    }
}
