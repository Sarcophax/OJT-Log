package com.example.ojtlog

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import com.example.ojtlog.models.FirestoreManager
import com.example.ojtlog.models.FirestoreManager.authChecking
import com.example.ojtlog.models.FirestoreManager.initializeUserDefaultData
//import com.example.ojtlog.models.FirestoreManager.startDataCaching
import com.example.ojtlog.models.SettingsData
import com.example.ojtlog.models.UserProfile
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    lateinit var themeToggleButton: MaterialButton
    lateinit var progress: ProgressBar
    lateinit var bottomNav: BottomNavigationView
    var onSettingsUpdated: ((SettingsData) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        progress = findViewById<ProgressBar>(R.id.main_progressBar)

        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            authChecking()
            onDataReady()
        }

        themeToggleButton = findViewById<MaterialButton>(R.id.themeToggleButton)

        bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                    true
                }

                R.id.nav_progress -> {
                    replaceFragment(ProgressFragment())
                    true
                }

                R.id.nav_history -> {
                    replaceFragment(HistoryFragment())
                    true
                }
                R.id.nav_settings -> {
                    replaceFragment(SettingsFragment())
                    true
                }
                else -> false
            }

    }

            themeToggleButton.addOnCheckedChangeListener { button, isChecked ->
                if(isChecked){
                    AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)
                }
                else{
                    AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)
                }
            }

    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerView, fragment)
            .commit()
    }

    private fun onDataReady(){
        progress.visibility = View.GONE
        replaceFragment(HomeFragment())
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        // Manual stop
//        FirestoreManager.stopCaching()
//    }

}