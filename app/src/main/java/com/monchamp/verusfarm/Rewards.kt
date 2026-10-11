package com.monchamp.verusfarm

import android.content.Context

// =============================================================================
//  Système de fidélité HONNÊTE.
//
//  - Compteur d'accueil : une SIMULATION de l'activité de minage en arrière-plan.
//    Ce n'est PAS de l'argent gagné ; le vrai solde retirable est dans Wallet.
//  - Tapotage : un mini-jeu d'engagement. Chaque tape donne +2 XP et fait
//    légèrement avancer la simulation. Il n'influence pas le vrai minage.
//  - XP : accumulés par le tapotage et le temps de minage. Les niveaux donnent
//    des avantages réels : une réduction des frais de retrait de l'administrateur.
//  - Énergie : limite l'usage du tapotage. Pleine en 1 heure, consommée à chaque
//    tape. À 0 %, il faut attendre qu'elle se recharge.
// =============================================================================
object Rewards {

    // -------- Énergie --------
    const val ENERGY_MAX = 100f
    const val ENERGY_REFILL_SECONDS = 3600f      // pleine en 1 heure
    const val TAP_COST = 4f                       // chaque tape consomme 4 %
    private const val REFILL_PER_MS = ENERGY_MAX / (ENERGY_REFILL_SECONDS * 1000f)

    // Énergie actuelle, recalculée d'après le temps écoulé depuis la dernière mesure.
    fun energy(ctx: Context): Float {
        val stored = Prefs.energyValue(ctx)
        val elapsed = (System.currentTimeMillis() - Prefs.energyTs(ctx)).coerceAtLeast(0L)
        val v = (stored + elapsed * REFILL_PER_MS).coerceIn(0f, ENERGY_MAX)
        return v
    }

    fun canTap(ctx: Context): Boolean = energy(ctx) >= TAP_COST

    // Millisecondes restantes avant énergie pleine (pour programmer la notification).
    fun msUntilFull(ctx: Context): Long {
        val missing = (ENERGY_MAX - energy(ctx)).coerceAtLeast(0f)
        return (missing / REFILL_PER_MS).toLong()
    }

    data class TapResult(val ok: Boolean, val xpGain: Int, val newLevel: Int, val leveledUp: Boolean)

    // Applique une tape. Renvoie ok=false si l'énergie est insuffisante.
    fun tap(ctx: Context): TapResult {
        val current = energy(ctx)
        if (current < TAP_COST) {
            Prefs.setEnergy(ctx, current, System.currentTimeMillis())
            return TapResult(false, 0, level(Prefs.xp(ctx)), false)
        }
        Prefs.setEnergy(ctx, current - TAP_COST, System.currentTimeMillis())
        Prefs.setEnergyNotified(ctx, false)   // elle n'est plus pleine : on pourra re-notifier

        val before = level(Prefs.xp(ctx))
        Prefs.addXp(ctx, XP_PER_TAP.toLong())
        // La tape fait légèrement avancer la simulation (équivaut à ~3 s de minage simulé).
        Prefs.addSimBonus(ctx, Simulation.RATE_PER_MS * 3000.0)
        val after = level(Prefs.xp(ctx))
        return TapResult(true, XP_PER_TAP, after, after > before)
    }

    // -------- XP & niveaux --------
    const val XP_PER_TAP = 2

    // XP nécessaires pour atteindre un niveau (progression douce).
    fun xpForLevel(level: Int): Long {
        if (level <= 0) return 0
        return (100.0 * level * level).toLong()      // niv.1=100, niv.2=400, niv.3=900…
    }

    fun level(xp: Long): Int {
        var l = 0
        while (xp >= xpForLevel(l + 1)) l++
        return l
    }

    // Progression (0..1) vers le niveau suivant.
    fun levelProgress(xp: Long): Float {
        val l = level(xp)
        val cur = xpForLevel(l)
        val next = xpForLevel(l + 1)
        if (next <= cur) return 0f
        return ((xp - cur).toFloat() / (next - cur).toFloat()).coerceIn(0f, 1f)
    }

    // -------- Avantage réel : réduction des frais de retrait --------
    //  +1 % de réduction par niveau, plafonné à 15 %.
    //  (Les frais admin de 20 % peuvent donc descendre jusqu'à 5 %.)
    const val MAX_FEE_DISCOUNT = 15.0
    fun feeDiscountPercent(ctx: Context): Double =
        level(Prefs.xp(ctx)).toDouble().coerceAtMost(MAX_FEE_DISCOUNT)

    // Frais effectifs appliqués à un retrait, après réduction de fidélité.
    fun effectiveFeePercent(ctx: Context): Double =
        (Config.ADMIN_FEE_PERCENT - feeDiscountPercent(ctx)).coerceAtLeast(0.0)

    // Libellé de l'avantage du palier courant (affiché dans la page).
    fun perkLabel(level: Int): String = when {
        level <= 0 -> "Aucun avantage pour l'instant — tape pour gagner de l'XP."
        level < 5 -> "−$level % sur les frais de retrait"
        level < 10 -> "−$level % sur les frais + priorité de traitement"
        level < 15 -> "−$level % sur les frais + priorité + badge Mineur confirmé"
        else -> "−15 % sur les frais (maximum) + badge Mineur expert"
    }
}

// Compteur SIMULÉ de l'accueil : part de 0,0000000001 VRSC et atteint 1 VRSC en 3 mois.
object Simulation {
    const val START = 0.0000000001                  // 1e-10 VRSC
    private const val TARGET = 1.0
    private const val NINETY_DAYS_MS = 90.0 * 24 * 3600 * 1000
    const val RATE_PER_MS = (TARGET - START) / NINETY_DAYS_MS

    // Valeur simulée à l'instant présent (vitesse constante + petits bonus de tapotage).
    fun value(ctx: Context): Double {
        val elapsed = (System.currentTimeMillis() - Prefs.simStart(ctx)).coerceAtLeast(0L)
        return START + elapsed * RATE_PER_MS + Prefs.simBonus(ctx)
    }
}
