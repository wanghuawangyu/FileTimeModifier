package com.example.filemod

object TimeModifier {
    init {
        System.loadLibrary("rust_core")
    }

    external fun setFileTimes(path: String, newMtimeMs: Long, newAtimeMs: Long): Boolean
}