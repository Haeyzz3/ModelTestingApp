package com.calorieko.modeltestingapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.calorieko.modeltestingapp.ui.theme.ModelTestingAppTheme

class MainActivity : ComponentActivity() {
    private lateinit var classifier: CalorieKoClassifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        classifier = CalorieKoClassifier(this)

        setContent {
            ModelTestingAppTheme {
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasCameraPermission = granted
                }

                // Auto-request camera permission on first launch
                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (hasCameraPermission) {
                        LiveInferenceScreen(
                            classifier = classifier,
                            onBack = { finish() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        // Fallback: shown if permission is denied
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Camera permission is required to use this app.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
    }
}

@Composable
fun LiveInferenceScreen(
    classifier: CalorieKoClassifier,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var liveResults by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }

    Box(modifier = modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            classifier = classifier,
            onFrameAnalyzed = { liveResults = it }
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    ResultDisplay(liveResults)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Close Camera")
            }
        }
    }
}

@Composable
fun ResultDisplay(results: List<Pair<String, Float>>) {
    if (results.isNotEmpty()) {
        val topMatch = results[0]
        val confidence = topMatch.second

        Text("TOP 3 CANDIDATES:", style = MaterialTheme.typography.titleMedium)
        results.forEach { (name, score) ->
            Text("- ${name.lowercase()}: ${(score * 100).toInt()}%")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        if (confidence < 0.70f) {
            Text("RESULT: ⚠️ UNCERTAIN", color = MaterialTheme.colorScheme.error)
            Text("Action: Better lighting required.")
        } else {
            Text("RESULT: ✅ ${topMatch.first.uppercase()}", color = MaterialTheme.colorScheme.primary)
        }
    }
}