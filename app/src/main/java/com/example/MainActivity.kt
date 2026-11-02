package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.screens.CameraScanScreen
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OrthorectifyScreen
import com.example.ui.screens.ScanSessionScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Firebase App Check configuration before UI or Firebase operations
        try {
            configureAppCheck(this, intent)
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Firebase App Check initialization ignored: ${e.message}")
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    when (val screen = currentScreen) {
        is Screen.Home -> HomeScreen(viewModel = viewModel)
        is Screen.CameraScanner -> CameraScanScreen(
            viewModel = viewModel,
            targetDocId = screen.targetDocId
        )
        is Screen.Orthorectify -> OrthorectifyScreen(
            sourceBitmap = screen.capturedBitmap,
            pageNumber = screen.pageNumber,
            onBack = {
                viewModel.navigateBack()
            },
            onConfirmStraightened = { rectifiedBmp, filter ->
                viewModel.confirmRectifiedPage(rectifiedBmp, filter, screen.targetDocId)
            }
        )
        is Screen.ScanSession -> ScanSessionScreen(viewModel = viewModel)
        is Screen.DocumentDetail -> DocumentDetailScreen(
            docId = screen.docId,
            viewModel = viewModel
        )
    }
}
