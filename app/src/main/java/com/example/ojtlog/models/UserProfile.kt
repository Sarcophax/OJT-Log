package com.example.ojtlog.models

import java.time.LocalDate

data class UserProfile(
    val goalHrs: Int? = 0,
    val totalHrsCompleted: Int? = 0,
    val hrsLeft: Int = 0,
    val startDate: String? = "",
)
