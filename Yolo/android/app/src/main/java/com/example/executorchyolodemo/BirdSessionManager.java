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
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class BirdSessionManager {
    private static final String TAG = "BirdSessionManager";
    private static final String THUMBNAILS_DIR = "bird_thumbnails";

    public static class BirdSighting implements Serializable {
        public String species;
        public String timestamp;
        public float confidence;
        public String thumbnailPath; // Changed from transient Bitmap to file path
        public String sightingId; // Unique ID for each sighting

        public BirdSighting(String species, String timestamp, float confidence, String thumbnailPath) {
            this.species = species;
            this.timestamp = timestamp;
            this.confidence = confidence;
            this.thumbnailPath = thumbnailPath;
            this.sightingId = UUID.randomUUID().toString();
        }

        // Helper method to load bitmap from file
        public Bitmap loadThumbnail() {
            if (thumbnailPath == null || thumbnailPath.isEmpty()) {
                return null;
            }
            try {
                File file = new File(thumbnailPath);
                if (file.exists()) {
                    return BitmapFactory.decodeFile(thumbnailPath);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load thumbnail from " + thumbnailPath, e);
            }
            return null;
        }
    }

    private Context context;
    private List<BirdSighting> currentSessionSightings;
    private Set<String> uniqueBirdsThisSession;
    private SimpleDateFormat timeFormat;
    private long lastSightingTime;
    private File thumbnailsDir;

    public BirdSessionManager(Context context) {
        this.context = context;
        timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        currentSessionSightings = new ArrayList<>();
        uniqueBirdsThisSession = new HashSet<>();
        lastSightingTime = 0;

        // Create thumbnails directory
        setupThumbnailsDirectory();
    }

    private void setupThumbnailsDirectory() {
        try {
            thumbnailsDir = new File(context.getFilesDir(), THUMBNAILS_DIR);
            if (!thumbnailsDir.exists()) {
                boolean created = thumbnailsDir.mkdirs();
                Log.d(TAG, "Thumbnails directory created: " + created);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create thumbnails directory", e);
        }
    }

    public void startNewSession() {
        // Clean up previous session thumbnails
        cleanupOldThumbnails();

        currentSessionSightings.clear();
        uniqueBirdsThisSession.clear();
        lastSightingTime = 0;

        Log.d(TAG, "New bird watching session started");
    }

    public void logBirdSighting(String species, Bitmap thumbnail, float confidence) {
        long currentTime = System.currentTimeMillis();

        // Avoid duplicate detections within 3 seconds of same species
        if (currentTime - lastSightingTime > 3000 || !uniqueBirdsThisSession.contains(species)) {
            String timestamp = timeFormat.format(new Date());

            // Save thumbnail to file instead of keeping in memory
            String thumbnailPath = saveThumbnailToFile(thumbnail, species, timestamp);

            BirdSighting sighting = new BirdSighting(species, timestamp, confidence, thumbnailPath);

            currentSessionSightings.add(sighting);
            uniqueBirdsThisSession.add(species);
            lastSightingTime = currentTime;

            Log.d(TAG, "Logged bird sighting: " + species + " with thumbnail: " + thumbnailPath);
        }
    }

    private String saveThumbnailToFile(Bitmap bitmap, String species, String timestamp) {
        if (bitmap == null || thumbnailsDir == null) {
            Log.w(TAG, "Cannot save thumbnail: bitmap or directory is null");
            return null;
        }

        try {
            // Create unique filename
            String filename = "bird_" + species.replaceAll("[^a-zA-Z0-9]", "_") +
                    "_" + timestamp.replaceAll(":", "") +
                    "_" + System.currentTimeMillis() + ".jpg";

            File thumbnailFile = new File(thumbnailsDir, filename);

            // Save bitmap to file
            FileOutputStream fos = new FileOutputStream(thumbnailFile);
            boolean saved = bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.close();

            if (saved) {
                Log.d(TAG, "Thumbnail saved successfully: " + thumbnailFile.getAbsolutePath());
                return thumbnailFile.getAbsolutePath();
            } else {
                Log.e(TAG, "Failed to save thumbnail");
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving thumbnail to file", e);
            return null;
        }
    }

    public ArrayList<BirdSighting> getCurrentSessionLogs() {
        return new ArrayList<>(currentSessionSightings);
    }

    public int getTotalBirdsInSession() {
        return currentSessionSightings.size();
    }

    public int getUniqueSpeciesCount() {
        return uniqueBirdsThisSession.size();
    }

    public String getSessionSummary() {
        return String.format("Found %d birds (%d unique species)",
                getTotalBirdsInSession(), getUniqueSpeciesCount());
    }

    private void cleanupOldThumbnails() {
        if (thumbnailsDir == null || !thumbnailsDir.exists()) {
            return;
        }

        try {
            File[] files = thumbnailsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        boolean deleted = file.delete();
                        Log.d(TAG, "Cleanup old thumbnail: " + file.getName() + " deleted: " + deleted);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up old thumbnails", e);
        }
    }

    public void cleanup() {
        cleanupOldThumbnails();
    }
}