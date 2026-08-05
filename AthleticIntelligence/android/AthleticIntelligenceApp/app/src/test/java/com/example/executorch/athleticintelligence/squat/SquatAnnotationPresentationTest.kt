/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import org.junit.Assert.assertEquals
import org.junit.Test

class SquatAnnotationPresentationTest {
    @Test
    fun `danger levels map to stable overlay tones`() {
        assertEquals(SquatOverlayTone.SUCCESS, squatAlertTone(DangerLevel.SAFE))
        assertEquals(SquatOverlayTone.WARNING, squatAlertTone(DangerLevel.WARNING))
        assertEquals(SquatOverlayTone.ERROR, squatAlertTone(DangerLevel.DANGER))
    }

    @Test
    fun `default projection center-crops to match a FILL_CENTER preview`() {
        // 400x200 source into a 300x300 target: scale = max(0.75, 1.5) = 1.5, width cropped.
        val projection = squatCenterCropProjection(
            sourceWidthPx = 400,
            sourceHeightPx = 200,
            targetWidthPx = 300f,
            targetHeightPx = 300f,
        )
        assertEquals(1.5f, projection.scale, 0f)
        assertEquals(-150f, projection.offsetX, 0f)
        assertEquals(0f, projection.offsetY, 0f)
    }

    @Test
    fun `fit-center projection letterboxes a wide source without cropping`() {
        // Same 400x200 source into 300x300 with fit-center scaling: min(0.75, 1.5)
        // is 0.75, so the whole source fits with vertical letterboxing.
        val projection = squatCenterCropProjection(
            sourceWidthPx = 400,
            sourceHeightPx = 200,
            targetWidthPx = 300f,
            targetHeightPx = 300f,
            fill = false,
        )
        assertEquals(0.75f, projection.scale, 0f)
        assertEquals(0f, projection.offsetX, 0f)
        assertEquals(75f, projection.offsetY, 0f)
        // The full source maps inside [0,300] x [75,225] — no negative/overflow = no crop.
        val topLeft = projection.project(0.0, 0.0)
        val bottomRight = projection.project(400.0, 200.0)
        assertEquals(0f, topLeft.x, 0f)
        assertEquals(75f, topLeft.y, 0f)
        assertEquals(300f, bottomRight.x, 0f)
        assertEquals(225f, bottomRight.y, 0f)
    }
}
