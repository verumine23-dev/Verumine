package com.monchamp.verusfarm.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monchamp.verusfarm.Catalog
import com.monchamp.verusfarm.Config
import com.monchamp.verusfarm.Country
import com.monchamp.verusfarm.Crypto
import com.monchamp.verusfarm.Cryptos
import com.monchamp.verusfarm.Diagnostics
import com.monchamp.verusfarm.DeviceIdentity
import com.monchamp.verusfarm.Heartbeat
import com.monchamp.verusfarm.HashPoint
import com.monchamp.verusfarm.MiningUi
import com.monchamp.verusfarm.PayoutMethod
import com.monchamp.verusfarm.PoolStats
import com.monchamp.verusfarm.PowerMode
import com.monchamp.verusfarm.Prefs
import com.monchamp.verusfarm.Rates
import com.monchamp.verusfarm.RewardNotifier
import com.monchamp.verusfarm.Rewards
import com.monchamp.verusfarm.Simulation
import com.monchamp.verusfarm.Supa
import com.monchamp.verusfarm.Tone
import com.monchamp.verusfarm.Withdraw
import com.monchamp.verusfarm.WithdrawRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =============================================================== ACCUEIL
@Composable
fun HomeTab(ui: MiningUi, pool: PoolStats?, device: DeviceInfo, goTab: (Int) -> Unit) {
    val ctx = LocalContext.current

    var sim by remember { mutableStateOf(Simulation.value(ctx)) }
    var energy by remember { mutableStateOf(Rewards.energy(ctx)) }
    var taps by remember { mutableStateOf(Prefs.tapCount(ctx)) }
    var xp by remember { mutableStateOf(Prefs.xp(ctx)) }
    val pluses = remember { mutableStateListOf<Long>() }
    val gifts = remember { mutableStateListOf<Long>() }
    var tapMsg by remember { mutableStateOf<String?>(null) }
    var lastTap by remember { mutableStateOf(0L) }
    var nextId by remember { mutableStateOf(0L) }
    var online by remember { mutableStateOf(true) }
    var showOffline by remember { mutableStateOf(false) }

    // Rafraîchit le compteur et l'énergie tant que l'écran est visible (rythme calme).
    LaunchedEffect(Unit) {
        while (true) {
            online = Heartbeat.hasInternet(ctx)
            sim = Simulation.value(ctx)      // ne progresse que pendant le minage réel (donc gelé hors ligne)
            energy = Rewards.energy(ctx)
            taps = Prefs.tapCount(ctx)
            xp = Prefs.xp(ctx)
            delay(500)
        }
    }

    val level = Rewards.level(ctx)
    val giftProgress = (taps % Rewards.TAPS_PER_GIFT).toFloat() / Rewards.TAPS_PER_GIFT.toFloat()
    val giftsToward = ((taps % Rewards.TAPS_PER_LEVEL) / Rewards.TAPS_PER_GIFT).toInt()
    val levelProgress = (taps % Rewards.TAPS_PER_LEVEL).toFloat() / Rewards.TAPS_PER_LEVEL.toFloat()

    fun onTap() {
        // Hors ligne : on bloque le tapotage et on invite à se connecter.
        if (!Heartbeat.hasInternet(ctx)) { online = false; showOffline = true; return }
        val now = System.currentTimeMillis()
        if (now - lastTap < 120L) return          // anti-abus : pas de rafales
        lastTap = now
        val res = Rewards.tap(ctx)
        if (!res.ok) {
            tapMsg = "Plus d'énergie. Reviens dans quelques instants pour profiter du tapotage."
            return
        }
        tapMsg = null
        pluses.add(nextId++)
        if (res.giftUnlocked) gifts.add(nextId++)
        RewardNotifier.scheduleEnergyFull(ctx)
        if (res.leveledUp) RewardNotifier.notifyReward(ctx, res.newLevel)
        energy = Rewards.energy(ctx)
        taps = Prefs.tapCount(ctx)
        xp = Prefs.xp(ctx)
        sim = Simulation.value(ctx)
    }

  Box(Modifier.fillMaxSize()) {
    TabColumn {
        BrandHeader(ui.tone)

        if (ui.tone == Tone.ERROR || !ui.engineOk) {
            NeonCard {
                Text(
                    if (!ui.engineOk) "Minage impossible sur cet appareil" else "Une erreur est survenue",
                    fontWeight = FontWeight.Bold, color = Corail
                )
                Text(ui.status, fontSize = 13.sp, color = Texte)
                Text("Ouvre Plus, puis Diagnostic, pour voir la cause exacte.", fontSize = 12.sp, color = TexteDoux)
            }
        }

        // Titre + pastille de récompense (toujours visible, sans scroller)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mon champ de minage", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Texte)
                val n = pool?.workers?.size ?: 0
                Text(
                    if (n > 0) "$n ${if (n > 1) "appareils connectés" else "appareil connecté"} au réseau"
                    else "On mine ensemble pour un meilleur avenir.",
                    fontSize = 13.sp, color = TexteDoux
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RewardOrb(progress = giftProgress, gifts = giftsToward)
                Text("$giftsToward/3", fontSize = 10.sp, color = Lavande)
            }
        }

        // Compteur SIMULÉ (clairement étiqueté : ce n'est pas un solde retirable)
        NeonCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Activité estimée (simulation)", fontSize = 13.sp, color = TexteDoux)
                    Text(
                        "${fmtSim(sim)} VRSC",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Texte
                    )
                    Text(
                        "Illustration du minage en arrière-plan. Ton solde réel se trouve dans Wallet.",
                        fontSize = 11.sp, color = Lavande
                    )
                }
                LogoMark(Modifier.height(64.dp))
            }
        }

        // Zone de tapotage
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            TapButton(
                cores = device.cores,
                activeCores = if (ui.enabled && ui.tone == Tone.OK) device.cores else device.cores / 2,
                enabled = energy >= Rewards.TAP_COST,
                centerTop = "TAP",
                centerBottom = if (energy >= Rewards.TAP_COST) "Tape pour gagner" else "Recharge…",
                onTap = { onTap() }
            )
            // Les « +2 XP » qui remontent vers le compteur
            pluses.forEach { id ->
                key(id) { FloatingPlus(onEnd = { pluses.remove(id) }) }
            }
        }

        // Barre d'énergie
        NeonCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Énergie de tapotage", fontSize = 13.sp, color = TexteDoux, modifier = Modifier.weight(1f))
                Text("${energy.toInt()} %", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (energy >= Rewards.TAP_COST) Menthe else Ambre)
            }
            EnergyBar(energy)
            Text(
                "Pleine, la barre permet au moins 150 tapotages. Elle se recharge toute seule (pleine en 1 heure).",
                fontSize = 11.sp, color = TexteDoux
            )
            if (tapMsg != null) Text(tapMsg!!, fontSize = 12.sp, color = Ambre)
        }

        // Compteur de récompenses (se remplit après 3 cadeaux = 450 tapotages)
        NeonCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Niveau $level", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Texte)
                    Text("$xp XP · $giftsToward/3 cadeaux vers le prochain niveau", fontSize = 12.sp, color = TexteDoux)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Violet.copy(alpha = 0.25f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(VIcons.Gift, contentDescription = null, tint = Lavande, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Récompenses", fontSize = 12.sp, color = Lavande)
                    }
                }
            }
            XpBar(levelProgress)
            Text(Rewards.perkLabel(level), fontSize = 12.sp, color = Menthe)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Hashrate", ui.hashrate, Modifier.weight(1f))
            val online = pool?.workers?.count { it.online } ?: 0
            val total = pool?.workers?.size ?: 0
            StatCard(
                "Appareils actifs",
                if (pool != null && total > 0) "$online/$total" else if (ui.enabled) "1/1" else "0/1",
                Modifier.weight(1f)
            )
            StatCard("Temps de minage", fmtDuration(ui.miningMs), Modifier.weight(1f))
        }

        AddDeviceCard { goTab(3) }
    }

    // Cadeaux qui s'envolent de la pastille vers le compteur de récompenses, en bas
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        gifts.forEach { id ->
            key(id) { GiftFly(onEnd = { gifts.remove(id) }) }
        }
    }

    // Fenêtre « connecte-toi » quand on tape hors ligne
    if (showOffline) {
        AlertDialog(
            onDismissRequest = { showOffline = false },
            containerColor = CarteHaute,
            title = { Text("Pas de connexion internet", color = Texte, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Connecte-toi à internet pour commencer. Le tapotage et l'activité de minage " +
                        "reprennent dès que la connexion revient.",
                    color = TexteDoux, fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showOffline = false }) { Text("Compris") }
            }
        )
    }
  }
}

@Composable
private fun AddDeviceCard(onClick: () -> Unit) {
    NeonCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Violet.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) { Icon(VIcons.Phone, null, tint = Lavande, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Ajouter un appareil", fontWeight = FontWeight.Bold, color = Texte)
                Text(
                    "Installe Verumine sur un autre téléphone : il rejoindra automatiquement ton champ de minage.",
                    fontSize = 12.sp, color = TexteDoux
                )
            }
            Icon(VIcons.Chevron, null, tint = Lavande)
        }
    }
}

// =============================================================== MINAGE
@Composable
fun MineTab(ui: MiningUi, pool: PoolStats?, history: List<HashPoint>, device: DeviceInfo, actions: Actions) {
    var range by remember { mutableStateOf(24) }
    var power by remember { mutableStateOf(device.mode) }
    TabColumn {
        TabHeader("Minage", "Règle la puissance et laisse tourner")

        NeonCard {
            Text("Statut du minage", fontSize = 13.sp, color = TexteDoux)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(toneColor(ui.tone), 10)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        !ui.enabled -> "Arrêté"
                        ui.tone == Tone.OK -> "Actif"
                        ui.tone == Tone.WARN -> "En attente"
                        else -> "Erreur"
                    },
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = toneColor(ui.tone), modifier = Modifier.weight(1f)
                )
                Text("VerusHash", fontSize = 12.sp, color = Lavande)
            }
            Text(ui.status, fontSize = 13.sp, color = TexteDoux)
        }

        NeonCard {
            SectionTitle("Puissance de calcul")
            Text(
                "Choisis la part du téléphone utilisée pour le minage. En « Faible », le téléphone reste " +
                    "fluide pour tes autres applications ; en « Max », toute la puissance est utilisée.",
                fontSize = 12.sp, color = TexteDoux
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Chip("Faible", power == PowerMode.ECO, Modifier.weight(1f)) {
                    power = PowerMode.ECO; actions.onSetPower(PowerMode.ECO)
                }
                Chip("Moyen", power == PowerMode.BALANCED, Modifier.weight(1f)) {
                    power = PowerMode.BALANCED; actions.onSetPower(PowerMode.BALANCED)
                }
                Chip("Max", power == PowerMode.MAX, Modifier.weight(1f)) {
                    power = PowerMode.MAX; actions.onSetPower(PowerMode.MAX)
                }
            }
            Spacer(Modifier.height(8.dp))
            val desc = when (power) {
                PowerMode.ECO -> "Faible : environ un quart des cœurs. Idéal pour utiliser le téléphone en même temps."
                PowerMode.BALANCED -> "Moyen : environ la moitié des cœurs. Bon équilibre entre gains et confort."
                PowerMode.MAX -> "Max : tous les cœurs (100 %). Gains maximum, téléphone plus sollicité et plus chaud."
            }
            Text(desc, fontSize = 12.sp, color = Lavande)
        }

        SectionTitle("Statistiques en temps réel")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                "Hashrate total",
                if (pool != null) fmtHash(pool.hashrate) else "—",
                Modifier.weight(1f),
                hint = if (pool != null) "moy. 24h ${fmtHash(pool.avg24h)}" else null,
                hintColor = TexteDoux
            )
            StatCard(
                "Parts validées",
                ui.accepted.toString(),
                Modifier.weight(1f),
                hint = if (ui.rejected > 0) "${ui.rejected} rejetées" else "aucune rejetée",
                hintColor = if (ui.rejected > 0) Ambre else Menthe
            )
            StatCard(
                "Efficacité",
                if (pool != null) "${"%.1f".format(java.util.Locale.US, pool.efficiency)} %" else "—",
                Modifier.weight(1f)
            )
        }

        NeonCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Évolution du hashrate", Modifier.weight(1f))
                listOf(1, 6, 24).forEach { h ->
                    Chip("${h}h", range == h) { range = h }
                    Spacer(Modifier.width(6.dp))
                }
            }
            HashChart(history, range)
        }

        NeonCard {
            SectionTitle("Paramètres de minage")
            InfoRow("Algorithme", "VerusHash 2.1 (VRSC)")
            InfoRow("Puissance utilisée", ui.hashrate)
            InfoRow("Cœurs actifs", "${ui.threads} sur ${device.cores}")
            val hot = ui.tempC >= 41f
            InfoRow(
                "Température",
                if (ui.tempC > 0f) "${ui.tempC.toInt()} °C  ${if (hot) "Chaud" else "OK"}" else "—",
                if (hot) Ambre else Menthe
            )
            InfoRow("Serveur", if (ui.pool != "—") ui.pool else "—")
            Text(
                "Le minage suit la puissance choisie ci-dessus et se réduit tout seul si le téléphone chauffe trop. " +
                    "La connexion à LuckPool se rafraîchit automatiquement toutes les 100 parts validées.",
                fontSize = 12.sp, color = TexteDoux
            )
        }
    }
}

@Composable
private fun HashChart(history: List<HashPoint>, hours: Int) {
    val last = history.lastOrNull()?.tsSec ?: 0L
    val inRange = history.filter { it.tsSec >= last - hours * 3600L }
    val step = (inRange.size / 120).coerceAtLeast(1)
    val pts = inRange.filterIndexed { i, _ -> i % step == 0 }

    if (pts.size < 2) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text("Pas encore de données à afficher.", color = TexteDoux, fontSize = 13.sp)
        }
        return
    }
    val maxV = (pts.maxOf { it.hashrate } * 1.1).coerceAtLeast(1.0)
    Box(Modifier.fillMaxWidth().height(160.dp)) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(Bord.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            val minT = pts.first().tsSec
            val spanT = (pts.last().tsSec - minT).coerceAtLeast(1L)
            val line = Path()
            val fill = Path()
            pts.forEachIndexed { i, p ->
                val x = (p.tsSec - minT).toFloat() / spanT.toFloat() * size.width
                val y = size.height - (p.hashrate / maxV).toFloat() * size.height
                if (i == 0) {
                    line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y)
                } else {
                    line.lineTo(x, y); fill.lineTo(x, y)
                }
            }
            fill.lineTo(size.width, size.height); fill.close()
            drawPath(fill, brush = Brush.verticalGradient(listOf(Violet.copy(alpha = 0.40f), Color.Transparent)))
            drawPath(line, color = Neon, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Text(fmtHash(maxV), fontSize = 10.sp, color = TexteDoux, modifier = Modifier.align(Alignment.TopStart))
    }
}

// =============================================================== WALLET
@Composable
fun WalletTab(pool: PoolStats?, device: DeviceInfo, actions: Actions) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Solde basé sur les calculs acceptés par Luckpool pour CET appareil (voir Earnings.kt).
    // Recalculé à chaque rafraîchissement du pool.
    val available = remember(pool) { Prefs.availableVrsc(ctx) }
    val acceptedShares = remember(pool) { Prefs.acceptedShares(ctx) }
    val pricePer10 = remember(pool) { Prefs.pricePer10(ctx) }

    var method by remember { mutableStateOf(PayoutMethod.MOBILE_MONEY) }
    var country by remember {
        mutableStateOf(Catalog.country(Prefs.lastCountry(ctx)) ?: Catalog.momoCountries.firstOrNull())
    }
    var crypto by remember { mutableStateOf<Crypto?>(Cryptos.of("VRSC")) }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var inVrsc by remember { mutableStateOf(true) }
    var picker by remember { mutableStateOf("") }   // "", "country", "crypto"
    var busy by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(false) }
    var ratesReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Rates.refresh() }
        ratesReady = true
    }

    val curCode = if (method == PayoutMethod.MOBILE_MONEY) (country?.currency ?: "USD") else "USD"
    val amt = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val amountVrsc = if (inVrsc) amt else Rates.currencyAmountToVrsc(amt, curCode)
    val amountCur = if (inVrsc) Rates.vrscAmountToCurrency(amt, curCode) else amt
    val feePct = Rewards.effectiveFeePercent(ctx)
    val discount = Rewards.feeDiscountPercent(ctx)
    val netVrsc = (amountVrsc * (1.0 - feePct / 100.0)).coerceAtLeast(0.0)

    TabColumn {
        TabHeader("Wallet", "Retire tes gains, partout dans le monde")

        NeonCard {
            Text("Solde disponible", fontSize = 13.sp, color = TexteDoux)
            Text("${fmtVrsc(available)} VRSC", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Texte)
            if (ratesReady) Text("≈ ${fmtMoney(Rates.vrscAmountToCurrency(available, curCode))} $curCode", fontSize = 12.sp, color = Menthe)
            InfoRow("Calculs acceptés", "${acceptedShares.toLong()}")
            if (pricePer10 > 0.0) InfoRow("Valeur de 10 calculs", "${fmtVrsc(pricePer10)} VRSC")
            Text(
                "Ton solde est calculé à partir de tes calculs acceptés par le réseau. La valeur reste sous " +
                    "la vraie récompense du pool.",
                fontSize = 11.sp, color = TexteDoux
            )
            if (pool == null) Text("Connexion au réseau en cours…", fontSize = 12.sp, color = Ambre)
        }

        // Choix du moyen de retrait
        NeonCard {
            SectionTitle("Demander un retrait")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Mobile Money", method == PayoutMethod.MOBILE_MONEY, Modifier.weight(1f)) {
                    method = PayoutMethod.MOBILE_MONEY; resultMsg = null
                }
                Chip("Crypto", method == PayoutMethod.CRYPTO, Modifier.weight(1f)) {
                    method = PayoutMethod.CRYPTO; resultMsg = null
                }
            }

            if (method == PayoutMethod.MOBILE_MONEY) {
                FieldRow("Pays", country?.let { "${it.flag}  ${it.name}" } ?: "Choisir un pays") { picker = "country" }
                FormField(phone, { phone = it }, "Numéro ${country?.let { "(+${it.dial})" } ?: ""}", KeyboardType.Phone)
            } else {
                FieldRow("Cryptomonnaie", crypto?.let { "${it.ticker} — ${it.name}" } ?: "Choisir") { picker = "crypto" }
                FormField(address, { address = it }, "Adresse de réception ${crypto?.network?.let { "($it)" } ?: ""}")
                FieldRow("Pays", country?.let { "${it.flag}  ${it.name}" } ?: "Choisir un pays") { picker = "country2" }
            }

            // Montant + bascule VRSC / devise
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("En VRSC", inVrsc, Modifier.weight(1f)) { inVrsc = true }
                Chip("En $curCode", !inVrsc, Modifier.weight(1f)) { inVrsc = false }
            }
            FormField(amountText, { amountText = it }, "Montant (${if (inVrsc) "VRSC" else curCode})", KeyboardType.Decimal)

            // Conversion affichée
            if (amt > 0.0 && ratesReady) {
                InfoRow("Équivalent", if (inVrsc) "${fmtMoney(amountCur)} $curCode" else "${fmtVrsc(amountVrsc)} VRSC")
            }

            FormField(first, { first = it }, "Prénom")
            FormField(last, { last = it }, "Nom")
            FormField(email, { email = it }, "Adresse e-mail", KeyboardType.Email)

            // Récapitulatif des frais
            InfoRow("Frais de l'administrateur", "${fmtPct(Config.ADMIN_FEE_PERCENT)}")
            if (discount > 0.0) InfoRow("Réduction fidélité (niveau)", "− ${fmtPct(discount)}", Menthe)
            InfoRow("Frais appliqués", fmtPct(feePct))
            InfoRow("Tu reçois (net)", "${fmtVrsc(netVrsc)} VRSC", Menthe)

            resultMsg?.let { Text(it, fontSize = 13.sp, color = if (resultOk) Menthe else Corail) }

            Button(
                onClick = {
                    resultMsg = null
                    val req = WithdrawRequest(
                        method = method,
                        amountVrsc = amountVrsc,
                        feePercent = feePct,
                        netVrsc = netVrsc,
                        countryCode = country?.code ?: "",
                        countryName = country?.name ?: "",
                        phone = phone,
                        currency = curCode,
                        amountCurrency = amountCur,
                        crypto = crypto?.ticker ?: "",
                        cryptoAddress = address,
                        firstName = first,
                        lastName = last,
                        email = email,
                        worker = DeviceIdentity.workerName(ctx)
                    )
                    val err = Withdraw.validate(req, available)
                    if (err != null) { resultMsg = err; resultOk = false; return@Button }
                    busy = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { Withdraw.send(req) }
                        busy = false
                        when (r) {
                            is Withdraw.Result.Ok -> {
                                Withdraw.add(ctx, req)
                                country?.let { Prefs.setLastCountry(ctx, it.code) }
                                resultOk = true
                                resultMsg = "Demande envoyée à l'administrateur. Statut : En attente. Tu seras payé sous peu."
                                amountText = ""
                            }
                            is Withdraw.Result.Error -> { resultOk = false; resultMsg = r.message }
                        }
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Envoi en cours…" else "Envoyer la demande de retrait") }
        }

        // Explication du nouveau système
        NeonCard {
            SectionTitle("Comment sont versés les gains ?")
            Text(
                "Tous les appareils minent vers le compte de Verumine. Pour toucher tes gains, tu fais une demande de " +
                    "retrait ici : par Mobile Money (dans les pays pris en charge) ou en crypto (partout dans le monde, " +
                    "avec ta propre adresse de réception).",
                fontSize = 13.sp, color = TexteDoux
            )
            Text(
                "La demande est transmise à l'administrateur, qui prélève ${fmtPct(Config.ADMIN_FEE_PERCENT)} de frais et " +
                    "t'envoie le reste par le moyen que tu as choisi. Dès qu'il valide, le statut change ici.",
                fontSize = 13.sp, color = TexteDoux
            )
            Text(
                "Astuce : plus ton niveau est élevé, plus tes frais baissent (jusqu'à −15 %). Tape chaque jour pour gagner de l'XP.",
                fontSize = 12.sp, color = Lavande
            )
        }

        // Historique
        val hist = remember(resultMsg) { Withdraw.history(ctx) }
        if (hist.isNotEmpty()) {
            NeonCard {
                SectionTitle("Mes demandes")
                hist.forEach { w ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${fmtVrsc(w.amountVrsc)} VRSC — ${if (w.method == PayoutMethod.CRYPTO) w.crypto else "Mobile Money"}",
                                color = Texte, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (w.method == PayoutMethod.CRYPTO) w.cryptoAddress.take(18) + "…"
                                else "${w.countryName} · ${w.phone}",
                                color = TexteDoux, fontSize = 11.sp
                            )
                        }
                        Text(w.status, fontSize = 12.sp, color = if (w.status.contains("payé", true)) Menthe else Ambre)
                    }
                }
            }
        }
    }

    when {
        picker == "country" -> PickerDialog(
            "Choisis ton pays", Catalog.momoCountries, { "${it.flag}  ${it.name} (+${it.dial})" },
            onPick = { country = it; picker = "" }, onDismiss = { picker = "" }
        )
        picker == "country2" -> PickerDialog(
            "Choisis ton pays", Catalog.countries, { "${it.flag}  ${it.name}" },
            onPick = { country = it; picker = "" }, onDismiss = { picker = "" }
        )
        picker == "crypto" -> PickerDialog(
            "Choisis une cryptomonnaie", Cryptos.list, { "${it.ticker} — ${it.name} (${it.network})" },
            onPick = { crypto = it; picker = "" }, onDismiss = { picker = "" }
        )
    }
}

// =============================================================== APPAREILS
@Composable
fun DevicesTab(actions: Actions) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf(Prefs.referralCode(ctx)) }
    // Récupère le VRAI code de parrainage depuis le serveur (celui que les filleuls pourront utiliser).
    LaunchedEffect(Unit) {
        val c = Supa.fetchMyReferralCode(ctx)
        if (!c.isNullOrBlank()) { Prefs.setReferralCode(ctx, c); code = c }
    }

    TabColumn {
        TabHeader("Mes appareils", "Tes appareils et ton parrainage")

        // ---- Carte Parrainage : code propre à l'utilisateur + partage ----
        NeonCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Violet.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) { Icon(VIcons.Group, contentDescription = null, tint = Lavande, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ton code de parrainage", fontWeight = FontWeight.Bold, color = Texte)
                    Text("Invite tes amis et gagne des récompenses", fontSize = 12.sp, color = TexteDoux)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CarteHaute)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(code, color = Neon, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Text("Copier", color = Lavande, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { clipboard.setText(AnnotatedString(code)) })
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val msg = "Rejoins-moi sur Verumine et mine de la crypto depuis ton téléphone ! " +
                        "Utilise mon code de parrainage : $code"
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, msg)
                    }
                    try { ctx.startActivity(android.content.Intent.createChooser(send, "Partager mon code")) } catch (_: Exception) {}
                },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
            ) { Text("Partager mon code") }
        }

        // ---- Liste des filleuls (sera remplie par le serveur) ----
        NeonCard {
            SectionTitle("Mes filleuls")
            Text(
                "Les personnes qui s'inscrivent avec ton code apparaîtront ici. " +
                    "Partage ton code pour commencer !",
                fontSize = 13.sp, color = TexteDoux
            )
        }

        // ---- Cet appareil ----
        NeonCard {
            SectionTitle("Cet appareil")
            InfoRow("Nom sur le réseau", DeviceIdentity.workerName(ctx))
            InfoRow("Type", "Téléphone Android")
        }
    }
}

// =============================================================== PLUS
@Composable
fun MoreTab(ui: MiningUi, device: DeviceInfo, actions: Actions) {
    var showLogs by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    TabColumn {
        TabHeader("Plus", "Réglages et protections")

        NeonCard {
            SectionTitle("Autorisations — pour un fonctionnement à 100 %")
            Text(
                "Chaque bouton qui clignote n'est pas encore accordé. Appuie dessus : un guide " +
                    "adapté à ta marque de téléphone t'indiquera exactement où appuyer.",
                fontSize = 12.sp, color = TexteDoux
            )
            Spacer(Modifier.height(8.dp))
            PermissionsPanel(checks = device.checks, actions = actions)
        }

        NeonCard {
            SectionTitle("Cet appareil")
            InfoRow("Nom sur le réseau", DeviceIdentity.workerName(ctx))
            InfoRow("Processeur", "${device.abi}, ${device.cores} cœurs")
            Text(
                "Ce nom identifie ton téléphone sur le réseau de minage. Il est unique et attribué " +
                    "automatiquement ; l'adresse de récompense est gérée par l'application.",
                fontSize = 12.sp, color = TexteDoux
            )
        }

        NeonCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Journal du mineur", Modifier.weight(1f))
                TextButton(onClick = { showLogs = !showLogs }) { Text(if (showLogs) "Masquer" else "Afficher") }
            }
            if (showLogs) {
                Text(
                    if (ui.logs.isEmpty()) "Aucun message pour le moment." else ui.logs.takeLast(25).joinToString("\n"),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TexteDoux
                )
            }
        }

        NeonCard {
            SectionTitle("Diagnostic")
            Text("À utiliser si le minage ne démarre pas : vérifie le processeur et teste le programme de minage.", fontSize = 13.sp, color = TexteDoux)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            report = withContext(Dispatchers.IO) { Diagnostics.build(ctx) }
                            busy = false
                        }
                    },
                    enabled = !busy, shape = RoundedCornerShape(12.dp)
                ) { Text(if (busy) "Test en cours…" else "Lancer le diagnostic") }
                if (report.isNotEmpty()) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(report)) }, shape = RoundedCornerShape(12.dp)) { Text("Copier") }
                }
            }
            if (report.isNotEmpty()) Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TexteDoux)
        }

        NeonCard {
            SectionTitle("À propos")
            Text(
                "Verumine : un champ de minage Verus (VRSC). Tous tes appareils minent ensemble et tu retires tes gains " +
                    "depuis la page Wallet.",
                fontSize = 13.sp, color = TexteDoux
            )
            Text("Version ${Diagnostics.appVersion(ctx)}", fontSize = 12.sp, color = Brume)
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun fmtPct(v: Double): String = "${"%.0f".format(java.util.Locale.US, v)} %"
