package com.example.ojtlog

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.ojtlog.models.FirestoreManager.saveManualLogData
import com.example.ojtlog.models.FirestoreManager.saveMissedDays
import com.example.ojtlog.models.FirestoreManager.saveQuickLogData
import com.example.ojtlog.models.FirestoreManager.saveUserProfile
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections.emptyList
import java.util.Date
import java.util.Locale
import kotlin.math.abs


class ManualLogFragment : DialogFragment(){

// region Variables
    var startTimeValue:String = ""
    var endTimeValue:String = ""
    var dateValue:Timestamp? = null
    var entryTypeValue:String = "regular"

    var tabPosition:Int = 0

    var workHrs = 0
    var latestCompleted = 0
    var hrsLeft =  0

    lateinit var startTime : EditText
    lateinit var endTime : EditText
    lateinit var dateTxt : EditText
    lateinit var tabLayout: TabLayout
    lateinit var timeLayout: LinearLayout

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    val userId = auth.currentUser!!.uid

    val settingsRef = db.collection("users")
        .document(userId)
        .collection("userData")
        .document("settings")

    val profileRef = db.collection("users")
        .document(userId)
        .collection("userData")
        .document("profile")

    val missedDaysRef = db.collection("users")
        .document(userId)
        .collection("userData")
        .document("missedDays")

//    endregion

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Send a signal that we are closing
        parentFragmentManager.setFragmentResult("log_request_key", bundleOf())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.home_manual_log_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val closeBtn = view.findViewById<Button>(R.id.button_close)

        tabLayout = view.findViewById<TabLayout>(R.id.home_manual_log_TabLayout) // Ensure your XML has this ID
        timeLayout = view.findViewById<LinearLayout>(R.id.layout_time_inputs)
        startTime = view.findViewById<EditText>(R.id.home_manual_log_startTime)
        endTime = view.findViewById<EditText>(R.id.home_manual_log_endTime)
        dateTxt = view.findViewById<EditText>(R.id.home_manual_log_date)
        val saveBtn = view.findViewById<Button>(R.id.home_manual_log_saveBtn)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // MANUAL
                        timeLayout.visibility = View.VISIBLE
                        entryTypeValue = "regular"
                        tabPosition = 0
                    }
                    1 -> { // ABSENT
                        timeLayout.visibility = View.GONE
                        entryTypeValue = "absent"
                        tabPosition = 1
                    }
                    2 -> { // HOLIDAY
                        timeLayout.visibility = View.GONE
                        entryTypeValue = "holiday"
                        tabPosition = 2
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })


        closeBtn.setOnClickListener {
            dismiss()
        }

        dateTxt.setOnClickListener {
                showDatePicker { selectedDate ->
                    dateValue = selectedDate

                    val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                    val displayDate = sdf.format(selectedDate.toDate())

                    dateTxt.setText(displayDate)
                }
        }

        startTime.setOnClickListener {
            showTimePicker(startTime) { _, timeString24h ->
                startTimeValue = timeString24h
            }
        }

        endTime.setOnClickListener {
            showTimePicker(endTime) { _, timeString24h ->
                endTimeValue = timeString24h
            }
        }

        saveBtn.setOnClickListener {

            saveBtn.isEnabled = false
            saveBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4B5563"))

            if(timeLayout.isVisible){
                if(startTimeValue != "" && endTimeValue != "" && dateValue != null){
                    lifecycleScope.launch {
                        handleManualLog(dateValue)
                        resetUiAndValue()

                        saveBtn.isEnabled = true
                        saveBtn.backgroundTintList = null
                    }
                }
            }

            if(timeLayout.isGone && tabPosition == 1){
                if(dateValue != null){
                    lifecycleScope.launch {
                            handleManualLog(dateValue)
                            val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                            val missedDay = sdf.format(dateValue!!.toDate())
                            saveMissedDays(missedDay)
                            resetUiAndValue()
                            saveBtn.isEnabled = true
                            saveBtn.backgroundTintList = null
                    }
                }
            }

            if(timeLayout.isGone && tabPosition == 2){
                if(dateValue != null){
                    lifecycleScope.launch {
                        handleManualLog(dateValue)
                        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                        val missedDay = sdf.format(dateValue!!.toDate())

                        saveMissedDays(missedDay)
                        resetUiAndValue()
                        saveBtn.isEnabled = true
                        saveBtn.backgroundTintList = null
                    }
                }
            }
        }
    }

   suspend fun handleManualLog(date:Timestamp?) {

            val profile = profileRef.get().await()
            val settings = settingsRef.get().await()

            val today = SimpleDateFormat("MM-dd-yyyy", Locale.getDefault()).format(Date())
            val previousCompleted = profile.getLong("totalHrsCompleted")!!.toInt()
            val goal = settings.getLong("totalGoalHrs")!!.toInt()


            try {
                if(timeLayout.isVisible){
                    workHrs = calculateWorkHours(startTimeValue, endTimeValue)
                    latestCompleted = previousCompleted + workHrs
                    hrsLeft = abs(latestCompleted - goal)
                    saveUserProfile(goal, latestCompleted, hrsLeft, today)
                }

                saveManualLogData(saveDate= date!!,timeIn = startTimeValue, timeOut = endTimeValue, workHrs = workHrs, entryType = entryTypeValue)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onStart() {
        super.onStart()
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun resetUiAndValue(){
        startTime.text.clear()
        endTime.text.clear()
        dateTxt.text.clear()
        startTimeValue = ""
        endTimeValue = ""
        dateValue = null
    }

    private fun showDatePicker(onDateSelected: (Timestamp) -> Unit) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->

                val selectedDate = Calendar.getInstance()
                selectedDate.set(selectedYear, selectedMonth, selectedDay)

                selectedDate.set(Calendar.HOUR_OF_DAY, 0)
                selectedDate.set(Calendar.MINUTE, 0)
                selectedDate.set(Calendar.SECOND, 0)
                selectedDate.set(Calendar.MILLISECOND, 0)

                val firebaseTimestamp = Timestamp(selectedDate.time)

                onDateSelected(firebaseTimestamp)
            },
            year, month, day
        )

        datePickerDialog.show()
    }

    private fun convertTimeToMinutes(timeStr: String): Int {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val date = sdf.parse(timeStr) ?: return 0
        val calendar = Calendar.getInstance()
        calendar.time = date

        return (calendar.get(Calendar.HOUR_OF_DAY) * 60) + calendar.get(Calendar.MINUTE)
    }

    fun calculateWorkHours(timeIn: String, timeOut: String): Int {
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
}