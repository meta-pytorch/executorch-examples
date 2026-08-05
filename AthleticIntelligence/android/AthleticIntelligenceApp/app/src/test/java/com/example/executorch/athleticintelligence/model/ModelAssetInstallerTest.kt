/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.model

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelAssetInstallerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `replaces a stale cached model even when the byte length is unchanged`() {
        val destination = temporary.root.toPath().resolve("models/rtmw.pte")
        installModelAsset(destination) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }

        installModelAsset(destination) { ByteArrayInputStream(byteArrayOf(4, 5, 6)) }

        assertArrayEquals(byteArrayOf(4, 5, 6), Files.readAllBytes(destination))
    }

    @Test
    fun `failed replacement preserves the last complete model`() {
        val destination = temporary.root.toPath().resolve("models/rtmw.pte")
        installModelAsset(destination) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        var opens = 0

        assertThrows(IOException::class.java) {
            installModelAsset(destination) {
                opens++
                if (opens == 1) ByteArrayInputStream(byteArrayOf(4, 5, 6))
                else throw IOException("copy interrupted")
            }
        }

        assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(destination))
    }
}
