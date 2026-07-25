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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.widget.Toast.makeText
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Основная активность приложения TrackLit.
 * Управляет жизненным циклом, разрешениями (GPS, уведомления) и датчиками (компас).
 */
class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(SettingsRepository(applicationContext)) as T
            }
        }
    }

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "total_distance" || key == "last_max_speed" || key == "current_speed") {
            runOnUiThread { viewModel.refreshDistance() }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val notificationGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else true

        if (fineGranted || coarseGranted) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                checkBackgroundPermission()
            } else {
                startTripService()
            }
        } else {
            makeText(this, if (viewModel.isRussian.value) "Требуется доступ к местоположению" else "Location permission required", Toast.LENGTH_LONG).show()
        }
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startTripService()
        } else {
            val msg = if (viewModel.isRussian.value) 
                "Фоновый доступ не предоставлен. Приложение может работать нестабильно в свернутом виде." 
                else "Background access not granted. App might be unstable when minimized."
            makeText(this, msg, Toast.LENGTH_LONG).show()
            startTripService()
        }
    }

    private val gpsSettingLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            checkLocationPermissions()
        } else {
            makeText(this, "GPS must be enabled for the app to function", Toast.LENGTH_LONG).show()
        }
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(gpsStatusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(gpsStatusReceiver, filter)
        }

        setContent {
            val isDark = when (viewModel.themeMode.value) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            CarDataTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainLocationScreen(viewModel, ::onStartClicked, ::onStopClicked)
                }
            }
        }
    }

    private fun onStartClicked() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
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
                if (!isLocationEnabled()) {
                    makeText(this, "Please enable GPS in settings", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
        }
    }

    private fun checkLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val hasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasNotification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasFine || !hasCoarse || !hasNotification) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                checkBackgroundPermission()
            } else {
                startTripService()
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun checkBackgroundPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startTripService()
        } else {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun startTripService() {
        viewModel.onStartTrip()
        val intent = Intent(this, LocationService::class.java)
        startForegroundService(intent)
    }

    private fun onStopClicked() {
        viewModel.onStopTrip()
        stopService(Intent(this, LocationService::class.java))
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        viewModel.loadFields()
        if (!isLocationEnabled() && viewModel.isTripStarted.value) {
            onStopClicked()
            showGpsRequiredDialog()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientationValues = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationValues)
            val azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
            if (!azimuth.isNaN()) {
                viewModel.compassHeading.value = -azimuth
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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

enum class Screen {
    MAIN, SETTINGS, HISTORY, INSTRUCTION, LEGAL, AUTHOR, GOODBYE, WELCOME
}

/**
 * Навигационный хост приложения. 
 * Переключает экраны между главным, настройками, историей и экраном прощания.
 */
@Composable
fun MainLocationScreen(
    viewModel: MainViewModel, 
    onStart: () -> Unit, 
    onStop: () -> Unit
) {
    val context = LocalContext.current
    var currentScreen by rememberSaveable { mutableStateOf(Screen.WELCOME) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            Screen.WELCOME -> {
                WelcomeScreen {
                    currentScreen = Screen.MAIN
                }
            }
            Screen.GOODBYE -> {
                GoodbyeScreen {
                    (context as? ComponentActivity)?.finishAffinity()
                }
            }
            Screen.HISTORY -> {
                BackHandler { currentScreen = Screen.SETTINGS }
                HistoryScreen(
                    history = viewModel.tripHistory,
                    isRussian = viewModel.isRussian.value,
                    onBack = { currentScreen = Screen.SETTINGS },
                    onShare = { shareTripHistory(context) },
                    onClear = { viewModel.clearHistory() },
                    onDeleteItem = { viewModel.deleteHistoryItem(it) }
                )
            }
            Screen.INSTRUCTION -> {
                BackHandler { currentScreen = Screen.SETTINGS }
                InstructionScreen(
                    isRussian = viewModel.isRussian.value,
                    onBack = { currentScreen = Screen.SETTINGS }
                )
            }
            Screen.SETTINGS -> {
                BackHandler { currentScreen = Screen.MAIN }
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.MAIN },
                    onHistoryClick = { currentScreen = Screen.HISTORY },
                    onInstructionClick = { currentScreen = Screen.INSTRUCTION },
                    onLegalClick = { currentScreen = Screen.LEGAL },
                    onAuthorClick = { currentScreen = Screen.AUTHOR }
                )
            }
            Screen.LEGAL -> {
                BackHandler { currentScreen = Screen.SETTINGS }
                LegalScreen(
                    isRussian = viewModel.isRussian.value,
                    onBack = { currentScreen = Screen.SETTINGS }
                )
            }
            Screen.AUTHOR -> {
                BackHandler { currentScreen = Screen.SETTINGS }
                AuthorScreen(
                    isRussian = viewModel.isRussian.value,
                    onBack = { currentScreen = Screen.SETTINGS }
                )
            }
            Screen.MAIN -> {
                MainLocationContent(
                    fields = viewModel.fields,
                    isTripStarted = viewModel.isTripStarted.value,
                    isRussian = viewModel.isRussian.value,
                    compassHeading = viewModel.compassHeading.value,
                    onSettingsClick = { currentScreen = Screen.SETTINGS },
                    onFieldChange = viewModel::onFieldChange,
                    onExitClick = { currentScreen = Screen.GOODBYE },
                    onStartClick = {
                        onStart()
                        val kmValue = viewModel.fields.getOrNull(0)?.value ?: ""
                        if (kmValue.isNotEmpty()) {
                            val msg = if (viewModel.isRussian.value) "Поездка начата" else "Trip started"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onStopClick = {
                        onStop()
                        val msg = if (viewModel.isRussian.value) "Поездка завершена!" else "Trip completed!"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}

/**
 * Приветственный экран приложения.
 * Отображается в течение 3 секунд при запуске.
 */
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcome")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp).alpha(alpha)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(180.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Добро пожаловать!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "TrackLit",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Ваш профессиональный GPS-трекер рейсов и расхода топлива.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Экран прощания с анимацией грустного лица.
 * Отображается в течение 3 секунд перед закрытием приложения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodbyeScreen(onFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "goodbye")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ), label = "fade"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Sad Face Drawing
            Canvas(modifier = Modifier.size(120.dp).alpha(alpha)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                
                // Head
                drawCircle(
                    color = Color(0xFFFFD54F),
                    radius = radius
                )
                
                // Eyes
                drawCircle(
                    color = Color.Black,
                    radius = 8f,
                    center = Offset(center.x - radius * 0.3f, center.y - radius * 0.2f)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 8f,
                    center = Offset(center.x + radius * 0.3f, center.y - radius * 0.2f)
                )
                
                // Sad Mouth
                drawArc(
                    color = Color.Black,
                    startAngle = 0f,
                    sweepAngle = -180f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.4f, center.y + radius * 0.1f),
                    size = androidx.compose.ui.geometry.Size(radius * 0.8f, radius * 0.4f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "До встречи!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Экран настроек приложения.
 * Содержит переключатели языка, темы, переход к истории и правовой информации.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onHistoryClick: () -> Unit,
    onInstructionClick: () -> Unit,
    onLegalClick: () -> Unit,
    onAuthorClick: () -> Unit
) {
    val context = LocalContext.current
    val isRussian = viewModel.isRussian.value
    val title = if (isRussian) "Настройки" else "Settings"
    
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Language Setting
                val languageSubtitle = when (viewModel.languageMode.value) {
                    0 -> if (isRussian) "Системный" else "System"
                    1 -> if (isRussian) "Русский" else "Russian"
                    else -> if (isRussian) "Английский" else "English"
                }

                SettingsItem(
                    icon = Icons.Default.Info,
                    title = if (isRussian) "Язык интерфейса" else "Interface Language",
                    subtitle = if (isRussian) "Выбрано: $languageSubtitle" else "Selected: $languageSubtitle",
                    onClick = { viewModel.toggleLanguage() }
                )

                // Theme Setting
                val themeSubtitle = when (viewModel.themeMode.value) {
                    0 -> if (isRussian) "Системная" else "System"
                    1 -> if (isRussian) "Светлая" else "Light"
                    else -> if (isRussian) "Темная" else "Dark"
                }

                SettingsItem(
                    icon = Icons.Default.Warning, // Using Warning as a placeholder or maybe find a better one if possible, but let's stick to standard available for now
                    title = if (isRussian) "Тема приложения" else "App Theme",
                    subtitle = if (isRussian) "Выбрано: $themeSubtitle" else "Selected: $themeSubtitle",
                    onClick = { viewModel.toggleTheme() }
                )

                // History
                SettingsItem(
                    icon = Icons.Default.Menu,
                    title = if (isRussian) "История рейсов" else "Trip History",
                    subtitle = if (isRussian) "Просмотр и удаление" else "View and delete records",
                    onClick = onHistoryClick
                )

                // Instructions
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = if (isRussian) "Инструкция" else "Instructions",
                    subtitle = if (isRussian) "Как пользоваться приложением" else "How to use the app",
                    onClick = onInstructionClick
                )

                // Legal
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = if (isRussian) "Правовая информация" else "Legal Information",
                    subtitle = if (isRussian) "Политика конфиденциальности" else "Privacy Policy & EULA",
                    onClick = onLegalClick
                )

                // Author
                SettingsItem(
                    icon = Icons.Default.AccountCircle,
                    title = if (isRussian) "Связь с автором" else "Contact Author",
                    subtitle = if (isRussian) "Предложения и замечания" else "Suggestions & feedback",
                    onClick = onAuthorClick
                )

                // Share Location
                SettingsItem(
                    icon = Icons.Default.LocationOn,
                    title = if (isRussian) "Поделиться координатами" else "Share Coordinates",
                    subtitle = if (isRussian) "Отправить текущую локацию" else "Send your current location",
                    onClick = { shareCurrentLocation(context) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(isRussian: Boolean, onBack: () -> Unit) {
    val title = if (isRussian) "Правовая информация" else "Legal Info"
    val acceptText = if (isRussian) "Принять и продолжить" else "Accept and Continue"
    
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isRussian) "Пользовательское соглашение" else "Terms of Service",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isRussian) 
                                "1. Приложение TrackLit предоставляет инструменты для расчета параметров поездки.\n2. Автор не несет ответственности за точность показаний GPS.\n3. Данные хранятся локально на устройстве.\n4. Используя приложение, вы соглашаетесь с обработкой геопозиции в фоновом режиме."
                                else "1. TrackLit provides tools for calculating trip parameters.\n2. The author is not responsible for GPS accuracy.\n3. Data is stored locally on the device.\n4. By using the app, you agree to background location processing.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isRussian) "Политика конфиденциальности" else "Privacy Policy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isRussian)
                                "Приложение собирает данные о местоположении исключительно для расчета дистанции и скорости. Данные не передаются третьим лицам и не хранятся на серверах."
                                else "The app collects location data solely for distance and speed calculations. Data is not shared with third parties or stored on servers.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(acceptText)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorScreen(isRussian: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val title = if (isRussian) "Связь с автором" else "Contact Author"
    
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (isRussian) "Предложения и замечания присылайте по ссылкам ниже" else "Please send your suggestions and feedback via the links below",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Telegram Link
                    FilledTonalIconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/BBC2331"))
                        context.startActivity(intent)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_telegram),
                            contentDescription = "Telegram",
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                    }

                    // GitHub Link
                    FilledTonalIconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/VladimirO3"))
                        context.startActivity(intent)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = "GitHub",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Email Link
                    FilledTonalIconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:verso0100@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "TrackLit Feedback")
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_gmail),
                            contentDescription = "Email",
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (isRussian) 
                            "© 2026. Все права защищены. Приложение разработано для помощи водителям в расчете эксплуатационных параметров автомобиля."
                            else "© 2026. All rights reserved. The app is designed to help drivers calculate vehicle operational parameters.",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun openExternalNavigator(context: android.content.Context) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    try {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                // Пытаемся открыть Яндекс Навигатор, если нет - Яндекс Карты, если нет - Google Maps
                val yandexNaviUri = android.net.Uri.parse("yandexnavi://show_point_on_map?lat=$lat&lon=$lon&zoom=16")
                val yandexMapsUri = android.net.Uri.parse("yandexmaps://maps.yandex.ru/?ll=$lon,$lat&z=16&l=map")
                val googleMapsUri = android.net.Uri.parse("geo:$lat,$lon?z=16")

                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, yandexNaviUri)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val mapsIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, yandexMapsUri)
                    try {
                        context.startActivity(mapsIntent)
                    } catch (e2: Exception) {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, googleMapsUri))
                    }
                }
            } else {
                Toast.makeText(context, "Location not available", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: SecurityException) {
        Toast.makeText(context, "Permission error", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<String>,
    isRussian: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onDeleteItem: (String) -> Unit
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
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = record,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 13.sp
                                )
                                IconButton(
                                    onClick = { onDeleteItem(record) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete Item",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
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
                    title = if (isRussian) "Работа в фоне" else "Background Mode",
                    text = if (isRussian) 
                        "Приложение работает в свернутом виде. Кликните на уведомление в шторке, чтобы мгновенно вернуться к расчетам." 
                        else "The app works when minimized. Tap the notification in the drawer to return to the app instantly."
                )
                InstructionItem(
                    number = "4",
                    title = if (isRussian) "Карта и навигация" else "Map & Navigation",
                    text = if (isRussian) 
                        "Проведите пальцем (свайп) в любую сторону по главному экрану, чтобы мгновенно открыть внешнее приложение навигации с вашей локацией." 
                        else "Swipe anywhere on the main screen to instantly open your external navigation app with your location."
                )
                InstructionItem(
                    number = "5",
                    title = if (isRussian) "Завершение рейса" else "End Trip",
                    text = if (isRussian) 
                        "По прибытии нажмите «Стоп». Данные сохранятся в историю, а поля ввода снова разблокируются для корректировки." 
                        else "Click 'Stop' upon arrival. Data will be saved to history, and input fields will unlock."
                )
                InstructionItem(
                    number = "6",
                    title = if (isRussian) "Дополнительно" else "Extra Features",
                    text = if (isRussian) 
                        "Используйте кнопку с иконкой локации для отправки координат. Одометр начнет счет при скорости выше 2 км/ч." 
                        else "Use the location icon to share coordinates. The odometer starts counting at speeds above 2 km/h."
                )
                InstructionItem(
                    number = "7",
                    title = if (isRussian) "Использование компаса" else "Compass Usage",
                    text = if (isRussian)
                        "Темно-синяя стрелка указывает на Север, красная — на Юг. Зеленая метка показывает обратный курс для возврата."
                        else "Dark blue needle points North, red points South. The green mark shows the return azimuth."
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
    isTripStarted: Boolean,
    isRussian: Boolean,
    compassHeading: Float,
    onSettingsClick: () -> Unit,
    onFieldChange: (Int, String) -> Unit,
    onExitClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val context = LocalContext.current
    
    // Адаптация под узкие и высокие экраны (например, 720*1640 ~ 360*820 dp)
    val isNarrowScreen = configuration.screenWidthDp <= 360
    val isTallScreen = configuration.screenHeightDp > 800
    
    val contentPadding = if (isNarrowScreen) 10.dp else 16.dp
    val baseFontSize = if (isNarrowScreen) 14.sp else 16.sp
    val statsFontSize = if (isNarrowScreen) 14.sp else 15.sp
    
    val currentTotalKm = fields.getOrNull(0)?.value?.toFloatOrNull() ?: 0f
    val currentRemainingFuel = fields.getOrNull(2)?.value?.toFloatOrNull() ?: 0f
    val speed = fields.getOrNull(4)?.value?.toFloatOrNull() ?: 0f

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
    val copyrightLabel = if (isRussian) "© 2026. Все права защищены." else "© 2026. All rights reserved."
    val exitDesc = if (isRussian) "Выход" else "Exit"
    val settingsDesc = if (isRussian) "Настройки" else "Settings"

    // Детектор свайпа для открытия навигатора (с защитой от многократного срабатывания)
    var lastSwipeTime by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    val currentTime = System.currentTimeMillis()
                    if (Math.abs(dragAmount) > 45 && (currentTime - lastSwipeTime > 1500)) {
                        lastSwipeTime = currentTime
                        openExternalNavigator(context)
                    }
                }
            }
    ) {
        if (isTripStarted) {
            SmokeEffect()
        }
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
                        // Оставляем пустым или добавляем что-то незначительное
                    },
                    actions = {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = onSettingsClick,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = settingsDesc,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                FilledTonalIconButton(
                                    onClick = {
                                        onStopClick()
                                        onExitClick()
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
                        .size(minOf(screenWidth, 400.dp))
                        .alpha(0.05f),
                    tint = MaterialTheme.colorScheme.primary
                )

                if (isLandscape) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(end = 30.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1.2f).verticalScroll(scrollStateLeft),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy((0).dp)
                                    ) {
                                        fields.forEachIndexed { index, field ->
                                            key(field.id) {
                                                AutomotiveGaugeField(
                                                    field = field,
                                                    onValueChange = { onFieldChange(index, it) },
                                                    isTripStarted = isTripStarted,
                                                    isNarrow = isNarrowScreen,
                                                    modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth(0.9f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CompassView(compassHeading, isRussian)
                            }
                            
                            // Пустая колонка для балансировки центра, если нужно, 
                            // но Row с Arrangement.Center и весами сделает все симметрично
                        }

                        Button(
                            onClick = if (isTripStarted) onStopClick else onStartClick,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(75.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (isTripStarted) stopLabel else startLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .verticalScroll(scrollStateLeft),
                        verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 2.dp else 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        fields.forEachIndexed { index, field ->
                            key(field.id) {
                                AutomotiveGaugeField(
                                    field = field,
                                    onValueChange = { onFieldChange(index, it) },
                                    isTripStarted = isTripStarted,
                                    isNarrow = isNarrowScreen
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(0.dp))
                        
                        Button(
                            onClick = if (isTripStarted) onStopClick else onStartClick,
                            modifier = Modifier.size(90.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(
                                text = if (isTripStarted) stopLabel else startLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        val compassSize = if (isTallScreen) 140.dp else if (isNarrowScreen) 110.dp else 130.dp
                        CompassView(compassHeading, isRussian, modifier = Modifier.size(compassSize))

                        Spacer(modifier = Modifier.height(65.dp))
                        Text(
                            text = copyrightLabel,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompassView(heading: Float, isRussian: Boolean, modifier: Modifier = Modifier) {
    val animatedHeading by animateFloatAsState(targetValue = heading)
    val onSurface = MaterialTheme.colorScheme.onSurface
    
    val displayDegrees = ((heading % 360 + 360) % 360).toInt()
    val returnDegrees = (displayDegrees + 180) % 360
    
    val nLabel = if (isRussian) "С" else "N"
    val sLabel = if (isRussian) "Ю" else "S"
    val eLabel = if (isRussian) "В" else "E"
    val wLabel = if (isRussian) "З" else "W"
    val returnLabel = if (isRussian) "Возврат" else "Return"
    
    Box(
        modifier = modifier.padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = center
            val radius = size.minDimension / 2
            
            // Outer ring
            drawCircle(
                color = onSurface.copy(alpha = 0.1f),
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
            
            // Degree marks - every 10 degrees
            for (i in 0 until 36) {
                val isMajor = i % 9 == 0 // 0, 90, 180, 270
                val isMedium = i % 3 == 0 && !isMajor // 30, 60, 120...
                
                val tickLength = if (isMajor) 15f else if (isMedium) 10f else 6f
                val alpha = if (isMajor) 0.6f else if (isMedium) 0.4f else 0.2f
                
                rotate(i * 10f) {
                    drawLine(
                        color = onSurface.copy(alpha = alpha),
                        start = androidx.compose.ui.geometry.Offset(center.x, 0f),
                        end = androidx.compose.ui.geometry.Offset(center.x, tickLength),
                        strokeWidth = if (isMajor) 2.5f else 1.5f
                    )
                }
            }
            
            rotate(degrees = animatedHeading) {
                // North indicator (Dark Blue)
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(center.x, center.y - radius + 25f)
                        lineTo(center.x - 12f, center.y)
                        lineTo(center.x + 12f, center.y)
                        close()
                    },
                    color = Color(0xFF00008B)
                )
                // South indicator (Red)
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(center.x, center.y + radius - 25f)
                        lineTo(center.x - 12f, center.y)
                        lineTo(center.x + 12f, center.y)
                        close()
                    },
                    color = Color.Red
                )
                
                // Return Azimuth Indicator (Green dashed or semi-transparent)
                rotate(degrees = 180f) {
                    drawPath(
                        path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(center.x, center.y - radius + 40f)
                            lineTo(center.x - 8f, center.y - radius + 60f)
                            lineTo(center.x + 8f, center.y - radius + 60f)
                            close()
                        },
                        color = Color(0xFF4CAF50).copy(alpha = 0.7f)
                    )
                }

                // Center pin
                drawCircle(color = onSurface, radius = 4f)
            }
        }
        
        // Cardinal Labels
        Text(
            text = nLabel,
            modifier = Modifier.align(Alignment.TopCenter),
            color = Color(0xFF00008B),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = sLabel,
            modifier = Modifier.align(Alignment.BottomCenter),
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = eLabel,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = wLabel,
            modifier = Modifier.align(Alignment.CenterStart),
            color = onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        // Numeric degree in the center
        Column(
            modifier = Modifier.align(Alignment.Center).padding(top = 45.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$displayDegrees°",
                fontSize = 10.sp,
                color = onSurface.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$returnLabel: $returnDegrees°",
                fontSize = 8.sp,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Medium
            )
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
                val uri = "https://yandex.ru/maps/?text=${location.latitude},${location.longitude}"
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
fun SmokeEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "smoke")
    
    // Увеличили прозрачность (alpha) для лучшей видимости
    val smokeParticles = listOf(
        SmokeParticleData(duration = 8000, delay = 0, size = 400f, alpha = 0.2f),
        SmokeParticleData(duration = 11000, delay = 2000, size = 550f, alpha = 0.15f),
        SmokeParticleData(duration = 7000, delay = 1000, size = 450f, alpha = 0.18f)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        smokeParticles.forEach { data ->
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = data.duration, delayMillis = data.delay, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "smokeProgress"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Дым плывет по более широкой траектории
                val x = -data.size + (width + data.size * 2) * progress
                val y = height * 0.9f - (height * 0.7f * progress)
                
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.LightGray.copy(alpha = data.alpha),
                            Color.Transparent
                        ),
                        center = Offset(x, y),
                        radius = data.size
                    ),
                    center = Offset(x, y),
                    radius = data.size
                )
            }
        }
    }
}

data class SmokeParticleData(
    val duration: Int,
    val delay: Int,
    val size: Float,
    val alpha: Float
)

@Composable
fun MyCheckboxScreen(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 2.dp),
            fontSize = 12.sp
        )
    }
}


@Composable
fun AutomotiveGaugeField(
    field: NumericField,
    onValueChange: (String) -> Unit,
    isTripStarted: Boolean,
    isNarrow: Boolean,
    modifier: Modifier = Modifier
) {
    val isReadOnly = isTripStarted || field.id == "max_speed" || field.id == "trip_km" || field.id == "current_speed_field"
    val baseFontSize = if (isNarrow) 16.sp else 18.sp
    
    // Определение максимального значения для каждого типа прибора для расчета дуги
    val maxValue = when (field.id) {
        "current_speed_field", "max_speed" -> 220f // Макс. скорость 220 км/ч
        "fuel" -> 200f // Макс. топливо 200 литров
        "fuelStandart" -> 54f // Макс. норма расхода 54 л/100км
        "trip_km" -> 1000f // Макс. для трипа в рамках шкалы
        "km" -> 1000f // Для одометра используем остаток от 1000 для анимации
        else -> 100f
    }

    // Расчет прогресса (нормализация 0.0 - 1.0)
    val progress = try {
        val value = field.value.replace(",", ".").toFloatOrNull() ?: 0f
        if (field.id == "km") {
            (value % 1000f) / 1000f // Для одометра показываем прогресс текущей тысячи
        } else {
            (value / maxValue).coerceIn(0f, 1f)
        }
    } catch (e: Exception) { 0f }

    // Цвет дуги зависит от типа поля
    val gaugeColor = when (field.id) {
        "fuel" -> Color(0xFF2196F3) // Голубой для топлива
        "current_speed_field", "max_speed" -> Color(0xFFFF9800) // Оранжевый для скорости
        "fuelStandart" -> Color(0xFF4CAF50) // Зеленый для экономичности
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        // Фоновая дуга "прибора"
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            val strokeWidth = 3.dp.toPx()
            drawArc(
                color = gaugeColor.copy(alpha = 0.15f),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            
            // "Заполненная" часть дуги на основе прогресса
            drawArc(
                color = gaugeColor,
                startAngle = 150f,
                sweepAngle = (240f * progress),
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }

        // Поле ввода поверх "прибора"
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = field.label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
            )
            androidx.compose.foundation.text.BasicTextField(
                value = field.value,
                onValueChange = onValueChange,
                readOnly = isReadOnly,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontSize = baseFontSize,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
            // Разделительная черта как на цифровой панели
            Box(modifier = Modifier.width(60.dp).height(1.dp).background(gaugeColor.copy(alpha = 0.5f)))
        }
    }
}

@Preview(showBackground = true, name = "Main Content - Started")
@Composable
fun PreviewMainLocationContentStarted() {
    CarDataTheme {
        MainLocationContent(
            fields = listOf(
                NumericField("km", "Одометр (км)", "10.0"),
                NumericField("trip_km", "Trip (km)", "5.0"),
                NumericField("fuel", "Fuel (L)", "30.0"),
                NumericField("fuelStandart", "Fuel Rate", "6.5"),
                NumericField("current_speed_field", "Current Speed", "40.0")
            ),
            isTripStarted = true,
            isRussian = true,
            compassHeading = 0f,
            onSettingsClick = {},
            onFieldChange = { _, _ -> },
            onExitClick = {},
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
                NumericField("km", "Одометр (км)", "0.0"),
                NumericField("fuel", "Fuel (L)", "45.0"),
                NumericField("std", "Fuel Rate (L/100)", "7.0"),
                NumericField("trip_km", "Trip (km)", "0.0"),
                NumericField("avg_speed", "Avg Speed (km/h)", "0.0")
            ),
            isTripStarted = false,
            isRussian = false,
            compassHeading = 0f,
            onSettingsClick = {},
            onFieldChange = { _, _ -> },
            onExitClick = {},
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
                NumericField("km", "Одометр (км)", "120.5"),
                NumericField("trip_km", "Trip (km)", "10.0"),
                NumericField("fuel", "Fuel (L)", "25.0"),
                NumericField("fuelStandart", "Fuel Rate", "8.2"),
                NumericField("current_speed_field", "Current Speed", "60.0")
            ),
            isTripStarted = true,
            isRussian = true,
            compassHeading = 45f,
            onSettingsClick = {},
            onFieldChange = { _, _ -> },
            onExitClick = {},
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
            onClear = {},
            onDeleteItem = {}
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
