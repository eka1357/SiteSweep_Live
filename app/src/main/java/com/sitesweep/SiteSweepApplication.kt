package com.sitesweep

import android.app.Application
import com.sitesweep.data.local.SiteSweepDatabase
import com.sitesweep.data.repository.SiteSweepRepository
import com.sitesweep.data.repository.SiteSweepRepositoryImpl

/**
 * Application class initializing offline database and repository singletons.
 */
class SiteSweepApplication : Application() {

    lateinit var database: SiteSweepDatabase
        private set

    lateinit var repository: SiteSweepRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = SiteSweepDatabase.getInstance(this)
        repository = SiteSweepRepositoryImpl(database)
    }
}
