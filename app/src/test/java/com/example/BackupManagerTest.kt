package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.utils.BackupManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testPerformDailyBackup_createsFileAndCleansOld() {
        val jsonSample = """{"version":1,"customers":[],"transactions":[]}"""

        val backupInfo = BackupManager.performDailyBackup(context, jsonSample)
        assertNotNull(backupInfo)
        assertTrue(backupInfo!!.fileName.startsWith("backup_"))
        assertTrue(backupInfo.fileName.endsWith(".json"))

        val backupsList = BackupManager.getDailyBackups(context)
        assertTrue(backupsList.isNotEmpty())
        assertEquals(backupInfo.fileName, backupsList.first().fileName)
    }

    @Test
    fun testMaxLocalBackupsRetention() {
        val backupDir = File(context.filesDir, "daily_backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        // Create 10 dummy backup files
        for (i in 1..10) {
            val dayStr = if (i < 10) "0$i" else "$i"
            val dummyFile = File(backupDir, "backup_2026-08-$dayStr.json")
            dummyFile.writeText("""{"dummy":$i}""")
            dummyFile.setLastModified(1700000000000L + (i * 86400000L))
        }

        // Perform backup which triggers cleanup
        BackupManager.performDailyBackup(context, """{"trigger":true}""")

        val remainingFiles = backupDir.listFiles { _, name -> name.startsWith("backup_") && name.endsWith(".json") } ?: emptyArray()
        assertTrue("Backups should be retained within rolling window (<= 7)", remainingFiles.size <= 7)
    }

    @Test
    fun testChangeTracking_markDataChangedAndMarkBackupCompleted() {
        assertFalse("Initially no unbacked changes should be present", BackupManager.hasUnbackedChanges(context))

        // Mutation occurs
        BackupManager.markDataChanged(context)
        assertTrue("hasUnbackedChanges should be true after mutation", BackupManager.hasUnbackedChanges(context))

        // Backup completed
        BackupManager.markBackupCompleted(context)
        assertFalse("hasUnbackedChanges should be false after backup completed", BackupManager.hasUnbackedChanges(context))
    }

    @Test
    fun testIsBackupNeededToday_withChanges() {
        // If backup was performed today and no changes, isBackupNeededToday should be false
        BackupManager.markBackupCompleted(context)
        assertFalse(BackupManager.isBackupNeededToday(context))

        // When a new change happens, isBackupNeededToday becomes true
        BackupManager.markDataChanged(context)
        assertTrue(BackupManager.isBackupNeededToday(context))
    }
}

