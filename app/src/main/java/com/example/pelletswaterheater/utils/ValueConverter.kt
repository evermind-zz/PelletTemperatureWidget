package com.example.pelletswaterheater.utils

import kotlinx.serialization.Serializable
import java.text.DecimalFormatSymbols

/**
 * Data class representing a converted metadata value with its logical value, name, and unit.
 */
@Serializable
data class ConvertedValue(
    val logical: String,
    val name: String,
    val unit: String? = null
)

/**
 * Utility object for converting raw metadata values based on [MetadataField] attributes.
 */
object ValueConverter {
    private val TAG = ValueConverter::class.java.simpleName

    /**
     * Converts a raw value using the provided metadata field.
     *
     * @param key The field key (e.g., "system.L_ambient").
     * @param rawValue The raw value to convert.
     * @param metadata The [MetadataField] containing conversion attributes.
     * @return The [ConvertedValue] with logical value, name, and unit.
     */
    fun convert(key: String, rawValue: String, metadata: MetadataField?): ConvertedValue {
        val name = metadata?.name ?: key
        if (metadata == null) {
            return ConvertedValue(logical = rawValue, name = name)
        }

        // String value (e.g., L_statetext)
        if (metadata.format == null && metadata.factor == null) {
            return ConvertedValue(logical = rawValue, name = name, unit = metadata.unit)
        }

        // Formatted value (e.g., L_usb_stick: "0" → "Aus")
        metadata.format?.let { format ->
            val mappings = format.split("|").associate {
                val (k, v) = it.split(":")
                k to v
            }
            val logical = mappings[rawValue] ?: rawValue
            return ConvertedValue(logical = logical, name = name, unit = metadata.unit)
        }

        // Numeric value (e.g., L_ambient: "219" → "21.9°C")
        metadata.factor?.let { factor ->
            val offset = metadata.offset ?: 0.0
            val decimals = metadata.decimals?.coerceIn(0, 4) ?: getDefaultDecimals(factor) // Limit to 0–4
            try {
                val rawNum = rawValue.toDouble()
                val logicalNum = (rawNum * factor) + offset
                val logicalStr = String.format("%.${decimals}f", logicalNum)
                    .replace(".", DecimalFormatSymbols.getInstance().decimalSeparator.toString())
                return ConvertedValue(
                    logical = logicalStr,
                    name = name,
                    unit = metadata.unit
                )
            } catch (e: NumberFormatException) {
                Logger.w(TAG, "Invalid number format for $key: $rawValue")
                return ConvertedValue(logical = rawValue, name = name, unit = metadata.unit)
            }
        }

        return ConvertedValue(logical = rawValue, name = name, unit = metadata.unit)
    }

    fun getDefaultDecimals(factor: Double): Int = if (isFactorOne(factor)) 0 else 1
    fun isFactorOne(factor: Double): Boolean = factor == 1.0
}