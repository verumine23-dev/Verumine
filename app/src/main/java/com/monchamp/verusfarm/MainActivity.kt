package com.monchamp.verusfarm

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.monchamp.verusfarm.ui.Actions
import com.monchamp.verusfarm.ui.AppRoot
import com.monchamp.verusfarm.ui.DeviceInfo
import com.monchamp.verusfarm.ui.VerusFarmTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var checks by mutableStateOf(Checks())
    private var wallet by mutableStateOf("")
    private var promptSetup by mutableStateOf(false)
    private var showRoulette by mutableStateOf(false)

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshChecks() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        wallet = Prefs.wallet(this)
        RewardNotifier.ensureChannel(this)

        // Toute première ouverture : le minage démarre tout seul
        if (Prefs.isFirstRun(this)) {
            Prefs.markFirstRunDone(this)
            Prefs.setEnabled(this, true)
        }
        // Propose la configuration (autorisations) tant qu'elle n'a pas été validée
        promptSetup = !Prefs.setupDone(this)

        // Roulette de reconnexion : demandée par la notification, ou en attente d'un précédent passage
        showRoulette = intent?.getBooleanExtra(EXTRA_SHOW_ROULETTE, false) == true || Prefs.pendingRoulette(this)

        MiningState.set { it.copy(enabled = Prefs.isEnabled(this), miningMs = Prefs.miningMs(this)) }
        refreshChecks()
        NetworkWake.register(this)
        RewardNotifier.scheduleEnergyFull(this)
        Heartbeat.schedule(this)   // battement de cœur : réveil périodique même hors ligne
        if (Prefs.isEnabled(this)) startMining()

        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        setContent {
            VerusFarmTheme {
                val ui by MiningState.ui.collectAsStateWithLifecycle()
                val pool by PoolState.stats.collectAsStateWithLifecycle()
                val history by PoolState.history.collectAsStateWithLifecycle()

                PoolRefresher(wallet)

                AppRoot(
                    ui = ui,
                    pool = pool,
                    history = history,
                    device = DeviceInfo(
                        checks = checks,
                        abi = DeviceIdentity.abi(),
                        cores = cores,
                        mode = Prefs.mode(this@MainActivity)
                    ),
                    actions = Actions(
                        onAskNotif = { askNotificationPermission() },
                        onAskBattery = { askBatteryExemption() },
                        onAskAdmin = { askAdmin() },
                        onOpenAppSettings = { openAppSettings() },
                        onOpenUrl = { openUrl(it) },
                        onSetPower = { setPowerMode(it) },
                        onOpenAutostart = { openAutostartSettings() },
                        onSetupDone = { Prefs.setSetupDone(this@MainActivity); promptSetup = false }
                    ),
                    promptSetup = promptSetup,
                    showRoulette = showRoulette,
                    onRouletteClaim = {
                        Rewards.grantReconnectJackpot(this@MainActivity)
                        Prefs.setPendingRoulette(this@MainActivity, false)
                        showRoulette = false
                    },
                    onRouletteClose = {
                        Prefs.setPendingRoulette(this@MainActivity, false)
                        showRoulette = false
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOW_ROULETTE, false) || Prefs.pendingRoulette(this)) {
            showRoulette = true
        }
    }

    override fun onResume() {
        super.onResume()
        refreshChecks()
        wallet = Prefs.wallet(this)
        RewardNotifier.scheduleEnergyFull(this)
    }

    companion object {
        const val EXTRA_SHOW_ROULETTE = "show_roulette"
    }

    // ---------- Minage ----------
    private fun startMining() {
        if (!Config.isUsableWallet(Prefs.wallet(this))) {
            MiningState.set {
                it.copy(status = "Minage en préparation…", tone = Tone.WARN)
            }
            return
        }
        Prefs.setEnabled(this, true)
        MiningState.set { it.copy(enabled = true) }
        try {
            ContextCompat.startForegroundService(this, Intent(this, MiningService::class.java))
        } catch (e: Exception) {
            MiningState.set {
                it.copy(status = "Impossible de démarrer le minage : ${e.message}", tone = Tone.ERROR)
            }
        }
    }

    // L'utilisateur a changé la puissance (Faible / Moyen / Max) sur la page Minage.
    // On enregistre le choix et, si le minage tourne, on le relance proprement avec le
    // nouveau nombre de cœurs (le service lit Prefs.mode au redémarrage).
    private fun setPowerMode(mode: PowerMode) {
        Prefs.setMode(this, mode)
        if (Prefs.isEnabled(this) && MiningService.running) {
            try {
                val intent = Intent(this, MiningService::class.java)
                    .putExtra(MiningService.EXTRA_RELOAD, true)
                ContextCompat.startForegroundService(this, intent)
            } catch (_: Exception) {
            }
        }
    }

    // ---------- Protections ----------
    private fun refreshChecks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        checks = Checks(
            notif = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            battery = pm.isIgnoringBatteryOptimizations(packageName),
            admin = dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
        )
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun askBatteryExemption() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun askAdmin() {
        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@MainActivity, AdminReceiver::class.java))
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Empêche la désinstallation accidentelle de Verumine.")
            }
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
        }
    }

    // Ouvre, au mieux, l'écran « Démarrage automatique » du constructeur. Chaque marque a
    // son propre écran (composant caché) : on essaie les plus connus, sinon on retombe sur
    // la fiche de l'application. Le guide affiché dans l'app explique où appuyer ensuite.
    private fun openAutostartSettings() {
        val candidates = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.transsion.phonemaster" to "com.itel.autobootmanager.activity.AutoBootMgrActivity",
            "com.transsion.phonemanager" to "com.itel.autobootmanager.activity.AutoBootMgrActivity"
        )
        for ((pkg, cls) in candidates) {
            try {
                // On tente d'ouvrir directement l'écran : si le composant n'existe pas,
                // startActivity lève une exception et on passe au candidat suivant.
                startActivity(
                    Intent().setComponent(ComponentName(pkg, cls))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (_: Exception) {
            }
        }
        // Rien de spécifique trouvé : on ouvre la fiche de l'application (le guide prend le relais).
        openAppSettings()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }
}

// Interroge Luckpool toutes les minutes tant que l'application est visible
@Composable
private fun PoolRefresher(wallet: String) {
    val owner = LocalLifecycleOwner.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(wallet) {
        if (!Config.isUsableWallet(wallet)) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var round = 0
            while (true) {
                val stats = withContext(Dispatchers.IO) { PoolApi.fetchStats(wallet) }
                if (stats != null) {
                    Earnings.update(ctx, stats)   // met à jour le solde d'après les calculs acceptés
                    PoolState.stats.value = stats
                }
                if (round % 5 == 0) {
                    val hist = withContext(Dispatchers.IO) { PoolApi.fetchHistory(wallet) }
                    if (hist.isNotEmpty()) PoolState.history.value = hist
                }
                round++
                delay(60_000L)
            }
        }
    }
}
