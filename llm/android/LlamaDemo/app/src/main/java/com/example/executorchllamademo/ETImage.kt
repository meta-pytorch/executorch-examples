/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.io.FileNotFoundException

/**
 * Helper class for loading and processing images for vision models.
 */
class ETImage(
    private val contentResolver: ContentResolver,
    val uri: Uri,
    sideSize: Int
) {
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    val bytes: ByteArray = getBytesFromImageURI(uri, sideSize)

    /**
     * Converts the byte array to an int array.
     * The runner expects an int array as input.
     */
    fun getInts(): IntArray {
        return IntArray(bytes.size) { i ->
            bytes[i].toInt() and 0xFF
        }
    }

    /**
     * Converts the byte array to a normalized float array.
     */
    fun getFloats(): FloatArray {
        return FloatArray(bytes.size) { i ->
            ((bytes[i].toInt() and 0xFF) / 255.0f - 0.5f) / 0.5f
        }
    }

    private fun getBytesFromImageURI(uri: Uri, sideSize: Int): ByteArray {
        try {
            val bitmap = resizeImage(uri, sideSize)

            if (bitmap == null) {
                ETLogging.getInstance().log("Unable to get bytes from Image URI. Bitmap is null")
                return ByteArray(0)
            }

            width = bitmap.width
            height = bitmap.height

            val rgbValues = ByteArray(width * height * 3)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    // Get the color of the current pixel
                    val color = bitmap.getPixel(x, y)

                    // Extract the RGB values from the color
                    val red = Color.red(color)
                    val green = Color.green(color)
                    val blue = Color.blue(color)

                    // Store the RGB values in the byte array
                    rgbValues[y * width + x] = red.toByte()
                    rgbValues[(y * width + x) + height * width] = green.toByte()
                    rgbValues[(y * width + x) + 2 * height * width] = blue.toByte()
                }
            }
            return rgbValues
        } catch (e: FileNotFoundException) {
            throw RuntimeException(e)
        }
    }

    private fun resizeImage(uri: Uri, sideSize: Int): Bitmap? {
        val inputStream = contentResolver.openInputStream(uri)
        if (inputStream == null) {
            ETLogging.getInstance().log("Unable to resize image, input streams is null")
            return null
        }
        val bitmap = BitmapFactory.decodeStream(inputStream)
        if (bitmap == null) {
            ETLogging.getInstance().log("Unable to resize image, bitmap during decode stream is null")
            return null
        }

        return Bitmap.createScaledBitmap(bitmap, sideSize, sideSize, false)
    }
}
