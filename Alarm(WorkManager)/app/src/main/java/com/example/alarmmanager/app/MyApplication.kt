package com.example.alarmmanager.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner

class MyApplication : Application() {


    companion object {
        private var sApplication: Application? = null

        fun getApplication(): Application? {
            return sApplication
        }

        fun getContext(): Context {
            return getApplication()!!.applicationContext
        }

        fun isActivityVisible(): String {
            return ProcessLifecycleOwner.get().lifecycle.currentState.name
        }
    }

    override fun onCreate() {
        super.onCreate()
    }
}