package com.rosseti.cardata

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.rosseti.cardata.data.SettingsRepository
import com.rosseti.cardata.model.NumericField
import java.util.Locale
/** Она отвечает за логику работы с данными и хранит состояние экрана,
чтобы оно не пропадало при повороте телефона или переключении между приложениями.
**/
class MainViewModel(private val repository: SettingsRepository) : ViewModel() {
    
    // Область пользовательского интерфейса
    var totalDistance = mutableFloatStateOf(0f) // Текущее расстояние прохода в метрах
        private set
    
    var isTripStarted = mutableStateOf(false)
        private set

    val fields = mutableStateListOf<NumericField>()
    private var baseKm: Float = 0f
    private var baseFuel: Float = 0f

    init {
        loadFields()
    }

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
        
        // Если у нас сохранено расстояние поездки > 0, мы предполагаем, что поездка была активной.
        if (savedTripDistance > 0f) {
            isTripStarted.value = true
        }
    }

    fun onFieldChange(index: Int, input: String) {
        if (index !in fields.indices) return

        val field = fields[index]
        val formattedInput = input.replace(',', '.')
        
        val isValidDecimal = formattedInput.isEmpty() || 
                           formattedInput.matches(Regex("""^\d{0,7}(\.\d{0,2})?$"""))
        
        if (isValidDecimal && formattedInput.length <= 10) {
            fields[index] = field.copy(value = formattedInput)
            
            val floatValue = formattedInput.toFloatOrNull() ?: 0f
            
            // Если пользователь вручную изменяет базовые значения, мы сбрасываем счетчик запуска.
            // чтобы избежать путаницы в общем количестве.
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

    fun onStartTrip() {
        isTripStarted.value = true
        // Если мы начинаем новую поездку, что текущие значения отображения являются основой.
        baseKm = fields.getOrNull(0)?.value?.toFloatOrNull() ?: 0f
        baseFuel = fields.getOrNull(1)?.value?.toFloatOrNull() ?: 0f
        repository.saveFieldValue("km", baseKm)
        repository.saveFieldValue("fuel", baseFuel)
        
        // Сбросить расстояние между поездками, если это совершенно новый старт
    }

    fun onStopTrip() {
        val traveledKm = totalDistance.floatValue / 1000f
        val fuelStd = fields.getOrNull(2)?.value?.toFloatOrNull() ?: 0f
        val fuelConsumed = (traveledKm * fuelStd) / 100f

        val finalKm = baseKm + traveledKm
        val remainingFuel = (baseFuel - fuelConsumed).coerceAtLeast(0f)

        // Сохранить окончательные значения в качестве новой основы для СЛЕДУЮЩЕЙ поездки
        repository.saveFieldValue("km", finalKm)
        repository.saveFieldValue("fuel", remainingFuel)
        
        // Обновление местных базовых переменных
        baseKm = finalKm
        baseFuel = remainingFuel

        // Сбросить счетчик отключения
        totalDistance.floatValue = 0f
        repository.saveTotalDistance(0f)
        isTripStarted.value = false

        // Sync UI fields
        fields[0] = fields[0].copy(value = String.format(Locale.US, "%.2f", finalKm))
        fields[1] = fields[1].copy(value = String.format(Locale.US, "%.2f", remainingFuel))
    }
}
