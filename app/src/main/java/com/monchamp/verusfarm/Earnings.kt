package com.monchamp.verusfarm

import android.content.Context

// =============================================================================
//  Solde de l'utilisateur, basé sur ses CALCULS ACCEPTÉS par Luckpool.
//
//  Principe (honnête et prudent) :
//   - On mémorise le nombre cumulé de parts (calculs) acceptées par Luckpool pour
//     CET appareil, lues sur le tableau de bord du pool (par worker).
//   - On déduit des VRAIES récompenses de Luckpool un prix par part, qu'on applique
//     par tranche de 10 calculs acceptés.
//   - Ce prix est volontairement maintenu EN DESSOUS de la vraie récompense du pool
//     (facteur de sécurité), et le solde total ne dépasse jamais les gains réels de
//     l'adresse (payé + en attente + en maturation). Le solde affiché reste donc
//     toujours inférieur ou égal à ce que l'administrateur a réellement reçu.
// =============================================================================
object Earnings {

    // Le prix par part reste strictement sous la vraie récompense (marge de sécurité de 20 %).
    private const val SAFETY = 0.80

    // À appeler à chaque rafraîchissement des stats Luckpool (thread principal).
    fun update(ctx: Context, stats: PoolStats) {
        val myName = DeviceIdentity.workerName(ctx)
        val me = stats.workers.firstOrNull { it.name.equals(myName, ignoreCase = true) }
        val curShares = me?.shares ?: 0.0

        // Accumulation des parts acceptées (les parts de Luckpool se remettent à zéro à chaque tour)
        val last = Prefs.lastWorkerShares(ctx)
        val delta = if (curShares >= last) curShares - last else curShares
        if (delta > 0.0) Prefs.addAcceptedShares(ctx, delta)
        Prefs.setLastWorkerShares(ctx, curShares)

        // Prix par part dérivé des vraies récompenses en attente du pool
        val totalShares = stats.workers.sumOf { it.shares }
        val pending = (stats.balance + stats.immature).coerceAtLeast(0.0)
        if (totalShares > 0.0 && pending > 0.0) {
            val realPerShare = pending / totalShares          // vraie récompense par part
            val safePerShare = realPerShare * SAFETY          // toujours en dessous
            Prefs.setPricePer10(ctx, safePerShare * 10.0)     // prix pour 10 calculs acceptés
        }

        // Solde = (calculs acceptés) × prix par part, plafonné aux gains réels de l'adresse
        val perShare = Prefs.pricePer10(ctx) / 10.0
        val accepted = Prefs.acceptedShares(ctx)
        val lifetime = (stats.paid + stats.balance + stats.immature).coerceAtLeast(0.0)
        val estimate = (accepted * perShare).coerceAtMost(lifetime)
        Prefs.setAvailableVrsc(ctx, estimate.coerceAtLeast(0.0))
    }
}
