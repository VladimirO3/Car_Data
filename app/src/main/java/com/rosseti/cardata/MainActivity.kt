/**
 * @Author Osetrov.V.V.
 */
package com.rosseti.cardata

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rosseti.cardata.data.SettingsRepository
import com.rosseti.cardata.model.NumericField
import com.rosseti.cardata.ui.theme.CarDataTheme

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
                NumericField("fuelStandart", "Норма расхода топлива", "6.5"),
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
        
        UpdateManager(this).checkForUpdates()

        if (!isGpsEnabled()) {
            showGpsDisabledDialog()
        }

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
        if (!isGpsEnabled()) {
            showGpsDisabledDialog()
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
        if (viewModel.isTripStarted.value && !isGpsEnabled()) {
            onStopClicked()
            showGpsDisabledDialog()
        }
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun showGpsDisabledDialog() {
        AlertDialog.Builder(this)
            .setTitle("GPS выключен")
            .setMessage("Для работы приложения необходимо включить GPS. Пожалуйста, включите его в настройках.")
            .setPositiveButton("Настройки") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Выход") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
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
    val currentTotalKm = fields.getOrNull(0)?.value ?: "0.00"
    val currentRemainingFuel = fields.getOrNull(1)?.value ?: "0.00"
    val currentAvgSpeed = fields.getOrNull(3)?.value ?: "0.00"
    
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Добавляем отступ сверху только для книжной ориентации
            if (!isLandscape) {
                Spacer(modifier = Modifier.height(16.dp))
            }

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
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.1f)),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "● РЕЙС",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.Red,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Блок статистики
            if (isLandscape) {
                Text(
                    text = "Километраж: $currentTotalKm км  |  Топливо: $currentRemainingFuel л  |  Ср. скорость: $currentAvgSpeed км/ч",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column {
                    Text(
                        text = "Общий километраж: $currentTotalKm км",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Остаток топлива: $currentRemainingFuel л",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Средняя скорость: $currentAvgSpeed км/ч",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        fields.forEachIndexed { index, field ->
                            CompactOutlinedTextField(
                                value = field.value,
                                onValueChange = { onFieldChange(index, it) },
                                label = field.label,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = field.id == "avg_speed"
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(24.dp))

                    Column(modifier = Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = onStartClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { Text("Старт") }
                        Button(onClick = onStopClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { Text("Стоп") }
                        
                        Spacer(modifier = Modifier.height(4.dp))

                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MyCheckbox(isWinter, onWinterChange, "Зима (+10%)", fontSize = 12.sp)
                                MyCheckbox(isSpring, onSpringChange, "Весна (+10%)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                // Обычный вид для портрета
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

            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 141.dp))
            if (!isLandscape) {
                Text(
                    text = "© 2026. Все права защищены.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (isLandscape) {
            Text(
                text = "© 2026",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val enabled = true
    val singleLine = true

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(48.dp),
        readOnly = readOnly,
        singleLine = singleLine,
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            color = if (readOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        ),
        keyboardOptions = keyboardOptions,
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                label = { Text(label, fontSize = 10.sp) },
                container = {
                    Box(
                        Modifier.border(
                            width = 1.dp,
                            color = if (readOnly) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outline,
                            shape = OutlinedTextFieldDefaults.shape
                        )
                    )
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            )
        }
    )
}

@Composable
fun MyCheckbox(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, fontSize: androidx.compose.ui.unit.TextUnit = 16.sp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = isChecked, onCheckedChange = onCheckedChange)
        Text(text = label, modifier = Modifier.padding(start = 4.dp), fontSize = fontSize)
    }
}
