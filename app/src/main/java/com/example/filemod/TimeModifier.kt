package com.example.filemod

object TimeModifier {
    init {
        System.loadLibrary("rust_core")
    }

    // 同时设置修改时间和访问时间（毫秒时间戳）
    external fun setFileTimes(path: String, newMtimeMs: Long, newAtimeMs: Long): Boolean
}