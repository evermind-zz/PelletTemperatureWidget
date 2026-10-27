package com.example.pelletswaterheater.utils

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable(with = DataJsonSerializer::class)
data class DataValuesJson(
    val sections: Map<String, Map<String, String>> = emptyMap(),
    val error: String? = null
)

object DataJsonSerializer : KSerializer<DataValuesJson> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("DataValuesJson") {
            element<Map<String, Map<String, String>>>("sections")
            element<String>("error", isOptional = true)
        }

    override fun deserialize(decoder: Decoder): DataValuesJson {
        require(decoder is JsonDecoder) // Ensure JSON input

        val jsonObj = decoder.decodeJsonElement().jsonObject
        val sections = mutableMapOf<String, Map<String, String>>()
        var error: String? = null

        for ((key, element) in jsonObj) {
            if (key == "error") {
                error = element.toString()
            } else {
                // Ensure the value is a JsonObject of string-to-string mappings
                val sectionMap = element.jsonObject.mapValues { it.value.jsonPrimitive.content }
                sections[key] = sectionMap
            }
        }

        return DataValuesJson(sections, error)
    }

    override fun serialize(encoder: Encoder, value: DataValuesJson) {
        require(encoder is JsonEncoder)

        val content = buildJsonObject {
            value.sections.forEach { (sectionName, valuesMap) ->
                put(sectionName, buildJsonObject {
                    valuesMap.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                })
            }
            value.error?.let { put("error", JsonPrimitive(it)) }
        }

        encoder.encodeJsonElement(content)
    }
}
