package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.DatabaseManager
import com.example.data.DatabaseSafetyManager
import com.example.data.DbStartupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        } catch (e: Exception) {
            // Ignored if already initialized or not needed
        }

        // Capture pre-migration database safety copy BEFORE any coroutine or UI opens Room
        DatabaseSafetyManager.prepareBeforeDatabaseOpen(this)
        CoroutineScope(Dispatchers.IO).launch {
            val state = DatabaseSafetyManager.prepareAndVerifySync(this@MainApplication)
            if (state is DbStartupState.Ready) {
                DatabaseManager.optimizeDatabase(com.example.data.AppDatabase.getDatabase(this@MainApplication))
            }
        }

        com.example.utils.AppPreferencesManager.init(this)
        com.example.utils.BackupManager.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}
