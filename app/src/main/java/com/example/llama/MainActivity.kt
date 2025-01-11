package com.example.llama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.llama.chat.ChatViewModel
import com.example.llama.chat.ui.ChatScreen
import com.example.llama.models.ModelSelectionScreen
import com.example.llama.settings.SettingsScreen
import com.example.llama.ui.theme.LlamaTheme

class MainActivity : ComponentActivity() {
    private val llama = Llama()
    
    private val viewModel by viewModels<ChatViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(llama) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            LlamaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var modelLoaded by remember { mutableStateOf(false) }
                    var showSettings by remember { mutableStateOf(false) }
                    
                    if (!modelLoaded) {
                        ModelSelectionScreen(
                            llama = llama,
                            onModelLoaded = { modelLoaded = true }
                        )
                    } else {
                        if (showSettings) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { showSettings = false }
                            )
                        } else {
                            ChatScreen(
                                viewModel = viewModel,
                                onOpenSettings = { showSettings = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
