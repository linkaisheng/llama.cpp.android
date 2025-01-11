package com.example.llama.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.llama.chat.model.MessageElement

@Composable
fun MessageContent(
    elements: List<MessageElement>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        elements.forEach { element ->
            when (element) {
                is MessageElement.Text -> {
                    MarkdownMessage(
                        markdown = element.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is MessageElement.CodeBlock -> {
                    MarkdownMessage(
                        markdown = "```${element.language}\n${element.code}\n```",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is MessageElement.List -> {
                    val markdown = buildString {
                        element.items.forEachIndexed { index, item ->
                            if (element.ordered) {
                                append("${index + 1}. $item\n")
                            } else {
                                append("* $item\n")
                            }
                        }
                    }
                    MarkdownMessage(
                        markdown = markdown,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is MessageElement.Quote -> {
                    MarkdownMessage(
                        markdown = "> ${element.content}",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    MarkdownMessage(
                        markdown = element.toString(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
} 