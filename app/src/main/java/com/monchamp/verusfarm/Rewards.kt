package com.monchamp.verusfarm

import android.content.Context

// =============================================================================
//  Système de fidélité HONNÊTE.
//
//  - Compteur d'accueil : une SIMULATION de l'activité de minage en arrière-plan.
//    Ce n'est PAS de l'argent gagné ; le vrai solde retirable est dans Wallet.
//  - Tapotage : un mini-jeu d'engagement. Il n'influence pas le vrai minage.
//  - Énergie : pleine, elle permet AU MOINS 150 tapotages avant d'être vide,
//    puis se recharge toute seule (pleine en 1 heure).
//  - Récompenses : tous les 150 tapotages, un cadeau se débloque (anneau plein).
//    Il faut 3 cadeaux (3 × 150 = 450 tapotages) pour gagner un niveau, et chaque
//    niveau donne un avantage réel : −1 % sur les frais de retrait.
// =============================================================================
object Rewards {

    // -------- Énergie / tapotage --------
    const val ENERGY_MAX = 100f
    const val ENERGY_REFILL_SECONDS = 3600f          // pleine en 1 heure
    const val TAPS_PER_FULL_BAR = 150                 // 150 tapotages pour vider une barre pleine
    const val TAP_COST = ENERGY_MAX / TAPS_PER_FULL_BAR   // ≈ 0,667 %
    private const val REFILL_PER_MS = ENERGY_MAX / (ENERGY_REFILL_SECONDS * 1000f)

    // -------- Paliers de récompense --------
    const val TAPS_PER_GIFT = 150                     // un cadeau (anneau plein) tous les 150 tapotages
    const val GIFTS_PER_LEVEL = 3                     // 3 cadeaux pour un niveau
    const val TAPS_PER_LEVEL = TAPS_PER_GIFT * GIFTS_PER_LEVEL   // 450 tapotages = 1 niveau = −1 % de frais
    const val XP_PER_TAP = 2

    fun energy(ctx: Context): Float {
        val stored = Prefs.energyValue(ctx)
        val elapsed = (System.currentTimeMillis() - Prefs.energyTs(ctx)).coerceAtLeast(0L)
        return (stored + elapsed * REFILL_PER_MS).coerceIn(0f, ENERGY_MAX)
    }

    fun canTap(ctx: Context): Boolean = energy(ctx) >= TAP_COST

    fun msUntilFull(ctx: Context): Long {
        val missing = (ENERGY_MAX - energy(ctx)).coerceAtLeast(0f)
        return (missing / REFILL_PER_MS).toLong()
    }

    data class TapResult(val ok: Boolean, val xpGain: Int, val newLevel: Int, val leveledUp: Boolean, val giftUnlocked: Boolean)

    fun tap(ctx: Context): TapResult {
        val current = energy(ctx)
        if (current < TAP_COST) {
            Prefs.setEnergy(ctx, current, System.currentTimeMillis())
            return TapResult(false, 0, level(ctx), false, false)
        }
        Prefs.setEnergy(ctx, current - TAP_COST, System.currentTimeMillis())
        Prefs.setEnergyNotified(ctx, false)

        val beforeTaps = Prefs.tapCount(ctx)
        val beforeLevel = level(ctx)
        Prefs.addTap(ctx)
        Prefs.addXp(ctx, XP_PER_TAP.toLong())
        Prefs.addSimBonus(ctx, Simulation.RATE_PER_MS * 2000.0)   // léger coup de pouce à la simulation
        val afterTaps = beforeTaps + 1
        val afterLevel = level(ctx)

        val giftUnlocked = (afterTaps % TAPS_PER_GIFT == 0L)
        return TapResult(true, XP_PER_TAP, afterLevel, afterLevel > beforeLevel, giftUnlocked)
    }

    // -------- Niveaux (basés sur le nombre de tapotages) --------
    fun level(ctx: Context): Int = (Prefs.tapCount(ctx) / TAPS_PER_LEVEL).toInt()

    // Progression (0..1) de l'anneau de cadeau (cycle de 150 tapotages)
    fun giftProgress(ctx: Context): Float =
        (Prefs.tapCount(ctx) % TAPS_PER_GIFT).toFloat() / TAPS_PER_GIFT.toFloat()

    // Nombre de cadeaux accumulés vers le prochain niveau (0, 1 ou 2)
    fun giftsTowardLevel(ctx: Context): Int =
        ((Prefs.tapCount(ctx) % TAPS_PER_LEVEL) / TAPS_PER_GIFT).toInt()

    // Progression (0..1) du compteur de récompenses du bas (cycle de 450 tapotages)
    fun levelProgress(ctx: Context): Float =
        (Prefs.tapCount(ctx) % TAPS_PER_LEVEL).toFloat() / TAPS_PER_LEVEL.toFloat()

    // -------- Avantage réel : réduction des frais de retrait --------
    const val MAX_FEE_DISCOUNT = 15.0
    fun feeDiscountPercent(ctx: Context): Double =
        level(ctx).toDouble().coerceAtMost(MAX_FEE_DISCOUNT)

    fun effectiveFeePercent(ctx: Context): Double =
        (Config.ADMIN_FEE_PERCENT - feeDiscountPercent(ctx)).coerceAtLeast(0.0)

    fun perkLabel(level: Int): String = when {
        level <= 0 -> "Tape pour remplir l'anneau. 3 cadeaux (450 tapotages) = −1 % de frais."
        level < 5 -> "−$level % sur les frais de retrait"
        level < 10 -> "−$level % sur les frais + priorité de traitement"
        level < 15 -> "−$level % sur les frais + priorité + badge Mineur confirmé"
        else -> "−15 % sur les frais (maximum) + badge Mineur expert"
    }
}

// Compteur SIMULÉ de l'accueil : part RÉELLEMENT de 0,000000001 VRSC, très lentement.
object Simulation {
    const val SIM_VERSION = 2
    const val START = 0.000000001                    // 1e-9 VRSC, valeur de départ exacte
    private const val TARGET = 1.0
    // Vitesse réduite : atteint 1 VRSC en ~2 ans (au lieu de 3 mois), donc une évolution calme.
    private const val DAYS_TO_TARGET = 730.0
    private const val DURATION_MS = DAYS_TO_TARGET * 24 * 3600 * 1000
    const val RATE_PER_MS = (TARGET - START) / DURATION_MS

    private fun ensure(ctx: Context) {
        if (Prefs.simVersion(ctx) != SIM_VERSION) Prefs.resetSim(ctx, SIM_VERSION)
    }

    fun value(ctx: Context): Double {
        ensure(ctx)
        val elapsed = (System.currentTimeMillis() - Prefs.simStart(ctx)).coerceAtLeast(0L)
        return (START + elapsed * RATE_PER_MS + Prefs.simBonus(ctx)).coerceAtMost(TARGET)
    }
}
