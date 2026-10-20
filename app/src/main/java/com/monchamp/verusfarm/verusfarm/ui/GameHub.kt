package com.monchamp.verusfarm.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monchamp.verusfarm.GameApi
import com.monchamp.verusfarm.Heartbeat
import kotlinx.coroutines.launch

// Catalogue affiché TOUJOURS (image + nom), même hors ligne ou si le serveur ne répond pas.
private data class GameDef(val key: String, val name: String)
private val GAME_CATALOG = listOf(
    GameDef("mot", "Devinette de mots"),
    GameDef("quiz", "Quiz des sujets"),
    GameDef("calcul", "Calcul Éclair"),
    GameDef("suite", "Suite Logique")
)

/**
 * Bouton flottant « jeux » : une pastille animée (petite secousse répétée pour
 * attirer l'œil) posée juste au-dessus de la barre du bas, côté droit, sans
 * recouvrir les boutons du menu.
 */
@Composable
fun GameFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val trans = rememberInfiniteTransition(label = "fab")
    val angle by trans.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                0f at 0; -14f at 90; 13f at 180; -11f at 270
                9f at 360; -6f at 450; 5f at 540; 0f at 640
                0f at 2400   // repos
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )
    val pulse by trans.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                1f at 0; 1.12f at 120; 1f at 300; 1f at 2400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    Box(
        modifier = modifier
            .size(60.dp)
            .graphicsLayer { rotationZ = angle; scaleX = pulse; scaleY = pulse }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFF5B942), Color(0xFFFF7A93), Color(0xFFA855F7))))
            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(VIcons.Casino, contentDescription = "Jeux", tint = Color(0xFF1A0F3D), modifier = Modifier.size(30.dp))
    }
}

/**
 * Fenêtre « jeux » (plus longue que le menu Plus) : solde en devise locale,
 * bouton Retirer, et la grille des jeux.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHubSheet(
    reloadKey: Int,
    onDismiss: () -> Unit,
    onOpenGame: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var state by remember { mutableStateOf<GameApi.State?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showOffline by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        state = GameApi.getState(ctx)
        loading = false
    }
    LaunchedEffect(reloadKey) { reload() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CarteHaute
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp)
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(VIcons.Game, contentDescription = null, tint = Color(0xFFF5C84B), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Espace jeux", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Texte)
            }

            val s = state
            val locked = s?.lockedUntil != null

            // ---- Carte solde + retrait ----
            NeonCard {
                Text("Ton solde de jeu", fontSize = 13.sp, color = TexteDoux)
                if (loading) {
                    Text("…", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Texte)
                } else if (s == null) {
                    Text("— —", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Brume)
                    Text(
                        "Solde indisponible : connecte-toi pour le charger. Tu peux déjà voir les jeux ci-dessous.",
                        fontSize = 12.sp, color = TexteDoux
                    )
                } else {
                    Text(
                        GameApi.formatLocal(s.balanceCents, s.currency, s.fxPerUsd),
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Menthe
                    )
                    Text("≈ ${GameApi.formatUsd(s.balanceCents)}", fontSize = 12.sp, color = TexteDoux)
                    Spacer(Modifier.height(4.dp))
                    val reached = s.balanceCents >= s.minWithdrawCents
                    InfoRow(
                        "Retrait minimum",
                        GameApi.formatUsd(s.minWithdrawCents),
                        valueColor = if (reached) Menthe else Ambre
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { showWithdraw = true },
                        enabled = reached,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Menthe.copy(alpha = 0.9f)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            if (reached) "Retirer mes gains" else "Atteins 100 \$ pour retirer",
                            fontWeight = FontWeight.Bold, color = Color(0xFF07021A)
                        )
                    }
                }
            }

            if (banner != null) {
                Text(banner!!, color = Menthe, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }

            // ---- Message de blocage éventuel ----
            if (locked) {
                NeonCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(VIcons.Timer, contentDescription = null, tint = Ambre, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Accès en pause", fontWeight = FontWeight.Bold, color = Ambre)
                    }
                    Text(
                        "Tu as épuisé ton quota ou enchaîné 3 pertes. Les jeux se rouvrent dans 24 h. " +
                            "Reviens plus tard !",
                        fontSize = 13.sp, color = TexteDoux
                    )
                }
            }

            // ---- Règles courtes ----
            Text(
                "0,50 \$ par manche gagnée • 3 manches par jeu • 3 pertes d'affilée = pause 24 h • connexion obligatoire",
                fontSize = 11.sp, color = Brume, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // ---- Grille des jeux (2 colonnes) : TOUJOURS visible ----
            GAME_CATALOG.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { def ->
                        val info = s?.games?.firstOrNull { it.key == def.key }
                        GameCard(
                            def = def,
                            info = info,
                            locked = locked,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                // Les jeux sont visibles, mais on ne joue qu'en ligne.
                                if (!Heartbeat.hasInternet(ctx)) {
                                    showOffline = true
                                } else if (!locked && info?.closed != true) {
                                    onOpenGame(def.key)
                                }
                            }
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (showOffline) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOffline = false },
            containerColor = CarteHaute,
            title = { Text("Pas de connexion", color = Texte) },
            text = {
                Text(
                    "Connecte-toi à internet ou attends la connexion pour commencer à jouer. " +
                        "Les jeux restent visibles, mais une manche a besoin du réseau.",
                    color = TexteDoux, fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showOffline = false }) { Text("Compris") }
            }
        )
    }

    if (showWithdraw && state != null) {
        WithdrawDialog(
            state = state!!,
            onDismiss = { showWithdraw = false },
            onDone = { msg ->
                showWithdraw = false
                banner = msg
                scope.launch { reload() }
            }
        )
    }
}

@Composable
private fun GameCard(
    def: GameDef,
    info: GameApi.GameInfo?,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val closed = info?.closed == true
    // La carte reste colorée et cliquable ; seul un jeu "fermé" ou en pause est atténué.
    val dim = locked || closed
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Carte, Color(0xFF0E0728))))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Violet.copy(alpha = if (dim) 0.25f else 0.75f), Bord.copy(alpha = 0.5f))),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val ctx = LocalContext.current
        val imgId = remember(def.key) { ctx.resources.getIdentifier("game_${def.key}", "drawable", ctx.packageName) }
        Box(Modifier.graphicsLayer { alpha = if (dim) 0.4f else 1f }, contentAlignment = Alignment.Center) {
            if (imgId != 0) Image(
                painter = painterResource(imgId), contentDescription = null,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp))
            ) else Icon(
                VIcons.gameIcon(def.key), contentDescription = null,
                tint = Color(0xFFF5C84B), modifier = Modifier.size(48.dp)
            )
        }
        Text(
            def.name, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (dim) Brume else Texte, textAlign = TextAlign.Center, maxLines = 2
        )
        when {
            locked -> Text("En pause (24 h)", fontSize = 11.sp, color = Ambre, textAlign = TextAlign.Center)
            closed -> Text("Terminé — change de jeu", fontSize = 11.sp, color = Ambre, textAlign = TextAlign.Center)
            info != null -> Text("${info.remaining} manche(s) restante(s)", fontSize = 11.sp, color = Menthe)
            else -> Text("Jouer", fontSize = 11.sp, color = Menthe)
        }
    }
}

@Composable
private fun WithdrawDialog(
    state: GameApi.State,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = CarteHaute,
        title = { Text("Retirer mes gains", color = Texte) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Montant : ${GameApi.formatLocal(state.balanceCents, state.currency, state.fxPerUsd)} " +
                        "(≈ ${GameApi.formatUsd(state.balanceCents)}).",
                    fontSize = 13.sp, color = TexteDoux
                )
                Text(
                    "Le traitement suit le même principe que le retrait du minage : ta demande est " +
                        "envoyée à l'administrateur, qui effectue le paiement.",
                    fontSize = 12.sp, color = Brume
                )
                FormField(method, { method = it }, "Moyen (MTN MoMo, Moov, carte…)")
                FormField(details, { details = it }, "Numéro / coordonnées de paiement")
                if (error != null) Text(error!!, color = Corail, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && method.isNotBlank() && details.isNotBlank(),
                onClick = {
                    error = null; busy = true
                    scope.launch {
                        val r = GameApi.requestWithdrawal(ctx, method.trim(), details.trim())
                        busy = false
                        if (r.ok) onDone("Demande de retrait envoyée")
                        else error = when (r.error) {
                            "too_low" -> "Il te faut au moins 100 \$ pour retirer."
                            else -> "Échec de l'envoi, réessaie."
                        }
                    }
                }
            ) { Text(if (busy) "Envoi…" else "Confirmer") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Annuler") } }
    )
}
