package com.example.readwisecar.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.readwisecar.ReadwiseCarApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ReadwiseCarApp

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompanionScreen(app)
                }
            }
        }
    }
}

@Composable
private fun CompanionScreen(app: ReadwiseCarApp) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(app.tokenStore.apiToken) }
    var autoAdvance by remember { mutableStateOf(app.tokenStore.autoAdvance) }
    var status by remember { mutableStateOf("Ready to sync") }
    var isSyncing by remember { mutableStateOf(false) }

    val totalCount by app.repository.observeCounts().collectAsState(initial = 0)
    val cachedCount by app.repository.observeCachedHtmlCount().collectAsState(initial = 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Readwise Car", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Readwise Token (readwise.io/access_token)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    app.tokenStore.apiToken = token
                    status = "Token saved locally."
                }
            ) {
                Text("Save Token")
            }

            Button(
                enabled = !isSyncing,
                onClick = {
                    app.tokenStore.apiToken = token
                    isSyncing = true
                    scope.launch {
                        val res = app.repository.syncFromReadwise { msg -> status = msg }
                        res.onFailure { status = "Error: ${it.message}" }
                        isSyncing = false
                    }
                }
            ) {
                Text(if (isSyncing) "Syncing..." else "Sync Now")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Auto-advance to next article")
            Switch(
                checked = autoAdvance,
                onCheckedChange = { enabled ->
                    autoAdvance = enabled
                    app.tokenStore.autoAdvance = enabled
                }
            )
        }

        Text("Library: $totalCount articles ($cachedCount cached offline)")
        Text("Status: $status", style = MaterialTheme.typography.bodyMedium)

        OutlinedButton(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    File(app.cacheDir, "tts").deleteRecursively()
                    withContext(Dispatchers.Main) {
                        status = "Cleared cached TTS audio files."
                    }
                }
            }
        ) {
            Text("Clear Audio Cache")
        }

        HorizontalDivider()

        Text(
            "Android Auto Sideload Checklist:\n" +
                "1. Open Settings -> Android Auto on your phone.\n" +
                "2. Tap Version 10 times to unlock Developer Settings.\n" +
                "3. Open the 3-dot menu -> Developer settings -> enable Unknown sources.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
