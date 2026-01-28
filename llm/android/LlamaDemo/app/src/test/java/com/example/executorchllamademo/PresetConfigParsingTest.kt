/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for preset config JSON parsing logic.
 */
class PresetConfigParsingTest {

    /**
     * Helper function to parse model JSON - mirrors the logic in PresetConfigManager.
     */
    private fun parseModelsJson(json: String): Map<String, ModelInfo> {
        val result = linkedMapOf<String, ModelInfo>()

        try {
            val root = JSONObject(json)
            val models = root.optJSONObject("models") ?: return emptyMap()

            val keys = models.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                try {
                    val modelObj = models.getJSONObject(key)
                    val modelInfo = parseModelInfo(modelObj)
                    if (modelInfo != null) {
                        result[key] = modelInfo
                    }
                } catch (e: Exception) {
                    // Skip invalid entries
                }
            }
        } catch (e: Exception) {
            // Return empty on parse failure
        }

        return result
    }

    private fun parseModelInfo(obj: JSONObject): ModelInfo? {
        val displayName = obj.optString("displayName").takeIf { it.isNotEmpty() } ?: return null
        val modelUrl = obj.optString("modelUrl").takeIf { it.isNotEmpty() } ?: return null
        val modelFilename = obj.optString("modelFilename").takeIf { it.isNotEmpty() } ?: return null
        val tokenizerUrl = obj.optString("tokenizerUrl", "")
        val tokenizerFilename = obj.optString("tokenizerFilename", "")

        val modelTypeStr = obj.optString("modelType", "LLAMA_3")
        val modelType = try {
            ModelType.valueOf(modelTypeStr)
        } catch (e: IllegalArgumentException) {
            ModelType.LLAMA_3
        }

        return ModelInfo(
            displayName = displayName,
            modelUrl = modelUrl,
            modelFilename = modelFilename,
            tokenizerUrl = tokenizerUrl,
            tokenizerFilename = tokenizerFilename,
            modelType = modelType
        )
    }

    @Test
    fun testParseValidJson() {
        val json = """
            {
              "models": {
                "test": {
                  "displayName": "Test Model",
                  "modelUrl": "https://example.com/model.pte",
                  "modelFilename": "model.pte",
                  "tokenizerUrl": "https://example.com/tokenizer.model",
                  "tokenizerFilename": "tokenizer.model",
                  "modelType": "LLAMA_3"
                }
              }
            }
        """.trimIndent()

        val models = parseModelsJson(json)

        assertEquals(1, models.size)
        assertTrue(models.containsKey("test"))

        val model = models["test"]!!
        assertEquals("Test Model", model.displayName)
        assertEquals("https://example.com/model.pte", model.modelUrl)
        assertEquals("model.pte", model.modelFilename)
        assertEquals("https://example.com/tokenizer.model", model.tokenizerUrl)
        assertEquals("tokenizer.model", model.tokenizerFilename)
        assertEquals(ModelType.LLAMA_3, model.modelType)
    }

    @Test
    fun testParseMultipleModels() {
        val json = """
            {
              "models": {
                "model1": {
                  "displayName": "Model 1",
                  "modelUrl": "https://example.com/model1.pte",
                  "modelFilename": "model1.pte",
                  "tokenizerUrl": "https://example.com/tokenizer1.model",
                  "tokenizerFilename": "tokenizer1.model",
                  "modelType": "LLAMA_3"
                },
                "model2": {
                  "displayName": "Model 2",
                  "modelUrl": "https://example.com/model2.pte",
                  "modelFilename": "model2.pte",
                  "tokenizerUrl": "https://example.com/tokenizer2.json",
                  "tokenizerFilename": "tokenizer2.json",
                  "modelType": "GEMMA_3"
                }
              }
            }
        """.trimIndent()

        val models = parseModelsJson(json)

        assertEquals(2, models.size)
        assertTrue(models.containsKey("model1"))
        assertTrue(models.containsKey("model2"))

        assertEquals(ModelType.LLAMA_3, models["model1"]?.modelType)
        assertEquals(ModelType.GEMMA_3, models["model2"]?.modelType)
    }

    @Test
    fun testParseEmptyJson() {
        val json = """{}"""
        val models = parseModelsJson(json)
        assertTrue(models.isEmpty())
    }

    @Test
    fun testParseEmptyModels() {
        val json = """{"models": {}}"""
        val models = parseModelsJson(json)
        assertTrue(models.isEmpty())
    }

    @Test
    fun testParseMissingRequiredField() {
        val json = """
            {
              "models": {
                "invalid": {
                  "displayName": "Test",
                  "modelFilename": "model.pte"
                }
              }
            }
        """.trimIndent()

        val models = parseModelsJson(json)
        assertTrue(models.isEmpty())
    }

    @Test
    fun testParseUnknownModelType() {
        val json = """
            {
              "models": {
                "test": {
                  "displayName": "Test Model",
                  "modelUrl": "https://example.com/model.pte",
                  "modelFilename": "model.pte",
                  "tokenizerUrl": "",
                  "tokenizerFilename": "",
                  "modelType": "UNKNOWN_TYPE"
                }
              }
            }
        """.trimIndent()

        val models = parseModelsJson(json)

        assertEquals(1, models.size)
        assertEquals(ModelType.LLAMA_3, models["test"]?.modelType)
    }

    @Test
    fun testParseSkipsInvalidEntries() {
        val json = """
            {
              "models": {
                "valid": {
                  "displayName": "Valid Model",
                  "modelUrl": "https://example.com/model.pte",
                  "modelFilename": "model.pte",
                  "tokenizerUrl": "",
                  "tokenizerFilename": "",
                  "modelType": "LLAMA_3"
                },
                "invalid": {
                  "displayName": "Invalid Model"
                }
              }
            }
        """.trimIndent()

        val models = parseModelsJson(json)

        assertEquals(1, models.size)
        assertTrue(models.containsKey("valid"))
    }

    @Test
    fun testParseInvalidJson() {
        val json = "not valid json"
        val models = parseModelsJson(json)
        assertTrue(models.isEmpty())
    }

    @Test
    fun testParseOptionalTokenizer() {
        val json = """
            {
              "models": {
                "test": {
                  "displayName": "Test Model",
                  "modelUrl": "https://example.com/model.pte",
                  "modelFilename": "model.pte"
                }
              }
            }
        """.trimIndent()

        val models = parseModelsJson(json)

        assertEquals(1, models.size)
        val model = models["test"]!!
        assertEquals("", model.tokenizerUrl)
        assertEquals("", model.tokenizerFilename)
    }
}
