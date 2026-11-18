package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FirebaseBackupSnapshot(
    val backupId: String = "",
    val backupDate: String = "",
    val chunkCount: Int = 0,
    val totalBytes: Long = 0L,
    val verified: Boolean = false,
    val customerCount: Int = 0,
    val transactionCount: Int = 0,
    val hasUserProfile: Boolean = false,
    val updatedAtMillis: Long = 0L
)

class FirebaseBackupRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = FirebaseAuth.getInstance()
    )

    companion object {
        private const val TAG = "FirebaseBackupRepo"
        const val MAX_CLOUD_BACKUP_DAYS = 7
        private const val MAX_CHUNK_CHARS = 350_000
        private const val MAX_CHUNK_UTF8_BYTES = 700_000
        private const val FIRESTORE_OP_TIMEOUT_MS = 15_000L
    }

    fun getCurrentUserUid(): String? = try {
        auth.currentUser?.uid
    } catch (_: Exception) {
        null
    }

    fun getCurrentUserEmail(): String? = try {
        auth.currentUser?.email
    } catch (_: Exception) {
        null
    }

    fun isSignedIn(): Boolean = !getCurrentUserUid().isNullOrBlank()

    private fun backupsCollection(uid: String) =
        db.collection("users").document(uid).collection("backups")

    suspend fun uploadDailyBackup(
        jsonString: String,
        customerCount: Int,
        transactionCount: Int,
        hasUserProfile: Boolean,
        dateIso: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    ): Result<FirebaseBackupSnapshot> = withContext(Dispatchers.IO) {
        val uid = getCurrentUserUid()
            ?: return@withContext Result.failure(IllegalStateException("Google account not signed in for Firebase backup"))
        if (jsonString.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Backup payload is empty"))
        }

        val backupId = "backup_$dateIso"
        val backupRef = backupsCollection(uid).document(backupId)

        try {
            val chunks = splitJsonIntoSafeChunks(jsonString)
            val totalUtf8Bytes = jsonString.toByteArray(Charsets.UTF_8).size
            if (chunks.size > 50) {
                return@withContext Result.failure(IllegalStateException("Backup exceeds cloud chunk limit (${chunks.size} chunks)"))
            }

            // 1. Create or reset manifest with verified = false (preserving immutable createdAt on update)
            val existingManifest = withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                backupRef.get().await()
            }
            if (!existingManifest.exists()) {
                val createMap = mapOf(
                    "userId" to uid,
                    "backupDate" to dateIso,
                    "chunkCount" to chunks.size,
                    "totalBytes" to totalUtf8Bytes,
                    "verified" to false,
                    "customerCount" to customerCount,
                    "transactionCount" to transactionCount,
                    "hasUserProfile" to hasUserProfile,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                    backupRef.set(createMap).await()
                }
            } else {
                val resetMap = mapOf(
                    "chunkCount" to chunks.size,
                    "totalBytes" to totalUtf8Bytes,
                    "verified" to false,
                    "customerCount" to customerCount,
                    "transactionCount" to transactionCount,
                    "hasUserProfile" to hasUserProfile,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                    backupRef.update(resetMap).await()
                }
            }

            // 2. Write every chunk under /users/{uid}/backups/{backupId}/chunks/chunk_XXX
            val existingChunksSnap = withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                backupRef.collection("chunks").get().await()
            }
            val existingChunkIds = existingChunksSnap.documents.map { it.id }.toSet()

            chunks.forEachIndexed { idx, payload ->
                val chunkId = String.format(Locale.US, "chunk_%03d", idx)
                val chunkRef = backupRef.collection("chunks").document(chunkId)
                if (chunkId in existingChunkIds) {
                    withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                        chunkRef.update(
                            mapOf(
                                "payload" to payload,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                } else {
                    withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                        chunkRef.set(
                            mapOf(
                                "userId" to uid,
                                "backupId" to backupId,
                                "chunkIndex" to idx,
                                "payload" to payload,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }

            // Clean any excess leftover chunks from an earlier larger snapshot on the same day
            val validChunkIds = (chunks.indices).map { String.format(Locale.US, "chunk_%03d", it) }.toSet()
            existingChunkIds.filterNot { it in validChunkIds }.forEach { staleChunkId ->
                withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                    backupRef.collection("chunks").document(staleChunkId).delete().await()
                }
            }

            // 3. Verify & mark verified = true only after all chunks are committed
            withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                backupRef.update(
                    mapOf(
                        "verified" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // 4. Prune snapshots older than 7 days strictly AFTER verified write succeeds
            enforceSevenDayRetention(uid)

            val refreshedDoc = withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                backupRef.get().await()
            }
            val snapshot = documentToSnapshot(refreshedDoc) ?: FirebaseBackupSnapshot(
                backupId = backupId,
                backupDate = dateIso,
                chunkCount = chunks.size,
                totalBytes = totalUtf8Bytes.toLong(),
                verified = true,
                customerCount = customerCount,
                transactionCount = transactionCount,
                hasUserProfile = hasUserProfile,
                updatedAtMillis = System.currentTimeMillis()
            )
            Result.success(snapshot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, backupRef.path)
            Result.failure(e)
        }
    }

    suspend fun listBackups(): Result<List<FirebaseBackupSnapshot>> = withContext(Dispatchers.IO) {
        val uid = getCurrentUserUid()
            ?: return@withContext Result.failure(IllegalStateException("Google account not signed in"))
        val path = backupsCollection(uid).path
        try {
            val snap = withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                backupsCollection(uid).get().await()
            }
            val list = snap.documents
                .mapNotNull { documentToSnapshot(it) }
                .filter { it.verified }
                .sortedByDescending { it.backupDate }
                .take(MAX_CLOUD_BACKUP_DAYS)
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun downloadBackupJson(backupId: String): Result<String> = withContext(Dispatchers.IO) {
        val uid = getCurrentUserUid()
            ?: return@withContext Result.failure(IllegalStateException("Google account not signed in"))
        val backupRef = backupsCollection(uid).document(backupId)
        try {
            val manifestDoc = withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                backupRef.get().await()
            }
            if (!manifestDoc.exists()) {
                return@withContext Result.failure(IllegalStateException("Backup $backupId not found"))
            }
            val expectedChunks = (manifestDoc.getLong("chunkCount") ?: 0L).toInt()
            val chunksSnap = withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                backupRef.collection("chunks").get().await()
            }
            val orderedChunks = chunksSnap.documents
                .mapNotNull { doc ->
                    val idx = (doc.getLong("chunkIndex") ?: -1L).toInt()
                    val payload = doc.getString("payload")
                    if (idx >= 0 && payload != null) idx to payload else null
                }
                .sortedBy { it.first }

            if (expectedChunks > 0 && orderedChunks.size < expectedChunks) {
                return@withContext Result.failure(
                    IllegalStateException("Incomplete backup chunks (${orderedChunks.size}/$expectedChunks)")
                )
            }
            val json = orderedChunks.joinToString(separator = "") { it.second }
            if (json.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Backup payload is empty"))
            }
            Result.success(json)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, backupRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = getCurrentUserUid()
            ?: return@withContext Result.failure(IllegalStateException("Google account not signed in"))
        val backupRef = backupsCollection(uid).document(backupId)
        try {
            val chunksSnap = withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                backupRef.collection("chunks").get().await()
            }
            chunksSnap.documents.forEach { chunkDoc ->
                withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                    chunkDoc.reference.delete().await()
                }
            }
            withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                backupRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, backupRef.path)
            Result.failure(e)
        }
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val uid = getCurrentUserUid()
            ?: return@withContext Result.failure(IllegalStateException("Please sign in with Google first"))
        val path = backupsCollection(uid).path
        try {
            val snap = withTimeout(10_000L) {
                backupsCollection(uid).get(Source.SERVER).await()
            }
            val count = snap.documents.count { (it.getBoolean("verified") ?: false) }
            Result.success("Connected to Firebase Firestore • $count verified cloud backup(s)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    private suspend fun enforceSevenDayRetention(uid: String) {
        try {
            val snap = withTimeout(FIRESTORE_OP_TIMEOUT_MS) {
                backupsCollection(uid).get().await()
            }
            val ordered = snap.documents
                .mapNotNull { doc ->
                    val date = doc.getString("backupDate") ?: return@mapNotNull null
                    doc.id to date
                }
                .sortedByDescending { it.second }

            if (ordered.size > MAX_CLOUD_BACKUP_DAYS) {
                val stale = ordered.drop(MAX_CLOUD_BACKUP_DAYS)
                stale.forEach { (staleId, _) ->
                    deleteBackup(staleId)
                    Log.d(TAG, "7-Day Firebase Retention: deleted stale backup $staleId")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Retention cleanup skipped/partial: ${e.message}")
        }
    }

    internal fun splitJsonIntoSafeChunks(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var start = 0
        val len = input.length
        while (start < len) {
            var end = minOf(start + MAX_CHUNK_CHARS, len)
            var slice = input.substring(start, end)
            while (slice.toByteArray(Charsets.UTF_8).size > MAX_CHUNK_UTF8_BYTES && end > start + 1) {
                end -= maxOf(1, (end - start) / 4)
                slice = input.substring(start, end)
            }
            result.add(slice)
            start = end
        }
        return result
    }

    private fun documentToSnapshot(doc: DocumentSnapshot): FirebaseBackupSnapshot? {
        if (!doc.exists()) return null
        val backupDate = doc.getString("backupDate") ?: return null
        val updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return FirebaseBackupSnapshot(
            backupId = doc.id,
            backupDate = backupDate,
            chunkCount = (doc.getLong("chunkCount") ?: 0L).toInt(),
            totalBytes = doc.getLong("totalBytes") ?: 0L,
            verified = doc.getBoolean("verified") ?: false,
            customerCount = (doc.getLong("customerCount") ?: 0L).toInt(),
            transactionCount = (doc.getLong("transactionCount") ?: 0L).toInt(),
            hasUserProfile = doc.getBoolean("hasUserProfile") ?: false,
            updatedAtMillis = updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        )
    }
}
