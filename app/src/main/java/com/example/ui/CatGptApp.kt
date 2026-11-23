package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ai.CatAiService
import com.example.audio.FelineAudioEngine
import com.example.auth.AuthScreen
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOut
import com.example.data.CatRepository
import com.example.data.FirestoreCatStore
import com.example.data.GuestCatStore
import com.example.location.CatLocationClient
import com.example.model.CatProfile
import com.example.model.DeviceRole
import com.example.model.UiState
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.GpsTrackerScreen
import com.example.ui.screens.HabitsScreen
import com.example.ui.screens.RelaxScreen
import com.example.ui.screens.TranslatorScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

sealed class CatRoute(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Chat : CatRoute("chat", "CatGPT", Icons.Rounded.AutoAwesome)
    data object Translator : CatRoute("translator", "Miados", Icons.Rounded.Translate)
    data object Relax : CatRoute("relax", "Música", Icons.Rounded.MusicNote)
    data object Gps : CatRoute("gps", "GPS", Icons.Rounded.LocationOn)
    data object Habits : CatRoute("habits", "Hábitos", Icons.Rounded.EmojiEvents)
}

private val CAT_ROUTES = listOf(
    CatRoute.Chat,
    CatRoute.Translator,
    CatRoute.Relax,
    CatRoute.Gps,
    CatRoute.Habits
)

@Composable
fun CatGptApp() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isGuestMode by rememberSaveable { mutableStateOf(false) }
    var selectedRole by rememberSaveable { mutableStateOf(DeviceRole.CLIENT) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                isGuestMode = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    when {
        user != null -> {
            key(user.uid) {
                CatAppShell(
                    sessionKey = user.uid,
                    userId = user.uid,
                    isGuest = false,
                    initialRole = selectedRole,
                    onRoleUpdated = { selectedRole = it }
                )
            }
        }
        isGuestMode -> {
            key("guest") {
                CatAppShell(
                    sessionKey = "guest",
                    userId = null,
                    isGuest = true,
                    initialRole = selectedRole,
                    onRoleUpdated = { selectedRole = it }
                )
            }
        }
        else -> {
            AuthScreen(
                selectedRole = selectedRole,
                onSelectRole = { selectedRole = it },
                onAuthSuccess = {
                    isGuestMode = false
                    currentUser = Firebase.auth.currentUser
                },
                onContinueAsGuest = {
                    isGuestMode = true
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatAppShell(
    sessionKey: String,
    userId: String?,
    isGuest: Boolean,
    initialRole: DeviceRole,
    onRoleUpdated: (DeviceRole) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: CatGptViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val store = if (userId != null && !isGuest) {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    FirestoreCatStore(CatRepository(db), userId)
                } else {
                    GuestCatStore()
                }
                CatGptViewModel(
                    store = store,
                    aiService = CatAiService(),
                    audioEngine = FelineAudioEngine(),
                    locationClient = CatLocationClient(app),
                    initialRole = initialRole
                )
            }
        }
    )

    val deviceRole by viewModel.deviceRole.collectAsStateWithLifecycle()
    val profileState by viewModel.catProfileState.collectAsStateWithLifecycle()
    val habitsState by viewModel.habitsState.collectAsStateWithLifecycle()
    val logsState by viewModel.habitLogsState.collectAsStateWithLifecycle()
    val translationsState by viewModel.translationsState.collectAsStateWithLifecycle()
    val locationsState by viewModel.locationsState.collectAsStateWithLifecycle()
    val chatMessagesState by viewModel.chatMessagesState.collectAsStateWithLifecycle()

    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val chatBusy by viewModel.chatBusy.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()

    val isRecording by viewModel.isRecordingMeow.collectAsStateWithLifecycle()
    val recordingAmplitude by viewModel.recordingAmplitude.collectAsStateWithLifecycle()
    val translatorBusy by viewModel.translatorBusy.collectAsStateWithLifecycle()
    val translatorError by viewModel.translatorError.collectAsStateWithLifecycle()

    val isRelaxPlaying by viewModel.isRelaxPlaying.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val relaxVolume by viewModel.relaxVolume.collectAsStateWithLifecycle()
    val sleepTimerSeconds by viewModel.sleepTimerSeconds.collectAsStateWithLifecycle()

    val gpsTrackingActive by viewModel.gpsTrackingActive.collectAsStateWithLifecycle()
    val gpsLocating by viewModel.gpsLocating.collectAsStateWithLifecycle()
    val gpsError by viewModel.gpsError.collectAsStateWithLifecycle()
    val coachState by viewModel.habitCoachState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: CatRoute.Chat.route
    var showProfileDialog by remember { mutableStateOf(false) }

    val profile = (profileState as? UiState.Success)?.data
    val catName = profile?.catName?.ifBlank { "Mingau" } ?: "Mingau"

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Pets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CatGPT",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = deviceRole.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (isGuest) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "Convidado",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showProfileDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("edit_cat_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Editar perfil do gato"
                            )
                        }
                        if (isGuest) {
                            IconButton(
                                onClick = {
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {},
                                        onAuthError = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        },
                                        scope = scope,
                                        onAuthCancelled = {}
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("guest_sign_in_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Login,
                                    contentDescription = "Entrar com Google"
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    viewModel.stopGpsTracking()
                                    signOut(credentialManager, scope)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = "Sair da conta"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        CAT_ROUTES.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        CAT_ROUTES.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = CatRoute.Chat.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 780.dp)
                    ) {
                        composable(CatRoute.Chat.route) {
                            ChatScreen(
                                profileState = profileState,
                                messagesState = chatMessagesState,
                                streamingReply = streamingReply,
                                chatBusy = chatBusy,
                                chatError = chatError,
                                onToggleGatones = viewModel::toggleGatonesMode,
                                onSendMessage = viewModel::sendChatMessage,
                                onClearChat = viewModel::clearChatHistory,
                                onDismissError = viewModel::clearChatError
                            )
                        }
                        composable(CatRoute.Translator.route) {
                            TranslatorScreen(
                                translationsState = translationsState,
                                isRecording = isRecording,
                                recordingAmplitude = recordingAmplitude,
                                translatorBusy = translatorBusy,
                                translatorError = translatorError,
                                onRecordMeow = viewModel::recordAndTranslateMeow,
                                onTranslateText = viewModel::translateTextOrPreset,
                                onDismissError = viewModel::clearTranslatorError
                            )
                        }
                        composable(CatRoute.Relax.route) {
                            RelaxScreen(
                                catName = catName,
                                isPlaying = isRelaxPlaying,
                                selectedPreset = selectedPreset,
                                volume = relaxVolume,
                                sleepTimerSeconds = sleepTimerSeconds,
                                onTogglePlay = viewModel::toggleRelaxPlayback,
                                onSelectPreset = viewModel::selectRelaxPreset,
                                onVolumeChange = viewModel::updateRelaxVolume,
                                onSelectSleepTimer = viewModel::setSleepTimerMinutes
                            )
                        }
                        composable(CatRoute.Gps.route) {
                            GpsTrackerScreen(
                                deviceRole = deviceRole,
                                profileState = profileState,
                                locationsState = locationsState,
                                trackingActive = gpsTrackingActive,
                                gpsLocating = gpsLocating,
                                gpsError = gpsError,
                                onSwitchRole = { role ->
                                    viewModel.setDeviceRole(role)
                                    onRoleUpdated(role)
                                },
                                onLocateMeNow = viewModel::locateCurrentPosition,
                                onStartTracking = viewModel::startGpsTracking,
                                onStopTracking = viewModel::stopGpsTracking,
                                onSetHomeSafeZone = viewModel::setCurrentPositionAsHome,
                                onDismissError = viewModel::clearGpsError
                            )
                        }
                        composable(CatRoute.Habits.route) {
                            HabitsScreen(
                                profileState = profileState,
                                habitsState = habitsState,
                                logsState = logsState,
                                coachState = coachState,
                                onRecordHabit = viewModel::recordHabit,
                                onAddHabit = viewModel::addCustomHabit,
                                onDeleteHabit = viewModel::deleteHabit,
                                onRequestCoach = viewModel::requestHabitCoach,
                                onDismissCoach = viewModel::dismissHabitCoach
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        CatProfileEditDialog(
            profile = profile,
            onDismiss = { showProfileDialog = false },
            onSave = { name, breed, gatones ->
                viewModel.updateCatProfile(name, breed, gatones)
                showProfileDialog = false
            }
        )
    }
}

@Composable
private fun CatProfileEditDialog(
    profile: CatProfile?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var catName by remember(profile) { mutableStateOf(profile?.catName ?: "Mingau") }
    var breed by remember(profile) { mutableStateOf(profile?.breed ?: "SRD Caramelo") }
    var gatones by remember(profile) { mutableStateOf(profile?.gatonesEnabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Perfil do Gato") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = catName,
                    onValueChange = { catName = it },
                    label = { Text("Nome do gato") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = breed,
                    onValueChange = { breed = it },
                    label = { Text("Raça ou apelido") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Modo Gatonês padrão", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = gatones, onCheckedChange = { gatones = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(catName, breed, gatones) },
                enabled = catName.isNotBlank()
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
