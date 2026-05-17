package com.example.filemod

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun DateTimePickerScreen(
    onConfirm: (Long) -> Unit,
    onCancel: () -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now()) }

    val timestamp = remember(date, time) {
        date.atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    Button(onClick = { onConfirm(timestamp) }) { Text("确认修改") }
    Button(onClick = onCancel) { Text("取消") }
}