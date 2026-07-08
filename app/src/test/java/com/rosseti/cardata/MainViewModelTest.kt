package com.rosseti.cardata

import com.rosseti.cardata.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any

/**
 * Единичные тесты для класса [MainViewModel].
 *
 * Проверяет бизнес-логику: одометр, расчет топлива (включая простой и сезонный),
 * расчет максимальной скорости и валидацию полей.
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
        `when`(repository.getMaxSpeed()).thenReturn("0")
        `when`(repository.getCurrentSpeed()).thenReturn(0f)
        `when`(repository.getStartTime()).thenReturn(0L)
        `when`(repository.getIsRussian()).thenReturn(false) // Default to English for tests
        
        viewModel = MainViewModel(repository)
    }

    @Test
    fun `init loads fields correctly with English labels`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        `when`(repository.getMaxSpeed()).thenReturn("120")
        `when`(repository.getIsRussian()).thenReturn(false)
        
        // Re-init to trigger loading
        viewModel = MainViewModel(repository)
        
        assertEquals("Speedometer (km)", viewModel.fields[0].label)
        assertEquals("100.00", viewModel.fields[0].value)
        
        assertEquals("Trip Distance (km)", viewModel.fields[1].label)
        assertEquals("", viewModel.fields[1].value) // 0f formatted as ""
        
        assertEquals("Remaining Fuel", viewModel.fields[2].label)
        assertEquals("50.00", viewModel.fields[2].value)
        
        assertEquals("Fuel Consumption Rate", viewModel.fields[3].label)
        assertEquals("10.00", viewModel.fields[3].value)
        
        assertEquals("Max Speed (km/h)", viewModel.fields[4].label)
        assertEquals("120", viewModel.fields[4].value)
    }

    @Test
    fun `init loads fields correctly with Russian labels`() {
        `when`(repository.getIsRussian()).thenReturn(true)
        
        viewModel = MainViewModel(repository)
        
        assertEquals("Спидометр (км)", viewModel.fields[0].label)
        assertEquals("Дистанция (км)", viewModel.fields[1].label)
        assertEquals("Остаток топлива", viewModel.fields[2].label)
        assertEquals("Норма расхода", viewModel.fields[3].label)
        assertEquals("Макс. скорость (км/ч)", viewModel.fields[4].label)
    }

    @Test
    fun `onStartTrip resets session data and saves base values`() {
        viewModel.onFieldChange(0, "100.0")
        viewModel.onFieldChange(2, "50.0")
        
        viewModel.onStartTrip()
        
        assertTrue(viewModel.isTripStarted.value)
        verify(repository).saveTripStarted(true)
        verify(repository).saveTotalDistance(0f)
        verify(repository).saveMaxSpeed("0")
        verify(repository).saveFieldValue("km", 100.0f)
        verify(repository).saveFieldValue("fuel", 50.0f)
    }

    @Test
    fun `refreshDistance updates UI fields correctly when fuel standard is provided`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        viewModel = MainViewModel(repository)
        
        viewModel.onStartTrip()
        
        // Simulating trip distance update (10 km)
        `when`(repository.getTotalDistance()).thenReturn(10000f)
        viewModel.refreshDistance()
        
        // Total KM: 100 + 10 = 110
        assertEquals("110.00", viewModel.fields[0].value)
        // Trip Distance: 10
        assertEquals("10.00", viewModel.fields[1].value)
        // Fuel consumed: (10 km * 10 l/100km) / 100 = 1 liter. Remaining: 50 - 1 = 49
        assertEquals("49.00", viewModel.fields[2].value)
    }

    @Test
    fun `refreshDistance does NOT update fuel if fuel standard is empty`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(0f)
        viewModel = MainViewModel(repository)
        
        // Set standard to empty
        viewModel.onFieldChange(3, "") 
        viewModel.onStartTrip()
        
        // 10 km traveled
        `when`(repository.getTotalDistance()).thenReturn(10000f)
        viewModel.refreshDistance()
        
        assertEquals("110.00", viewModel.fields[0].value)
        assertEquals("50.00", viewModel.fields[2].value) // Fuel remains unchanged
    }

    @Test
    fun `max speed is updated only if current speed is higher`() {
        viewModel.onStartTrip()
        
        // Current speed 80 km/h
        `when`(repository.getCurrentSpeed()).thenReturn(80.4f)
        `when`(repository.getMaxSpeed()).thenReturn("0")
        
        viewModel.refreshDistance()
        verify(repository).saveMaxSpeed("80") // Integer expected
        
        // Current speed 60 km/h (should not update record)
        `when`(repository.getMaxSpeed()).thenReturn("80")
        `when`(repository.getCurrentSpeed()).thenReturn(60f)
        
        viewModel.refreshDistance()
        // verify saveMaxSpeed was called only once (with "80")
        verify(repository, times(1)).saveMaxSpeed("80")
    }

    @Test
    fun `seasonal factors are additive`() {
        `when`(repository.getFieldValue("km")).thenReturn(0f)
        `when`(repository.getFieldValue("fuel")).thenReturn(100f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        viewModel = MainViewModel(repository)
        
        viewModel.toggleWinter(true) // +10%
        viewModel.toggleSpring(true) // +10% -> Total +20%
        
        viewModel.onStartTrip()
        `when`(repository.getTotalDistance()).thenReturn(100000f) // 100 km
        
        viewModel.refreshDistance()
        
        // 100 km * (10 * 1.2) / 100 = 12 L. Remaining: 100 - 12 = 88
        assertEquals("88.00", viewModel.fields[2].value)
    }

    @Test
    fun `onFieldChange validates decimal input correctly`() {
        viewModel.onFieldChange(0, "invalid")
        assertEquals("", viewModel.fields[0].value)
        
        viewModel.onFieldChange(0, "123.456") // Too many decimals
        assertEquals("", viewModel.fields[0].value)

        viewModel.onFieldChange(0, "123.45")
        assertEquals("123.45", viewModel.fields[0].value)
    }
}
