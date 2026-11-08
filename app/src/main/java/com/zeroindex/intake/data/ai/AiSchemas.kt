package com.zeroindex.intake.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A tiny builder for strict JSON Schemas. Strict structured output requires
 * every property to be listed as required and additionalProperties to be
 * false, so optional values are expressed as nullable types instead - this
 * makes that the only way to write one.
 */
internal object S {
    fun obj(vararg props: Pair<String, JsonElement>): JsonObject = JsonObject(
        mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(props.toMap()),
            "required" to JsonArray(props.map { JsonPrimitive(it.first) }),
            "additionalProperties" to JsonPrimitive(false),
        ),
    )

    fun array(items: JsonElement) = JsonObject(mapOf("type" to JsonPrimitive("array"), "items" to items))
    fun string(description: String? = null) = typed("string", description)
    fun number(description: String? = null) = typed("number", description)
    fun integer(description: String? = null) = typed("integer", description)
    fun boolean(description: String? = null) = typed("boolean", description)
    fun nullableString(description: String? = null) = nullable("string", description)
    fun nullableNumber(description: String? = null) = nullable("number", description)
    fun enumOf(vararg values: String) = JsonObject(
        mapOf("type" to JsonPrimitive("string"), "enum" to JsonArray(values.map(::JsonPrimitive))),
    )

    private fun typed(type: String, description: String?) = JsonObject(
        buildMap {
            put("type", JsonPrimitive(type))
            description?.let { put("description", JsonPrimitive(it)) }
        },
    )

    private fun nullable(type: String, description: String?) = JsonObject(
        buildMap {
            put("type", JsonArray(listOf(JsonPrimitive(type), JsonPrimitive("null"))))
            description?.let { put("description", JsonPrimitive(it)) }
        },
    )
}
