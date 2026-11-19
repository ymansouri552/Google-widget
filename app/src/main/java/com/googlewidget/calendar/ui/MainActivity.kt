package com.googlewidget.calendar.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.googlewidget.calendar.ui.app.AppScreen
import com.googlewidget.calendar.ui.theme.GoogleWidgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoogleWidgetTheme {
                AppScreen()
            }
        }
    }
}
