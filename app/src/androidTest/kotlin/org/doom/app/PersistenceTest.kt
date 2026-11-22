package org.doom.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.doom.database.DoomDatabase
import org.doom.database.InstalledModEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class PersistenceTest {
    @Test fun desiredStateSurvivesReopeningDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "doom-test-${UUID.randomUUID()}.db"
        val record = InstalledModEntity("test.mod", "Test Mod", "1.0.0", "mods/test.mod.doommod", desiredEnabled = true)
        try {
            Room.databaseBuilder(context, DoomDatabase::class.java, name).build().useDatabase {
                it.mods().insert(record)
            }
            Room.databaseBuilder(context, DoomDatabase::class.java, name).build().useDatabase {
                assertEquals(listOf(record), it.mods().observeRecords().first().map { row -> row.mod })
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}

private inline fun DoomDatabase.useDatabase(block: (DoomDatabase) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}
