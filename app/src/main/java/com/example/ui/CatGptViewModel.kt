package com.example.ui

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.CatAiService
import com.example.audio.FelineAudioEngine
import com.example.data.CatSessionStore
import com.example.location.CatLocationClient
import com.example.location.calculateDistanceMeters
import com.example.model.CatHabit
import com.example.model.CatProfile
import com.example.model.ChatMessageRecord
import com.example.model.DeviceRole
import com.example.model.GpsPoint
import com.example.model.HabitCategory
import com.example.model.HabitLog
import com.example.model.MeowTranslation
import com.example.model.RelaxSoundPreset
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CatGptViewModel(
    private val store: CatSessionStore,
    private val aiService: CatAiService,
    private val audioEngine: FelineAudioEngine,
    private val locationClient: CatLocationClient,
    initialRole: DeviceRole
) : ViewModel() {

    private companion object {
        const val TAG = "CatGptVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }

    val isGuestSession: Boolean get() = store.isGuestSession

    private val _deviceRole = MutableStateFlow(initialRole)
    val deviceRole: StateFlow<DeviceRole> = _deviceRole.asStateFlow()

    val catProfileState: StateFlow<UiState<CatProfile?>> =
        store.observeCatProfile().toUiStateFlow("Falha ao carregar perfil do gato")

    val habitsState: StateFlow<UiState<List<CatHabit>>> =
        store.observeHabits().toUiStateFlow("Falha ao carregar hábitos")

    val habitLogsState: StateFlow<UiState<List<HabitLog>>> =
        store.observeHabitLogs().toUiStateFlow("Falha ao carregar histórico de pontos")

    val translationsState: StateFlow<UiState<List<MeowTranslation>>> =
        store.observeTranslations().toUiStateFlow("Falha ao carregar traduções")

    val locationsState: StateFlow<UiState<List<GpsPoint>>> =
        store.observeLocations().toUiStateFlow("Falha ao carregar histórico GPS")

    val chatMessagesState: StateFlow<UiState<List<ChatMessageRecord>>> =
        store.observeChatMessages().toUiStateFlow("Falha ao carregar mensagens")

    // Chat state
    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    // Translator state
    private val _isRecordingMeow = MutableStateFlow(false)
    val isRecordingMeow: StateFlow<Boolean> = _isRecordingMeow.asStateFlow()

    private val _recordingAmplitude = MutableStateFlow(0f)
    val recordingAmplitude: StateFlow<Float> = _recordingAmplitude.asStateFlow()

    private val _translatorBusy = MutableStateFlow(false)
    val translatorBusy: StateFlow<Boolean> = _translatorBusy.asStateFlow()

    private val _translatorError = MutableStateFlow<String?>(null)
    val translatorError: StateFlow<String?> = _translatorError.asStateFlow()

    // Relax music state
    private val _isRelaxPlaying = MutableStateFlow(false)
    val isRelaxPlaying: StateFlow<Boolean> = _isRelaxPlaying.asStateFlow()

    private val _selectedPreset = MutableStateFlow(RelaxSoundPreset.PURR_THERAPY)
    val selectedPreset: StateFlow<RelaxSoundPreset> = _selectedPreset.asStateFlow()

    private val _relaxVolume = MutableStateFlow(0.75f)
    val relaxVolume: StateFlow<Float> = _relaxVolume.asStateFlow()

    private val _sleepTimerSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerSeconds: StateFlow<Int?> = _sleepTimerSeconds.asStateFlow()

    // GPS state
    private val _gpsTrackingActive = MutableStateFlow(false)
    val gpsTrackingActive: StateFlow<Boolean> = _gpsTrackingActive.asStateFlow()

    private val _gpsLocating = MutableStateFlow(false)
    val gpsLocating: StateFlow<Boolean> = _gpsLocating.asStateFlow()

    private val _gpsError = MutableStateFlow<String?>(null)
    val gpsError: StateFlow<String?> = _gpsError.asStateFlow()

    // Habits & coaching state
    private val _habitCoachState = MutableStateFlow<UiState<String>?>(null)
    val habitCoachState: StateFlow<UiState<String>?> = _habitCoachState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private var gpsJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var recordJob: Job? = null

    init {
        viewModelScope.launch {
            store.ensureInitialized().onFailure { error ->
                _actionMessage.value = error.localizedMessage ?: "Erro ao inicializar dados."
            }
        }
    }

    fun setDeviceRole(role: DeviceRole) {
        _deviceRole.value = role
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun clearChatError() {
        _chatError.value = null
    }

    fun clearTranslatorError() {
        _translatorError.value = null
    }

    fun clearGpsError() {
        _gpsError.value = null
    }

    fun dismissHabitCoach() {
        _habitCoachState.value = null
    }

    fun updateCatProfile(catName: String, breed: String, gatonesEnabled: Boolean) {
        viewModelScope.launch {
            store.updateCatProfileInfo(catName, breed, gatonesEnabled)
                .onSuccess { _actionMessage.value = "Perfil de ${catName.ifBlank { "Mingau" }} atualizado!" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Erro ao salvar perfil." }
        }
    }

    fun toggleGatonesMode(enabled: Boolean) {
        val profile = (catProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            store.updateCatProfileInfo(
                profile.catName.ifBlank { "Mingau" },
                profile.breed.ifBlank { "SRD" },
                enabled
            )
        }
    }

    fun sendChatMessage(messageText: String) {
        val trimmed = messageText.trim()
        if (trimmed.isEmpty() || _chatBusy.value) return

        val profile = (catProfileState.value as? UiState.Success)?.data
        val catName = profile?.catName?.ifBlank { "Mingau" } ?: "Mingau"
        val breed = profile?.breed?.ifBlank { "SRD" } ?: "SRD"
        val gatones = profile?.gatonesEnabled ?: true
        val history = (chatMessagesState.value as? UiState.Success)?.data ?: emptyList()

        viewModelScope.launch {
            _chatBusy.value = true
            _chatError.value = null
            _streamingReply.value = ""

            val saveUserResult = store.saveChatMessage("USER", trimmed, gatones)
            if (saveUserResult.isFailure) {
                _chatBusy.value = false
                _streamingReply.value = null
                _chatError.value = saveUserResult.exceptionOrNull()?.localizedMessage
                    ?: "Erro ao salvar mensagem."
                return@launch
            }

            try {
                val finalBuffer = StringBuilder()
                aiService.streamCatChat(history, trimmed, catName, breed, gatones)
                    .collect { partial ->
                        finalBuffer.clear()
                        finalBuffer.append(partial)
                        _streamingReply.value = partial
                    }

                val finalText = finalBuffer.toString().trim()
                if (finalText.isNotEmpty()) {
                    store.saveChatMessage("CATGPT", finalText, gatones)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "CatGPT chat timed out", e)
                _chatError.value = "CatGPT demorou para responder (60s). Tente novamente."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "CatGPT chat failed", e)
                _chatError.value = e.localizedMessage ?: "Falha na resposta da IA CatGPT."
            } finally {
                _streamingReply.value = null
                _chatBusy.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            store.clearChatHistory()
                .onSuccess { _actionMessage.value = "Conversa limpa!" }
                .onFailure { _chatError.value = it.localizedMessage ?: "Erro ao limpar conversa." }
        }
    }

    fun recordAndTranslateMeow(extraContext: String = "") {
        if (_isRecordingMeow.value || _translatorBusy.value) return
        val catName = (catProfileState.value as? UiState.Success)?.data?.catName?.ifBlank { "Mingau" } ?: "Mingau"

        recordJob = viewModelScope.launch {
            _isRecordingMeow.value = true
            _translatorError.value = null
            try {
                val wavBytes = audioEngine.recordMeowWav(durationMs = 4500L) { amp ->
                    _recordingAmplitude.value = amp
                }
                _isRecordingMeow.value = false
                _translatorBusy.value = true

                val contextDesc = extraContext.ifBlank { "Áudio gravado ao vivo pelo tutor perto do gato." }
                val analysis = aiService.translateMeow(wavBytes, contextDesc, catName)
                store.saveTranslation(
                    inputMode = "AUDIO",
                    meowTranscription = analysis.meowTranscription,
                    humanMeaning = analysis.humanMeaning,
                    gatonesReply = analysis.gatonesReply,
                    detectedMood = analysis.detectedMood,
                    confidencePercent = analysis.confidencePercent,
                    recommendedAction = analysis.recommendedAction
                ).onFailure {
                    _translatorError.value = it.localizedMessage ?: "Erro ao salvar tradução."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Meow audio translation timed out", e)
                _translatorError.value = "Análise de áudio expirou após 60s. Tente novamente."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Meow audio translation failed", e)
                _translatorError.value = e.localizedMessage ?: "Erro ao gravar ou traduzir miado."
            } finally {
                _isRecordingMeow.value = false
                _recordingAmplitude.value = 0f
                _translatorBusy.value = false
            }
        }
    }

    fun translateTextOrPreset(inputText: String, mode: String) {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty() || _translatorBusy.value) return
        val catName = (catProfileState.value as? UiState.Success)?.data?.catName?.ifBlank { "Mingau" } ?: "Mingau"

        viewModelScope.launch {
            _translatorBusy.value = true
            _translatorError.value = null
            try {
                val analysis = aiService.translateMeow(null, trimmed, catName)
                store.saveTranslation(
                    inputMode = mode,
                    meowTranscription = analysis.meowTranscription,
                    humanMeaning = analysis.humanMeaning,
                    gatonesReply = analysis.gatonesReply,
                    detectedMood = analysis.detectedMood,
                    confidencePercent = analysis.confidencePercent,
                    recommendedAction = analysis.recommendedAction
                ).onFailure {
                    _translatorError.value = it.localizedMessage ?: "Erro ao salvar tradução."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Meow text translation timed out", e)
                _translatorError.value = "Tradução expirou após 60s. Tente novamente."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Meow text translation failed", e)
                _translatorError.value = e.localizedMessage ?: "Falha ao traduzir vocalização."
            } finally {
                _translatorBusy.value = false
            }
        }
    }

    // Relax Music Controls
    fun selectRelaxPreset(preset: RelaxSoundPreset) {
        _selectedPreset.value = preset
        if (_isRelaxPlaying.value) {
            audioEngine.startRelaxSound(viewModelScope, preset, _relaxVolume.value)
        }
    }

    fun toggleRelaxPlayback() {
        if (_isRelaxPlaying.value) {
            audioEngine.stopRelaxSound()
            _isRelaxPlaying.value = false
            sleepTimerJob?.cancel()
            _sleepTimerSeconds.value = null
        } else {
            audioEngine.startRelaxSound(viewModelScope, _selectedPreset.value, _relaxVolume.value)
            _isRelaxPlaying.value = true
        }
    }

    fun updateRelaxVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _relaxVolume.value = clamped
        audioEngine.setVolume(clamped)
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _sleepTimerSeconds.value = null
            return
        }
        if (!_isRelaxPlaying.value) {
            toggleRelaxPlayback()
        }
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes * 60
            _sleepTimerSeconds.value = remaining
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _sleepTimerSeconds.value = remaining
            }
            audioEngine.stopRelaxSound()
            _isRelaxPlaying.value = false
            _sleepTimerSeconds.value = null
            _actionMessage.value = "Timer do Modo Relaxante concluído. Bons sonhos felinos!"
        }
    }

    // GPS Tracking Controls
    fun locateCurrentPosition() {
        if (_gpsLocating.value) return
        viewModelScope.launch {
            _gpsLocating.value = true
            _gpsError.value = null
            try {
                val loc = locationClient.fetchFreshCurrentLocation()
                if (loc == null) {
                    _gpsError.value = "Não foi possível obter sua localização GPS atual. Verifique se a localização do aparelho está ligada."
                } else {
                    persistLocationFix(loc, trackingActive = _gpsTrackingActive.value)
                    _actionMessage.value = "Localização atualizada para sua posição GPS!"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to locate current position", e)
                _gpsError.value = e.localizedMessage ?: "Erro ao buscar localização atual."
            } finally {
                _gpsLocating.value = false
            }
        }
    }

    fun startGpsTracking() {
        if (gpsJob?.isActive == true) return
        _gpsError.value = null
        _gpsTrackingActive.value = true

        gpsJob = viewModelScope.launch {
            try {
                locationClient.fetchFreshCurrentLocation()?.let { initialLoc ->
                    persistLocationFix(initialLoc, trackingActive = true)
                }

                locationClient.observeLocationUpdates(5000L).collect { location ->
                    persistLocationFix(location, trackingActive = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "GPS stream failed", e)
                _gpsError.value = e.localizedMessage ?: "Erro no sensor GPS."
                _gpsTrackingActive.value = false
            }
        }
    }

    private suspend fun persistLocationFix(location: Location, trackingActive: Boolean) {
        val profile = (catProfileState.value as? UiState.Success)?.data
        val radius = profile?.homeRadiusMeters ?: 150.0
        val homeUnset = profile == null || (profile.homeLatitude == 0.0 && profile.homeLongitude == 0.0)

        if (homeUnset) {
            store.setHomeSafeZone(location.latitude, location.longitude, radius)
        }

        val homeLat = if (homeUnset) location.latitude else (profile?.homeLatitude ?: location.latitude)
        val homeLon = if (homeUnset) location.longitude else (profile?.homeLongitude ?: location.longitude)
        val dist = calculateDistanceMeters(location.latitude, location.longitude, homeLat, homeLon)
        val inside = dist <= radius
        val rolePrefix = if (_deviceRole.value == DeviceRole.CLIENT) "Cliente GPS" else "Posição Atual"
        val zoneLabel = if (inside) {
            "$rolePrefix • Zona Segura (${dist.toInt()}m)"
        } else {
            "$rolePrefix • Fora da Zona (${dist.toInt()}m)"
        }

        store.recordGpsLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.toDouble(),
            speedMps = location.speed.toDouble(),
            insideSafeZone = inside,
            zoneLabel = zoneLabel,
            trackingActive = trackingActive
        ).onFailure { err ->
            _gpsError.value = err.localizedMessage ?: "Erro ao sincronizar ponto GPS."
        }
    }

    fun stopGpsTracking() {
        gpsJob?.cancel()
        gpsJob = null
        _gpsTrackingActive.value = false
        val profile = (catProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            store.setGpsTrackingActive(
                active = false,
                lastZoneLabel = profile.lastZoneLabel.ifBlank { "Rastreamento pausado" },
                lastLat = profile.lastLatitude,
                lastLon = profile.lastLongitude,
                lastAcc = profile.lastAccuracyMeters
            )
        }
    }

    fun setCurrentPositionAsHome(radiusMeters: Double) {
        viewModelScope.launch {
            val live = locationClient.fetchFreshCurrentLocation()
            val profile = (catProfileState.value as? UiState.Success)?.data
            val lat = live?.latitude
                ?: profile?.lastLatitude?.takeIf { it != 0.0 }
            val lon = live?.longitude
                ?: profile?.lastLongitude?.takeIf { it != 0.0 }

            if (lat == null || lon == null) {
                _gpsError.value = "Ative o GPS para definir sua localização atual como Casa."
                return@launch
            }

            store.setHomeSafeZone(lat, lon, radiusMeters)
                .onSuccess {
                    if (live != null) {
                        persistLocationFix(live, trackingActive = _gpsTrackingActive.value)
                    }
                    _actionMessage.value = "Zona Segura da Casa definida na sua localização atual (raio ${radiusMeters.toInt()}m)!"
                }
                .onFailure { _gpsError.value = it.localizedMessage ?: "Erro ao salvar Zona Segura." }
        }
    }

    // Habits & Scoring
    fun recordHabit(habit: CatHabit) {
        val currentScore = (catProfileState.value as? UiState.Success)?.data?.totalScore ?: 0L
        viewModelScope.launch {
            store.recordHabitAction(habit, currentScore)
                .onSuccess {
                    val sign = if (habit.points >= 0) "+${habit.points}" else "${habit.points}"
                    _actionMessage.value = "${habit.resolvedCategory.emoji} ${habit.title}: $sign pts!"
                }
                .onFailure {
                    _actionMessage.value = it.localizedMessage ?: "Erro ao registrar hábito."
                }
        }
    }

    fun addCustomHabit(title: String, category: HabitCategory, points: Long, positiveHabit: Boolean) {
        viewModelScope.launch {
            store.addCustomHabit(title, category, points, positiveHabit)
                .onSuccess { _actionMessage.value = "Novo hábito felino adicionado!" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Erro ao criar hábito." }
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            store.deleteHabit(habitId)
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Erro ao remover hábito." }
        }
    }

    fun requestHabitCoach() {
        val profile = (catProfileState.value as? UiState.Success)?.data
        val habits = (habitsState.value as? UiState.Success)?.data ?: emptyList()
        val catName = profile?.catName?.ifBlank { "Mingau" } ?: "Mingau"
        val score = profile?.totalScore ?: 0L
        val gatones = profile?.gatonesEnabled ?: true

        viewModelScope.launch {
            _habitCoachState.value = UiState.Loading
            try {
                val advice = aiService.generateHabitCoaching(catName, score, habits, gatones)
                _habitCoachState.value = UiState.Success(advice)
            } catch (e: TimeoutCancellationException) {
                _habitCoachState.value = UiState.Error("O coach felino demorou para responder.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Habit coach failed", e)
                _habitCoachState.value = UiState.Error(e.localizedMessage ?: "Erro ao consultar IA de hábitos.")
            }
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackMessage: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackMessage, rootError)
                emit(UiState.Error(rootError.localizedMessage ?: fallbackMessage))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    override fun onCleared() {
        super.onCleared()
        audioEngine.stopRelaxSound()
        gpsJob?.cancel()
        sleepTimerJob?.cancel()
        recordJob?.cancel()
    }
}
