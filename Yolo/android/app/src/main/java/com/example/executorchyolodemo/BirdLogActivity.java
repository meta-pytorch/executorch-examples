/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchyolodemo;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class BirdLogActivity extends AppCompatActivity {
    private static final String TAG = "BirdLogActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bird_log);

        RecyclerView recyclerView = findViewById(R.id.birdLogRecyclerView);
        TextView sessionSummary = findViewById(R.id.sessionSummary);

        // FIXED: Get bird logs from Intent extras (the correct way)
        ArrayList<BirdSessionManager.BirdSighting> birdLogs =
                (ArrayList<BirdSessionManager.BirdSighting>) getIntent().getSerializableExtra("bird_logs");

        if (birdLogs == null) {
            birdLogs = new ArrayList<>();
        }

        // Setup RecyclerView
        BirdLogAdapter adapter = new BirdLogAdapter(birdLogs);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Show summary
        sessionSummary.setText(String.format("Session Summary: %d birds detected", birdLogs.size()));

        Log.d(TAG, "BirdLogActivity loaded with " + birdLogs.size() + " sightings");
    }

    // RecyclerView Adapter
    public static class BirdLogAdapter extends RecyclerView.Adapter<BirdLogAdapter.BirdViewHolder> {
        private static final String TAG = "BirdLogAdapter";
        private List<BirdSessionManager.BirdSighting> birdSightings;

        public BirdLogAdapter(List<BirdSessionManager.BirdSighting> birdSightings) {
            this.birdSightings = birdSightings;
        }

        @NonNull
        @Override
        public BirdViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bird_log, parent, false);
            return new BirdViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BirdViewHolder holder, int position) {
            BirdSessionManager.BirdSighting sighting = birdSightings.get(position);

            holder.birdName.setText(sighting.species);
            holder.timestamp.setText(sighting.timestamp);
            holder.confidence.setText(String.format("%.1f%%", sighting.confidence * 100));

            // FIXED: Load bitmap from file instead of using transient bitmap
            try {
                Bitmap thumbnail = sighting.loadThumbnail();
                if (thumbnail != null) {
                    holder.thumbnail.setImageBitmap(thumbnail);
                    Log.d(TAG, "Loaded thumbnail for " + sighting.species);
                } else {
                    // Default bird icon if thumbnail loading fails
                    holder.thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
                    Log.w(TAG, "Failed to load thumbnail for " + sighting.species + ", using default icon");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading thumbnail for " + sighting.species, e);
                holder.thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }

        @Override
        public int getItemCount() {
            return birdSightings.size();
        }

        static class BirdViewHolder extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            TextView birdName;
            TextView timestamp;
            TextView confidence;

            BirdViewHolder(View itemView) {
                super(itemView);
                thumbnail = itemView.findViewById(R.id.birdThumbnail);
                birdName = itemView.findViewById(R.id.birdName);
                timestamp = itemView.findViewById(R.id.timestamp);
                confidence = itemView.findViewById(R.id.confidence);
            }
        }
    }
}