/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchyolodemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.pytorch.executorch.EValue;
import org.pytorch.executorch.Module;
import org.pytorch.executorch.Tensor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BirdDetectionPipeline {
    private static final String TAG = "BirdDetectionPipeline";

    private static final float CONFIDENCE_THRESHOLD = 0.75f; // Increased from 0.6 to 0.75 (25% boost)
    private static final float NMS_THRESHOLD = 0.35f; // Reduced from 0.4 to 0.35 for better suppression
    private static final int MAX_DETECTIONS = 3; // Reduced from 5 to 3 for more selectivity
    private static final int MIN_BOX_SIZE = 40; // Increased from 30 to 40
    private static final float MIN_ASPECT_RATIO = 0.3f; // Tightened from 0.2
    private static final float MAX_ASPECT_RATIO = 3.5f; // Tightened from 5.0
    private static final boolean DEBUG_OUTPUT = true;

    private Map<String, DetectionHistory> detectionHistory = new HashMap<>();
    private int frameCounter = 0;
    private static final float STABILITY_THRESHOLD = 0.8f; // Require 80% confidence across frames
    private static final int HISTORY_WINDOW = 5; // Track last 5 frames
    private static final float TEMPORAL_BONUS = 0.15f; // Higher bonus for stable detections

    private static class DetectionHistory {
        List<Float> recentConfidences = new ArrayList<>();
        int consecutiveFrames = 0;
        long lastSeenTime = System.currentTimeMillis();

        void addConfidence(float confidence) {
            recentConfidences.add(confidence);
            if (recentConfidences.size() > HISTORY_WINDOW) {
                recentConfidences.remove(0);
            }
            consecutiveFrames++;
            lastSeenTime = System.currentTimeMillis();
        }

        float getAverageConfidence() {
            if (recentConfidences.isEmpty()) return 0f;
            float sum = 0f;
            for (float conf : recentConfidences) sum += conf;
            return sum / recentConfidences.size();
        }

        boolean isStable() {
            return recentConfidences.size() >= 3 &&
                    getAverageConfidence() >= STABILITY_THRESHOLD &&
                    consecutiveFrames >= 3;
        }
    }

    private Module yoloModule;
    private Module classifierModule;
    private String[] birdSpeciesNames;

    public static class BirdDetection {
        public RectF boundingBox;
        public String species;
        public float confidence;
        public boolean isStable; // New field for stability

        public BirdDetection(RectF boundingBox, String species, float confidence, boolean isStable) {
            this.boundingBox = boundingBox;
            this.species = species;
            this.confidence = confidence;
            this.isStable = isStable;
        }
    }

    private static class Detection {
        public RectF boundingBox;
        public float confidence;
        public int classIndex;
        public String locationKey;

        public Detection(RectF boundingBox, float confidence, int classIndex) {
            this.boundingBox = boundingBox;
            this.confidence = confidence;
            this.classIndex = classIndex;
            this.locationKey = generateLocationKey(boundingBox);
        }

        private String generateLocationKey(RectF box) {
            // More precise location key for better stability tracking
            int centerX = (int) ((box.left + box.right) / 2 / 50); // 50px grid (tighter)
            int centerY = (int) ((box.top + box.bottom) / 2 / 50);
            return centerX + "," + centerY;
        }
    }

    public BirdDetectionPipeline(Context context) throws IOException {
        this(context, null);
    }

    public BirdDetectionPipeline(Context context, String modelDir) throws IOException {
        try {
            String yoloPath;
            String classifierPath;

            if (modelDir != null) {
                yoloPath = modelDir + "/yolo_detector.pte";
                classifierPath = modelDir + "/bird_classifier.pte";
            } else {
                yoloPath = Utils.assetFilePath(context, "yolo_detector.pte");
                classifierPath = Utils.assetFilePath(context, "bird_classifier.pte");
            }

            yoloModule = Module.load(yoloPath);
            classifierModule = Module.load(classifierPath);

            loadBirdSpeciesNames(context);
            Log.d(TAG, "Models loaded successfully with OPTIMIZED settings for false positive reduction");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load models", e);
            throw new IOException("Model loading failed: " + e.getMessage(), e);
        }
    }

    private void loadBirdSpeciesNames(Context context) throws IOException {
        try {
            InputStream is = context.getAssets().open("bird_species.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);

            Gson gson = new Gson();
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> speciesList = gson.fromJson(json, listType);
            birdSpeciesNames = speciesList.toArray(new String[0]);

            Log.d(TAG, "Loaded " + birdSpeciesNames.length + " bird species names");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load bird species names", e);
            birdSpeciesNames = new String[]{"Bird"};
        }
    }

    public List<BirdDetection> detectBirds(Bitmap bitmap) {
        List<BirdDetection> results = new ArrayList<>();
        frameCounter++;

        try {
            // Cleanup old detection history every 30 frames
            if (frameCounter % 30 == 0) {
                cleanupOldDetections();
            }

            Tensor yoloInput = preprocessForYolo(bitmap);
            if (yoloInput == null) {
                return results;
            }

            EValue[] yoloOutputs = yoloModule.forward(EValue.from(yoloInput));
            if (yoloOutputs == null || yoloOutputs.length == 0) {
                return results;
            }

            List<Detection> detections = parseYoloV8OutputOptimized(yoloOutputs, bitmap.getWidth(), bitmap.getHeight());

            if (DEBUG_OUTPUT) {
                Log.d(TAG, "Raw detections before NMS: " + detections.size());
            }

            // Apply enhanced NMS
            List<Detection> filteredDetections = applyEnhancedNMS(detections);

            if (DEBUG_OUTPUT) {
                Log.d(TAG, "Detections after enhanced NMS: " + filteredDetections.size());
            }

            // Apply temporal stability tracking
            List<Detection> stableDetections = applyTemporalStabilityTracking(filteredDetections);

            // Limit to MAX_DETECTIONS with quality ranking
            if (stableDetections.size() > MAX_DETECTIONS) {
                // Sort by confidence and take top detections
                Collections.sort(stableDetections, (a, b) -> Float.compare(b.confidence, a.confidence));
                stableDetections = stableDetections.subList(0, MAX_DETECTIONS);
            }

            // Process stable detections only
            for (Detection detection : stableDetections) {
                try {
                    if (validateBirdDetectionEnhanced(bitmap, detection.boundingBox)) {
                        String[] speciesResult = classifyBird(bitmap, detection.boundingBox);
                        float classifierConfidence = Float.parseFloat(speciesResult[1]);

                        // Stricter classifier requirement for false positive reduction
                        if (classifierConfidence > 0.5f) {
                            // Weighted average favoring detection confidence
                            float finalConfidence = (detection.confidence * 0.8f + classifierConfidence * 0.2f);

                            // Check if this detection is stable over time
                            DetectionHistory history = detectionHistory.get(detection.locationKey);
                            boolean isStable = history != null && history.isStable();

                            results.add(new BirdDetection(
                                    detection.boundingBox,
                                    speciesResult[0],
                                    finalConfidence,
                                    isStable
                            ));

                            if (DEBUG_OUTPUT) {
                                Log.d(TAG, "FINAL BIRD: " + speciesResult[0] + " conf=" + finalConfidence + " stable=" + isStable);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to classify detection", e);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Bird detection failed", e);
        }

        if (DEBUG_OUTPUT) {
            Log.d(TAG, "TOTAL FINAL RESULTS: " + results.size());
        }

        return results;
    }

    private boolean validateBirdDetectionEnhanced(Bitmap bitmap, RectF boundingBox) {
        float width = boundingBox.width();
        float height = boundingBox.height();

        // Enhanced size validation
        if (width < MIN_BOX_SIZE || height < MIN_BOX_SIZE) {
            if (DEBUG_OUTPUT) {
                Log.d(TAG, "Rejected: too small " + width + "x" + height);
            }
            return false;
        }

        // Enhanced aspect ratio validation
        float aspectRatio = width / height;
        if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
            if (DEBUG_OUTPUT) {
                Log.d(TAG, "Rejected: bad aspect ratio " + aspectRatio);
            }
            return false;
        }

        // Bounds checking
        if (boundingBox.left < 0 || boundingBox.top < 0 ||
                boundingBox.right > bitmap.getWidth() || boundingBox.bottom > bitmap.getHeight()) {
            if (DEBUG_OUTPUT) {
                Log.d(TAG, "Rejected: outside bounds");
            }
            return false;
        }

        // Additional validation: check for minimum area relative to image size
        float area = width * height;
        float imageArea = bitmap.getWidth() * bitmap.getHeight();
        float relativeArea = area / imageArea;

        if (relativeArea < 0.01f) { // Must be at least 1% of image
            if (DEBUG_OUTPUT) {
                Log.d(TAG, "Rejected: area too small relative to image");
            }
            return false;
        }

        return true;
    }

    private List<Detection> applyTemporalStabilityTracking(List<Detection> detections) {
        // Update detection history and apply temporal bonus
        for (Detection detection : detections) {
            String locationKey = detection.locationKey;
            DetectionHistory history = detectionHistory.computeIfAbsent(locationKey, k -> new DetectionHistory());

            history.addConfidence(detection.confidence);

            // Apply temporal bonus for stable detections
            if (history.isStable()) {
                detection.confidence = Math.min(1.0f, detection.confidence + TEMPORAL_BONUS);
                if (DEBUG_OUTPUT) {
                    Log.d(TAG, "Temporal stability bonus applied at " + locationKey + " new conf: " + detection.confidence);
                }
            }
        }

        return detections;
    }

    private void cleanupOldDetections() {
        long currentTime = System.currentTimeMillis();
        long maxAge = 5000; // 5 seconds

        detectionHistory.entrySet().removeIf(entry ->
                currentTime - entry.getValue().lastSeenTime > maxAge);

        if (DEBUG_OUTPUT) {
            Log.d(TAG, "Cleaned up old detections, remaining: " + detectionHistory.size());
        }
    }

    private Tensor preprocessForYolo(Bitmap bitmap) {
        try {
            if (bitmap == null || bitmap.isRecycled()) {
                Log.w(TAG, "Invalid bitmap for preprocessing");
                return null;
            }

            Bitmap resized = Bitmap.createScaledBitmap(bitmap, 640, 640, false);
            int[] pixels = new int[640 * 640];
            resized.getPixels(pixels, 0, 640, 0, 0, 640, 640);

            float[] floatArray = new float[3 * 640 * 640];
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                floatArray[i] = ((pixel >> 16) & 0xff) / 255.0f; // R
                floatArray[640 * 640 + i] = ((pixel >> 8) & 0xff) / 255.0f; // G
                floatArray[2 * 640 * 640 + i] = (pixel & 0xff) / 255.0f; // B
            }

            return Tensor.fromBlob(floatArray, new long[]{1, 3, 640, 640});
        } catch (Exception e) {
            Log.e(TAG, "Failed to preprocess for YOLO", e);
            return null;
        }
    }

    private List<Detection> parseYoloV8OutputOptimized(EValue[] outputs, int imageWidth, int imageHeight) {
        List<Detection> detections = new ArrayList<>();

        try {
            float[] output = outputs[0].toTensor().getDataAsFloatArray();

            if (DEBUG_OUTPUT) {
                Log.d(TAG, "=== YOLOv8 OPTIMIZED PARSING ===");
                Log.d(TAG, "Output length: " + output.length);
            }

            int numDetections = 8400;
            int numFeatures = 84;

            if (output.length != numDetections * numFeatures) {
                Log.e(TAG, "Unexpected output size: " + output.length);
                return detections;
            }

            float scaleX = (float) imageWidth / 640.0f;
            float scaleY = (float) imageHeight / 640.0f;

            int validDetectionCount = 0;
            int totalChecked = 0;

            for (int i = 0; i < numDetections && validDetectionCount < 15; i++) { // Reduced max checks
                totalChecked++;

                float x = output[0 * numDetections + i];
                float y = output[1 * numDetections + i];
                float w = output[2 * numDetections + i];
                float h = output[3 * numDetections + i];

                // Enhanced coordinate validation
                if (x <= 0 || y <= 0 || w <= 0 || h <= 0 || w > 640 || h > 640) {
                    continue;
                }

                float maxClassScore = 0;
                int bestClass = -1;

                for (int classIdx = 0; classIdx < 80; classIdx++) {
                    int featureIdx = 4 + classIdx;
                    if (featureIdx < numFeatures) {
                        float classScore = output[featureIdx * numDetections + i];
                        if (classScore > maxClassScore) {
                            maxClassScore = classScore;
                            bestClass = classIdx;
                        }
                    }
                }

                float confidence = maxClassScore;

                if (DEBUG_OUTPUT && totalChecked <= 5) {
                    Log.d(TAG, "Detection " + i + ": conf=" + confidence + " class=" + bestClass);
                }

                // Stricter confidence and class checking
                if (confidence > CONFIDENCE_THRESHOLD && bestClass == 14) { // 14 = bird class in COCO
                    RectF boundingBox = convertYoloV8Coordinates(x, y, w, h, scaleX, scaleY, imageWidth, imageHeight);

                    if (boundingBox != null) {
                        detections.add(new Detection(boundingBox, confidence, bestClass));
                        validDetectionCount++;

                        if (DEBUG_OUTPUT) {
                            Log.d(TAG, "ACCEPTED detection " + validDetectionCount + ": " +
                                    boundingBox.toString() + " conf=" + confidence);
                        }
                    }
                }
            }

            if (DEBUG_OUTPUT) {
                Log.d(TAG, "Checked " + totalChecked + " detections, found " + validDetectionCount + " valid");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse YOLOv8 output", e);
        }

        return detections;
    }

    private RectF convertYoloV8Coordinates(float centerX, float centerY, float width, float height,
                                           float scaleX, float scaleY, int imageWidth, int imageHeight) {

        float scaledCenterX = centerX * scaleX;
        float scaledCenterY = centerY * scaleY;
        float scaledWidth = width * scaleX;
        float scaledHeight = height * scaleY;

        float left = scaledCenterX - scaledWidth / 2;
        float top = scaledCenterY - scaledHeight / 2;
        float right = scaledCenterX + scaledWidth / 2;
        float bottom = scaledCenterY + scaledHeight / 2;

        // Clamp to image bounds
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(imageWidth, right);
        bottom = Math.min(imageHeight, bottom);

        float finalWidth = right - left;
        float finalHeight = bottom - top;

        // Stricter size validation
        if (finalWidth >= MIN_BOX_SIZE && finalHeight >= MIN_BOX_SIZE) {
            return new RectF(left, top, right, bottom);
        }

        return null;
    }

    private List<Detection> applyEnhancedNMS(List<Detection> detections) {
        if (detections.size() <= 1) return detections;

        Collections.sort(detections, (a, b) -> Float.compare(b.confidence, a.confidence));

        List<Detection> result = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];

        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;

            result.add(detections.get(i));

            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;

                float iou = calculateIoU(detections.get(i).boundingBox, detections.get(j).boundingBox);
                if (iou > NMS_THRESHOLD) {
                    suppressed[j] = true;

                    if (DEBUG_OUTPUT) {
                        Log.d(TAG, "Enhanced NMS: Suppressed detection with IoU " + iou);
                    }
                }
            }

            if (result.size() >= MAX_DETECTIONS) {
                break;
            }
        }

        return result;
    }

    private float calculateIoU(RectF box1, RectF box2) {
        float area1 = (box1.right - box1.left) * (box1.bottom - box1.top);
        float area2 = (box2.right - box2.left) * (box2.bottom - box2.top);

        float intersectLeft = Math.max(box1.left, box2.left);
        float intersectTop = Math.max(box1.top, box2.top);
        float intersectRight = Math.min(box1.right, box2.right);
        float intersectBottom = Math.min(box1.bottom, box2.bottom);

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0;
        }

        float intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop);
        return intersectArea / (area1 + area2 - intersectArea);
    }

    private Tensor preprocessForClassifier(Bitmap bitmap) {
        try {
            if (bitmap == null || bitmap.isRecycled()) {
                return null;
            }

            Bitmap resized = Bitmap.createScaledBitmap(bitmap, 224, 224, false);
            int[] pixels = new int[224 * 224];
            resized.getPixels(pixels, 0, 224, 0, 0, 224, 224);

            float[] mean = {0.485f, 0.456f, 0.406f};
            float[] std = {0.229f, 0.224f, 0.225f};
            float[] floatArray = new float[3 * 224 * 224];

            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                floatArray[i] = (((pixel >> 16) & 0xff) / 255.0f - mean[0]) / std[0];
                floatArray[224 * 224 + i] = (((pixel >> 8) & 0xff) / 255.0f - mean[1]) / std[1];
                floatArray[2 * 224 * 224 + i] = ((pixel & 0xff) / 255.0f - mean[2]) / std[2];
            }

            return Tensor.fromBlob(floatArray, new long[]{1, 3, 224, 224});
        } catch (Exception e) {
            Log.e(TAG, "Failed to preprocess for classifier", e);
            return null;
        }
    }

    private String[] classifyBird(Bitmap bitmap, RectF boundingBox) throws Exception {
        Bitmap croppedBird = cropBitmap(bitmap, boundingBox);
        if (croppedBird == null) {
            return new String[]{"Bird", "0.5"};
        }

        Tensor classifierInput = preprocessForClassifier(croppedBird);
        if (classifierInput == null) {
            return new String[]{"Bird", "0.5"};
        }

        EValue[] classifierOutputs = classifierModule.forward(EValue.from(classifierInput));
        return parseClassifierOutput(classifierOutputs);
    }

    private String[] parseClassifierOutput(EValue[] outputs) {
        try {
            float[] probabilities = outputs[0].toTensor().getDataAsFloatArray();
            int maxIndex = 0;
            float maxProb = probabilities[0];

            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i];
                    maxIndex = i;
                }
            }

            String species = (maxIndex < birdSpeciesNames.length) ?
                    birdSpeciesNames[maxIndex] : "Bird";

            return new String[]{species, String.valueOf(maxProb)};
        } catch (Exception e) {
            return new String[]{"Bird", "0.5"};
        }
    }

    private Bitmap cropBitmap(Bitmap bitmap, RectF box) {
        try {
            if (bitmap == null || bitmap.isRecycled()) {
                return null;
            }

            int left = (int) Math.max(0, box.left);
            int top = (int) Math.max(0, box.top);
            int width = (int) Math.min(bitmap.getWidth() - left, box.width());
            int height = (int) Math.min(bitmap.getHeight() - top, box.height());

            if (width > 0 && height > 0 && left + width <= bitmap.getWidth() && top + height <= bitmap.getHeight()) {
                return Bitmap.createBitmap(bitmap, left, top, width, height);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to crop bitmap", e);
        }
        return null;
    }

    public void cleanup() {
        detectionHistory.clear();
    }
}