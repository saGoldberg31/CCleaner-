package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Formatters
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalBatterySaverEnabled
import com.example.ui.theme.LocalLiquidGlassEnabled
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun StorageRadialHero(
    usageFraction: Float,
    usedBytes: Long,
    totalBytes: Long,
    freeBytes: Long,
    isScanning: Boolean,
    isCleaning: Boolean = false,
    cleaningProgress: Float = 0f,
    cleaningStatusLabel: String = "",
    modifier: Modifier = Modifier,
    size: Dp = 216.dp
) {
    val telemetryColors = LocalTelemetryColors.current
    val liquidEnabled = LocalLiquidGlassEnabled.current
    val batterySaver = LocalBatterySaverEnabled.current

    val animatedStorageFraction by animateFloatAsState(
        targetValue = usageFraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "storage_sweep"
    )

    val animatedCleanProgress by animateFloatAsState(
        targetValue = cleaningProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "cleaning_sweep"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "scan_clean_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (batterySaver) 1600 else 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val orbitalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (batterySaver) 2800 else 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_rot"
    )
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (batterySaver) 3200 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liquid_halo_scale"
    )

    val isProcessing = isScanning || isCleaning
    val accentColor = when {
        isCleaning -> telemetryColors.healthy
        usageFraction >= 0.85f -> telemetryColors.danger
        usageFraction >= 0.70f -> telemetryColors.warning
        else -> telemetryColors.healthy
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag(if (isCleaning) "cleaning_circular_progress" else "storage_radial_hero"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val inset = strokeWidth / 2f + 10.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Ambient radial glow behind the gauge
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isProcessing) pulseAlpha * 0.38f else if (liquidEnabled) 0.20f else 0.14f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = (this.size.minDimension / 2f) * if (liquidEnabled) haloScale else 1f
                )
            )

            // Subtle liquid glass inner lens ring
            if (liquidEnabled) {
                drawCircle(
                    color = Color.White.copy(alpha = if (telemetryColors.isDark) 0.10f else 0.45f),
                    radius = (this.size.minDimension / 2f - inset - strokeWidth * 0.75f) * haloScale,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            if (isCleaning) {
                // Full 360° circular track for smooth cleaning progress
                drawArc(
                    color = telemetryColors.gaugeTrack,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Smooth determinate progress arc from top (-90°)
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            telemetryColors.info,
                            telemetryColors.healthy,
                            telemetryColors.info
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedCleanProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Smooth rotating outer shimmer arc
                drawArc(
                    color = telemetryColors.healthy.copy(alpha = pulseAlpha),
                    startAngle = orbitalRotation,
                    sweepAngle = 85f,
                    useCenter = false,
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = Size(this.size.width - 8.dp.toPx(), this.size.height - 8.dp.toPx()),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                // Storage Track Arc
                drawArc(
                    color = telemetryColors.gaugeTrack,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Active Storage Usage Arc
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(telemetryColors.info, accentColor, accentColor)
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * animatedStorageFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Subtle scanning ring
                if (isScanning) {
                    drawArc(
                        color = telemetryColors.info.copy(alpha = pulseAlpha),
                        startAngle = orbitalRotation,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = Size(this.size.width - 8.dp.toPx(), this.size.height - 8.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        AnimatedContent(
            targetState = isCleaning,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "hero_center_content"
        ) { cleaningActive ->
            if (cleaningActive) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = telemetryColors.healthy,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Formatters.formatPercent(animatedCleanProgress),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp
                        ),
                        color = telemetryColors.healthy
                    )
                    Text(
                        text = cleaningStatusLabel.ifBlank { "جاري التنظيف…" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = Formatters.formatPercent(animatedStorageFraction),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "التخزين المستخدم",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accentColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "${Formatters.formatBytes(freeBytes)} متاح",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${Formatters.formatBytes(usedBytes)} / ${Formatters.formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CleaningCircularProgressCard(
    progress: Float,
    statusLabel: String,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "card_clean_progress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "card_clean_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "card_spin_angle"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassCard(
                shape = MaterialTheme.shapes.medium,
                accentTint = telemetryColors.healthy,
                shimmerActive = true
            )
            .testTag("files_cleaning_circular_progress"),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2f + 2.dp.toPx()
                    val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                    val topLeft = Offset(inset, inset)

                    drawArc(
                        color = telemetryColors.gaugeTrack,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(telemetryColors.info, telemetryColors.healthy, telemetryColors.info)
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = telemetryColors.healthy.copy(alpha = 0.7f),
                        startAngle = spinAngle,
                        sweepAngle = 60f,
                        useCenter = false,
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(this.size.width - 2.dp.toPx(), this.size.height - 2.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = Formatters.formatPercent(animatedProgress),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = telemetryColors.healthy
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "جاري تنفيذ التنظيف…",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = statusLabel.ifBlank { "معالجة الملفات المحددة" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MiniMetricTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val liquidEnabled = LocalLiquidGlassEnabled.current
    val batterySaver = LocalBatterySaverEnabled.current
    val infiniteTransition = rememberInfiniteTransition(label = "metric_tile_pulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (liquidEnabled && !batterySaver) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "metric_icon_scale"
    )

    Surface(
        modifier = modifier.liquidGlassCard(
            shape = MaterialTheme.shapes.medium,
            accentTint = accentColor,
            isElevated = true
        ),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}
