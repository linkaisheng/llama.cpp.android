package com.example.llama.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.llama.chat.model.ChatMessage
import com.example.llama.chat.model.MessageStatus
import com.example.llama.chat.model.Role
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == Role.USER
    val backgroundColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI头像
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .padding(end = 8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = "AI",
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    ),
                color = backgroundColor,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    MessageContent(
                        elements = message.elements,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // 状态指示器
                    if (!isUser && message.status != MessageStatus.Complete) {
                        Spacer(modifier = Modifier.height(4.dp))
                        when (message.status) {
                            MessageStatus.Generating -> {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                )
                            }
                            is MessageStatus.Error -> {
                                Text(
                                    text = "生成失败",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            MessageStatus.Stopped -> {
                                Text(
                                    text = "已停止生成",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }

            // 时间戳
            Text(
                text = DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.SHORT)
                    .withZone(ZoneId.systemDefault())
                    .format(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )
        }

        if (isUser) {
            // 用户头像
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .padding(start = 8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User",
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.onTertiary
                )
            }
        }
    }
} 