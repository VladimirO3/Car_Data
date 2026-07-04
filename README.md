# My app for collecting data on vehicle movement 
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

<footer>
    <p>&copy; 2026 TrackLit Project. Разработано для оптимизации работы водителей.</p>
</footer>

</body>
</html>
