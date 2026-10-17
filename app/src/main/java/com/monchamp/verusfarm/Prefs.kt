package com.monchamp.verusfarm

import android.content.Context

enum class PowerMode(val label: String) {
    ECO("Éco"),
    BALANCED("Équilibré"),
    MAX("Maximum")
}

// Petite mémoire de l'application (survit aux redémarrages du téléphone)
object Prefs {
    private const val FILE = "verusfarm"

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isFirstRun(ctx: Context): Boolean = !sp(ctx).getBoolean("first_run_done", false)
    fun markFirstRunDone(ctx: Context) = sp(ctx).edit().putBoolean("first_run_done", true).apply()

    fun isEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, value: Boolean) = sp(ctx).edit().putBoolean("enabled", value).apply()

    fun wallet(ctx: Context): String =
        sp(ctx).getString("wallet", null)?.takeIf { it.isNotBlank() } ?: Config.DEFAULT_WALLET

    fun setWallet(ctx: Context, value: String) = sp(ctx).edit().putString("wallet", value.trim()).apply()

    fun customName(ctx: Context): String = sp(ctx).getString("worker_name", "") ?: ""
    fun setCustomName(ctx: Context, value: String) =
        sp(ctx).edit().putString("worker_name", value.filter { it.isLetterOrDigit() }.lowercase().take(12)).apply()

    // Puissance de calcul choisie par l'utilisateur sur la page Minage (Faible / Moyen / Max).
    // Par défaut : Maximum (100 % de la puissance du téléphone).
    fun mode(ctx: Context): PowerMode =
        when (sp(ctx).getString("power_mode", "MAX")) {
            "ECO" -> PowerMode.ECO
            "BALANCED" -> PowerMode.BALANCED
            else -> PowerMode.MAX
        }

    fun setMode(ctx: Context, value: PowerMode) =
        sp(ctx).edit().putString("power_mode", value.name).apply()

    // Quel programme de minage fonctionne sur ce téléphone (0 = optimisé, 1 = secours)
    fun engineIndex(ctx: Context): Int = sp(ctx).getInt("engine_idx", 0)
    fun setEngineIndex(ctx: Context, value: Int) = sp(ctx).edit().putInt("engine_idx", value).apply()

    // Temps de minage cumulé (millisecondes)
    fun miningMs(ctx: Context): Long = sp(ctx).getLong("mining_ms", 0L)
    fun addMiningMs(ctx: Context, delta: Long) =
        sp(ctx).edit().putLong("mining_ms", miningMs(ctx) + delta).apply()

    // Identifiant aléatoire de secours, créé une seule fois
    fun randomSuffix(ctx: Context): String {
        val existing = sp(ctx).getString("random_suffix", null)
        if (!existing.isNullOrBlank()) return existing
        val created = java.util.UUID.randomUUID().toString().replace("-", "").take(6)
        sp(ctx).edit().putString("random_suffix", created).apply()
        return created
    }

    // ---------------------------------------------------------------- Double
    private fun getDouble(ctx: Context, key: String, def: Double): Double {
        val bits = sp(ctx).getLong(key, java.lang.Double.doubleToRawLongBits(def))
        return java.lang.Double.longBitsToDouble(bits)
    }
    private fun putDouble(ctx: Context, key: String, v: Double) =
        sp(ctx).edit().putLong(key, java.lang.Double.doubleToRawLongBits(v)).apply()

    // ---------------------------------------------- Compteur simulé (accueil)
    fun simStart(ctx: Context): Long {
        val s = sp(ctx).getLong("sim_start", 0L)
        if (s != 0L) return s
        val now = System.currentTimeMillis()
        sp(ctx).edit().putLong("sim_start", now).apply()
        return now
    }
    fun simBonus(ctx: Context): Double = getDouble(ctx, "sim_bonus", 0.0)
    fun addSimBonus(ctx: Context, v: Double) = putDouble(ctx, "sim_bonus", simBonus(ctx) + v)

    fun simVersion(ctx: Context): Int = sp(ctx).getInt("sim_version", 0)
    fun resetSim(ctx: Context, version: Int) {
        sp(ctx).edit()
            .putLong("sim_start", System.currentTimeMillis())
            .putLong("sim_bonus", java.lang.Double.doubleToRawLongBits(0.0))
            .putInt("sim_version", version)
            .apply()
    }

    // --------------------------------------------- Compteur de tapotages (total)
    fun tapCount(ctx: Context): Long = sp(ctx).getLong("tap_count", 0L)
    fun addTap(ctx: Context) = sp(ctx).edit().putLong("tap_count", tapCount(ctx) + 1L).apply()

    // ------------------------------------------------------- Énergie / tapotage
    fun energyValue(ctx: Context): Float = sp(ctx).getFloat("energy_val", 100f)
    fun energyTs(ctx: Context): Long = sp(ctx).getLong("energy_ts", System.currentTimeMillis())
    fun setEnergy(ctx: Context, value: Float, ts: Long) =
        sp(ctx).edit().putFloat("energy_val", value).putLong("energy_ts", ts).apply()

    // Dernière fois qu'on a prévenu l'utilisateur que l'énergie était pleine
    fun energyNotified(ctx: Context): Boolean = sp(ctx).getBoolean("energy_notified", true)
    fun setEnergyNotified(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("energy_notified", v).apply()

    // ------------------------------------------------------------------- XP
    fun xp(ctx: Context): Long = sp(ctx).getLong("xp", 0L)
    fun addXp(ctx: Context, delta: Long) = sp(ctx).edit().putLong("xp", xp(ctx) + delta).apply()
    fun lastNotifiedLevel(ctx: Context): Int = sp(ctx).getInt("xp_level_notified", 0)
    fun setLastNotifiedLevel(ctx: Context, v: Int) = sp(ctx).edit().putInt("xp_level_notified", v).apply()

    // --------------------------------------------------- Solde retirable (VRSC)
    //  Calculé à partir des calculs acceptés par Luckpool pour cet appareil.
    fun availableVrsc(ctx: Context): Double = getDouble(ctx, "available_vrsc", 0.0)
    fun setAvailableVrsc(ctx: Context, v: Double) = putDouble(ctx, "available_vrsc", v)

    // Nombre cumulé de calculs (parts) acceptés par Luckpool pour cet appareil
    fun acceptedShares(ctx: Context): Double = getDouble(ctx, "accepted_shares", 0.0)
    fun addAcceptedShares(ctx: Context, v: Double) = putDouble(ctx, "accepted_shares", acceptedShares(ctx) + v)
    // Dernière valeur de parts vue chez Luckpool pour le worker (pour calculer l'écart)
    fun lastWorkerShares(ctx: Context): Double = getDouble(ctx, "last_worker_shares", 0.0)
    fun setLastWorkerShares(ctx: Context, v: Double) = putDouble(ctx, "last_worker_shares", v)
    // Prix estimé de 10 calculs acceptés (VRSC), dérivé des vraies récompenses de Luckpool
    fun pricePer10(ctx: Context): Double = getDouble(ctx, "price_per10", 0.0)
    fun setPricePer10(ctx: Context, v: Double) = putDouble(ctx, "price_per10", v)

    // ------------------------------- Rappels (battement de cœur, même hors ligne)
    // Dernier moment où on a prévenu « connecte-toi » / « tu ne mines pas ».
    fun lastReminderAt(ctx: Context, kind: String): Long = sp(ctx).getLong("reminder_$kind", 0L)
    fun setLastReminderAt(ctx: Context, kind: String, ts: Long) =
        sp(ctx).edit().putLong("reminder_$kind", ts).apply()

    // ----------------------------------------- Configuration initiale (onboarding)
    fun setupDone(ctx: Context): Boolean = sp(ctx).getBoolean("setup_done", false)
    fun setSetupDone(ctx: Context) = sp(ctx).edit().putBoolean("setup_done", true).apply()

    // Récompense « roulette » en attente : déclenchée à la reconnexion après une coupure internet.
    fun pendingRoulette(ctx: Context): Boolean = sp(ctx).getBoolean("pending_roulette", false)
    fun setPendingRoulette(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("pending_roulette", v).apply()

    // Réglages constructeur (OEM) qu'on ne peut pas vérifier par programme :
    // l'utilisateur confirme lui-même qu'il les a activés (bouton « C'est fait »).
    fun autostartDone(ctx: Context): Boolean = sp(ctx).getBoolean("oem_autostart_done", false)
    fun setAutostartDone(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("oem_autostart_done", v).apply()
    fun recentsLockDone(ctx: Context): Boolean = sp(ctx).getBoolean("oem_recents_done", false)
    fun setRecentsLockDone(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("oem_recents_done", v).apply()

    // ------------------------------------------------- Dernier pays choisi
    fun lastCountry(ctx: Context): String = sp(ctx).getString("last_country", "") ?: ""
    fun setLastCountry(ctx: Context, code: String) = sp(ctx).edit().putString("last_country", code).apply()

    // --------------------------------------- Historique des demandes de retrait
    fun withdrawals(ctx: Context): String = sp(ctx).getString("withdrawals", "[]") ?: "[]"
    fun setWithdrawals(ctx: Context, json: String) = sp(ctx).edit().putString("withdrawals", json).apply()

    // --------------------------------------- Notifications (page Notifications)
    fun notificationsJson(ctx: Context): String = sp(ctx).getString("notifications", "[]") ?: "[]"
    fun setNotificationsJson(ctx: Context, json: String) = sp(ctx).edit().putString("notifications", json).apply()
    fun welcomeNotifDone(ctx: Context): Boolean = sp(ctx).getBoolean("welcome_notif", false)
    fun setWelcomeNotifDone(ctx: Context) = sp(ctx).edit().putBoolean("welcome_notif", true).apply()

    // --------------------------------------- Compte / session Supabase
    fun loggedIn(ctx: Context): Boolean = sp(ctx).getString("sb_access", "").isNullOrBlank().not()
    fun accessToken(ctx: Context): String = sp(ctx).getString("sb_access", "") ?: ""
    fun refreshToken(ctx: Context): String = sp(ctx).getString("sb_refresh", "") ?: ""
    fun userId(ctx: Context): String = sp(ctx).getString("sb_uid", "") ?: ""
    fun accountEmail(ctx: Context): String = sp(ctx).getString("sb_email", "") ?: ""
    fun setSession(ctx: Context, access: String, refresh: String, uid: String, email: String) =
        sp(ctx).edit()
            .putString("sb_access", access).putString("sb_refresh", refresh)
            .putString("sb_uid", uid).putString("sb_email", email).apply()
    fun clearSession(ctx: Context) =
        sp(ctx).edit().remove("sb_access").remove("sb_refresh").remove("sb_uid").remove("sb_email").apply()

    // Code de parrainage de CET utilisateur (local pour l'instant ; remplacé par le serveur plus tard).
    fun referralCode(ctx: Context): String {
        val existing = sp(ctx).getString("referral_code", null)
        if (!existing.isNullOrBlank()) return existing
        val created = java.util.UUID.randomUUID().toString().replace("-", "").uppercase().take(8)
        sp(ctx).edit().putString("referral_code", created).apply()
        return created
    }
    fun setReferralCode(ctx: Context, code: String) =
        sp(ctx).edit().putString("referral_code", code.trim().uppercase()).apply()
    // Code de parrainage saisi à l'inscription (le parrain), s'il y en a un.
    fun referredByCode(ctx: Context): String = sp(ctx).getString("referred_by", "") ?: ""
    fun setReferredByCode(ctx: Context, code: String) =
        sp(ctx).edit().putString("referred_by", code.trim().uppercase()).apply()
}
