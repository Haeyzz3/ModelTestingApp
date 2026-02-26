package com.calorieko.modeltestingapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
                var isLiveMode by remember { mutableStateOf(false) }
                // Check if camera permission was already granted (e.g. from a previous session)
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
                    // Immediately open live camera after permission is granted
                    if (granted) isLiveMode = true
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isLiveMode && hasCameraPermission) {
                        LiveInferenceScreen(
                            classifier = classifier,
                            onBack = { isLiveMode = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        InferenceScreen(
                            classifier = classifier,
                            onOpenLive = {
                                if (hasCameraPermission) isLiveMode = true
                                else permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close() // Releasing resources when app is closed
    }
}

@Composable
fun InferenceScreen(
    classifier: CalorieKoClassifier,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var results by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val source = ImageDecoder.createSource(context.contentResolver, it)
            bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
            bitmap?.let { b -> results = classifier.classify(b) }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("CalorieKo Inference Tester", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Selected Dish",
                modifier = Modifier.size(300.dp).padding(8.dp)
            )
        } ?: Text("No image selected")

        Spacer(Modifier.height(16.dp))

        Button(onClick = { launcher.launch("image/*") }) {
            Text("Select Food Photo")
        }

        // New Button for Live Mode
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenLive) {
            Text("Open Live Camera")
        }

        Spacer(Modifier.height(24.dp))

        ResultDisplay(results)
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