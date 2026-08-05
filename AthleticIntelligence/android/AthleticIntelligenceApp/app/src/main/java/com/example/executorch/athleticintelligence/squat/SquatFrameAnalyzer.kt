/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

/**
 * Result of processing one frame through [SquatFrameAnalyzer]. [activeIssues] mirrors
 * `RepEvidenceTracker.active_issues` (unthrottled, used for a persistent "current issues"
 * display). [immediateAlerts] mirrors Python's unthrottled `all_alerts` detector
 * stream; [emittedAlerts] is its cooldown-throttled `banner_alerts` subset.
 */
data class SquatFrameResult(
    val metrics: FrameMetrics,
    val phase: SquatPhase,
    val reps: Int,
    val activeIssues: List<Alert>,
    val immediateAlerts: List<Alert>,
    val emittedAlerts: List<Alert>,
    val completedRep: RepRecord?,
)

/**
 * Owns everything needed to turn a stream of per-frame landmarks into squat phases,
 * rep counts, and rep grades: the [SquatStateMachine], the in-progress
 * [RepEvidenceTracker], the list of [RepRecord]s completed so far, the frame count, and
 * `SquatGuard` (`_process_view`, `_update_rep_tracking`), minus all OpenCV/drawing code.
 *
 * Missing landmarks (an empty [Landmarks] map) flow through [extractFrameMetrics] like
 * any other frame: every geometry lookup fails closed to null/NOT_APPLICABLE, coverage
 * is zero, and [SquatStateMachine.update] is skipped when the tracking angle is null -
 * so a blank frame never invents a phase transition.
 */
class SquatFrameAnalyzer(
    private val view: SquatView,
    private val alertCooldownFrames: Int = ALERT_COOLDOWN_FRAMES,
) {
    companion object {
        const val ALERT_COOLDOWN_FRAMES = 60
    }

    private val stateMachine = SquatStateMachine()
    private var currentTracker: RepEvidenceTracker? = null
    private val completedRecords = mutableListOf<RepRecord>()

    // Per-metric cooldown, mirroring `SquatGuard._last_alert_frame: Dict[str, int]`.
    private val lastAlertFrame = mutableMapOf<String, Long>()

    var frameCount: Long = 0L
        private set

    val phase: SquatPhase get() = stateMachine.phase
    val reps: Int get() = stateMachine.reps

    fun completedRecords(): List<RepRecord> = completedRecords.toList()

    fun processFrame(landmarks: Landmarks, widthPx: Double, heightPx: Double): SquatFrameResult {
        frameCount += 1

        val metrics = extractFrameMetrics(landmarks, widthPx, heightPx, view, stateMachine.phase)
        // Python calls detect_* inside `_process_view` before updating the
        // state machine for this frame. Preserve that ordering, including
        // detect_side's previous-trunk sample used by butt-wink detection.
        val immediateAlerts = when (view) {
            SquatView.FRONT -> detectFront(landmarks, widthPx, heightPx)
            SquatView.SIDE -> detectSide(landmarks, widthPx, heightPx, stateMachine).alerts
            SquatView.BACK -> detectBack(landmarks, widthPx, heightPx)
        }
        val emittedAlerts = throttleAlerts(immediateAlerts)

        val prevPhase = stateMachine.phase
        if (metrics.trackingKneeAngleDeg != null) {
            stateMachine.update(metrics.trackingKneeAngleDeg)
        }
        val newPhase = stateMachine.phase

        val completedRecord = updateRepTracking(prevPhase, newPhase, metrics)

        val activeIssues = currentTracker?.activeIssues ?: emptyList()

        return SquatFrameResult(
            metrics = metrics,
            phase = newPhase,
            reps = stateMachine.reps,
            activeIssues = activeIssues,
            immediateAlerts = immediateAlerts,
            emittedAlerts = emittedAlerts,
            completedRep = completedRecord,
        )
    }

    /** Mirrors `SquatGuard._update_rep_tracking`. */
    private fun updateRepTracking(prevPhase: SquatPhase, newPhase: SquatPhase, metrics: FrameMetrics): RepRecord? {
        if (prevPhase == SquatPhase.STANDING && newPhase != SquatPhase.STANDING) {
            currentTracker = RepEvidenceTracker(stateMachine.reps + 1, view)
        }

        currentTracker?.recordFrame(metrics)

        var completedRecord: RepRecord? = null
        if (prevPhase != SquatPhase.STANDING && newPhase == SquatPhase.STANDING) {
            currentTracker?.let { tracker ->
                val record = tracker.finalize()
                // Only a genuinely completed rep increments `reps`; an aborted
                // descent->standing transition does not, so its record is discarded.
                if (stateMachine.reps > completedRecords.size) {
                    completedRecords.add(record)
                    completedRecord = record
                }
            }
            currentTracker = null
        }
        return completedRecord
    }

    /** Mirrors the `_last_alert_frame` cooldown throttling in `SquatGuard._process_view`. */
    private fun throttleAlerts(alerts: List<Alert>): List<Alert> {
        val emitted = mutableListOf<Alert>()
        for (alert in alerts) {
            val last = lastAlertFrame[alert.metric] ?: -alertCooldownFrames.toLong()
            if (frameCount - last >= alertCooldownFrames) {
                emitted.add(alert)
                lastAlertFrame[alert.metric] = frameCount
            }
        }
        return emitted
    }

    fun reset() {
        stateMachine.reset()
        currentTracker = null
        completedRecords.clear()
        lastAlertFrame.clear()
        frameCount = 0L
    }
}
