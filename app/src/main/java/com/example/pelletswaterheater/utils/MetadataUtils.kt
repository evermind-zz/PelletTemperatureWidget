package com.example.pelletswaterheater.utils

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModule

/**
 * Represents either a structured field or a simple string label.
 */
@Serializable
sealed class MetadataEntry

/**
 * Represents a metadata field with value and optional attributes like unit, factor, etc.
 */
@Serializable
@SerialName("field")
data class MetadataField(
    @SerialName("val")
    val value: String,
    val unit: String? = null,
    val factor: Double? = null,
    val offset: Double? = null,
    val name: String? = null,
    val format: String? = null,
    val min: String? = null,
    val max: String? = null,
    val decimals: Int? = null
) : MetadataEntry()

/**
 * Represents a simple metadata label (plain string).
 */
@Serializable
@SerialName("label")
data class MetadataLabel(val label: String) : MetadataEntry()

/**
 * Represents JSON metadata with dynamic sections and an optional error field.
 * Uses a custom serializer to handle root-level sections (e.g., "system", "hk1").
 */
@Serializable(with = MetadataJsonSerializer::class)
data class MetadataJson(
    val sections: Map<String, Map<String, MetadataEntry>> = emptyMap(),
    val error: String? = null
)

object MetadataJsonSerializer : KSerializer<MetadataJson> {
    private val serializerClass = MetadataJsonSerializer::class.java.simpleName
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MetadataJson")

    override fun serialize(encoder: Encoder, value: MetadataJson) {
        require(encoder is JsonEncoder) { "$serializerClass requires JsonEncoder" }
        val jsonObject = buildJsonObject {
            value.sections.forEach { (section, fields) ->
                put(
                    section,
                    encoder.json.encodeToJsonElement(
                        MapSerializer(
                            String.serializer(),
                            MetadataEntry.serializer()
                        ), fields
                    )
                )
            }
            value.error?.let { put("error", JsonPrimitive(it)) }
        }
        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): MetadataJson {
        require(decoder is JsonDecoder) { "$serializerClass requires JsonDecoder" }
        val jsonObject = decoder.decodeJsonElement().jsonObject

        val sections = mutableMapOf<String, Map<String, MetadataEntry>>()
        var error: String? = null

        // Parse each root-level key; treat non-"error" keys as sections.
        jsonObject.forEach { (sectionKey, sectionValue) ->
            if (sectionKey == "error") {
                // Error is currently a simple string; handle structured errors if API changes
                error = sectionValue.jsonPrimitive?.content ?: sectionValue.toString()
                return@forEach
            }

            val fieldMap = mutableMapOf<String, MetadataEntry>()

            sectionValue.jsonObject.forEach { (fieldKey, fieldValue) ->
                val entry: MetadataEntry = try {
                    if (fieldValue is JsonPrimitive && fieldValue.isString) {
                        MetadataLabel(fieldValue.content)
                    } else {
                        decoder.json.decodeFromJsonElement(MetadataField.serializer(), fieldValue)
                    }
                } catch (e: Exception) {
                    Logger.e(serializerClass, "Failed to parse entry $sectionKey.$fieldKey", e)
                    return@forEach
                }
                fieldMap[fieldKey] = entry
            }

            sections[sectionKey] = fieldMap
        }

        return MetadataJson(sections = sections, error = error)
    }
}

object MetadataUtils {
    private val TAG = MetadataUtils::class.java.simpleName
    private const val METADATA_FILE = "metadata.json"
    private val json = Json {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            // Not strictly needed now, since we're using @Serializable on MetadataEntry
        }
    }

    /**
     * Parses server JSON into [MetadataJson].
     *
     * @param jsonString The JSON string from the server.
     * @return The parsed [MetadataJson] or null if parsing fails.
     */
    fun parseServerJson(jsonString: String): MetadataJson? {
        return try {
            json.decodeFromString<MetadataJson>(jsonString)
        } catch (e: Exception) {
            Logger.e(TAG, "Server JSON parse error: $e", e)
            null
        }
    }

    /**
     * Parses user JSON into [MetadataJson].
     *
     * @param jsonString The JSON string from the user.
     * @return The parsed [MetadataJson] or null if parsing fails.
     */
    fun parseUserJson(jsonString: String): MetadataJson? {
        return try {
            json.decodeFromString<MetadataJson>(jsonString)
        } catch (e: Exception) {
            Logger.e(TAG, "User JSON parse error: $e", e)
            null
        }
    }

    /**
     * Saves metadata JSON to internal storage.
     *
     * @param context The application context.
     * @param jsonString The JSON string to save.
     */
    fun saveMetadata(context: Context, jsonString: String) {
        try {
            context.filesDir.resolve(METADATA_FILE).writeText(jsonString)
        } catch (e: Exception) {
            Logger.e(TAG, "Metadata save error: $e", e)
        }
    }

    /**
     * Loads cached metadata from internal storage.
     *
     * @param context The application context.
     * @return The parsed [MetadataJson] or null if loading fails.
     */
    fun loadCachedMetadata(context: Context): MetadataJson? {
        return try {
            val file = context.filesDir.resolve(METADATA_FILE)
            if (file.exists()) {
                json.decodeFromString<MetadataJson>(file.readText())
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Cached metadata load error: $e", e)
            null
        }
    }

    /**
     * Merges server and user metadata, prioritizing user fields.
     *
     * @param server The server [MetadataJson] or null.
     * @param user The user [MetadataJson] or null.
     * @return The merged [MetadataJson].
     */
    fun mergeMetadata(server: MetadataJson?, user: MetadataJson?): MetadataJson {
        if (user == null) return server ?: MetadataJson()
        if (server == null) return user

        val mergedSections = mutableMapOf<String, Map<String, MetadataEntry>>()
        val allSectionNames = (server.sections.keys + user.sections.keys).distinct()

        for (section in allSectionNames) {
            val serverSection = server.sections[section]
            val userSection = user.sections[section]

            val mergedFieldMap = mutableMapOf<String, MetadataEntry>()

            if (userSection != null) {
                // Copy all user fields (overwrite)
                mergedFieldMap.putAll(userSection)

                // Optionally warn about dropped server fields
                serverSection?.forEach { (key, _) ->
                    if (!mergedFieldMap.containsKey(key)) {
                        Logger.w(TAG, "Dropped unmapped field: $section.$key")
                    }
                }

                if (mergedFieldMap.isNotEmpty()) {
                    mergedSections[section] = mergedFieldMap
                }
            } else if (serverSection != null) {
                mergedSections[section] = serverSection
            }
        }

        return MetadataJson(
            sections = mergedSections,
            error = server.error ?: user.error
        )
    }

    /**
     * Parses server data (value-focused) JSON into [DataValuesJson].
     */
    fun parseDataValuesJson(jsonString: String): DataValuesJson? {
        return try {
            json.decodeFromString<DataValuesJson>(jsonString)
        } catch (e: Exception) {
            Logger.e(TAG, "Data JSON parse error: $e", e)
            null
        }
    }
}