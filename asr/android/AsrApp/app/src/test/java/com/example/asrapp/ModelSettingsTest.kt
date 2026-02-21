package com.example.asrapp

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
            tokenizerPath = "/data/local/tmp/asr/tokenizer.json"
        )
        assertFalse(settings.isValid())
    }

    @Test
    fun `isValid returns false when tokenizer path is empty`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/asr/model.pte",
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
            modelPath = "/data/local/tmp/asr/model.pte",
            tokenizerPath = "/data/local/tmp/asr/tokenizer.json"
        )
        assertTrue(settings.isValid())
    }

    @Test
    fun `isValid returns true even without preprocessor`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/asr/model.pte",
            tokenizerPath = "/data/local/tmp/asr/tokenizer.json",
            preprocessorPath = ""
        )
        assertTrue(settings.isValid())
    }

    @Test
    fun `isValid returns true with data path`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/asr/model.pte",
            tokenizerPath = "/data/local/tmp/asr/tokenizer.model",
            dataPath = "/data/local/tmp/asr/data.ptd"
        )
        assertTrue(settings.isValid())
    }

    @Test
    fun `hasPreprocessor returns false when preprocessor is empty`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/asr/model.pte",
            tokenizerPath = "/data/local/tmp/asr/tokenizer.json",
            preprocessorPath = ""
        )
        assertFalse(settings.hasPreprocessor())
    }

    @Test
    fun `hasPreprocessor returns false when preprocessor is blank`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/asr/model.pte",
            tokenizerPath = "/data/local/tmp/asr/tokenizer.json",
            preprocessorPath = "   "
        )
        assertFalse(settings.hasPreprocessor())
    }

    @Test
    fun `hasPreprocessor returns true when preprocessor is set`() {
        val settings = ModelSettings(
            modelPath = "/data/local/tmp/asr/model.pte",
            tokenizerPath = "/data/local/tmp/asr/tokenizer.json",
            preprocessorPath = "/data/local/tmp/asr/preprocess.pte"
        )
        assertTrue(settings.hasPreprocessor())
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

    @Test
    fun `modelType defaults to PARAKEET`() {
        val settings = ModelSettings()
        assertEquals(ModelType.PARAKEET, settings.modelType)
    }

    @Test
    fun `modelType can be set to WHISPER`() {
        val settings = ModelSettings(modelType = ModelType.WHISPER)
        assertEquals(ModelType.WHISPER, settings.modelType)
    }

    @Test
    fun `copy preserves modelType`() {
        val original = ModelSettings(
            modelPath = "/path/to/model.pte",
            tokenizerPath = "/path/to/tokenizer.json",
            modelType = ModelType.WHISPER
        )
        val updated = original.copy(modelPath = "/path/to/other.pte")

        assertEquals(ModelType.WHISPER, updated.modelType)
        assertEquals("/path/to/other.pte", updated.modelPath)
    }

    @Test
    fun `copy can change modelType`() {
        val original = ModelSettings(modelType = ModelType.WHISPER)
        val updated = original.copy(modelType = ModelType.PARAKEET)

        assertEquals(ModelType.WHISPER, original.modelType)
        assertEquals(ModelType.PARAKEET, updated.modelType)
    }
}
