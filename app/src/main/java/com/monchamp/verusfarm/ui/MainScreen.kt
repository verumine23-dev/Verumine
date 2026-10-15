package com.monchamp.verusfarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.monchamp.verusfarm.Checks
import com.monchamp.verusfarm.HashPoint
import com.monchamp.verusfarm.MiningUi
import com.monchamp.verusfarm.PoolStats
import com.monchamp.verusfarm.PowerMode

// Informations sur cet appareil
class DeviceInfo(
    val checks: Checks,
    val abi: String,
    val cores: Int,
    val mode: PowerMode
)

// Actions déclenchées par l'écran
class Actions(
    val onAskNotif: () -> Unit,
    val onAskBattery: () -> Unit,
    val onAskAdmin: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onSetPower: (PowerMode) -> Unit = {},
    val onOpenAutostart: () -> Unit = {},
    val onSetupDone: () -> Unit = {}
)

@Composable
fun AppRoot(
    ui: MiningUi,
    pool: PoolStats?,
    history: List<HashPoint>,
    device: DeviceInfo,
    actions: Actions,
    promptSetup: Boolean = false,
    showRoulette: Boolean = false,
    onRouletteClaim: () -> Unit = {},
    onRouletteClose: () -> Unit = {}
) {
    var splashDone by rememberSaveable { mutableStateOf(false) }
    var setupDismissed by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        MainScreen(ui, pool, history, device, actions)
        if (splashDone && promptSetup && !setupDismissed && !showRoulette) {
            SetupDialog(
                checks = device.checks,
                actions = actions,
                onDone = { setupDismissed = true; actions.onSetupDone() }
            )
        }
        // Roulette de reconnexion : prioritaire, passe par-dessus tout le reste.
        if (splashDone && showRoulette) {
            RouletteReward(onClaim = onRouletteClaim, onClose = onRouletteClose)
        }
        if (!splashDone) SplashScreen(onFinished = { splashDone = true })
    }
}

@Composable
fun MainScreen(
    ui: MiningUi,
    pool: PoolStats?,
    history: List<HashPoint>,
    device: DeviceInfo,
    actions: Actions
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = tab != 0) { tab = 0 }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Fond, FondBas)))
            .systemBarsPadding()
    ) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> HomeTab(ui, pool, device) { tab = it }
                1 -> MineTab(ui, pool, history, device, actions)
                2 -> WalletTab(pool, device, actions)
                3 -> DevicesTab()
                else -> MoreTab(ui, device, actions)
            }
        }
        BottomBar(tab) { tab = it }
    }
}
