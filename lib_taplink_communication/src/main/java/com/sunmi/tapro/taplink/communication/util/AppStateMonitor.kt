package com.sunmi.tapro.taplink.communication.util

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Application state monitor
 * Monitors application foreground/background state changes, used to optimize heartbeat mechanism
 */
class AppStateMonitor private constructor() : DefaultLifecycleObserver, ComponentCallbacks2 {
    
    companion object {
        private const val TAG = "AppStateMonitor"
        
        @Volatile
        private var INSTANCE: AppStateMonitor? = null
        
        fun getInstance(): AppStateMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppStateMonitor().also { INSTANCE = it }
            }
        }
    }
    
    private var isAppInForeground = true
    private var isLowMemory = false
    private val stateChangeListeners = mutableSetOf<AppStateChangeListener>()
    
    /**
     * Initialize monitor
     */
    fun initialize(application: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        application.registerComponentCallbacks(this)
        LogUtil.d(TAG, "AppStateMonitor initialized")
    }
    
    /**
     * Add state change listener
     */
    fun addStateChangeListener(listener: AppStateChangeListener) {
        synchronized(stateChangeListeners) {
            stateChangeListeners.add(listener)
        }
    }
    
    /**
     * Remove state change listener
     */
    fun removeStateChangeListener(listener: AppStateChangeListener) {
        synchronized(stateChangeListeners) {
            stateChangeListeners.remove(listener)
        }
    }
    
    /**
     * Get current application state
     */
    fun getAppState(): AppState {
        return when {
            !isAppInForeground && isLowMemory -> AppState.BACKGROUND_LOW_MEMORY
            !isAppInForeground -> AppState.BACKGROUND
            isLowMemory -> AppState.FOREGROUND_LOW_MEMORY
            else -> AppState.FOREGROUND
        }
    }
    
    // Lifecycle callbacks
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        isAppInForeground = true
        LogUtil.d(TAG, "App moved to foreground")
        notifyStateChange(getAppState())
    }
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        isAppInForeground = false
        LogUtil.d(TAG, "App moved to background")
        notifyStateChange(getAppState())
    }
    
    // ComponentCallbacks2
    override fun onTrimMemory(level: Int) {
        val wasLowMemory = isLowMemory
        isLowMemory = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> true
            else -> false
        }
        
        if (wasLowMemory != isLowMemory) {
            LogUtil.d(TAG, "Memory state changed: isLowMemory=$isLowMemory, level=$level")
            notifyStateChange(getAppState())
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        // No special handling needed when configuration changes
    }
    
    override fun onLowMemory() {
        isLowMemory = true
        LogUtil.w(TAG, "Low memory warning received")
        notifyStateChange(getAppState())
    }
    
    private fun notifyStateChange(newState: AppState) {
        synchronized(stateChangeListeners) {
            stateChangeListeners.forEach { listener ->
                try {
                    listener.onAppStateChanged(newState)
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error notifying state change listener: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Application state enumeration
     */
    enum class AppState {
        FOREGROUND,                // Foreground normal state
        FOREGROUND_LOW_MEMORY,     // Foreground low memory state
        BACKGROUND,                // Background normal state
        BACKGROUND_LOW_MEMORY      // Background low memory state
    }
    
    /**
     * Application state change listener
     */
    interface AppStateChangeListener {
        fun onAppStateChanged(newState: AppState)
    }
}





