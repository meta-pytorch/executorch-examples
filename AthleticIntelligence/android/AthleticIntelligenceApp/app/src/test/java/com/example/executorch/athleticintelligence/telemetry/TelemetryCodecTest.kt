/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.telemetry

import com.example.executorch.athleticintelligence.activity.ActivityType
import com.example.executorch.athleticintelligence.activity.SessionCompletion
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryCodecTest {
    private val codec = TelemetryCodec()

    @Test
    fun `encodes snake case squat envelope with schema version 2`() {
        val root = codec.parseJsonObject(codec.encodeEnvelope(squatEnvelope()))

        assertEquals(2, root.requireInt("schema_version"))
        assertEquals("frame", root.requireString("event_type"))
        assertEquals("squat", root.requirePayloadType())
        assertTrue(root.containsKey("session_elapsed_ms"))
        assertFalse(root.containsKey("sessionElapsedMs"))
    }

    @Test
    fun `round trips structured squat alerts and metric availability`() {
        val envelope = squatEnvelope()

        val encoded = codec.encodeEnvelope(envelope)
        val decoded = codec.decodeEnvelope(encoded)
        val payload = decoded.payload as SquatTelemetryPayload
        val alertJson = codec.parseJsonObject(encoded).getValue("payload").jsonObject
            .getValue("alerts").jsonArray.single().jsonObject

        assertEquals(envelope, decoded)
        assertEquals("DANGER", alertJson.requireString("level"))
        assertEquals(2, alertJson.requireInt("severity"))
        assertEquals(0.08, alertJson.getValue("value").jsonPrimitive.double, 0.0)
        assertEquals("NOT_APPLICABLE", payload.metricAvailability["heel_lift"])
    }

    @Test
    fun `summary details round trip selected view and annotated video`() {
        val summary = SessionSummaryRecord(
            sessionId = "video-session",
            activityType = ActivityType.SQUATS,
            telemetryFileName = "video-session.jsonl",
            completion = SessionCompletion.COMPLETE,
            frameCount = 7L,
            startedAtUtc = "2026-07-12T23:00:00Z",
            endedAtUtc = "2026-07-12T23:00:30Z",
            details = SquatSessionDetails("side", "video-session.mp4"),
        )

        val decoded = codec.decodeSummary(codec.encodeSummary(summary))
        val details = decoded.details as SquatSessionDetails

        assertEquals("side", details.view)
        assertEquals("video-session.mp4", details.annotatedVideoFileName)
    }

    @Test
    fun `legacy schema 1 squat string alerts migrate to structured alerts`() {
        val legacyJson = """
            {
              "schema_version":1,
              "event_type":"frame",
              "timestamp_utc":"2026-07-12T23:00:00Z",
              "session_elapsed_ms":10,
              "frame_index":1,
              "source":"live_camera",
              "activity_type":"SQUATS",
              "payload":{"payload_type":"squat","phase":"DESCENDING","alerts":["legacy warning"]}
            }
        """.trimIndent()

        val payload = codec.decodeEnvelope(legacyJson).payload as SquatTelemetryPayload

        assertEquals("legacy warning", payload.alerts.single().message)
        assertEquals(1, payload.alerts.single().severity)
    }

    private fun squatEnvelope() = TelemetryEnvelope(
        timestampUtc = "2026-07-12T23:00:01Z",
        sessionElapsedMs = 20L,
        frameIndex = 2L,
        source = TelemetrySource.LIVE_CAMERA,
        activityType = ActivityType.SQUATS,
        payload = SquatTelemetryPayload(
            phase = "DESCENDING",
            alerts = listOf(
                SquatTelemetryAlert(
                    level = "DANGER",
                    severity = 2,
                    metric = "knee_valgus_l",
                    message = "Keep knees tracking over toes",
                    value = 0.08,
                    viewLabel = "Front",
                ),
            ),
            metrics = mapOf("knee_valgus_l" to 0.08),
            metricAvailability = mapOf("heel_lift" to "NOT_APPLICABLE"),
        ),
    )

    private fun JsonObject.requireInt(key: String): Int = getValue(key).jsonPrimitive.int
    private fun JsonObject.requireString(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.requirePayloadType(): String =
        getValue("payload").jsonObject.getValue("payload_type").jsonPrimitive.content
}
