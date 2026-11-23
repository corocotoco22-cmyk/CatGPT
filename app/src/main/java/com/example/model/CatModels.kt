package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class DeviceRole(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    SERVER(
        title = "Servidor",
        subtitle = "Central do Dono • Recebe GPS em tempo real e monitora o gato",
        badge = "Servidor"
    ),
    CLIENT(
        title = "Cliente",
        subtitle = "Dispositivo do Gato • Envia sua localização GPS atual ao vivo",
        badge = "Cliente"
    )
}

enum class HabitCategory(val labelPt: String, val emoji: String) {
    HYDRATION("Hidratação", "💧"),
    SCRATCHER("Arranhador", "🪵"),
    LITTER("Caixinha", "🧼"),
    PLAY("Caça & Brincadeira", "🪶"),
    NAP("Soneca Zen", "😴"),
    GROOMING("Escovação", "✨"),
    MISCHIEF("Travessura", "🐾")
}

fun safeHabitCategory(raw: String?): HabitCategory {
    return enumValues<HabitCategory>().firstOrNull { it.name == raw } ?: HabitCategory.PLAY
}

@Keep
@IgnoreExtraProperties
data class CatProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val catName: String = "Mingau",
    val breed: String = "SRD Caramelo",
    val totalScore: Long = 0L,
    val gatonesEnabled: Boolean = true,
    val homeLatitude: Double = 0.0,
    val homeLongitude: Double = 0.0,
    val homeRadiusMeters: Double = 150.0,
    val lastLatitude: Double = 0.0,
    val lastLongitude: Double = 0.0,
    val lastAccuracyMeters: Double = 0.0,
    val lastZoneLabel: String = "Aguardando sinal GPS",
    val gpsTrackingActive: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CatHabit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = HabitCategory.PLAY.name,
    val points: Long = 10L,
    val completionCount: Long = 0L,
    val streakDays: Long = 0L,
    val positiveHabit: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedCategory: HabitCategory
        get() = safeHabitCategory(category)
}

@Keep
@IgnoreExtraProperties
data class HabitLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val habitId: String = "",
    val habitTitle: String = "",
    val pointsDelta: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MeowTranslation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val inputMode: String = "TEXT",
    val meowTranscription: String = "",
    val humanMeaning: String = "",
    val gatonesReply: String = "",
    val detectedMood: String = "",
    val confidencePercent: Long = 85L,
    val recommendedAction: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GpsPoint(
    @DocumentId val id: String = "",
    val userId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val speedMps: Double = 0.0,
    val insideSafeZone: Boolean = true,
    val zoneLabel: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessageRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sender: String = "USER",
    val text: String = "",
    val gatonesMode: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class RelaxSoundPreset(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    PURR_THERAPY(
        title = "Ronronar 28 Hz",
        subtitle = "Frequência terapêutica que acalma gatos ansiosos",
        badge = "28 Hz"
    ),
    FELINE_HARP(
        title = "Harpa Felina",
        subtitle = "Acordes agudos suaves inspirados na audição dos gatos",
        badge = "Pentatônica"
    ),
    WINDOW_RAIN(
        title = "Chuva na Janela",
        subtitle = "Ruído rosa suave para sonecas profundas no sofá",
        badge = "Ambiente"
    ),
    SOLAR_NAP(
        title = "Soneca ao Sol",
        subtitle = "Harmônicos mornos para relaxamento pós-brincadeira",
        badge = "Zen"
    )
}

data class CatRank(
    val title: String,
    val nextTitle: String,
    val minScore: Long,
    val nextThreshold: Long,
    val badgeEmoji: String
)

fun computeCatRank(score: Long): CatRank = when {
    score < 100 -> CatRank("Gatinho Aprendiz", "Caçador de Laser", 0, 100, "🐱")
    score < 300 -> CatRank("Caçador de Laser", "Mestre do Ronrom", 100, 300, "⚡")
    score < 600 -> CatRank("Mestre do Ronrom", "Guardião da Caixa", 300, 600, "👑")
    score < 1000 -> CatRank("Guardião da Caixa", "Faraó Felino", 600, 1000, "🏺")
    else -> CatRank("Faraó Felino", "Lenda Suprema", 1000, 2000, "🌟")
}
