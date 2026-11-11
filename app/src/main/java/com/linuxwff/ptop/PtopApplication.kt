package com.linuxwff.ptop

import android.app.Application

class PtopApplication : Application() {
    /** One sampler for the whole process: survives activity recreation and the
     *  background service, so counters keep flowing when "后台运行" is picked. */
    val monitor: Monitor by lazy { Monitor(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
