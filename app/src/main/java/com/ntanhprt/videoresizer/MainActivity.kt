package com.ntanhprt.videoresizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ntanhprt.videoresizer.ui.navigation.AppNavigation
import com.ntanhprt.videoresizer.ui.theme.VideoResizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoResizerTheme {
                AppNavigation()
            }
        }
    }
}
