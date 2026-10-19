package com.monchamp.verusfarm

import kotlinx.coroutines.runBlocking

/**
 * Synchronisation avec le serveur (Supabase) :
 *   - enregistre l'appareil (lien worker ↔ utilisateur, pour le tableau de bord admin) ;
 *   - rapporte le minage (calculs acceptés) pour les statistiques par période ;
 *   - récupère les messages de l'admin et les ajoute à la page Notifications
 *     (avec une bannière sonore pour les nouveaux).
 *
 * Appelé quand l'app est ouverte (coroutine) et à chaque battement de cœur (thread).
 */
object ServerSync {

    suspend fun run(ctx: android.content.Context) {
        if (Prefs.accessToken(ctx).isBlank()) return
        try { Push.register(ctx) } catch (_: Exception) {}        // jeton push FCM
        try { Supa.registerDevice(ctx) } catch (_: Exception) {}
        try { Supa.reportMining(ctx) } catch (_: Exception) {}
        try {
            val items = Supa.fetchNotifications(ctx)
            // Du plus ancien au plus récent pour un ordre naturel dans la liste.
            items.reversed().forEach { n ->
                if (!Prefs.serverNotifSeen(ctx, n.id)) {
                    NotifStore.load(ctx)
                    NotifStore.addServer(ctx, n.title, n.body, n.kind, n.tsSec)
                    Prefs.markServerNotifSeen(ctx, n.id)
                    RewardNotifier.toast(ctx, n.title, n.body)   // bannière sonore
                }
            }
        } catch (_: Exception) {}
    }

    // Variante bloquante pour les contextes sans coroutine (ex. battement de cœur).
    fun runBlockingSafe(ctx: android.content.Context) {
        try { runBlocking { run(ctx) } } catch (_: Exception) {}
    }
}
