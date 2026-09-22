package org.opensources.umai.core.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement

/**
 * `RecipeSummary.image` is typed as `anyOf[{}, null]` in the OpenAPI schema and
 * is sent as a bare string or a number depending on the Mealie version. Umai
 * only uses it as a cache-busting token, so any scalar is read as text.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object ScalarAsStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ScalarAsString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element: JsonElement = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}
