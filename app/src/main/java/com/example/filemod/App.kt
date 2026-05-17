package com.example.filemod

import android.content.ContentValues
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
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
    if (android.content.ContentResolver.SCHEME_FILE == uri.scheme) {
        return uri.path
    }
    if (DocumentsContract.isDocumentUri(context, uri)) {
        val docId = DocumentsContract.getDocumentId(uri)
        val decodedId = Uri.decode(docId)
        val split = decodedId.split(":").toTypedArray()
        val type = split[0]
        val id = if (split.size > 1) split[1] else ""
        if ("primary".equals(type, ignoreCase = true)) {
            return "${Environment.getExternalStorageDirectory().absolutePath}/$id"
        }
        if (type == "image" || type == "video" || type == "audio") {
            val contentUri = when (type) {
                "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val selection = "_id=?"
            context.contentResolver.query(contentUri, arrayOf(MediaStore.MediaColumns.DATA), selection, arrayOf(id), null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                }
            }
        }
    }
    try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                if (!path.isNullOrEmpty()) return path
            }
        }
    } catch (_: Exception) {}
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

fun updateMediaStoreTimestamp(context: Context, mediaUri: Uri, timestamp: Long) {
    try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
        }
        val isImage = mediaUri.toString().contains("image", true)
        val isVideo = mediaUri.toString().contains("video", true)
        if (isImage || isVideo) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
        }
        context.contentResolver.update(mediaUri, values, null, null)
    } catch (e: Exception) {
        Log.e("TimeModifier", "MediaStore更新失败", e)
    }
}

/**
 * 通过创建临时文件、移动原文件、重命名来修改文件时间
 * 1. 在同级目录创建 .原文件名.tmp
 * 2. 复制内容并设置目标时间
 * 3. 原文件移动到 DCIM/.Trash
 * 4. 临时文件重命名为原文件名
 */
fun modifyFileByCreateTemp(context: Context, uri: Uri, targetTimeMillis: Long): Boolean {
    val originalPath = getRealPathFromUri(context, uri) ?: return false
    val originalFile = File(originalPath)
    if (!originalFile.exists()) return false

    val parentDir = originalFile.parentFile ?: return false
    val tempFileName = ".${originalFile.name}.tmp"
    val tempFile = File(parentDir, tempFileName)

    try {
        // 1. 复制原文件内容到临时文件
        originalFile.inputStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        // 2. 设置临时文件的最后修改时间
        if (!tempFile.setLastModified(targetTimeMillis)) {
            Log.e("TimeModifier", "设置临时文件时间失败")
            tempFile.delete()
            return false
        }

        // 3. 移动原文件到回收站
        val trashDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            ".Trash"
        )
        if (!trashDir.exists()) trashDir.mkdirs()

        val trashFile = if (File(trashDir, originalFile.name).exists()) {
            File(trashDir, "${originalFile.nameWithoutExtension}_${System.currentTimeMillis()}.${originalFile.extension}")
        } else {
            File(trashDir, originalFile.name)
        }

        if (!originalFile.renameTo(trashFile)) {
            Log.e("TimeModifier", "移动原文件到回收站失败")
            tempFile.delete()
            return false
        }

        // 4. 临时文件重命名为原文件名
        if (!tempFile.renameTo(originalFile)) {
            Log.e("TimeModifier", "重命名临时文件失败，尝试恢复原文件")
            trashFile.renameTo(originalFile)
            tempFile.delete()
            return false
        }

        // 5. 只做媒体扫描，不手动删除原 URI，避免权限问题
        MediaScannerConnection.scanFile(
            context,
            arrayOf(originalFile.absolutePath),
            null
        ) { _, mediaUri ->
            if (mediaUri != null) {
                updateMediaStoreTimestamp(context, mediaUri, targetTimeMillis)
            }
        }

        return true
    } catch (e: Exception) {
        Log.e("TimeModifier", "操作失败", e)
        if (tempFile.exists()) tempFile.delete()
        return false
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
    var modifyMode by remember { mutableStateOf("") } // "direct" 或 "copy"
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

            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = {
                    if (selectedUris.isEmpty()) {
                        Toast.makeText(context, "请先选择文件", Toast.LENGTH_SHORT).show()
                    } else {
                        modifyMode = "direct"
                        showDatePicker = true
                    }
                }) {
                    Text("修改源文件时间")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = {
                    if (selectedUris.isEmpty()) {
                        Toast.makeText(context, "请先选择文件", Toast.LENGTH_SHORT).show()
                    } else {
                        modifyMode = "copy"
                        showDatePicker = true
                    }
                }) {
                    Text("创建文件修改时间")
                }
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

    // 最终确认对话框
    if (showConfirmDialog) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val timeStr = LocalDateTime.of(selectedDate, selectedTime).format(formatter)
        val modeText = if (modifyMode == "direct") {
            "（直接修改源文件的最后修改时间）"
        } else {
            "（原文件将移至 DCIM/.Trash，并创建新文件，其修改时间设为 $timeStr）"
        }

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认修改") },
            text = { Text("将所选文件的时间设置为：\n$timeStr\n$modeText") },
            confirmButton = {
                TextButton(onClick = {
                    val timestamp = LocalDateTime.of(selectedDate, selectedTime)
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

                    var successCount = 0
                    selectedUris.forEach { uri ->
                        when (modifyMode) {
                            "direct" -> {
                                val path = getRealPathFromUri(context, uri)
                                if (path != null) {
                                    val file = File(path)
                                    if (file.setLastModified(timestamp)) {
                                        successCount++
                                        MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, mediaUri ->
                                            if (mediaUri != null) updateMediaStoreTimestamp(context, mediaUri, timestamp)
                                        }
                                    }
                                }
                            }
                            "copy" -> {
                                val ok = modifyFileByCreateTemp(context, uri, timestamp)
                                if (ok) successCount++
                            }
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