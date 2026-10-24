package com.pavo.amberoid

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pavo.amberoid.player.AmberoidUI
import com.pavo.amberoid.player.viewmodel.PlayerViewModel
import com.pavo.amberoid.ui.theme.AmberoidTheme

val NerdFont: FontFamily = FontFamily(Font(R.font.symbols_nerd_font_regular))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetController.hide(WindowInsetsCompat.Type.systemBars())

        @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
        setContent {
            AmberoidTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                val viewModel: PlayerViewModel = viewModel()
                val context = androidx.compose.ui.platform.LocalContext.current

                val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.loadSongs()
                    }
                }

                LaunchedEffect(Unit) {
                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        permissionToRequest
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        viewModel.loadSongs()
                    } else {
                        permissionLauncher.launch(permissionToRequest)
                    }
                }
                AmberoidUI(windowSizeClass = windowSizeClass)
            }
        }
    }
}
