package com.example.asr

/**
 * Data class representing the model file settings for ASR inference.
 *
 * @param modelPath Path to the main model (.pte file)
 * @param tokenizerPath Path to the tokenizer file (.json, .bin, or .model file)
 * @param preprocessorPath Optional path to the preprocessor model (.pte file).
 *                         If empty, raw WAV audio will be used directly.
 * @param dataPath Optional path to external data file (.ptd file)
 */
data class ModelSettings(
    val modelPath: String = "",
    val tokenizerPath: String = "",
    val preprocessorPath: String = "",
    val dataPath: String = ""
) {
    /**
     * Check if the minimum required files are set for inference.
     * Model and tokenizer are required. Preprocessor is optional.
     */
    fun isValid(): Boolean {
        return modelPath.isNotBlank() && tokenizerPath.isNotBlank()
    }

    /**
     * Check if preprocessor is configured.
     * If false, raw WAV audio should be used directly without mel-spectrogram conversion.
     */
    fun hasPreprocessor(): Boolean {
        return preprocessorPath.isNotBlank()
    }

    companion object {
        val MODEL_EXTENSIONS = arrayOf(".pte")
        val TOKENIZER_EXTENSIONS = arrayOf(".json", ".bin", ".model")
        val DATA_EXTENSIONS = arrayOf(".ptd")
    }
}
