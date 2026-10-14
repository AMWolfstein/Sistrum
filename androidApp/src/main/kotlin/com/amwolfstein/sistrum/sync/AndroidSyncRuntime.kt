package com.amwolfstein.sistrum.sync

import android.content.Context

/**
 * Process-lifetime holder for the local Room library store.
 *
 * Originally this object owned the desktop-sync runtime (MQTT handoff, foreground
 * data-sync service, progress notifications). With desktop sync removed it only
 * owns the local data layer that the MediaStore scanner, playback, lyrics, and
 * listening trackers all consume.
 */
internal object AndroidSyncRuntime {
    private lateinit var store: AndroidLibrarySyncStore
    private lateinit var appContext: Context
    private lateinit var database: SyncDatabase

    /** Room may create the file before the playback DataStore's first read. */
    @Volatile internal var libraryDatabaseExistedAtStart: Boolean? = null
        private set

    @Synchronized
    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        libraryDatabaseExistedAtStart = appContext.getDatabasePath("library-sync.db").exists()
        database = SyncDatabase.create(appContext)
        store = AndroidLibrarySyncStore(database, appContext.filesDir)
    }

    internal fun syncStore(): AndroidLibrarySyncStore = store

    internal fun database(): SyncDatabase = database
}
