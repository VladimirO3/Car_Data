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

/**
 * Единичные тесты для класса [MainViewModel].
 *
 * Проверяет бизнес-логику, включая расчет пробега, топлива и максимальной скорости.
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
        `when`(repository.getMaxSpeed()).thenReturn("0.00")
        `when`(repository.getCurrentSpeed()).thenReturn(0f)
        
        viewModel = MainViewModel(repository)
    }

    @Test
    fun `init loads fields from repository`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        `when`(repository.getMaxSpeed()).thenReturn("120.50")
        
        // Re-init to trigger loading
        viewModel = MainViewModel(repository)
        
        assertEquals("100.00", viewModel.fields[0].value)
        assertEquals("50.00", viewModel.fields[1].value)
        assertEquals("10.00", viewModel.fields[2].value)
        assertEquals("120.50", viewModel.fields[3].value)
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
        verify(repository).saveMaxSpeed("0.00")
    }

    @Test
    fun `addDistance updates UI fields correctly when fuel standard is present`() {
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
    fun `addDistance does NOT update fuel if fuel standard is empty`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(0f)
        viewModel = MainViewModel(repository)
        
        // Simulating trip start with empty fuel standard
        viewModel.onFieldChange(2, "") 
        viewModel.onStartTrip()
        
        `when`(repository.getTotalDistance()).thenReturn(10000f) // 10 km
        viewModel.refreshDistance()
        
        assertEquals("110.00", viewModel.fields[0].value)
        assertEquals("50.00", viewModel.fields[1].value) // Fuel remains unchanged
    }

    @Test
    fun `max speed is updated if current speed is higher`() {
        viewModel.onStartTrip()
        
        `when`(repository.getCurrentSpeed()).thenReturn(60.0f)
        `when`(repository.getMaxSpeed()).thenReturn("0.00")
        
        viewModel.refreshDistance()
        
        verify(repository).saveMaxSpeed("60.00")
    }

    @Test
    fun `onStopTrip saves all values and stops trip`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        `when`(repository.getMaxSpeed()).thenReturn("85.50")
        viewModel = MainViewModel(repository)
        
        viewModel.onStartTrip()
        `when`(repository.getTotalDistance()).thenReturn(5000f) // 5 km
        
        viewModel.onStopTrip()
        
        assertFalse(viewModel.isTripStarted.value)
        verify(repository).saveFieldValue("km", 105f)
        verify(repository).saveFieldValue("fuel", 49.5f)
        verify(repository).saveMaxSpeed("85.50")
        verify(repository).saveTripStarted(false)
    }
}
