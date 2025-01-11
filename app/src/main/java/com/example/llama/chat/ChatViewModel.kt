package com.example.llama.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llama.Llama
import com.example.llama.chat.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.util.UUID

class ChatViewModel(
    private val llama: Llama
) : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private var currentGenerationJob: Job? = null

    init {
        startNewChat()
    }

    fun startNewChat(title: String = "New Chat", systemPrompt: String? = null) {
        val topic = ChatTopic(
            title = title,
            messages = emptyList(),
            systemPrompt = systemPrompt
        )
        _messages.value = topic.messages
    }

    fun setTemperature(value: Float) {
        llama.updateConfig(llama.getCurrentConfig().copy(temperature = value))
    }

    fun setTopP(value: Float) {
        llama.updateConfig(llama.getCurrentConfig().copy(topP = value))
    }

    fun setMaxGenerateTokens(value: Int) {
        llama.updateConfig(llama.getCurrentConfig().copy(maxGenerateTokens = value))
    }

    suspend fun sendMessage(content: String) {
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            content = content,
            timestamp = Instant.now(),
            status = MessageStatus.Sent,
            elements = listOf(MessageElement.Text(content))
        )
        _messages.value = _messages.value + userMessage

        val assistantMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.ASSISTANT,
            content = "",
            timestamp = Instant.now(),
            status = MessageStatus.Generating,
            elements = listOf(MessageElement.Text(""))
        )
        _messages.value = _messages.value + assistantMessage

        _isGenerating.value = true
        currentGenerationJob = viewModelScope.launch {
            try {
                var generatedContent = ""
                llama.generate(content).collect { token ->
                    generatedContent += token
                    _messages.value = _messages.value.map { message ->
                        if (message.id == assistantMessage.id) {
                            message.copy(
                                content = generatedContent,
                                elements = listOf(MessageElement.Text(generatedContent))
                            )
                        } else {
                            message
                        }
                    }
                }
                _messages.value = _messages.value.map { message ->
                    if (message.id == assistantMessage.id) {
                        message.copy(status = MessageStatus.Complete)
                    } else {
                        message
                    }
                }
            } catch (e: Exception) {
                _messages.value = _messages.value.map { message ->
                    if (message.id == assistantMessage.id) {
                        message.copy(status = MessageStatus.Error(e.message ?: "Unknown error"))
                    } else {
                        message
                    }
                }
            } finally {
                _isGenerating.value = false
                currentGenerationJob = null
            }
        }
    }

    fun stopGeneration() {
        currentGenerationJob?.cancel()
        _isGenerating.value = false
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            llama.unload()
        }
    }
} 