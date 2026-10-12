package com.pronoscore360.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.pronoscore360.app.ui.nav.AppNav
import com.pronoscore360.app.ui.theme.PronoTheme
import com.pronoscore360.app.vm.SessionViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val session: SessionViewModel = hiltViewModel()
            val theme by session.theme.collectAsState()
            val user by session.user.collectAsState()
            val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(user?.id) {
                if (user != null) {
                    if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    getSharedPreferences("fcm", MODE_PRIVATE).getString("pending_token", null)?.let { session.registerDevice(it) }
                }
            }
            PronoTheme(theme) { AppNav(session) }
        }
    }
}
