/**
 * @Author Osetrov.V.V.
 * © 2026 Osetrov V.V. Все права защищены.
 */
package com.rosseti.cardata

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.widget.Toast.makeText
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.rosseti.cardata.data.SettingsRepository
import com.rosseti.cardata.model.NumericField
import com.rosseti.cardata.ui.theme.CarDataTheme
import java.util.Locale

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
     * Слушатель для изменений в [SharedPreferences], которые инициируют обновление интерфейса пользователя.
     * Следит за пройденным расстоянием и максимальной скоростью.
     */
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "total_distance" || key == "last_max_speed" || key == "current_speed") {
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
            makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
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
            makeText(this, "Background access not granted", Toast.LENGTH_SHORT).show()
            startTripService()
        }
    }

    /**
     * Обработчик результата включения GPS через системный диалог.
     */
    private val gpsSettingLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Пользователь включил GPS, продолжаем стандартную проверку разрешений
            checkLocationPermissions()
        } else {
            makeText(this, "GPS must be enabled for the app to function", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Слушатель для отслеживания изменений состояния GPS.
     */
    private val gpsStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                if (!isLocationEnabled() && viewModel.isTripStarted.value) {
                    onStopClicked()
                    showGpsRequiredDialog()
                }
            }
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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(gpsStatusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(gpsStatusReceiver, filter)
        }

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
     * Метод использует Google Play Services для проверки настроек местоположения.
     * Если GPS выключен, пользователю будет показан системный диалог для его включения.
     * Если GPS включен, переходит к проверке разрешений.
     */
    private fun onStartClicked() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // Настройки местоположения удовлетворяют требованиям, проверяем разрешения
            checkLocationPermissions()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                    gpsSettingLauncher.launch(intentSenderRequest)
                } catch (sendEx: Exception) {
                    Log.e("MainActivity", "Error launching GPS setting dialog", sendEx)
                }
            } else {
                // Если системный диалог недоступен, используем старый способ с переходом в настройки
                if (!isLocationEnabled()) {
                    makeText(this, "Please enable GPS in settings", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
        }
    }

    /**
     * Выполняет последовательную проверку разрешений на доступ к местоположению.
     */
    private fun checkLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // На Android 13+ также проверяем уведомления, но не блокируем запуск без них
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(permissions.toTypedArray())
            } else {
                checkBackgroundPermission()
            }
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
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
        if (!isLocationEnabled() && viewModel.isTripStarted.value) {
            onStopClicked()
            showGpsRequiredDialog()
        }
    }

    private fun showGpsRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("GPS is Off")
            .setMessage("GPS must be enabled for the app to function properly. Trip stopped.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(gpsStatusReceiver)
        getSharedPreferences("AppPrefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}

/**
 * Перечисление экранов приложения.
 */
enum class Screen {
    MAIN, HISTORY, INSTRUCTION
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
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }

    if (currentScreen == Screen.HISTORY) {
        BackHandler { currentScreen = Screen.MAIN }
        HistoryScreen(
            history = viewModel.tripHistory,
            isRussian = viewModel.isRussian.value,
            onBack = { currentScreen = Screen.MAIN },
            onShare = { shareTripHistory(context) },
            onClear = { viewModel.clearHistory() }
        )
    } else if (currentScreen == Screen.INSTRUCTION) {
        BackHandler { currentScreen = Screen.MAIN }
        InstructionScreen(
            isRussian = viewModel.isRussian.value,
            onBack = { currentScreen = Screen.MAIN }
        )
    } else {
        MainLocationContent(
            fields = viewModel.fields,
            isSpring = viewModel.isSpring.value,
            isWinter = viewModel.isWinter.value,
            isTripStarted = viewModel.isTripStarted.value,
            isRussian = viewModel.isRussian.value,
            onSpringChange = { viewModel.toggleSpring(it) },
            onWinterChange = { viewModel.toggleWinter(it) },
            onLanguageToggle = { viewModel.toggleLanguage() },
            onFieldChange = viewModel::onFieldChange,
            onHistoryClick = { currentScreen = Screen.HISTORY },
            onInstructionClick = { currentScreen = Screen.INSTRUCTION },
            onStartClick = {
                onStart()
                val kmValue = viewModel.fields.getOrNull(0)?.value ?: ""
                if (kmValue.isNotEmpty()) {
                    val msg = if (viewModel.isRussian.value) "Поездка начата" else "Trip started"
                    makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onStopClick = {
                onStop()
                val msg = if (viewModel.isRussian.value) "Поездка завершена!" else "Trip completed!"
                makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        )
    }
}

/**
 * Экран истории поездок.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<String>,
    isRussian: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit
) {
    val title = if (isRussian) "История рейсов" else "Trip History"
    val emptyMsg = if (isRussian) "История пока пуста" else "History is empty"
    val clearTitle = if (isRussian) "Очистить историю?" else "Clear history?"
    val clearMsg = if (isRussian) "Это действие нельзя отменить." else "This action cannot be undone."
    val confirmText = if (isRussian) "Удалить" else "Clear"
    val cancelText = if (isRussian) "Отмена" else "Cancel"
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
        )
    )
    
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(clearTitle) },
            text = { Text(clearMsg) },
            confirmButton = {
                Button(onClick = {
                    onClear()
                    showDeleteDialog = false
                }) { Text(confirmText) }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) { Text(cancelText) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (history.isNotEmpty()) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear History")
                            }
                        }
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(emptyMsg, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(history) { record ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = record,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.sp
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionScreen(
    isRussian: Boolean,
    onBack: () -> Unit
) {
    val title = if (isRussian) "Инструкция" else "Instructions"
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
        )
    )

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InstructionItem(
                    number = "1",
                    title = if (isRussian) "Подготовка к рейсу" else "Preparation",
                    text = if (isRussian) 
                        "Перед началом поездки введите текущие показания одометра и количество топлива в баке. Также укажите норму расхода вашего автомобиля." 
                        else "Before starting, enter current odometer reading, fuel amount, and your vehicle's fuel consumption rate."
                )
                InstructionItem(
                    number = "2",
                    title = if (isRussian) "Запуск отслеживания" else "Start Tracking",
                    text = if (isRussian) 
                        "Нажмите кнопку «Старт». Приложение запросит доступ к GPS. После этого поля ввода заблокируются, и начнется автоматический расчет." 
                        else "Click 'Start'. The app will request GPS access. Fields will lock, and automatic calculation begins."
                )
                InstructionItem(
                    number = "3",
                    title = if (isRussian) "Сезонные режимы" else "Seasonal Factors",
                    text = if (isRussian) 
                        "Если на улице зима или весна, отметьте соответствующие пункты. Это добавит +10% к расчету расхода топлива для большей точности." 
                        else "Check Winter or Spring boxes if applicable. This adds +10% to fuel calculation for better accuracy."
                )
                InstructionItem(
                    number = "4",
                    title = if (isRussian) "Работа в фоне" else "Background Mode",
                    text = if (isRussian) 
                        "Вы можете свернуть приложение. В шторке уведомлений будет отображаться актуальная скорость и пройденный путь в реальном времени." 
                        else "You can minimize the app. Current speed and trip distance will be shown in the notification drawer."
                )
                InstructionItem(
                    number = "5",
                    title = if (isRussian) "Завершение рейса" else "End Trip",
                    text = if (isRussian) 
                        "По прибытии нажмите «Стоп». Данные будут сохранены в историю, а поля ввода снова станут доступны для корректировки." 
                        else "Click 'Stop' upon arrival. Data will be saved to history, and input fields will unlock."
                )
                InstructionItem(
                    number = "6",
                    title = if (isRussian) "Дополнительно" else "Extra Features",
                    text = if (isRussian) 
                        "Используйте кнопку с иконкой локации, чтобы отправить свои точные координаты через мессенджеры." 
                        else "Use the location icon button to share your precise coordinates via messaging apps."
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun InstructionItem(number: String, title: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = number,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("DefaultLocale", "UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainLocationContent(
    fields: List<NumericField>,
    isWinter: Boolean,
    isSpring: Boolean,
    isTripStarted: Boolean,
    isRussian: Boolean,
    onWinterChange: (Boolean) -> Unit,
    onSpringChange: (Boolean) -> Unit,
    onLanguageToggle: () -> Unit,
    onFieldChange: (Int, String) -> Unit,
    onHistoryClick: () -> Unit,
    onInstructionClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentTotalKm = fields.getOrNull(0)?.value?.toFloatOrNull() ?: 0f
    val currentRemainingFuel = fields.getOrNull(2)?.value?.toFloatOrNull() ?: 0f
    val speed = fields.getOrNull(5)?.value?.toFloatOrNull() ?: 0f

    val localContext = LocalContext.current
    val scrollStateLeft = rememberScrollState()
    val scrollStateRight = rememberScrollState()

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
        )
    )

    val tripLabel = if (isRussian) "● РЕЙС" else "● TRIP"
    val startLabel = if (isRussian) "Старт" else "Start"
    val stopLabel = if (isRussian) "Стоп" else "Stop"
    val winterLabel = if (isRussian) "Зима (+10%)" else "Winter (+10%)"
    val springLabel = if (isRussian) "Весна (+10%)" else "Spring (+10%)"
    val copyrightLabel = if (isRussian) "© 2026. Все права защищены." else "© 2026. All rights reserved."
    val exitDesc = if (isRussian) "Выход" else "Exit"
    val shareDesc = if (isRussian) "Отправить координаты" else "Send Coordinates"
    val historyDesc = if (isRussian) "История поездок" else "Trip History"
    val instructionDesc = if (isRussian) "Инструкция" else "Instructions"

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "TrackLit",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (isTripStarted) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(
                                        text = tripLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = (if (isLandscape) Modifier.padding(start = 20.dp) else Modifier).padding(start = 8.dp)
                        ) {
                            IconButton(
                                onClick = onLanguageToggle,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Text(
                                    text = if (isRussian) "RU" else "EN",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp
                                )
                            }
                            FilledTonalIconButton(
                                onClick = onInstructionClick,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = instructionDesc,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = onHistoryClick,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = historyDesc,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                FilledTonalIconButton(
                                    onClick = { shareCurrentLocation(localContext) },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = shareDesc,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                FilledTonalIconButton(
                                    onClick = {
                                        onStopClick()
                                        (localContext as? ComponentActivity)?.finishAffinity()
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = exitDesc,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(400.dp)
                        .alpha(0.05f),
                    tint = MaterialTheme.colorScheme.primary
                )

                if (isLandscape) {
                    Column(modifier = Modifier.fillMaxSize().padding(5.dp)) {
                        val landscapeStats = if (isRussian) {
                            "Спидометр: %.2f км | Топливо: %.2f л | Скорость: %.0f км/ч"
                        } else {
                            "Speedometer: %.2f km | Fuel: %.2f L | Speed: %.0f km/h"
                        }
                        Text(
                            text = String.format(Locale.US, landscapeStats,
                                currentTotalKm, currentRemainingFuel, speed),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).verticalScroll(scrollStateLeft),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy((0).dp)
                                    ) {
                                        fields.forEachIndexed { index, field ->
                                            key(field.id) {
                                                OutlinedTextField(
                                                    value = field.value,
                                                    onValueChange = { onFieldChange(index, it) },
                                                    label = { Text(field.label, fontSize = 9.sp) },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    modifier = Modifier.width(260.dp),
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                                    readOnly = isTripStarted || field.id == "max_speed" || field.id == "trip_km" || field.id == "current_speed_field",
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
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onStartClick,
                                    modifier = Modifier.width(240.dp),
                                    enabled = !isTripStarted
                                ) { Text(startLabel) }
                                Button(
                                    onClick = onStopClick,
                                    modifier = Modifier.width(240.dp),
                                    enabled = isTripStarted
                                ) { Text(stopLabel) }

                                Spacer(modifier = Modifier.height(10.dp))

                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(start = 20.dp)
                                    ) {
                                        MyCheckboxScreenCompact(isWinter, onWinterChange, winterLabel)
                                        MyCheckboxScreenCompact(isSpring, onSpringChange, springLabel)
                                    }
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(scrollStateLeft),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val portraitStats = if (isRussian) {
                            "Спидометр: %.2f км\nТопливо: %.2f л | Скорость: %.0f км/ч"
                        } else {
                            "Speedometer: %.2f km\nFuel: %.2f L | Speed: %.0f km/h"
                        }
                        Text(
                            text = String.format(Locale.US, portraitStats,
                                currentTotalKm, currentRemainingFuel, speed),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                        fields.forEachIndexed { index, field ->
                            key(field.id) {
                                OutlinedTextField(
                                    value = field.value,
                                    onValueChange = { onFieldChange(index, it) },
                                    label = { Text(field.label) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    readOnly = isTripStarted || field.id == "max_speed" || field.id == "trip_km" || field.id == "current_speed_field"
                                )
                            }
                        }
                        Row {
                            MyCheckboxScreen(isWinter, onWinterChange, winterLabel)
                            MyCheckboxScreenSpring(isSpring, onSpringChange, springLabel)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onStartClick,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isTripStarted
                        ) { Text(startLabel) }
                        Button(
                            onClick = onStopClick,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isTripStarted
                        ) { Text(stopLabel) }

                        Spacer(modifier = Modifier.height(160.dp))

                        Text(
                            text = copyrightLabel,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun shareTripHistory(context: android.content.Context) {
    val repository = SettingsRepository(context)
    val history = repository.getTripHistory()
    val isRussian = repository.getIsRussian()

    if (history.isEmpty()) {
        Toast.makeText(context, if (isRussian) "История пуста" else "History is empty", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, history)
    }
    context.startActivity(Intent.createChooser(intent, if (isRussian) "Поделиться историей..." else "Share history via..."))
}

private fun shareCurrentLocation(context: android.content.Context) {
    val repository = SettingsRepository(context)
    val isRussian = repository.getIsRussian()
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    
    try {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val uri = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                val message = if (isRussian) {
                    "Мои текущие координаты: ${location.latitude}, ${location.longitude}\nСсылка на карту: $uri"
                } else {
                    "My current location: ${location.latitude}, ${location.longitude}\nMap link: $uri"
                }

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                context.startActivity(Intent.createChooser(intent, if (isRussian) "Поделиться координатами..." else "Share location via..."))
            } else {
                Toast.makeText(context, if (isRussian) "Координаты еще не определены" else "Location not determined yet", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: SecurityException) {
        Toast.makeText(context, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MyCheckboxScreen(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun MyCheckboxScreenSpring(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

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

@Preview(showBackground = true, name = "Main Content - Started")
@Composable
fun PreviewMainLocationContentStarted() {
    CarDataTheme {
        MainLocationContent(
            fields = listOf(
                NumericField("km", "Speedometer (km)", "10.0"),
                NumericField("trip_km", "Trip (km)", "5.0"),
                NumericField("fuel", "Fuel (L)", "30.0"),
                NumericField("fuelStandart", "Fuel Rate", "6.5"),
                NumericField("current_speed_field", "Current Speed", "40.0")
            ),
            isWinter = false,
            isSpring = false,
            isTripStarted = true,
            isRussian = true,
            onSpringChange = {},
            onWinterChange = {},
            onLanguageToggle = {},
            onFieldChange = { _, _ -> },
            onHistoryClick = {},
            onInstructionClick = {},
            onStartClick = {},
            onStopClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Main Content - Stopped")
@Composable
fun PreviewMainLocationContentStopped() {
    CarDataTheme {
        MainLocationContent(
            fields = listOf(
                NumericField("km", "Speedometer (km)", "0.0"),
                NumericField("fuel", "Fuel (L)", "45.0"),
                NumericField("std", "Fuel Rate (L/100)", "7.0"),
                NumericField("trip_km", "Trip (km)", "0.0"),
                NumericField("avg_speed", "Avg Speed (km/h)", "0.0")
            ),
            isWinter = false,
            isSpring = false,
            isTripStarted = false,
            isRussian = false,
            onSpringChange = {},
            onWinterChange = {},
            onLanguageToggle = {},
            onFieldChange = { _, _ -> },
            onHistoryClick = {},
            onInstructionClick = {},
            onStartClick = {},
            onStopClick = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape", name = "Main Content - Landscape")
@Composable
fun PreviewMainLocationContentLandscape() {
    CarDataTheme {
        MainLocationContent(
            fields = listOf(
                NumericField("km", "Speedometer (km)", "120.5"),
                NumericField("trip_km", "Trip (km)", "10.0"),
                NumericField("fuel", "Fuel (L)", "25.0"),
                NumericField("fuelStandart", "Fuel Rate", "8.2"),
                NumericField("current_speed_field", "Current Speed", "60.0")
            ),
            isWinter = true,
            isSpring = false,
            isTripStarted = true,
            isRussian = true,
            onSpringChange = {},
            onWinterChange = {},
            onLanguageToggle = {},
            onFieldChange = { _, _ -> },
            onHistoryClick = {},
            onInstructionClick = {},
            onStartClick = {},
            onStopClick = {}
        )
    }
}

@Preview(showBackground = true, name = "History Screen")
@Composable
fun PreviewHistoryScreen() {
    CarDataTheme {
        HistoryScreen(
            history = listOf(
                "15.10.2023 | 08:30-10:15 | Путь: 45.20 км | Общий: 12540.30 км | Топливо: 32.50 л",
                "14.10.2023 | 12:00-14:00 | Путь: 80.00 км | Общий: 12495.10 км | Топливо: 40.00 л"
            ),
            isRussian = true,
            onBack = {},
            onShare = {},
            onClear = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewMyCheckboxScreen() {
    CarDataTheme {
        MyCheckboxScreen(isChecked = true, onCheckedChange = {}, label = "Winter (+10%)")
    }
}
