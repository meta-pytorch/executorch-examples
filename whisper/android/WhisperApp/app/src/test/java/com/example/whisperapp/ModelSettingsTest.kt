package com.example.whisperapp

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
            tokenizerPath = "/data/local/tmp/whisper/tokenizer.json"
        )
        assertFalse(settings.isValid())
    }

    @Test
    fun `isValid returns false when tokenizer path is empty`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/whisper/model.pte",
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
            modelPath = "/data/local/tmp/whisper/model.pte",
            tokenizerPath = "/data/local/tmp/whisper/tokenizer.json"
        )
        assertTrue(settings.isValid())
    }

    @Test
    fun `isValid returns true even without preprocessor`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/whisper/model.pte",
            tokenizerPath = "/data/local/tmp/whisper/tokenizer.json",
            preprocessorPath = ""
        )
        assertTrue(settings.isValid())
    }

    @Test
    fun `hasPreprocessor returns false when preprocessor is empty`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/whisper/model.pte",
            tokenizerPath = "/data/local/tmp/whisper/tokenizer.json",
            preprocessorPath = ""
        )
        assertFalse(settings.hasPreprocessor())
    }

    @Test
    fun `hasPreprocessor returns false when preprocessor is blank`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/whisper/model.pte",
            tokenizerPath = "/data/local/tmp/whisper/tokenizer.json",
            preprocessorPath = "   "
        )
        assertFalse(settings.hasPreprocessor())
    }

    @Test
    fun `hasPreprocessor returns true when preprocessor is set`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/whisper/model.pte",
            tokenizerPath = "/data/local/tmp/whisper/tokenizer.json",
            preprocessorPath = "/data/local/tmp/whisper/preprocess.pte"
        )
        assertTrue(settings.hasPreprocessor())
    }

    @Test
    fun `default directory constant is correct`() {
        assertEquals("/data/local/tmp/whisper", ModelSettings.DEFAULT_DIRECTORY)
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
            tokenizerPath = "/path/to/tokenizer.json"
        )
        val updated = original.copy(preprocessorPath = "/path/to/preprocess.pte")

        assertEquals("/path/to/model.pte", updated.modelPath)
        assertEquals("/path/to/tokenizer.json", updated.tokenizerPath)
        assertEquals("/path/to/preprocess.pte", updated.preprocessorPath)
        assertEquals("", original.preprocessorPath) // Original unchanged
    }
}
