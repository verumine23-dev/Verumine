package com.monchamp.verusfarm.ui

import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monchamp.verusfarm.Checks
import com.monchamp.verusfarm.Prefs
import com.monchamp.verusfarm.VerumineWidget

/* =====================================================================
   CENTRE D'AUTORISATIONS — réutilisé dans la fenêtre d'accueil (SetupDialog)
   et sur la page « Plus ». Liste complète des autorisations nécessaires au
   fonctionnement à 100 %, avec bouton clignotant tant que ce n'est pas fait
   et guide pas-à-pas adapté à la marque du téléphone.
   ===================================================================== */

// Un guide : des étapes numérotées (adaptées à la marque) + une petite note.
private data class PermGuide(val title: String, val steps: List<String>, val note: String)

// Marque du téléphone (en minuscules).
private fun brand(): String = (Build.MANUFACTURER ?: "").lowercase()

// Guide « Démarrage automatique » selon la marque.
private fun autostartGuide(): PermGuide {
    val b = brand()
    val steps = when {
        b.contains("xiaomi") || b.contains("redmi") || b.contains("poco") -> listOf(
            "La page « Démarrage auto » va s'ouvrir.",
            "Trouve « Verumine » dans la liste.",
            "Active l'interrupteur à côté de Verumine.",
            "Reviens ici et appuie sur « C'est fait »."
        )
        b.contains("tecno") || b.contains("infinix") || b.contains("itel") || b.contains("transsion") -> listOf(
            "Ouvre « Phone Master » ou Réglages, puis « Gestion des applications ».",
            "Cherche « Démarrage auto » ou « Auto-start ».",
            "Active Verumine dans la liste.",
            "Dans « Économie d'énergie », mets Verumine sur « Autoriser en arrière-plan ».",
            "Reviens ici et appuie sur « C'est fait »."
        )
        b.contains("oppo") || b.contains("realme") || b.contains("oneplus") -> listOf(
            "Ouvre « Démarrage auto » (Auto-start).",
            "Active l'interrupteur de Verumine.",
            "Dans « Usage de la batterie », autorise l'activité en arrière-plan.",
            "Reviens ici et appuie sur « C'est fait »."
        )
        b.contains("vivo") -> listOf(
            "Ouvre « Démarrage en arrière-plan » (Auto-start).",
            "Active Verumine.",
            "Dans « Consommation d'énergie élevée », autorise Verumine.",
            "Reviens ici et appuie sur « C'est fait »."
        )
        b.contains("huawei") || b.contains("honor") -> listOf(
            "Ouvre « Démarrage des applications » (App launch).",
            "Désactive « Gérer automatiquement » pour Verumine.",
            "Active : Démarrage auto, Démarrage secondaire, Exécution en arrière-plan.",
            "Reviens ici et appuie sur « C'est fait »."
        )
        b.contains("samsung") -> listOf(
            "La fiche de l'application va s'ouvrir.",
            "Va dans « Batterie » puis choisis « Sans restriction ».",
            "Dans Réglages → Batterie → « Limites d'arrière-plan », vérifie que Verumine n'est PAS en veille.",
            "Reviens ici et appuie sur « C'est fait »."
        )
        else -> listOf(
            "La fiche de l'application va s'ouvrir.",
            "Cherche « Démarrage auto », « Auto-start » ou « Lancement automatique ».",
            "Active cette option pour Verumine.",
            "Reviens ici et appuie sur « C'est fait »."
        )
    }
    return PermGuide(
        "Démarrage automatique",
        steps,
        "Ce réglage se trouve chez le constructeur de ton téléphone — son nom change selon la marque. " +
            "Si tu ne trouves pas « Démarrage auto », cherche « Auto-start » ou « Lancement automatique »."
    )
}

// Guide « Ajouter le widget à l'écran d'accueil » (geste au lancement).
private fun widgetGuide(): PermGuide = PermGuide(
    "Ajouter le widget Verumine",
    listOf(
        "Reste appuyé quelques secondes sur un espace vide de l'écran d'accueil.",
        "Touche « Widgets ».",
        "Cherche « Verumine » dans la liste.",
        "Maintiens le widget et glisse-le sur ton écran d'accueil.",
        "Reviens ici et appuie sur « C'est fait »."
    ),
    "Le widget te rappelle chaque jour de revenir miner et jouer. Sur beaucoup de téléphones, " +
        "le bouton « Ajouter » le place directement."
)

// Guide « Verrouiller dans les récentes » (aucun réglage : c'est un geste à l'écran).
private fun recentsGuide(): PermGuide = PermGuide(
    "Verrouiller dans les applications récentes",
    listOf(
        "Ouvre la liste des applications récentes (bouton carré, ou balaye vers le haut et garde le doigt).",
        "Trouve la carte « Verumine ».",
        "Maintiens appuyé sur la carte (ou appuie sur l'icône en haut de la carte).",
        "Choisis « Verrouiller » (un cadenas 🔒 apparaît).",
        "Reviens ici et appuie sur « C'est fait »."
    ),
    "Le verrouillage empêche que Verumine soit fermé quand tu balaies tes applications récentes."
)

// Dialogue de guide pas-à-pas.
@Composable
private fun GuideDialog(
    guide: PermGuide,
    openLabel: String?,          // ex. « Ouvrir les réglages » ; null = pas de bouton d'ouverture
    onOpen: () -> Unit,
    onConfirmDone: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarteHaute,
        title = { Text(guide.title, color = Texte, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                guide.steps.forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "${i + 1}", color = Neon, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Violet.copy(alpha = 0.25f)),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(step, color = Texte, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(guide.note, color = TexteDoux, fontSize = 12.sp)
                if (openLabel != null) {
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onOpen, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(openLabel)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmDone) { Text("✓ C'est fait", color = Menthe, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

// Bouton d'action qui clignote (pour attirer l'œil tant que ce n'est pas accordé).
@Composable
private fun PulseButton(label: String, onClick: () -> Unit) {
    val trans = rememberInfiniteTransition()
    val a by trans.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse)
    )
    Button(onClick = onClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.alpha(a)) {
        Text(label)
    }
}

// Une ligne d'autorisation : titre, description, état (✓ / bouton clignotant).
@Composable
private fun PermRow(
    title: String,
    detail: String,
    granted: Boolean,
    actionLabel: String,
    essential: Boolean,
    onAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Texte, fontSize = 15.sp)
                if (essential) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "ESSENTIEL", color = Ambre, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Ambre.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(detail, fontSize = 12.sp, color = TexteDoux)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Menthe, 8); Spacer(Modifier.width(6.dp))
                Text("Activé", color = Menthe, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        } else {
            PulseButton(actionLabel, onAction)
        }
    }
}

/**
 * Panneau complet des autorisations. Utilisé dans la fenêtre d'accueil et la page Plus.
 * - battery / notif / admin : état réel (Checks) + action système directe.
 * - démarrage auto / verrouillage récents : guidés (réglages du constructeur ou geste),
 *   l'utilisateur confirme « C'est fait » (on ne peut pas le vérifier par programme).
 */
@Composable
fun PermissionsPanel(checks: Checks, actions: Actions) {
    val ctx = LocalContext.current
    var autostartDone by remember { mutableStateOf(Prefs.autostartDone(ctx)) }
    var recentsDone by remember { mutableStateOf(Prefs.recentsLockDone(ctx)) }
    var widgetDone by remember { mutableStateOf(Prefs.widgetAdded(ctx)) }

    // Quel guide est ouvert en ce moment (null = aucun).
    var guide by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PermRow(
            "Batterie sans restriction",
            "Empêche Android d'endormir le minage.",
            checks.battery, "Autoriser", essential = true
        ) { actions.onAskBattery() }

        PermRow(
            "Notifications",
            "Pour suivre le minage et recevoir les rappels, même hors ligne.",
            checks.notif, "Autoriser", essential = true
        ) { actions.onAskNotif() }

        PermRow(
            "Démarrage automatique",
            "Pour que Verumine se relance tout seul après un redémarrage.",
            autostartDone, "Activer", essential = true
        ) { guide = "autostart" }

        PermRow(
            "Verrouiller dans les récentes",
            "Empêche la fermeture quand tu balaies tes applications.",
            recentsDone, "Comment ?", essential = false
        ) { guide = "recents" }

        PermRow(
            "Protection contre la désinstallation",
            "Active l'administrateur de l'appareil (optionnel).",
            checks.admin, "Activer", essential = false
        ) { actions.onAskAdmin() }

        PermRow(
            "Widget écran d'accueil",
            "Un rappel quotidien pour revenir miner et jouer.",
            widgetDone, "Ajouter", essential = false
        ) {
            if (VerumineWidget.requestPin(ctx)) {
                Prefs.setWidgetAdded(ctx, true); widgetDone = true
            } else {
                guide = "widget"
            }
        }
    }

    when (guide) {
        "autostart" -> GuideDialog(
            guide = autostartGuide(),
            openLabel = "Ouvrir les réglages",
            onOpen = { actions.onOpenAutostart() },
            onConfirmDone = {
                Prefs.setAutostartDone(ctx, true); autostartDone = true; guide = null
            },
            onDismiss = { guide = null }
        )
        "recents" -> GuideDialog(
            guide = recentsGuide(),
            openLabel = null,
            onOpen = { },
            onConfirmDone = {
                Prefs.setRecentsLockDone(ctx, true); recentsDone = true; guide = null
            },
            onDismiss = { guide = null }
        )
        "widget" -> GuideDialog(
            guide = widgetGuide(),
            openLabel = null,
            onOpen = { },
            onConfirmDone = {
                Prefs.setWidgetAdded(ctx, true); widgetDone = true; guide = null
            },
            onDismiss = { guide = null }
        )
    }
}
