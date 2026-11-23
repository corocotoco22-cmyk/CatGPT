package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.MeowTranslation
import com.example.model.UiState

private val MEOW_PRESETS = listOf(
    "Miado curto e agudo olhando para você",
    "Miado longo e insistente perto do pote",
    "Ronronar alto com piscadas lentas",
    "Trinado rápido (Mrrrp!) na porta",
    "Tagarelar (ekekek) olhando pela janela"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslatorScreen(
    translationsState: UiState<List<MeowTranslation>>,
    isRecording: Boolean,
    recordingAmplitude: Float,
    translatorBusy: Boolean,
    translatorError: String?,
    onRecordMeow: (String) -> Unit,
    onTranslateText: (String, String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var phoneticInput by rememberSaveable { mutableStateOf("") }
    var permissionDeniedNote by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedNote = false
            onRecordMeow(phoneticInput)
        } else {
            permissionDeniedNote = true
        }
    }

    val animatedAmp by animateFloatAsState(targetValue = recordingAmplitude, label = "amp")

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Tradutor de Miados & Gatonês",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Grave um miado real pelo microfone ou digite/escolha o som que seu gato fez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live audio waveform visualizer
                    MeowWaveformBar(
                        amplitude = if (isRecording) animatedAmp else 0.12f,
                        active = isRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !translatorBusy && !isRecording) {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    permissionDeniedNote = false
                                    onRecordMeow(phoneticInput)
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            .testTag("record_meow_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (translatorBusy) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(30.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isRecording) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                                    contentDescription = "Gravar miado",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            isRecording -> "Ouvindo seu gato... (4,5s)"
                            translatorBusy -> "Decifrando gatonês com IA..."
                            else -> "Toque para gravar o miado"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (permissionDeniedNote) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Permissão de microfone negada. Você ainda pode usar o tradutor por texto ou atalhos abaixo!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = phoneticInput,
                            onValueChange = { phoneticInput = it },
                            label = { Text("Ou descreva o miado (ex: Miaaau-prrr!)") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("meow_text_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val text = phoneticInput
                                phoneticInput = ""
                                onTranslateText(text, "TEXT")
                            },
                            enabled = phoneticInput.isNotBlank() && !translatorBusy && !isRecording,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("translate_meow_button")
                        ) {
                            Icon(Icons.Rounded.Translate, contentDescription = "Traduzir")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MEOW_PRESETS.forEach { preset ->
                            AssistChip(
                                onClick = {
                                    if (!translatorBusy && !isRecording) {
                                        onTranslateText(preset, "PRESET")
                                    }
                                },
                                label = { Text(preset) }
                            )
                        }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = translatorError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismissError() }
                ) {
                    Text(
                        text = translatorError ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        item {
            Text(
                text = "Histórico de Traduções",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        when (translationsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(
                        text = translationsState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is UiState.Success -> {
                if (translationsState.data.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Pets,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Nenhuma tradução ainda. Grave um miado ou escolha um dos sons acima!",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else {
                    items(translationsState.data, key = { it.id.ifBlank { it.meowTranscription + it.humanMeaning } }) { item ->
                        TranslationHistoryCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeowWaveformBar(
    amplitude: Float,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Canvas(modifier = modifier) {
        val barCount = 24
        val spacing = size.width / barCount
        val barWidth = spacing * 0.55f
        for (i in 0 until barCount) {
            val waveFactor = kotlin.math.sin((i / barCount.toFloat()) * Math.PI).toFloat()
            val heightFraction = (0.2f + amplitude * waveFactor * 0.8f).coerceIn(0.15f, 1f)
            val barHeight = size.height * heightFraction
            val x = i * spacing + (spacing - barWidth) / 2f
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun TranslationHistoryCard(item: MeowTranslation) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "${item.detectedMood} • ${item.confidencePercent}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = item.inputMode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "“${item.meowTranscription}”",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Humano: ${item.humanMeaning}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Resposta em Gatonês: ${item.gatonesReply}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dica: ${item.recommendedAction}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
