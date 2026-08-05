/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.model

import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Installs a model by content, without exposing a stale or partially copied file. */
internal fun installModelAsset(destination: Path, openSource: () -> InputStream): Path {
    val sourceDigest = openSource().use(::sha256)
    if (Files.isRegularFile(destination)) {
        val cachedDigest = Files.newInputStream(destination).use(::sha256)
        if (cachedDigest.contentEquals(sourceDigest)) return destination
    }

    Files.createDirectories(destination.parent)
    val temporary = destination.resolveSibling(".${destination.fileName}.tmp-${System.nanoTime()}")
    try {
        val copiedDigest = MessageDigest.getInstance("SHA-256")
        openSource().use { input ->
            FileOutputStream(temporary.toFile()).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copiedDigest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        check(copiedDigest.digest().contentEquals(sourceDigest)) {
            "copied model content did not match the packaged asset"
        }
        try {
            Files.move(
                temporary,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
        }
        return destination
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private fun sha256(input: InputStream): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return digest.digest()
        digest.update(buffer, 0, count)
    }
}
