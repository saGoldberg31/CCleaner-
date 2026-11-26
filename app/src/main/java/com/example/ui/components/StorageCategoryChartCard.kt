package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DiskCategoryUsage
import com.example.model.DiskStorageCategory
import com.example.model.Formatters
import com.example.model.StorageChartMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.TelemetryColors
import kotlin.math.atan2
import kotlin.math.hypot

private data class DonutArcSegment(
    val usage: DiskCategoryUsage,
    val color: Color,
    val startAngle: Float,
    val sweepAngle: Float,
    val fraction: Float
)

@Composable
fun StorageCategoryChartCard(
    slices: List<DiskCategoryUsage>,
    chartMode: StorageChartMode,
    hasConnectedFolders: Boolean,
    onSelectChartMode: (StorageChartMode) -> Unit,
    onAddFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    var selectedCategory by remember { mutableStateOf<DiskStorageCategory?>(null) }

    val totalBytes = remember(slices) { slices.sumOf { it.bytes.coerceAtLeast(0L) } }

    // Build non-zero arc segments for the circular chart
    val arcSegments = remember(slices, totalBytes, telemetryColors) {
        if (totalBytes <= 0L) {
            emptyList()
        } else {
            val nonZero = slices.filter { it.bytes > 0L }
            val gapDegrees = if (nonZero.size > 1) 2.2f else 0f
            var currentAngle = -90f
            nonZero.map { usage ->
                val rawFraction = (usage.bytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
                val fullSweep = rawFraction * 360f
                val adjustedSweep = (fullSweep - gapDegrees).coerceAtLeast(if (rawFraction > 0.005f) 2f else 0.8f)
                val seg = DonutArcSegment(
                    usage = usage,
                    color = categoryColor(usage.category, telemetryColors),
                    startAngle = currentAngle,
                    sweepAngle = adjustedSweep,
                    fraction = rawFraction
                )
                currentAngle += fullSweep
                seg
            }
        }
    }

    val selectedUsage = remember(slices, selectedCategory) {
        selectedCategory?.let { cat -> slices.firstOrNull { it.category == cat } }
    }

    val animProgress by animateFloatAsState(
        targetValue = if (arcSegments.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "donut_entry_anim"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "محلل المساحة حسب الفئة",
            icon = Icons.Default.PieChart
        )
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassCard(
                    shape = MaterialTheme.shapes.large,
                    shimmerActive = true
                )
                .testTag("storage_category_chart_card"),
            shape = MaterialTheme.shapes.large,
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Mode toggle chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StorageChartMode.entries.forEach { mode ->
                        val chipTag = if (mode == StorageChartMode.FULL_DISK) {
                            "chart_mode_full_disk"
                        } else {
                            "chart_mode_file_categories"
                        }
                        FilterChip(
                            selected = chartMode == mode,
                            onClick = {
                                selectedCategory = null
                                onSelectChartMode(mode)
                            },
                            label = { Text(mode.labelAr) },
                            modifier = Modifier.testTag(chipTag)
                        )
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWide = maxWidth >= 540.dp
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            CategoryDonutCanvas(
                                segments = arcSegments,
                                selectedCategory = selectedCategory,
                                selectedUsage = selectedUsage,
                                totalBytes = totalBytes,
                                chartMode = chartMode,
                                sweepReveal = animProgress,
                                trackColor = telemetryColors.gaugeTrack,
                                onSelectCategory = { cat ->
                                    selectedCategory = if (selectedCategory == cat) null else cat
                                },
                                modifier = Modifier.size(204.dp)
                            )
                            CategoryLegendList(
                                slices = slices,
                                totalBytes = totalBytes,
                                selectedCategory = selectedCategory,
                                telemetryColors = telemetryColors,
                                onSelectCategory = { cat ->
                                    selectedCategory = if (selectedCategory == cat) null else cat
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CategoryDonutCanvas(
                                segments = arcSegments,
                                selectedCategory = selectedCategory,
                                selectedUsage = selectedUsage,
                                totalBytes = totalBytes,
                                chartMode = chartMode,
                                sweepReveal = animProgress,
                                trackColor = telemetryColors.gaugeTrack,
                                onSelectCategory = { cat ->
                                    selectedCategory = if (selectedCategory == cat) null else cat
                                },
                                modifier = Modifier.size(196.dp)
                            )
                            CategoryLegendList(
                                slices = slices,
                                totalBytes = totalBytes,
                                selectedCategory = selectedCategory,
                                telemetryColors = telemetryColors,
                                onSelectCategory = { cat ->
                                    selectedCategory = if (selectedCategory == cat) null else cat
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (!hasConnectedFolders) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "لتحليل الصور والفيديو والمستندات بدقة، وصّل مجلدات جهازك (مثل Downloads أو DCIM أو Documents).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onAddFolderClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chart_add_folder_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إضافة مجلد لتحليل الفئات")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDonutCanvas(
    segments: List<DonutArcSegment>,
    selectedCategory: DiskStorageCategory?,
    selectedUsage: DiskCategoryUsage?,
    totalBytes: Long,
    chartMode: StorageChartMode,
    sweepReveal: Float,
    trackColor: Color,
    onSelectCategory: (DiskStorageCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current

    val selectedStrokeBoost by animateFloatAsState(
        targetValue = if (selectedCategory != null) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "donut_slice_pop"
    )

    Box(
        modifier = modifier.testTag("storage_category_donut_chart"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(segments) {
                    detectTapGestures { offset ->
                        if (segments.isEmpty()) return@detectTapGestures
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = hypot(dx, dy)
                        val minDim = minOf(size.width, size.height).toFloat()
                        val minR = minDim * 0.22f
                        val maxR = minDim * 0.50f
                        if (dist in minR..maxR) {
                            // Angle clockwise starting from -90° (top)
                            val rawDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            val normalized = ((rawDeg + 90f) + 360f) % 360f
                            val tappedAngleFromStart = normalized - 90f
                            val hit = segments.firstOrNull { seg ->
                                val relStart = ((seg.startAngle + 90f) + 360f) % 360f
                                val relEnd = relStart + seg.sweepAngle
                                val angle = normalized
                                angle in relStart..relEnd || (tappedAngleFromStart in seg.startAngle..(seg.startAngle + seg.sweepAngle))
                            }
                            if (hit != null) {
                                onSelectCategory(hit.usage.category)
                            }
                        }
                    }
                }
        ) {
            val baseStroke = 24.dp.toPx()
            val maxSelectedStroke = 32.dp.toPx()
            val inset = maxSelectedStroke / 2f + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Base circular track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = baseStroke, cap = StrokeCap.Butt)
            )

            segments.forEach { seg ->
                val isSelected = seg.usage.category == selectedCategory
                val hasSelection = selectedCategory != null
                val alpha = when {
                    !hasSelection -> 1f
                    isSelected -> 1f
                    else -> 0.35f
                }
                val stroke = if (isSelected) {
                    baseStroke + (maxSelectedStroke - baseStroke) * selectedStrokeBoost
                } else {
                    baseStroke
                }
                drawArc(
                    color = seg.color.copy(alpha = alpha),
                    startAngle = seg.startAngle,
                    sweepAngle = seg.sweepAngle * sweepReveal,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
            }
        }

        AnimatedContent(
            targetState = selectedUsage,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "donut_center_readout"
        ) { active ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(horizontal = 26.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag("donut_center_summary")
            ) {
                if (active != null) {
                    val activeColor = categoryColor(active.category, telemetryColors)
                    val pct = if (totalBytes > 0L) {
                        (active.bytes.toDouble() / totalBytes.toDouble()).toFloat()
                    } else 0f
                    Icon(
                        imageVector = categoryIcon(active.category),
                        contentDescription = active.category.labelAr,
                        tint = activeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Formatters.formatBytes(active.bytes),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = activeColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${active.category.labelAr} (${Formatters.formatPercent(pct)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = Formatters.formatBytes(totalBytes),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (chartMode == StorageChartMode.FULL_DISK) {
                            "إجمالي القرص"
                        } else {
                            "إجمالي الفئات"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "اضغط على فئة للتفاصيل",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryLegendList(
    slices: List<DiskCategoryUsage>,
    totalBytes: Long,
    selectedCategory: DiskStorageCategory?,
    telemetryColors: TelemetryColors,
    onSelectCategory: (DiskStorageCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slices.forEach { slice ->
            val isSelected = slice.category == selectedCategory
            val color = categoryColor(slice.category, telemetryColors)
            val fraction = if (totalBytes > 0L) {
                (slice.bytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            } else 0f

            val animatedFraction by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
                label = "legend_bar_${slice.category.name}"
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidPressBounce()
                    .liquidGlassCard(
                        shape = RoundedCornerShape(12.dp),
                        accentTint = if (isSelected) color else null,
                        borderWidth = if (isSelected) 1.5.dp else 1.dp
                    )
                    .clickable { onSelectCategory(slice.category) }
                    .semantics(mergeDescendants = true) {}
                    .testTag("category_legend_${slice.category.name}"),
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Icon(
                                imageVector = categoryIcon(slice.category),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = slice.category.labelAr,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (slice.itemCount > 0) {
                                Text(
                                    text = "(${slice.itemCount})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = Formatters.formatBytes(slice.bytes),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = Formatters.formatPercent(fraction),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = color
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { animatedFraction },
                        color = color,
                        trackColor = telemetryColors.gaugeTrack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }
        }
    }
}

private fun categoryColor(category: DiskStorageCategory, colors: TelemetryColors): Color {
    return when (category) {
        DiskStorageCategory.PHOTOS -> colors.categoryPhotos
        DiskStorageCategory.VIDEOS -> colors.categoryVideos
        DiskStorageCategory.AUDIO -> colors.categoryAudio
        DiskStorageCategory.DOCUMENTS -> colors.categoryDocuments
        DiskStorageCategory.ARCHIVES_APK -> colors.categoryArchives
        DiskStorageCategory.APPS -> colors.categoryApps
        DiskStorageCategory.OTHER -> colors.categoryOther
        DiskStorageCategory.FREE -> colors.categoryFree
    }
}

private fun categoryIcon(category: DiskStorageCategory): ImageVector {
    return when (category) {
        DiskStorageCategory.PHOTOS -> Icons.Default.Image
        DiskStorageCategory.VIDEOS -> Icons.Default.VideoFile
        DiskStorageCategory.AUDIO -> Icons.Default.AudioFile
        DiskStorageCategory.DOCUMENTS -> Icons.Default.Description
        DiskStorageCategory.ARCHIVES_APK -> Icons.Default.Android
        DiskStorageCategory.APPS -> Icons.Default.Apps
        DiskStorageCategory.OTHER -> Icons.Default.FolderOpen
        DiskStorageCategory.FREE -> Icons.Default.Storage
    }
}
