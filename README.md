# Приложение для сбора данных транспортных средств . 
<img src="2026-07-03_22-48-20.png" width="700" alt="Описание">
    <h1>Проект TrackLit</h1>
    <p>Интеллектуальный GPS-трекер для учета поездок и топлива</p>
</header>

<div class="card">
    <h2>Общее описание</h2>
    <p><strong>TrackLit</strong> — это специализированное Android-приложение для водителей. Оно автоматизирует процесс ведения путевого листа, отслеживая пройденную дистанцию в реальном времени через GPS и рассчитывая остаток топлива с учетом различных условий эксплуатации.</p>
    <div>
        <span class="tag">Kotlin</span>
        <span class="tag">Jetpack Compose</span>
        <span class="tag">Foreground Service</span>
        <span class="tag">GPS/Fused Location</span>
    </div>
</div>

<div class="card">
    <h2>Архитектура кода</h2>
    <ul>
        <li><strong>MainActivity.kt:</strong> Точка входа. Управляет UI на Jetpack Compose и запрашивает разрешения (Location permissions).</li>
        <li><strong>LocationService.kt:</strong> "Сердце" приложения. Фоновая служба, которая продолжает работать, даже когда приложение свернуто. Получает координаты от спутников.</li>
        <li><strong>MainViewModel.kt:</strong> Обрабатывает бизнес-логику. Считает расход, обновляет данные и взаимодействует с UI.</li>
        <li><strong>SettingsRepository.kt:</strong> Отвечает за сохранение данных (пробег, топливо) в памяти устройства (SharedPreferences).</li>
    </ul>
</div>

<div class="card">
    <h2>Ключевые функции</h2>
    <div class="feature-list">
        <div>
            <h3>Отслеживание</h3>
            <p>Использование <code>FusedLocationProviderClient</code> для высокоточного определения координат и автоматического подсчета пробега.</p>
        </div>
        <div>
            <h3>Умный расчет топлива</h3>
            <p>Формула расхода учитывает базовую норму + коэффициенты (Зима/Весна), которые добавляют 10% к затратам.</p>
        </div>
        <div>
            <h3>Адаптивный UI</h3>
            <p>Интерфейс на Jetpack Compose корректно отображается как в портретном, так и в ландшафтном режиме (удобно для крепления в авто).</p>
        </div>
        <div>
            <h3>Безопасность данных</h3>
            <p>Данные сохраняются при каждом обновлении дистанции. Даже при перезагрузке телефона прогресс поездки не будет потерян.</p>
        </div>
    </div>
</div>

<div class="card">
    <h2>Технические особенности</h2>
    <ul>
        <li>Минимальная версия Android: <strong>10.0 (API 30)</strong>.</li>
        <li>Использование <strong>Foreground Service</strong> с уведомлением для предотвращения остановки системы.</li>
        <li>Поддержка <strong>Material Design 3</strong> для современного внешнего вида.</li>
    </ul>
</div>
<h1>Документация приложения TrackLit</h1>
    <p>Это техническое описание архитектуры, классов и методов Android-приложения для учета пробега и расхода топлива.</p>

<div class="class-box">
        <h2>MainActivity</h2>
        <span class="tag">UI Layer</span>
        <p>Центральная Activity приложения. Управляет жизненным циклом UI (Jetpack Compose), запрашивает разрешения и контролирует запуск/остановку служб.</p>
        <table>
            <tr><th>Метод</th><th>Описание</th></tr>
            <tr><td class="method-name">onCreate()</td><td>Инициализирует UI, регистрирует слушателей настроек и проверяет состояние GPS.</td></tr>
            <tr><td class="method-name">onStartClicked()</td><td>Проверяет GPS и разрешения (включая фоновые), затем инициирует запуск рейса.</td></tr>
            <tr><td class="method-name">startTripService()</td><td>Запускает Foreground Service для фонового отслеживания.</td></tr>
            <tr><td class="method-name">onStopClicked()</td><td>Останавливает службу и сохраняет финальные данные через ViewModel.</td></tr>
            <tr><td class="method-name">showGpsDisabledDialog()</td><td>Отображает критическое окно с требованием включить GPS.</td></tr>
            <tr><td class="method-name">showRationaleDialog()</td><td>Показывает пользователю обоснование необходимости разрешений на геолокацию.</td></tr>
        </table>
    </div>
    <div class="class-box">
        <h2>MainViewModel</h2>
        <span class="tag">Business Logic Layer</span>
        <p>Обрабатывает все вычисления, хранит состояние UI и синхронизирует данные между экраном и репозиторием.</p>
        <table>
            <tr><th>Метод</th><th>Описание</th></tr>
            <tr><td class="method-name">loadFields()</td><td>Загружает сохраненные значения одометра и топлива из памяти при старте.</td></tr>
            <tr><td class="method-name">calculateRemainingFuel()</td><td>Рассчитывает остаток топлива с учетом пройденного пути и сезонных коэффициентов (+10%).</td></tr>
            <tr><td class="method-name">calculateAverageSpeed()</td><td>Вычисляет среднюю скорость на основе времени старта и пройденного расстояния.</td></tr>
            <tr><td class="method-name">onFieldChange()</td><td>Валидирует ввод пользователя (десятичные числа) и сохраняет их в репозиторий.</td></tr>
            <tr><td class="method-name">onStartTrip()</td><td>Сбрасывает текущий пробег и фиксирует время начала рейса.</td></tr>
            <tr><td class="method-name">refreshDistance()</td><td>Обновляет UI-поля на основе актуальных данных от GPS-службы.</td></tr>
        </table>
    </div>
    <div class="class-box">
        <h2>LocationService</h2>
        <span class="tag">Background Layer</span>
        <p>Foreground Service, который работает в фоне. Получает данные от GPS-провайдера и обновляет общее расстояние поездки.</p>
        <table>
            <tr><th>Метод</th><th>Описание</th></tr>
            <tr><td class="method-name">onLocationResult()</td><td>Обрабатывает новые координаты, фильтрует GPS-помехи и суммирует дистанцию.</td></tr>
            <tr><td class="method-name">startLocationUpdates()</td><td>Настраивает параметры запроса GPS (интервал, точность) и запускает прослушивание.</td></tr>
            <tr><td class="method-name">onStartCommand()</td><td>Создает уведомление в шторке и переводит службу в режим Foreground.</td></tr>
        </table>
    </div>
    <div class="class-box">
        <h2>SettingsRepository</h2>
        <span class="tag">Data Layer</span>
        <p>Обеспечивает постоянное хранение данных приложения с использованием SharedPreferences.</p>
        <table>
            <tr><th>Метод</th><th>Описание</th></tr>
            <tr><td class="method-name">saveTotalDistance()</td><td>Записывает накопленный пробег текущей поездки.</td></tr>
            <tr><td class="method-name">saveFieldValue()</td><td>Сохраняет значения конкретных полей (спидометр, остаток в баке).</td></tr>
            <tr><td class="method-name">getStartTime()</td><td>Возвращает метку времени начала текущего рейса.</td></tr>
            <tr><td class="method-name">saveCurrentSpeed()</td><td>Кэширует последнюю мгновенную скорость для UI.</td></tr>
        </table>
    </div>
<footer>
    <p>&copy; 2026 TrackLit Project. Разработано для оптимизации работы водителей.</p>
</footer>

</body>
</html>
