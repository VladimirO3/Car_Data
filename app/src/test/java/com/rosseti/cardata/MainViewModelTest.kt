package com.rosseti.cardata

import com.rosseti.cardata.data.SettingsRepository
import org.junit.Assert.assertEquals
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
        
        viewModel = MainViewModel(repository)
    }

    @Test
    fun `init loads fields from repository`() {
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        
        // Re-init to trigger loading
        viewModel = MainViewModel(repository)
        
        assertEquals("100.00", viewModel.fields[0].value)
        assertEquals("50.00", viewModel.fields[1].value)
    }

    @Test
    fun `onStartTrip sets isTripStarted to true`() {
        viewModel.onStartTrip()
        assertTrue(viewModel.isTripStarted.value)
    }

    @Test
    fun `addDistance updates distance when trip started`() {
        viewModel.onStartTrip()
        viewModel.addDistance(1000f) // 1 km
        
        assertEquals(1000f, viewModel.totalDistance.floatValue, 0.01f)
        verify(repository).saveTotalDistance(1000f)
    }

    @Test
    fun `addDistance does not update when trip not started`() {
        viewModel.addDistance(1000f)
        assertEquals(0f, viewModel.totalDistance.floatValue, 0.01f)
    }

    @Test
    fun `onFieldChange updates field value and saves to repository`() {
        viewModel.onFieldChange(0, "123.45")
        
        assertEquals("123.45", viewModel.fields[0].value)
        verify(repository).saveFieldValue("km", 123.45f)
    }

    @Test
    fun `onStopTrip resets totalDistance and saves final values`() {
        // Настройка начального состояния
        `when`(repository.getFieldValue("km")).thenReturn(100f)
        `when`(repository.getFieldValue("fuel")).thenReturn(50f)
        `when`(repository.getFieldValue("fuelStandart")).thenReturn(10f)
        viewModel = MainViewModel(repository)
        
        viewModel.onStartTrip()
        viewModel.addDistance(10000f) // 10 км, должно потреблять 1 литр (10 * 10 / 100)
        
        viewModel.onStopTrip()
        
        assertEquals(0f, viewModel.totalDistance.floatValue, 0.01f)
        // Final KM: 100 + 10 = 110
        // Final Fuel: 50 - 1 = 49
        verify(repository).saveFieldValue("km", 110f)
        verify(repository).saveFieldValue("fuel", 49f)
        verify(repository).saveTotalDistance(0f)
    }
}
