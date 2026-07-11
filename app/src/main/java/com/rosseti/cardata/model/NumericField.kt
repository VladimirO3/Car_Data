/**
 * @Author Osetrov.V.V.
 */
/**
 * © 2026 Osetrov V.V. Все права защищены.
 */
package com.rosseti.cardata.model

/**
 * Класс данных, представляющий числовое поле ввода в пользовательском интерфейсе.
 * Содержит сведения об ID, метке и текущем значении поля.
 *
 * @property id Уникальный идентификатор поля (например, «km», «fuel»).
 * @property label Описательная метка, отображаемая пользователю для данного поля.
 * @property value Текущее строковое значение поля, введенное или вычисленное.
 */
data class NumericField(
    val id: String,
    val label: String,
    val value: String
)
