package com.example.ojtlog.models

import com.google.firebase.firestore.FieldValue

data class QuickLogData(
    val calculatedWorkHrs: Int = 0,
    val currentDate: FieldValue,
    val timeIn: String = "",
    val timeOut: String = "",
    val entryType:String = "",
)
