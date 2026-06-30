/**
 * @Author Osetrov.V.V.
 */
package com.rosseti.cardata.model

/**
 * Data class representing a numeric input field in the UI.
 * Holds information about the field's ID, label, and current value.
 *
 * @property id Unique identifier for the field (e.g., "km", "fuel").
 * @property label The descriptive label displayed to the user for this field.
 * @property value The current string value of the field as entered or calculated.
 */
data class NumericField(
    val id: String,
    val label: String,
    val value: String
)
