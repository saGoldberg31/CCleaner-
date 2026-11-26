package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AnalyzedPhotoItem
import com.example.model.Formatters
import com.example.model.PhotoCompressionPreset
import com.example.model.PhotoQualityIssue
import com.example.ui.CleanProUiState
import com.example.ui.components.liquidGlassCard
import com.example.ui.components.liquidPressBounce
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalLiquidGlassEnabled
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun PhotoOptimizerScreen(
    uiState: CleanProUiState,
    onBack: () -> Unit,
    onPickPhotos: () -> Unit,
    onSelectPreset: (PhotoCompressionPreset) -> Unit,
    onTogglePhotoSelection: (Uri) -> Unit,
    onSelectAllPhotos: (Boolean) -> Unit,
    onCompressSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("photo_optimizer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }
                        Column {
                            Text(
                                text = "محسن وضغط الصور",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "كشف الصور المهزوزة والمكررة وتقليل الحجم",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onPickPhotos,
                        modifier = Modifier.testTag("photo_optimizer_pick_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختيار صور")
                    }
                }

                // Compression Preset Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PhotoCompressionPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = uiState.photoCompressionPreset == preset,
                            onClick = { onSelectPreset(preset) },
                            label = { Text("${preset.labelAr} (${preset.detailAr})") },
                            modifier = Modifier.testTag("preset_${preset.name}")
                        )
                    }
                }

                AnimatedVisibility(visible = uiState.photoOptimizeBanner != null) {
                    uiState.photoOptimizeBanner?.let { msg ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassCard(
                                    shape = MaterialTheme.shapes.medium,
                                    accentTint = telemetryColors.healthy,
                                    shimmerActive = true
                                )
                                .testTag("photo_optimize_banner")
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = telemetryColors.healthy,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                if (uiState.analyzedPhotos.isNotEmpty()) {
                    val issuesCount = uiState.analyzedPhotos.count { it.issue != PhotoQualityIssue.NONE }
                    val allSelected = uiState.analyzedPhotos.all { it.isSelected }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${uiState.analyzedPhotos.size} صورة • $issuesCount تحتاج مراجعة",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { onSelectAllPhotos(!allSelected) }) {
                            Text(if (allSelected) "إلغاء تحديد الكل" else "تحديد الكل")
                        }
                    }
                }
            }

            if (uiState.isAnalyzingPhotos) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("جاري فحص حدة الصور والإضاءة وبصمة التكرار…")
                    }
                }
            } else if (uiState.analyzedPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassCard(shape = MaterialTheme.shapes.large)
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "تحليل جودة الصور وضغطها بذكاء",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "اختر صوراً من معرضك عبر منتقي الصور الآمن ليقوم التطبيق باكتشاف الصور المهزوزة أو ذات الإضاءة الضعيفة أو المكررة، مع إمكانية ضغطها وحفظ نسخة خفيفة دون فقدان الوضوح.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onPickPhotos,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("اختيار صور من المعرض")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("analyzed_photos_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.analyzedPhotos, key = { it.uri.toString() }) { photo ->
                        AnalyzedPhotoRow(
                            photo = photo,
                            onToggle = { onTogglePhotoSelection(photo.uri) },
                            modifier = Modifier.animateItem()
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            AnimatedVisibility(visible = uiState.selectedPhotosCount > 0) {
                val liquidEnabled = LocalLiquidGlassEnabled.current
                Surface(
                    tonalElevation = if (liquidEnabled) 0.dp else 6.dp,
                    shadowElevation = if (liquidEnabled) 0.dp else 8.dp,
                    color = if (liquidEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (liquidEnabled) {
                                Modifier.liquidGlassCard(
                                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                                    isElevated = true
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${uiState.selectedPhotosCount} صورة محددة",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "توفير متوقع: ${Formatters.formatBytes(uiState.estimatedPhotoSavingsBytes)}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = telemetryColors.healthy
                            )
                        }
                        Button(
                            onClick = onCompressSelected,
                            enabled = !uiState.isCompressingPhotos,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("compress_photos_button")
                        ) {
                            if (uiState.isCompressingPhotos) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Compress,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.isCompressingPhotos) "جاري الضغط…" else "ضغط وحفظ")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzedPhotoRow(
    photo: AnalyzedPhotoItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    val thumb = remember(photo.uri, photo.thumbnail) {
        photo.thumbnail?.asImageBitmap()
    }
    val issueColor = when (photo.issue) {
        PhotoQualityIssue.NONE -> telemetryColors.healthy
        PhotoQualityIssue.BLURRY -> telemetryColors.warning
        PhotoQualityIssue.UNDEREXPOSED, PhotoQualityIssue.OVEREXPOSED -> telemetryColors.info
        PhotoQualityIssue.DUPLICATE -> telemetryColors.danger
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .liquidPressBounce()
            .liquidGlassCard(
                shape = MaterialTheme.shapes.medium,
                accentTint = if (photo.isSelected) MaterialTheme.colorScheme.primary else null
            )
            .clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = photo.isSelected,
                onCheckedChange = { onToggle() },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("photo_checkbox_${photo.displayName}")
            )

            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = photo.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = photo.displayName
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = photo.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = issueColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = photo.issue.labelAr,
                        style = MaterialTheme.typography.labelSmall,
                        color = issueColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (photo.width > 0 && photo.height > 0) {
                    Text(
                        text = "${photo.width}×${photo.height}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.formatBytes(photo.sizeBytes),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "← ${Formatters.formatBytes(photo.estimatedCompressedBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = telemetryColors.healthy
                )
            }
        }
    }
}
