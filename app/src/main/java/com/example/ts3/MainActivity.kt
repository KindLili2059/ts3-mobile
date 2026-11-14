package com.example.ts3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ts3.data.model.ConnectionState
import com.example.ts3.ui.ChannelScreen
import com.example.ts3.ui.ConnectScreen
import com.example.ts3.ui.theme.Ts3Theme
import com.example.ts3.viewmodel.Ts3ViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Ts3Theme {
                val viewModel: Ts3ViewModel = viewModel()
                val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

                if (connectionState == ConnectionState.ESTABLISHED) {
                    ChannelScreen(viewModel)
                } else {
                    ConnectScreen(viewModel)
                }
            }
        }
    }
}
