package com.example.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class GoogleSignInAccountInfo(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?
)

object GoogleAuthHelper {

    fun getCurrentFirebaseUser(): FirebaseUser? {
        return try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
    }

    fun getCurrentAccountInfo(): GoogleSignInAccountInfo? {
        val user = getCurrentFirebaseUser() ?: return null
        return GoogleSignInAccountInfo(
            uid = user.uid,
            displayName = user.displayName ?: "Google Account",
            email = user.email ?: "",
            photoUrl = user.photoUrl?.toString()
        )
    }

    /**
     * Signs into Firebase Authentication via Jetpack Credential Manager (GetSignInWithGoogleOption).
     * Never blocks local offline usage when cancelled or signed out.
     */
    suspend fun signInWithGoogle(activity: Activity): Result<GoogleSignInAccountInfo> {
        return try {
            val webClientIdRes = activity.resources.getIdentifier(
                "default_web_client_id",
                "string",
                activity.packageName
            )
            if (webClientIdRes == 0) {
                return Result.failure(
                    IllegalStateException("Google Sign-In client configuration is unavailable in this build.")
                )
            }
            val serverClientId = activity.getString(webClientIdRes)
            if (serverClientId.isBlank()) {
                return Result.failure(
                    IllegalStateException("Google Sign-In client ID is blank.")
                )
            }

            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val credentialManager = CredentialManager.create(activity)
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(
                    googleIdTokenCredential.idToken,
                    null
                )
                val authResult = withContext(Dispatchers.IO) {
                    FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                }
                val user = authResult.user
                    ?: return Result.failure(IllegalStateException("Firebase sign-in returned no user."))
                Result.success(
                    GoogleSignInAccountInfo(
                        uid = user.uid,
                        displayName = user.displayName ?: googleIdTokenCredential.displayName ?: "Google User",
                        email = user.email ?: googleIdTokenCredential.id,
                        photoUrl = (user.photoUrl ?: googleIdTokenCredential.profilePictureUri)?.toString()
                    )
                )
            } else {
                Result.failure(IllegalStateException("Unsupported credential type returned."))
            }
        } catch (_: GetCredentialCancellationException) {
            Result.failure(IllegalStateException("Google Sign-In cancelled"))
        } catch (e: NoCredentialException) {
            Result.failure(IllegalStateException("No Google account found on device: ${e.message ?: ""}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {}
    }
}
