package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.FileCategory
import com.example.model.Formatters
import com.example.model.ScannedFileItem
import com.example.ui.CleanProUiState
import com.example.ui.components.CleaningCircularProgressCard
import com.example.ui.components.liquidGlassCard
import com.example.ui.components.liquidPressBounce
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalLiquidGlassEnabled
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun FilesAnalyzerScreen(
    uiState: CleanProUiState,
    onSelectCategoryFilter: (FileCategory?) -> Unit,
    onToggleFileSelection: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onCleanSelected: () -> Unit,
    onAddFolderClick: () -> Unit,
    onRescanClick: () -> Unit,
    onPickPhotosDuplicates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Text(
                    "سيتم حذف ${uiState.selectedFilesCount} ملف بشكل نهائي بمساحة " +
                        "${Formatters.formatBytes(uiState.selectedFilesBytes)}."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onCleanSelected()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.confirm_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.confirm_delete_cancel))
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
        ) {
            // Top Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "محلل الملفات والمكررات",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${uiState.filteredFiles.size} عنصر • ${uiState.safFolders.size} مجلد متصل",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onAddFolderClick,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = stringResource(R.string.add_folder),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مجلد")
                        }
                        OutlinedButton(
                            onClick = onRescanClick,
                            enabled = !uiState.scanProgress.isScanning,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "إعادة الفحص",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedCategoryFilter == null,
                        onClick = { onSelectCategoryFilter(null) },
                        label = { Text("الكل (${uiState.scannedFiles.size})") }
                    )
                    FileCategory.entries.forEach { cat ->
                        val count = uiState.scannedFiles.count { it.category == cat }
                        FilterChip(
                            selected = uiState.selectedCategoryFilter == cat,
                            onClick = { onSelectCategoryFilter(cat) },
                            label = { Text("${cat.labelAr} ($count)") }
                        )
                    }
                }

                // Select All Row
                if (uiState.filteredFiles.any { it.isDeletableSaf }) {
                    val deletableInFilter = uiState.filteredFiles.filter { it.isDeletableSaf }
                    val allSelected = deletableInFilter.isNotEmpty() && deletableInFilter.all { it.isSelected }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { onSelectAll(!allSelected) }) {
                            Text(if (allSelected) "إلغاء تحديد الكل" else "تحديد كل العناصر القابلة للحذف")
                        }
                        TextButton(onClick = onPickPhotosDuplicates) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("فحص صور من المعرض")
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.cleaningProgress.isCleaning) {
                    CleaningCircularProgressCard(
                        progress = uiState.cleaningProgress.progress,
                        statusLabel = uiState.cleaningProgress.statusLabel
                    )
                }
            }

            // Content List or Empty State
            if (uiState.filteredFiles.isEmpty()) {
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
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_clean_shield),
                                contentDescription = "لا توجد مخلفات",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(MaterialTheme.shapes.medium),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = if (uiState.safFolders.isEmpty()) {
                                    "لم يتم ربط مجلدات للفحص بعد"
                                } else {
                                    "لا توجد ملفات في هذا التصنيف"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (uiState.safFolders.isEmpty()) {
                                    "أضف مجلد التنزيلات أو أي مجلد آخر لفحص الملفات المؤقتة والمكررة وحزم APK بأمان."
                                } else {
                                    "كل شيء يبدو نظيفاً في هذا التصنيف! يمكنك تجربة فحص مجلد إضافي أو مقارنة صور المعرض."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = onAddFolderClick) {
                                    Text(stringResource(R.string.add_folder))
                                }
                                OutlinedButton(onClick = onPickPhotosDuplicates) {
                                    Text("مقارنة صور")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredFiles, key = { it.id }) { item ->
                        ScannedFileRow(
                            item = item,
                            onToggle = { onToggleFileSelection(item.id) },
                            modifier = Modifier.animateItem()
                        )
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }

            // Sticky Bottom Action Bar when items selected
            if (uiState.selectedFilesCount > 0) {
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
                        Column {
                            Text(
                                text = "${uiState.selectedFilesCount} ملف محدد",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = Formatters.formatBytes(uiState.selectedFilesBytes),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = telemetryColors.healthy
                            )
                        }
                        Button(
                            onClick = { showDeleteConfirm = true },
                            enabled = !uiState.cleaningProgress.isCleaning && !uiState.scanProgress.isScanning,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("files_delete_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.clean_selected))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannedFileRow(
    item: ScannedFileItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetryColors = LocalTelemetryColors.current
    val (badgeIcon, badgeColor) = when (item.category) {
        FileCategory.TEMP_AND_EMPTY -> Icons.Default.DeleteSweep to telemetryColors.warning
        FileCategory.RESIDUAL_APK -> Icons.Default.Android to telemetryColors.healthy
        FileCategory.LARGE_FILES -> Icons.Default.FolderZip to telemetryColors.info
        FileCategory.DUPLICATE_FILES -> Icons.Default.ContentCopy to telemetryColors.danger
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .liquidPressBounce(enabled = item.isDeletableSaf)
            .liquidGlassCard(
                shape = MaterialTheme.shapes.medium,
                accentTint = if (item.isSelected) MaterialTheme.colorScheme.primary else null
            )
            .clickable(enabled = item.isDeletableSaf, onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isDeletableSaf) {
                Checkbox(
                    checked = item.isSelected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = badgeColor
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = item.category.labelAr,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.folderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.category.labelAr,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor
                    )
                    if (item.duplicateGroupHash != null) {
                        Text(
                            text = "• #${item.duplicateGroupHash}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!item.isDeletableSaf) {
                        Text(
                            text = "• للعرض من المعرض",
                            style = MaterialTheme.typography.labelSmall,
                            color = telemetryColors.info
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = Formatters.formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
