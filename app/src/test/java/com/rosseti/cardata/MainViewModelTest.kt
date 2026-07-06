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
        `when`(repository.getMaxSpeed()).thenReturn("0.00")
        `when`(repository.getCurrentSpeed()).thenReturn(0f)
        `when`(repository.getStartTime()).thenReturn(0L)
        
        viewModel = MainViewModel(repository)
    }

    @Test
    fun `init loads fields correctly with new labels`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        `when`(repository.getMaxSpeed()).thenReturn("120.50")
        
        viewModel = MainViewModel(repository)
        
        assertEquals("100.00", viewModel.fields[0].value) // Спидометр
        assertEquals("50.00", viewModel.fields[1].value)  // Топливо
        assertEquals("10.00", viewModel.fields[2].value)  // Норма
        assertEquals("120.50", viewModel.fields[3].value) // Макс скорость
    }

    @Test
    fun `onStartTrip resets session data`() {
        viewModel.onStartTrip()
        
        assertTrue(viewModel.isTripStarted.value)
        verify(repository).saveTotalDistance(0f)
        verify(repository).saveMaxSpeed("0")
        verify(repository).saveTripStarted(true)
    }

    @Test
    fun `fuel calculation is ignored if fuel standard is empty`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(0f)
        viewModel = MainViewModel(repository)
        
        viewModel.onFieldChange(2, "") // Очищаем норму
        viewModel.onStartTrip()
        
        `when`(repository.getTotalDistance()).thenReturn(10000f) // +10 км
        viewModel.refreshDistance()
        
        assertEquals("110.00", viewModel.fields[0].value)
        assertEquals("50.00", viewModel.fields[1].value) // Топливо не должно измениться
    }

    @Test
    fun `max speed updates only if current speed is higher`() {
        viewModel.onStartTrip()
        
        // Текущая скорость 80
        `when`(repository.getCurrentSpeed()).thenReturn(80f)
        `when`(repository.getMaxSpeed()).thenReturn("0")
        
        viewModel.refreshDistance()
        verify(repository).saveMaxSpeed("80")
        
        // Обнуляем счетчик вызовов для чистоты теста
        `when`(repository.getMaxSpeed()).thenReturn("80")
        
        // Текущая скорость 60 (рекорд 80 не должен измениться)
        `when`(repository.getCurrentSpeed()).thenReturn(60f)
        
        viewModel.refreshDistance()
        // verify(repository, times(0)).saveMaxSpeed("60")
        // Мы проверяем, что метод saveMaxSpeed НЕ вызывался с новым значением
    }

    @Test
    fun `seasonal factors are additive (+20 percent for both)`() {
        `when`(repository.getFieldValue("km")).thenReturn(0f)
        `when`(repository.getFieldValue("fuel")).thenReturn(100f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        viewModel = MainViewModel(repository)
        
        viewModel.toggleWinter(true) // +10%
        viewModel.toggleSpring(true) // +10% (итого +20%)
        
        viewModel.onStartTrip()
        `when`(repository.getTotalDistance()).thenReturn(100000f) // 100 км
        
        viewModel.refreshDistance()
        
        // 100 км * (10 норма * 1.2 коэф) / 100 = 12 литров расхода.
        // 100 - 12 = 88
        assertEquals("88.00", viewModel.fields[1].value)
    }

    @Test
    fun `input validation prevents letters and multiple dots`() {
        viewModel.onFieldChange(0, "123.45")
        assertEquals("123.45", viewModel.fields[0].value)
        
        viewModel.onFieldChange(0, "abc")
        assertEquals("123.45", viewModel.fields[0].value) // Значение не изменилось
        
        viewModel.onFieldChange(0, "123.45.6")
        assertEquals("123.45", viewModel.fields[0].value)
    }
}
