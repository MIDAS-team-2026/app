package com.midas26.mobileapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.midas26.mobileapp.ui.navigation.AppNavHost
import com.midas26.mobileapp.ui.theme.AppTheme
import com.midas26.mobileapp.ui.theme.FontSizeLevel
import com.midas26.mobileapp.ui.theme.LocalFontSizeScale
import com.midas26.mobileapp.ui.theme.LocalHighContrast
import com.midas26.mobileapp.util.PrefsManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val prefs = PrefsManager.from(context)
    var fontSizeLevel by remember {
        mutableStateOf(FontSizeLevel.fromIndex(prefs.getAccessibilityFontSize()))
    }
    var highContrast by remember { mutableStateOf(prefs.getHighContrast()) }

    CompositionLocalProvider(
        LocalFontSizeScale provides fontSizeLevel,
        LocalHighContrast provides highContrast
    ) {
        AppTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AppNavHost(
                        onFontSizeChange = { level ->
                            fontSizeLevel = level
                            prefs.setAccessibilityFontSize(level.ordinal)
                        },
                        onHighContrastChange = { enabled ->
                            highContrast = enabled
                            prefs.setHighContrast(enabled)
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppRootPreview() {
    AppRoot()
}
