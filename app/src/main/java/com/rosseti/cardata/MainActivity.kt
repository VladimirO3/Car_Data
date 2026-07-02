/**
 * @Author Osetrov.V.V.
 */
package com.rosseti.cardata

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.widget.Toast.makeText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
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
            onSpringChange = {},
            onWinterChange = {},
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
 * - Наблюдение за [android.content.SharedPreferences], чтобы инициировать обновления пользовательского интерфейса при обновлении данных о расстоянии фоновым сервисом.
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
     * Слушатель для изменений в [android.content.SharedPreferences], которые инициируют обновление интерфейса пользователя при изменении расстояния поездки.
     * В частности, он следит за ключом «total_distance», чтобы [MainViewModel] оставался синхронизированным.
     */
    private val preferenceChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
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
        if (isGranted) {
            startTripService()
        } else {
            makeText(this, "Фоновый доступ не предоставлен", Toast.LENGTH_SHORT).show()
            startTripService()
        }
    }

    /**
     * Вызывается при создании активности. Инициализирует настройки приложения,
     * регистрирует слушателя изменений в [android.content.SharedPreferences] для обновления данных о расстоянии
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
     * Метод сначала проверяет, включен ли GPS на устройстве. Если нет, перенаправляет в настройки.
     * Затем проверяет наличие разрешения на доступ к точному местоположению ([Manifest.permission.ACCESS_FINE_LOCATION]).
     * Если разрешение предоставлено, переходит к проверке фонового доступа через [checkBackgroundPermission].
     * В противном случае запрашивает необходимые разрешения (точное и примерное местоположение)
     * через [requestPermissionLauncher].
     */
	private fun onStartClicked() {
        if (!isLocationEnabled()) {
            makeText(this, "Пожалуйста, включите GPS в настройках", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            checkBackgroundPermission()
        } else {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
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
        isSpring = viewModel.isSpring.value,
        isWinter = viewModel.isWinter.value,
        onSpringChange = { viewModel.toggleSpring(it) },
        onWinterChange = { viewModel.toggleWinter(it) },
        onFieldChange = viewModel::onFieldChange,
        onStartClick = {
            onStart()
            val kmValue = viewModel.fields.getOrNull(0)?.value ?: ""
            if (kmValue.isNotEmpty()) {
                makeText(context, "Рейс запущен", Toast.LENGTH_SHORT).show()
            }
        },
        onStopClick = {
            onStop()
            makeText(context, "Рейс завершен!", Toast.LENGTH_LONG).show()
        }
    )
}

/**
 * Основной компонент интерфейса пользователя, отвечающий за отображение главного
 */
@SuppressLint("DefaultLocale")
@Composable
fun MainLocationContent(
    fields: List<NumericField>,
    isWinter: Boolean,
    isSpring: Boolean,
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
    
    val scrollStateLeft = rememberScrollState()
    val scrollStateRight = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                Text(
                    text = "TrackLit",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = String.format(Locale.US, "Километраж: %.2f км | Топливо: %.2f л | Ср. скорость: %.2f км/ч", 
                                        currentTotalKm, currentRemainingFuel, currentAvgSpeed),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(scrollStateLeft),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                fields.forEachIndexed { index, field ->
                                    key(field.id) {
                                        OutlinedTextField(
                                            value = field.value,
                                            onValueChange = { onFieldChange(index, it) },
                                            label = { Text(field.label, fontSize = 9.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.width(260.dp).padding(vertical = 1.dp),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
                                            readOnly = field.id == "avg_speed",
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(scrollStateRight),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onStartClick,
                            modifier = Modifier.width(240.dp)
                        ) { Text("Старт") }
                        Button(
                            onClick = onStopClick,
                            modifier = Modifier.width(240.dp)
                        ) { Text("Стоп") }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MyCheckboxScreenCompact(isWinter, onWinterChange, "Зима (+10%)")
                                MyCheckboxScreenCompact(isSpring, onSpringChange, "Весна (+10%)")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(80.dp))
                        Text(
                            text = "Разработано: Осетров В.В.\n© 2026",
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(scrollStateLeft),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "TrackLit",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = String.format(Locale.US, "Общий километраж: %.2f км\nОстаток топлива: %.2f л\nСредняя скорость: %.2f км/ч", 
                                        currentTotalKm, currentRemainingFuel, currentAvgSpeed),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Row {
                    MyCheckboxScreen(isWinter, onWinterChange)
                    MyCheckboxScreenSpring(isSpring, onSpringChange)
                }
                fields.forEachIndexed { index, field ->
                    key(field.id) {
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
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStartClick, modifier = Modifier.fillMaxWidth()) { Text("Старт") }
                Button(onClick = onStopClick, modifier = Modifier.fillMaxWidth()) { Text("Стоп") }
                Spacer(modifier = Modifier.height(100.dp))
                Text(
                    text = "Разработано: Осетров В.В.\n© 2026. Все права защищены.",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Иконка в правом верхнем углу
        IconButton(
            onClick = { /* Действие при нажатии */ },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Настройки",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Отображает строку с флажком (Checkbox) для выбора зимнего режима.
 * Данный режим предполагает корректировку расхода топлива (например, +10%).
 *
 * @param isChecked Текущее состояние флажка (выбрано или нет).
 * @param onCheckedChange Лямбда-выражение, вызываемое при изменении состояния флажка.
 */
@Composable
fun MyCheckboxScreen(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = "Зима (+10%)",
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
@Composable
fun MyCheckboxScreenSpring(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = "Весна (+10%)",
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/**
 * Компактная версия чекбокса для альбомной ориентации.
 */
@Composable
fun MyCheckboxScreenCompact(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(0.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}
