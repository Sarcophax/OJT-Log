package com.example.ojtlog.models

import com.google.firebase.Timestamp

data class LogEntry(
    val calculatedWorkHrs: Int = 0,
    val savedDate: Timestamp? = null,
    val timeIn: String = "",
    val timeOut: String = "",
    val entryType: String = "",
)
