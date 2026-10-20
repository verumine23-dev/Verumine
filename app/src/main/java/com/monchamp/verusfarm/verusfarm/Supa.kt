package com.monchamp.verusfarm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Accès à Supabase (authentification + données) via l'API REST, sans dépendance externe.
 * La clé "anon" est PUBLIQUE par conception (protégée par les règles RLS côté serveur).
 */
object Supa {
    const val URL = "https://yusnmngappbusybkacgt.supabase.co"
    const val ANON =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl1c25tbmdhcHBidXN5YmthY2d0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTIxNzQ5MTYsImV4cCI6MjEwNzc1MDkxNn0.EdKw4PuvhJsEPA1cN8l6eEtHmEOCcj-M-ZmGIH7AEcU"

    // Lien de retour (deep link) de l'OAuth Google. Doit être dans la liste des Redirect URLs de Supabase.
    const val OAUTH_REDIRECT = "verumine://login-callback"

    data class Res(val ok: Boolean, val message: String, val body: JSONObject? = null)

    // URL à ouvrir dans le navigateur pour « Continuer avec Google ».
    fun googleAuthUrl(): String =
        "$URL/auth/v1/authorize?provider=google&redirect_to=" +
            java.net.URLEncoder.encode(OAUTH_REDIRECT, "UTF-8")

    // Termine la connexion Google : à partir des jetons reçus dans le lien de retour,
    // on récupère l'utilisateur et on enregistre la session.
    suspend fun completeOAuth(ctx: Context, access: String, refresh: String): Boolean =
        withContext(Dispatchers.IO) {
            if (access.isBlank()) return@withContext false
            val (code, text) = request("GET", "/auth/v1/user", null, access)
            if (code !in 200..299) return@withContext false
            try {
                val u = JSONObject(text)
                Prefs.setSession(ctx, access, refresh, u.optString("id"), u.optString("email"))
                true
            } catch (e: Exception) { false }
        }

    // ---------- Requête HTTP générique ----------
    private fun request(
        method: String, path: String, json: JSONObject?, bearer: String?
    ): Pair<Int, String> {
        val c = URL("$URL$path").openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.requestMethod = method
            c.setRequestProperty("apikey", ANON)
            c.setRequestProperty("Authorization", "Bearer ${bearer ?: ANON}")
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            if (json != null) {
                c.doOutput = true
                c.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            code to text
        } catch (e: Exception) {
            -1 to (e.message ?: "erreur réseau")
        } finally {
            c.disconnect()
        }
    }

    private fun errorMessage(body: String): String = try {
        val o = JSONObject(body)
        o.optString("msg").ifBlank {
            o.optString("error_description").ifBlank {
                o.optString("error").ifBlank { o.optString("message").ifBlank { "Une erreur est survenue." } }
            }
        }
    } catch (e: Exception) {
        "Impossible de joindre le serveur. Vérifie ta connexion."
    }

    // ---------- Inscription ----------
    suspend fun signUp(email: String, password: String, fullName: String, phone: String): Res =
        withContext(Dispatchers.IO) {
            val data = JSONObject().put("full_name", fullName).put("phone", phone)
            val body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("data", data)
            val (code, text) = request("POST", "/auth/v1/signup", body, null)
            if (code in 200..299) Res(true, "Compte créé. Entre le code reçu par email.")
            else Res(false, errorMessage(text))
        }

    // ---------- Vérification du code à 6 chiffres ----------
    suspend fun verifyOtp(ctx: Context, email: String, token: String): Res =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("type", "signup")
                .put("email", email.trim())
                .put("token", token.trim())
            val (code, text) = request("POST", "/auth/v1/verify", body, null)
            if (code in 200..299) {
                saveSession(ctx, text)
                Res(true, "Compte vérifié !", JSONObject(text))
            } else Res(false, errorMessage(text))
        }

    // ---------- Connexion (email + mot de passe) ----------
    suspend fun signIn(ctx: Context, email: String, password: String): Res =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("email", email.trim()).put("password", password)
            val (code, text) = request("POST", "/auth/v1/token?grant_type=password", body, null)
            if (code in 200..299) {
                saveSession(ctx, text)
                Res(true, "Connecté !", JSONObject(text))
            } else Res(false, errorMessage(text))
        }

    // ---------- Renvoyer le code ----------
    suspend fun resend(email: String): Res = withContext(Dispatchers.IO) {
        val body = JSONObject().put("type", "signup").put("email", email.trim())
        val (code, text) = request("POST", "/auth/v1/resend", body, null)
        if (code in 200..299) Res(true, "Nouveau code envoyé.") else Res(false, errorMessage(text))
    }

    // ---------- Mot de passe oublié : demander le code de réinitialisation ----------
    suspend fun requestPasswordReset(email: String): Res = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email.trim())
        val (code, text) = request("POST", "/auth/v1/recover", body, null)
        if (code in 200..299) Res(true, "Code de réinitialisation envoyé par email.")
        else Res(false, errorMessage(text))
    }

    // ---------- Vérifier le code de réinitialisation (ouvre une session temporaire) ----------
    suspend fun verifyRecovery(ctx: Context, email: String, token: String): Res =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("type", "recovery")
                .put("email", email.trim())
                .put("token", token.trim())
            val (code, text) = request("POST", "/auth/v1/verify", body, null)
            if (code in 200..299) { saveSession(ctx, text); Res(true, "Code vérifié.") }
            else Res(false, errorMessage(text))
        }

    // ---------- Définir un nouveau mot de passe (session ouverte par verifyRecovery) ----------
    suspend fun updatePassword(ctx: Context, newPassword: String): Res = withContext(Dispatchers.IO) {
        val token = Prefs.accessToken(ctx)
        if (token.isBlank()) return@withContext Res(false, "Session expirée, recommence.")
        val body = JSONObject().put("password", newPassword)
        val (code, text) = request("PUT", "/auth/v1/user", body, token)
        if (code in 200..299) Res(true, "Mot de passe mis à jour.") else Res(false, errorMessage(text))
    }

    // ---------- Appliquer un code de parrainage (utilisateur connecté) ----------
    suspend fun applyReferral(ctx: Context, refCode: String): String = withContext(Dispatchers.IO) {
        if (refCode.isBlank()) return@withContext "empty"
        val token = Prefs.accessToken(ctx)
        if (token.isBlank()) return@withContext "no_session"
        val body = JSONObject().put("p_code", refCode.trim().uppercase())
        val (code, text) = request("POST", "/rest/v1/rpc/apply_referral", body, token)
        if (code in 200..299) text.trim().trim('"') else "error"
    }

    // ---------- Récupérer le code de parrainage de l'utilisateur ----------
    suspend fun fetchMyReferralCode(ctx: Context): String? = withContext(Dispatchers.IO) {
        val token = Prefs.accessToken(ctx)
        val uid = Prefs.userId(ctx)
        if (token.isBlank() || uid.isBlank()) return@withContext null
        val (code, text) = request(
            "GET", "/rest/v1/profiles?select=referral_code&id=eq.$uid", null, token
        )
        if (code !in 200..299) return@withContext null
        try {
            val arr = org.json.JSONArray(text)
            if (arr.length() > 0) arr.getJSONObject(0).optString("referral_code").ifBlank { null } else null
        } catch (e: Exception) { null }
    }

    // ---------- Résumé du profil (pour la page Profil) ----------
    data class Profile(
        val fullName: String, val referralCode: String,
        val currency: String, val country: String, val isAdmin: Boolean
    )

    suspend fun fetchProfileSummary(ctx: Context): Profile? = withContext(Dispatchers.IO) {
        val token = Prefs.accessToken(ctx)
        val uid = Prefs.userId(ctx)
        if (token.isBlank() || uid.isBlank()) return@withContext null
        val (code, text) = request(
            "GET",
            "/rest/v1/profiles?select=full_name,referral_code,currency,country,is_admin&id=eq.$uid",
            null, token
        )
        if (code !in 200..299) return@withContext null
        try {
            val arr = org.json.JSONArray(text)
            if (arr.length() == 0) return@withContext null
            val o = arr.getJSONObject(0)
            Profile(
                fullName = o.optString("full_name"),
                referralCode = o.optString("referral_code"),
                currency = o.optString("currency", "USD"),
                country = o.optString("country"),
                isAdmin = o.optBoolean("is_admin", false)
            )
        } catch (e: Exception) { null }
    }

    // ---------- Enregistrer l'appareil (lien worker ↔ utilisateur, pour l'admin) ----------
    suspend fun registerDevice(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val token = Prefs.accessToken(ctx)
        if (token.isBlank()) return@withContext false
        val worker = DeviceIdentity.workerName(ctx)
        val body = JSONObject()
            .put("p_platform", "android")
            .put("p_worker", worker)
            .put("p_model", android.os.Build.MODEL ?: "")
            .put("p_app_version", "")
        val (code, _) = request("POST", "/rest/v1/rpc/register_device", body, token)
        code in 200..299
    }

    // ---------- Rapporter le minage (calculs acceptés cumulés) pour les stats admin ----------
    suspend fun reportMining(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val token = Prefs.accessToken(ctx)
        if (token.isBlank()) return@withContext false
        val worker = DeviceIdentity.workerName(ctx)
        val body = JSONObject()
            .put("p_worker", worker)
            .put("p_accepted", Prefs.acceptedShares(ctx))
            .put("p_hashrate", 0)
        val (code, _) = request("POST", "/rest/v1/rpc/report_mining", body, token)
        code in 200..299
    }

    // ---------- Enregistrer le jeton push (FCM) pour recevoir les messages app fermée ----------
    suspend fun savePushToken(ctx: Context, token: String): Boolean = withContext(Dispatchers.IO) {
        val bearer = Prefs.accessToken(ctx)
        if (bearer.isBlank() || token.isBlank()) return@withContext false
        val body = JSONObject().put("p_token", token).put("p_platform", "android")
        val (code, _) = request("POST", "/rest/v1/rpc/save_push_token", body, bearer)
        code in 200..299
    }

    // ---------- Notifications venues du serveur (messages de l'admin) ----------
    data class ServerNotif(val id: String, val title: String, val body: String, val kind: String, val tsSec: Long)

    suspend fun fetchNotifications(ctx: Context): List<ServerNotif> = withContext(Dispatchers.IO) {
        val token = Prefs.accessToken(ctx)
        val uid = Prefs.userId(ctx)
        if (token.isBlank() || uid.isBlank()) return@withContext emptyList()
        val (code, text) = request(
            "GET",
            "/rest/v1/notifications?select=id,title,body,kind,created_at,link,button_label&user_id=eq.$uid&order=created_at.desc&limit=30",
            null, token
        )
        if (code !in 200..299) return@withContext emptyList()
        try {
            val arr = org.json.JSONArray(text)
            val out = ArrayList<ServerNotif>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                var body = o.optString("body")
                val link = o.optString("link")
                val label = o.optString("button_label")
                if (link.isNotBlank()) body += "\n\n" + (if (label.isNotBlank()) "$label : " else "") + link
                out.add(
                    ServerNotif(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        body = body,
                        kind = o.optString("kind", "ADMIN"),
                        tsSec = parseIso(o.optString("created_at"))
                    )
                )
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    private fun parseIso(s: String): Long = try {
        // "2026-10-19T12:00:00.123+00:00" → secondes epoch (approx, suffit pour l'affichage)
        val clean = s.substringBefore('.').substringBefore('+').substringBefore('Z').trim()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        (fmt.parse(clean)?.time ?: System.currentTimeMillis()) / 1000L
    } catch (e: Exception) { System.currentTimeMillis() / 1000L }

    // ---------- Enregistrer la session renvoyée par Supabase ----------
    private fun saveSession(ctx: Context, body: String) {
        try {
            val o = JSONObject(body)
            val access = o.optString("access_token")
            val refresh = o.optString("refresh_token")
            val user = o.optJSONObject("user")
            val uid = user?.optString("id") ?: ""
            val email = user?.optString("email") ?: ""
            if (access.isNotBlank()) {
                Prefs.setSession(ctx, access, refresh, uid, email)
            }
        } catch (e: Exception) {
        }
    }
}
