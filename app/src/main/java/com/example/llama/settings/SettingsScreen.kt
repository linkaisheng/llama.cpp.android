package com.example.llama.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.llama.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    var temperature by remember { mutableStateOf(0.7f) }
    var topP by remember { mutableStateOf(0.9f) }
    var maxTokens by remember { mutableStateOf(2048) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // Temperature slider
            Text(
                text = "Temperature: ${String.format("%.2f", temperature)}",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = temperature,
                onValueChange = { 
                    temperature = it
                    viewModel.setTemperature(it)
                },
                valueRange = 0f..1f,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Top-P slider
            Text(
                text = "Top-P: ${String.format("%.2f", topP)}",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = topP,
                onValueChange = { 
                    topP = it
                    viewModel.setTopP(it)
                },
                valueRange = 0f..1f,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Max tokens slider
            Text(
                text = "Max Tokens: $maxTokens",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = maxTokens.toFloat(),
                onValueChange = { 
                    maxTokens = it.toInt()
                    viewModel.setMaxGenerateTokens(it.toInt())
                },
                valueRange = 128f..4096f,
                steps = 31,  // (4096-128)/128 = 31 steps
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
} 