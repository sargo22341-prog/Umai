package org.opensources.umai.llm.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Reads what a language model was asked to answer as a JSON object. */
object ModelAnswer {

    /** The JSON object [answer] holds; `null` when it holds anything else, or no JSON at all. */
    fun objectOrNull(answer: String): JsonObject? = try {
        Json.parseToJsonElement(answer) as? JsonObject
    } catch (_: SerializationException) {
        null
    }
}
