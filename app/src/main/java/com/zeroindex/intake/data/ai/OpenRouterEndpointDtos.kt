package com.zeroindex.intake.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/v1/models/{id}/endpoints`: the providers that serve one model. */
@Serializable
internal data class EndpointsResponse(val data: EndpointsData)

@Serializable
internal data class EndpointsData(val id: String = "", val endpoints: List<OrEndpoint> = emptyList())

/** `GET /api/v1/endpoints/zdr`: the endpoints that keep nothing after answering. */
@Serializable
internal data class ZdrResponse(val data: List<OrEndpoint> = emptyList())

/** One provider's endpoint for a model. Only what privacy and capability filtering need. */
@Serializable
internal data class OrEndpoint(
    @SerialName("model_id") val modelId: String = "",
    @SerialName("provider_name") val providerName: String = "",
    val tag: String = "",
    @SerialName("supported_parameters") val supportedParameters: List<String> = emptyList(),
    /** 0 is healthy; negative values are degraded, down or retired. */
    val status: Int = 0,
    @SerialName("safety_retention") val safetyRetention: Boolean? = null,
)

/** The on-disk cache of the zero-data-retention list, trimmed to the fields used. */
@Serializable
internal data class ZdrCache(val fetchedAt: Long, val data: List<OrEndpoint>)
