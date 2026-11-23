package com.example.data

import com.example.model.CatHabit
import com.example.model.CatProfile
import com.example.model.ChatMessageRecord
import com.example.model.GpsPoint
import com.example.model.HabitCategory
import com.example.model.HabitLog
import com.example.model.MeowTranslation
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

interface CatSessionStore {
    val isGuestSession: Boolean
    suspend fun ensureInitialized(): Result<Unit>
    fun observeCatProfile(): Flow<CatProfile?>
    fun observeHabits(): Flow<List<CatHabit>>
    fun observeHabitLogs(): Flow<List<HabitLog>>
    fun observeTranslations(): Flow<List<MeowTranslation>>
    fun observeLocations(): Flow<List<GpsPoint>>
    fun observeChatMessages(): Flow<List<ChatMessageRecord>>

    suspend fun updateCatProfileInfo(
        catName: String,
        breed: String,
        gatonesEnabled: Boolean
    ): Result<Unit>

    suspend fun recordHabitAction(
        habit: CatHabit,
        currentTotalScore: Long
    ): Result<Long>

    suspend fun addCustomHabit(
        title: String,
        category: HabitCategory,
        points: Long,
        positiveHabit: Boolean
    ): Result<Unit>

    suspend fun deleteHabit(habitId: String): Result<Unit>

    suspend fun saveTranslation(
        inputMode: String,
        meowTranscription: String,
        humanMeaning: String,
        gatonesReply: String,
        detectedMood: String,
        confidencePercent: Long,
        recommendedAction: String
    ): Result<Unit>

    suspend fun recordGpsLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        speedMps: Double,
        insideSafeZone: Boolean,
        zoneLabel: String,
        trackingActive: Boolean
    ): Result<Unit>

    suspend fun setHomeSafeZone(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): Result<Unit>

    suspend fun setGpsTrackingActive(
        active: Boolean,
        lastZoneLabel: String,
        lastLat: Double,
        lastLon: Double,
        lastAcc: Double
    ): Result<Unit>

    suspend fun saveChatMessage(
        sender: String,
        text: String,
        gatonesMode: Boolean
    ): Result<Unit>

    suspend fun clearChatHistory(): Result<Unit>
}

class FirestoreCatStore(
    private val repository: CatRepository,
    private val userId: String
) : CatSessionStore {

    override val isGuestSession: Boolean = false

    override suspend fun ensureInitialized(): Result<Unit> =
        repository.ensureInitialized(userId)

    override fun observeCatProfile(): Flow<CatProfile?> =
        repository.observeCatProfile(userId)

    override fun observeHabits(): Flow<List<CatHabit>> =
        repository.observeHabits(userId)

    override fun observeHabitLogs(): Flow<List<HabitLog>> =
        repository.observeHabitLogs(userId)

    override fun observeTranslations(): Flow<List<MeowTranslation>> =
        repository.observeTranslations(userId)

    override fun observeLocations(): Flow<List<GpsPoint>> =
        repository.observeLocations(userId)

    override fun observeChatMessages(): Flow<List<ChatMessageRecord>> =
        repository.observeChatMessages(userId)

    override suspend fun updateCatProfileInfo(
        catName: String,
        breed: String,
        gatonesEnabled: Boolean
    ): Result<Unit> =
        repository.updateCatProfileInfo(userId, catName, breed, gatonesEnabled)

    override suspend fun recordHabitAction(
        habit: CatHabit,
        currentTotalScore: Long
    ): Result<Long> =
        repository.recordHabitAction(userId, habit, currentTotalScore)

    override suspend fun addCustomHabit(
        title: String,
        category: HabitCategory,
        points: Long,
        positiveHabit: Boolean
    ): Result<Unit> =
        repository.addCustomHabit(userId, title, category, points, positiveHabit)

    override suspend fun deleteHabit(habitId: String): Result<Unit> =
        repository.deleteHabit(userId, habitId)

    override suspend fun saveTranslation(
        inputMode: String,
        meowTranscription: String,
        humanMeaning: String,
        gatonesReply: String,
        detectedMood: String,
        confidencePercent: Long,
        recommendedAction: String
    ): Result<Unit> =
        repository.saveTranslation(
            userId = userId,
            inputMode = inputMode,
            meowTranscription = meowTranscription,
            humanMeaning = humanMeaning,
            gatonesReply = gatonesReply,
            detectedMood = detectedMood,
            confidencePercent = confidencePercent,
            recommendedAction = recommendedAction
        )

    override suspend fun recordGpsLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        speedMps: Double,
        insideSafeZone: Boolean,
        zoneLabel: String,
        trackingActive: Boolean
    ): Result<Unit> =
        repository.recordGpsLocation(
            userId = userId,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            speedMps = speedMps,
            insideSafeZone = insideSafeZone,
            zoneLabel = zoneLabel,
            trackingActive = trackingActive
        )

    override suspend fun setHomeSafeZone(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): Result<Unit> =
        repository.setHomeSafeZone(userId, latitude, longitude, radiusMeters)

    override suspend fun setGpsTrackingActive(
        active: Boolean,
        lastZoneLabel: String,
        lastLat: Double,
        lastLon: Double,
        lastAcc: Double
    ): Result<Unit> =
        repository.setGpsTrackingActive(userId, active, lastZoneLabel, lastLat, lastLon, lastAcc)

    override suspend fun saveChatMessage(
        sender: String,
        text: String,
        gatonesMode: Boolean
    ): Result<Unit> =
        repository.saveChatMessage(userId, sender, text, gatonesMode)

    override suspend fun clearChatHistory(): Result<Unit> =
        repository.clearChatHistory(userId)
}

/**
 * Local in-memory store for Guest ("Convidado") mode.
 * Keeps session state on-device without querying Firestore or requiring anonymous auth.
 */
class GuestCatStore : CatSessionStore {

    override val isGuestSession: Boolean = true

    private val guestUid = "guest_local"

    private val profileFlow = MutableStateFlow<CatProfile?>(
        CatProfile(
            id = guestUid,
            userId = guestUid,
            catName = "Mingau",
            breed = "SRD Caramelo",
            totalScore = 0L,
            gatonesEnabled = true,
            homeLatitude = 0.0,
            homeLongitude = 0.0,
            homeRadiusMeters = 150.0,
            lastLatitude = 0.0,
            lastLongitude = 0.0,
            lastAccuracyMeters = 0.0,
            lastZoneLabel = "Aguardando sinal GPS",
            gpsTrackingActive = false,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    )

    private val habitsFlow = MutableStateFlow(
        listOf(
            CatHabit("guest_h1", guestUid, "Bebeu água na fonte", HabitCategory.HYDRATION.name, 15L, 0L, 0L, true, Timestamp.now(), Timestamp.now()),
            CatHabit("guest_h2", guestUid, "Usou o arranhador", HabitCategory.SCRATCHER.name, 20L, 0L, 0L, true, Timestamp.now(), Timestamp.now()),
            CatHabit("guest_h3", guestUid, "Usou a caixinha de areia", HabitCategory.LITTER.name, 25L, 0L, 0L, true, Timestamp.now(), Timestamp.now()),
            CatHabit("guest_h4", guestUid, "Brincadeira de caça (15 min)", HabitCategory.PLAY.name, 30L, 0L, 0L, true, Timestamp.now(), Timestamp.now()),
            CatHabit("guest_h5", guestUid, "Soneca zen sem sustos", HabitCategory.NAP.name, 10L, 0L, 0L, true, Timestamp.now(), Timestamp.now()),
            CatHabit("guest_h6", guestUid, "Escovação tranquila", HabitCategory.GROOMING.name, 20L, 0L, 0L, true, Timestamp.now(), Timestamp.now()),
            CatHabit("guest_h7", guestUid, "Derrubou objeto da mesa", HabitCategory.MISCHIEF.name, -10L, 0L, 0L, false, Timestamp.now(), Timestamp.now())
        )
    )

    private val logsFlow = MutableStateFlow<List<HabitLog>>(emptyList())
    private val translationsFlow = MutableStateFlow<List<MeowTranslation>>(emptyList())
    private val locationsFlow = MutableStateFlow<List<GpsPoint>>(emptyList())
    private val chatMessagesFlow = MutableStateFlow<List<ChatMessageRecord>>(emptyList())

    override suspend fun ensureInitialized(): Result<Unit> = Result.success(Unit)

    override fun observeCatProfile(): Flow<CatProfile?> = profileFlow.asStateFlow()
    override fun observeHabits(): Flow<List<CatHabit>> = habitsFlow.asStateFlow()
    override fun observeHabitLogs(): Flow<List<HabitLog>> = logsFlow.asStateFlow()
    override fun observeTranslations(): Flow<List<MeowTranslation>> = translationsFlow.asStateFlow()
    override fun observeLocations(): Flow<List<GpsPoint>> = locationsFlow.asStateFlow()
    override fun observeChatMessages(): Flow<List<ChatMessageRecord>> = chatMessagesFlow.asStateFlow()

    override suspend fun updateCatProfileInfo(
        catName: String,
        breed: String,
        gatonesEnabled: Boolean
    ): Result<Unit> {
        val safeName = catName.trim().ifBlank { "Mingau" }.take(40)
        val safeBreed = breed.trim().ifBlank { "SRD" }.take(50)
        profileFlow.update { current ->
            current?.copy(
                catName = safeName,
                breed = safeBreed,
                gatonesEnabled = gatonesEnabled,
                updatedAt = Timestamp.now()
            )
        }
        return Result.success(Unit)
    }

    override suspend fun recordHabitAction(
        habit: CatHabit,
        currentTotalScore: Long
    ): Result<Long> {
        val nextScore = (currentTotalScore + habit.points).coerceIn(0L, 1_000_000L)
        habitsFlow.update { list ->
            list.map { item ->
                if (item.id == habit.id) {
                    item.copy(
                        completionCount = (item.completionCount + 1L).coerceIn(0L, 100_000L),
                        streakDays = if (item.positiveHabit) (item.streakDays + 1L).coerceIn(0L, 10_000L) else 0L,
                        updatedAt = Timestamp.now()
                    )
                } else {
                    item
                }
            }
        }
        logsFlow.update { list ->
            val entry = HabitLog(
                id = UUID.randomUUID().toString(),
                userId = guestUid,
                habitId = habit.id,
                habitTitle = habit.title.ifBlank { "Hábito felino" }.take(80),
                pointsDelta = habit.points.coerceIn(-50L, 200L),
                createdAt = Timestamp.now()
            )
            (listOf(entry) + list).take(25)
        }
        profileFlow.update { current ->
            current?.copy(totalScore = nextScore, updatedAt = Timestamp.now())
        }
        return Result.success(nextScore)
    }

    override suspend fun addCustomHabit(
        title: String,
        category: HabitCategory,
        points: Long,
        positiveHabit: Boolean
    ): Result<Unit> {
        val cleanTitle = title.trim().take(80)
        if (cleanTitle.isEmpty()) return Result.failure(IllegalArgumentException("Informe o nome do hábito."))
        val clampedPoints = if (positiveHabit) points.coerceIn(1L, 200L) else (-points.coerceIn(1L, 50L))
        val newHabit = CatHabit(
            id = UUID.randomUUID().toString(),
            userId = guestUid,
            title = cleanTitle,
            category = category.name,
            points = clampedPoints,
            completionCount = 0L,
            streakDays = 0L,
            positiveHabit = positiveHabit,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        habitsFlow.update { it + newHabit }
        return Result.success(Unit)
    }

    override suspend fun deleteHabit(habitId: String): Result<Unit> {
        habitsFlow.update { list -> list.filterNot { it.id == habitId } }
        return Result.success(Unit)
    }

    override suspend fun saveTranslation(
        inputMode: String,
        meowTranscription: String,
        humanMeaning: String,
        gatonesReply: String,
        detectedMood: String,
        confidencePercent: Long,
        recommendedAction: String
    ): Result<Unit> {
        val item = MeowTranslation(
            id = UUID.randomUUID().toString(),
            userId = guestUid,
            inputMode = inputMode,
            meowTranscription = meowTranscription.trim().ifBlank { "Miau!" }.take(200),
            humanMeaning = humanMeaning.trim().ifBlank { "Pedido de atenção felina." }.take(500),
            gatonesReply = gatonesReply.trim().ifBlank { "Prrr-miau!" }.take(500),
            detectedMood = detectedMood.trim().ifBlank { "Curioso" }.take(60),
            confidencePercent = confidencePercent.coerceIn(0L, 100L),
            recommendedAction = recommendedAction.trim().ifBlank { "Ofereça carinho e água fresca." }.take(300),
            createdAt = Timestamp.now()
        )
        translationsFlow.update { (listOf(item) + it).take(20) }
        return Result.success(Unit)
    }

    override suspend fun recordGpsLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        speedMps: Double,
        insideSafeZone: Boolean,
        zoneLabel: String,
        trackingActive: Boolean
    ): Result<Unit> {
        val safeLat = latitude.coerceIn(-90.0, 90.0)
        val safeLon = longitude.coerceIn(-180.0, 180.0)
        val safeAcc = accuracyMeters.coerceIn(0.0, 10000.0)
        val safeSpeed = speedMps.coerceIn(0.0, 200.0)
        val safeZone = zoneLabel.trim().ifBlank { "Localização atual" }.take(100)

        val pt = GpsPoint(
            id = UUID.randomUUID().toString(),
            userId = guestUid,
            latitude = safeLat,
            longitude = safeLon,
            accuracyMeters = safeAcc,
            speedMps = safeSpeed,
            insideSafeZone = insideSafeZone,
            zoneLabel = safeZone,
            createdAt = Timestamp.now()
        )
        locationsFlow.update { (listOf(pt) + it).take(30) }
        profileFlow.update { current ->
            current?.copy(
                lastLatitude = safeLat,
                lastLongitude = safeLon,
                lastAccuracyMeters = safeAcc,
                lastZoneLabel = safeZone,
                gpsTrackingActive = trackingActive,
                updatedAt = Timestamp.now()
            )
        }
        return Result.success(Unit)
    }

    override suspend fun setHomeSafeZone(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): Result<Unit> {
        profileFlow.update { current ->
            current?.copy(
                homeLatitude = latitude.coerceIn(-90.0, 90.0),
                homeLongitude = longitude.coerceIn(-180.0, 180.0),
                homeRadiusMeters = radiusMeters.coerceIn(10.0, 5000.0),
                updatedAt = Timestamp.now()
            )
        }
        return Result.success(Unit)
    }

    override suspend fun setGpsTrackingActive(
        active: Boolean,
        lastZoneLabel: String,
        lastLat: Double,
        lastLon: Double,
        lastAcc: Double
    ): Result<Unit> {
        profileFlow.update { current ->
            current?.copy(
                lastLatitude = lastLat.coerceIn(-90.0, 90.0),
                lastLongitude = lastLon.coerceIn(-180.0, 180.0),
                lastAccuracyMeters = lastAcc.coerceIn(0.0, 10000.0),
                lastZoneLabel = lastZoneLabel.trim().take(100),
                gpsTrackingActive = active,
                updatedAt = Timestamp.now()
            )
        }
        return Result.success(Unit)
    }

    override suspend fun saveChatMessage(
        sender: String,
        text: String,
        gatonesMode: Boolean
    ): Result<Unit> {
        val msg = ChatMessageRecord(
            id = UUID.randomUUID().toString(),
            userId = guestUid,
            sender = if (sender == "USER") "USER" else "CATGPT",
            text = text.trim().ifBlank { "Miau!" }.take(4000),
            gatonesMode = gatonesMode,
            createdAt = Timestamp.now()
        )
        chatMessagesFlow.update { (it + msg).takeLast(50) }
        return Result.success(Unit)
    }

    override suspend fun clearChatHistory(): Result<Unit> {
        chatMessagesFlow.value = emptyList()
        return Result.success(Unit)
    }
}
