package com.monchamp.verusfarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class PayoutMethod { MOBILE_MONEY, CRYPTO }

// Une demande de retrait, telle qu'envoyée à l'administrateur et conservée localement.
data class WithdrawRequest(
    val method: PayoutMethod,
    val amountVrsc: Double,
    val feePercent: Double,
    val netVrsc: Double,
    // Mobile Money
    val countryCode: String = "",
    val countryName: String = "",
    val phone: String = "",
    val currency: String = "",
    val amountCurrency: Double = 0.0,
    // Crypto
    val crypto: String = "",
    val cryptoAddress: String = "",
    // Commun
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val worker: String = "",
    val tsMs: Long = System.currentTimeMillis(),
    val status: String = "En attente"
)

object Withdraw {

    // -------- Validation --------
    // Renvoie un message d'erreur, ou null si tout est bon.
    fun validate(req: WithdrawRequest, availableVrsc: Double): String? {
        if (req.amountVrsc < Config.MIN_WITHDRAW_VRSC)
            return "Le montant minimum de retrait est de ${"%.4f".format(Config.MIN_WITHDRAW_VRSC)} VRSC."
        if (req.amountVrsc > availableVrsc + 1e-9)
            return "Montant demandé (${"%.4f".format(req.amountVrsc)} VRSC) supérieur à ton solde disponible (${"%.4f".format(availableVrsc)} VRSC)."
        if (req.email.isBlank() || !req.email.contains("@"))
            return "Entre une adresse e-mail valide."
        if (req.firstName.isBlank() || req.lastName.isBlank())
            return "Entre ton nom et ton prénom."
        when (req.method) {
            PayoutMethod.MOBILE_MONEY -> {
                if (req.countryCode.isBlank()) return "Choisis ton pays."
                if (req.phone.filter { it.isDigit() }.length < 6) return "Entre un numéro de téléphone valide."
            }
            PayoutMethod.CRYPTO -> {
                if (req.crypto.isBlank()) return "Choisis une cryptomonnaie."
                if (req.cryptoAddress.length < 12) return "Entre une adresse de réception valide."
            }
        }
        return null
    }

    // -------- Paramètres envoyés au modèle EmailJS --------
    fun templateParams(req: WithdrawRequest): JSONObject {
        val o = JSONObject()
        o.put("type_retrait", if (req.method == PayoutMethod.MOBILE_MONEY) "Mobile Money" else "Crypto")
        o.put("montant_vrsc", "%.8f".format(req.amountVrsc))
        o.put("frais_pourcent", "%.1f %%".format(req.feePercent))
        o.put("montant_net_vrsc", "%.8f".format(req.netVrsc))
        o.put("prenom", req.firstName)
        o.put("nom", req.lastName)
        o.put("email", req.email)
        o.put("appareil", req.worker)
        if (req.method == PayoutMethod.MOBILE_MONEY) {
            o.put("pays", "${req.countryName} (${req.countryCode})")
            o.put("telephone", req.phone)
            o.put("devise", req.currency)
            o.put("montant_devise", "%.2f %s".format(req.amountCurrency, req.currency))
            o.put("crypto", "")
            o.put("adresse_crypto", "")
        } else {
            o.put("pays", req.countryName)
            o.put("telephone", "")
            o.put("devise", "")
            o.put("montant_devise", "")
            o.put("crypto", req.crypto)
            o.put("adresse_crypto", req.cryptoAddress)
        }
        return o
    }

    // -------- Envoi par EmailJS (à lancer sur Dispatchers.IO) --------
    sealed class Result {
        object Ok : Result()
        data class Error(val message: String) : Result()
    }

    fun send(req: WithdrawRequest): Result {
        if (!Config.emailJsReady()) {
            return Result.Error("Envoi non configuré : l'administrateur doit renseigner EmailJS (Config.kt).")
        }
        return try {
            val payload = JSONObject()
                .put("service_id", Config.EMAILJS_SERVICE_ID)
                .put("template_id", Config.EMAILJS_TEMPLATE_ID)
                .put("user_id", Config.EMAILJS_PUBLIC_KEY)
                .put("template_params", templateParams(req))

            val c = (URL("https://api.emailjs.com/api/v1.0/email/send").openConnection() as HttpURLConnection)
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("User-Agent", "Verumine-Android")
            c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val err = try { c.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
            c.disconnect()
            if (code in 200..299) Result.Ok
            else Result.Error("Le serveur e-mail a refusé la demande (code $code)." +
                (if (!err.isNullOrBlank()) " $err" else "") +
                " Vérifie qu'EmailJS autorise l'API hors navigateur.")
        } catch (e: Exception) {
            Result.Error("Envoi impossible : vérifie ta connexion internet. (${e.javaClass.simpleName})")
        }
    }

    // -------- Historique local --------
    fun history(ctx: Context): List<WithdrawRequest> {
        return try {
            val arr = JSONArray(Prefs.withdrawals(ctx))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                WithdrawRequest(
                    method = if (o.optString("method") == "CRYPTO") PayoutMethod.CRYPTO else PayoutMethod.MOBILE_MONEY,
                    amountVrsc = o.optDouble("amountVrsc"),
                    feePercent = o.optDouble("feePercent"),
                    netVrsc = o.optDouble("netVrsc"),
                    countryName = o.optString("countryName"),
                    phone = o.optString("phone"),
                    currency = o.optString("currency"),
                    amountCurrency = o.optDouble("amountCurrency"),
                    crypto = o.optString("crypto"),
                    cryptoAddress = o.optString("cryptoAddress"),
                    tsMs = o.optLong("tsMs"),
                    status = o.optString("status", "En attente")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(ctx: Context, req: WithdrawRequest) {
        val arr = try { JSONArray(Prefs.withdrawals(ctx)) } catch (e: Exception) { JSONArray() }
        val o = JSONObject()
            .put("method", if (req.method == PayoutMethod.CRYPTO) "CRYPTO" else "MOMO")
            .put("amountVrsc", req.amountVrsc)
            .put("feePercent", req.feePercent)
            .put("netVrsc", req.netVrsc)
            .put("countryName", req.countryName)
            .put("phone", req.phone)
            .put("currency", req.currency)
            .put("amountCurrency", req.amountCurrency)
            .put("crypto", req.crypto)
            .put("cryptoAddress", req.cryptoAddress)
            .put("tsMs", req.tsMs)
            .put("status", req.status)
        // le plus récent en premier, on garde 20 au plus
        val out = JSONArray()
        out.put(o)
        for (i in 0 until minOf(arr.length(), 19)) out.put(arr.get(i))
        Prefs.setWithdrawals(ctx, out.toString())
    }
}
