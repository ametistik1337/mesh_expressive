package com.droid.dolphy.hid

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.droid.dolphy.MonetHelper
import kotlin.concurrent.thread

class UsbKeyboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen awake while used as a portable USB keyboard
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Allow display above lockscreen when connected
        try {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } catch (_: Exception) {}

        setContent {
            val monetPrimary = MonetHelper.getMonetPrimaryColor(this)
            val darkColors = darkColorScheme(
                primary = monetPrimary,
                background = Color(0xFF121312),
                surface = Color(0xFF1E1F1C)
            )
            MaterialTheme(colorScheme = darkColors) {
                UsbKeyboardScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        thread {
            UsbHidDriver.releaseAll()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        thread {
            UsbHidDriver.releaseAll()
        }
    }
}
