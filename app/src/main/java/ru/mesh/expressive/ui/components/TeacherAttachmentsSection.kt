package ru.mesh.expressive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mesh.expressive.data.model.LessonMaterialItem
import ru.mesh.expressive.ui.theme.ExpressiveCardShape
import ru.mesh.expressive.ui.theme.PillShape
import ru.mesh.expressive.ui.viewmodel.MeshMainViewModel
import ru.mesh.expressive.util.MeshFileDownloader

@Composable
fun TeacherAttachmentsSection(
    files: List<LessonMaterialItem>,
    viewModel: MeshMainViewModel,
    modifier: Modifier = Modifier
) {
    if (files.isEmpty()) return

    val context = LocalContext.current
    val downloadedFiles by viewModel.downloadedFileNames.collectAsState()
    val downloadingUrl by viewModel.currentlyDownloadingUrl.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "ФАЙЛЫ ОТ УЧИТЕЛЯ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "${files.size}",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        files.forEach { fileMat ->
            val ext = fileMat.title.substringAfterLast('.', "").lowercase()
            val (icon, tintColor) = getFileVisuals(ext)
            val isDownloaded = downloadedFiles.contains(fileMat.title) || MeshFileDownloader.isDownloaded(context, fileMat.title)
            val isCurrentDownloading = downloadingUrl == fileMat.url && !fileMat.url.isNullOrBlank()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(ExpressiveCardShape)
                    .clickable {
                        if (!fileMat.url.isNullOrBlank()) {
                            viewModel.openAttachment(context, fileMat.url, fileMat.title)
                        }
                    },
                shape = ExpressiveCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tintColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = tintColor
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileMat.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fileMat.typeName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isCurrentDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isDownloaded) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (!fileMat.url.isNullOrBlank()) {
                                        viewModel.downloadAttachment(context, fileMat.url, fileMat.title, openAfterDownload = false)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Скачать",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            FilledTonalButton(
                                onClick = {
                                    viewModel.openAttachment(context, fileMat.url.orEmpty(), fileMat.title)
                                },
                                shape = PillShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Открыть", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                if (!fileMat.url.isNullOrBlank()) {
                                    viewModel.downloadAttachment(context, fileMat.url, fileMat.title, openAfterDownload = false)
                                }
                            },
                            shape = PillShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Скачать", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun getFileVisuals(extension: String): Pair<ImageVector, Color> {
    return when (extension.lowercase()) {
        "pdf" -> Icons.Default.PictureAsPdf to Color(0xFFE53935)
        "odt", "doc", "docx" -> Icons.Default.Description to Color(0xFF1E88E5)
        "xls", "xlsx", "csv" -> Icons.Default.TableChart to Color(0xFF43A047)
        "ppt", "pptx" -> Icons.Default.Slideshow to Color(0xFFFB8C00)
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip to Color(0xFF8E24AA)
        "png", "jpg", "jpeg", "webp", "gif" -> Icons.Default.Image to Color(0xFF00ACC1)
        else -> Icons.Default.InsertDriveFile to Color(0xFF5C6BC0)
    }
}
