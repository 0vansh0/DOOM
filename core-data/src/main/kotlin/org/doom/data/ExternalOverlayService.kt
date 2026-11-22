package org.doom.data

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.Settings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.doom.domain.DoomPreferences
import org.doom.engine.runtime.*
import org.doom.model.*
import javax.inject.Inject

/** Started only by a visible Activity action. A process kill requires an explicit new start. */
@AndroidEntryPoint
class ExternalOverlayService : Service() {
    @Inject internal lateinit var repository: RoomModRepository
    @Inject lateinit var preferences: DoomPreferences
    @Inject lateinit var controller: ExternalOverlayController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: DefaultModRuntime
    private var running: Job? = null
    private var modId: String? = null
    private var persistent = false
    private var chargingDuration: Long? = null
    private var receiverRegistered = false
    private var watching = false
    private val authorization = MutableStateFlow(false)
    private var percent: Int? = null
    private val ops by lazy { getSystemService(AppOpsManager::class.java) }
    private val watcher = AppOpsManager.OnOpChangedListener { _, _ ->
        mainExecutor.execute {
            authorization.value = Settings.canDrawOverlays(this)
            engine.recheckPermissions()
            if (!authorization.value) finish("Android overlay permission was revoked.")
        }
    }
    private val battery = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            percent = if (scale > 0 && level in 0..scale) level * 100 / scale else null
            engine.batteryChanged(false, percent)
            if (chargingDuration != null && intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) {
                finish("Charging glow stopped after charger disconnection.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = DefaultModRuntime(AndroidEffects(this), SystemClock::elapsedRealtime,
            { Settings.canDrawOverlays(this) }, { event ->
                android.util.Log.i("DoomExternal", "${event.event} ${event.result}")
                scope.launch { repository.recordRuntimeDiagnostic(event) }
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            finish("External overlay stopped from notification.")
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra("modId")
        if (id == null || running != null) {
            if (running == null) stopSelf()
            return START_NOT_STICKY
        }
        modId = id
        persistent = intent.getBooleanExtra("persistent", false)
        chargingDuration = intent.getLongExtra("chargingDuration", 0).takeIf { it in 1..20_000 }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "External floating glow", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val openIntent = packageManager.getLaunchIntentForPackage(packageName)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(if (persistent) "DOOM floating glow" else "DOOM external overlay test")
                .setContentText("User-started overlay · tap Stop to remove")
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop", stop).build())
                .apply {
                    if (openIntent != null) setContentIntent(PendingIntent.getActivity(this@ExternalOverlayService, 2,
                        openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }.build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(701, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(701, notification)
            authorization.value = Settings.canDrawOverlays(this)
            ops.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, watcher)
            watching = true
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(battery, filter, RECEIVER_NOT_EXPORTED)
            else registerReceiver(battery, filter)
            receiverRegistered = true
            engine.begin(false)
            running = scope.launch {
                try {
                    launch {
                        engine.expiryDeadline.collectLatest { deadline ->
                            if (deadline != null) {
                                delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1))
                                engine.expire()
                                if (!persistent) finish("External test finished.")
                            }
                        }
                    }
                    combine(repository.observeDetails(id), preferences.values, authorization) { mod, prefs, permitted ->
                        mod?.takeIf { !prefs.safeMode && permitted && it.desiredEnabled &&
                            it.status == ModStatus.READY_TO_ACTIVATE && it.runtimeAvailable &&
                            it.approvedCapabilities.containsAll(it.capabilities) && "overlay.render" in it.capabilities }
                    }.distinctUntilChanged().collectLatest { mod ->
                        if (mod == null) {
                            finish("External overlay stopped. Enable the mod and approve its capabilities and Android access.")
                            return@collectLatest
                        }
                        engine.deactivate(id)
                        val verified = withTimeout(5_000) { repository.loadRuntimeDefinition(id) }
                        val overlay = verified.subscriptions.values.firstOrNull { it.action.effect == ModEffect.SHOW_OVERLAY }
                            ?: error("This package does not declare an overlay.show effect.")
                        val testDefinition = verified.copy(subscriptions = mapOf(ModEvent.MANUAL to
                            Subscription(Condition.ALWAYS, overlay.action)))
                        if (!engine.activate(mod, testDefinition)) error("Mod activation was blocked.")
                        engine.batteryChanged(false, percent)
                        engine.dispatch(ModEvent.MANUAL, id)
                        if (engine.states.value[id]?.presentation == null) error("Android did not create the overlay.")
                        // Host-owned user test lifetime. Package parameters and rendering remain validated.
                        engine.extendPresentation(id, if (persistent) null else chargingDuration ?: 12_000)
                        controller.shown(id, persistent, chargingDuration != null)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    android.util.Log.e("DoomExternal", "External overlay failed", error)
                    finish(error.message?.take(140) ?: "External overlay is unavailable on this device.")
                }
            }
        } catch (error: RuntimeException) {
            android.util.Log.e("DoomExternal", "Service initialization failed", error)
            finish("Android could not start the floating overlay. Check notification and overlay access.")
        }
        return START_NOT_STICKY
    }

    private fun finish(message: String) {
        engine.stop()
        controller.finished(message)
        stopSelf()
    }

    override fun onDestroy() {
        engine.stop()
        running?.cancel()
        if (watching) ops.stopWatchingMode(watcher)
        if (receiverRegistered) unregisterReceiver(battery)
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (controller.state.value.modId == modId) controller.finished("External overlay stopped.")
        android.util.Log.i("DoomExternal", "Service destroyed; windows and listeners released")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "doom_external_overlay"
        private const val STOP = "org.doom.STOP_EXTERNAL_OVERLAY"
    }
}
