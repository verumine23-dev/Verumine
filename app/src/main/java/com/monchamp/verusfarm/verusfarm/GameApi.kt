package com.monchamp.verusfarm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Jeux d'argent : appels aux fonctions (RPC) Supabase.
 * Toute la logique (gains, quotas, anti-triche) vit côté serveur ; l'app ne
 * fait qu'afficher et envoyer les réponses. Le solde fait foi en centimes de $.
 */
object GameApi {

    // --- Modèles ---
    data class GameInfo(
        val key: String, val name: String, val emoji: String,
        val played: Int, val wins: Int, val remaining: Int, val closed: Boolean
    )

    data class State(
        val balanceCents: Long,
        val currency: String,
        val fxPerUsd: Double,
        val minWithdrawCents: Long,
        val rewardCents: Int,
        val lockedUntil: String?,   // ISO ou null
        val activeGame: String?,
        val games: List<GameInfo>
    )

    data class RoundStart(
        val ok: Boolean, val error: String? = null,
        val roundId: String? = null, val game: String? = null,
        val prompt: JSONObject? = null, val extra: JSONObject? = null
    )

    data class SubmitResult(
        val ok: Boolean, val error: String? = null,
        val won: Boolean = false, val rewardCents: Int = 0,
        val balanceCents: Long = 0, val currency: String = "USD", val fxPerUsd: Double = 1.0,
        val lockedUntil: String? = null, val message: String? = null,
        val answer: JSONObject? = null
    )

    data class Simple(val ok: Boolean, val error: String? = null, val body: JSONObject? = null)

    // --- Requête REST générique ---
    private fun req(method: String, path: String, json: JSONObject?, bearer: String?): Pair<Int, String> {
        val c = URL("${Supa.URL}$path").openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 12_000
            c.readTimeout = 25_000
            c.requestMethod = method
            c.setRequestProperty("apikey", Supa.ANON)
            c.setRequestProperty("Authorization", "Bearer ${bearer ?: Supa.ANON}")
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            if (json != null) {
                c.doOutput = true
                c.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: Exception) {
            -1 to (e.message ?: "réseau")
        } finally {
            c.disconnect()
        }
    }

    private fun rpc(ctx: Context, fn: String, params: JSONObject): Pair<Int, String> {
        val token = Prefs.accessToken(ctx)
        var (code, text) = req("POST", "/rest/v1/rpc/$fn", params, token.ifBlank { null })
        // Jeton expiré (401/403) -> on le rafraîchit une fois puis on réessaie.
        if ((code == 401 || code == 403) && refreshToken(ctx)) {
            val t = Prefs.accessToken(ctx)
            val r = req("POST", "/rest/v1/rpc/$fn", params, t.ifBlank { null })
            code = r.first; text = r.second
        }
        return code to text
    }

    // Rafraîchit la session Supabase à partir du refresh_token stocké.
    private fun refreshToken(ctx: Context): Boolean {
        val rt = Prefs.refreshToken(ctx)
        if (rt.isBlank()) return false
        val (code, text) = req("POST", "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", rt), null)
        if (code !in 200..299) return false
        return try {
            val o = JSONObject(text)
            val access = o.optString("access_token")
            val refresh = o.optString("refresh_token")
            val user = o.optJSONObject("user")
            if (access.isNotBlank()) {
                Prefs.setSession(ctx, access, refresh, user?.optString("id") ?: Prefs.userId(ctx),
                    user?.optString("email") ?: Prefs.accountEmail(ctx))
                true
            } else false
        } catch (e: Exception) { false }
    }

    // Une fonction renvoyant jsonb peut renvoyer soit l'objet, soit "null".
    private fun asObject(text: String): JSONObject? = try {
        val t = text.trim()
        if (t.startsWith("{")) JSONObject(t) else null
    } catch (e: Exception) { null }

    // --- État complet ---
    suspend fun getState(ctx: Context): State? = withContext(Dispatchers.IO) {
        val (code, text) = rpc(ctx, "game_get_state", JSONObject())
        if (code !in 200..299) return@withContext null
        val o = asObject(text) ?: return@withContext null
        val arr = o.optJSONArray("games") ?: JSONArray()
        val games = ArrayList<GameInfo>()
        for (i in 0 until arr.length()) {
            val g = arr.getJSONObject(i)
            games.add(
                GameInfo(
                    key = g.optString("key"), name = g.optString("name"), emoji = g.optString("emoji"),
                    played = g.optInt("played"), wins = g.optInt("wins"),
                    remaining = g.optInt("remaining"), closed = g.optBoolean("closed")
                )
            )
        }
        State(
            balanceCents = o.optLong("balance_cents"),
            currency = o.optString("currency", "USD"),
            fxPerUsd = o.optDouble("fx_per_usd", 1.0),
            minWithdrawCents = o.optLong("min_withdraw_cents", 10000L),
            rewardCents = o.optInt("reward_cents", 50),
            lockedUntil = if (o.isNull("locked_until")) null else o.optString("locked_until"),
            activeGame = if (o.isNull("active_game")) null else o.optString("active_game"),
            games = games
        )
    }

    // --- Démarrer une manche ---
    suspend fun startRound(ctx: Context, game: String, subject: String? = null): RoundStart =
        withContext(Dispatchers.IO) {
            val p = JSONObject().put("p_game", game)
            if (subject != null) p.put("p_subject", subject)
            val (code, text) = rpc(ctx, "game_start_round", p)
            if (code !in 200..299) return@withContext RoundStart(false, "reseau")
            val o = asObject(text) ?: return@withContext RoundStart(false, "reseau")
            val err = if (o.has("error")) o.optString("error") else null
            if (err != null) return@withContext RoundStart(false, err, extra = o)
            RoundStart(
                true, roundId = o.optString("round_id"), game = o.optString("game"),
                prompt = o.optJSONObject("prompt")
            )
        }

    // --- Soumettre une réponse ---
    suspend fun submit(ctx: Context, roundId: String, answer: JSONObject): SubmitResult =
        withContext(Dispatchers.IO) {
            val p = JSONObject().put("p_round", roundId).put("p_answer", answer)
            val (code, text) = rpc(ctx, "game_submit", p)
            if (code !in 200..299) return@withContext SubmitResult(false, "reseau")
            val o = asObject(text) ?: return@withContext SubmitResult(false, "reseau")
            val err = if (o.has("error")) o.optString("error") else null
            if (err != null) return@withContext SubmitResult(false, err)
            SubmitResult(
                ok = true,
                won = o.optBoolean("won"),
                rewardCents = o.optInt("reward_cents"),
                balanceCents = o.optLong("balance_cents"),
                currency = o.optString("currency", "USD"),
                fxPerUsd = o.optDouble("fx_per_usd", 1.0),
                lockedUntil = if (o.isNull("locked_until")) null else o.optString("locked_until"),
                message = if (o.isNull("message")) null else o.optString("message"),
                answer = o.optJSONObject("answer")
            )
        }

    // --- Demander un retrait ---
    suspend fun requestWithdrawal(ctx: Context, method: String, details: String): Simple =
        withContext(Dispatchers.IO) {
            val p = JSONObject().put("p_method", method).put("p_details", details)
            val (code, text) = rpc(ctx, "game_request_withdrawal", p)
            if (code !in 200..299) return@withContext Simple(false, "reseau")
            val o = asObject(text) ?: return@withContext Simple(false, "reseau")
            if (o.has("error")) Simple(false, o.optString("error"), o) else Simple(true, body = o)
        }

    // ================================================================
    //  Devise locale, déduite de l'adresse IP une seule fois.
    // ================================================================
    private const val LOCALE_FLAG = "game_locale_done"

    suspend fun ensureLocale(ctx: Context) = withContext(Dispatchers.IO) {
        val sp = ctx.getSharedPreferences("verusfarm", Context.MODE_PRIVATE)
        if (sp.getBoolean(LOCALE_FLAG, false)) return@withContext
        if (Prefs.accessToken(ctx).isBlank()) return@withContext
        try {
            val conn = URL("https://ipapi.co/json/").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/json")
            val rc = conn.responseCode
            val body = (if (rc in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (rc !in 200..299) return@withContext
            val o = JSONObject(body)
            val country = o.optString("country_code", "")
            var currency = o.optString("currency", "USD").ifBlank { "USD" }
            val fx = fxFor(currency)
            val p = JSONObject()
                .put("p_country", country)
                .put("p_currency", currency)
                .put("p_fx", fx)
            val (rc2, _) = rpc(ctx, "game_set_locale", p)
            if (rc2 in 200..299) sp.edit().putBoolean(LOCALE_FLAG, true).apply()
        } catch (e: Exception) {
            // silencieux : le solde reste en USD par défaut jusqu'au prochain essai
        }
    }

    // Taux approximatifs (unités locales pour 1 $). À affiner plus tard si besoin ;
    // le solde réel reste stocké en dollars, ceci ne sert qu'à l'affichage.
    private fun fxFor(cur: String): Double = when (cur.uppercase()) {
        "USD" -> 1.0
        "EUR" -> 0.92
        "XOF", "XAF" -> 600.0      // Franc CFA (Bénin, etc.)
        "NGN" -> 1600.0            // Naira
        "GHS" -> 15.0              // Cedi
        "GBP" -> 0.79
        "CAD" -> 1.37
        "MAD" -> 10.0
        "DZD" -> 134.0
        "TND" -> 3.1
        "EGP" -> 48.0
        "KES" -> 129.0
        "ZAR" -> 18.0
        "INR" -> 83.0
        else -> 1.0
    }

    // ---- Formatage monétaire pour l'affichage ----
    fun formatLocal(cents: Long, currency: String, fx: Double): String {
        val amount = (cents / 100.0) * fx
        val decimals = if (fx >= 50) 0 else 2
        val s = String.format("%,.${decimals}f", amount)
        return "$s $currency"
    }

    fun formatUsd(cents: Long): String = String.format("%.2f $", cents / 100.0)

    // Notifications « punch » liées aux jeux (pause 24 h, retrait disponible).
    // Elles s'affichent en bannière ET sont stockées dans la page Notifications.
    fun notifyFromResult(ctx: Context, r: SubmitResult) {
        try {
            if (r.lockedUntil != null) {
                Prefs.setGamesLockedUntil(ctx, System.currentTimeMillis() + 24L * 60 * 60 * 1000)
                Prefs.setGamesUnlockNotified(ctx, false)
                RewardNotifier.gamePunch(
 ctx,"Pause jeux",
                    "Tu as atteint ta limite du jour. Reviens dans 24 h pour rejouer et gagner !"
                )
            }
            if (r.balanceCents >= 10000) {
                if (!Prefs.gamesWithdrawNotified(ctx)) {
                    RewardNotifier.gamePunch(
 ctx,"Retrait disponible",
                        "Ton solde de jeu a atteint 100 \$. Tu peux demander ton retrait depuis l'espace jeux."
                    )
                    Prefs.setGamesWithdrawNotified(ctx, true)
                }
            } else {
                Prefs.setGamesWithdrawNotified(ctx, false)
            }
        } catch (_: Exception) {
        }
    }
}
