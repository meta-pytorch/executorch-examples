/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.video

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotatedVideoRecorderTest {
    @Test
    fun `presentation timeline preserves elapsed gaps and remains monotonic`() {
        val timeline = VideoPresentationTimeline()

        assertEquals(0L, timeline.normalize(1_000_000L))
        assertEquals(500_000L, timeline.normalize(1_500_000L))
        assertEquals(500_001L, timeline.normalize(1_400_000L))
    }
}
