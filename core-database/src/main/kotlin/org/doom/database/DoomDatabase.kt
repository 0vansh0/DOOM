package org.doom.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.doom.model.ModStatus
import org.doom.model.DiagnosticFailureCategory
import org.doom.model.DiagnosticResult
import org.doom.model.DiagnosticSeverity

@Entity(tableName = "installed_mods")
data class InstalledModEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val packagePath: String,
    val desiredEnabled: Boolean = false,
    val status: ModStatus = ModStatus.INSTALLED_DISABLED,
    val installedAt: Long = System.currentTimeMillis(),
    val lastError: String? = null,
    val description: String = "",
    val minAndroidApi: Int = 31,
    val defaultColor: String = "#B48CFF",
    val defaultIntensity: Int = 45,
    val sha256: String? = null,
    val declaredCapabilities: String? = null,
    val author: String? = null,
    val license: String? = null,
    @ColumnInfo(defaultValue = "10") val minimumIntensity: Int = 10,
    @ColumnInfo(defaultValue = "60") val maximumIntensity: Int = 60,
    val ringDefaults: String? = null,
    val optionDefinitions: String? = null,
    val settingDefinitions: String? = null
)

@Entity(tableName = "mod_settings",
    foreignKeys = [ForeignKey(entity = InstalledModEntity::class, parentColumns = ["id"],
        childColumns = ["modId"], onDelete = ForeignKey.CASCADE)])
data class ModSettingsEntity(@PrimaryKey val modId: String, val settingsJson: String, val updatedAt: Long)

@Entity(tableName = "capability_grants", primaryKeys = ["modId", "capabilityId"],
    foreignKeys = [ForeignKey(entity = InstalledModEntity::class, parentColumns = ["id"],
        childColumns = ["modId"], onDelete = ForeignKey.CASCADE)])
data class CapabilityGrantEntity(val modId: String, val capabilityId: String, val approved: Boolean, val grantedAt: Long)

@Entity(tableName = "mod_execution_log", indices = [Index("modId")],
    foreignKeys = [ForeignKey(entity = InstalledModEntity::class, parentColumns = ["id"],
        childColumns = ["modId"], onDelete = ForeignKey.CASCADE)])
data class ModExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modId: String,
    val event: String,
    val result: String,
    val message: String?,
    val timestamp: Long
)

/** Recovery journal intentionally has no FK to installed_mods so uninstall cannot erase rollback state. */
@Entity(tableName = "change_transactions", indices = [Index("targetIdentity", "status"), Index("modId")])
data class ChangeTransactionEntity(
    @PrimaryKey val transactionId: String,
    val modId: String,
    val operationId: String,
    val operationVersion: Int,
    val targetIdentity: String,
    val originalState: String,
    val requestedState: String,
    val appliedState: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val failureReason: String?,
    val recoveryMetadata: String?
)

/** Diagnostic history is intentionally independent from installed_mods so uninstall cannot erase recovery evidence. */
@Entity(tableName = "execution_diagnostics", indices = [Index("timestamp"), Index("modId"), Index("severity")])
data class ExecutionDiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val modId: String,
    val runtimeSessionId: String?,
    val workflowId: String?,
    val triggerOrOperationId: String?,
    val severity: DiagnosticSeverity,
    val result: DiagnosticResult,
    val failureCategory: DiagnosticFailureCategory,
    val message: String,
    val durationMillis: Long?,
    val resourceOwner: String?
)

@Entity(tableName = "marketplace_catalog", indices = [Index("refreshedAt")])
data class MarketplaceCatalogEntity(
    @PrimaryKey val modId: String,
    val name: String,
    val summary: String,
    val category: String,
    val developer: String?,
    val version: String,
    val manifestSchema: Int,
    val definitionVersion: Int,
    val minAndroidApi: Int,
    val capabilitiesJson: String,
    val compatibilityJson: String,
    val sha256: String,
    val publishedAt: String?,
    val versionId: String,
    val refreshedAt: Long
)

data class InstalledModRecord(
    @Embedded val mod: InstalledModEntity,
    @Relation(parentColumn = "id", entityColumn = "modId") val settings: ModSettingsEntity?,
    @Relation(parentColumn = "id", entityColumn = "modId") val grants: List<CapabilityGrantEntity>
)

@Dao
interface ModDao {
    @Insert suspend fun insert(mod: InstalledModEntity)
    @Update suspend fun update(mod: InstalledModEntity)

    @Transaction
    @Query("SELECT * FROM installed_mods ORDER BY name COLLATE NOCASE, id")
    fun observeRecords(): Flow<List<InstalledModRecord>>

    @Transaction
    @Query("SELECT * FROM installed_mods WHERE id = :id")
    fun observeRecord(id: String): Flow<InstalledModRecord?>

    @Transaction
    @Query("SELECT * FROM installed_mods WHERE id = :id")
    suspend fun record(id: String): InstalledModRecord?

    @Query("SELECT * FROM installed_mods ORDER BY id")
    suspend fun rows(): List<InstalledModEntity>

    @Upsert suspend fun putSettings(settings: ModSettingsEntity)

    @Query("DELETE FROM mod_settings WHERE modId = :id")
    suspend fun resetSettings(id: String)

    @Query("SELECT timestamp FROM mod_execution_log WHERE modId = :id AND event = 'runtime_failed' AND timestamp >= :since")
    suspend fun runtimeFailures(id: String, since: Long): List<Long>
    @Query("SELECT timestamp FROM execution_diagnostics WHERE modId = :id AND failureCategory IN ('RUNTIME_FAILURE','CLEANUP_FAILURE','TIMEOUT','RESOURCE_LIMIT') AND timestamp >= :since")
    suspend fun qualifyingFaults(id: String, since: Long): List<Long>
    @Upsert suspend fun putGrant(grant: CapabilityGrantEntity)

    @Query("DELETE FROM installed_mods WHERE id = :id")
    suspend fun delete(id: String)

    @Insert suspend fun log(event: ModExecutionLogEntity)

    @Query("SELECT * FROM mod_execution_log WHERE modId = :id ORDER BY id")
    suspend fun logs(id: String): List<ModExecutionLogEntity>

    @Insert suspend fun insertChangeTransaction(transaction: ChangeTransactionEntity)
    @Update suspend fun updateChangeTransaction(transaction: ChangeTransactionEntity)
    @Query("SELECT * FROM change_transactions WHERE transactionId = :id")
    suspend fun changeTransaction(id: String): ChangeTransactionEntity?
    @Query("SELECT * FROM change_transactions WHERE targetIdentity = :target AND status IN ('APPLIED','PREPARED','RESTORING','UNCERTAIN','CONFLICT')")
    suspend fun activeChangesForTarget(target: String): List<ChangeTransactionEntity>
    @Query("SELECT * FROM change_transactions WHERE status IN ('PREPARED','RESTORING','UNCERTAIN') ORDER BY createdAt")
    suspend fun unresolvedChanges(): List<ChangeTransactionEntity>
    @Query("SELECT * FROM change_transactions ORDER BY createdAt DESC LIMIT 50")
    fun observeChangeHistory(): Flow<List<ChangeTransactionEntity>>

    @Insert suspend fun insertDiagnostic(event: ExecutionDiagnosticEntity)
    @Query("SELECT * FROM execution_diagnostics ORDER BY timestamp DESC, id DESC LIMIT 200")
    fun observeDiagnostics(): Flow<List<ExecutionDiagnosticEntity>>
    @Query("SELECT * FROM execution_diagnostics ORDER BY timestamp DESC, id DESC LIMIT 200")
    suspend fun recentDiagnostics(): List<ExecutionDiagnosticEntity>
    @Query("""SELECT start.* FROM execution_diagnostics AS start
        WHERE start.triggerOrOperationId = 'workflow.started' AND start.runtimeSessionId IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM execution_diagnostics AS terminal
            WHERE terminal.runtimeSessionId = start.runtimeSessionId
            AND terminal.triggerOrOperationId IN ('workflow.terminal','workflow.interrupted'))
        ORDER BY start.timestamp""")
    suspend fun interruptedWorkflowStarts(): List<ExecutionDiagnosticEntity>
    @Query("DELETE FROM execution_diagnostics")
    suspend fun deleteDiagnostics()
    @Query("DELETE FROM execution_diagnostics WHERE id NOT IN (SELECT id FROM execution_diagnostics ORDER BY timestamp DESC, id DESC LIMIT 200)")
    suspend fun trimDiagnostics()

    @Query("""DELETE FROM mod_execution_log WHERE id IN (
        SELECT id FROM mod_execution_log ORDER BY id DESC LIMIT -1 OFFSET 200
    )""")
    suspend fun trimLog()
    @Query("DELETE FROM mod_execution_log")
    suspend fun deleteLogs()
}

@Dao
interface MarketplaceCatalogDao {
    @Upsert suspend fun upsert(entries: List<MarketplaceCatalogEntity>)
    @Query("SELECT * FROM marketplace_catalog ORDER BY refreshedAt DESC, name COLLATE NOCASE LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<MarketplaceCatalogEntity>
    @Query("SELECT * FROM marketplace_catalog WHERE modId = :id LIMIT 1")
    suspend fun find(id: String): MarketplaceCatalogEntity?
    @Query("DELETE FROM marketplace_catalog WHERE modId NOT IN (SELECT modId FROM marketplace_catalog ORDER BY refreshedAt DESC LIMIT :limit)")
    suspend fun trim(limit: Int = 100)
}

@Database(
    entities = [InstalledModEntity::class, ModSettingsEntity::class,
        CapabilityGrantEntity::class, ModExecutionLogEntity::class, ChangeTransactionEntity::class,
        ExecutionDiagnosticEntity::class, MarketplaceCatalogEntity::class],
    version = 9, exportSchema = true
)
abstract class DoomDatabase : RoomDatabase() {
    abstract fun mods(): ModDao
    abstract fun marketplaceCatalog(): MarketplaceCatalogDao
}
