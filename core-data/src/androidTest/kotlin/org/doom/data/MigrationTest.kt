package org.doom.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.doom.database.DoomDatabase
import org.doom.database.MIGRATION_1_2
import org.doom.database.MIGRATION_2_3
import org.doom.database.MIGRATION_4_5
import org.doom.database.MIGRATION_5_6
import org.doom.database.MIGRATION_6_7
import org.doom.database.MIGRATION_7_8
import org.doom.database.MIGRATION_8_9
import org.doom.database.MIGRATION_3_4
import org.doom.model.ModStatus
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MigrationTest {
    @Test fun versionSixMigrationAddsDurableChangeJournal() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-v6-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        try {
            val source = instrumentation.context.assets.open("org.doom.database.DoomDatabase/6.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                val entities = source.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val table = entities.getJSONObject(i)
                    old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", table.getString("tableName")))
                    val indices = table.getJSONArray("indices")
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j)
                        .getString("createSql").replace("\${TABLE_NAME}", table.getString("tableName")))
                }
                source.getJSONArray("setupQueries").let { queries ->
                    for (i in 0 until queries.length()) old.execSQL(queries.getString(i))
                }
                old.execSQL("""INSERT INTO installed_mods
                    (id,name,version,packagePath,desiredEnabled,status,installedAt,lastError,description,minAndroidApi,
                     defaultColor,defaultIntensity,sha256,declaredCapabilities,author,license,minimumIntensity,
                     maximumIntensity,ringDefaults,optionDefinitions,settingDefinitions)
                    VALUES ('preserved.mod','Preserved','1','mods/preserved',0,'INSTALLED_DISABLED',1,NULL,'',31,
                    '#AABBCC',30,NULL,NULL,NULL,NULL,10,60,NULL,NULL,NULL)""")
                old.version = 6
            }
            val upgraded = Room.databaseBuilder(context, DoomDatabase::class.java, name)
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build()
            try {
                assertEquals("Preserved", upgraded.mods().record("preserved.mod")!!.mod.name)
                val columns = upgraded.openHelper.readableDatabase.query("PRAGMA table_info(change_transactions)")
                try { assertTrue(columns.count >= 13) } finally { columns.close() }
                val diagnosticColumns = upgraded.openHelper.readableDatabase.query("PRAGMA table_info(execution_diagnostics)")
                try { assertTrue(diagnosticColumns.count >= 12) } finally { diagnosticColumns.close() }
                val catalogColumns = upgraded.openHelper.readableDatabase.query("PRAGMA table_info(marketplace_catalog)")
                try { assertEquals(15, catalogColumns.count) } finally { catalogColumns.close() }
            } finally { upgraded.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun marketplaceCatalogCachePersistsAndCanBeTrimmed() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DoomDatabase::class.java).build()
        try {
            val entry = org.doom.database.MarketplaceCatalogEntity(
                modId = "community.catalogtest", name = "Catalog Test", summary = "Cached listing", category = "demo",
                developer = "Example Developer", version = "1.0.0", manifestSchema = 3, definitionVersion = 4,
                minAndroidApi = 31, capabilitiesJson = "[\"ui.preview\"]", compatibilityJson = "{}",
                sha256 = "a".repeat(64), publishedAt = "2026-11-20T00:00:00.000Z", versionId = "published-version-1", refreshedAt = 10
            )
            database.marketplaceCatalog().upsert(listOf(entry))
            assertEquals(entry, database.marketplaceCatalog().find(entry.modId))
            database.marketplaceCatalog().trim(0)
            assertNull(database.marketplaceCatalog().find(entry.modId))
        } finally { database.close() }
    }

    @Test fun additiveVersionFourMigrationPreservesSettingsAndGrants() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-v4-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        try {
            val source = instrumentation.context.assets.open("org.doom.database.DoomDatabase/4.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                val tables = source.getJSONArray("entities")
                for (i in 0 until tables.length()) {
                    val table = tables.getJSONObject(i)
                    val tableName = table.getString("tableName")
                    old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", tableName))
                    val indices = table.getJSONArray("indices")
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j)
                        .getString("createSql").replace("\${TABLE_NAME}", tableName))
                }
                val setup = source.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL("""INSERT INTO installed_mods
                    (id,name,version,packagePath,desiredEnabled,status,installedAt,description,minAndroidApi,defaultColor,defaultIntensity)
                    VALUES ('v4.mod','Existing Mod','1.0.0','mods/v4.mod.doommod',0,'INSTALLED_DISABLED',123,'Preserved',31,'#AABBCC',30)""")
                old.execSQL("""INSERT INTO mod_settings VALUES ('v4.mod','{"color":"#AABBCC","intensity":30}',456)""")
                old.execSQL("INSERT INTO capability_grants VALUES ('v4.mod','ui.preview',1,789)")
                old.version = 4
            }
            val upgraded = Room.databaseBuilder(context, DoomDatabase::class.java, name)
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build()
            try {
                val record = upgraded.mods().record("v4.mod")!!
                assertNull(record.mod.optionDefinitions)
                assertNull(record.mod.settingDefinitions)
                assertEquals("Existing Mod", record.mod.name)
                assertEquals(30, JSONObject(record.settings!!.settingsJson).getInt("intensity"))
                assertEquals(456L, record.settings!!.updatedAt)
                assertTrue(record.grants.single().approved)
                assertEquals(789L, record.grants.single().grantedAt)
            } finally { upgraded.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun preservesVersionOneLibraryAndChildren() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        try {
            val source = instrumentation.context.assets.open("org.doom.database.DoomDatabase/1.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                val entities = source.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val table = entities.getJSONObject(i)
                    val tableName = table.getString("tableName")
                    old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", tableName))
                    val indices = table.getJSONArray("indices")
                    for (j in 0 until indices.length()) {
                        old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName))
                    }
                }
                val setup = source.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL("INSERT INTO installed_mods VALUES ('old.mod','Old Mod','1.2.0',1,'Legacy',31,'#B48CFF',45)")
                old.execSQL("INSERT INTO mod_settings VALUES ('old.mod','color','#AABBCC')")
                old.execSQL("INSERT INTO capability_grants VALUES ('old.mod','overlay')")
                old.execSQL("INSERT INTO mod_execution_log VALUES (7,'old.mod',123,'renderer_failed')")
                old.version = 1
            }
            val upgraded = Room.databaseBuilder(context, DoomDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build()
            try {
                val record = upgraded.mods().record("old.mod")!!
                assertEquals("Old Mod", record.mod.name)
                assertEquals("mods/old.mod.doommod", record.mod.packagePath)
                assertEquals(ModStatus.ENABLE_REQUESTED, record.mod.status)
                assertTrue(record.mod.desiredEnabled)
                assertNull(record.mod.sha256)
                assertEquals(10, record.mod.minimumIntensity)
                assertEquals(60, record.mod.maximumIntensity)
                assertTrue(record.grants.single().approved)
                val settings = JSONObject(record.settings!!.settingsJson)
                assertEquals("#AABBCC", settings.getString("color"))
                assertEquals(45, settings.getInt("intensity"))
                val failure = upgraded.mods().logs("old.mod").single()
                assertEquals(7L, failure.id)
                assertEquals(123L, failure.timestamp)
                assertEquals("ERROR", failure.result)
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
