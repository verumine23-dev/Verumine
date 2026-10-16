package com.monchamp.verusfarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    ui: MiningUi,
    pool: PoolStats?,
    history: List<HashPoint>,
    device: DeviceInfo,
    actions: Actions
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    // Écran secondaire ouvert par-dessus les onglets : null | "notifications" | "more"
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var showMoreSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    BackHandler(enabled = overlay != null || tab != 0) {
        if (overlay != null) overlay = null else tab = 0
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Fond, FondBas)))
            .systemBarsPadding()
    ) {
        Box(Modifier.weight(1f)) {
            when (overlay) {
                "notifications" -> NotificationsScreen(onBack = { overlay = null })
                "more" -> Column(Modifier.fillMaxSize()) {
                    BackRow("Réglages & autorisations") { overlay = null }
                    MoreTab(ui, device, actions)
                }
                else -> {
                    when (tab) {
                        0 -> HomeTab(ui, pool, device) { tab = it }
                        1 -> MineTab(ui, pool, history, device, actions)
                        2 -> WalletTab(pool, device, actions)
                        else -> DevicesTab(actions)
                    }
                    // Cloche flottante en haut à droite (sur les onglets uniquement)
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp)) {
                        BellButton(onClick = { overlay = "notifications" })
                    }
                }
            }
        }
        BottomBar(tab) { i ->
            if (i == 4) { showMoreSheet = true }      // « Plus » ouvre la fenêtre (menu)
            else { tab = i; overlay = null }
        }
    }

    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = sheetState,
            containerColor = CarteHaute
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                Text("Plus", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Texte,
                    modifier = Modifier.padding(bottom = 10.dp))
                SheetRow("🔔", "Notifications") { showMoreSheet = false; overlay = "notifications" }
                SheetRow("👥", "Parrainage & filleuls") { showMoreSheet = false; overlay = null; tab = 3 }
                SheetRow("⚙️", "Réglages & autorisations") { showMoreSheet = false; overlay = "more" }
                SheetRow("👤", "Profil", enabled = false) {}
                SheetRow("🏆", "Classement", enabled = false) {}
                SheetRow("🎯", "Missions", enabled = false) {}
            }
        }
    }
}

// Ligne du menu « Plus »
@Composable
private fun SheetRow(icon: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (enabled) Texte else Brume, fontSize = 15.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (!enabled) Text("Bientôt", color = Brume, fontSize = 11.sp)
        else Text("›", color = Lavande, fontSize = 18.sp)
    }
}

// En-tête avec bouton retour pour les écrans secondaires
@Composable
private fun BackRow(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(CarteHaute).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("←", color = Texte, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Texte)
    }
}
