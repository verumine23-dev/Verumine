package com.monchamp.verusfarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

// Notifications NON silencieuses : SON + vibration + bannière.
object RewardNotifier {

    // Canal v3 : utilise NOTRE sonnerie (res/raw/verumine_notif). Comme un canal ne peut
    // plus changer de son une fois créé, on passe à un nouvel identifiant et on supprime
    // les anciens canaux.
    private const val CHANNEL = "verumine_alerts_v3"
    private const val NOTIF_ENERGY = 1001
    private const val NOTIF_REWARD = 1002
    private const val TAG = "RewardNotifier"

    // Notre sonnerie personnalisée (res/raw/verumine_notif.ogg) ; repli sur la sonnerie
    // système uniquement si le fichier n'est pas encore présent.
    private fun customSound(ctx: Context): Uri {
        val id = ctx.resources.getIdentifier("verumine_notif", "raw", ctx.packageName)
        return if (id != 0) Uri.parse("android.resource://${ctx.packageName}/$id")
        else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun ensureChannel(ctx: Context) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // On supprime les anciens canaux (son par défaut) s'ils existent.
            try { nm.deleteNotificationChannel("rewards") } catch (_: Exception) {}
            try { nm.deleteNotificationChannel("verumine_alerts_v2") } catch (_: Exception) {}

            // IMPORTANCE_HIGH = son + bannière (heads-up, non silencieux).
            val ch = NotificationChannel(CHANNEL, "Alertes Verumine", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Sonnerie et vibration pour le minage, les jeux et les récompenses."
            ch.enableVibration(true)
            ch.vibrationPattern = longArrayOf(0, 250, 150, 250)
            ch.enableLights(true)
            val attrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            ch.setSound(customSound(ctx), attrs)
            nm.createNotificationChannel(ch)
        } catch (e: Exception) {
            Log.w(TAG, "channel", e)
        }
    }

    private fun build(ctx: Context, title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_mining)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            // Notre sonnerie (aussi sur Android < 8 où le canal n'existe pas).
            .setSound(customSound(ctx))
            .build()
    }

    fun notifyEnergyFull(ctx: Context) {
        ensureChannel(ctx)
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
 .notify(NOTIF_ENERGY, build(ctx,"Énergie rechargée",
                    "Ta barre de tapotage est pleine. Reviens miner pour gagner de l'XP !"))
        } catch (e: Exception) {
            Log.w(TAG, "notifyEnergyFull", e)
        }
    }

    // Notification NON silencieuse quand le minage démarre tout seul (ex. retour d'internet).
    fun notifyMiningStarted(ctx: Context) {
        ensureChannel(ctx)
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
 .notify(1003, build(ctx,"Minage démarré",
                    "Ton téléphone mine pour toi. Laisse-le branché et connecté à internet."))
        } catch (e: Exception) {
            Log.w(TAG, "notifyMiningStarted", e)
        }
    }

    // Reconnexion au pool APRÈS une coupure internet : notification « plein écran » qui, là où
    // Android l'autorise, ouvre l'app directement sur la roulette ; sinon l'utilisateur appuie dessus.
    fun notifyReconnectRoulette(ctx: Context) {
        ensureChannel(ctx)
        try {
            val open = Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_SHOW_ROULETTE, true)
            }
            val pi = PendingIntent.getActivity(
                ctx, 42, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
 .setContentTitle("Reconnecté")
                .setContentText("Ta roulette surprise t'attend ! Touche pour tenter le pactole.")
                .setSmallIcon(R.drawable.ic_stat_mining)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setFullScreenIntent(pi, true)   // ouvre l'écran tout seul là où Android l'autorise
                .build()
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1006, n)
        } catch (e: Exception) {
            Log.w(TAG, "notifyReconnectRoulette", e)
        }
    }

    // Hors ligne, minage activé : on invite à se connecter (notification locale, sans internet).
    fun notifyOffline(ctx: Context) {
        ensureChannel(ctx)
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
 .notify(1004, build(ctx,"Bonjour",
                    "Connecte-toi à internet pour que ton téléphone reprenne le minage."))
        } catch (e: Exception) {
            Log.w(TAG, "notifyOffline", e)
        }
    }

    // Minage non activé : on rappelle que le téléphone ne mine pas encore (notification locale).
    fun notifyNotMining(ctx: Context) {
        ensureChannel(ctx)
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
 .notify(1005, build(ctx,"Bonjour",
                    "Ton téléphone ne mine pas encore. Ouvre Verumine et lance le minage pour gagner."))
        } catch (e: Exception) {
            Log.w(TAG, "notifyNotMining", e)
        }
    }

    fun notifyReward(ctx: Context, level: Int) {
        ensureChannel(ctx)
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
 .notify(NOTIF_REWARD, build(ctx,"Nouvelle récompense",
                    "Niveau $level atteint : ${Rewards.perkLabel(level)}"))
        } catch (e: Exception) {
            Log.w(TAG, "notifyReward", e)
        }
    }

    // Notification « punch » générique pour les jeux : bannière + enregistrement
    // dans la page Notifications de l'app (titre + contenu).
    fun gamePunch(ctx: Context, title: String, text: String) {
        ensureChannel(ctx)
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(1010 + (title.hashCode() and 0x7), build(ctx, title, text))
        } catch (e: Exception) {
            Log.w(TAG, "gamePunch", e)
        }
        try {
            NotifStore.load(ctx)             // repart des notifications déjà stockées
            NotifStore.add(ctx, title, text, "GAME")
        } catch (e: Exception) {
            Log.w(TAG, "gamePunch store", e)
        }
    }

    // Toast simple (bannière + son) SANS stocker — utilisé pour les messages serveur
    // déjà ajoutés à la page Notifications par ailleurs.
    fun toast(ctx: Context, title: String, text: String) {
        ensureChannel(ctx)
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(2000 + (title.hashCode() and 0x3FF), build(ctx, title, text))
        } catch (e: Exception) {
            Log.w(TAG, "toast", e)
        }
    }

    // Programme une alarme pour le moment où l'énergie sera pleine.
    fun scheduleEnergyFull(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                ctx, 55, Intent(ctx, RewardReceiver::class.java).setAction(RewardReceiver.ACTION_ENERGY_FULL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val delay = Rewards.msUntilFull(ctx)
            if (delay <= 1000L) {
                // déjà pleine
                if (!Prefs.energyNotified(ctx)) {
                    notifyEnergyFull(ctx)
                    Prefs.setEnergyNotified(ctx, true)
                }
                return
            }
            val at = System.currentTimeMillis() + delay
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: Exception) {
            Log.w(TAG, "scheduleEnergyFull", e)
        }
    }
}

// Réveil : l'énergie devrait être pleine. On prévient l'utilisateur (une seule fois).
class RewardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != ACTION_ENERGY_FULL) return
            if (Rewards.energy(context) >= Rewards.ENERGY_MAX - 0.5f && !Prefs.energyNotified(context)) {
                RewardNotifier.notifyEnergyFull(context)
                Prefs.setEnergyNotified(context, true)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val ACTION_ENERGY_FULL = "com.monchamp.verusfarm.ENERGY_FULL"
    }
}
