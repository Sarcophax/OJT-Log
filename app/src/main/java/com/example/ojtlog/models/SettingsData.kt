package com.example.ojtlog.models

data class SettingsData (
    val totalGoalHrs: Int? = 0,
    val dailyIn: String = "",
    val dailyOut: String = "",
    val workHrs: Int? = 0,
    val workingDays: List<String> = emptyList(),
)
