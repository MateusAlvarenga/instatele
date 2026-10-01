package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.screens.ConfigScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.TransferScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

    // Request notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Check if permission is already granted
            val context = viewModel.getApplication<android.app.Application>()
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToConfig = { viewModel.navigateTo(AppScreen.CONFIG) },
                    onNavigateToTransfer = { viewModel.navigateTo(AppScreen.TRANSFER) },
                    onNavigateToHistory = { viewModel.navigateTo(AppScreen.HISTORY) }
                )

                AppScreen.CONFIG -> ConfigScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateTo(AppScreen.HOME) }
                )

                AppScreen.TRANSFER -> TransferScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateTo(AppScreen.HOME) },
                    onNavigateToHistory = { viewModel.navigateTo(AppScreen.HISTORY) }
                )

                AppScreen.HISTORY -> HistoryScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateTo(AppScreen.HOME) }
                )
            }
        }
    }
}
