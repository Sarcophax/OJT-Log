package com.example.ojtlog.models

import com.google.firebase.Timestamp

data class ManualLog(
    val calculatedWorkHrs: Int = 0,
    val saveDate:String,
    val timeIn: String = "",
    val timeOut: String = "",
    val entryType: String = "",
)
