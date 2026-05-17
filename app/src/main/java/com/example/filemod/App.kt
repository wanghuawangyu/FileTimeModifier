package com.example.filemod

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
fun App() {
    var targetFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var pickerVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (pickerVisible && targetFiles.isNotEmpty()) {
        DateTimePickerScreen(
            onConfirm = { newTimestamp ->
                targetFiles.forEach { file ->
                    val success = TimeModifier.setFileTimes(file.absolutePath, newTimestamp, -1)
                    Log.i("TimeModifier", "Modified ${file.name}: $success")
                }
                pickerVisible = false
                Toast.makeText(context, "修改完成", Toast.LENGTH_SHORT).show()
            },
            onCancel = { pickerVisible = false }
        )
    } else {
        FileBrowserScreen(onFilesSelected = { files ->
            targetFiles = files
            pickerVisible = true
        })
    }
}