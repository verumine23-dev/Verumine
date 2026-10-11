package com.monchamp.verusfarm

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Conversion VRSC <-> devises. Récupère les taux en direct, avec valeurs de secours.
object Rates {

    @Volatile private var vrscUsd: Double = Config.FALLBACK_VRSC_USD
    @Volatile private var usdRates: Map<String, Double> = mapOf("USD" to 1.0, "EUR" to 0.92)
    @Volatile private var lastFetch: Long = 0L

    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Verumine-Android")
        try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    // À appeler depuis un thread d'arrière-plan (Dispatchers.IO).
    fun refresh(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastFetch < 60_000L) return true   // pas plus d'une fois par minute
        var ok = false

        get("https://api.coingecko.com/api/v3/simple/price?ids=verus-coin&vs_currencies=usd")?.let { body ->
            try {
                val p = JSONObject(body).optJSONObject("verus-coin")
                val v = p?.optDouble("usd", 0.0) ?: 0.0
                if (v > 0.0) { vrscUsd = v; ok = true }
            } catch (_: Exception) {
            }
        }

        get("https://open.er-api.com/v6/latest/USD")?.let { body ->
            try {
                val rates = JSONObject(body).optJSONObject("rates")
                if (rates != null) {
                    val m = HashMap<String, Double>()
                    val it = rates.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        m[k] = rates.optDouble(k, 0.0)
                    }
                    if (m.isNotEmpty()) { usdRates = m; ok = true }
                }
            } catch (_: Exception) {
            }
        }

        if (ok) lastFetch = now
        return ok
    }

    fun vrscToUsd(): Double = vrscUsd

    // 1 VRSC = combien dans cette devise ?
    fun vrscToCurrency(currency: String): Double {
        val fx = usdRates[currency] ?: 1.0
        return vrscUsd * fx
    }

    // Convertit un montant en VRSC vers la devise choisie.
    fun vrscAmountToCurrency(vrsc: Double, currency: String): Double = vrsc * vrscToCurrency(currency)

    // Convertit un montant en devise vers des VRSC.
    fun currencyAmountToVrsc(amount: Double, currency: String): Double {
        val perVrsc = vrscToCurrency(currency)
        return if (perVrsc > 0.0) amount / perVrsc else 0.0
    }
}
