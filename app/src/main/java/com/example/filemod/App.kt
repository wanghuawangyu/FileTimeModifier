package com.example.filemod

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
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
    // 如果是文件 scheme，直接返回路径
    if (android.content.ContentResolver.SCHEME_FILE.equals(uri.scheme)) {
        return uri.path
    }

    // 尝试 MediaStore 的 DATA 字段
    try {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return cursor.getString(columnIndex)
            }
        }
    } catch (_: Exception) {}

    // 处理文档 Uri
    if (DocumentsContract.isDocumentUri(context, uri)) {
        val docId = DocumentsContract.getDocumentId(uri)
        val split = docId.split(":").toTypedArray()
        if (split.size >= 2) {
            val type = split[0]
            val id = split[1]
            if ("primary".equals(type, ignoreCase = true)) {
                return "${Environment.getExternalStorageDirectory().absolutePath}/$id"
            }
        }
    }

    // 最后尝试直接获取路径
    return uri.path
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
                        selectedDate = millis.let {
                            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        }
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
                Text("将所选文件的修改时间和访问时间设置为：\n$timeStr\n\n（注：创建时间受系统限制无法直接修改）")
            },
            confirmButton = {
                TextButton(onClick = {
                    val timestamp = LocalDateTime.of(selectedDate, selectedTime)
                        .toInstant(ZoneOffset.UTC).toEpochMilli()

                    var successCount = 0
                    selectedUris.forEach { uri ->
                        val path = getRealPathFromUri(context, uri)
                        if (path != null) {
                            if (TimeModifier.setFileTimes(path, timestamp, timestamp)) {
                                successCount++
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