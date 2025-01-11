package com.example.llama.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatInput(
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }

    Surface(
        modifier = modifier,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 输入框
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息...") },
                singleLine = false,
                maxLines = 5
            )

            // 发送/停止按钮
            if (isGenerating) {
                IconButton(onClick = onStopGeneration) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "停止生成",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSendMessage(input)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送消息",
                        tint = if (input.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
} 