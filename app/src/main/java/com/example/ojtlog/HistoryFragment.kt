package com.example.ojtlog

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ojtlog.models.FirestoreManager
import com.example.ojtlog.models.LogEntry
import com.example.ojtlog.models.SettingsData
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.example.ojtlog.LogAdapter
import kotlin.math.log

class HistoryFragment: Fragment(R.layout.history_fragment) {


    lateinit var recyclerView: RecyclerView
    val db = Firebase.firestore
    private val auth = Firebase.auth

    val userId = auth.currentUser!!.uid

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        lateinit var logCountTxt: TextView

        super.onViewCreated(view, savedInstanceState)


        logCountTxt = view.findViewById<TextView>(R.id.history_logCountTxt)

        recyclerView= view.findViewById<RecyclerView>(R.id.history_recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val logsList = mutableListOf<LogEntry>()
        val adapter = LogAdapter(logsList)
        recyclerView.adapter = adapter

        db.collection("users").document(userId).collection("Logs")
            .get()
            .addOnSuccessListener { documents ->
                val tempArrayList = mutableListOf<LogEntry>()

                for (document in documents) {
                    val log = document.toObject(LogEntry::class.java)
                    tempArrayList.add(log)
                }
                val sortedList = tempArrayList.sortedByDescending { it.savedDate }
                val logCount = sortedList.size
                logCountTxt.text = logCount.toString()
                adapter.updateData(sortedList)
            }

    }

}
