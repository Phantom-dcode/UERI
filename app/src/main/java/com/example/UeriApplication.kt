package com.example

import android.app.Application
import android.util.Log
import com.example.auth.FirebaseAuthManager

/**
 * Custom Application class for UERI.
 * Ensures critical infrastructure (including FirebaseAuthManager and persistent credentials)
 * is initialized with an active Application context as soon as the process starts.
 */
class UeriApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Initializing UERI Application runtime and FirebaseAuthManager")
        // Initialize FirebaseAuthManager with Application context immediately
        FirebaseAuthManager.getInstance(this)
    }

    companion object {
        private const val TAG = "UeriApplication"
    }
}
