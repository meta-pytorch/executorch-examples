/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.pytorch.executorchexamples.dl3;

import android.graphics.Bitmap;
import java.nio.FloatBuffer;
import org.pytorch.executorch.Tensor;

/**
 * Contains utility functions for {@link Tensor} creation from {@link android.graphics.Bitmap}.
 */
public final class TensorImageUtils {

  public static float[] TORCHVISION_NORM_MEAN_RGB = new float[] {0.485f, 0.456f, 0.406f};
  public static float[] TORCHVISION_NORM_STD_RGB = new float[] {0.229f, 0.224f, 0.225f};

  /**
   * Creates new {@link Tensor} from full {@link android.graphics.Bitmap}, normalized with specified
   * in parameters mean and std.
   *
   * @param normMeanRGB means for RGB channels normalization, length must equal 3, RGB order
   * @param normStdRGB standard deviation for RGB channels normalization, length must equal 3, RGB
   *     order
   */
  public static Tensor bitmapToFloat32Tensor(
      final Bitmap bitmap, final float[] normMeanRGB, final float normStdRGB[]) {
    checkNormMeanArg(normMeanRGB);
    checkNormStdArg(normStdRGB);

    return bitmapToFloat32Tensor(
        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), normMeanRGB, normStdRGB);
  }

  /**
   * Creates new {@link Tensor} from specified area of {@link android.graphics.Bitmap}, normalized
   * with specified in parameters mean and std.
   */
  private static Tensor bitmapToFloat32Tensor(
      final Bitmap bitmap,
      int x,
      int y,
      int width,
      int height,
      float[] normMeanRGB,
      float[] normStdRGB) {
    final FloatBuffer floatBuffer = Tensor.allocateFloatBuffer(3 * width * height);
    bitmapToFloatBuffer(bitmap, x, y, width, height, normMeanRGB, normStdRGB, floatBuffer, 0);
    return Tensor.fromBlob(floatBuffer, new long[] {1, 3, height, width});
  }

  private static void bitmapToFloatBuffer(
      final Bitmap bitmap,
      final int x,
      final int y,
      final int width,
      final int height,
      final float[] normMeanRGB,
      final float[] normStdRGB,
      final FloatBuffer outBuffer,
      final int outBufferOffset) {
    final int pixelsCount = height * width;
    final int[] pixels = new int[pixelsCount];
    bitmap.getPixels(pixels, 0, width, x, y, width, height);
    final int offset_g = pixelsCount;
    final int offset_b = 2 * pixelsCount;
    for (int i = 0; i < pixelsCount; i++) {
      final int c = pixels[i];
      float r = ((c >> 16) & 0xff) / 255.0f;
      float g = ((c >> 8) & 0xff) / 255.0f;
      float b = ((c) & 0xff) / 255.0f;
      outBuffer.put(outBufferOffset + i, (r - normMeanRGB[0]) / normStdRGB[0]);
      outBuffer.put(outBufferOffset + offset_g + i, (g - normMeanRGB[1]) / normStdRGB[1]);
      outBuffer.put(outBufferOffset + offset_b + i, (b - normMeanRGB[2]) / normStdRGB[2]);
    }
  }

  private static void checkNormStdArg(float[] normStdRGB) {
    if (normStdRGB.length != 3) {
      throw new IllegalArgumentException("normStdRGB length must be 3");
    }
  }

  private static void checkNormMeanArg(float[] normMeanRGB) {
    if (normMeanRGB.length != 3) {
      throw new IllegalArgumentException("normMeanRGB length must be 3");
    }
  }
}
