package com.example.ojtlog

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.core.animate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

class ProgressFragment: Fragment(R.layout.progress_fragment) {

    // region Variables

    lateinit var estimatedDateTxt: TextView
    lateinit var daysLeftTxt: TextView
    lateinit var hrsLeftTxt: TextView
    lateinit var progressBarCircle: ProgressBar
    lateinit var progressPercentageTxt: TextView


    var estimatedDate:String = ""
    var daysLeft:String = ""
    var hrsLeft:String = ""
    var goal:Int = 0
    var totalHoursCompleted:Int = 0
    var progressPercentage:Double = 0.0

    lateinit var start:String

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

    // endregion


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        estimatedDateTxt = view.findViewById<TextView>(R.id.estimatedDayTxt)
        daysLeftTxt = view.findViewById<TextView>(R.id.progress_daysLeftTxt)
        hrsLeftTxt = view.findViewById<TextView>(R.id.progress_hrsLeftTxt)
        progressBarCircle = view.findViewById<ProgressBar>(R.id.progressBarCircle)
        progressPercentageTxt = view.findViewById<TextView>(R.id.progress_percentageTxt)

        lifecycleScope.launch {
            fetchData()
            populateUI()
        }
    }

    suspend fun fetchData(){

        val profile = profileRef.get().await()
        val settings = settingsRef.get().await()
        val missedDays = missedDaysRef.get().await()

        goal = profile.getLong("goalHrs")!!.toInt()
        val dailyWorkHrs = settings.getLong("workHrs")!!.toInt()
        start = profile.get("startDate")!!.toString()
        val missed = missedDays.get("missedDays") as? List<String> ?: emptyList()
        val estimatedDay = getEstimatedDay(goal, dailyWorkHrs)
        totalHoursCompleted = profile.getLong("totalHrsCompleted")!!.toInt()

        if(start.isNullOrEmpty()){
            Toast.makeText(requireContext(), "0 Logs detected, Please add a Log first", Toast.LENGTH_LONG).show()

            val fragment = HomeFragment()

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerView, fragment)
                .addToBackStack(null)
                .commit()


            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_home
        }else{
            estimatedDate = calculateBusinessFinishDate(estimatedDay, start, missed)
            daysLeft = calculateDaysLeft(goal,dailyWorkHrs,totalHoursCompleted).toString()
            hrsLeft = profile.getLong("hrsLeft")!!.toInt().toString()
            progressPercentage = calculatePercentage(totalHoursCompleted, goal)
        }
    }

    fun populateUI(){
        progressBarCircle.max = goal
        updateProgressSmoothly(progressBarCircle, totalHoursCompleted, 2000)
        progressPercentageTxt.text = "%.2f".format(progressPercentage)
        estimatedDateTxt.text = estimatedDate
        daysLeftTxt.text = daysLeft
        hrsLeftTxt.text = hrsLeft
    }

    fun updateProgressSmoothly(progressBar: ProgressBar, targetValue: Int, timeInMillis: Long) {
        val animation = ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, targetValue)

        // ADJUST SPEED HERE: higher number = slower/smoother
        animation.duration = timeInMillis

        // Use Decelerate for a "natural" landing
        animation.interpolator = DecelerateInterpolator()

        animation.start()
    }

    fun calculateBusinessFinishDate(daysNeeded: Int, startDateStr: String, missedDays: List<String>): String {
        var daysNeeded = daysNeeded

        val inputFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
        val outputFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH)

        var currentDate = LocalDate.parse(startDateStr, inputFormatter)
        val skippedDates = missedDays.map { LocalDate.parse(it, inputFormatter) }.toSet()

        while (daysNeeded > 0) {
            val isWeekend = currentDate.dayOfWeek == DayOfWeek.SATURDAY ||
                    currentDate.dayOfWeek == DayOfWeek.SUNDAY
            val isMissedDay = skippedDates.contains(currentDate)

            if (!isWeekend && !isMissedDay) {
                daysNeeded -= 1
            }

            if (daysNeeded > 0) {
                currentDate = currentDate.plusDays(1)
            }
        }

        return currentDate.format(outputFormatter)
    }

    fun calculatePercentage(progress: Int, goal: Int): Double {
        if (goal == 0) return 0.0
        return (progress.toDouble() / goal.toDouble()) * 100
    }

    fun getEstimatedDay(goalHours:Int, workHours:Int):Int{
        return ceil(goalHours.toDouble() / workHours).toInt()
    }


    fun calculateDaysLeft(goalHours: Int, workHours: Int, hoursAlreadyWorked: Int): Int {
        val totalDaysNeeded = ceil(goalHours.toDouble() / workHours).toInt()

        val daysCompleted = hoursAlreadyWorked / workHours

        val daysLeft = totalDaysNeeded - daysCompleted

        //  Ensure we don't return a negative number if you overwork
        return if (daysLeft > 0) daysLeft else 0
    }
}

