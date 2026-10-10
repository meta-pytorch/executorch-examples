package com.mathpal.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mathpal.app.ui.components.StreakBadge
import com.mathpal.app.ui.theme.MathPalGreen

enum class ProblemStatus { LOCKED, UNLOCKED, COMPLETED }

data class PracticeProblem(
    val id: String,
    val title: String,
    val category: String,
    val gradeLevel: String = "Grade 8",
    val status: ProblemStatus,
)

enum class GradeLevel(val label: String, val emoji: String) {
    GRADE_4("Grade 4", "🌱"),
    GRADE_5("Grade 5", "🌿"),
    GRADE_6("Grade 6", "🌳"),
    GRADE_7("Grade 7", "⚡"),
    GRADE_8("Grade 8", "🔥"),
}

enum class TopicFilter(val label: String) {
    ALL("All"),
    EQUATIONS("Equations"),
    GEOMETRY("Geometry"),
    RATIOS("Ratios"),
    PERCENTAGES("Percentages"),
    PROBABILITY("Probability"),
    FRACTIONS("Fractions"),
    ARITHMETIC("Arithmetic"),
}

@Composable
fun PracticeScreen(
    completedCount: Int = 0,
    totalCount: Int = 10,
    streak: Int = 0,
    problems: List<PracticeProblem> = emptyList(),
    onProblemSelected: (problemId: String) -> Unit,
) {
    var selectedGrade by remember { mutableStateOf(GradeLevel.GRADE_8) }
    var selectedTopic by remember { mutableStateOf(TopicFilter.ALL) }

    val filteredProblems = problems.filter { p ->
        val gradeMatch = p.gradeLevel == selectedGrade.label
        val topicMatch = selectedTopic == TopicFilter.ALL || p.category.equals(selectedTopic.label, ignoreCase = true)
        gradeMatch && topicMatch
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Practice",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                StreakBadge(streak = streak)
            }
        }

        // Grade level selector
        item {
            Text(
                text = "📚 Grade Level",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GradeLevel.entries) { grade ->
                    FilterChip(
                        selected = selectedGrade == grade,
                        onClick = { selectedGrade = grade },
                        label = { Text("${grade.emoji} ${grade.label}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        }

        // Topic filter
        item {
            Text(
                text = "🎯 Topic",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TopicFilter.entries) { topic ->
                    FilterChip(
                        selected = selectedTopic == topic,
                        onClick = {
                            selectedTopic = if (selectedTopic == topic) TopicFilter.ALL else topic
                        },
                        label = { Text(topic.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        }

        // Progress
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${filteredProblems.size} problems available",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { completedCount.toFloat() / totalCount.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }

        // Problem cards
        if (filteredProblems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🔍", style = MaterialTheme.typography.displaySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No problems for this topic yet",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Try a different topic or grade level",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        itemsIndexed(filteredProblems) { _, problem ->
            ProblemCard(
                problem = problem,
                onClick = {
                    if (problem.status != ProblemStatus.LOCKED) {
                        onProblemSelected(problem.id)
                    }
                },
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ProblemCard(
    problem: PracticeProblem,
    onClick: () -> Unit,
) {
    val isLocked = problem.status == ProblemStatus.LOCKED
    val isCompleted = problem.status == ProblemStatus.COMPLETED

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        enabled = !isLocked,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCompleted -> MathPalGreen.copy(alpha = 0.1f)
                isLocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLocked) 0.dp else 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = problem.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isLocked) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📂 ${problem.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = when (problem.status) {
                    ProblemStatus.COMPLETED -> Icons.Default.CheckCircle
                    ProblemStatus.LOCKED -> Icons.Default.Lock
                    ProblemStatus.UNLOCKED -> Icons.Default.PlayArrow
                },
                contentDescription = problem.status.name,
                tint = when (problem.status) {
                    ProblemStatus.COMPLETED -> MathPalGreen
                    ProblemStatus.LOCKED -> MaterialTheme.colorScheme.outline
                    ProblemStatus.UNLOCKED -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}
