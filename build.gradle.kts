// Файл сборки верхнего уровня, в котором можно добавить параметры конфигурации, общие для всех подпроектов/модулей.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.dokka) apply false
}