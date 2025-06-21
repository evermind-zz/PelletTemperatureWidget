package com.example.pelletswaterheater.utils

import android.content.Context
import android.util.Log
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

@Serializable(with = MetadataJsonSerializer::class)
data class MetadataJson(
    val sections: Map<String, Map<String, MetadataField>> = emptyMap(),
    val error: String? = null
)

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

object MetadataJsonSerializer : KSerializer<MetadataJson> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MetadataJson")

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
                    Log.e("MetadataJsonSerializer", "Failed to deserialize section $key: $e")
                }
            }
        }

        return MetadataJson(sections, error)
    }
}

object MetadataUtils {
    private val TAG = MetadataUtils::class.java.simpleName
    private const val METADATA_FILE = "metadata.json"
    private val json = Json {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            serializersModuleOf(MapSerializer(String.serializer(), MetadataField.serializer()))
        }
    }

    fun parseServerJson(jsonString: String): MetadataJson? {
        return try {
            json.decodeFromString<MetadataJson>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Server JSON parse error: $e")
            null
        }
    }

    fun parseUserJson(jsonString: String): MetadataJson? {
        return try {
            json.decodeFromString<MetadataJson>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "User JSON parse error: $e")
            null
        }
    }

    fun saveMetadata(context: Context, jsonString: String) {
        try {
            context.filesDir.resolve(METADATA_FILE).writeText(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Metadata save error: $e")
        }
    }

    fun loadCachedMetadata(context: Context): MetadataJson? {
        return try {
            val file = context.filesDir.resolve(METADATA_FILE)
            if (file.exists()) {
                json.decodeFromString<MetadataJson>(file.readText())
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cached metadata load error: $e")
            null
        }
    }

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
