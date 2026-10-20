package com.monchamp.verusfarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

// « Réveil » : quand le téléphone redémarre, on relance le minage.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action
            val wanted = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.QUICKBOOT_POWERON"
            if (wanted) {
                // Toujours relancer le battement de cœur au démarrage du téléphone,
                // même si le minage est désactivé (pour les rappels hors ligne).
                Heartbeat.schedule(context)
                NetworkWake.register(context)
                if (Prefs.isEnabled(context)) {
                    ContextCompat.startForegroundService(context, Intent(context, MiningService::class.java))
                }
            }
        } catch (e: Exception) {
            Log.w("BootReceiver", "démarrage refusé", e)
        }
    }
}
