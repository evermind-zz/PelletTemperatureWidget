package com.example.pelletswaterheater.utils

import android.content.Context
import kotlinx.serialization.KSerializer
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf

/**
 * Represents JSON metadata with dynamic sections and an optional error field.
 * Uses a custom serializer to handle root-level sections (e.g., "system", "hk1").
 */
@Serializable(with = MetadataJsonSerializer::class)
data class MetadataJson(
    val sections: Map<String, Map<String, MetadataField>> = emptyMap(),
    val error: String? = null
)

/**
 * Represents a metadata field with value and optional attributes like unit, factor, etc.
 */
@Serializable
data class MetadataField(
    val value: String,
    val unit: String? = null,
    val factor: Double? = null,
    val offset: Double? = null,
    val name: String? = null,
    val format: String? = null,
    val min: String? = null,
    val max: String? = null,
    val decimals: Int? = null
)

/**
 * Custom serializer for [MetadataJson] to handle JSON with root-level sections.
 * Deserializes dynamic keys (e.g., "system", "hk1") as sections and "error" as a field.
 * Serializes sections as root-level keys and includes "error" if present.
 */
object MetadataJsonSerializer : KSerializer<MetadataJson> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MetadataJson")

    // Custom serializer needed because JSON has no root object; sections (e.g., "system", "hk1") are top-level keys.
    override fun serialize(encoder: Encoder, value: MetadataJson) {
        require(encoder is JsonEncoder) { "MetadataJsonSerializer requires JsonEncoder" }
        val jsonObject = buildJsonObject {
            value.sections.forEach { (section, fields) ->
                put(
                    section,
                    encoder.json.encodeToJsonElement(
                        MapSerializer(String.serializer(), MetadataField.serializer()),
                        fields
                    )
                )
            }
            value.error?.let { put("error", it) }
        }
        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): MetadataJson {
        require(decoder is JsonDecoder) { "MetadataJsonSerializer requires JsonDecoder" }
        val jsonObject = decoder.decodeJsonElement().jsonObject
        var error: String? = null
        val sections = mutableMapOf<String, Map<String, MetadataField>>()

        // Parse each root-level key; treat non-"error" keys as sections.
        jsonObject.forEach { (key, value) ->
            if (key == "error") {
                error = value.jsonPrimitive.contentOrNull
            } else {
                try {
                    sections[key] = decoder.json.decodeFromJsonElement(
                        MapSerializer(String.serializer(), MetadataField.serializer()),
                        value
                    )
                } catch (e: Exception) {
                    Logger.e("MetadataJsonSerializer", "Failed to deserialize section $key: $e", e)
                }
            }
        }

        return MetadataJson(sections, error)
    }
}

/**
 * Utility object for parsing, saving, and merging metadata JSON.
 */
object MetadataUtils {
    private val TAG = MetadataUtils::class.java.simpleName
    private const val METADATA_FILE = "metadata.json"
    private val json = Json {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            serializersModuleOf(MapSerializer(String.serializer(), MetadataField.serializer()))
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

        val merged = mutableMapOf<String, Map<String, MetadataField>>()
        val allSections = (server.sections.keys + user.sections.keys).distinct()
        allSections.forEach { section ->
            val serverSection = server.sections[section]
            val userSection = user.sections[section]
            if (userSection != null) {
                val mergedSection = mutableMapOf<String, MetadataField>()
                userSection.forEach { (key, value) ->
                    mergedSection[key] = value
                }
                serverSection?.forEach { (key, _) ->
                    if (!mergedSection.containsKey(key)) {
                        Logger.w(TAG, "Dropped unmapped field: $section.$key")
                    }
                }
                if (mergedSection.isNotEmpty()) {
                    merged[section] = mergedSection
                }
            } else if (serverSection != null) {
                merged[section] = serverSection
            }
        }
        return MetadataJson(sections = merged, error = server.error ?: user.error)
    }
}