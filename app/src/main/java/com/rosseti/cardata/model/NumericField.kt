package com.rosseti.cardata.model

/**
 * содержит простую модель данных (data class). Это своего рода «контейнер» или «шаблон»,
 * который описывает, из чего состоит каждое поле ввода в вашем приложении.
 */
data class NumericField(
    val id: String,
    val label: String,
    val value: String
)
