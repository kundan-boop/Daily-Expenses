package com.example

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.ui.AuthViewModel
import com.example.ui.screens.MainContainer
import com.example.ui.theme.DailyExpenseTheme
import com.example.utils.BackupManager
import com.google.firebase.FirebaseApp

class MainActivity : FragmentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "FirebaseApp init error: ${e.message}")
        }

        // Auto re-lock app and trigger backup (only if data changed) whenever app is backgrounded or closed
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                authViewModel.lockApp()
                BackupManager.performAutoBackupOnAppClose(applicationContext)
            }
        })

        enableEdgeToEdge()
        setContent {
            DailyExpenseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainContainer(authViewModel = authViewModel)
                }
            }
        }
    }
}


