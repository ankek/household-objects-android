package dev.hho.android.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

internal fun inMemoryHhoDatabase(): HhoDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        HhoDatabase::class.java,
    ).build()

internal fun fileHhoDatabase(name: String): HhoDatabase =
    Room.databaseBuilder(
        ApplicationProvider.getApplicationContext(),
        HhoDatabase::class.java,
        name,
    ).build()

internal fun deleteHhoDatabaseFile(name: String) {
    val context: android.content.Context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(name)
}
