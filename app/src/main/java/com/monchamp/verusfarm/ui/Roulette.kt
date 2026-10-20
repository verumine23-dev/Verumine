package com.monchamp.verusfarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* =====================================================================
   ROULETTE DE RECONNEXION
   Affichée UNIQUEMENT à la reconnexion après une coupure internet.
   4 tentatives ; le système est fait pour que seule la DERNIÈRE gagne le pactole.
   Enchaînement : compte à rebours 4 s → roue → tentatives → animation de gain.
   ===================================================================== */

private enum class Phase { COUNTDOWN, WHEEL, WIN }

// 8 quartiers : les pairs = récompense (brillant), les impairs = case vide (sombre).
// Le quartier 0 est LE pactole. Les tentatives perdantes tombent sur une case vide.
private const val SEGMENTS = 8
private val EMPTY_SEGMENTS = listOf(1, 3, 5, 7)

@Composable
fun RouletteReward(onClaim: () -> Unit, onClose: () -> Unit) {
    var phase by remember { mutableStateOf(Phase.COUNTDOWN) }
    var count by remember { mutableIntStateOf(4) }
    var attempt by remember { mutableIntStateOf(0) }   // tentatives déjà jouées (0..4)
    var spinning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Compte à rebours de 4 secondes avant la roue.
    LaunchedEffect(Unit) {
        for (c in 4 downTo 1) { count = c; delay(1000) }
        phase = Phase.WHEEL
        message = "Tu as 4 chances. Tente le pactole !"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2080219))   // fond sombre quasi opaque
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (phase) {
            Phase.COUNTDOWN -> Countdown(count)
            Phase.WHEEL -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(VIcons.Gift, contentDescription = null, tint = Neon, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Roulette surprise", color = Neon, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Tentative ${(attempt + 1).coerceAtMost(4)} / 4",
                    color = Ambre, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
                Wheel(rotationDeg = rotation.value)
                Text(message, color = TexteDoux, fontSize = 14.sp, textAlign = TextAlign.Center)
                Button(
                    onClick = {
                        if (spinning || attempt >= 4) return@Button
                        spinning = true
                        val thisAttempt = attempt + 1
                        val win = thisAttempt >= 4
                        val target = if (win) 0 else EMPTY_SEGMENTS.random()
                        // Angle pour amener le quartier visé sous le pointeur (en haut, 270°).
                        val segMid = target * (360f / SEGMENTS) + (360f / SEGMENTS) / 2f
                        val base = ((270f - segMid) % 360f + 360f) % 360f
                        val finalRot = rotation.value - (rotation.value % 360f) + 360f * 5 + base
                        scope.launch {
                            rotation.animateTo(finalRot, tween(2600, easing = FastOutSlowInEasing))
                            attempt = thisAttempt
                            spinning = false
                            if (win) { phase = Phase.WIN }
                            else message = when (thisAttempt) {
                                1 -> "Si proche ! Il te reste 3 chances."
                                2 -> "Presque… encore 2 chances."
                                else -> "Dernière chance : c'est le moment !"
                            }
                        }
                    },
                    enabled = !spinning && attempt < 4,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet)
                ) {
                    Text(if (spinning) "La roue tourne…" else "Tourner", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Phase.WIN -> WinAnimation(onDone = { onClaim(); onClose() })
        }
    }
}

@Composable
private fun Countdown(count: Int) {
    val pulse = rememberInfiniteTransition()
    val s by pulse.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(600), RepeatMode.Reverse))
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(VIcons.Gift, contentDescription = null, tint = Neon, modifier = Modifier.size(90.dp).scale(s))
        Text("Ta récompense arrive…", color = Neon, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("$count", color = Ambre, fontSize = 64.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Wheel(rotationDeg: Float) {
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val sweep = 360f / SEGMENTS
            rotate(rotationDeg, pivot = center) {
                for (i in 0 until SEGMENTS) {
                    val reward = i % 2 == 0
                    val color = when {
                        i == 0 -> Color(0xFFFFC53D)                 // pactole : doré
                        reward -> Color(0xFF8B5CF6)                 // récompense : violet
                        else -> Color(0xFF241A42)                   // case vide : sombre
                    }
                    drawArc(
                        color = color,
                        startAngle = i * sweep,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                    )
                }
            }
            // Marqueurs : pastilles dorées (pièces) sur les quartiers gagnants.
            for (i in 0 until SEGMENTS) {
                if (i % 2 == 0) {
                    val midDeg = i * sweep + sweep / 2f + rotationDeg
                    val rad = Math.toRadians(midDeg.toDouble())
                    val mx = center.x + (r * 0.62f) * cos(rad).toFloat()
                    val my = center.y + (r * 0.62f) * sin(rad).toFloat()
                    drawCircle(color = Color(0xFFF5C84B), radius = r * 0.085f, center = Offset(mx, my))
                    drawCircle(color = Color(0xFF2A1A05), radius = r * 0.035f, center = Offset(mx, my))
                }
            }
        }
        // Moyeu central
        Box(
            Modifier.size(54.dp).background(CarteHaute, RoundedCornerShape(27.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(VIcons.Gift, contentDescription = null, tint = Color(0xFFF5C84B), modifier = Modifier.size(26.dp)) }
        // Pointeur en haut
        Text("▼", color = Neon, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun WinAnimation(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf(false) }
    var coinFly by remember { mutableStateOf(false) }

    // Explosion de paillettes : une progression 0→1 qui pousse les particules vers l'extérieur.
    val burst = remember { Animatable(0f) }
    val giftPulse = rememberInfiniteTransition()
    val gs by giftPulse.animateFloat(0.8f, 1.25f, infiniteRepeatable(tween(500), RepeatMode.Reverse))
    val coin = remember { Animatable(0f) }   // 0 = sur le cadeau, 1 = en bas (vers l'icône cadeau de l'accueil)

    val particles = remember {
        List(60) {
            Triple(
                Random.nextFloat() * 6.2832f,            // angle
                0.35f + Random.nextFloat() * 0.65f,      // portée relative
                Random.nextInt(0, 5)                      // couleur
            )
        }
    }
    val colors = listOf(Color(0xFFFFC53D), Neon, Color(0xFFFF5C8A), Color(0xFF5CE1E6), Color(0xFFB388FF))

    LaunchedEffect(Unit) {
        burst.animateTo(1f, tween(1400, easing = LinearEasing))
        opened = true
        delay(400)
        coinFly = true
        coin.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        delay(150)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Paillettes
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.42f)
            val maxR = size.minDimension * 0.55f
            val p = burst.value
            particles.forEach { (ang, reach, ci) ->
                val d = maxR * reach * p
                val x = c.x + d * cos(ang)
                val y = c.y + d * sin(ang) + p * 120f    // légère chute
                drawCircle(colors[ci], radius = 7f * (1f - p * 0.4f), center = Offset(x, y))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("JACKPOT !", color = Color(0xFFFFC53D), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.height(140.dp)) {
                Icon(
                    if (opened) VIcons.Trophy else VIcons.Gift, contentDescription = null,
                    tint = Color(0xFFFFC53D), modifier = Modifier.size(96.dp).scale(gs)
                )
                // La pièce d'or sort du cadeau et file vers le bas (icône cadeau de l'accueil).
                if (coinFly) {
                    Icon(
                        VIcons.Coin, contentDescription = null, tint = Color(0xFFF5C84B),
                        modifier = Modifier.size(40.dp).scale(1f - coin.value * 0.4f)
                            .padding(top = (coin.value * 220f).dp)
                    )
                }
            }
            Text("Tu as gagné le pactole : énergie rechargée + XP bonus !",
                color = Texte, fontSize = 15.sp, textAlign = TextAlign.Center)
            Button(onClick = onDone, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                Text("Récupérer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
