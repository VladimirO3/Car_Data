package com.rosseti.cardata

import com.rosseti.cardata.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.atLeastOnce

/**
 * Единичные тесты для класса [MainViewModel].
 *
 * Этот тестовый пакет проверяет бизнес-логику MainViewModel, включая:
 * - Инициализация и загрузка постоянных данных из [SettingsRepository].
 * - Управление жизненным циклом поездки (начало и остановка поездок).
 * - Отслеживание расстояния и условные обновления на основе статуса поездки.
 * - Обновления динамических полей и их синхронизация с репозиторием.
 * - Расчет расхода топлива и конечного пробега по завершении поездки.
 *
 * Он использует Mockito для инъекции зависимостей и насмешки над слоем репозитория.
 */
class MainViewModelTest {

    @Mock
    private lateinit var repository: SettingsRepository

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Стандартное имитационное поведение
        `when`(repository.getFieldValue(any())).thenReturn(0f)
        `when`(repository.getTotalDistance()).thenReturn(0f)
        `when`(repository.isTripStarted()).thenReturn(false)
        `when`(repository.getAvgSpeed()).thenReturn("0.00")
        
        viewModel = MainViewModel(repository)
    }

    @Test
    fun `init loads fields from repository`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        
        // Re-init to trigger loading
        viewModel = MainViewModel(repository)
        
        assertEquals("100.00", viewModel.fields[0].value)
        assertEquals("50.00", viewModel.fields[1].value)
        assertEquals("10.00", viewModel.fields[2].value)
    }

    @Test
    fun `onStartTrip sets isTripStarted to true and saves base values`() {
        viewModel.onFieldChange(0, "100.0")
        viewModel.onFieldChange(1, "50.0")
        
        viewModel.onStartTrip()
        
        assertTrue(viewModel.isTripStarted.value)
        verify(repository).saveTripStarted(true)
        verify(repository).saveFieldValue("km", 100.0f)
        verify(repository).saveFieldValue("fuel", 50.0f)
        verify(repository).saveTotalDistance(0f)
    }

    @Test
    fun `addDistance updates UI fields correctly`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        viewModel = MainViewModel(repository)
        
        viewModel.onStartTrip()
        
        // Simulating trip distance update from repository
        `when`(repository.getTotalDistance()).thenReturn(10000f) // 10 km
        viewModel.refreshDistance()
        
        // Total KM should be 100 + 10 = 110
        assertEquals("110.00", viewModel.fields[0].value)
        // Fuel consumed: (10 km * 10 l/100km) / 100 = 1 liter. Remaining: 50 - 1 = 49
        assertEquals("49.00", viewModel.fields[1].value)
    }

    @Test
    fun `fuel consumption increases with winter mode`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        viewModel = MainViewModel(repository)
        
        viewModel.toggleWinter(true)
        viewModel.onStartTrip()
        
        // 10 km traveled
        `when`(repository.getTotalDistance()).thenReturn(10000f)
        viewModel.refreshDistance()
        
        // Winter factor is 1.1x. Fuel consumed: (10 * (10 * 1.1)) / 100 = 1.1 liters.
        // Remaining: 50 - 1.1 = 48.9
        assertEquals("48.90", viewModel.fields[1].value)
    }

    @Test
    fun `fuel consumption increases with spring mode`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        viewModel = MainViewModel(repository)
        
        viewModel.toggleSpring(true)
        viewModel.onStartTrip()
        
        `when`(repository.getTotalDistance()).thenReturn(10000f)
        viewModel.refreshDistance()
        
        // Spring factor is also 1.1x. Remaining: 50 - 1.1 = 48.9
        assertEquals("48.90", viewModel.fields[1].value)
    }

    @Test
    fun `fuel consumption increases with both winter and spring mode`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        viewModel = MainViewModel(repository)
        
        viewModel.toggleWinter(true)
        viewModel.toggleSpring(true)
        viewModel.onStartTrip()
        
        `when`(repository.getTotalDistance()).thenReturn(10000f)
        viewModel.refreshDistance()
        
        // Combined factor: 1.0 + 0.1 (winter) + 0.1 (spring) = 1.2x
        // Fuel consumed: (10 * (10 * 1.2)) / 100 = 1.2 liters.
        // Remaining: 50 - 1.2 = 48.8
        assertEquals("48.80", viewModel.fields[1].value)
    }

    @Test
    fun `onFieldChange validates decimal input`() {
        viewModel.onFieldChange(0, "invalid")
        assertEquals("", viewModel.fields[0].value) // Не обновляется если невалидно
        
        viewModel.onFieldChange(0, "123.456") // Слишком много знаков (макс 2)
        assertEquals("", viewModel.fields[0].value)

        viewModel.onFieldChange(0, "123.45") // Валидно
        assertEquals("123.45", viewModel.fields[0].value)
    }

    @Test
    fun `onFieldChange km resets totalDistance`() {
        viewModel.onStartTrip()
        viewModel.addDistance(1000f)
        assertEquals(1000f, viewModel.totalDistance.floatValue, 0.01f)
        
        viewModel.onFieldChange(0, "2000.0")
        assertEquals(0f, viewModel.totalDistance.floatValue, 0.01f)
        verify(repository).saveTotalDistance(0f)
    }

    @Test
    fun `onStopTrip saves all values and stops trip`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        viewModel = MainViewModel(repository)
        
        viewModel.onStartTrip()
        `when`(repository.getTotalDistance()).thenReturn(5000f) // 5 km
        
        viewModel.onStopTrip()
        
        assertFalse(viewModel.isTripStarted.value)
        // Final KM: 100 + 5 = 105
        verify(repository).saveFieldValue("km", 105f)
        // Final Fuel: 50 - (5 * 10 / 100) = 49.5
        verify(repository).saveFieldValue("fuel", 49.5f)
        verify(repository).saveTripStarted(false)
    }
}
