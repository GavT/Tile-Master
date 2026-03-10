package com.example.tilemaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tilemaster.ui.theme.TileMasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TileMasterTheme {
                val gameViewModel: GameViewModel = viewModel()
                GameScreen(viewModel = gameViewModel)
            }
        }
    }
}
