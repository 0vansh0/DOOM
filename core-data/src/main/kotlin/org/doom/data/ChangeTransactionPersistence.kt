package org.doom.data

import android.content.Context
import android.provider.Settings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.doom.domain.ChangeTransactionCoordinator
import org.doom.database.ChangeTransactionEntity
import org.doom.database.DoomDatabase
import org.doom.model.*

class RoomChangeTransactionStore(private val database: DoomDatabase) : ChangeTransactionStore {
    private val dao get() = database.mods()
    override suspend fun insert(transaction: ChangeTransaction) = dao.insertChangeTransaction(transaction.toEntity())
    override suspend fun update(transaction: ChangeTransaction) = dao.updateChangeTransaction(transaction.toEntity())
    override suspend fun find(transactionId: String) = dao.changeTransaction(transactionId)?.toModel()
    override suspend fun activeForTarget(targetIdentity: String) = dao.activeChangesForTarget(targetIdentity).map { it.toModel() }
    override suspend fun unresolved() = dao.unresolvedChanges().map { it.toModel() }
    fun observeHistory(): Flow<List<ChangeTransaction>> = dao.observeChangeHistory().map { list -> list.map { it.toModel() } }
}

/** Public Android API adapter; authorization remains the user's WRITE_SETTINGS special access. */
class AndroidSystemSettingAccess(private val context: Context) : SystemSettingAccess {
    override fun isAuthorized(targetIdentity: String): Boolean =
        targetIdentity == PrivilegedOperationCatalog.SCREEN_TIMEOUT_TARGET && Settings.System.canWrite(context)

    override suspend fun read(targetIdentity: String): Long {
        requireTarget(targetIdentity)
        val value = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
        check(value > 0) { "Screen timeout could not be read." }
        return value.toLong()
    }

    override suspend fun write(targetIdentity: String, value: Long) {
        requireTarget(targetIdentity)
        require(value in 1L..Int.MAX_VALUE.toLong()) { "Screen timeout value is invalid." }
        check(Settings.System.canWrite(context)) { "WRITE_SETTINGS access is not granted." }
        check(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, value.toInt())) {
            "Android rejected the screen timeout update."
        }
    }

    private fun requireTarget(targetIdentity: String) {
        require(targetIdentity == PrivilegedOperationCatalog.SCREEN_TIMEOUT_TARGET) { "Unsupported system setting." }
    }
}

private fun ChangeTransaction.toEntity() = ChangeTransactionEntity(
    transactionId, modId, operationId, operationVersion, targetIdentity, originalState,
    requestedState, appliedState, createdAt, updatedAt, status.name, failureReason, recoveryMetadata
)

private fun ChangeTransactionEntity.toModel() = ChangeTransaction(
    transactionId, modId, operationId, operationVersion, targetIdentity, originalState,
    requestedState, appliedState, createdAt, updatedAt, ChangeTransactionStatus.valueOf(status), failureReason, recoveryMetadata
)

@Module
@InstallIn(SingletonComponent::class)
object ChangeTransactionModule {
    @Provides @Singleton
    fun changeTransactionStore(database: DoomDatabase) = RoomChangeTransactionStore(database)

    @Provides @Singleton
    fun systemSettingAccess(@dagger.hilt.android.qualifiers.ApplicationContext context: Context) = AndroidSystemSettingAccess(context)

    @Provides @Singleton
    fun changeTransactionCoordinator(
        store: RoomChangeTransactionStore,
        access: AndroidSystemSettingAccess,
        preferences: org.doom.domain.DoomPreferences,
        shizuku: ShizukuPrivilegedAdapter
    ) = ChangeTransactionCoordinator(store, access,
        OperationApproval { _, _ -> false }, org.doom.engine.CapabilityRegistry.operationDefinitions,
        android.os.Build.VERSION.SDK_INT, adapterGate = shizuku,
        safeModeEnabled = { preferences.values.first().safeMode })
}
