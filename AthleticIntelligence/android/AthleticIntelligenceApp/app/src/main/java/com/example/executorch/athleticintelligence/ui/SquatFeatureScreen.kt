/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlin.math.atan2
import com.example.executorch.athleticintelligence.R
import com.example.executorch.athleticintelligence.activity.ActivityType
import com.example.executorch.athleticintelligence.history.ActivityHistoryPresentation
import com.example.executorch.athleticintelligence.history.buildActivityInsightSnapshot
import com.example.executorch.athleticintelligence.telemetry.SessionSummaryRecord
import com.example.executorch.athleticintelligence.squat.SquatAnnotationFrame
import com.example.executorch.athleticintelligence.squat.SquatOverlayTone
import com.example.executorch.athleticintelligence.squat.SquatPhase
import com.example.executorch.athleticintelligence.squat.SquatView
import com.example.executorch.athleticintelligence.squat.TrackingStatus
import com.example.executorch.athleticintelligence.squat.primaryAlert
import com.example.executorch.athleticintelligence.squat.squatAlertTone
import com.example.executorch.athleticintelligence.squat.squatAnnotationConnections
import com.example.executorch.athleticintelligence.squat.squatCenterCropProjection
import com.example.executorch.athleticintelligence.squat.visibleSquatLandmarks

data class SquatFeatureUiState(
    val selectedDestination: DashboardDestination,
    val timelineSegment: TimelineSegment,
    val backendReady: Boolean,
    val sessionActive: Boolean,
    val phase: SquatPhase,
    val repCount: Int,
    val currentCorrection: String?,
    val latestScore: Int?,
    val tracking: TrackingStatus,
    val history: ActivityHistoryPresentation,
    val pose: SquatPoseUiState? = null,
    // Whether the frame behind the pose overlay is center-cropped or letterboxed.
    // The pose projection must use the same scaling mode to remain aligned.
    val poseFillsView: Boolean = true,
    val collectionError: String? = null,
    val sessionEnding: Boolean = false,
    val cameraStatus: String? = null,
    val cameraRecoveryLabel: String? = null,
    val selectedView: SquatView = SquatView.SIDE,
    val canChangeView: Boolean = true,
    val trendPeriod: TrendPeriod = TrendPeriod.WEEK,
    val summaries: List<SessionSummaryRecord> = emptyList(),
)

data class SquatFeatureActions(
    val selectDestination: (DashboardDestination) -> Unit,
    val selectTimelineSegment: (TimelineSegment) -> Unit,
    val startSession: () -> Unit,
    val endSession: () -> Unit,
    val recoverCamera: () -> Unit = {},
    val selectView: (SquatView) -> Unit = {},
    val exportSession: (String) -> Unit = {},
    val selectTrendPeriod: (TrendPeriod) -> Unit = {},
)

@Composable
fun SquatFeatureScreen(
    state: SquatFeatureUiState,
    actions: SquatFeatureActions,
    cameraContent: @Composable () -> Unit,
) {
    ActivityFeatureShell(
        selectedDestination = state.selectedDestination,
        onDestinationSelected = actions.selectDestination,
    ) { destination, wide ->
        when (destination) {
            DashboardDestination.HOME -> SquatLive(state, actions, wide, cameraContent)
            DashboardDestination.INSIGHTS -> SquatInsights(state, actions)
            DashboardDestination.TIMELINE -> SquatTimeline(state, actions)
        }
    }
}

@Composable
private fun SquatLive(
    state: SquatFeatureUiState,
    actions: SquatFeatureActions,
    wide: Boolean,
    cameraContent: @Composable () -> Unit,
) {
    if (wide) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("squat-live-wide"),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SquatCapturePanel(state, actions, cameraContent, Modifier.weight(1.1f))
            SquatLiveSummary(state, Modifier.weight(.9f).fillMaxHeight(), compact = true)
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("squat-live-narrow"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SquatCapturePanel(state, actions, cameraContent, Modifier.fillMaxWidth())
            SquatLiveSummary(state, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SquatCapturePanel(
    state: SquatFeatureUiState,
    actions: SquatFeatureActions,
    cameraContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.testTag("squat-capture-panel")) {
        DashboardScreenHeader(
            eyebrow = "LIVE ANALYSIS",
            title = "Form coaching",
            trailing = {
                DashboardStatusPill(
                    text = if (state.backendReady) "Ready" else "Preparing",
                    color = if (state.backendReady) MetaSemantic.success else MetaSemantic.warning,
                )
            },
        )
        Spacer(Modifier.height(14.dp))
        SquatViewSelector(
            selectedView = state.selectedView,
            enabled = state.canChangeView,
            onSelect = actions.selectView,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(.78f)
                .clip(MetaCameraShape)
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outline, MetaCameraShape),
        ) {
            cameraContent()
            state.pose?.let { SquatPoseOverlay(it, fillView = state.poseFillsView) }
            state.pose?.annotationFrame?.let {
                SquatAnnotationBadge(
                    frame = it,
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                color = Color(0xB3080A0B),
                shape = CircleShape,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                                if (state.tracking == TrackingStatus.TRACKED) MetaSemantic.success else MetaSemantic.warning,
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(
                        text = if (state.tracking == TrackingStatus.TRACKED) {
                            "Pose tracked"
                        } else {
                            stringResource(R.string.squat_tracking_lost)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SquatControlSurface("PHASE", state.phase.displayName())
            val sessionActionDescription = when {
                state.sessionEnding -> stringResource(R.string.squat_end_session)
                state.sessionActive -> stringResource(R.string.squat_end_session)
                else -> stringResource(R.string.squat_start_session)
            }
            Button(
                onClick = if (state.sessionActive) actions.endSession else actions.startSession,
                enabled = !state.sessionEnding && (state.sessionActive || state.backendReady),
                modifier = Modifier
                    .size(76.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = sessionActionDescription
                        stateDescription = squatLiveGuidance(state)
                    },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.sessionActive) MetaSemantic.error else MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.background,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                if (state.sessionEnding) {
                    Text(
                        text = "Ending…",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                } else if (state.sessionActive) {
                    Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(5.dp)))
                } else {
                    Box(
                        Modifier
                            .size(52.dp)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    )
                }
            }
            SquatControlSurface("REPS", state.repCount.toString())
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = squatLiveGuidance(state),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("squat-live-guidance"),
        )
        state.collectionError?.let {
            Spacer(Modifier.height(12.dp))
            DashboardCard {
                Text("Collection stopped", color = MetaSemantic.error, fontWeight = FontWeight.Bold)
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.cameraStatus?.let { status ->
            Spacer(Modifier.height(12.dp))
            DashboardCard {
                Text(status, fontWeight = FontWeight.Bold)
                state.cameraRecoveryLabel?.let { label ->
                    Button(
                        onClick = actions.recoverCamera,
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun SquatViewSelector(
    selectedView: SquatView,
    enabled: Boolean,
    onSelect: (SquatView) -> Unit,
) {
    Column {
        Text(
            "VIEW",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        DashboardSegmentedSurface(Modifier.fillMaxWidth()) {
            SquatView.entries.forEach { view ->
                DashboardSegment(
                    label = view.displayName(),
                    selected = selectedView == view,
                    onClick = { onSelect(view) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun SquatControlSurface(overline: String, value: String) {
    Surface(
        modifier = Modifier
            .sizeIn(minWidth = 88.dp, minHeight = 48.dp)
            .clearAndSetSemantics { contentDescription = "$overline $value" },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(overline, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SquatAnnotationBadge(
    frame: SquatAnnotationFrame,
    modifier: Modifier = Modifier,
) {
    val alert = frame.primaryAlert()
    val message = alert?.message ?: frame.currentCorrection ?: return
    val tone = alert?.let { squatAlertTone(it.level) } ?: SquatOverlayTone.WARNING
    val accent = when (tone) {
        SquatOverlayTone.SUCCESS -> MetaSemantic.success
        SquatOverlayTone.WARNING -> MetaSemantic.warning
        SquatOverlayTone.ERROR -> MetaSemantic.error
    }
    Surface(
        modifier = modifier.sizeIn(maxWidth = 280.dp),
        color = Color(0xB3080A0B),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(accent, CircleShape),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun SquatLiveSummary(
    state: SquatFeatureUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    DashboardCard(modifier.testTag("squat-live-summary")) {
        Text("Session pulse", style = MaterialTheme.typography.titleLarge)
        Text("Immediate feedback from this session", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (!compact) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardMetricTile("REPS", state.repCount.toString(), Modifier.weight(1f))
                DashboardMetricTile("PHASE", state.phase.displayName(), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(if (compact) 12.dp else 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetricTile("SCORE", state.latestScore?.toString() ?: "—", Modifier.weight(1f))
            DashboardMetricTile("ANGLE", "${state.selectedView.displayName()} view", Modifier.weight(1f))
        }
        if (!compact) {
            Spacer(Modifier.height(10.dp))
            DashboardMetricTile("TRACKING", state.tracking.displayName(), Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
        Text("TOP FOCUS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        when {
            state.tracking != TrackingStatus.TRACKED -> {
                Text("Move fully into frame", style = MaterialTheme.typography.titleMedium)
            }

            state.currentCorrection != null -> {
                Text(
                    state.currentCorrection,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            compact -> Text("Start moving to see feedback", style = MaterialTheme.typography.titleMedium)

            else -> DashboardEmptyState(
                "Start moving to see feedback",
                "Your highest-priority form correction will appear here.",
            )
        }
    }
}

@Composable
private fun SquatPoseOverlay(pose: SquatPoseUiState, fillView: Boolean = true) {
    val frame = pose.annotationFrame
    val visible = visibleSquatLandmarks(frame.landmarks)
    val jointColor = MetaSemantic.warning
    val angleArcColor = MetaSemantic.warning
    // Keep angle labels readable over changing camera backgrounds.
    val angleTextPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            isAntiAlias = true
            isFakeBoldText = true
        }
    }
    val angleBgPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#CC1A1A1A")
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Squat pose overlay, ${visible.size} landmarks"
            },
    ) {
        val projection = squatCenterCropProjection(
            sourceWidthPx = frame.sourceWidthPx,
            sourceHeightPx = frame.sourceHeightPx,
            targetWidthPx = size.width,
            targetHeightPx = size.height,
            fill = fillView,
        )
        fun point(name: String): Offset? = visible[name]?.let {
            projection.project(it.xPx, it.yPx).let { projected ->
                Offset(projected.x, projected.y)
            }
        }

        squatAnnotationConnections.forEach { (startName, endName) ->
            val start = point(startName)
            val end = point(endName)
            if (start != null && end != null) {
                drawLine(MetaSemantic.success, start, end, 3.dp.toPx(), StrokeCap.Round)
            }
        }
        visible.keys.forEach { name ->
            point(name)?.let { drawCircle(jointColor, 4.dp.toPx(), it) }
        }

        frame.angleAnnotations.forEach { annotation ->
            val vertex = point(annotation.vertex) ?: return@forEach
            val a = point(annotation.a) ?: return@forEach
            val c = point(annotation.c) ?: return@forEach
            val a1 = Math.toDegrees(atan2((a.y - vertex.y).toDouble(), (a.x - vertex.x).toDouble()))
            val a2 = Math.toDegrees(atan2((c.y - vertex.y).toDouble(), (c.x - vertex.x).toDouble()))
            var sweep = (a2 - a1).toFloat()
            while (sweep <= -180f) sweep += 360f
            while (sweep > 180f) sweep -= 360f
            val r = 46f
            drawArc(
                color = angleArcColor.copy(alpha = 0.4f),
                startAngle = a1.toFloat(),
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(vertex.x - r, vertex.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 10f),
            )
            val text = "${annotation.label} ${"%.0f".format(annotation.degrees)}°"
            val lx = vertex.x + 14f
            val ly = vertex.y - 14f
            drawContext.canvas.nativeCanvas.apply {
                val tw = angleTextPaint.measureText(text)
                drawRoundRect(lx - 6, ly - 34, lx + tw + 10, ly + 10, 8f, 8f, angleBgPaint)
                drawText(text, lx, ly, angleTextPaint)
            }
        }
    }
}

private fun squatLiveGuidance(state: SquatFeatureUiState): String = when {
    state.tracking != TrackingStatus.TRACKED ->
        "Tracking lost. Move fully into frame to resume coaching."
    state.sessionEnding ->
        "Ending session. Keep the app open while results are saved."
    !state.backendReady ->
        "Preparing motion analysis. Start becomes available when ready."
    state.sessionActive ->
        "Session active. Keep your full body in frame and follow Top Focus."
    else ->
        "Ready to start. Stand fully in frame, then begin your set."
}

/** Squat's severity is a coarse 1/2 Int (WARNING/DANGER); map onto the shared 0..1 scale. */
private fun Int.toSeverityFloat(): Float = coerceIn(1, 2) / 2f

@Composable
private fun SquatInsights(state: SquatFeatureUiState, actions: SquatFeatureActions) {
    val snapshot = buildActivityInsightSnapshot(ActivityType.SQUATS, state.summaries, state.trendPeriod)
    val selectedWindow = snapshot.trendWindows.firstOrNull { it.period == state.trendPeriod }
    val heroTiles = listOf(
        MetricTileSpec("REPS", (selectedWindow?.actionCount ?: 0).toString()),
        MetricTileSpec("AVG SCORE", snapshot.averageScore?.toString() ?: "—", MetaSemantic.success),
        MetricTileSpec("ISSUE RATE", "${selectedWindow?.issueRatePercent ?: 0}%", MetaSemantic.warning),
        MetricTileSpec("SESSIONS", state.history.sessionRows.size.toString()),
    )
    val deepInsightRows = snapshot.topIssues.map {
        FocusRowSpec(
            label = it.title,
            timeText = it.detail,
            context = it.capturedAtEpochMs?.let(::formatCaptureDateTime).orEmpty(),
            detail = "",
            severity = it.severity.toSeverityFloat(),
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DashboardScreenHeader(
            eyebrow = "PERFORMANCE",
            title = "Squat insights",
            trailing = { PeriodSelector(state.trendPeriod, actions.selectTrendPeriod) },
        )
        MetricHeroGrid(heroTiles)
        TrendChartCard(
            "Form trend",
            "Affected reps by session date",
            "No trend yet",
            "Complete sessions on multiple days to reveal your form trend.",
            snapshot.dailyTrendPoints,
        )
        IssueBarsCard(
            "Issue profile",
            "Most frequent form corrections",
            "No issues recorded",
            "Flag frequency appears after a completed session.",
            snapshot.recentIssueCounts,
        )
        DeepInsightsCard(
            "Deep insights",
            "Highest-severity measured deviations",
            "Nothing to review",
            "Measured recommendations will appear after a session.",
            deepInsightRows,
        )
    }
}

@Composable
private fun SquatTimeline(state: SquatFeatureUiState, actions: SquatFeatureActions) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DashboardScreenHeader(eyebrow = "CAPTURE HISTORY", title = "Squat timeline")
        TimelineSegmentSelector(state.timelineSegment, actions.selectTimelineSegment)
        when (state.timelineSegment) {
            TimelineSegment.SESSION -> TimelineFeedCard(
                "Session events",
                "Completed squat sessions in capture order",
                "No squat sessions yet",
                "Complete a session to add it to your timeline.",
                state.history.sessionRows.map { row ->
                    val hasIssues = state.history.mistakeRows.any { it.sessionId == row.sessionId }
                    TimelineRowSpec(
                        accent = if (hasIssues) MetaSemantic.warning else MetaSemantic.success,
                        title = row.title,
                        time = row.status,
                        detail = "${formatCaptureDateTime(row.startedAtEpochMs)} • ${row.detail}",
                        action = {
                            Button(
                                onClick = { actions.exportSession(row.sessionId) },
                                modifier = Modifier.sizeIn(minWidth = 80.dp, minHeight = 44.dp),
                            ) {
                                Text("Export")
                            }
                        },
                    )
                },
            )

            TimelineSegment.MISTAKES -> DeepInsightsCard(
                "Form corrections",
                "Prioritized coaching cues from completed sessions",
                "No form corrections yet",
                "Detected squat corrections will appear here.",
                state.history.mistakeRows.map {
                    FocusRowSpec(
                        label = it.title,
                        timeText = it.detail,
                        context = it.capturedAtEpochMs?.let(::formatCaptureDateTime).orEmpty(),
                        detail = "",
                        severity = it.severity.toSeverityFloat(),
                    )
                },
            )
        }
    }
}

private fun SquatPhase.displayName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun SquatView.displayName(): String =
    wireName.replaceFirstChar { it.uppercase() }

private fun TrackingStatus.displayName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
