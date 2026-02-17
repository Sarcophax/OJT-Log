package com.example.ojtlog.models

import android.util.Log
import androidx.compose.material3.TimePickerState
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
//import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

object FirestoreManager {
        private val db = Firebase.firestore
        private val auth = Firebase.auth

//    private var profileListener: ListenerRegistration? = null

    fun <T : Any> saveSettings(data: T, onComplete: (Boolean) -> Unit) {
            val userId = auth.currentUser?.uid

            if (userId != null) {
                db.collection("users")
                    .document(userId) // Uses the unique Anonymous ID
                    .collection("userData")
                    .document("settings")
                    .set(data)
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { onComplete(false) }
            } else {
                onComplete(false)
            }
        }

    fun <T : Any> saveProfile(data: T, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            db.collection("users")
                .document(userId) // Uses the unique Anonymous ID
                .collection("userData")
                .document("userProfile")
                .set(data)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        } else {
            onComplete(false)
        }
    }

    fun fetchSettings(onSuccess: (SettingsData?) -> Unit) {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            db.collection("users").document(userId).collection("userData").document("settings")
                .get()
                .addOnSuccessListener { document ->
                    // SIGNPOST: .toObject converts Firestore data back into your Kotlin class
                    val settings = document.toObject(SettingsData::class.java)
                    onSuccess(settings)
                }
                .addOnFailureListener {
                    onSuccess(null)
                }
        } else {
            onSuccess(null)
        }
    }

    fun listenToSettings(onResult: (SettingsData?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onResult(null)

        // SIGNPOST: .addSnapshotListener stays "alive"
        db.collection("users").document(userId).collection("userData").document("settings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val settings = snapshot.toObject(SettingsData::class.java)
                    onResult(settings)
                } else {
                    onResult(null)
                }
            }
    }

    fun listenToProfile(onResult: (UserProfile?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onResult(null)

        db.collection("users").document(userId).collection("userData").document("profile")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val userProfile = snapshot.toObject(UserProfile::class.java)
                    onResult(userProfile)
                } else {
                    onResult(null)
                }
            }
    }

    fun <T: Any>saveUserLogEntry(data: T, onComplete:(Boolean) -> Unit){
        val userId = auth.currentUser?.uid

        if (userId != null) {
            db.collection("users")
                .document(userId) // Uses the unique Anonymous ID
                .collection("userData")
                .document("userProfile")
                .set(data)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        } else {
            onComplete(false)
        }
    }

    fun fetchUserProfile(onSuccess: (SettingsData?) -> Unit) {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            db.collection("users").document(userId).collection("userData").document("userProfile")
                .get()
                .addOnSuccessListener { document ->
                    // SIGNPOST: .toObject converts Firestore data back into your Kotlin class
                    val settings = document.toObject(SettingsData::class.java)
                    onSuccess(settings)
                }
                .addOnFailureListener {
                    onSuccess(null)
                }
        } else {
            onSuccess(null)
        }
    }

    suspend fun saveUserProfile(goalHrs:Int, totalHrsCompleted: Int, hrsLeft: Int, startDate:String) {
        val userProfile = db.collection("users").document(auth.currentUser!!.uid)
            .collection("userData").document("profile")

        val snapshot = userProfile.get().await()

        var finalStartDate = startDate

        if (snapshot.exists()) {
            val existingDate = snapshot.getString("startDate")
            if (!existingDate.isNullOrEmpty()) {
                finalStartDate = existingDate
            }
        }

        val profileData = UserProfile(
            goalHrs = goalHrs,
            totalHrsCompleted = totalHrsCompleted,
            hrsLeft = hrsLeft,
            startDate = finalStartDate
        )
        userProfile.set(profileData).await()
    }


    suspend fun authChecking(){
        println("Auth Started")
        println("Current User: ${auth.currentUser}")
        if(auth.currentUser == null){

            auth.signInAnonymously().await()
            initializeUserDefaultData()
//            startDataCaching()
            println("USER DOESN'T EXIST - CREATED DONE!")
        }
    }

//    fun startDataCaching(){
//        val userId = auth.currentUser!!.uid
//        val userRef = db.collection("users").document(userId).collection("userData")
//
//        profileListener = userRef.document("profile").addSnapshotListener { snapshots, _ ->
//            println("Profile cached!")
//        }
//
//        profileListener = userRef.document("settings").addSnapshotListener { snapshots, _ ->
//            println("Settings cached!")
//        }
//
//        profileListener = userRef.document("missedDays").addSnapshotListener { snapshots, _ ->
//            println("missedDays cached!")
//        }
//    }

//    fun stopCaching(){
//        profileListener?.remove()
//    }

    suspend fun initializeUserDefaultData(){
        println("Initialization Started")

        val userId = auth.currentUser?.uid ?: return

        db.enableNetwork().await()

        val userRootRef = db.collection("users").document(userId)
        val userProfileRef = userRootRef.collection("userData").document("profile")
        val userSettingsRef = userRootRef.collection("userData").document("settings")

        val document = userProfileRef.get().await()
        if (!document.exists()) {
            val batch = db.batch()
            val timestampData = hashMapOf("createdAt" to FieldValue.serverTimestamp())

            batch.set(userRootRef,timestampData, SetOptions.merge())
            batch.set(userProfileRef, UserProfile(goalHrs = 0, totalHrsCompleted = 0, hrsLeft = 0,startDate = ""))
            batch.set(userSettingsRef, SettingsData(totalGoalHrs = 0, dailyIn = "", dailyOut = "", workingDays = emptyList()))

            batch.commit().await()
            println("Initialization Ended")
        }
    }

    suspend fun saveQuickLogData(timeIn: String, timeOut: String, workHrs: Int){
        val userProfile = db.collection("users").document(auth.currentUser!!.uid)
            .collection("Logs").document()

        val document = userProfile.get().await()
        if(!document.exists()){
            val batch = db.batch()
            batch.set(userProfile, LogEntry(workHrs, Timestamp.now(), timeIn, timeOut,  entryType = "regular"))
            batch.commit().await()
        }
    }

    suspend fun saveManualLogData(saveDate: Timestamp, timeIn: String, timeOut: String, workHrs: Int, entryType:String){
        val userProfile = db.collection("users").document(auth.currentUser!!.uid)
            .collection("Logs").document()

        val document = userProfile.get().await()
        if(!document.exists()){
            val batch = db.batch()
            batch.set(userProfile, LogEntry(workHrs, saveDate, timeIn, timeOut, entryType = entryType))
            batch.commit().await()
        }
    }

    suspend fun saveMissedDays(missedDays:String){
        val missedDaysRef = db.collection("users").document(auth.currentUser!!.uid)
            .collection("userData").document("missedDays")

        try {
            missedDaysRef.set(
                mapOf("missed" to FieldValue.arrayUnion(missedDays)),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.e("FirestoreError", "Failed to update missed days", e)
        }
    }
}