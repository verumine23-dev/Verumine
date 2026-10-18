package com.monchamp.verusfarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

/** Écran de jeu complet : démarre une manche, affiche l'énigme, envoie la réponse. */
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

    fun start() {
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
                    "resolve_first" -> { blockedTitle = "Manche en cours"; blockedMsg = "Une manche est déjà ouverte. Reviens et réessaie." }
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
                canReplay = r.lockedUntil == null
                phase = "result"
            }
        }
    }

    LaunchedEffect(Unit) { if (gameKey != "quiz") start() }

    Column(Modifier.fillMaxSize()) {
        GameHeader(gameName(gameKey), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (phase) {
                "subject" -> SubjectPicker { subject = it; start() }
                "loading" -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Neon)
                }
                "play" -> {
                    val p = prompt
                    if (p == null) {
                        Text("…", color = TexteDoux)
                    } else when (gameKey) {
                        "mot" -> MotPlay(p) { submit(it) }
                        "quiz" -> QuizPlay(p) { submit(it) }
                        "calcul" -> CalculPlay(p, roundId) { submit(it) }
                        "suite" -> SuitePlay(p) { submit(it) }
                    }
                }
                "result" -> ResultView(
                    gameKey = gameKey,
                    result = result,
                    canReplay = canReplay,
                    onReplay = { start() },
                    onBack = onBack
                )
                "blocked" -> BlockedView(blockedTitle, blockedMsg, onBack)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ---------------- En-tête ----------------
@Composable
private fun GameHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(CarteHaute).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("←", color = Texte, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Texte)
    }
}

// ---------------- Choix du sujet (quiz) ----------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectPicker(onPick: (String) -> Unit) {
    NeonCard {
        SectionTitle("Choisis un sujet")
        Text("Réponds à la question posée dans le thème choisi.", fontSize = 13.sp, color = TexteDoux)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QUIZ_SUBJECTS.forEach { s ->
                Chip(label = s, selected = false) { onPick(s) }
            }
        }
    }
}

// ---------------- Jeu : Devinette de mots ----------------
@Composable
private fun MotPlay(prompt: JSONObject, onSubmit: (JSONObject) -> Unit) {
    val clue = prompt.optString("clue")
    val len = prompt.optInt("length")
    var guess by remember { mutableStateOf("") }
    NeonCard {
        SectionTitle("Trouve le mot")
        Text(clue, fontSize = 16.sp, color = Texte)
        Text("Mot de $len lettres", fontSize = 13.sp, color = Lavande)
    }
    FormField(guess, { guess = it }, "Ta réponse")
    Button(
        onClick = { onSubmit(JSONObject().put("guess", guess.trim())) },
        enabled = guess.isNotBlank(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) { Text("Valider", fontWeight = FontWeight.Bold) }
}

// ---------------- Jeu : Quiz ----------------
@Composable
private fun QuizPlay(prompt: JSONObject, onSubmit: (JSONObject) -> Unit) {
    val question = prompt.optString("question")
    val options = prompt.optJSONArray("options")
    NeonCard {
        SectionTitle("Question")
        Text(question, fontSize = 16.sp, color = Texte)
    }
    if (options != null) {
        for (i in 0 until options.length()) {
            val label = options.optString(i)
            OutlinedButton(
                onClick = { onSubmit(JSONObject().put("choice", i)) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text(label, fontSize = 15.sp, color = Texte) }
        }
    }
}

// ---------------- Jeu : Calcul Éclair (chronométré) ----------------
@Composable
private fun CalculPlay(prompt: JSONObject, roundId: String, onSubmit: (JSONObject) -> Unit) {
    val expr = prompt.optString("expr")
    val seconds = prompt.optInt("seconds", 12)
    var value by remember(roundId) { mutableStateOf("") }
    var remaining by remember(roundId) { mutableStateOf(seconds) }
    var sent by remember(roundId) { mutableStateOf(false) }

    LaunchedEffect(roundId) {
        remaining = seconds
        while (remaining > 0 && !sent) {
            delay(1000)
            remaining -= 1
        }
        if (!sent) {
            sent = true
            onSubmit(JSONObject().put("value", value.trim().toLongOrNull() ?: -1L))
        }
    }

    NeonCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Calcule vite !")
            Spacer(Modifier.weight(1f))
            Text("⏱ ${remaining}s", fontWeight = FontWeight.Bold,
                color = if (remaining <= 3) Corail else Menthe, fontSize = 16.sp)
        }
        Text("$expr = ?", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Texte,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    FormField(value, { value = it }, "Ton résultat", keyboard = KeyboardType.Number)
    Button(
        onClick = {
            if (!sent) {
                sent = true
                onSubmit(JSONObject().put("value", value.trim().toLongOrNull() ?: -1L))
            }
        },
        enabled = value.isNotBlank(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) { Text("Valider", fontWeight = FontWeight.Bold) }
}

// ---------------- Jeu : Suite Logique ----------------
@Composable
private fun SuitePlay(prompt: JSONObject, onSubmit: (JSONObject) -> Unit) {
    val terms = prompt.optJSONArray("terms")
    val shown = buildString {
        if (terms != null) {
            for (i in 0 until terms.length()) { append(terms.optInt(i)); append(",  ") }
        }
        append("?")
    }
    var value by remember { mutableStateOf("") }
    NeonCard {
        SectionTitle("Quel est le nombre suivant ?")
        Text(shown, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Texte,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    FormField(value, { value = it }, "Le nombre suivant", keyboard = KeyboardType.Number)
    Button(
        onClick = { onSubmit(JSONObject().put("value", value.trim().toLongOrNull() ?: -1L)) },
        enabled = value.isNotBlank(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) { Text("Valider", fontWeight = FontWeight.Bold) }
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
    NeonCard {
        Text(
            if (won) "🎉 Gagné !" else "😕 Perdu",
            fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = if (won) Menthe else Corail,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        if (won) {
            Text(
                "+ ${GameApi.formatUsd(result.rewardCents.toLong())} ajouté à ton solde",
                fontSize = 15.sp, color = Menthe,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        } else {
            val good = correctText(gameKey, result.answer)
            if (good != null) Text(
                "Bonne réponse : $good",
                fontSize = 14.sp, color = TexteDoux,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Solde : ${GameApi.formatLocal(result.balanceCents, result.currency, result.fxPerUsd)}",
            fontSize = 14.sp, color = Texte,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }

    // Message de fin de quota / blocage renvoyé par le serveur
    val note = when (result.message) {
        "quota_done" -> "Tu as épuisé ton quota du jour. Les jeux reviennent dans 24 h."
        "locked_losses" -> "3 pertes d'affilée : pause de 24 h. Reviens plus tard."
        "timeout" -> "Temps écoulé !"
        else -> null
    }
    if (note != null) {
        NeonCard { Text(note, fontSize = 13.sp, color = Ambre) }
    }

    if (canReplay) {
        Button(
            onClick = onReplay,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Jouer une autre manche", fontWeight = FontWeight.Bold) }
    }
    OutlinedButton(
        onClick = onBack,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) { Text("Retour aux jeux", color = Texte) }
}

private fun correctText(gameKey: String, answer: JSONObject?): String? {
    if (answer == null) return null
    return when (gameKey) {
        "mot" -> answer.optString("word")
        "calcul", "suite" -> answer.opt("answer")?.toString()
        else -> null   // le quiz montre déjà les options
    }
}

// ---------------- Vue de blocage ----------------
@Composable
private fun BlockedView(title: String, msg: String, onBack: () -> Unit) {
    NeonCard {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ambre)
        Text(msg, fontSize = 14.sp, color = TexteDoux)
    }
    Button(
        onClick = onBack,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Violet),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) { Text("Retour aux jeux", fontWeight = FontWeight.Bold, color = Color.White) }
}
