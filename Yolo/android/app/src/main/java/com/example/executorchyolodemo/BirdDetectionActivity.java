/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchyolodemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BirdDetectionActivity extends AppCompatActivity {
    private static final String TAG = "BirdDetectionActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private static final int FRAME_SKIP_COUNT = 5; // Process every 5th frame

    private PreviewView previewView;
    private ImageView overlayImageView;
    private TextView resultTextView;
    private Button sessionButton;
    private Button viewLogsButton;

    private BirdDetectionPipeline birdPipeline;
    private BirdSessionManager sessionManager;
    private ExecutorService cameraExecutor;
    private AtomicBoolean isProcessing = new AtomicBoolean(false);
    private int frameCounter = 0;
    private boolean isSessionActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bird_detection);

        initializeViews();
        initializeManagers();
        setupClickListeners();

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Initialize bird detection pipeline
        try {
            birdPipeline = new BirdDetectionPipeline(this);
            Log.d(TAG, "Bird detection pipeline initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize bird pipeline", e);
            Toast.makeText(this, "Failed to load models: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up the detection pipeline
        if (detectionPipeline != null) {
            detectionPipeline.close();
            detectionPipeline = null;
        }
        
        Log.d(TAG, "BirdDetectionActivity destroyed");
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Optional: Release resources when app goes to background
        if (detectionPipeline != null) {
            detectionPipeline.close();
            detectionPipeline = null;
        }
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        overlayImageView = findViewById(R.id.overlayImageView);
        resultTextView = findViewById(R.id.resultTextView);
        sessionButton = findViewById(R.id.sessionButton);
        viewLogsButton = findViewById(R.id.viewLogsButton);
    }

    private void initializeManagers() {
        sessionManager = new BirdSessionManager(this); // Pass context for file operations
        updateSessionUI();
    }

    private void setupClickListeners() {
        sessionButton.setOnClickListener(v -> {
            if (isSessionActive) {
                endSession();
            } else {
                startSession();
            }
        });

        viewLogsButton.setOnClickListener(v -> viewBirdLogs());
    }

    private void startSession() {
        isSessionActive = true;
        sessionManager.startNewSession();
        sessionButton.setText("End Session");
        sessionButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
        resultTextView.setText("Session started! Point camera at birds to detect them.");

        Toast.makeText(this, "Bird watching session started!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Bird watching session started");
    }

    private void endSession() {
        isSessionActive = false;
        sessionButton.setText("Start Session");
        sessionButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));

        String sessionSummary = sessionManager.getSessionSummary();
        resultTextView.setText("Session ended. " + sessionSummary);

        Toast.makeText(this, "Session ended: " + sessionSummary, Toast.LENGTH_LONG).show();
        Log.d(TAG, "Bird watching session ended: " + sessionSummary);
    }

    private void viewBirdLogs() {
        Intent intent = new Intent(this, BirdLogActivity.class);

        // FIXED: Pass the sighting data (without bitmaps) through Intent
        ArrayList<BirdSessionManager.BirdSighting> currentLogs = sessionManager.getCurrentSessionLogs();
        intent.putExtra("bird_logs", currentLogs);

        startActivity(intent);
    }

    private void updateSessionUI() {
        if (isSessionActive) {
            sessionButton.setText("End Session");
            sessionButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
        } else {
            sessionButton.setText("Start Session");
            sessionButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            Camera camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis);
            Log.d(TAG, "Camera bound successfully");
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        try {
            // Skip frames to reduce processing load
            frameCounter++;
            if (frameCounter % FRAME_SKIP_COUNT != 0) {
                return;
            }

            // Skip if already processing or session not active
            if (isProcessing.get() || !isSessionActive) {
                return;
            }

            isProcessing.set(true);

            Bitmap bitmap = imageProxyToBitmapSimple(imageProxy);
            if (bitmap != null) {
                List<BirdDetectionPipeline.BirdDetection> detections =
                        birdPipeline.detectBirds(bitmap);

                runOnUiThread(() -> updateUI(bitmap, detections));
            }
        } catch (Exception e) {
            Log.e(TAG, "Analysis failed", e);
        } finally {
            isProcessing.set(false);
            imageProxy.close();
        }
    }

    private Bitmap imageProxyToBitmapSimple(ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            ImageProxy.PlaneProxy y = planes[0];
            ImageProxy.PlaneProxy u = planes[1];
            ImageProxy.PlaneProxy v = planes[2];

            int ySize = y.getBuffer().remaining();
            int uSize = u.getBuffer().remaining();
            int vSize = v.getBuffer().remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            y.getBuffer().get(nv21, 0, ySize);
            v.getBuffer().get(nv21, ySize, vSize);
            u.getBuffer().get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    imageProxy.getWidth(), imageProxy.getHeight(), null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0,
                    imageProxy.getWidth(), imageProxy.getHeight()), 80, out);

            byte[] imageBytes = out.toByteArray();
            return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert ImageProxy to bitmap", e);
            return null;
        }
    }

    private void updateUI(Bitmap originalBitmap, List<BirdDetectionPipeline.BirdDetection> detections) {
        if (!isSessionActive) {
            resultTextView.setText("Session not active. Press 'Start Session' to begin bird watching.");
            overlayImageView.setImageBitmap(null);
            return;
        }

        if (detections.isEmpty()) {
            overlayImageView.setImageBitmap(null);
            String sessionInfo = sessionManager.getSessionSummary();
            resultTextView.setText("No birds detected. " + sessionInfo);
            return;
        }

        // Create overlay with bounding boxes
        Bitmap workingBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(workingBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.GREEN);
        textPaint.setTextSize(30);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(4, 2, 2, Color.BLACK);

        Paint stablePaint = new Paint();
        stablePaint.setColor(Color.BLUE);
        stablePaint.setStrokeWidth(7);
        stablePaint.setStyle(Paint.Style.STROKE);
        stablePaint.setAntiAlias(true);

        StringBuilder resultText = new StringBuilder("🐦 Birds Detected:\n");

        for (BirdDetectionPipeline.BirdDetection detection : detections) {
            // Use different colors for stable vs unstable detections
            Paint currentPaint = detection.isStable ? stablePaint : paint;
            canvas.drawRect(detection.boundingBox, currentPaint);

            String label = detection.species + " (" + String.format("%.1f%%", detection.confidence * 100) + ")";
            if (detection.isStable) {
                label += " ✓"; // Add checkmark for stable detections
            }

            // Background for text
            Paint backgroundPaint = new Paint();
            backgroundPaint.setColor(detection.isStable ?
                    Color.argb(180, 0, 0, 150) : Color.argb(180, 0, 150, 0));
            float textWidth = textPaint.measureText(label);
            canvas.drawRect(detection.boundingBox.left,
                    detection.boundingBox.top - 40,
                    detection.boundingBox.left + textWidth + 20,
                    detection.boundingBox.top, backgroundPaint);

            canvas.drawText(label,
                    detection.boundingBox.left + 10,
                    detection.boundingBox.top - 15,
                    textPaint);

            resultText.append("• ").append(detection.species)
                    .append(" (").append(String.format("%.1f%%", detection.confidence * 100)).append(")")
                    .append(detection.isStable ? " ✓" : "").append("\n");

            // Log bird sighting with ORIGINAL bitmap (not the overlay)
            if (isSessionActive) {
                Bitmap thumbnail = createThumbnailFixed(originalBitmap, detection.boundingBox);
                sessionManager.logBirdSighting(detection.species, thumbnail, detection.confidence);
                Log.d(TAG, "Created thumbnail for " + detection.species + ": " + (thumbnail != null ? "Success" : "Failed"));
            }
        }

        // Add session summary to result text
        String sessionInfo = sessionManager.getSessionSummary();
        resultText.append("\n📊 ").append(sessionInfo);

        overlayImageView.setImageBitmap(workingBitmap);
        resultTextView.setText(resultText.toString());
    }

    private Bitmap createThumbnailFixed(Bitmap originalBitmap, RectF boundingBox) {
        try {
            if (originalBitmap == null || originalBitmap.isRecycled()) {
                Log.e(TAG, "Original bitmap is null or recycled");
                return null;
            }

            float padding = 30;
            int left = (int) Math.max(0, boundingBox.left - padding);
            int top = (int) Math.max(0, boundingBox.top - padding);
            int right = (int) Math.min(originalBitmap.getWidth(), boundingBox.right + padding);
            int bottom = (int) Math.min(originalBitmap.getHeight(), boundingBox.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            Log.d(TAG, "Creating thumbnail: left=" + left + " top=" + top + " width=" + width + " height=" + height);

            if (width > 0 && height > 0 && width <= originalBitmap.getWidth() && height <= originalBitmap.getHeight()) {
                Bitmap croppedBitmap = Bitmap.createBitmap(originalBitmap, left, top, width, height);

                int maxThumbnailSize = 150;
                float scale = Math.min((float) maxThumbnailSize / width, (float) maxThumbnailSize / height);
                int scaledWidth = Math.max(1, (int) (width * scale));
                int scaledHeight = Math.max(1, (int) (height * scale));

                Bitmap thumbnail = Bitmap.createScaledBitmap(croppedBitmap, scaledWidth, scaledHeight, true);

                Log.d(TAG, "Thumbnail created successfully: " + scaledWidth + "x" + scaledHeight);

                if (croppedBitmap != thumbnail) {
                    croppedBitmap.recycle();
                }

                return thumbnail;
            } else {
                Log.w(TAG, "Invalid thumbnail dimensions: " + width + "x" + height);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create thumbnail", e);
        }
        return null;
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (birdPipeline != null) {
            birdPipeline.cleanup();
        }
        if (sessionManager != null) {
            sessionManager.cleanup();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSessionUI();
    }
}
