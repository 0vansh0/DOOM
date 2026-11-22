package org.doom.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Copy children before dropping the previous parent so foreign keys stay enabled.
        db.execSQL("CREATE TEMP TABLE legacy_settings AS SELECT * FROM mod_settings")
        db.execSQL("CREATE TEMP TABLE legacy_grants AS SELECT * FROM capability_grants")
        db.execSQL("CREATE TEMP TABLE legacy_log AS SELECT * FROM mod_execution_log")
        db.execSQL("DROP TABLE mod_settings")
        db.execSQL("DROP TABLE capability_grants")
        db.execSQL("DROP TABLE mod_execution_log")
        db.execSQL("ALTER TABLE installed_mods RENAME TO legacy_mods")
        db.execSQL("""CREATE TABLE installed_mods (
            id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, version TEXT NOT NULL,
            packagePath TEXT NOT NULL, desiredEnabled INTEGER NOT NULL, status TEXT NOT NULL,
            installedAt INTEGER NOT NULL, lastError TEXT, description TEXT NOT NULL,
            minAndroidApi INTEGER NOT NULL, defaultColor TEXT NOT NULL, defaultIntensity INTEGER NOT NULL
        )""")
        db.execSQL("""INSERT INTO installed_mods
            SELECT id, title, version, 'mods/' || id || '.doommod', enabled,
                CASE WHEN enabled = 1 THEN 'ENABLE_REQUESTED' ELSE 'INSTALLED_DISABLED' END,
                0, NULL, description, minAndroidApi, defaultColor, defaultIntensity
            FROM legacy_mods""")
        db.execSQL("""CREATE TABLE mod_settings (
            modId TEXT NOT NULL PRIMARY KEY, settingsJson TEXT NOT NULL, updatedAt INTEGER NOT NULL,
            FOREIGN KEY(modId) REFERENCES installed_mods(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )""")
        db.execSQL("""INSERT INTO mod_settings
            SELECT id, '{"color":"' || COALESCE(
                (SELECT value FROM legacy_settings WHERE modId = id AND key = 'color'), defaultColor
            ) || '","intensity":' || COALESCE(
                (SELECT value FROM legacy_settings WHERE modId = id AND key = 'intensity'), defaultIntensity
            ) || '}', 0 FROM legacy_mods WHERE EXISTS (
                SELECT 1 FROM legacy_settings WHERE modId = id
            )""")
        db.execSQL("""CREATE TABLE capability_grants (
            modId TEXT NOT NULL, capabilityId TEXT NOT NULL, approved INTEGER NOT NULL, grantedAt INTEGER NOT NULL,
            PRIMARY KEY(modId, capabilityId),
            FOREIGN KEY(modId) REFERENCES installed_mods(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )""")
        db.execSQL("INSERT INTO capability_grants SELECT modId, capability, 1, 0 FROM legacy_grants")
        db.execSQL("""CREATE TABLE mod_execution_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, modId TEXT NOT NULL, event TEXT NOT NULL,
            result TEXT NOT NULL, message TEXT, timestamp INTEGER NOT NULL,
            FOREIGN KEY(modId) REFERENCES installed_mods(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )""")
        db.execSQL("""INSERT INTO mod_execution_log
            SELECT sequence, modId, event, CASE WHEN event = 'renderer_failed' THEN 'ERROR' ELSE 'LEGACY' END,
                NULL, timeMillis FROM legacy_log""")
        db.execSQL("CREATE INDEX index_mod_execution_log_modId ON mod_execution_log(modId)")
        db.execSQL("DROP TABLE legacy_mods")
        db.execSQL("DROP TABLE legacy_settings")
        db.execSQL("DROP TABLE legacy_grants")
        db.execSQL("DROP TABLE legacy_log")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE installed_mods ADD COLUMN sha256 TEXT")
        db.execSQL("ALTER TABLE installed_mods ADD COLUMN declaredCapabilities TEXT")
        db.execSQL("ALTER TABLE installed_mods ADD COLUMN author TEXT")
        db.execSQL("ALTER TABLE installed_mods ADD COLUMN license TEXT")
        db.execSQL("ALTER TABLE installed_mods ADD COLUMN minimumIntensity INTEGER NOT NULL DEFAULT 10")
        db.execSQL("ALTER TABLE installed_mods ADD COLUMN maximumIntensity INTEGER NOT NULL DEFAULT 60")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE installed_mods ADD COLUMN ringDefaults TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE installed_mods ADD COLUMN optionDefinitions TEXT")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE installed_mods ADD COLUMN settingDefinitions TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS change_transactions (
            transactionId TEXT NOT NULL PRIMARY KEY,
            modId TEXT NOT NULL,
            operationId TEXT NOT NULL,
            operationVersion INTEGER NOT NULL,
            targetIdentity TEXT NOT NULL,
            originalState TEXT NOT NULL,
            requestedState TEXT NOT NULL,
            appliedState TEXT,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            status TEXT NOT NULL,
            failureReason TEXT,
            recoveryMetadata TEXT
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_change_transactions_targetIdentity_status ON change_transactions(targetIdentity, status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_change_transactions_modId ON change_transactions(modId)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS execution_diagnostics (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            timestamp INTEGER NOT NULL,
            modId TEXT NOT NULL,
            runtimeSessionId TEXT,
            workflowId TEXT,
            triggerOrOperationId TEXT,
            severity TEXT NOT NULL,
            result TEXT NOT NULL,
            failureCategory TEXT NOT NULL,
            message TEXT NOT NULL,
            durationMillis INTEGER,
            resourceOwner TEXT
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_execution_diagnostics_timestamp ON execution_diagnostics(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_execution_diagnostics_modId ON execution_diagnostics(modId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_execution_diagnostics_severity ON execution_diagnostics(severity)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `marketplace_catalog` (`modId` TEXT NOT NULL, `name` TEXT NOT NULL, `summary` TEXT NOT NULL, `category` TEXT NOT NULL, `developer` TEXT, `version` TEXT NOT NULL, `manifestSchema` INTEGER NOT NULL, `definitionVersion` INTEGER NOT NULL, `minAndroidApi` INTEGER NOT NULL, `capabilitiesJson` TEXT NOT NULL, `compatibilityJson` TEXT NOT NULL, `sha256` TEXT NOT NULL, `publishedAt` TEXT, `versionId` TEXT NOT NULL, `refreshedAt` INTEGER NOT NULL, PRIMARY KEY(`modId`))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_marketplace_catalog_refreshedAt` ON `marketplace_catalog` (`refreshedAt`)")
    }
}
