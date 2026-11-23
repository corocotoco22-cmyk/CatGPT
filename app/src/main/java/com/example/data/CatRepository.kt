package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.CatHabit
import com.example.model.CatProfile
import com.example.model.ChatMessageRecord
import com.example.model.GpsPoint
import com.example.model.HabitCategory
import com.example.model.HabitLog
import com.example.model.MeowTranslation
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Usuário precisa estar autenticado com o Google.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    suspend fun ensureInitialized(userId: String): Result<Unit> {
        val profilePath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Usuário autenticado diferente do esperado." }

            val profileSnap = userDoc(uid).get().await()
            if (!profileSnap.exists()) {
                val initialProfile = mapOf(
                    "userId" to uid,
                    "catName" to "Mingau",
                    "breed" to "SRD Caramelo",
                    "totalScore" to 0L,
                    "gatonesEnabled" to true,
                    "homeLatitude" to 0.0,
                    "homeLongitude" to 0.0,
                    "homeRadiusMeters" to 150.0,
                    "lastLatitude" to 0.0,
                    "lastLongitude" to 0.0,
                    "lastAccuracyMeters" to 0.0,
                    "lastZoneLabel" to "Aguardando sinal GPS",
                    "gpsTrackingActive" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc(uid).set(initialProfile).await()
            }

            val existingHabits = userDoc(uid).collection("habits").limit(1).get().await()
            if (existingHabits.isEmpty) {
                val defaults = listOf(
                    DefaultHabit("Bebeu água na fonte", HabitCategory.HYDRATION, 15L, true),
                    DefaultHabit("Usou o arranhador", HabitCategory.SCRATCHER, 20L, true),
                    DefaultHabit("Usou a caixinha de areia", HabitCategory.LITTER, 25L, true),
                    DefaultHabit("Brincadeira de caça (15 min)", HabitCategory.PLAY, 30L, true),
                    DefaultHabit("Soneca zen sem sustos", HabitCategory.NAP, 10L, true),
                    DefaultHabit("Escovação tranquila", HabitCategory.GROOMING, 20L, true),
                    DefaultHabit("Derrubou objeto da mesa", HabitCategory.MISCHIEF, -10L, false)
                )
                val batch = db.batch()
                defaults.forEach { item ->
                    val ref = userDoc(uid).collection("habits").document()
                    batch.set(
                        ref,
                        mapOf(
                            "userId" to uid,
                            "title" to item.title,
                            "category" to item.category.name,
                            "points" to item.points,
                            "completionCount" to 0L,
                            "streakDays" to 0L,
                            "positiveHabit" to item.positiveHabit,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, profilePath)
            Result.failure(e)
        }
    }

    fun observeCatProfile(userId: String): Flow<CatProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid"
        emitAll(
            userDoc(uid).snapshots()
                .map { it.toObject(CatProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeHabits(userId: String): Flow<List<CatHabit>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/habits"
        emitAll(
            userDoc(uid).collection("habits")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { it.toObjects(CatHabit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeHabitLogs(userId: String): Flow<List<HabitLog>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/habit_logs"
        emitAll(
            userDoc(uid).collection("habit_logs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { it.toObjects(HabitLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeTranslations(userId: String): Flow<List<MeowTranslation>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/translations"
        emitAll(
            userDoc(uid).collection("translations")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { it.toObjects(MeowTranslation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeLocations(userId: String): Flow<List<GpsPoint>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/locations"
        emitAll(
            userDoc(uid).collection("locations")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .snapshots()
                .map { it.toObjects(GpsPoint::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeChatMessages(userId: String): Flow<List<ChatMessageRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/chat_messages"
        emitAll(
            userDoc(uid).collection("chat_messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(50)
                .snapshots()
                .map { it.toObjects(ChatMessageRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun updateCatProfileInfo(
        userId: String,
        catName: String,
        breed: String,
        gatonesEnabled: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val safeName = catName.trim().ifBlank { "Mingau" }.take(40)
            val safeBreed = breed.trim().ifBlank { "SRD" }.take(50)
            userDoc(uid).update(
                mapOf(
                    "catName" to safeName,
                    "breed" to safeBreed,
                    "gatonesEnabled" to gatonesEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordHabitAction(
        userId: String,
        habit: CatHabit,
        currentTotalScore: Long
    ): Result<Long> {
        val path = "users/$userId/habits/${habit.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(habit.id.isNotBlank()) { "Habit ID não pode estar vazio." }

            val nextScore = (currentTotalScore + habit.points).coerceIn(0L, 1_000_000L)
            val nextCount = (habit.completionCount + 1L).coerceIn(0L, 100_000L)
            val nextStreak = if (habit.positiveHabit) {
                (habit.streakDays + 1L).coerceIn(0L, 10_000L)
            } else {
                0L
            }

            val batch = db.batch()
            val habitRef = userDoc(uid).collection("habits").document(habit.id)
            batch.update(
                habitRef,
                mapOf(
                    "completionCount" to nextCount,
                    "streakDays" to nextStreak,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logRef = userDoc(uid).collection("habit_logs").document()
            batch.set(
                logRef,
                mapOf(
                    "userId" to uid,
                    "habitId" to habit.id.take(128),
                    "habitTitle" to habit.title.ifBlank { "Hábito felino" }.take(80),
                    "pointsDelta" to habit.points.coerceIn(-50L, 200L),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userDoc(uid),
                mapOf(
                    "totalScore" to nextScore,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(nextScore)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addCustomHabit(
        userId: String,
        title: String,
        category: HabitCategory,
        points: Long,
        positiveHabit: Boolean
    ): Result<Unit> {
        val path = "users/$userId/habits"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanTitle = title.trim().take(80)
            require(cleanTitle.isNotEmpty()) { "Informe o nome do hábito." }
            val clampedPoints = if (positiveHabit) points.coerceIn(1L, 200L) else (-points.coerceIn(1L, 50L))

            val ref = userDoc(uid).collection("habits").document()
            ref.set(
                mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "category" to category.name,
                    "points" to clampedPoints,
                    "completionCount" to 0L,
                    "streakDays" to 0L,
                    "positiveHabit" to positiveHabit,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteHabit(userId: String, habitId: String): Result<Unit> {
        val path = "users/$userId/habits/$habitId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(habitId.isNotBlank())
            userDoc(uid).collection("habits").document(habitId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveTranslation(
        userId: String,
        inputMode: String,
        meowTranscription: String,
        humanMeaning: String,
        gatonesReply: String,
        detectedMood: String,
        confidencePercent: Long,
        recommendedAction: String
    ): Result<Unit> {
        val path = "users/$userId/translations"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val safeMode = if (inputMode in listOf("AUDIO", "TEXT", "PRESET")) inputMode else "TEXT"
            val ref = userDoc(uid).collection("translations").document()
            ref.set(
                mapOf(
                    "userId" to uid,
                    "inputMode" to safeMode,
                    "meowTranscription" to meowTranscription.trim().ifBlank { "Miau!" }.take(200),
                    "humanMeaning" to humanMeaning.trim().ifBlank { "Pedido de atenção felina." }.take(500),
                    "gatonesReply" to gatonesReply.trim().ifBlank { "Prrr-miau!" }.take(500),
                    "detectedMood" to detectedMood.trim().ifBlank { "Curioso" }.take(60),
                    "confidencePercent" to confidencePercent.coerceIn(0L, 100L),
                    "recommendedAction" to recommendedAction.trim().ifBlank { "Ofereça carinho e água fresca." }.take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordGpsLocation(
        userId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        speedMps: Double,
        insideSafeZone: Boolean,
        zoneLabel: String,
        trackingActive: Boolean
    ): Result<Unit> {
        val path = "users/$userId/locations"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val safeLat = latitude.coerceIn(-90.0, 90.0)
            val safeLon = longitude.coerceIn(-180.0, 180.0)
            val safeAcc = accuracyMeters.coerceIn(0.0, 10000.0)
            val safeSpeed = speedMps.coerceIn(0.0, 200.0)
            val safeZone = zoneLabel.trim().ifBlank { "Localização atual" }.take(100)

            val batch = db.batch()
            val locRef = userDoc(uid).collection("locations").document()
            batch.set(
                locRef,
                mapOf(
                    "userId" to uid,
                    "latitude" to safeLat,
                    "longitude" to safeLon,
                    "accuracyMeters" to safeAcc,
                    "speedMps" to safeSpeed,
                    "insideSafeZone" to insideSafeZone,
                    "zoneLabel" to safeZone,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userDoc(uid),
                mapOf(
                    "lastLatitude" to safeLat,
                    "lastLongitude" to safeLon,
                    "lastAccuracyMeters" to safeAcc,
                    "lastZoneLabel" to safeZone,
                    "gpsTrackingActive" to trackingActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun setHomeSafeZone(
        userId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            userDoc(uid).update(
                mapOf(
                    "homeLatitude" to latitude.coerceIn(-90.0, 90.0),
                    "homeLongitude" to longitude.coerceIn(-180.0, 180.0),
                    "homeRadiusMeters" to radiusMeters.coerceIn(10.0, 5000.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setGpsTrackingActive(
        userId: String,
        active: Boolean,
        lastZoneLabel: String,
        lastLat: Double,
        lastLon: Double,
        lastAcc: Double
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            userDoc(uid).update(
                mapOf(
                    "lastLatitude" to lastLat.coerceIn(-90.0, 90.0),
                    "lastLongitude" to lastLon.coerceIn(-180.0, 180.0),
                    "lastAccuracyMeters" to lastAcc.coerceIn(0.0, 10000.0),
                    "lastZoneLabel" to lastZoneLabel.trim().take(100),
                    "gpsTrackingActive" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveChatMessage(
        userId: String,
        sender: String,
        text: String,
        gatonesMode: Boolean
    ): Result<Unit> {
        val path = "users/$userId/chat_messages"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val safeSender = if (sender == "USER") "USER" else "CATGPT"
            val safeText = text.trim().ifBlank { "Miau!" }.take(4000)
            val ref = userDoc(uid).collection("chat_messages").document()
            ref.set(
                mapOf(
                    "userId" to uid,
                    "sender" to safeSender,
                    "text" to safeText,
                    "gatonesMode" to gatonesMode,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(userId: String): Result<Unit> {
        val path = "users/$userId/chat_messages"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val snap = userDoc(uid).collection("chat_messages").get().await()
            if (!snap.isEmpty) {
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun handleFlowError(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    private data class DefaultHabit(
        val title: String,
        val category: HabitCategory,
        val points: Long,
        val positiveHabit: Boolean
    )
}
