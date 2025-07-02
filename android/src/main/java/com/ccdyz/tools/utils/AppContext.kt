package com.ccdyz.tools.utils

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AppContext {
    private var context: Context? = null
    
    fun init(context: Context) {
        this.context = context.applicationContext
    }
    
    fun get(): Context {
        return context ?: throw IllegalStateException("AppContext not initialized")
    }
}