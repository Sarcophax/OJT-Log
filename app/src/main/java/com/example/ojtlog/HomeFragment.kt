package com.example.ojtlog

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.core.snap
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.ojtlog.models.FirestoreManager
import com.example.ojtlog.models.FirestoreManager.saveQuickLogData
import com.example.ojtlog.models.FirestoreManager.saveUserProfile
import com.example.ojtlog.models.SettingsData
import com.example.ojtlog.models.UserProfile
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import androidx.core.content.edit
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await


class HomeFragment: Fragment(R.layout.home_fragment) {

//    region Variables
    lateinit var goalHrsTxt: TextView
    lateinit var hrsLeftTxt: TextView
    lateinit var completedHrsTxt: TextView
    lateinit var quickLogBtn:Button
    lateinit var manualLogBtn:Button

    var userSettingsFinished:Boolean = false

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

//    endregion

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        quickLogBtn = view.findViewById<Button>(R.id.home_quickLogBtn)
        manualLogBtn = view.findViewById<Button>(R.id.home_manualLogBtn)

        goalHrsTxt = view.findViewById<TextView>(R.id.home_goalHrsTxt)
        hrsLeftTxt = view.findViewById<TextView>(R.id.home_hrsLeftTxt)
        completedHrsTxt = view.findViewById<TextView>(R.id.home_completeHrsBtn)

        lifecycleScope.launch {
            populateHomeUI()
            userSettingsFinished = checkSettingsExistence()
        }

        quickLogBtn.setOnClickListener {
            handleQuickLog()
        }

        manualLogBtn.setOnClickListener {

            if(userSettingsFinished == false){
                Toast.makeText(requireContext(), "Set Up your settings first!", Toast.LENGTH_LONG).show()

                val fragment = SettingsFragment()

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainerView, fragment)
                    .addToBackStack(null)
                    .commit()
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_settings
            }else{


                parentFragmentManager.setFragmentResultListener("log_request_key", viewLifecycleOwner) { _, _ ->
                    lifecycleScope.launch {
                        populateHomeUI()
                    }
                }

                val dialog = ManualLogFragment()
                dialog.show(parentFragmentManager, "MyFloatingLayout")
            }
        }
    }

    suspend fun checkSettingsExistence():Boolean{

        try {
            // FETCHING: The .await() pauses here until the network request finishes
            val settings = settingsRef.get().await()

            if (settings.exists()) {
                val istotalGoalHrsEmpty = settings.getLong("totalGoalHrs")!!.toInt()
                val isdailyInEmpty = settings.getString("dailyIn")
                val isdailyOutEmpty = settings.getString("dailyOut")

                if (istotalGoalHrsEmpty == null || isdailyInEmpty.isNullOrEmpty() || isdailyOutEmpty.isNullOrEmpty()){
                    return false
                }else{
                    return true
                }


            }
        } catch (e: Exception) {
            // ERROR: Handle network failures or permission issues
            return false
        }
        return false
    }

    private fun handleQuickLog() {

        if(userSettingsFinished == false){
                Toast.makeText(requireContext(), "Set Up your settings first!", Toast.LENGTH_LONG).show()

                val fragment = SettingsFragment()

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainerView, fragment)
                    .addToBackStack(null)
                    .commit()


                val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
                bottomNav.selectedItemId = R.id.nav_settings
        }else{

            val sharedPref = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val today = SimpleDateFormat("MM-dd-yyyy", Locale.getDefault()).format(Date())
            val lastDate = sharedPref.getString("last_log_date", "")

            if (lastDate == today) {
                Toast.makeText(requireContext(), "You already logged today!", Toast.LENGTH_SHORT).show()
                return
            }

            lifecycleScope.launch {
                val profile = profileRef.get().await()
                val settings = settingsRef.get().await()
                try {
                    val dailyIn = settings?.get("dailyIn").toString()
                    val dailyOut = settings.get("dailyOut").toString()
                    val goal = settings.getLong("totalGoalHrs")!!.toInt()
                    val previousCompleted = profile.getLong("totalHrsCompleted")!!.toInt()

                    val workHrs = calculateWorkHours(dailyIn, dailyOut)
                    val latestCompleted = previousCompleted + workHrs
                    val hrsLeft = abs(latestCompleted - goal)

                saveQuickLogData(timeIn = dailyIn, timeOut = dailyOut, workHrs = workHrs)
                saveUserProfile(goal, latestCompleted, hrsLeft, today)

                populateHomeUI()
                sharedPref.edit().putString("last_log_date", today).apply()
                Toast.makeText(requireContext(), "Log successful for $today!", Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    suspend fun populateHomeUI() {
        val snapshot = profileRef.get().await()
        val goalHrsCache = snapshot.getLong("goalHrs")!!.toInt().toString()
        val hrsLeftCache = snapshot.getLong("hrsLeft")!!.toInt().toString()
        val completedHrsCache = snapshot.getLong("totalHrsCompleted")!!.toInt().toString()

        if (snapshot.exists()) {
            goalHrsTxt.text = "$goalHrsCache Hrs"
            hrsLeftTxt.text = "$hrsLeftCache Hrs Left"
            completedHrsTxt.text = "$completedHrsCache Hrs"
        }
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

    private fun convertTimeToMinutes(timeStr: String): Int {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val date = sdf.parse(timeStr) ?: return 0
        val calendar = Calendar.getInstance()
        calendar.time = date

        return (calendar.get(Calendar.HOUR_OF_DAY) * 60) + calendar.get(Calendar.MINUTE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.onSettingsUpdated = null
    }
}