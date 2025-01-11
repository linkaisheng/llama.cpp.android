package com.example.llama.chat.model

import java.util.UUID
import java.time.Instant

// 消息角色
enum class Role {
    SYSTEM,
    USER,
    ASSISTANT
}

// 消息状态
sealed class MessageStatus {
    object Sending : MessageStatus()
    object Sent : MessageStatus()
    object Generating : MessageStatus()
    object Complete : MessageStatus()
    data class Error(val error: String) : MessageStatus()
    object Stopped : MessageStatus()
}

// 消息内容元素
sealed class MessageElement {
    data class Text(val content: String) : MessageElement()
    data class CodeBlock(
        val language: String,
        val code: String
    ) : MessageElement()
    data class List(
        val items: kotlin.collections.List<String>,
        val ordered: Boolean = false
    ) : MessageElement()
    data class Quote(val content: String) : MessageElement()
    // 可以根据需要添加更多类型，如表格、图片等
}

// 聊天消息
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val elements: List<MessageElement>,
    val status: MessageStatus = MessageStatus.Complete,
    val timestamp: Instant = Instant.now()
)

// 对话主题
data class ChatTopic(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage>,
    val systemPrompt: String? = null,  // 允许每个对话设置自定义的system prompt
    val updatedAt: Instant = Instant.now()
)

// 对话设置
data class ChatSettings(
    val maxContextLength: Int = 4096,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxGenerateTokens: Int = 2048,
    val stopSequences: List<String> = emptyList()
)

// 对话状态
sealed class ChatState {
    object Idle : ChatState()
    object Loading : ChatState()
    data class Active(val topic: ChatTopic) : ChatState()
    data class Error(val message: String) : ChatState()
} 