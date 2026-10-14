package com.aroha.hams.viewmodels

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aroha.hams.ui.HamsApp
import com.aroha.hams.ui.theme.HAMSV2Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HAMSV2Theme {
                HamsApp()
            }
        }
    }
}
