package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalBatterySaverEnabled
import com.example.ui.theme.LocalLiquidGlassEnabled
import com.example.ui.theme.LocalTelemetryColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen ambient fluid mesh backdrop that shines behind frosted/translucent
 * Liquid Glass cards. Combines the high-resolution fluid waves wallpaper with
 * animated luminous fluid caustics and a soft adaptive scrim so that the frosted glass
 * cards pop with vibrant refraction and depth while preserving text legibility.
 * Respects Battery Saver by reducing animation speed/opacity.
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val liquidEnabled = LocalLiquidGlassEnabled.current
    val batterySaver = LocalBatterySaverEnabled.current
    val baseBackground = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBackground)
    ) {
        if (liquidEnabled) {
            // 1. High-resolution fluid wave curves wallpaper
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.bg_glass),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(-0.12f, 0f),
                modifier = Modifier.fillMaxSize()
            )

            // 2. Animated fluid oceanic caustics that gently breathe behind the cards
            val infiniteTransition = rememberInfiniteTransition(label = "liquid_mesh_bg")
            val cycleDuration = if (batterySaver) 24000 else 12000
            val phase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = (2.0 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = cycleDuration, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "liquid_phase"
            )
            val intensity = if (batterySaver) 0.25f else 0.65f

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Orb A: Luminous Cyan caustic light (illuminating top hero & gauge)
                val orbACenter = Offset(
                    x = w * (0.28f + 0.12f * cos(phase)),
                    y = h * (0.22f + 0.08f * sin(phase))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.40f * intensity),
                            Color.Transparent
                        ),
                        center = orbACenter,
                        radius = w * 0.78f
                    ),
                    center = orbACenter,
                    radius = w * 0.78f
                )

                // Orb B: Electric Azure caustic light (illuminating mid-screen cards)
                val orbBCenter = Offset(
                    x = w * (0.72f - 0.14f * sin(phase * 0.85f)),
                    y = h * (0.48f + 0.10f * cos(phase * 0.9f))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.38f * intensity),
                            Color.Transparent
                        ),
                        center = orbBCenter,
                        radius = w * 0.72f
                    ),
                    center = orbBCenter,
                    radius = w * 0.72f
                )

                // Orb C: Deep Radiant Sapphire caustic light (illuminating lower section)
                val orbCCenter = Offset(
                    x = w * (0.40f + 0.16f * sin(phase * 1.15f)),
                    y = h * (0.78f - 0.08f * cos(phase * 1.1f))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2563EB).copy(alpha = 0.35f * intensity),
                            Color.Transparent
                        ),
                        center = orbCCenter,
                        radius = w * 0.80f
                    ),
                    center = orbCCenter,
                    radius = w * 0.80f
                )
            }

            // 3. Adaptive vertical scrim: protects status/nav bars while letting liquid glass pop in content zones
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.20f),  // Top status bar safe area
                            0.14f to Color.Black.copy(alpha = 0.05f), // Hero gauge: maximum glass pop
                            0.45f to Color.Transparent,              // Mid cards: pure wallpaper vibrance
                            0.75f to Color.Black.copy(alpha = 0.08f), // Lower cards: balanced contrast
                            1.0f to Color.Black.copy(alpha = 0.22f)   // Bottom nav bar safe area
                        )
                    )
            )
        }
        content()
    }
}

/**
 * Applies translucent Liquid Glass frosting, specular sheen, and multi-stop
 * refractive rim border when Liquid Glass Mode is active; falls back cleanly to
 * standard Material 3 surface styling otherwise.
 */
fun Modifier.liquidGlassCard(
    shape: Shape? = null,
    accentTint: Color? = null,
    isElevated: Boolean = false,
    borderWidth: Dp = 1.dp,
    shimmerActive: Boolean = false
): Modifier = composed {
    val liquidEnabled = LocalLiquidGlassEnabled.current
    val batterySaver = LocalBatterySaverEnabled.current
    val resolvedShape = shape ?: RoundedCornerShape(26.dp)

    if (!liquidEnabled) {
        this
            .clip(resolvedShape)
            .background(
                color = if (isElevated) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                shape = resolvedShape
            )
            .border(
                width = borderWidth,
                color = (accentTint ?: MaterialTheme.colorScheme.outline).copy(alpha = 0.45f),
                shape = resolvedShape
            )
    } else {
        // Soft white gradient fill: ~0.14 at top, ~0.08 in middle, ~0.04 at bottom
        val fillTop = Color.White.copy(alpha = 0.14f)
        val fillMid = Color.White.copy(alpha = 0.08f)
        val fillBottom = Color.White.copy(alpha = 0.04f)

        // 1dp border, white at ~0.28 alpha, slightly brighter on top/left
        val rimTop = accentTint?.copy(alpha = 0.35f) ?: Color.White.copy(alpha = 0.30f)
        val rimBottom = accentTint?.copy(alpha = 0.20f) ?: Color.White.copy(alpha = 0.16f)

        val shimmerFraction = if (shimmerActive && !batterySaver) {
            val infiniteTransition = rememberInfiniteTransition(label = "glass_shimmer")
            val pos by infiniteTransition.animateFloat(
                initialValue = -0.35f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "shimmer_x"
            )
            pos
        } else {
            -1f
        }

        this
            .clip(resolvedShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(fillTop, fillMid, fillBottom),
                    start = Offset.Zero,
                    end = Offset(900f, 900f)
                ),
                shape = resolvedShape
            )
            .drawBehind {
                // Top inner specular highlight line for curved glass depth
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.40f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(size.width * 0.08f, 1.5.dp.toPx()),
                    end = Offset(size.width * 0.92f, 1.5.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Optional animated specular sheen sweep
                if (shimmerFraction in -0.35f..1.35f) {
                    val centerX = size.width * shimmerFraction
                    val bandWidth = size.width * 0.45f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            start = Offset(centerX - bandWidth, 0f),
                            end = Offset(centerX + bandWidth, size.height)
                        )
                    )
                }
            }
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(rimTop, rimBottom, rimTop.copy(alpha = 0.15f))
                ),
                shape = resolvedShape
            )
    }
}

/**
 * Adds a tactile spring bounce scale-down while pressing without consuming clicks.
 */
fun Modifier.liquidPressBounce(enabled: Boolean = true): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.968f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "press_bounce"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Press -> pressed = true
                        PointerEventType.Release, PointerEventType.Exit -> pressed = false
                    }
                }
            }
        }
}

/**
 * Smooth staggered entrance animation for hero cards and dashboard sections.
 */
@Composable
fun StaggeredLiquidReveal(
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it / 6 }
            ) +
            scaleIn(
                initialScale = 0.96f,
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            )
    ) {
        content()
    }
}

/**
 * An animated rotating gradient border with a soft glow, like the prompt box in Google AI Studio.
 * Drawn with drawWithContent so it renders above the card's glass fill without being obscured.
 */
fun Modifier.animatedGlowBorder(
    shape: Shape = RoundedCornerShape(26.dp),
    enabled: Boolean = true
): Modifier = composed {
    val liquidEnabled = LocalLiquidGlassEnabled.current
    val batterySaver = LocalBatterySaverEnabled.current
    if (!enabled || !liquidEnabled || batterySaver) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "rotating_glow_border")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    val colorsInts = remember {
        intArrayOf(
            0xFFFF7A00.toInt(), // Warm Orange
            0x00FF7A00,         // Orange fades out
            0x00101922,         // Transparent gap
            0xFF3B82F6.toInt(), // Blue
            0xFF00D2FF.toInt(), // Cyan / Teal
            0xFF10B981.toInt(), // Bright Green
            0x0010B981,         // Green fades out
            0x00101922,         // Transparent gap
            0xFFFF7A00.toInt()  // Warm Orange (closed loop)
        )
    }

    this.drawWithContent {
        // 1. Draw card background, glass fill, and inner contents first
        drawContent()

        if (size.width <= 0f || size.height <= 0f) return@drawWithContent

        val strokeWidth = 1.5.dp.toPx()
        val glowWidth = 5.dp.toPx()
        val cornerRadiusPx = if (shape is CornerBasedShape) {
            shape.topStart.toPx(size, this)
        } else {
            26.dp.toPx()
        }

        val cx = size.width / 2f
        val cy = size.height / 2f
        val shader = SweepGradient(cx, cy, colorsInts, null)
        val matrix = Matrix()
        matrix.postRotate(angle, cx, cy)
        shader.setLocalMatrix(matrix)

        val rotatingBrush = object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size): android.graphics.Shader = shader
        }

        // 2. Faint outer glow (broader stroke centered on the border line)
        drawRoundRect(
            brush = rotatingBrush,
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = androidx.compose.ui.geometry.Size(
                size.width - strokeWidth,
                size.height - strokeWidth
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = glowWidth),
            alpha = 0.40f
        )

        // 3. Sharp 1.5dp rotating gradient border drawn above the glass fill
        drawRoundRect(
            brush = rotatingBrush,
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = androidx.compose.ui.geometry.Size(
                size.width - strokeWidth,
                size.height - strokeWidth
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
            alpha = 1.0f
        )
    }
}
