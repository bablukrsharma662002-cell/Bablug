package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AnimationScene
import com.example.model.CharacterTemplate
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StageCraftRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeScenes(userId: String): Flow<List<AnimationScene>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid/scenes"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("scenes")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .map { FirestoreMappers.snapshotToScene(it) }
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCharacterLibrary(userId: String): Flow<List<CharacterTemplate>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid/characters"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("characters")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .map { FirestoreMappers.snapshotToCharacterTemplate(it) }
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveScene(scene: AnimationScene): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/scenes"
        return try {
            val uid = requireUserId()
            val collection = db.collection("users").document(uid).collection("scenes")
            if (scene.id.isBlank()) {
                val docRef = collection.document()
                docPath = docRef.path
                val payload = FirestoreMappers.sceneCreateMap(uid, scene)
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = collection.document(scene.id)
                docPath = docRef.path
                val payload = FirestoreMappers.sceneUpdateMap(scene)
                docRef.update(payload).await()
                Result.success(scene.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (scene.id.isBlank()) OperationType.CREATE else OperationType.UPDATE,
                docPath,
            )
            Result.failure(e)
        }
    }

    suspend fun deleteScene(sceneId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/scenes/$sceneId"
        return try {
            val uid = requireUserId()
            require(sceneId.isNotBlank()) { "Scene ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("scenes").document(sceneId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCharacterTemplate(template: CharacterTemplate): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/characters"
        return try {
            val uid = requireUserId()
            val collection = db.collection("users").document(uid).collection("characters")
            if (template.id.isBlank() || template.id.startsWith("preset_")) {
                val docRef = collection.document()
                docPath = docRef.path
                val payload = FirestoreMappers.characterTemplateCreateMap(uid, template)
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = collection.document(template.id)
                docPath = docRef.path
                val payload = FirestoreMappers.characterTemplateUpdateMap(template)
                docRef.update(payload).await()
                Result.success(template.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (template.id.isBlank() || template.id.startsWith("preset_")) OperationType.CREATE else OperationType.UPDATE,
                docPath,
            )
            Result.failure(e)
        }
    }

    suspend fun deleteCharacterTemplate(characterId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/characters/$characterId"
        return try {
            val uid = requireUserId()
            require(characterId.isNotBlank()) { "Character template ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("characters").document(characterId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
