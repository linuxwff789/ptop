package com.linuxwff.ptop

import android.app.Activity
import android.app.Application
import android.os.Bundle

class PtopApplication : Application() {

    /** Number of started activities; 0 means the UI is not visible. */
    @Volatile
    var visible = 0
        private set
    /** One sampler for the whole process: survives activity recreation and the
     *  background service, so counters keep flowing when "后台运行" is picked. */
    val monitor: Monitor by lazy { Monitor(this) }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                visible++
            }

            override fun onActivityStopped(activity: Activity) {
                visible = (visible - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
