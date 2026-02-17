package com.example.ojtlog

import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.ToggleButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.ojtlog.models.FirestoreManager
import com.example.ojtlog.models.SettingsData
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SettingsFragment: Fragment(R.layout.settings_fragment) {

//    region Variables

    private var dailyInValue: String = ""
    private var dailyOutValue: String = ""

    lateinit var totalGoalHrs: EditText
    lateinit var timeIn : EditText
    lateinit var timeOut : EditText
    lateinit var monBtn : ToggleButton
    lateinit var tueBtn  : ToggleButton
    lateinit var wedBtn  : ToggleButton
    lateinit var thuBtn  : ToggleButton
    lateinit var friBtn  : ToggleButton
    lateinit var satBtn  : ToggleButton
    lateinit var sunBtn  : ToggleButton
    lateinit var saveBtn:Button


    var totalGoalHrsCache:Int? = 0
    var workingDays:List<String>? = null



    private val db = Firebase.firestore
    private val auth = Firebase.auth
    val userId = auth.currentUser!!.uid

    val settingsRef = db.collection("users")
        .document(userId)
        .collection("userData")
        .document("settings")

//    endregion

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        totalGoalHrs = view.findViewById<EditText>(R.id.settings_totalGoalHrsEditTxt)
        timeIn = view.findViewById<EditText>(R.id.settings_dailyInEditTxt)
        timeOut = view.findViewById<EditText>(R.id.settings_dailyOutEditTxt)
        monBtn = view.findViewById<ToggleButton>(R.id.settings_toggleMonBtn)
        tueBtn = view.findViewById<ToggleButton>(R.id.settings_toggleTueBtn)
        wedBtn = view.findViewById<ToggleButton>(R.id.settings_toggleWedBtn)
        thuBtn = view.findViewById<ToggleButton>(R.id.settings_toggleThuBtn)
        friBtn = view.findViewById<ToggleButton>(R.id.settings_toggleFriBtn)
        satBtn = view.findViewById<ToggleButton>(R.id.settings_toggleSatBtn)
        sunBtn = view.findViewById<ToggleButton>(R.id.settings_toggleSunBtn)
        saveBtn = view.findViewById<Button>(R.id.settings_saveBtn)

        lifecycleScope.launch {
            fetchSettingsData()
            populateSettingsUI()
        }

        timeIn.setOnClickListener {
            showTimePicker(timeIn) { _, timeString24h ->
                dailyInValue = timeString24h
            }
        }

        timeOut.setOnClickListener {
            showTimePicker(timeOut) { _, timeString24h ->
                dailyOutValue = timeString24h
            }
        }


        saveBtn.setOnClickListener {
            saveBtn.isEnabled = false
            saveBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4B5563"))

            val dayButtons = mapOf(
                monBtn to "Mon",
                tueBtn to "Tue",
                wedBtn to "Wed",
                thuBtn to "Thu",
                friBtn to "Fri",
                satBtn to "Sat",
                sunBtn to "Sun"
            )

            val selectedDays = dayButtons.filter { it.key.isChecked }.values.toList()

            println("SELECTED DAYS: $selectedDays")

            val settings = SettingsData(
                totalGoalHrs = totalGoalHrs.text.toString()
                    .toIntOrNull(),
                dailyIn = dailyInValue,
                dailyOut = dailyOutValue,
                workHrs = calculateWorkHours(dailyInValue,dailyOutValue),
                workingDays = selectedDays
            )

            FirestoreManager.saveSettings(settings) { success ->
                if (success) {
                    saveBtn.isEnabled = true
                    saveBtn.backgroundTintList = null
                    Toast.makeText(requireContext(), "Settings Saved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Save Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showTimePicker(targetEditText: EditText, onTimeSelected: (Int, String) -> Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePicker = TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->

            val amPm = if (selectedHour < 12) "AM" else "PM"
            val displayHour = if (selectedHour % 12 == 0) 12 else selectedHour % 12
            val formattedTime = String.format("%02d:%02d %s", displayHour, selectedMinute, amPm)

            targetEditText.setText(formattedTime)

            val totalMinutes = (selectedHour * 60) + selectedMinute

            onTimeSelected(totalMinutes, formattedTime)

        }, hour, minute, false)

        timePicker.show()
    }

    private suspend fun fetchSettingsData(){
        val snapshot = settingsRef.get().await()
        totalGoalHrsCache = snapshot.getLong("totalGoalHrs")!!.toInt()
        dailyInValue = snapshot.get("dailyIn").toString()
        dailyOutValue = snapshot.get("dailyOut").toString()
        workingDays = snapshot.get("workingDays") as? List<String> ?: emptyList()
    }
    private suspend fun populateSettingsUI() {
        val snapshot = settingsRef.get().await()

        if(snapshot.exists()){
            totalGoalHrs.setText(totalGoalHrsCache.toString())
            timeIn.setText(dailyInValue)
            timeOut.setText(dailyOutValue)
        }

        val dayButtons = mapOf(
            monBtn to "Mon",
            tueBtn to "Tue",
            wedBtn to "Wed",
            thuBtn to "Thu",
            friBtn to "Fri",
            satBtn to "Sat",
            sunBtn to "Sun"
        )

        dayButtons.forEach { (button, dayName) ->
            button.isChecked = workingDays!!.contains(dayName)
        }
    }

    private fun calculateWorkHours(timeIn: String, timeOut: String): Int {
        val startMinutes = convertTimeToMinutes(timeIn)
        val endMinutes = convertTimeToMinutes(timeOut)
        val lunchBreak = 1

        val diff = if (endMinutes >= startMinutes) {
            endMinutes - startMinutes
        } else {
            (1440 - startMinutes) + endMinutes
        }

        return (diff / 60) - lunchBreak
    }

    private fun convertTimeToMinutes(timeStr: String): Int {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val date = sdf.parse(timeStr) ?: return 0
        val calendar = Calendar.getInstance()
        calendar.time = date

        return (calendar.get(Calendar.HOUR_OF_DAY) * 60) + calendar.get(Calendar.MINUTE)
    }

}
