package com.example.filemod

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import java.io.File

@Composable
fun FileBrowserScreen(onFilesSelected: (List<File>) -> Unit) {
    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    val (files, setFiles) = remember { mutableStateOf<List<File>>(emptyList()) }
    val selectedFiles = remember { mutableStateListOf<File>() }

    LaunchedEffect(currentDir) {
        setFiles(currentDir.listFiles()?.toList() ?: emptyList())
    }

    Column {
        Row {
            Text(currentDir.absolutePath)
            if (currentDir != Environment.getExternalStorageDirectory()) {
                Button(onClick = { currentDir = currentDir.parentFile ?: currentDir }) {
                    Text("UP")
                }
            }
        }
        LazyColumn {
            items(files) { file ->
                Row(
                    Modifier.clickable {
                        if (file.isDirectory) currentDir = file
                        else {
                            if (selectedFiles.contains(file)) selectedFiles.remove(file)
                            else selectedFiles.add(file)
                        }
                    }
                ) {
                    Checkbox(checked = selectedFiles.contains(file), onCheckedChange = null)
                    Text(if (file.isDirectory) "[DIR] ${file.name}" else file.name)
                }
            }
        }
        Button(onClick = { onFilesSelected(selectedFiles.toList()) }) {
            Text("修改选中文件属性")
        }
    }
}