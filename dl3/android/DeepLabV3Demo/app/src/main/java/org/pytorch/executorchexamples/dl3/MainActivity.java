/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.pytorch.executorchexamples.dl3;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.pytorch.executorch.EValue;
import org.pytorch.executorch.Module;
import org.pytorch.executorch.Tensor;

public class MainActivity extends Activity implements Runnable {
  private ImageView mImageView;
  private Button mButtonXnnpack;
  private Button mDownloadModelButton;
  private ProgressBar mProgressBar;
  private android.widget.TextView mInferenceTimeText;
  private android.widget.TextView mModelStatusText;
  private Bitmap mBitmap = null;
  private Module mModule = null;
  private long mInferenceTime = 0;

  // Model download configuration
  private static final String MODEL_URL = "TODO";
  private static final String MODEL_FILENAME = "dl3_xnnpack_fp32.pte";
  private String mModelPath; // Will be set in onCreate using getFilesDir()

  // Sample images from assets
  private static final String[] SAMPLE_IMAGES = {"corgi.jpeg", "deeplab.jpg", "dog.jpg"};
  private int mCurrentSampleIndex = 0;

  private static final int REQUEST_PICK_IMAGE = 1002;

  // see http://host.robots.ox.ac.uk:8080/pascal/VOC/voc2007/segexamples/index.html for the list of
  // classes with indexes
  private static final int CLASSNUM = 21;

  // Colors for all 21 PASCAL VOC classes
  private static final int[] CLASS_COLORS = {
      0x00000000, // 0: Background (transparent)
      0xFFE6194B, // 1: Aeroplane (red)
      0xFF3CB44B, // 2: Bicycle (green)
      0xFFFFE119, // 3: Bird (yellow)
      0xFF4363D8, // 4: Boat (blue)
      0xFFF58231, // 5: Bottle (orange)
      0xFF911EB4, // 6: Bus (purple)
      0xFF46F0F0, // 7: Car (cyan)
      0xFFF032E6, // 8: Cat (magenta)
      0xFFBCF60C, // 9: Chair (lime)
      0xFFFABEBE, // 10: Cow (pink)
      0xFF008080, // 11: Dining Table (teal)
      0xFF00FF00, // 12: Dog (bright green)
      0xFF9A6324, // 13: Horse (brown)
      0xFFFFD8B1, // 14: Motorbike (peach)
      0xFFFF0000, // 15: Person (red)
      0xFF800000, // 16: Potted Plant (maroon)
      0xFF0000FF, // 17: Sheep (blue)
      0xFF808000, // 18: Sofa (olive)
      0xFFE6BEFF, // 19: Train (lavender)
      0xFFAA6E28, // 20: TV/Monitor (tan)
  };

  // Handle image picker result
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
      try {
        android.net.Uri imageUri = data.getData();
        if (imageUri != null) {
          InputStream inputStream = getContentResolver().openInputStream(imageUri);
          Bitmap selectedBitmap = BitmapFactory.decodeStream(inputStream);
          if (selectedBitmap != null) {
            // Resize to 224x224 for the model
            mBitmap = Bitmap.createScaledBitmap(selectedBitmap, 224, 224, true);
            mImageView.setImageBitmap(mBitmap);
            mInferenceTimeText.setVisibility(View.INVISIBLE);
            showUIMessage(this, "Image loaded - tap Run to segment");
          }
          if (inputStream != null) {
            inputStream.close();
          }
        }
      } catch (Exception e) {
        Log.e("MainActivity", "Error loading picked image", e);
        showUIMessage(this, "Failed to load image");
      }
    }
  }

  private void loadSampleImage() {
    try {
      String imageName = SAMPLE_IMAGES[mCurrentSampleIndex];
      mBitmap = BitmapFactory.decodeStream(getAssets().open(imageName));
      if (mBitmap != null) {
        mBitmap = Bitmap.createScaledBitmap(mBitmap, 224, 224, true);
        mImageView.setImageBitmap(mBitmap);
        mInferenceTimeText.setVisibility(View.INVISIBLE);
      }
    } catch (IOException e) {
      Log.e("MainActivity", "Error loading sample image", e);
    }
  }

  private void nextSampleImage() {
    mCurrentSampleIndex = (mCurrentSampleIndex + 1) % SAMPLE_IMAGES.length;
    loadSampleImage();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    try {
      Os.setenv("ADSP_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir, true);
      Os.setenv("LD_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir, true);
    } catch (ErrnoException e) {
      finish();
    }

    // Initialize all views
    mImageView = findViewById(R.id.imageView);
    mButtonXnnpack = findViewById(R.id.xnnpackButton);
    mDownloadModelButton = findViewById(R.id.downloadModelButton);
    mProgressBar = findViewById(R.id.progressBar);
    mInferenceTimeText = findViewById(R.id.inferenceTimeText);
    mModelStatusText = findViewById(R.id.modelStatusText);

    // Set model path to app's private storage
    mModelPath = getFilesDir().getAbsolutePath() + "/" + MODEL_FILENAME;

    // Load first sample image
    loadSampleImage();

    // Check if model exists and load it, otherwise show download button
    loadModelOrShowDownloadButton();

    // Download button click handler
    mDownloadModelButton.setOnClickListener(v -> downloadModel());

    // Next sample image button
    final Button buttonNext = findViewById(R.id.nextButton);
    buttonNext.setOnClickListener(v -> nextSampleImage());

    // Run segmentation button
    mButtonXnnpack.setOnClickListener(v -> {
      mButtonXnnpack.setEnabled(false);
      mProgressBar.setVisibility(ProgressBar.VISIBLE);
      mInferenceTimeText.setVisibility(View.INVISIBLE);
      mButtonXnnpack.setText(getString(R.string.run_model));
      new Thread(MainActivity.this).start();
    });

    // Reset to current sample image
    final Button resetImage = findViewById(R.id.resetImage);
    resetImage.setOnClickListener(v -> loadSampleImage());

    // Pick Image from gallery
    final Button pickImageButton = findViewById(R.id.loadAndRefreshButton);
    pickImageButton.setOnClickListener(v -> {
      Intent intent = new Intent(Intent.ACTION_PICK);
      intent.setType("image/*");
      startActivityForResult(intent, REQUEST_PICK_IMAGE);
    });
  }

  @Override
  public void run() {
    final Tensor inputTensor =
        TensorImageUtils.bitmapToFloat32Tensor(
            mBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB);

    boolean imageSegementationSuccess = false;
    final long startTime = SystemClock.elapsedRealtime();
    Tensor outputTensor = mModule.forward(EValue.from(inputTensor))[0].toTensor();
    mInferenceTime = SystemClock.elapsedRealtime() - startTime;
    Log.d("ImageSegmentation", "inference time (ms): " + mInferenceTime);

    final float[] scores = outputTensor.getDataAsFloatArray();
    int width = mBitmap.getWidth();
    int height = mBitmap.getHeight();

    // Get original pixels for blending
    int[] originalPixels = new int[width * height];
    mBitmap.getPixels(originalPixels, 0, width, 0, 0, width, height);

    int[] intValues = new int[width * height];
    for (int j = 0; j < height; j++) {
      for (int k = 0; k < width; k++) {
        int maxi = 0;
        double maxnum = -Double.MAX_VALUE;
        for (int i = 0; i < CLASSNUM; i++) {
          float score = scores[i * (width * height) + j * width + k];
          if (score > maxnum) {
            maxnum = score;
            maxi = i;
          }
        }
        int pixelIndex = j * width + k;
        int classColor = CLASS_COLORS[maxi];
        
        if (maxi == 0) {
          // Background: show original image
          intValues[pixelIndex] = originalPixels[pixelIndex];
        } else {
          // Blend segmentation color with original at 50% opacity
          intValues[pixelIndex] = blendColors(originalPixels[pixelIndex], classColor, 0.5f);
          imageSegementationSuccess = true;
        }
      }
    }

    Bitmap bmpSegmentation = Bitmap.createScaledBitmap(mBitmap, width, height, true);
    Bitmap outputBitmap = bmpSegmentation.copy(bmpSegmentation.getConfig(), true);
    outputBitmap.setPixels(
        intValues,
        0,
        outputBitmap.getWidth(),
        0,
        0,
        outputBitmap.getWidth(),
        outputBitmap.getHeight());
    final Bitmap transferredBitmap =
        Bitmap.createScaledBitmap(outputBitmap, mBitmap.getWidth(), mBitmap.getHeight(), true);

    final boolean showUserIndicationOnImgSegFail = !imageSegementationSuccess;
    runOnUiThread(
            () -> {
              if (showUserIndicationOnImgSegFail) {
                Toast.makeText(this, "No objects detected", Toast.LENGTH_SHORT).show();
              }
              mImageView.setImageBitmap(transferredBitmap);
              mButtonXnnpack.setEnabled(true);
              mButtonXnnpack.setText(R.string.run_xnnpack);
              mProgressBar.setVisibility(ProgressBar.INVISIBLE);
              mInferenceTimeText.setText("Inference: " + mInferenceTime + " ms");
              mInferenceTimeText.setVisibility(View.VISIBLE);
            });
  }

  void showUIMessage(final Context context, final String msg) {
    runOnUiThread(new Runnable() {
      public void run() {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
      }
    });
  }

  // Blend two colors with given alpha for the overlay
  private int blendColors(int background, int foreground, float alpha) {
    int bgR = (background >> 16) & 0xFF;
    int bgG = (background >> 8) & 0xFF;
    int bgB = background & 0xFF;
    int fgR = (foreground >> 16) & 0xFF;
    int fgG = (foreground >> 8) & 0xFF;
    int fgB = foreground & 0xFF;
    int r = (int) (bgR * (1 - alpha) + fgR * alpha);
    int g = (int) (bgG * (1 - alpha) + fgG * alpha);
    int b = (int) (bgB * (1 - alpha) + fgB * alpha);
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  private void loadModelOrShowDownloadButton() {
    File modelFile = new File(mModelPath);
    if (modelFile.exists()) {
      try {
        mModule = Module.load(mModelPath);
        mButtonXnnpack.setEnabled(true);
        mDownloadModelButton.setEnabled(false);
        mDownloadModelButton.setText("Model Ready");
        mModelStatusText.setText("Model loaded");
        mModelStatusText.setVisibility(View.VISIBLE);
      } catch (Exception e) {
        Log.e("MainActivity", "Failed to load model", e);
        showUIMessage(this, "Failed to load model: " + e.getMessage());
        mButtonXnnpack.setEnabled(false);
        mDownloadModelButton.setVisibility(View.VISIBLE);
        mModelStatusText.setText("Model load failed");
        mModelStatusText.setVisibility(View.VISIBLE);
      }
    } else {
      mButtonXnnpack.setEnabled(false);
      mDownloadModelButton.setVisibility(View.VISIBLE);
      mModelStatusText.setText("Model not found");
      mModelStatusText.setVisibility(View.VISIBLE);
    }
  }

  private void downloadModel() {
    mDownloadModelButton.setEnabled(false);
    mDownloadModelButton.setText(R.string.downloading);
    mProgressBar.setVisibility(View.VISIBLE);
    mModelStatusText.setText("Downloading...");

    new Thread(() -> {
      try {
        URL url = new URL(MODEL_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(true);
        connection.connect();

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
          throw new IOException("Server returned HTTP " + connection.getResponseCode());
        }

        // Download is a zip file, extract the .pte file
        try (InputStream input = connection.getInputStream();
             ZipInputStream zipIn = new ZipInputStream(input)) {
          ZipEntry entry;
          boolean found = false;
          while ((entry = zipIn.getNextEntry()) != null) {
            String fileName = entry.getName();
            // Look for the .pte file
            if (fileName.endsWith(".pte")) {
              File outputFile = new File(mModelPath);
              try (FileOutputStream output = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = zipIn.read(buffer)) != -1) {
                  output.write(buffer, 0, bytesRead);
                }
              }
              found = true;
              break;
            }
            zipIn.closeEntry();
          }
          if (!found) {
            throw new IOException("No .pte file found in zip");
          }
        }

        runOnUiThread(() -> {
          mDownloadModelButton.setText(R.string.download_model);
          mProgressBar.setVisibility(View.INVISIBLE);
          loadModelOrShowDownloadButton();
          showUIMessage(this, "Model downloaded successfully!");
        });
      } catch (Exception e) {
        Log.e("MainActivity", "Failed to download model", e);
        runOnUiThread(() -> {
          mDownloadModelButton.setEnabled(true);
          mDownloadModelButton.setText(R.string.download_model);
          mProgressBar.setVisibility(View.INVISIBLE);
          mModelStatusText.setText("Download failed");
          showUIMessage(this, "Download failed: " + e.getMessage());
        });
      }
    }).start();
  }
}
