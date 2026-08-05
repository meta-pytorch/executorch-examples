/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.telemetry

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class TelemetryCodec(
    private val json: Json = deterministicJson,
) {
    fun encodeEnvelope(envelope: TelemetryEnvelope): String =
        json.encodeToString(TelemetryEnvelope.serializer(), envelope)

    fun decodeEnvelope(encoded: String): TelemetryEnvelope {
        val root = json.parseToJsonElement(encoded).jsonObject
        val payload = root["payload"] as? JsonObject
        val alerts = payload?.get("alerts") as? JsonArray
        if (alerts == null || alerts.none { it is JsonPrimitive && it.isString }) {
            return json.decodeFromJsonElement(TelemetryEnvelope.serializer(), root)
        }

        val migratedAlerts = JsonArray(
            alerts.map { alert ->
                if (alert is JsonPrimitive && alert.isString) {
                    buildJsonObject {
                        put("level", "WARNING")
                        put("severity", 1)
                        put("message", alert.content)
                    }
                } else {
                    alert
                }
            },
        )
        val migratedPayload = JsonObject(payload.toMutableMap().apply {
            this["alerts"] = migratedAlerts
        })
        val migratedRoot = JsonObject(root.toMutableMap().apply {
            this["payload"] = migratedPayload
        })
        return json.decodeFromJsonElement(TelemetryEnvelope.serializer(), migratedRoot)
    }

    fun encodeSummary(summary: SessionSummaryRecord): String =
        json.encodeToString(SessionSummaryRecord.serializer(), summary)

    fun decodeSummary(encoded: String): SessionSummaryRecord =
        json.decodeFromString(SessionSummaryRecord.serializer(), encoded)

    fun parseJsonObject(encoded: String): JsonObject =
        json.parseToJsonElement(encoded).jsonObject

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val deterministicJson: Json =
            Json {
                encodeDefaults = true
                explicitNulls = false
                ignoreUnknownKeys = false
                prettyPrint = false
                classDiscriminator = "payload_type"
            }
    }
}
