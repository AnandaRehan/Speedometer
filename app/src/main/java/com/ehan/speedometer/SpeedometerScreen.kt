package com.ehan.speedometer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ehan.speedometer.ui.theme.SpeedometerTheme
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f
private const val TICK_COUNT = 12

@Composable
fun SpeedometerRoute(viewModel: SpeedometerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) viewModel.startTracking()
    }

    KeepScreenOn()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.resumeUpdates() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pauseUpdates() }

    SpeedometerScreen(
        state = state,
        permissionGranted = granted,
        onToggle = {
            when {
                !granted -> launcher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
                state.tracking -> viewModel.stopTracking()
                else -> viewModel.startTracking()
            }
        },
        onReset = viewModel::reset,
        onUnitChange = viewModel::setUseMph,
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
fun SpeedometerScreen(
    state: SpeedoState,
    permissionGranted: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onUnitChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mph = state.useMph
    val unit = if (mph) "mph" else "km/h"

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SpeedGauge(
            speed = state.speedMps.toSpeed(mph),
            maxValue = if (mph) 160f else 240f,
            unit = unit,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = when {
                !permissionGranted -> "Izin lokasi dibutuhkan untuk membaca kecepatan"
                !state.tracking -> "Tekan Mulai untuk mengukur kecepatan"
                !state.hasFix -> "Mencari sinyal GPS…"
                else -> "Akurasi GPS ±${state.accuracyM?.toInt() ?: 0} m"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Maks",
                value = "%.0f".format(state.maxSpeedMps.toSpeed(mph)),
                unit = unit,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Rata-rata",
                value = "%.0f".format(state.avgSpeedMps.toSpeed(mph)),
                unit = unit,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Jarak",
                value = "%.2f".format(state.distanceM.toDistance(mph)),
                unit = if (mph) "mi" else "km",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Waktu bergerak",
                value = state.movingTimeMs.toClock(),
                unit = "",
                modifier = Modifier.weight(1f),
            )
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("km/h", "mph").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = (index == 1) == mph,
                    onClick = { onUnitChange(index == 1) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                ) { Text(label) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onToggle, modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        !permissionGranted -> "Beri izin"
                        state.tracking -> "Berhenti"
                        else -> "Mulai"
                    }
                )
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text("Reset")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SpeedGauge(
    speed: Float,
    maxValue: Float,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(durationMillis = 500),
        label = "speed",
    )
    val fraction = (animated / maxValue).coerceIn(0f, 1f)

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.outline
    val progressColor = lerp(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.error,
        fraction,
    )

    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.06f
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - stroke / 2

            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP_ANGLE * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            for (i in 0..TICK_COUNT) {
                val angle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * i / TICK_COUNT).toDouble())
                val major = i % 2 == 0
                val inner = radius - stroke * if (major) 1.9f else 1.5f
                val outer = radius - stroke * 1.1f
                drawLine(
                    color = tickColor,
                    start = center + Offset(cos(angle).toFloat() * inner, sin(angle).toFloat() * inner),
                    end = center + Offset(cos(angle).toFloat() * outer, sin(angle).toFloat() * outer),
                    strokeWidth = if (major) stroke * 0.18f else stroke * 0.1f,
                    cap = StrokeCap.Round,
                )
            }

            val needleAngle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * fraction).toDouble())
            val needleLength = radius - stroke * 2.6f
            drawLine(
                color = progressColor,
                start = center,
                end = center + Offset(
                    cos(needleAngle).toFloat() * needleLength,
                    sin(needleAngle).toFloat() * needleLength,
                ),
                strokeWidth = stroke * 0.35f,
                cap = StrokeCap.Round,
            )
            drawCircle(color = progressColor, radius = stroke * 0.75f, center = center)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 24.dp),
        ) {
            Text(
                text = "%.0f".format(animated),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Float.toSpeed(mph: Boolean): Float = this * if (mph) 2.236936f else 3.6f
private fun Float.toDistance(mph: Boolean): Float = this / if (mph) 1609.344f else 1000f

private fun Long.toClock(): String {
    val totalSeconds = this / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Preview(showBackground = true)
@Composable
private fun SpeedometerPreview() {
    SpeedometerTheme {
        SpeedometerScreen(
            state = SpeedoState(
                tracking = true,
                hasFix = true,
                speedMps = 16.7f,
                maxSpeedMps = 22f,
                distanceM = 3400f,
                movingTimeMs = 412_000L,
                accuracyM = 6f,
            ),
            permissionGranted = true,
            onToggle = {},
            onReset = {},
            onUnitChange = {},
        )
    }
}
