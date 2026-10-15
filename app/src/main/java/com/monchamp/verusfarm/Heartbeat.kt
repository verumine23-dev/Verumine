package com.monchamp.verusfarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Battement de cœur : toutes les 30 minutes, Android réveille Verumine tout seul,
 * même hors connexion et même si l'application n'a jamais été rouverte.
 *
 * À chaque réveil :
 *   - on réarme le prochain battement (dans 30 min) ;
 *   - on se remet à l'écoute du retour d'internet (NetworkWake) ;
 *   - si le minage est activé : on relance le service de minage (qui, hors ligne,
 *     attend internet et repart tout seul dès qu'il revient) ; hors ligne, on
 *     rappelle gentiment à l'utilisateur de se connecter ;
 *   - si le minage n'est pas activé : on rappelle que le téléphone ne mine pas encore.
 *
 * On utilise une alarme « allow-while-idle » : elle se déclenche même en veille
 * profonde (Doze) et place brièvement l'application sur la liste blanche, ce qui
 * autorise le démarrage du service en arrière-plan.
 */
object Heartbeat {
    const val ACTION = "com.monchamp.verusfarm.HEARTBEAT"
    private const val REQ = 88
    private const val TAG = "Heartbeat"

    const val INTERVAL_MS = 30 * 60 * 1000L          // 30 minutes

    // Un rappel « connecte-toi » au plus toutes les 2 h ; « tu ne mines pas » toutes les 6 h.
    private const val OFFLINE_MIN_GAP_MS = 2 * 60 * 60 * 1000L
    private const val IDLE_MIN_GAP_MS = 6 * 60 * 60 * 1000L

    private fun pending(ctx: Context) = android.app.PendingIntent.getBroadcast(
        ctx, REQ,
        Intent(ctx, HeartbeatReceiver::class.java).setAction(ACTION),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun schedule(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = System.currentTimeMillis() + INTERVAL_MS
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx))
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pending(ctx))
            }
        } catch (e: Exception) {
            Log.w(TAG, "schedule", e)
        }
    }

    fun cancel(ctx: Context) {
        try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx))
        } catch (e: Exception) {
            Log.w(TAG, "cancel", e)
        }
    }

    fun hasInternet(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork
        val caps = if (n != null) cm.getNetworkCapabilities(n) else null
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) {
        false
    }

    // Décide quoi faire à chaque battement. Centralisé ici pour être réutilisable.
    fun beat(ctx: Context) {
        schedule(ctx)                 // réarme le prochain battement, quoi qu'il arrive
        NetworkWake.register(ctx)     // se remet à l'écoute du retour d'internet

        val now = System.currentTimeMillis()
        if (!Prefs.isEnabled(ctx)) {
            // Le minage n'est pas activé : rappel discret (au plus toutes les 6 h).
            if (now - Prefs.lastReminderAt(ctx, "idle") >= IDLE_MIN_GAP_MS) {
                RewardNotifier.notifyNotMining(ctx)
                Prefs.setLastReminderAt(ctx, "idle", now)
            }
            return
        }

        // Le minage est activé.
        val online = hasInternet(ctx)
        if (online) {
            // En ligne : si le service dort, on le relance ; le détecteur de connexion prend ensuite le relais.
            if (!MiningService.running) {
                try {
                    ContextCompat.startForegroundService(ctx, Intent(ctx, MiningService::class.java))
                } catch (e: Exception) {
                    Log.w(TAG, "relance service", e)
                }
            }
        } else {
            // Hors ligne : on démarre quand même le service (il attend internet et repart seul),
            // et on rappelle à l'utilisateur de se connecter (au plus toutes les 2 h).
            if (!MiningService.running) {
                try {
                    ContextCompat.startForegroundService(ctx, Intent(ctx, MiningService::class.java))
                } catch (e: Exception) {
                    Log.w(TAG, "relance service hors ligne", e)
                }
            }
            if (now - Prefs.lastReminderAt(ctx, "offline") >= OFFLINE_MIN_GAP_MS) {
                RewardNotifier.notifyOffline(ctx)
                Prefs.setLastReminderAt(ctx, "offline", now)
            }
        }
    }
}

// Réveil périodique déclenché par l'alarme (toutes les 30 min, même en veille / hors ligne).
class HeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Heartbeat.beat(context)
        } catch (e: Exception) {
            Log.w("Heartbeat", "onReceive", e)
            // On réarme quand même pour ne jamais casser la chaîne de battements.
            try { Heartbeat.schedule(context) } catch (_: Exception) { }
        }
    }
}
