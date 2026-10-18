package com.monchamp.verusfarm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monchamp.verusfarm.GameApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val QUIZ_SUBJECTS = listOf("Culture générale", "Géographie", "Sport", "Sciences")

private fun gameName(key: String): String = when (key) {
    "mot" -> "Devinette de mots"
    "quiz" -> "Quiz des sujets"
    "calcul" -> "Calcul Éclair"
    "suite" -> "Suite Logique"
    else -> "Jeu"
}

private fun emojiFor(key: String): String = when (key) {
    "mot" -> "🔤"; "quiz" -> "❓"; "calcul" -> "⚡"; "suite" -> "🔢"; else -> "🎮"
}

/** Image du jeu depuis res/drawable/game_<key>.png si elle existe, sinon une icône. */
@Composable
private fun GameImage(key: String, sizeDp: Int) {
    val ctx = LocalContext.current
    val id = remember(key) { ctx.resources.getIdentifier("game_$key", "drawable", ctx.packageName) }
    if (id != 0) {
        Image(
            painter = painterResource(id), contentDescription = null,
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(16.dp))
        )
    } else {
        Box(
            Modifier.size(sizeDp.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2A1A52), Color(0xFF160C3B)))),
            contentAlignment = Alignment.Center
        ) { Text(emojiFor(key), fontSize = (sizeDp * 0.5).sp) }
    }
}

/** Écran de jeu complet, habillage casino + niveaux + animations. */
@Composable
fun GamePlayScreen(gameKey: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var subject by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf(if (gameKey == "quiz") "subject" else "loading") }
    var roundId by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf<JSONObject?>(null) }
    var result by remember { mutableStateOf<GameApi.SubmitResult?>(null) }
    var blockedTitle by remember { mutableStateOf("") }
    var blockedMsg by remember { mutableStateOf("") }
    var canReplay by remember { mutableStateOf(true) }
    var level by remember { mutableStateOf(1) }
    var combo by remember { mutableStateOf(0) }
    var celebrate by remember { mutableStateOf(false) }

    fun start(advance: Boolean) {
        if (advance) level = (level + 1).coerceAtMost(3)
        phase = "loading"; result = null
        scope.launch {
            val r = GameApi.startRound(ctx, gameKey, subject)
            if (r.ok && r.prompt != null) {
                roundId = r.roundId ?: ""; prompt = r.prompt; phase = "play"
            } else {
                when (r.error) {
                    "locked" -> { blockedTitle = "⏳ Accès en pause"; blockedMsg = "Reviens dans 24 h : quota épuisé ou 3 pertes d'affilée." }
                    "game_closed", "game_done" -> { blockedTitle = "Jeu terminé"; blockedMsg = "Tu as fini tes 3 manches ici. Choisis un autre jeu." }
                    "finish_active" -> { blockedTitle = "Termine d'abord l'autre jeu"; blockedMsg = "Tu dois finir le jeu en cours avant d'en commencer un nouveau." }
                    "no_question" -> { blockedTitle = "Indisponible"; blockedMsg = "Aucune question pour ce sujet pour l'instant." }
                    else -> { blockedTitle = "Connexion requise"; blockedMsg = "Impossible de démarrer. Vérifie ta connexion internet." }
                }
                phase = "blocked"
            }
        }
    }

    fun submit(answer: JSONObject) {
        phase = "loading"
        scope.launch {
            val r = GameApi.submit(ctx, roundId, answer)
            if (!r.ok) {
                blockedTitle = "Oups"; blockedMsg = "La réponse n'a pas pu être envoyée. Reviens et réessaie."
                phase = "blocked"
            } else {
                result = r
                if (r.won) combo += 1 else combo = 0
                canReplay = r.lockedUntil == null
                celebrate = true
                phase = "result"
            }
        }
    }

    LaunchedEffect(Unit) {
        val st = GameApi.getState(ctx)
        level = ((st?.games?.firstOrNull { it.key == gameKey }?.played) ?: 0) + 1
        if (gameKey != "quiz") start(false)
    }

    Box(Modifier.fillMaxSize().background(casinoBackdrop())) {
        Column(Modifier.fillMaxSize()) {
            CasinoHeader(gameKey, level, combo, onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (phase) {
                    "subject" -> SubjectPicker { subject = it; start(false) }
                    "loading" -> Box(Modifier.fillMaxWidth().padding(top = 70.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFF5C84B))
                    }
                    "play" -> {
                        val p = prompt
                        if (p != null) when (gameKey) {
                            "mot" -> MotPlay(p) { submit(it) }
                            "quiz" -> QuizPlay(p) { submit(it) }
                            "calcul" -> CalculPlay(p, roundId) { submit(it) }
                            "suite" -> SuitePlay(p) { submit(it) }
                        }
                    }
                    "result" -> ResultView(gameKey, result, canReplay, { start(true) }, onBack)
                    "blocked" -> BlockedView(blockedTitle, blockedMsg, onBack)
                }
                Spacer(Modifier.height(40.dp))
            }
        }

        // Animations par-dessus tout : gain ou perte.
        if (celebrate && result != null) {
            if (result!!.won) {
                WinCelebration(
                    reward = GameApi.formatLocal(result!!.rewardCents.toLong(), result!!.currency, result!!.fxPerUsd),
                    combo = combo,
                    onFinished = { celebrate = false }
                )
            } else {
                LoseFlash(onFinished = { celebrate = false })
            }
        }
    }
}

// ---------------- En-tête casino ----------------
@Composable
private fun CasinoHeader(gameKey: String, level: Int, combo: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1B1046)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("←", color = Texte, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            GameImage(gameKey, 40)
            Spacer(Modifier.width(10.dp))
            Text(gameName(gameKey), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFF5C84B))
            Spacer(Modifier.weight(1f))
            if (combo >= 2) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Corail.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) { Text("🔥 x$combo", color = Corail, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Manche", fontSize = 12.sp, color = TexteDoux)
            Spacer(Modifier.width(10.dp))
            LevelGems(current = level, total = 3)
            Spacer(Modifier.width(8.dp))
            Text("$level / 3", fontSize = 12.sp, color = Lavande, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------- Choix du sujet (quiz) ----------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectPicker(onPick: (String) -> Unit) {
    CasinoCard {
        Text("Choisis ton thème", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Texte)
        Text("Une bonne réponse = 0,50 \$. Choisis bien ton terrain de jeu.", fontSize = 13.sp, color = TexteDoux)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QUIZ_SUBJECTS.forEach { s ->
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF2A1A52), Color(0xFF1B1046))))
                        .border(1.dp, Color(0xFFF5C84B).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable { onPick(s) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) { Text(s, color = Texte, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
        }
    }
}

// ---------------- Jeu : Devinette de mots ----------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotPlay(prompt: JSONObject, onSubmit: (JSONObject) -> Unit) {
    val clue = prompt.optString("clue")
    val len = prompt.optInt("length")
    var guess by remember { mutableStateOf("") }
    CasinoCard {
        Text("🔎 Indice", fontSize = 13.sp, color = Color(0xFFF5C84B), fontWeight = FontWeight.Bold)
        Text(clue, fontSize = 18.sp, color = Texte, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(len.coerceIn(1, 14)) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF221447))
                        .border(1.dp, Color(0xFFF5C84B).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                )
            }
        }
        Text("$len lettres", fontSize = 12.sp, color = Lavande)
    }
    FormField(guess, { guess = it }, "Ta réponse")
    NeonCTA("Valider", enabled = guess.isNotBlank()) {
        onSubmit(JSONObject().put("guess", guess.trim()))
    }
}

// ---------------- Jeu : Quiz ----------------
@Composable
private fun QuizPlay(prompt: JSONObject, onSubmit: (JSONObject) -> Unit) {
    val question = prompt.optString("question")
    val options = prompt.optJSONArray("options")
    CasinoCard {
        Text("❓ Question", fontSize = 13.sp, color = Color(0xFFF5C84B), fontWeight = FontWeight.Bold)
        Text(question, fontSize = 18.sp, color = Texte, fontWeight = FontWeight.SemiBold)
    }
    if (options != null) {
        val letters = listOf("A", "B", "C", "D")
        for (i in 0 until options.length()) {
            val label = options.optString(i)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF231551), Color(0xFF170E3C))))
                    .border(1.dp, Color(0xFFF5C84B).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable { onSubmit(JSONObject().put("choice", i)) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFC9972B), Color(0xFFF5C84B)))),
                    contentAlignment = Alignment.Center
                ) { Text(letters.getOrElse(i) { "?" }, color = Color(0xFF2A1A05), fontWeight = FontWeight.Black, fontSize = 14.sp) }
                Spacer(Modifier.width(12.dp))
                Text(label, color = Texte, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------- Jeu : Calcul Éclair ----------------
@Composable
private fun CalculPlay(prompt: JSONObject, roundId: String, onSubmit: (JSONObject) -> Unit) {
    val expr = prompt.optString("expr")
    val seconds = prompt.optInt("seconds", 12)
    var value by remember(roundId) { mutableStateOf("") }
    var remaining by remember(roundId) { mutableStateOf(seconds) }
    var sent by remember(roundId) { mutableStateOf(false) }

    LaunchedEffect(roundId) {
        remaining = seconds
        while (remaining > 0 && !sent) { delay(1000); remaining -= 1 }
        if (!sent) { sent = true; onSubmit(JSONObject().put("value", value.trim().toLongOrNull() ?: -1L)) }
    }

    CasinoCard {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimerRing(remaining = remaining, total = seconds)
        }
        Text("$expr = ?", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFFF5C84B),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    FormField(value, { value = it }, "Ton résultat", keyboard = KeyboardType.Number)
    NeonCTA("Valider", enabled = value.isNotBlank()) {
        if (!sent) { sent = true; onSubmit(JSONObject().put("value", value.trim().toLongOrNull() ?: -1L)) }
    }
}

// ---------------- Jeu : Suite Logique ----------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuitePlay(prompt: JSONObject, onSubmit: (JSONObject) -> Unit) {
    val terms = prompt.optJSONArray("terms")
    var value by remember { mutableStateOf("") }
    CasinoCard {
        Text("🔢 Trouve le nombre suivant", fontSize = 15.sp, color = Color(0xFFF5C84B), fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (terms != null) for (i in 0 until terms.length()) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF2A1A52), Color(0xFF160C3B))))
                        .border(1.dp, Lavande.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("${terms.optInt(i)}", color = Texte, fontWeight = FontWeight.Black, fontSize = 18.sp) }
            }
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFC9972B), Color(0xFFF5C84B)))),
                contentAlignment = Alignment.Center
            ) { Text("?", color = Color(0xFF2A1A05), fontWeight = FontWeight.Black, fontSize = 22.sp) }
        }
    }
    FormField(value, { value = it }, "Le nombre suivant", keyboard = KeyboardType.Number)
    NeonCTA("Valider", enabled = value.isNotBlank()) {
        onSubmit(JSONObject().put("value", value.trim().toLongOrNull() ?: -1L))
    }
}

// ---------------- Résultat d'une manche ----------------
@Composable
private fun ResultView(
    gameKey: String,
    result: GameApi.SubmitResult?,
    canReplay: Boolean,
    onReplay: () -> Unit,
    onBack: () -> Unit
) {
    if (result == null) return
    val won = result.won
    CasinoCard {
        Text(
            if (won) "🎉 Manche gagnée !" else "😕 Manche perdue",
            fontSize = 22.sp, fontWeight = FontWeight.Black,
            color = if (won) Menthe else Corail,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        if (won) {
            Text(
                "+ ${GameApi.formatLocal(result.rewardCents.toLong(), result.currency, result.fxPerUsd)}",
                fontSize = 18.sp, color = Color(0xFFF5C84B), fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        } else {
            val good = correctText(gameKey, result.answer)
            if (good != null) Text(
                "Bonne réponse : $good", fontSize = 14.sp, color = TexteDoux,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            "Solde : ${GameApi.formatLocal(result.balanceCents, result.currency, result.fxPerUsd)}",
            fontSize = 14.sp, color = Texte,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }

    val note = when (result.message) {
        "quota_done" -> "Quota du jour épuisé. Les jeux reviennent dans 24 h."
        "locked_losses" -> "3 pertes d'affilée : pause de 24 h. Reviens plus tard."
        "timeout" -> "⏱ Temps écoulé !"
        else -> null
    }
    if (note != null) CasinoCard { Text(note, fontSize = 13.sp, color = Color(0xFFF5C84B)) }

    if (canReplay) NeonCTA("Jouer la manche suivante") { onReplay() }
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1046))
            .border(1.dp, Bord.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center
    ) { Text("Retour aux jeux", color = Texte, fontWeight = FontWeight.Bold) }
}

private fun correctText(gameKey: String, answer: JSONObject?): String? {
    if (answer == null) return null
    return when (gameKey) {
        "mot" -> answer.optString("word")
        "calcul", "suite" -> answer.opt("answer")?.toString()
        else -> null
    }
}

// ---------------- Vue de blocage ----------------
@Composable
private fun BlockedView(title: String, msg: String, onBack: () -> Unit) {
    CasinoCard {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFF5C84B))
        Text(msg, fontSize = 14.sp, color = TexteDoux)
    }
    NeonCTA("Retour aux jeux") { onBack() }
}
