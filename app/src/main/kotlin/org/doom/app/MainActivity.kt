package org.doom.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import org.doom.model.ThemePreference
import org.doom.model.ModStatus
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import dagger.hilt.android.AndroidEntryPoint
import org.doom.core.designsystem.DoomTheme
import org.doom.feature.home.HomeScreen
import org.doom.feature.discover.DiscoverScreen
import org.doom.feature.installed.InstalledScreen
import org.doom.feature.mod.details.ModDetailsScreen
import org.doom.feature.settings.SettingsScreen
import org.doom.feature.settings.DeviceDiagnosticsScreen
import org.doom.model.ShizukuConnectionPhase
import org.doom.app.ModImportDialog

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var resumed = false
    private val model: DoomViewModel by viewModels()
    private val importer: ModImportViewModel by viewModels()
    private val marketplace: MarketplaceViewModel by viewModels()
    private val account: AccountViewModel by viewModels()
    private val feedback: FeedbackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by model.preferences.collectAsStateWithLifecycle()
            val dark = when (preferences.theme) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val barStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            DoomTheme(dark = dark) {
                DoomNavigation(model, importer, marketplace, account, feedback) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")))
                    } catch (_: ActivityNotFoundException) {
                        model.report("Android overlay settings are unavailable.")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // Shizuku permission can be revoked while DOOM is backgrounded in its manager.
        // Refresh on return so the visible adapter state is not left stale.
        model.refreshShizuku()
        model.refreshRequirements()
        model.foreground(hasWindowFocus())
    }

    override fun onPause() {
        resumed = false
        model.foreground(false)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (resumed) model.foreground(hasFocus)
    }
}

private val tabs = listOf("Home" to "⌂", "Discover" to "◇", "Installed" to "▤", "Diagnostics" to "ⓘ", "Settings" to "⚙")

@Composable
private fun DoomNavigation(model: DoomViewModel, importer: ModImportViewModel, marketplaceModel: MarketplaceViewModel, accountModel: AccountViewModel, feedbackModel: FeedbackViewModel, openOverlaySettings: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val batteryPercent by model.batteryPercent.collectAsStateWithLifecycle()
    val external by model.externalState.collectAsStateWithLifecycle()
    val activity = requireNotNull(androidx.activity.compose.LocalActivity.current)
    var notificationRequest by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = notificationRequest
        notificationRequest = null
        if (granted && requested != null) model.report("Notifications enabled. Tap the external overlay action again.")
        else model.report("Enable DOOM notifications to use the floating overlay Stop control.")
    }
    fun externalStart(id: String, persistent: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationRequest = id to persistent
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else model.testExternal(activity, id, persistent)
    }
    val runtime by model.runtimeStates.collectAsStateWithLifecycle()
    val runningWorkflows by model.runningWorkflows.collectAsStateWithLifecycle()
    val library by model.library.collectAsStateWithLifecycle()
    val examples by model.examples.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val deviceCapabilities by model.deviceCapabilities.collectAsStateWithLifecycle()
    val shizukuState by model.shizukuState.collectAsStateWithLifecycle()
    val changeHistory by model.changeHistory.collectAsStateWithLifecycle()
    val executionDiagnostics by model.executionDiagnostics.collectAsStateWithLifecycle()
    val screenTimeoutMillis by model.screenTimeoutMillis.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val importState by importer.state.collectAsStateWithLifecycle()
    val marketplaceState by marketplaceModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importer.select(uri?.toString())
    }
    val diagnosticPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) model.exportDiagnostics(uri.toString())
    }
    ModImportDialog(importState, importer::install, importer::dismiss) {
        nav.navigate("installed") { popUpTo("home"); launchSingleTop = true }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            model.dismissMessage(it)
        }
    }
    val installed = (library as? LoadState.Ready)?.value.orEmpty()
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        NavigationBar {
            tabs.forEach { (title, symbol) ->
                val route = title.lowercase(java.util.Locale.ROOT)
                NavigationBarItem(
                    selected = entry?.destination?.hierarchy?.any { it.route == route } == true,
                    onClick = {
                        nav.navigate(route) {
                            popUpTo("home")
                            launchSingleTop = true
                        }
                    },
                    icon = { Text(symbol, modifier = Modifier.clearAndSetSemantics {}) },
                    label = { Text(title) }
                )
            }
        }
    }) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(installed.size) { nav.navigate("discover") } }
            composable("discover") {
                DiscoverScreen(
                    examples = (examples as? LoadState.Ready)?.value.orEmpty(),
                    installed = installed.map { it.id }.toSet(),
                    loading = examples == LoadState.Loading,
                    failed = examples == LoadState.Error,
                    busy = busy,
                    importMod = { picker.launch(arrayOf("*/*")) },
                    importAvailable = importState == ImportState.Idle,
                    install = model::installExample,
                    retry = model::loadExamples,
                    marketplace = marketplaceState.page,
                    marketplaceBusy = marketplaceState.busy,
                    marketplaceMessage = marketplaceState.message,
                    marketplaceQuery = marketplaceState.query,
                    selectedCategory = marketplaceState.category,
                    selectedSort = marketplaceState.sort,
                    loadMoreAvailable = marketplaceState.page?.nextCursor != null,
                    selectedMarketplaceMod = marketplaceState.selected,
                    reviewName = marketplaceState.candidate?.manifest?.name,
                    reviewId = marketplaceState.candidate?.manifest?.id,
                    reviewDeveloper = marketplaceState.selected?.developer,
                    reviewAuthor = marketplaceState.candidate?.manifest?.author,
                    reviewSha256 = marketplaceState.candidate?.sha256,
                    reviewVersion = marketplaceState.candidate?.manifest?.version,
                    reviewCapabilities = marketplaceState.candidate?.manifest?.capabilities.orEmpty(),
                    reviewCompatible = marketplaceState.canInstallReviewedPackage,
                    reviewIsUpdate = marketplaceState.reviewIsUpdate,
                    compatibilityWarnings = marketplaceState.compatibilityWarnings,
                    marketplaceActionBusy = marketplaceState.busy,
                    onMarketplaceSearch = marketplaceModel::search,
                    onMarketplaceCategory = { marketplaceModel.refresh(category = it) },
                    onMarketplaceSort = { marketplaceModel.refresh(sort = it) },
                    onMarketplaceLoadMore = marketplaceModel::loadMore,
                    onMarketplaceRetry = { marketplaceModel.refresh() },
                    onMarketplaceOpen = marketplaceModel::open,
                    onMarketplaceCloseDetails = marketplaceModel::closeDetails,
                    onMarketplacePrepareInstall = { version, update -> marketplaceModel.prepareInstall(version, update) },
                    onMarketplaceConfirmInstall = marketplaceModel::confirmInstall,
                    onMarketplaceCancelReview = marketplaceModel::cancelReview,
                    installedVersions = installed.associate { it.id to it.version },
                    enabledMods = installed.filter { it.enabled }.map { it.id }.toSet()
                )
            }
            composable("installed") {
                InstalledScreen(installed, library == LoadState.Loading, library == LoadState.Error, runtime) {
                    nav.navigate("mod/$it")
                }
            }
            composable("mod/{id}") { backStack ->
                val id = requireNotNull(backStack.arguments?.getString("id"))
                val details = remember(id) { model.details(id) }
                val mod by details.collectAsStateWithLifecycle(installed.find { it.id == id })
                val current = mod
                ModDetailsScreen(
                    mod = current,
                    busy = busy,
                    externalMessage = external.message,
                    externalRunning = external.modId == id,
                    testExternal = { externalStart(id, false) },
                    startFloating = { externalStart(id, true) },
                    stopFloating = model::stopExternal,
                    batteryPercent = batteryPercent.takeIf { current?.approvedCapabilities?.contains("battery.read") == true },
                    resetSettings = { model.resetSettings(id) },
                    runtime = runtime[id]?.takeIf { current?.status == ModStatus.READY_TO_ACTIVATE },
                    runTest = { model.manual(id) },
                    dismissEffect = { model.dismissEffect(id) },
                    permissionRequired = current?.status == ModStatus.PERMISSION_REQUIRED,
                    compatibility = current?.let(model::compatibility),
                    safeMode = preferences.safeMode,
                    showPreviewInitially = preferences.previewOnOpen,
                    back = { nav.popBackStack() },
                    openOverlaySettings = openOverlaySettings,
                    onGrant = { capability, approved -> if (current != null) model.setGrant(current.id, capability, approved) },
                    onEnabled = { if (current != null) { if (it) model.requestEnable(current.id) else model.disable(current.id) } },
                    onConfigure = { if (current != null) model.configure(current.id, it) },
                    onSettingsChange = { if (current != null) model.configureSettings(current.id, it) },
                    uninstall = { if (current != null) model.uninstall(current.id) }
                )
            }
            composable("feedback") {
                val feedbackState by feedbackModel.state.collectAsStateWithLifecycle()
                FeedbackCenter(feedbackState, feedbackModel::submit, { nav.navigate("account") }) { nav.popBackStack() }
            }
            composable("account") {
                val accountState by accountModel.state.collectAsStateWithLifecycle()
                AccountScreen(accountState, accountModel::signIn, accountModel::register, accountModel::logout) { nav.popBackStack() }
            }
            composable("settings") {
                SettingsScreen(preferences, busy, model::setPreviewOnOpen, model::setSafeMode,
                    model::setTheme, model::setDiagnosticLogging, model::disableAll,
                    exportDiagnostics = { diagnosticPicker.launch("doom-diagnostics.json") },
                    shizukuState = shizukuState,
                    refreshShizuku = { model.refreshShizuku() },
                    requestShizukuAuthorization = { model.requestShizukuAuthorization() },
                    openShizuku = {
                        val launch = activity.packageManager.getLaunchIntentForPackage(org.doom.data.ShizukuConnectionManager.SHIZUKU_PACKAGE)
                        if (launch != null) runCatching { activity.startActivity(launch) }
                            .onFailure { model.report("Could not open the Shizuku app.") }
                        else runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }
                            .onFailure { model.report("Open https://shizuku.rikka.app/ to follow the official setup instructions.") }
                    },
                    testShizukuDiagnostic = model::testShizukuDiagnostic,
                    screenTimeoutMillis = screenTimeoutMillis,
                    changeHistory = changeHistory,
                    canWriteSystemSettings = Settings.System.canWrite(activity),
                    refreshRecovery = model::refreshScreenTimeout,
                    openWriteSettings = {
                        runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${activity.packageName}"))) }
                            .onFailure { model.report("Android Modify system settings access is unavailable.") }
                    },
                    applyTimeoutTest = model::applyScreenTimeoutTest,
                    restoreChange = model::restoreChange, openFeedback = { nav.navigate("feedback") },
                    openAccount = { nav.navigate("account") })
            }
            composable("diagnostics") {
                LaunchedEffect(Unit) { model.refreshCapabilities(true) }
                DeviceDiagnosticsScreen(
                    snapshot = deviceCapabilities,
                    refresh = { model.refreshCapabilities(true) },
                    openOverlaySettings = openOverlaySettings,
                    requestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationRequest = "" to false
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else model.report("Notification permission is already available.")
                    },
                    openAppSettings = {
                        runCatching {
                            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${activity.packageName}")))
                        }.onFailure { model.report("Android app settings are unavailable.") }
                    },
                    installedMods = installed,
                    runtimeStates = runtime,
                    diagnosticEvents = executionDiagnostics,
                    safeMode = preferences.safeMode,
                    retryMod = model::requestEnable,
                    disableMod = model::disable,
                    clearDiagnostics = model::clearExecutionDiagnostics,
                    runningWorkflows = runningWorkflows,
                    changeHistory = changeHistory,
                    openSettings = { nav.navigate("settings") }
                )
            }
        }
    }
}
