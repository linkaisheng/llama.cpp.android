package com.example.llama.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.llama.chat.ChatViewModel
import com.example.llama.chat.model.ChatMessage
import com.example.llama.chat.model.MessageStatus
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val inputText = remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val isGenerating by viewModel.isGenerating.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar with settings button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true
        ) {
            items(messages.reversed()) { message ->
                MessageBubble(
                    message = message,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                )
            }
        }

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText.value,
                onValueChange = { inputText.value = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                placeholder = { Text("Type a message...") },
                maxLines = 3
            )

            if (isGenerating) {
                IconButton(
                    onClick = { 
                        coroutineScope.launch {
                            viewModel.stopGeneration()
                        }
                    }
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
            } else {
                IconButton(
                    onClick = {
                        if (inputText.value.isNotEmpty()) {
                            coroutineScope.launch {
                                viewModel.sendMessage(inputText.value)
                                inputText.value = ""
                            }
                        }
                    },
                    enabled = inputText.value.isNotEmpty()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
} 