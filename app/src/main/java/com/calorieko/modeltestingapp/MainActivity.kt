package com.calorieko.modeltestingapp

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
import com.calorieko.modeltestingapp.ui.theme.ModelTestingAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize your classifier engine
        val classifier = CalorieKoClassifier(this)

        setContent {
            ModelTestingAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    InferenceScreen(
                        classifier = classifier,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun InferenceScreen(classifier: CalorieKoClassifier, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var results by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }

    // Launcher to pick an image from the gallery
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
        uri?.let {
            // Convert URI to Bitmap
            val source = ImageDecoder.createSource(context.contentResolver, it)
            bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
            // Run inference immediately after picking
            bitmap?.let { b ->
                results = classifier.classify(b)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CalorieKo Inference Tester",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Display the selected image
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Selected Dish",
                modifier = Modifier
                    .size(300.dp)
                    .padding(8.dp)
            )
        } ?: Text("No image selected")

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { launcher.launch("image/*") }) {
            Text("Select Food Photo")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Display Top 3 Results logic from test_model.py
        if (results.isNotEmpty()) {
            val topMatch = results[0]
            val confidence = topMatch.second

            Text(text = "TOP 3 CANDIDATES:", style = MaterialTheme.typography.titleMedium)

            results.forEach { (name, score) ->
                Text(
                    text = "- ${name.lowercase()}: ${(score * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Final Decision Logic matching your Python script
            if (confidence < 0.70f) {
                Text(
                    text = "RESULT: ⚠️ UNCERTAIN",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text("Action: Please retake with better lighting.")
            } else {
                Text(
                    text = "RESULT: ✅ ${topMatch.first.uppercase()}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}