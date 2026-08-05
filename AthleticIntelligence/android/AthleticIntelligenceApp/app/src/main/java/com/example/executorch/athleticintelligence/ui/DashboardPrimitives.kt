/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorch.athleticintelligence.history.ActivityDailyTrendPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
internal fun DashboardScreenHeader(
    eyebrow: String,
    title: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun DashboardStatusPill(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun DashboardCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MetaCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), content = content)
    }
}

@Composable
internal fun DashboardMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(modifier, MetaMetricShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Text(
                value,
                color = accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DashboardEmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MetaCardShape)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun DashboardSegmentedSurface(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(MetaPillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        content = content,
    )
}

@Composable
internal fun DashboardSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val gradient = LocalMetaGradient.current
    Surface(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
            ),
        shape = MetaSegmentShape,
        color = Color.Transparent,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (selected) Modifier.background(gradient, MetaSegmentShape)
                    else Modifier.background(Color.Transparent, MetaSegmentShape)
                )
                .padding(horizontal = 13.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun DestinationBar(
    selected: DashboardDestination,
    navigationEnabled: Boolean,
    select: (DashboardDestination) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                DashboardDestination.entries.forEach { destination ->
                    DestinationItem(
                        destination = destination,
                        selected = selected == destination,
                        enabled = navigationEnabled || selected == destination,
                        onClick = { select(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DestinationRail(
    selected: DashboardDestination,
    navigationEnabled: Boolean,
    select: (DashboardDestination) -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "PIQ",
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
            DashboardDestination.entries.forEach { destination ->
                DestinationItem(
                    destination = destination,
                    selected = selected == destination,
                    enabled = navigationEnabled || selected == destination,
                    onClick = { select(destination) },
                )
            }
        }
    }
}

@Composable
private fun DestinationItem(
    destination: DashboardDestination,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .testTag("destination-${destination.label}")
            .semantics { this.selected = selected }
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            ),
        shape = MetaCardShape,
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DestinationGlyph(destination, selected)
            Spacer(Modifier.height(4.dp))
            Text(destination.label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DestinationGlyph(destination: DashboardDestination, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        when (destination) {
            DashboardDestination.HOME -> {
                drawCircle(color, size.minDimension * 0.28f, center, style = Stroke(stroke))
                drawCircle(color, size.minDimension * 0.09f, center)
            }

            DashboardDestination.INSIGHTS -> {
                val points = listOf(
                    Offset(size.width * .12f, size.height * .75f),
                    Offset(size.width * .38f, size.height * .48f),
                    Offset(size.width * .60f, size.height * .62f),
                    Offset(size.width * .86f, size.height * .22f),
                )
                points.zipWithNext().forEach { (start, end) ->
                    drawLine(color, start, end, stroke, StrokeCap.Round)
                }
                points.forEach { drawCircle(color, 1.7.dp.toPx(), it) }
            }

            DashboardDestination.TIMELINE -> {
                drawLine(
                    color,
                    Offset(size.width * .26f, size.height * .12f),
                    Offset(size.width * .26f, size.height * .88f),
                    stroke,
                )
                listOf(.22f, .5f, .78f).forEach { y ->
                    drawCircle(color, 2.3.dp.toPx(), Offset(size.width * .26f, size.height * y))
                    drawLine(
                        color,
                        Offset(size.width * .43f, size.height * y),
                        Offset(size.width * .88f, size.height * y),
                        stroke,
                    )
                }
            }
        }
    }
}

// ---- Squat Insights/Timeline building blocks ----

internal data class MetricTileSpec(val label: String, val value: String, val accent: Color? = null)

/** Lays out metric tiles two per row. */
@Composable
internal fun MetricHeroGrid(tiles: List<MetricTileSpec>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTiles.forEach { tile ->
                    DashboardMetricTile(
                        tile.label,
                        tile.value,
                        Modifier.weight(1f),
                        tile.accent ?: MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun PeriodSelector(selected: TrendPeriod, select: (TrendPeriod) -> Unit) {
    DashboardSegmentedSurface {
        TrendPeriod.entries.forEach { period ->
            DashboardSegment(
                label = period.label,
                selected = selected == period,
                onClick = { select(period) },
            )
        }
    }
}

/** Day-bucketed issue-rate line chart for squat insights. */
@Composable
internal fun TrendChartCard(
    title: String,
    subtitle: String,
    emptyTitle: String,
    emptyBody: String,
    points: List<ActivityDailyTrendPoint>,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        if (points.none { it.actionCount > 0 }) {
            DashboardEmptyState(emptyTitle, emptyBody)
        } else {
            val dateFormatter = DateTimeFormatter.ofPattern("MMM d")
            val zone = ZoneId.systemDefault()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("100%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Text("Issue rate", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            val summary = buildString {
                append(title)
                append(". ")
                append(points.size)
                append(" day buckets. ")
                append(points.sumOf { it.actionCount })
                append(" actions, ")
                append(points.sumOf { it.affectedActionCount })
                append(" affected.")
            }
            val outlineColor = MaterialTheme.colorScheme.outline
            val primaryColor = MaterialTheme.colorScheme.primary
            val surfaceColor = MaterialTheme.colorScheme.surface
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .semantics { contentDescription = summary }
                    .padding(vertical = 10.dp),
            ) {
                val left = 4.dp.toPx()
                val right = size.width - 4.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                drawLine(outlineColor, Offset(left, 0f), Offset(left, bottom), 1.dp.toPx())
                drawLine(outlineColor, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
                if (points.isNotEmpty()) {
                    val xStep = if (points.size == 1) 0f else (right - left) / (points.size - 1)
                    val chartPoints = points.mapIndexed { index, point ->
                        Offset(
                            x = if (points.size == 1) (left + right) / 2f else left + xStep * index,
                            y = bottom - bottom * (point.issueRatePercent / 100f),
                        )
                    }
                    chartPoints.zipWithNext().forEach { (start, end) ->
                        drawLine(primaryColor, start, end, 3.dp.toPx(), StrokeCap.Round)
                    }
                    chartPoints.forEach {
                        drawCircle(surfaceColor, 6.dp.toPx(), it)
                        drawCircle(primaryColor, 4.dp.toPx(), it)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    Instant.ofEpochMilli(points.first().dayStartEpochMs).atZone(zone).format(dateFormatter),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                Text(
                    Instant.ofEpochMilli(points.last().dayStartEpochMs).atZone(zone).format(dateFormatter),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** Most-frequent-issue bar list for squat insights. */
@Composable
internal fun IssueBarsCard(
    title: String,
    subtitle: String,
    emptyTitle: String,
    emptyBody: String,
    issueCounts: List<IssueCount>,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        if (issueCounts.isEmpty()) {
            DashboardEmptyState(emptyTitle, emptyBody)
        } else {
            val maxCount = max(1, issueCounts.maxOf { it.count })
            issueCounts.take(5).forEach { issue ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(issue.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(issue.count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(issue.count / maxCount.toFloat())
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Maps severity in the range 0..1 to a color and label. */
@Composable
internal fun severityColor(severity: Float): Color = when {
    severity >= .67f -> MetaSemantic.error
    severity >= .34f -> MetaSemantic.warning
    else -> MaterialTheme.colorScheme.primary
}

internal fun severityLabel(severity: Float): String = when {
    severity >= .67f -> "High"
    severity >= .34f -> "Medium"
    else -> "Low"
}

@Composable
internal fun TimelineRow(
    accent: Color,
    title: String,
    time: String,
    detail: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(12.dp)
                .background(accent, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            action?.let {
                Spacer(Modifier.height(8.dp))
                it()
            }
        }
    }
}

internal data class TimelineRowSpec(
    val accent: Color,
    val title: String,
    val time: String,
    val detail: String,
    val action: (@Composable () -> Unit)? = null,
)

/** Session or correction feed card for the squat timeline. */
@Composable
internal fun TimelineFeedCard(
    title: String,
    subtitle: String,
    emptyTitle: String,
    emptyBody: String,
    rows: List<TimelineRowSpec>,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        if (rows.isEmpty()) {
            DashboardEmptyState(emptyTitle, emptyBody)
        } else {
            rows.forEach { row -> TimelineRow(row.accent, row.title, row.time, row.detail, row.action) }
        }
    }
}

internal data class FocusRowSpec(
    val label: String,
    val timeText: String,
    val context: String,
    val detail: String,
    val severity: Float,
)

/** Severity-ranked correction list for squat insights. */
@Composable
internal fun DeepInsightsCard(
    title: String,
    subtitle: String,
    emptyTitle: String,
    emptyBody: String,
    rows: List<FocusRowSpec>,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        if (rows.isEmpty()) {
            DashboardEmptyState(emptyTitle, emptyBody)
        } else {
            rows.forEach { FocusIssueRow(it.label, it.timeText, it.context, it.detail, it.severity) }
        }
    }
}

/**
 * One flagged squat correction used by the timeline and insights views.
 * [context] is a date/session-context string; [detail] is optional context.
 */
@Composable
internal fun FocusIssueRow(label: String, timeText: String, context: String, detail: String, severity: Float) {
    TimelineRow(
        accent = severityColor(severity),
        title = label,
        time = timeText,
        detail = listOfNotNull(
            context.takeIf { it.isNotBlank() },
            "${severityLabel(severity)} severity",
            detail.takeIf { it.isNotBlank() },
        ).joinToString(" • "),
    )
}

@Composable
internal fun TopFocusCard(label: String, timeText: String, context: String, detail: String, severity: Float) {
    Surface(
        shape = MetaCardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, severityColor(severity).copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "${severityLabel(severity)} PRIORITY",
                color = severityColor(severity),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            FocusIssueRow(label, timeText, context, detail, severity)
        }
    }
}

@Composable
internal fun TimelineSegmentSelector(selected: TimelineSegment, select: (TimelineSegment) -> Unit) {
    DashboardSegmentedSurface(Modifier.fillMaxWidth()) {
        DashboardSegment(
            "Session",
            selected == TimelineSegment.SESSION,
            { select(TimelineSegment.SESSION) },
            Modifier.weight(1f),
        )
        DashboardSegment(
            "Mistakes",
            selected == TimelineSegment.MISTAKES,
            { select(TimelineSegment.MISTAKES) },
            Modifier.weight(1f),
        )
    }
}
