package com.mathpal.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mathpal.app.ui.home.HomeScreen
import com.mathpal.app.ui.onboarding.OnboardingScreen
import com.mathpal.app.ui.practice.PracticeScreen
import com.mathpal.app.ui.progress.ProgressScreen
import com.mathpal.app.ui.solve.MathViewModel
import com.mathpal.app.ui.solve.SolveScreen
import com.mathpal.app.ui.theme.MathPalTheme

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Practice : Screen("practice", "Practice", Icons.Default.School)
    data object Progress : Screen("progress", "Progress", Icons.Default.TrendingUp)
}

class MathPalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MathPalTheme {
                val mathViewModel: MathViewModel = viewModel()
                MathPalApp(mathViewModel)
            }
        }
    }
}

@Composable
fun MathPalApp(mathViewModel: MathViewModel) {
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        mathViewModel.ensureModelLoaded()
    }

    val tabs = listOf(Screen.Home, Screen.Practice, Screen.Progress)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                if (!mathViewModel.isModelLoaded) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.mathpal.app.R.drawable.mathpal_mascot),
                                contentDescription = "MathPal",
                                modifier = Modifier.size(160.dp),
                            )
                            Spacer(modifier = Modifier.padding(8.dp))
                            Text(
                                text = "MathPal",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                            // Sliding math symbols
                            val allSymbols = listOf("➕", "➖", "✖️", "➗", "=", "π", "√", "∫", "d/dx", "∞", "Σ", "Δ", "≈", "%", "θ", "λ")
                            val transition = rememberInfiniteTransition(label = "sym")
                            val symbolIndex by transition.animateFloat(
                                initialValue = 0f,
                                targetValue = allSymbols.size.toFloat(),
                                animationSpec = infiniteRepeatable(
                                    animation = tween(
                                        durationMillis = allSymbols.size * 800,
                                        easing = LinearEasing,
                                    ),
                                ),
                                label = "idx",
                            )
                            val idx = symbolIndex.toInt() % allSymbols.size
                            val window = (0 until 4).map { allSymbols[(idx + it) % allSymbols.size] }
                            Text(
                                text = window.joinToString("   "),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            )
                            androidx.compose.foundation.layout.Spacer(
                                modifier = Modifier.padding(16.dp)
                            )
                            CircularProgressIndicator()
                            androidx.compose.foundation.layout.Spacer(
                                modifier = Modifier.padding(8.dp)
                            )
                            Text(mathViewModel.loadingMessage)
                            androidx.compose.foundation.layout.Spacer(
                                modifier = Modifier.padding(24.dp)
                            )
                            Text(
                                text = "⚡ Powered by ExecuTorch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Text(
                                text = "On-device AI • No internet needed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                } else {
                    val speechLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            val spoken = result.data
                                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                                ?.firstOrNull()
                            if (!spoken.isNullOrBlank()) {
                                mathViewModel.solve(spoken)
                                navController.navigate("solve")
                            }
                        }
                    }
                    HomeScreen(
                        onSolve = { question ->
                            mathViewModel.solve(question)
                            navController.navigate("solve")
                        },
                        onDailyChallenge = {
                            mathViewModel.solve("A bag has 5 red and 3 blue marbles. If you pick 2 without replacement, what is the probability both are red?")
                            navController.navigate("solve")
                        },
                        onVoiceInput = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your math problem...")
                            }
                            speechLauncher.launch(intent)
                        },
                    )
                }
            }
            composable("solve") {
                SolveScreen(
                    question = mathViewModel.currentQuestion,
                    steps = mathViewModel.steps,
                    finalAnswer = mathViewModel.finalAnswer,
                    isGenerating = mathViewModel.isGenerating,
                    onGotIt = { navController.popBackStack() },
                    onExplainMore = { mathViewModel.explainMore() },
                    onNewProblem = {
                        mathViewModel.stopGeneration()
                        navController.popBackStack()
                    },
                )
            }
            composable("practice") {
                val bankProblems = com.mathpal.app.gamification.ProblemBank.DAILY_PROBLEMS
                val practiceProblems = bankProblems.map { p ->
                    com.mathpal.app.ui.practice.PracticeProblem(
                        id = p.id,
                        title = if (p.question.length > 80) p.question.take(80) + "..." else p.question,
                        category = p.category.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " "),
                        gradeLevel = p.gradeLevel.label,
                        status = com.mathpal.app.ui.practice.ProblemStatus.UNLOCKED,
                    )
                }
                PracticeScreen(
                    problems = practiceProblems,
                    onProblemSelected = { problemId ->
                        val problem = bankProblems.find { it.id == problemId }
                        if (problem != null) {
                            mathViewModel.solve(problem.question)
                            navController.navigate("solve")
                        }
                    }
                )
            }
            composable("progress") {
                ProgressScreen(
                    onPracticeWeak = { topics ->
                        navController.navigate("practice")
                    }
                )
            }
        }
    }
}
