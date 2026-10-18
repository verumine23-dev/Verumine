package com.monchamp.verusfarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random

private val Or = Color(0xFFF5C84B)
private val OrFonce = Color(0xFFC9972B)

// =====================================================================
//  Fond « table de jeu » : dégradé radial sombre + halo doré en haut.
// =====================================================================
fun casinoBackdrop(): Brush = Brush.verticalGradient(
    listOf(Color(0xFF241056), Color(0xFF160C3B), Color(0xFF07021A))
)

// =====================================================================
//  Gemmes de niveau (manches) : ● ● ● remplies jusqu'au niveau courant.
// =====================================================================
@Composable
fun LevelGems(current: Int, total: Int = 3, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 1..total) {
            val on = i <= current
            Box(
                Modifier
                    .size(if (on) 16.dp else 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (on) Brush.linearGradient(listOf(Or, OrFonce))
                        else Brush.linearGradient(listOf(Color(0xFF2A1A52), Color(0xFF221447)))
                    )
                    .border(1.dp, if (on) Or.copy(alpha = 0.8f) else Bord.copy(alpha = 0.4f), RoundedCornerShape(50))
            )
        }
    }
}

// =====================================================================
//  Bouton principal « casino » : dégradé or, léger battement lumineux.
// =====================================================================
@Composable
fun NeonCTA(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val trans = rememberInfiniteTransition(label = "cta")
    val glow by trans.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "g"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(OrFonce, Or, OrFonce))
                else Brush.horizontalGradient(listOf(Color(0xFF3A2E12), Color(0xFF4A3A18)))
            )
            .border(
                2.dp,
                if (enabled) Color.White.copy(alpha = 0.25f * glow) else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Color(0xFF2A1A05) else Brume,
            fontWeight = FontWeight.Black, fontSize = 17.sp
        )
    }
}

// =====================================================================
//  Carte de jeu « feutrée » : bordure dorée, fond profond.
// =====================================================================
@Composable
fun CasinoCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1C1150), Color(0xFF120A33))))
            .border(1.5.dp, Brush.verticalGradient(listOf(Or.copy(alpha = 0.7f), Bord.copy(alpha = 0.4f))), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

// =====================================================================
//  Confettis + pluie de pièces (dessinés, aucune image requise).
// =====================================================================
private class FxParticle(
    val x: Float, val size: Float, val rot: Float, val drift: Float,
    val delay: Float, val color: Color, val isCoin: Boolean, val spin: Float
)

@Composable
fun ConfettiCoins(modifier: Modifier = Modifier, running: Boolean) {
    if (!running) return
    val colors = listOf(Or, Neon, Menthe, Corail, Color.White, Lavande)
    val parts = remember {
        List(40) {
            val coin = it % 3 == 0
            FxParticle(
                x = Random.nextFloat(),
                size = if (coin) Random.nextFloat() * 10f + 16f else Random.nextFloat() * 8f + 8f,
                rot = Random.nextFloat() * 360f,
                drift = (Random.nextFloat() - 0.5f) * 0.4f,
                delay = Random.nextFloat() * 0.25f,
                color = colors[Random.nextInt(colors.size)],
                isCoin = coin,
                spin = (Random.nextFloat() - 0.5f) * 900f
            )
        }
    }
    val t = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { t.animateTo(1f, tween(1700, easing = LinearEasing)) }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        parts.forEach { p ->
            val local = ((t.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val x = p.x * w + p.drift * w * local + sin(local * 10f + p.rot) * 12f
            val y = -40f + (h + 120f) * local
            val angle = p.rot + p.spin * local
            if (p.isCoin) {
                drawCircle(OrFonce, radius = p.size, center = Offset(x, y))
                drawCircle(Or, radius = p.size * 0.78f, center = Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.5f), radius = p.size * 0.28f,
                    center = Offset(x - p.size * 0.25f, y - p.size * 0.25f))
            } else {
                rotate(degrees = angle, pivot = Offset(x, y)) {
                    drawRoundRect(
                        color = p.color,
                        topLeft = Offset(x - p.size / 2f, y - p.size / 2f),
                        size = androidx.compose.ui.geometry.Size(p.size, p.size * 1.6f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                    )
                }
            }
        }
    }
}

// =====================================================================
//  Overlay de victoire : scrim + « GAGNÉ ! » doré qui surgit + gain +
//  combo, par-dessus les confettis. S'efface tout seul.
// =====================================================================
@Composable
fun WinCelebration(reward: String, combo: Int, onFinished: () -> Unit) {
    val pop = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        pop.animateTo(1f, tween(450, easing = EaseOutBack))
        kotlinx.coroutines.delay(1400)
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC07021A)),
        contentAlignment = Alignment.Center
    ) {
        ConfettiCoins(running = true)
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🎉", fontSize = (54 * pop.value).coerceAtLeast(1f).sp)
            Text(
                "GAGNÉ !",
                fontSize = 40.sp, fontWeight = FontWeight.Black, color = Or,
                modifier = Modifier.scale(pop.value)
            )
            Box(
                Modifier
                    .scale(pop.value)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(OrFonce, Or)))
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text("+ $reward", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF2A1A05))
            }
            if (combo >= 2) {
                Text("🔥 Série x$combo", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Corail,
                    modifier = Modifier.scale(pop.value))
            }
        }
    }
}

// =====================================================================
//  Overlay de défaite : flash rouge + tampon « PERDU » qui claque.
// =====================================================================
@Composable
fun LoseFlash(onFinished: () -> Unit) {
    val stamp = remember { Animatable(2.2f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        stamp.animateTo(1f, tween(360, easing = EaseOutBack))
        kotlinx.coroutines.delay(950)
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0x66FF2D55), Color(0xCC07021A)),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .graphicsLayer { scaleX = stamp.value; scaleY = stamp.value; rotationZ = -12f }
                .clip(RoundedCornerShape(16.dp))
                .border(BorderStroke(4.dp, Corail), RoundedCornerShape(16.dp))
                .padding(horizontal = 26.dp, vertical = 12.dp)
        ) {
            Text("PERDU", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Corail)
        }
    }
}

// =====================================================================
//  Anneau de temps lumineux pour le jeu de calcul.
// =====================================================================
@Composable
fun TimerRing(remaining: Int, total: Int, modifier: Modifier = Modifier) {
    val frac = (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val danger = remaining <= 3
    Box(modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val tl = Offset(stroke / 2f, stroke / 2f)
            drawArc(Bord.copy(alpha = 0.5f), -90f, 360f, false, tl, arc,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(
                Brush.sweepGradient(
                    if (danger) listOf(Corail, Color(0xFFFF2D55), Corail)
                    else listOf(Menthe, Or, Menthe)
                ),
                -90f, -360f * frac, false, tl, arc,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
        Text("$remaining", fontSize = 40.sp, fontWeight = FontWeight.Black,
            color = if (danger) Corail else Texte)
    }
}
