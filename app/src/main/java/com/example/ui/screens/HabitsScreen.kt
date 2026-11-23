package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CatHabit
import com.example.model.CatProfile
import com.example.model.HabitCategory
import com.example.model.HabitLog
import com.example.model.UiState
import com.example.model.computeCatRank

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitsScreen(
    profileState: UiState<CatProfile?>,
    habitsState: UiState<List<CatHabit>>,
    logsState: UiState<List<HabitLog>>,
    coachState: UiState<String>?,
    onRecordHabit: (CatHabit) -> Unit,
    onAddHabit: (String, HabitCategory, Long, Boolean) -> Unit,
    onDeleteHabit: (String) -> Unit,
    onRequestCoach: () -> Unit,
    onDismissCoach: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    val profile = (profileState as? UiState.Success)?.data
    val catName = profile?.catName?.ifBlank { "Mingau" } ?: "Mingau"
    val totalScore = profile?.totalScore ?: 0L
    val rank = computeCatRank(totalScore)
    val span = (rank.nextThreshold - rank.minScore).coerceAtLeast(1L)
    val progress = ((totalScore - rank.minScore).toFloat() / span.toFloat()).coerceIn(0f, 1f)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Score & Feline Rank Hero Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${rank.badgeEmoji} ${rank.title}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Pontuação felina de $catName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EmojiEvents,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$totalScore pts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Próximo nível: ${rank.nextTitle} (${rank.nextThreshold} pts)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_habit_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Novo Hábito")
                        }
                        OutlinedButton(
                            onClick = onRequestCoach,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("habit_coach_button")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Coach IA")
                        }
                    }
                }
            }
        }

        // AI Coach Card
        item {
            AnimatedVisibility(visible = coachState != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Conselho do CatGPT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            TextButton(onClick = onDismissCoach) {
                                Text("Fechar")
                            }
                        }
                        when (coachState) {
                            is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(26.dp))
                            is UiState.Error -> Text(coachState.message, color = MaterialTheme.colorScheme.error)
                            is UiState.Success -> Text(
                                text = coachState.data,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            null -> Unit
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Hábitos & Travessuras",
                style = MaterialTheme.typography.titleLarge
            )
        }

        when (habitsState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(text = habitsState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                items(habitsState.data, key = { it.id.ifBlank { it.title } }) { habit ->
                    HabitCard(
                        habit = habit,
                        onRecord = { onRecordHabit(habit) },
                        onDelete = { onDeleteHabit(habit.id) }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Últimos Registros de Pontuação",
                style = MaterialTheme.typography.titleLarge
            )
        }

        when (logsState) {
            is UiState.Success -> {
                val logs = logsState.data
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "Toque em +pts ou -pts em qualquer hábito para começar a pontuar!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(logs.take(10), key = { it.id.ifBlank { "${it.habitId}_${it.pointsDelta}" } }) { log ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.habitTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (log.pointsDelta >= 0) "+${log.pointsDelta} pts" else "${log.pointsDelta} pts",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (log.pointsDelta >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    if (showAddDialog) {
        AddHabitDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, category, pts, positive ->
                onAddHabit(title, category, pts, positive)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun HabitCard(
    habit: CatHabit,
    onRecord: () -> Unit,
    onDelete: () -> Unit
) {
    val cat = habit.resolvedCategory
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = cat.emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${habit.completionCount}x",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (habit.streakDays > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${habit.streakDays}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Excluir hábito",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onRecord,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (habit.positiveHabit) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("log_habit_${habit.id}")
            ) {
                val label = if (habit.points >= 0) "+${habit.points}" else "${habit.points}"
                Text(text = "$label pts", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddHabitDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, HabitCategory, Long, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var pointsText by remember { mutableStateOf("20") }
    var positiveHabit by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(HabitCategory.PLAY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Hábito Felino") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Descrição (ex: Aceitou cortar unhas)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Pontos") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (positiveHabit) "Hábito positivo (+)" else "Travessura (-)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = positiveHabit, onCheckedChange = { positiveHabit = it })
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HabitCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text("${cat.emoji} ${cat.labelPt}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pts = pointsText.toLongOrNull() ?: 15L
                    onConfirm(title, selectedCategory, pts, positiveHabit)
                },
                enabled = title.isNotBlank()
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
