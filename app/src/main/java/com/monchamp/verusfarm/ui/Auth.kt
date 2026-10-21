package com.monchamp.verusfarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monchamp.verusfarm.Prefs
import com.monchamp.verusfarm.Supa
import kotlinx.coroutines.launch

private enum class AuthMode { LOGIN, REGISTER, VERIFY, FORGOT, RESET }

@Composable
fun AuthFlow(onAuthed: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var referral by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var pendingReferral by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Fond, FondBas)))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Verumine", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Neon)
        Text(
            I18n.tr(when (mode) {
                AuthMode.LOGIN -> "Connecte-toi pour continuer"
                AuthMode.REGISTER -> "Crée ton compte Verumine"
                AuthMode.VERIFY -> "Vérifie ton adresse email"
                AuthMode.FORGOT -> "Réinitialise ton mot de passe"
                AuthMode.RESET -> "Choisis un nouveau mot de passe"
            }),
            fontSize = 14.sp, color = TexteDoux, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp)
        )

        when (mode) {
            AuthMode.LOGIN -> {
                FormField(email, { email = it }, "Email", KeyboardType.Email)
                Spacer(Modifier.height(12.dp))
                FormField(password, { password = it }, "Mot de passe", KeyboardType.Password)
            }
            AuthMode.REGISTER -> {
                FormField(fullName, { fullName = it }, "Nom complet")
                Spacer(Modifier.height(12.dp))
                FormField(email, { email = it }, "Email", KeyboardType.Email)
                Spacer(Modifier.height(12.dp))
                FormField(phone, { phone = it }, "Numéro de téléphone", KeyboardType.Phone)
                Spacer(Modifier.height(12.dp))
                FormField(password, { password = it }, "Mot de passe (min. 8 caractères)", KeyboardType.Password)
                Spacer(Modifier.height(12.dp))
                FormField(referral, { referral = it }, "Code de parrainage (facultatif)")
            }
            AuthMode.VERIFY -> {
                Text(
                    I18n.tr("Un code de vérification a été envoyé à") + "\n$email",
                    color = TexteDoux, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                FormField(codeInput, { codeInput = it }, "Code de vérification", KeyboardType.Number)
            }
            AuthMode.FORGOT -> {
                FormField(email, { email = it }, "Email de ton compte", KeyboardType.Email)
            }
            AuthMode.RESET -> {
                Text(
                    I18n.tr("Un code a été envoyé à") + "\n$email",
                    color = TexteDoux, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                FormField(codeInput, { codeInput = it }, "Code de vérification", KeyboardType.Number)
                Spacer(Modifier.height(12.dp))
                FormField(password, { password = it }, "Nouveau mot de passe (min. 8)", KeyboardType.Password)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(I18n.tr(error!!), color = Corail, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        if (info != null) {
            Spacer(Modifier.height(10.dp))
            Text(I18n.tr(info!!), color = Menthe, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                error = null; info = null
                when (mode) {
                    AuthMode.LOGIN -> {
                        if (email.isBlank() || password.isBlank()) { error = "Entre ton email et ton mot de passe."; return@Button }
                        busy = true
                        scope.launch {
                            val r = Supa.signIn(ctx, email, password)
                            busy = false
                            if (r.ok) onAuthed() else error = r.message
                        }
                    }
                    AuthMode.REGISTER -> {
                        if (fullName.isBlank() || email.isBlank() || password.length < 8) {
                            error = "Remplis ton nom, un email valide et un mot de passe d'au moins 8 caractères."
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            val r = Supa.signUp(email, password, fullName, phone)
                            busy = false
                            if (r.ok) { pendingReferral = referral; mode = AuthMode.VERIFY; info = "Compte créé. Entre le code reçu par email." }
                            else error = r.message
                        }
                    }
                    AuthMode.VERIFY -> {
                        if (codeInput.trim().length < 4) { error = "Entre le code de vérification reçu par email."; return@Button }
                        busy = true
                        scope.launch {
                            val r = Supa.verifyOtp(ctx, email, codeInput)
                            if (r.ok) {
                                // applique le parrainage si un code a été saisi (notifie le parrain)
                                if (pendingReferral.isNotBlank()) Supa.applyReferral(ctx, pendingReferral)
                                busy = false
                                onAuthed()
                            } else { busy = false; error = r.message }
                        }
                    }
                    AuthMode.FORGOT -> {
                        if (email.isBlank()) { error = "Entre l'email de ton compte."; return@Button }
                        busy = true
                        scope.launch {
                            val r = Supa.requestPasswordReset(email)
                            busy = false
                            if (r.ok) { codeInput = ""; password = ""; mode = AuthMode.RESET; info = r.message }
                            else error = r.message
                        }
                    }
                    AuthMode.RESET -> {
                        if (codeInput.trim().length < 4 || password.length < 8) {
                            error = "Entre le code de vérification et un nouveau mot de passe d'au moins 8 caractères."
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            val v = Supa.verifyRecovery(ctx, email, codeInput)
                            if (!v.ok) { busy = false; error = v.message; return@launch }
                            val u = Supa.updatePassword(ctx, password)
                            busy = false
                            if (u.ok) onAuthed() else error = u.message
                        }
                    }
                }
            },
            enabled = !busy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                I18n.tr(if (busy) "Patiente…" else when (mode) {
                    AuthMode.LOGIN -> "Se connecter"
                    AuthMode.REGISTER -> "Créer mon compte"
                    AuthMode.VERIFY -> "Vérifier"
                    AuthMode.FORGOT -> "Envoyer le code"
                    AuthMode.RESET -> "Réinitialiser"
                }),
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }

        // Connexion avec Google (ouvre le navigateur, revient dans l'app via un lien)
        if (mode == AuthMode.LOGIN || mode == AuthMode.REGISTER) {
            Spacer(Modifier.height(14.dp))
            Text(I18n.tr("ou"), color = Brume, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = {
                    try {
                        ctx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(Supa.googleAuthUrl())
                            )
                        )
                    } catch (_: Exception) {
                        error = "Impossible d'ouvrir le navigateur pour Google."
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(I18n.tr("Continuer avec Google"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        Spacer(Modifier.height(18.dp))

        when (mode) {
            AuthMode.LOGIN -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    I18n.tr("Mot de passe oublié ?"),
                    color = Lavande, fontSize = 14.sp,
                    modifier = Modifier.clickable { error = null; info = null; codeInput = ""; mode = AuthMode.FORGOT }
                )
                Text(
                    I18n.tr("Pas de compte ? Créer un compte"),
                    color = Lavande, fontSize = 14.sp,
                    modifier = Modifier.clickable { error = null; info = null; mode = AuthMode.REGISTER }
                )
            }
            AuthMode.FORGOT, AuthMode.RESET -> Text(
                I18n.tr("Retour à la connexion"),
                color = Lavande, fontSize = 14.sp,
                modifier = Modifier.clickable { error = null; info = null; mode = AuthMode.LOGIN }
            )
            AuthMode.REGISTER -> Text(
                I18n.tr("Déjà un compte ? Se connecter"),
                color = Lavande, fontSize = 14.sp,
                modifier = Modifier.clickable { error = null; info = null; mode = AuthMode.LOGIN }
            )
            AuthMode.VERIFY -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    I18n.tr("Renvoyer le code"),
                    color = Lavande, fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        error = null
                        scope.launch { val r = Supa.resend(email); info = if (r.ok) r.message else null; if (!r.ok) error = r.message }
                    }
                )
                Text(
                    I18n.tr("Modifier l'email"),
                    color = TexteDoux, fontSize = 13.sp,
                    modifier = Modifier.clickable { error = null; info = null; mode = AuthMode.REGISTER }
                )
            }
        }

        Spacer(Modifier.height(30.dp))
        Text(
            I18n.tr("En continuant, tu acceptes que Verumine mine de la crypto sur ton appareil selon tes réglages."),
            color = Brume, fontSize = 11.sp, textAlign = TextAlign.Center
        )
    }
}
