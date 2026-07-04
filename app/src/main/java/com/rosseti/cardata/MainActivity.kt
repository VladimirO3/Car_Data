/**
 * @Author Osetrov.V.V.
 */
package com.rosseti.cardata

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import android.widget.Toast.makeText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rosseti.cardata.data.SettingsRepository
import com.rosseti.cardata.model.NumericField
import com.rosseti.cardata.ui.theme.CarDataTheme
import java.util.Locale

/**
 * Предоставляет компонованный предварительный просмотр экрана [MainLocationContent].
 * В этом предварительном просмотре отображается макет интерфейса пользовательского интерфейса с имитационными данными, включая спидометр.
 * Поля топлива и потребления, чтобы проверить внешний вид в инструменте дизайна IDE.
 */
@Preview(showBackground = true)
@Composable
fun PreviewMainLocationContent() {
    CarDataTheme {
        MainLocationContent(
            fields = listOf(
                NumericField("km", "Спидометр(км)", "10.0"),
                NumericField("fuel", "Топливо(л)", "30.0"),
                NumericField("std", "Расхода топлива(л/100)", "6.5"),
                NumericField("avg_speed", "Ср. скорость (км/ч)", "45.0")
            ),
            isWinter = false,
            isSpring = false,
            isTripStarted = true,
            onWinterChange = {},
            onSpringChange = {},
            onFieldChange = { _, _ -> },
            onStartClick = {},
            onStopClick = {}
        )
    }
}

/**
 * Основная точка входа в приложение, ответственная за пользовательский интерфейс и
 * управление жизненным циклом отслеживания поездок.
 *
 * Эта деятельность обрабатывает:
 * - Запросы на разрешение выполнения для служб местоположения (точные, основные и фоновые).
 * - Инициализация [MainViewModel] с его репозиторием посредством настраиваемого [ViewModelProvider.Factory].
 * - Наблюдение за [SharedPreferences], чтобы инициировать обновления пользовательского интерфейса при обновлении данных о расстоянии фоновым сервисом.
 * - Запуск и остановка [LocationService] для фонового GPS-отслеживания.
 * - Рендеринг пользовательского интерфейса на основе Compose для ввода данных о поездке, мониторинга состояния и выбора зимнего режима.
 *
 * @see LocationService
 * @see MainViewModel
 * @author Osetrov V.V.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(SettingsRepository(applicationContext)) as T
            }
        }
    }

    /**
     * Слушатель для изменений в [SharedPreferences], которые инициируют обновление интерфейса пользователя при изменении расстояния поездки.
     * В частности, он следит за ключом «total_distance», чтобы [MainViewModel] оставался синхронизированным.
     */
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "total_distance") {
            runOnUiThread { viewModel.refreshDistance() }
        }
    }

    /**
     * Обработчик результата запроса разрешений на доступ к точному и примерному местоположению.
     *
     * Если разрешение [Manifest.permission.ACCESS_FINE_LOCATION] предоставлено,
     * инициируется проверка разрешений на фоновое использование местоположения.
     * В противном случае пользователю выводится уведомление о необходимости разрешений.
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            checkBackgroundPermission()
        } else {
            makeText(this, "Требуется разрешение на местоположение", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Обработчик результата запроса разрешения на доступ к местоположению в фоновом режиме.
     *
     * Начиная с Android 10 (API 29), для отслеживания координат, когда приложение свернуто,
     * требуется отдельное подтверждение. Независимо от выбора пользователя (предоставлено
     * разрешение или нет), предпринимается попытка запуска [LocationService], при этом
     * в случае отказа выводится соответствующее уведомление.
     */
    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        startTripService()
        if (!isGranted) {
            makeText(this, "Фоновый доступ не предоставлен", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Вызывается при создании активности. Инициализирует настройки приложения,
     * регистрирует слушателя изменений в [SharedPreferences] для обновления данных о расстоянии
     * и устанавливает пользовательский интерфейс с помощью Jetpack Compose.
     *
     * @param savedInstanceState Если активность воссоздается из предыдущего сохраненного состояния, это этот Bundle.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        setContent {
            CarDataTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainLocationScreen(viewModel, ::onStartClicked, ::onStopClicked)
                }
            }
        }
    }

    /**
     * Обрабатывает нажатие кнопки «Старт» для начала отслеживания поездки.
     *
     * Метод проверяет наличие разрешения на доступ к точному местоположению ([Manifest.permission.ACCESS_FINE_LOCATION]).
     * Если разрешение предоставлено, переходит к проверке фонового доступа через [checkBackgroundPermission].
     * В противном случае запрашивает необходимые разрешения (точное и примерное местоположение)
     * через [requestPermissionLauncher].
     */
    private fun onStartClicked() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            checkBackgroundPermission()
        } else {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    /**
     * Проверяет наличие разрешения на доступ к местоположению в фоновом режиме.
     *
     * Начиная с Android 10 (API 29), для получения координат в фоновом режиме требуется
     * явное разрешение [Manifest.permission.ACCESS_BACKGROUND_LOCATION].
     * - Если устройство работает на версии ниже Android Q, сразу запускает службу.
     * - Если разрешение уже предоставлено, инициирует запуск [startTripService].
     * - В противном случае запускает системный диалог запроса через [backgroundPermissionLauncher].
     */
    private fun checkBackgroundPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startTripService()
        } else {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    /**
     * Запускает процесс отслеживания поездки.
     *
     * Метод выполняет следующие действия:
     * 1. Уведомляет [MainViewModel] о начале поездки для инициализации начальных данных.
     * 2. Создает и запускает [LocationService] как Foreground Service для обеспечения
     * непрерывного получения координат местоположения в фоновом режиме.
     */
    private fun startTripService() {
        viewModel.onStartTrip()
        val intent = Intent(this, LocationService::class.java)
        startForegroundService(intent)
    }

    /**
     * Обрабатывает нажатие кнопки «Стоп» для завершения отслеживания поездки.
     *
     * Данный метод выполняет следующие действия:
     * 1. Вызывает [MainViewModel.onStopTrip] для сохранения финальных данных и завершения логики поездки.
     * 2. Останавливает фоновую службу [LocationService], прекращая получение обновлений местоположения.
     */
    private fun onStopClicked() {
        viewModel.onStopTrip()
        stopService(Intent(this, LocationService::class.java))
    }

    /**
     * Вызывается, когда активность переходит в состояние взаимодействия с пользователем.
     *
     * Переопределяет стандартное поведение для принудительного обновления данных полей
     * через [viewModel], обеспечивая актуальность отображаемой информации (пробега и топлива)
     * при возврате пользователя в приложение.
     */
    override fun onResume() {
        super.onResume()
        viewModel.loadFields()
    }

    override fun onDestroy() {
        super.onDestroy()
        getSharedPreferences("AppPrefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}

/**
 * Многоуровневый компоновочный файл, который служит основным экраном для отслеживания местоположения и ввода данных.
 */
@Composable
fun MainLocationScreen(
    viewModel: MainViewModel, 
    onStart: () -> Unit, 
    onStop: () -> Unit
) {
    val context = LocalContext.current
    MainLocationContent(
        fields = viewModel.fields,
        isWinter = viewModel.isWinter.value,
        onWinterChange = { viewModel.toggleWinter(it) },
        isSpring = viewModel.isSpring.value,
        onSpringChange = { viewModel.toggleSpring(it) },
        isTripStarted = viewModel.isTripStarted.value,
        onFieldChange = viewModel::onFieldChange,
        onStartClick = {
            onStart()
            makeText(context, "Рейс запущен", Toast.LENGTH_SHORT).show()
        },
        onStopClick = {
            onStop()
            makeText(context, "Рейс завершен!", Toast.LENGTH_LONG).show()
        }
    )
}

/**
 * Основной компонент интерфейса пользователя, обрабатывающий главный экран GPS-трекер.
 *
 * Эта компоновка адаптируется как к портретным, так и к ландшафтным ориентациям, обеспечивая:
 * - Краткое представление текущего пробега и оставшегося топлива.
 * - Поля ввода для числовых данных (спидометр, топливо, нормы потребления).
 * - Переключатель зимнего режима для корректировки расчёта топлива.
 * - Кнопки управления для запуска и остановки службы отслеживания поездок.
 * - Информация о разработчиках и авторском праве.
 *
 * @param fields Список объектов [NumericField], содержащих данные для полей ввода.
 * @param isWinter Boolean A, указывающий на активность зимнего режима (+10% расхода топлива).
 * @param onWinterChange Callback активируется при переключении зимнего режима.
 * @param onFieldChange Callback инициируется при редактировании значения числового поля.
 * Предоставление индекса поля и нового значения строки.
 * @param onStartClick Callback выполняется при нажатии кнопки «Start».
 * @param onStopClick Callback выполняется при нажатии кнопки «Стоп».
 */
@SuppressLint("DefaultLocale")
@Composable
fun MainLocationContent(
    fields: List<NumericField>,
    isWinter: Boolean,
    isSpring: Boolean,
    isTripStarted: Boolean,
    onWinterChange: (Boolean) -> Unit,
    onSpringChange: (Boolean) -> Unit,
    onFieldChange: (Int, String) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentTotalKm = fields.getOrNull(0)?.value?.toFloatOrNull() ?: 0f
    val currentRemainingFuel = fields.getOrNull(1)?.value?.toFloatOrNull() ?: 0f
    val currentAvgSpeed = fields.getOrNull(3)?.value?.toFloatOrNull() ?: 0f
    
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TrackLit",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            if (isTripStarted) {
                Spacer(modifier = Modifier.width(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "● РЕЙС",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Блок статистики
        if (isLandscape) {
            Text(
                text = String.format(Locale.US, "Километраж: %.2f км  |  Топливо: %.2f л  |  Ср. скорость: %.2f км/ч", 
                                    currentTotalKm, currentRemainingFuel, currentAvgSpeed),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column {
                Text(
                    text = String.format(Locale.US, "Общий километраж: %.2f км", currentTotalKm),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = String.format(Locale.US, "Остаток топлива: %.2f л", currentRemainingFuel),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = String.format(Locale.US, "Средняя скорость: %.2f км/ч", currentAvgSpeed),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fields.forEachIndexed { index, field ->
                        OutlinedTextField(
                            value = field.value,
                            onValueChange = { onFieldChange(index, it) },
                            label = { Text(field.label) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = field.id == "avg_speed"
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MyCheckbox(isWinter, onWinterChange, "Зима (+10%)")
                        Spacer(modifier = Modifier.width(8.dp))
                        MyCheckbox(isSpring, onSpringChange, "Весна (+10%)")
                    }
                    Button(onClick = onStartClick, modifier = Modifier.fillMaxWidth()) { Text("Старт") }
                    Button(onClick = onStopClick, modifier = Modifier.fillMaxWidth()) { Text("Стоп") }
                }
            }
        } else {
            fields.forEachIndexed { index, field ->
                OutlinedTextField(
                    value = field.value,
                    onValueChange = { onFieldChange(index, it) },
                    label = { Text(field.label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = field.id == "avg_speed"
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MyCheckbox(isWinter, onWinterChange, "Зима (+10%)")
                Spacer(modifier = Modifier.width(16.dp))
                MyCheckbox(isSpring, onSpringChange, "Весна (+10%)")
            }
            Button(onClick = onStartClick, modifier = Modifier.fillMaxWidth()) { Text("Старт") }
            Button(onClick = onStopClick, modifier = Modifier.fillMaxWidth()) { Text("Стоп") }
        }

        Spacer(modifier = Modifier.height(125.dp))
        Text(
            text = "© 2026. Все права защищены.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MyCheckbox(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = isChecked, onCheckedChange = onCheckedChange)
        Text(text = label, modifier = Modifier.padding(start = 4.dp))
    }
}
