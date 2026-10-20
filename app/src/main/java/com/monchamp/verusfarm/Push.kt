package com.monchamp.verusfarm

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.runBlocking

/**
 * Gestion du jeton push (FCM) : récupération + envoi au serveur Supabase.
 *
 * Ne compile qu'une fois Firebase ajouté au projet (voir le guide FCM).
 * Appelle Push.register(ctx) après connexion (par ex. dans ServerSync.run).
 */
object Push {

    fun saveToken(ctx: Context, token: String) {
        if (token.isBlank()) return
        Thread {
            try { runBlocking { Supa.savePushToken(ctx, token) } } catch (_: Exception) {}
        }.start()
    }

    fun register(ctx: Context) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { t -> saveToken(ctx, t) }
        } catch (_: Exception) {}
    }
}
