/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.pytorch.executorchexamples.dl3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.pytorch.executorch.Module;
import org.pytorch.executorch.Tensor;

/**
 * Sanity check test for model loading.
 * 
 * This test will skip if the model file is not available.
 * The model should be in the app's private storage (same location as MainActivity uses).
 */
@SmallTest
public class SanityCheck {

    private static final String MODEL_FILENAME = "dl3_xnnpack_fp32.pte";
    private String modelPath;

    @Before
    public void setUp() {
        // Use the app's private files directory (same as MainActivity)
        Context context = ApplicationProvider.getApplicationContext();
        modelPath = context.getFilesDir().getAbsolutePath() + "/" + MODEL_FILENAME;
        
        // Check if model file exists, skip test if not
        File modelFile = new File(modelPath);
        assumeTrue("Model file not found at " + modelPath + ". Run UIWorkflowTest first to download the model.", 
                   modelFile.exists());
    }

    @Test
    public void testModuleForward() {
        Module module = Module.load(modelPath);
        // Test with sample inputs (ones) and make sure there is no crash.
        Tensor outputTensor = module.forward()[0].toTensor();
        long[] shape = outputTensor.shape();
        // batch_size * classes * width * height
        assertEquals(4, shape.length);
        assertEquals(1, shape[0]);
        assertEquals(21, shape[1]);
        assertEquals(224, shape[2]);
        assertEquals(224, shape[3]);
    }
}
