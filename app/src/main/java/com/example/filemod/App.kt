package com.example.filemod

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.content.ContentValues
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video
import android.provider.MediaStore.Files

// ─── 工具函数 ───

fun getFileName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else null
    }
}

fun getRealPathFromUri(context: Context, uri: Uri): String? {
    // 1. 文件 scheme 直接返回
    if (ContentResolver.SCHEME_FILE == uri.scheme) {
        return uri.path
    }

    // 2. 处理文档 URI（例如 image:23090）
    if (DocumentsContract.isDocumentUri(context, uri)) {
        val docId = DocumentsContract.getDocumentId(uri)
        // URL 解码，将 %3A 还原为冒号
        val decodedId = Uri.decode(docId)
        val split = decodedId.split(":").toTypedArray()
        val type = split[0]
        val id = if (split.size > 1) split[1] else ""

        // 主存储 primary
        if ("primary".equals(type, ignoreCase = true)) {
            return "${Environment.getExternalStorageDirectory().absolutePath}/$id"
        }

        // 媒体类型（image/video/audio）
        if (type == "image" || type == "video" || type == "audio") {
            val contentUri: Uri = when (type) {
                "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val selection = "_id=?"
            val selectionArgs = arrayOf(id)
            context.contentResolver.query(
                contentUri,
                arrayOf(MediaStore.MediaColumns.DATA),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    return cursor.getString(columnIndex)
                }
            }
        }
    }

    // 3. 通用 DATA 列查询
    try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val path = cursor.getString(columnIndex)
                    if (!path.isNullOrEmpty()) return path
                }
            }
    } catch (_: Exception) {}

    // 4. 尝试 _data 列（大小写不敏感）
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex("_data")
                if (idx >= 0) {
                    val path = cursor.getString(idx)
                    if (!path.isNullOrEmpty()) return path
                }
            }
        }
    } catch (_: Exception) {}

    return null
}

fun copyUriToTempFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}")
        tempFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        inputStream.close()
        tempFile
    } catch (e: Exception) {
        Log.e("TimeModifier", "复制文件失败: $e")
        null
    }
}

fun writeFileBackToUri(context: Context, uri: Uri, tempFile: File): Boolean {
    return try {
        val outputStream = context.contentResolver.openOutputStream(uri, "wt") ?: return false
        tempFile.inputStream().use { input ->
            input.copyTo(outputStream)
        }
        outputStream.close()
        true
    } catch (e: Exception) {
        Log.e("TimeModifier", "覆盖文件失败: $e")
        false
    }
}

// ─── 主界面 ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTimeModifierApp() {
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var fileNames by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTime by remember { mutableStateOf(LocalTime.now()) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
            fileNames = uris.joinToString(", ") { uri ->
                getFileName(context, uri) ?: uri.lastPathSegment ?: "未知文件"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = fileNames,
            onValueChange = {},
            label = { Text("已选文件") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { fileLauncher.launch("*/*") }) {
                Text("浏览")
            }
            Button(onClick = {
                if (selectedUris.isEmpty()) {
                    Toast.makeText(context, "请先选择文件", Toast.LENGTH_SHORT).show()
                } else {
                    showDatePicker = true
                }
            }) {
                Text("设置时间")
            }
        }
    }

    // 日期选择
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 时间选择
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                    showConfirmDialog = true
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }

    // 最终确认
    if (showConfirmDialog) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val timeStr = LocalDateTime.of(selectedDate, selectedTime).format(formatter)
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认修改") },
            text = {
                Text("将所选文件的修改时间设置为：\n$timeStr\n\n（注：创建时间受系统限制无法直接修改）")
            },
            confirmButton = {
                TextButton(onClick = {
                    val timestamp = LocalDateTime.of(selectedDate, selectedTime)
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

                    var successCount = 0
                    selectedUris.forEach { uri ->
                        // 尝试直接路径修改
                        val path = getRealPathFromUri(context, uri)
                        if (path != null) {
                            val file = File(path)
                            val result = file.setLastModified(timestamp)
                            Log.d("TimeModifier", "直接修改 $path -> $result")
                            if (result) {
                                successCount++
                                // 异步扫描，并在扫描后更新数据库精确时间
                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(path),
                                    null
                                ) { scannedPath, mediaUri ->
                                    if (mediaUri != null) {
                                        updateMediaStoreTimestamp(context, mediaUri, timestamp)
                                    }
                                }
                                return@forEach
                            }
                        }

                        // 路径获取失败，使用复制-修改-覆盖
                        Log.d("TimeModifier", "尝试复制方式处理 $uri")
                        val tempFile = copyUriToTempFile(context, uri)
                        if (tempFile != null) {
                            if (tempFile.setLastModified(timestamp)) {
                                if (writeFileBackToUri(context, uri, tempFile)) {
                                    successCount++
                                    // 覆盖后通知 ContentResolver 变化，并更新数据库
                                    context.contentResolver.notifyChange(uri, null)
                                    updateMediaStoreTimestamp(context, uri, timestamp)
                                }
                            }
                            tempFile.delete()
                        }
                    }
                    showConfirmDialog = false
                    Toast.makeText(context, "已处理 $successCount/${selectedUris.size} 个文件", Toast.LENGTH_SHORT).show()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("取消") }
            }
        )
    }
}

fun updateMediaStoreTimestamp(context: Context, mediaUri: Uri, timestamp: Long) {
    try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000) // 单位：秒
        }
        // 如果是图片或视频，也尝试更新 DATE_TAKEN（拍摄时间）
        val isImage = mediaUri.toString().contains("image", ignoreCase = true)
        val isVideo = mediaUri.toString().contains("video", ignoreCase = true)
        if (isImage || isVideo) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
        }
        context.contentResolver.update(mediaUri, values, null, null)
        Log.d("TimeModifier", "MediaStore 更新成功: $mediaUri")
    } catch (e: Exception) {
        Log.e("TimeModifier", "MediaStore 更新失败: $e")
    }
}