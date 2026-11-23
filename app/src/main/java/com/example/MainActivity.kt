package com.example

import android.app.UiModeManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.example.data.PreferencesRepository
import com.example.ui.CadenceApp
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.resolveCadenceColorScheme
import com.example.ui.theme.resolveIsDark
import com.example.ui.theme.toAppCompatNightMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val startupTheme = PreferencesRepository.readStartupThemeSnapshot(this)
        val darkTheme = startupTheme.appearanceMode.resolveIsDark(resources.configuration)

        AppCompatDelegate.setDefaultNightMode(startupTheme.appearanceMode.toAppCompatNightMode())
        syncPlatformNightMode(startupTheme.appearanceMode)

        val initialSurfaceColor = resolveCadenceColorScheme(
            palette = startupTheme.palette,
            darkTheme = darkTheme,
            context = this
        ).background
        val initialSurfaceArgb = initialSurfaceColor.toArgb()

        val barStyle = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )

        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(ColorDrawable(initialSurfaceArgb))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }

        setContent {
            CadenceApp()
        }
    }

    private fun syncPlatformNightMode(mode: AppearanceMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val uiModeManager = getSystemService(UiModeManager::class.java)
                val platformMode = when (mode) {
                    AppearanceMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    AppearanceMode.DARK -> UiModeManager.MODE_NIGHT_YES
                    AppearanceMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                }
                uiModeManager?.setApplicationNightMode(platformMode)
            }
        }
    }
}
