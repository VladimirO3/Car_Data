package com.rosseti.cardata

import com.google.android.gms.location.LocationCallback
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.*
import com.google.android.gms.location.FusedLocationProviderClient
import androidx.core.content.edit
import androidx.compose.ui.tooling.preview.Preview
import com.rosseti.cardata.ui.theme.CarDataTheme


// Модель для полей ввода
data class NumericField(val id: String, val label: String, var value: String)

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null

    // Состояния для хранения дистанции и работы GPS в Compose
    private var totalDistanceState = mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Загружаем ранее сохраненную дистанцию из памяти смартфона
        val sharedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        totalDistanceState.floatValue = sharedPrefs.getFloat("total_distance", 0f)

        // Настраиваем отслеживание перемещения
        setupLocationTracking(sharedPrefs)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainLocationScreen(totalDistanceState.floatValue)
                }
            }
        }
    }

    private fun setupLocationTracking(sharedPrefs: SharedPreferences) {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (lastLocation != null) {
                        // Считаем расстояние между предыдущей и текущей точкой GPS
                        val distance = lastLocation!!.distanceTo(location)

                        // Исключаем погрешность GPS (допустим, прыжки менее 1 метра игнорируем)
                        if (distance > 1.0f) {
                            totalDistanceState.floatValue += distance
                            // Сохраняем новую дистанцию в память смартфона
                            sharedPrefs.edit {
                                putFloat(
                                    "total_distance",
                                    totalDistanceState.floatValue
                                )
                            }
                        }
                    }
                    lastLocation = location
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // каждые 5 секунд
            .setMinUpdateDistanceMeters(2f) // обновлять, если прошли минимум 2 метра
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onPause() {
        super.onPause()
        // Отключаем GPS при сворачивании для экономии батареи
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

@Composable
fun ButtonStart(onClick: () -> Unit) {
    Button(onClick = { onClick() },
        Modifier
            .fillMaxWidth()
            .padding(16.dp)// Отступы от краев

    ) {

        Text("Поехали")
    }
}
@Composable
fun ButtonStop(onClick: () -> Unit) {
    Button(onClick = { onClick() },
        Modifier
            .fillMaxWidth()
            .padding(16.dp)// Отступы от краев

    ) {

        Text("Приехали")
    }
}
@Composable
fun MainLocationScreen(totalDistance: Float) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }

    // Загружаем сохраненный ввод для полей по 10 цифр
    val fields = remember {
        mutableStateListOf(
            NumericField("km", "Километраж", sharedPrefs.getFloat("km", 0f).let { if (it == 0f) "" else it.toString() }),
            NumericField("fuel", "Остаток топлива", sharedPrefs.getFloat("fuel", 0f).let { if (it == 0f) "" else it.toString() }),
            NumericField("fuelStandart", "Норма расхода топлива", sharedPrefs.getFloat("fuelStandart", 0f).let { if (it == 0f) "" else it.toString() })
        )
    }

    // Запрос разрешений на геолокацию при запуске
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            (context as? MainActivity)?.startLocationUpdates()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    MainLocationContent(
        totalDistance = totalDistance,
        fields = fields,
        onFieldChange = { index, input ->
            val field = fields[index]
            val formattedInput = input.replace(',', '.')
            val isValidDecimal = formattedInput.isEmpty() || formattedInput.matches(Regex("""^\d{0,7}(\.\d{0,2})?$"""))
            // Ограничение: до 10 цифр и запрет букв
            if (isValidDecimal && formattedInput.length <= 10) {
                fields[index] = field.copy(value = formattedInput)
                // Преобразование строки в Float (если пусто, запишет 0.0f)
                // Преобразуем строку в Float, убирая финальную точку, если пользователь только начал её вводить (например "12.")
                val cleanInput = if (formattedInput.endsWith('.')) formattedInput.dropLast(1) else formattedInput
                val floatValue = cleanInput.toFloatOrNull() ?: 0f

                // Сохраняем корректный Float тип
                sharedPrefs.edit().putFloat(field.id, floatValue).apply()
            }
        },
        onStartClick = {
            val kmField = fields[0]
            Log.d("LogisticData", "Текущий километраж: ${kmField.value}")
            Toast.makeText(context, "Рейс запущен. Километраж: ${kmField.value}", Toast.LENGTH_SHORT).show()
        }
    )
}

@Composable
fun MainLocationContent(
    totalDistance: Float,
    fields: List<NumericField>,
    onFieldChange: (Int, String) -> Unit,
    onStartClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Logistic_data application",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        // ГЛАВНЫЙ ЗАГОЛОВОК
        Text(
            text = "Перед началом рейся",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // ИНФОРМАЦИЯ О ПРОЙДЕННОМ ПУТИ (переводим метры в километры для удобства)
        val distanceInKm = totalDistance/ 1000
        Text(
            text = "Пройденный путь при использовании: ${String.format("%.2f", distanceInKm)} км (${String.format("%.0f", totalDistance)} м)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ПОЛЯ ВВОДА ПО 10 ЦИФР
        fields.forEachIndexed { index, field ->
            OutlinedTextField(
                value = field.value,
                onValueChange = { input -> onFieldChange(index, input) },
                label = { Text(field.label) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Данные полей и пройденный путь считаются и сохраняются автоматически.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Text("Выполнить запуск отслеживания конверсий:")

        ButtonStart(onClick = onStartClick)
    }
}

@Preview(showBackground = true)
@Composable
fun MainLocationScreenPreview() {
    CarDataTheme {
        MainLocationContent(
            totalDistance = 1500f,
            fields = listOf(
                NumericField("km", "Километраж", "123.45"),
                NumericField("fuel", "Остаток топлива", "50.0"),
                NumericField("fuelStandart", "Норма расхода топлива", "8.5")
            ),
            onFieldChange = { _, _ -> },
            onStartClick = {}
        )
    }
}
