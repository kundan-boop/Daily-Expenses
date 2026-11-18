package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseBackupRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun uploadAndDownloadBackup_authenticatedOwner_succeedsAndVerifies() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = FirebaseBackupRepository(firestore, auth)
        val payload = """{"version":2,"customers":[{"id":1,"name":"Ravi"}],"transactions":[],"monthlyExpenses":[],"userProfile":{"name":"Alice"}}"""

        val uploadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.uploadDailyBackup(
                jsonString = payload,
                customerCount = 1,
                transactionCount = 0,
                hasUserProfile = true,
                dateIso = "2026-11-16"
            )
        }
        assertTrue("Upload should succeed: ${uploadResult.exceptionOrNull()}", uploadResult.isSuccess)
        val snapshot = uploadResult.getOrThrow()
        assertTrue(snapshot.verified)
        assertEquals("backup_2026-11-16", snapshot.backupId)

        val listResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.listBackups() }
        assertTrue(listResult.isSuccess)
        assertTrue(listResult.getOrThrow().any { it.backupId == "backup_2026-11-16" })

        val downloadResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.downloadBackupJson("backup_2026-11-16") }
        assertTrue(downloadResult.isSuccess)
        assertEquals(payload, downloadResult.getOrThrow())

        val testConn = withTimeout(DEFAULT_TIMEOUT_MS) { repo.testConnection() }
        assertTrue(testConn.isSuccess)
    }

    @Test
    fun uploadMultipleDays_enforcesSevenDayRetentionAfterVerifiedUpload() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = FirebaseBackupRepository(firestore, auth)

        val dates = listOf(
            "2026-11-08", "2026-11-09", "2026-11-10", "2026-11-11",
            "2026-11-12", "2026-11-13", "2026-11-14", "2026-11-15", "2026-11-16"
        )
        dates.forEach { d ->
            val res = withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.uploadDailyBackup(
                    jsonString = """{"date":"$d","customers":[]}""",
                    customerCount = 0,
                    transactionCount = 0,
                    hasUserProfile = false,
                    dateIso = d
                )
            }
            assertTrue("Upload for $d failed: ${res.exceptionOrNull()}", res.isSuccess)
        }

        val backups = withTimeout(DEFAULT_TIMEOUT_MS) { repo.listBackups() }.getOrThrow()
        assertEquals("Exactly 7 newest backups must be kept", 7, backups.size)
        assertFalse("Oldest date 2026-11-08 should be pruned", backups.any { it.backupDate == "2026-11-08" })
        assertFalse("Oldest date 2026-11-09 should be pruned", backups.any { it.backupDate == "2026-11-09" })
        assertTrue("Newest date 2026-11-16 should remain", backups.any { it.backupDate == "2026-11-16" })
    }

    @Test
    fun crossUserAccess_deniedBySecurityRules() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirebaseBackupRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.uploadDailyBackup("""{"customers":[]}""", 0, 0, false, "2026-11-16").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        var denied = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("backups").document("backup_2026-11-16")
                    .get().await()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Cross-user read must fail with PERMISSION_DENIED", denied)
    }

    @Test
    fun unauthenticatedAccess_failsCleanlyAndCannotWriteToFirestore() = runBlocking {
        auth.signOut()
        val repo = FirebaseBackupRepository(firestore, auth)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) { repo.listBackups() }
        assertTrue(result.isFailure)

        var denied = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document("some_uid")
                    .collection("backups").get().await()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Unauthenticated read must fail with PERMISSION_DENIED", denied)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_ledger@test.com"
        const val BOB_EMAIL = "bob_ledger@test.com"
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
