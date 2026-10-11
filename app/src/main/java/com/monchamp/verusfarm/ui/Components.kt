package com.monchamp.verusfarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monchamp.verusfarm.R
import com.monchamp.verusfarm.Tone

// Colonne défilante utilisée par chaque onglet
@Composable
fun TabColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Carte, Color(0xFF0E0728))))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Violet.copy(alpha = 0.75f), Bord.copy(alpha = 0.5f))),
                shape
            )
            .padding(padding.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold, color = Texte
    )
}

@Composable
fun TabHeader(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Texte)
        Text(subtitle, fontSize = 14.sp, color = TexteDoux)
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null, hintColor: Color = Menthe) {
    NeonCard(modifier = modifier, padding = 12) {
        Text(label, fontSize = 12.sp, color = TexteDoux, maxLines = 1)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Texte, maxLines = 1)
        if (hint != null) Text(hint, fontSize = 11.sp, color = hintColor, maxLines = 1)
    }
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun StatusPill(tone: Tone) {
    val c = toneColor(tone)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(c)
        Spacer(Modifier.width(8.dp))
        Text(
            when (tone) {
                Tone.IDLE -> "Arrêté"
                Tone.OK -> "En marche"
                Tone.WARN -> "Attention"
                Tone.ERROR -> "Erreur"
            },
            color = c, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
        )
    }
}

@Composable
fun LogoMark(modifier: Modifier = Modifier) {
    val img = ImageBitmap.imageResource(R.drawable.logo_v)
    Canvas(modifier.aspectRatio(img.width.toFloat() / img.height.toFloat())) {
        drawImage(
            img,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
fun BrandHeader(tone: Tone) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LogoMark(Modifier.height(34.dp))
        Spacer(Modifier.width(8.dp))
        Text("Verumine", modifier = Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Texte)
        StatusPill(tone)
    }
}

// Anneau : un segment par cœur du processeur (toujours tous actifs = pleine puissance)
@Composable
fun CoreRing(total: Int, active: Int, color: Color, track: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 10.dp.toPx()
        val n = total.coerceIn(1, 16)
        val gap = 7f
        val step = 360f / n
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        for (i in 0 until n) {
            drawArc(
                color = if (i < active) color else track,
                startAngle = -90f + i * step + gap / 2f,
                sweepAngle = step - gap,
                useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

// Bouton de tapotage central : chaque appui déclenche onTap (si autorisé).
// L'appui long ne fait rien (clickable simple). L'anti-abus est géré par l'appelant.
@Composable
fun TapButton(
    cores: Int,
    activeCores: Int,
    enabled: Boolean,
    centerTop: String,
    centerBottom: String,
    onTap: () -> Unit
) {
    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Violet.copy(alpha = if (enabled) 0.50f else 0.18f), Color.Transparent),
                    center = center, radius = r
                ),
                radius = r, center = center
            )
        }
        CoreRing(
            total = cores,
            active = activeCores,
            color = Neon,
            track = Bord.copy(alpha = 0.7f),
            modifier = Modifier.size(212.dp)
        )
        Box(
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        if (enabled) listOf(Color(0xFF4C1D95), Color(0xFF1E0B5C), Color(0xFF12073A))
                        else listOf(Color(0xFF2A1A52), Color(0xFF160A3C), Color(0xFF0E0730))
                    )
                )
                .border(2.dp, Brush.verticalGradient(listOf(Neon, Violet.copy(alpha = 0.35f))), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled
                ) { onTap() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(VIcons.Bolt, contentDescription = null, tint = if (enabled) Lavande else Brume, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(4.dp))
                Text(centerTop, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Texte, textAlign = TextAlign.Center)
                Text(centerBottom, fontSize = 11.sp, color = if (enabled) Menthe else Ambre, textAlign = TextAlign.Center)
            }
        }
    }
}

// Un « +2 » qui monte et s'efface. onEnd est appelé à la fin pour le retirer.
@Composable
fun FloatingPlus(onEnd: () -> Unit) {
    val t = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        t.animateTo(1f, tween(900))
        onEnd()
    }
    Text(
        "+2 XP",
        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Menthe,
        modifier = Modifier
            .graphicsLayer {
                translationY = -150f * t.value
                alpha = 1f - t.value
            }
    )
}

// Pastille de récompense : une icône cadeau entourée d'un anneau de progression.
// L'anneau se remplit tous les 150 tapotages ; "gifts" (0..2) montre l'avancée vers le niveau.
@Composable
fun RewardOrb(progress: Float, gifts: Int, size: Int = 56, modifier: Modifier = Modifier) {
    Box(Modifier.size(size.dp).then(modifier), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val tl = Offset(stroke / 2f, stroke / 2f)
            drawArc(Bord.copy(alpha = 0.6f), -90f, 360f, false, tl, arc, style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(Menthe, Neon, Menthe)),
                -90f, 360f * progress.coerceIn(0f, 1f), false, tl, arc,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text("🎁", fontSize = (size / 3).sp)
    }
}

// Cadeau qui s'envole vers le bas (déclenché à chaque anneau rempli). onEnd le retire.
@Composable
fun GiftFly(onEnd: () -> Unit) {
    val t = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1100))
        onEnd()
    }
    val f = t.value
    Text(
        "🎁",
        fontSize = 34.sp,
        modifier = Modifier.graphicsLayer {
            translationY = 60f + 520f * f
            alpha = if (f < 0.85f) 1f else (1f - f) / 0.15f
            val s = 1f - 0.35f * f
            scaleX = s; scaleY = s
        }
    )
}

// Barre d'énergie horizontale.
@Composable
fun EnergyBar(percent: Float, modifier: Modifier = Modifier) {
    val p = (percent / 100f).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(50))
            .background(CarteHaute)
    ) {
        Box(
            Modifier
                .fillMaxWidth(p)
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        if (p > 0.15f) listOf(Violet, Neon) else listOf(Ambre, Corail)
                    )
                )
        )
    }
}

// Barre de progression XP fine.
@Composable
fun XpBar(progress: Float, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(CarteHaute)
    ) {
        Box(
            Modifier
                .fillMaxWidth(p)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Menthe, Neon)))
        )
    }
}

@Composable
fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Violet else CarteHaute)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (selected) Color.White else Texte)
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = Texte) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = TexteDoux, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, color = valueColor, textAlign = TextAlign.End, fontSize = 14.sp)
    }
}

@Composable
fun CheckRow(title: String, detail: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Texte)
            Text(detail, fontSize = 12.sp, color = TexteDoux)
        }
        Spacer(Modifier.width(12.dp))
        if (done) Text("Activé", color = Menthe, fontWeight = FontWeight.SemiBold)
        else FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) { Text(action) }
    }
}

// Champ sélectionnable : ouvre une liste (pays, devise, crypto).
@Composable
fun FieldRow(label: String, value: String, onClick: () -> Unit) {
    Column {
        Text(label, fontSize = 12.sp, color = TexteDoux)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CarteHaute)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, color = Texte, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(VIcons.Chevron, null, tint = Lavande)
        }
    }
}

// Dialogue de choix dans une liste, avec recherche.
@Composable
fun <T> PickerDialog(
    title: String,
    items: List<T>,
    labelOf: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) items else items.filter { labelOf(it).contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarteHaute,
        title = { Text(title, color = Texte) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Rechercher") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filtered.take(260).forEach { item ->
                        Text(
                            labelOf(item),
                            color = Texte, fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(item) }
                                .padding(vertical = 12.dp)
                        )
                    }
                    if (filtered.isEmpty()) Text("Aucun résultat", color = TexteDoux, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

// Champ texte stylé pour les formulaires.
@Composable
fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}

// Écran de configuration proposé dès la première ouverture : l'utilisateur accorde
// les autorisations sans avoir à aller dans Plus (dont l'anti-mise en veille).
@Composable
fun SetupDialog(
    checks: com.monchamp.verusfarm.Checks,
    onNotif: () -> Unit,
    onBattery: () -> Unit,
    onAdmin: () -> Unit,
    onAutostart: () -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        containerColor = CarteHaute,
        title = { Text("Avant de commencer", color = Texte) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Pour que ton téléphone mine sans s'arrêter, accorde ces autorisations. " +
                        "La plus importante empêche Android de mettre l'application en veille.",
                    fontSize = 13.sp, color = TexteDoux
                )
                CheckRow(
                    "Batterie sans restriction", "ESSENTIEL : empêche Android d'endormir le minage.",
                    checks.battery, "Autoriser", onBattery
                )
                CheckRow(
                    "Notifications", "Pour suivre le minage et les récompenses.",
                    checks.notif, "Autoriser", onNotif
                )
                CheckRow(
                    "Protection contre la désinstallation", "Garde l'application active (administrateur).",
                    checks.admin, "Activer", onAdmin
                )
                CheckRow(
                    "Démarrage automatique", "Sur Tecno, Infinix, Xiaomi : autorise le lancement auto.",
                    false, "Ouvrir", onAutostart
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) { Text("C'est parti") }
        }
    )
}

// Barre de navigation du bas
@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items: List<Pair<String, ImageVector>> = listOf(
        "Accueil" to VIcons.Home,
        "Minage" to VIcons.Bolt,
        "Wallet" to VIcons.Wallet,
        "Appareils" to VIcons.Phone,
        "Plus" to VIcons.More
    )
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(shape)
            .background(Color(0xFF110A2E))
            .border(1.dp, Bord.copy(alpha = 0.7f), shape)
            .padding(vertical = 6.dp)
    ) {
        items.forEachIndexed { i, item ->
            val sel = i == selected
            val tint = if (sel) Neon else Brume
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(item.second, contentDescription = item.first, tint = tint, modifier = Modifier.size(24.dp))
                Text(item.first, fontSize = 11.sp, color = tint, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
