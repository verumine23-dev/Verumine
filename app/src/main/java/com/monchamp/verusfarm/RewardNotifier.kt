package com.monchamp.verusfarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

// Notifications NON silencieuses : énergie de tapotage pleine, nouvelle récompense.
object RewardNotifier {

    private const val CHANNEL = "rewards"
    private const val NOTIF_ENERGY = 1001
    private const val NOTIF_REWARD = 1002
    private const val TAG = "RewardNotifier"

    fun ensureChannel(ctx: Context) {
        try {
            // IMPORTANCE_HIGH = son + bannière (non silencieux)
            val ch = NotificationChannel(CHANNEL, "Récompenses et tapotage", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Signale quand l'énergie est pleine ou qu'une récompense est disponible."
            ch.enableVibration(true)
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
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
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
    }

    fun notifyEnergyFull(ctx: Context) {
        ensureChannel(ctx)
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ENERGY, build(ctx, "Énergie rechargée ⚡",
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
                .notify(1003, build(ctx, "Minage démarré ⛏️",
                    "Ton téléphone mine pour toi. Laisse-le branché et connecté à internet."))
        } catch (e: Exception) {
            Log.w(TAG, "notifyMiningStarted", e)
        }
    }

    fun notifyReward(ctx: Context, level: Int) {
        ensureChannel(ctx)
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_REWARD, build(ctx, "Nouvelle récompense 🎁",
                    "Niveau $level atteint : ${Rewards.perkLabel(level)}"))
        } catch (e: Exception) {
            Log.w(TAG, "notifyReward", e)
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
