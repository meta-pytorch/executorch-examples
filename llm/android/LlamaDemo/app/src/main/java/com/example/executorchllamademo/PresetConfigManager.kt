/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages loading and parsing of preset model configurations from JSON.
 * Supports loading from bundled assets, local cache, or remote URL.
 */
class PresetConfigManager(private val context: Context) {

    companion object {
        private const val TAG = "PresetConfigManager"
        private const val ASSET_FILENAME = "preset_models.json"
        private const val CACHE_FILENAME = "preset_models_cache.json"
        private const val PREFS_NAME = "preset_config_prefs"
        private const val PREF_CUSTOM_URL = "custom_config_url"
    }

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILENAME)

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the currently configured custom URL, or null if using default.
     */
    fun getCustomConfigUrl(): String? {
        return prefs.getString(PREF_CUSTOM_URL, null)
    }

    /**
     * Saves a custom config URL to preferences.
     */
    fun setCustomConfigUrl(url: String?) {
        prefs.edit().apply {
            if (url.isNullOrBlank()) {
                remove(PREF_CUSTOM_URL)
            } else {
                putString(PREF_CUSTOM_URL, url)
            }
            apply()
        }
    }

    /**
     * Loads models from the current configuration source.
     * Priority: cached config (if custom URL was loaded) -> bundled asset
     */
    fun loadModels(): Map<String, ModelInfo> {
        // If we have a cached config from a custom URL, use it
        if (cacheFile.exists() && getCustomConfigUrl() != null) {
            try {
                val json = cacheFile.readText()
                val models = parseModelsJson(json)
                if (models.isNotEmpty()) {
                    Log.d(TAG, "Loaded ${models.size} models from cache")
                    return models
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load cached config, falling back to asset", e)
            }
        }

        // Fall back to bundled asset
        return loadFromAsset()
    }

    /**
     * Loads models from the bundled asset file.
     */
    private fun loadFromAsset(): Map<String, ModelInfo> {
        return try {
            val json = context.assets.open(ASSET_FILENAME).bufferedReader().use { it.readText() }
            val models = parseModelsJson(json)
            Log.d(TAG, "Loaded ${models.size} models from asset")
            models
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load models from asset", e)
            emptyMap()
        }
    }

    /**
     * Downloads config from a URL and caches it locally.
     * Returns the parsed models, or null if download/parse failed.
     */
    suspend fun loadFromUrl(url: String): Result<Map<String, ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    Exception("HTTP error: $responseCode ${connection.responseMessage}")
                )
            }

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val models = parseModelsJson(json)

            if (models.isEmpty()) {
                return@withContext Result.failure(Exception("No valid models found in config"))
            }

            // Cache the config and save the URL
            cacheFile.writeText(json)
            setCustomConfigUrl(url)

            Log.d(TAG, "Loaded ${models.size} models from URL: $url")
            Result.success(models)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config from URL: $url", e)
            Result.failure(e)
        }
    }

    /**
     * Resets to the default bundled configuration.
     * Clears the cached config and custom URL.
     */
    fun resetToDefault(): Map<String, ModelInfo> {
        // Delete cached config
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        // Clear custom URL
        setCustomConfigUrl(null)

        Log.d(TAG, "Reset to default configuration")
        return loadFromAsset()
    }

    /**
     * Parses the JSON string into a map of ModelInfo objects.
     * Handles invalid entries gracefully by skipping them.
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
                    } else {
                        Log.w(TAG, "Skipping invalid model entry: $key")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing model entry '$key': ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing models JSON", e)
        }

        return result
    }

    /**
     * Parses a single model JSON object into a ModelInfo.
     * Returns null if required fields are missing or invalid.
     */
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
            Log.w(TAG, "Unknown model type '$modelTypeStr', defaulting to LLAMA_3")
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
}
