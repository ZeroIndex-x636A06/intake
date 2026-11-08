package com.zeroindex.intake.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The subset of `GET /api/v1/models` the app uses. Unknown fields are ignored
 * by the shared Json instance, so OpenRouter adding fields never breaks this.
 */
@Serializable
internal data class ModelsResponse(val data: List<OrModel> = emptyList())

@Serializable
internal data class OrModel(
    val id: String,
    val name: String = id,
    val description: String = "",
    val created: Long = 0,
    @SerialName("context_length") val contextLength: Long? = null,
    val pricing: OrPricing = OrPricing(),
    val architecture: OrArchitecture = OrArchitecture(),
    @SerialName("supported_parameters") val supportedParameters: List<String> = emptyList(),
)

/** Prices are strings of USD per token (not per million) in the API. */
@Serializable
internal data class OrPricing(
    val prompt: String = "0",
    val completion: String = "0",
    val image: String? = null,
    val request: String? = null,
)

@Serializable
internal data class OrArchitecture(
    @SerialName("input_modalities") val inputModalities: List<String> = emptyList(),
    @SerialName("output_modalities") val outputModalities: List<String> = emptyList(),
)

/** `GET /api/v1/key`: used only to test a key and show its remaining credit. */
@Serializable
internal data class KeyResponse(val data: KeyData)

@Serializable
data class KeyData(
    val label: String? = null,
    val limit: Double? = null,
    @SerialName("limit_remaining") val limitRemaining: Double? = null,
    val usage: Double? = null,
    @SerialName("is_free_tier") val isFreeTier: Boolean = false,
)

/** The on-disk cache of the catalog, so the picker works offline. */
@Serializable
internal data class CatalogCache(val fetchedAt: Long, val data: List<OrModel>)
