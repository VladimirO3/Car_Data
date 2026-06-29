/**
 * @Author Osetrov.V.V.
 */
package com.rosseti.cardata

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import android.widget.Toast.makeText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.*
import com.rosseti.cardata.data.SettingsRepository
import com.rosseti.cardata.model.NumericField
import com.rosseti.cardata.ui.theme.CarDataTheme

/**
 * Функция предварительного просмотра основного содержимого экрана местоположения.
 */
@Preview(showBackground = true)
@Composable
fun PreviewMainLocationContent() {
    CarDataTheme {
        MainLocationContent(
            fields = listOf(
                NumericField("km", "Спидометр(км)", "10.0"),
                NumericField("fuel", "Топливо(л)", "30.0"),
                NumericField("std", "Расхода топлива(л/100)", "6.5")
            ),
            onFieldChange = { _, _ -> },
            onStartClick = {},
            onStopClick = {}
        )
    }
}

/**
 * Основное действие приложения.
 * Управление службами отслеживания пользовательского интерфейса, жизненного цикла и местоположения.
 *
 * @property fusedLocationClient Client для взаимодействия с поставщиком интегрированного местоположения.
 * @property locationCallback Callback для получения обновлений местоположения.
 * @property lastLocation Сохраняет последнее известное местоположение для расчета расстояния.
 */
class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null

    /**
     * ViewModel для основного экрана, инициализированного [SettingsRepository].
     */
    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(SettingsRepository(applicationContext)) as T
            }
        }
    }

    /**
     * Средство запуска для запроса разрешений на местоположение.
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupLocationTracking()

        setContent {
            CarDataTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainLocationScreen(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /**
     * Настройка логики обратного вызова по местоположению.
     * Вычисляет расстояние, пройденное между обновлениями местоположения и обновлением ViewModel.
     */
    private fun setupLocationTracking() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    lastLocation?.let { last ->
                        viewModel.addDistance(last.distanceTo(location))
                    }
                    lastLocation = location
                }
            }
        }
    }

    /**
     * Начало получения обновлений местоположения с указанными параметрами.
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(2f)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        lastLocation = null // Reset last location when updates stop
    }
}

/**
 * Основной экран компоновочный, соединяющий ViewModel и содержимое интерфейса.
 *
 * @param viewModel The ViewModel, предоставляющий управляющие состоянием и событиями.
 */
@Composable
fun MainLocationScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    
    MainLocationContent(
        fields = viewModel.fields,
        onFieldChange = viewModel::onFieldChange,
        onStartClick = {
            viewModel.onStartTrip()
            val kmValue = viewModel.fields.getOrNull(0)?.value ?: ""
            val fuelValue = viewModel.fields.getOrNull(1)?.value ?: ""
            makeText(context, "Рейс запущен. Километраж: $kmValue, Топливо: $fuelValue", Toast.LENGTH_SHORT).show()
        },
        onStopClick = {
            viewModel.onStopTrip()
            makeText(context, "Рейс завершен!", Toast.LENGTH_LONG).show()
        }
    )
}

/**
 * Содержимое пользовательского интерфейса, составляемое для главного экрана местоположения.
 *
 * @param fields Список числовых полей для отображения.
 * @param onFieldChange Callback для изменения значения поля.
 * @param onStartClick Callback для нажатия кнопки «Start».
 * @param onStopClick Callback для того, чтобы отменить нажатие на кнопку «Остановить».
 */
@SuppressLint("DefaultLocale")
@Composable
fun MainLocationContent(
    fields: List<NumericField>,
    onFieldChange: (Int, String) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val currentTotalKm = fields.getOrNull(0)?.value?.toFloatOrNull() ?: 0f
    val currentRemainingFuel = fields.getOrNull(1)?.value?.toFloatOrNull() ?: 0f

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Тruck crane Rosseti-Ural",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text("Перед началом рейса", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)

        Text(
            text = String.format("Общий километраж: %.2f км\nОстаток топлива: %.2f л", 
                                currentTotalKm, currentRemainingFuel),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )

        fields.forEachIndexed { index, field ->
            OutlinedTextField(
                value = field.value,
                onValueChange = { onFieldChange(index, it) },
                label = { Text(field.label) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStartClick, modifier = Modifier.fillMaxWidth()) { Text("Старт") }
        Button(onClick = onStopClick, modifier = Modifier.fillMaxWidth()) { Text("Стоп") }
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "Разработано: Осетров В.В.\n© 2026 Rosseti-Ural. Все права защищены.",
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}
