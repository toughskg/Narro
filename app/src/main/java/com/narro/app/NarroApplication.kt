package com.narro.app

import android.app.Application
import androidx.room.Room
import com.narro.app.data.local.datastore.SettingsRepository
import com.narro.app.data.local.db.NarroDatabase
import com.narro.app.data.repository.DocumentRepositoryImpl
import com.narro.app.domain.repository.DocumentRepository
import com.narro.app.security.PinStore

class NarroApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

class AppGraph(application: Application) {
    val database: NarroDatabase = Room.databaseBuilder(
        application,
        NarroDatabase::class.java,
        "narro.db",
    ).build()

    val documents: DocumentRepository = DocumentRepositoryImpl(
        contentResolver = application.contentResolver,
        database = database,
        filesDir = application.filesDir,
    )
    val settings = SettingsRepository(application)
    val pinStore = PinStore(application)
}
