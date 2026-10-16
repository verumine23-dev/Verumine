package com.monchamp.verusfarm.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.monchamp.verusfarm.NotifStore

private val RougeBadge = Color(0xFFFF3B5C)

// Cloche avec compteur rouge (+N) tant qu'il y a des notifications non lues.
@Composable
fun BellButton(onClick: () -> Unit) {
    val items by NotifStore.items.collectAsState()
    val unread = items.count { !it.read }
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(CarteHaute)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("🔔", fontSize = 20.sp)
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(RougeBadge),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (unread > 9) "+9" else "+$unread",
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Page Notifications : liste + suppression (corbeille) par notification.
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val items by NotifStore.items.collectAsState()

    // Ouvrir la page = tout marquer comme lu (le badge repasse à zéro).
    LaunchedEffect(Unit) { NotifStore.markAllRead(ctx) }

    TabColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(CarteHaute).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("←", color = Texte, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Notifications", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Texte)
                Text("Tes messages et alertes", fontSize = 13.sp, color = TexteDoux)
            }
            if (items.isNotEmpty()) {
                Text(
                    "Tout effacer", color = RougeBadge, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { NotifStore.clear(ctx) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔔", fontSize = 48.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Aucune notification pour l'instant", color = TexteDoux, fontSize = 14.sp)
                }
            }
        } else {
            items.forEach { n ->
                NeonCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(n.title, fontWeight = FontWeight.Bold, color = Texte, fontSize = 15.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(n.body, color = TexteDoux, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(fmtWhen(n.tsSec), color = Lavande, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(RougeBadge.copy(alpha = 0.15f))
                                .clickable { NotifStore.delete(ctx, n.id) },
                            contentAlignment = Alignment.Center
                        ) { Text("🗑", fontSize = 16.sp) }
                    }
                }
            }
        }
    }
}

private fun fmtWhen(tsSec: Long): String {
    val now = System.currentTimeMillis() / 1000L
    val d = (now - tsSec).coerceAtLeast(0)
    return when {
        d < 60 -> "à l'instant"
        d < 3600 -> "il y a ${d / 60} min"
        d < 86400 -> "il y a ${d / 3600} h"
        else -> "il y a ${d / 86400} j"
    }
}
