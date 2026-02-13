package com.example.parakeetapp

/**
 * Data class representing the model file settings for Parakeet inference.
 *
 * @param modelPath Path to the main Parakeet model (.pte file)
 * @param tokenizerPath Path to the tokenizer file (.json, .bin, or .model file)
 * @param dataPath Optional path to external data file (.ptd file)
 */
data class ModelSettings(
    val modelPath: String = "",
    val tokenizerPath: String = "",
    val dataPath: String = ""
) {
    /**
     * Check if the minimum required files are set for inference.
     * Model and tokenizer are required.
     */
    fun isValid(): Boolean {
        return modelPath.isNotBlank() && tokenizerPath.isNotBlank()
    }

    companion object {
        const val DEFAULT_DIRECTORY = "/data/local/tmp/parakeet"
        val MODEL_EXTENSIONS = arrayOf(".pte")
        val TOKENIZER_EXTENSIONS = arrayOf(".json", ".bin", ".model")
        val DATA_EXTENSIONS = arrayOf(".ptd")
    }
}
