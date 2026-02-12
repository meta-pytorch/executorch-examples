package com.example.parakeetapp

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ModelSettings data class.
 */
class ModelSettingsTest {

    @Test
    fun `isValid returns false when model path is empty`() {
        val settings = ModelSettings(
            modelPath = "",
            tokenizerPath = "/data/local/tmp/parakeet/tokenizer.model"
        )
        assertFalse(settings.isValid())
    }

    @Test
    fun `isValid returns false when tokenizer path is empty`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/parakeet/model.pte",
            tokenizerPath = ""
        )
        assertFalse(settings.isValid())
    }

    @Test
    fun `isValid returns false when both paths are empty`() {
        val settings = ModelSettings()
        assertFalse(settings.isValid())
    }

    @Test
    fun `isValid returns true when model and tokenizer are set`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/parakeet/model.pte",
            tokenizerPath = "/data/local/tmp/parakeet/tokenizer.model"
        )
        assertTrue(settings.isValid())
    }

    @Test
    fun `isValid returns true with data path`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/parakeet/model.pte",
            tokenizerPath = "/data/local/tmp/parakeet/tokenizer.model",
            dataPath = "/data/local/tmp/parakeet/data.ptd"
        )
        assertTrue(settings.isValid())
    }

    @Test
    fun `default directory constant is correct`() {
        assertEquals("/data/local/tmp/parakeet", ModelSettings.DEFAULT_DIRECTORY)
    }

    @Test
    fun `model extensions include pte`() {
        assertTrue(ModelSettings.MODEL_EXTENSIONS.contains(".pte"))
    }

    @Test
    fun `tokenizer extensions include json bin and model`() {
        assertTrue(ModelSettings.TOKENIZER_EXTENSIONS.contains(".json"))
        assertTrue(ModelSettings.TOKENIZER_EXTENSIONS.contains(".bin"))
        assertTrue(ModelSettings.TOKENIZER_EXTENSIONS.contains(".model"))
    }

    @Test
    fun `data extensions include ptd`() {
        assertTrue(ModelSettings.DATA_EXTENSIONS.contains(".ptd"))
    }

    @Test
    fun `copy creates new instance with updated values`() {
        val original = ModelSettings(
            modelPath = "/path/to/model.pte",
            tokenizerPath = "/path/to/tokenizer.model"
        )
        val updated = original.copy(dataPath = "/path/to/data.ptd")

        assertEquals("/path/to/model.pte", updated.modelPath)
        assertEquals("/path/to/tokenizer.model", updated.tokenizerPath)
        assertEquals("/path/to/data.ptd", updated.dataPath)
        assertEquals("", original.dataPath) // Original unchanged
    }
}
