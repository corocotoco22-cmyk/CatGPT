package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.location.calculateDistanceMeters
import com.example.model.CatProfile
import com.example.model.DeviceRole
import com.example.model.GpsPoint
import com.example.model.UiState
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.cos

@Composable
fun GpsTrackerScreen(
    deviceRole: DeviceRole,
    profileState: UiState<CatProfile?>,
    locationsState: UiState<List<GpsPoint>>,
    trackingActive: Boolean,
    gpsLocating: Boolean,
    gpsError: String?,
    onSwitchRole: (DeviceRole) -> Unit,
    onLocateMeNow: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onSetHomeSafeZone: (Double) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    var pendingActionAfterGrant by remember { mutableStateOf<String?>(null) }

    val profile = (profileState as? UiState.Success)?.data
    val catName = profile?.catName?.ifBlank { "Mingau" } ?: "Mingau"
    val homeLat = profile?.homeLatitude ?: 0.0
    val homeLon = profile?.homeLongitude ?: 0.0
    val currentLat = profile?.lastLatitude ?: 0.0
    val currentLon = profile?.lastLongitude ?: 0.0
    val hasValidCoords = !(currentLat == 0.0 && currentLon == 0.0)
    val accuracy = profile?.lastAccuracyMeters ?: 0.0
    val zoneLabel = profile?.lastZoneLabel ?: "Aguardando sinal GPS"
    val initialRadius = (profile?.homeRadiusMeters ?: 150.0).toFloat()
    var sliderRadiusMeters by remember(initialRadius) { mutableFloatStateOf(initialRadius) }

    val distanceFromHome = if (hasValidCoords && !(homeLat == 0.0 && homeLon == 0.0)) {
        calculateDistanceMeters(currentLat, currentLon, homeLat, homeLon)
    } else {
        0.0
    }
    val insideSafeZone = distanceFromHome <= sliderRadiusMeters

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            permissionDenied = false
            when (pendingActionAfterGrant) {
                "locate" -> onLocateMeNow()
                "home" -> onSetHomeSafeZone(sliderRadiusMeters.toDouble())
                else -> onStartTracking()
            }
        } else {
            permissionDenied = true
        }
        pendingActionAfterGrant = null
    }

    // Auto-center on the user's real current GPS position when permission is already granted
    LaunchedEffect(profile != null, hasValidCoords) {
        if (profile != null && !hasValidCoords && hasLocationPermission()) {
            if (deviceRole == DeviceRole.CLIENT) {
                onStartTracking()
            } else {
                onLocateMeNow()
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "gps_pulse")
    val radarPulse by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar"
    )

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("pt-BR")) }
    val trailPoints = (locationsState as? UiState.Success)?.data ?: emptyList()

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
                Column(modifier = Modifier.padding(16.dp)) {
                    // Active Role Switcher (Servidor vs Cliente)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = deviceRole == DeviceRole.SERVER,
                            onClick = { onSwitchRole(DeviceRole.SERVER) },
                            label = { Text("Servidor (Dono)") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = deviceRole == DeviceRole.CLIENT,
                            onClick = { onSwitchRole(DeviceRole.CLIENT) },
                            label = { Text("Cliente (Gato)") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Smartphone, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Rastreador GPS • $catName",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = if (hasValidCoords) zoneLabel else "Toque em Minha Localização para centralizar no seu GPS",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (insideSafeZone) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            color = if (trackingActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (trackingActive) "AO VIVO" else deviceRole.badge.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (trackingActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Interactive Radar & GPS Trail Canvas
                    Box(modifier = Modifier.fillMaxWidth()) {
                        CatGpsRadarCanvas(
                            homeLat = homeLat,
                            homeLon = homeLon,
                            currentLat = currentLat,
                            currentLon = currentLon,
                            hasValidCoords = hasValidCoords,
                            radiusMeters = sliderRadiusMeters.toDouble(),
                            trail = trailPoints,
                            trackingActive = trackingActive,
                            pulseProgress = radarPulse,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                        if (!hasValidCoords) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Sem coordenadas ainda.\nToque em \"Minha Localização Atual\" abaixo.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryChip(
                            "Latitude",
                            if (hasValidCoords) "%.5f".format(currentLat) else "--",
                            Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TelemetryChip(
                            "Longitude",
                            if (hasValidCoords) "%.5f".format(currentLon) else "--",
                            Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TelemetryChip(
                            "Precisão",
                            if (hasValidCoords) "±${accuracy.toInt()}m" else "--",
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Locate Me Now (Real Current GPS Location)
                    OutlinedButton(
                        onClick = {
                            if (hasLocationPermission()) {
                                permissionDenied = false
                                onLocateMeNow()
                            } else {
                                pendingActionAfterGrant = "locate"
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        enabled = !gpsLocating,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("locate_me_button")
                    ) {
                        if (gpsLocating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Obtendo localização GPS atual...")
                        } else {
                            Icon(Icons.Rounded.MyLocation, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Minha Localização Atual")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (trackingActive) {
                                    onStopTracking()
                                } else if (hasLocationPermission()) {
                                    permissionDenied = false
                                    onStartTracking()
                                } else {
                                    pendingActionAfterGrant = "track"
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (trackingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("gps_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (trackingActive) Icons.Rounded.LocationOff else Icons.Rounded.GpsFixed,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when {
                                    trackingActive -> "Parar GPS"
                                    deviceRole == DeviceRole.CLIENT -> "Transmitir GPS"
                                    else -> "Rastrear Agora"
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val uri = Uri.parse("geo:$currentLat,$currentLon?q=$currentLat,$currentLon($catName)")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                try {
                                    context.startActivity(intent)
                                } catch (_: android.content.ActivityNotFoundException) {
                                    // Nenhum app de mapas instalado no dispositivo
                                }
                            },
                            enabled = hasValidCoords,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("open_maps_button")
                        ) {
                            Icon(Icons.Rounded.Map, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mapa")
                        }
                    }

                    if (permissionDenied) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Permissão de localização recusada. Ative o GPS nas configurações do Android para usar sua localização atual.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Raio da Zona Segura (Casa): ${sliderRadiusMeters.toInt()} metros",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = sliderRadiusMeters,
                        onValueChange = { sliderRadiusMeters = it },
                        valueRange = 30f..500f
                    )
                    OutlinedButton(
                        onClick = {
                            if (hasLocationPermission()) {
                                permissionDenied = false
                                onSetHomeSafeZone(sliderRadiusMeters.toDouble())
                            } else {
                                pendingActionAfterGrant = "home"
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("set_home_zone_button")
                    ) {
                        Icon(Icons.Rounded.Home, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Definir Posição Atual como Casa")
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = gpsError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismissError() }
                ) {
                    Text(
                        text = gpsError ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Text(
                text = "Histórico de Localização",
                style = MaterialTheme.typography.titleLarge
            )
        }

        when (locationsState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(text = locationsState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                if (trailPoints.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Inicie o GPS ou toque em \"Minha Localização Atual\" para registrar coordenadas reais.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(trailPoints, key = { it.id.ifBlank { "${it.latitude}_${it.longitude}" } }) { pt ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = if (pt.insideSafeZone) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = pt.zoneLabel,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "%.5f, %.5f • ±%.0fm".format(pt.latitude, pt.longitude, pt.accuracyMeters),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = pt.createdAt?.toDate()?.let { timeFormatter.format(it) } ?: "Agora",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CatGpsRadarCanvas(
    homeLat: Double,
    homeLon: Double,
    currentLat: Double,
    currentLon: Double,
    hasValidCoords: Boolean,
    radiusMeters: Double,
    trail: List<GpsPoint>,
    trackingActive: Boolean,
    pulseProgress: Float,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val safeZoneColor = MaterialTheme.colorScheme.secondary
    val catColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxPxRadius = (size.minDimension / 2f) * 0.78f

            listOf(0.33f, 0.66f, 1.0f).forEach { factor ->
                drawCircle(
                    color = gridColor,
                    radius = maxPxRadius * factor,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
            }

            val safeRadiusPx = maxPxRadius * 0.65f
            drawCircle(
                color = safeZoneColor.copy(alpha = 0.12f),
                radius = safeRadiusPx,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = safeZoneColor.copy(alpha = 0.6f),
                radius = safeRadiusPx,
                center = Offset(cx, cy),
                style = Stroke(width = 3f)
            )

            if (!hasValidCoords) return@Canvas

            val refLat = if (homeLat != 0.0 || homeLon != 0.0) homeLat else currentLat
            val refLon = if (homeLat != 0.0 || homeLon != 0.0) homeLon else currentLon

            val metersPerDegLat = 111_132.0
            val metersPerDegLon = 111_320.0 * cos(Math.toRadians(refLat)).coerceAtLeast(0.1)
            val scaleMeters = radiusMeters.coerceAtLeast(50.0) * 1.55
            val pxPerMeter = maxPxRadius / scaleMeters.toFloat()

            fun toOffset(lat: Double, lon: Double): Offset {
                val dxMeters = (lon - refLon) * metersPerDegLon
                val dyMeters = (lat - refLat) * metersPerDegLat
                val x = (cx + dxMeters.toFloat() * pxPerMeter).coerceIn(20f, size.width - 20f)
                val y = (cy - dyMeters.toFloat() * pxPerMeter).coerceIn(20f, size.height - 20f)
                return Offset(x, y)
            }

            if (trail.size >= 2) {
                val path = Path()
                val reversed = trail.reversed()
                reversed.forEachIndexed { index, pt ->
                    val p = toOffset(pt.latitude, pt.longitude)
                    if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(
                    path = path,
                    color = catColor.copy(alpha = 0.55f),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
            }

            drawCircle(
                color = safeZoneColor,
                radius = 9f,
                center = Offset(cx, cy)
            )

            val catPos = toOffset(currentLat, currentLon)
            if (trackingActive) {
                drawCircle(
                    color = catColor.copy(alpha = (1f - pulseProgress) * 0.45f),
                    radius = 16f + 30f * pulseProgress,
                    center = catPos
                )
            }
            drawCircle(
                color = catColor,
                radius = 14f,
                center = catPos
            )
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White,
                radius = 5f,
                center = catPos
            )
        }
    }
}
