package com.monchamp.verusfarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Widget écran d'accueil (style Duolingo) : un petit encart qui change de
 * message CHAQUE JOUR pour rappeler à l'utilisateur d'ouvrir Verumine
 * (miner, jouer, récupérer ses gains). Un appui ouvre l'application.
 *
 * Le message du jour est choisi à partir du numéro de jour, donc il change
 * tout seul à minuit ; en plus, on programme un réveil quotidien pour
 * rafraîchir proprement l'affichage.
 */
class VerumineWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { renderOne(context, manager, it) }
        scheduleDailyRefresh(context)
    }

    override fun onEnabled(context: Context) {
        scheduleDailyRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            refreshAll(context)
            scheduleDailyRefresh(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.monchamp.verusfarm.WIDGET_REFRESH"
        private const val TAG = "VerumineWidget"

        // Messages tournants (changent chaque jour). Ton Duolingo : motivant + culpabilisant gentiment.
        private val TITLES = listOf(
            "Verumine t'attend",
            "Garde ta série !",
            "Des gains t'attendent",
            "Joue et gagne",
            "Surprise du jour",
            "1 minute suffit",
            "Ton solde du jour"
        )
        private val MESSAGES = listOf(
            "Ton téléphone n'a pas encore miné aujourd'hui. Ouvre l'appli !",
            "Ne casse pas ta série : reviens miner un peu aujourd'hui.",
            "Tes récompenses n'attendent que toi. Reviens vite !",
            "3 manches de jeu gratuites aujourd'hui. Tente ta chance !",
            "Une roulette surprise t'attend peut-être. Viens voir !",
            "Lance le minage en un clin d'œil et repars gagnant.",
            "Vérifie combien tu as gagné aujourd'hui sur Verumine."
        )
        // Mascotte selon l'humeur du message du jour (res/drawable/widget_mascot_*.png).
        private val MOODS = listOf(
            "widget_mascot_sad",    // n'a pas miné
            "widget_mascot_cheer",  // garde ta série
            "widget_mascot_happy",  // récompenses
            "widget_mascot_cheer",  // jeux
            "widget_mascot_happy",  // surprise
            "widget_mascot_cheer",  // lance le minage
            "widget_mascot_happy"   // solde du jour
        )

        private fun todayIndex(): Int {
            val day = System.currentTimeMillis() / 86_400_000L     // numéro de jour (UTC)
            val n = TITLES.size
            return ((day % n).toInt() + n) % n
        }

        fun refreshAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, VerumineWidget::class.java))
                ids.forEach { renderOne(context, manager, it) }
            } catch (e: Exception) {
                Log.w(TAG, "refreshAll", e)
            }
        }

        private fun renderOne(context: Context, manager: AppWidgetManager, id: Int) {
            try {
                val i = todayIndex()
                val views = RemoteViews(context.packageName, R.layout.widget_verumine)
                views.setTextViewText(R.id.widget_title, TITLES[i])
                views.setTextViewText(R.id.widget_message, MESSAGES[i])
                // Mascotte du jour (selon l'humeur) ; repli sur le logo si l'image manque.
                val moodId = context.resources.getIdentifier(MOODS[i], "drawable", context.packageName)
                views.setImageViewResource(R.id.widget_mascot, if (moodId != 0) moodId else R.mipmap.ic_launcher)

                // Appui n'importe où = ouvrir l'application.
                val open = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                val pi = PendingIntent.getActivity(
                    context, 71, open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_root, pi)

                manager.updateAppWidget(id, views)
            } catch (e: Exception) {
                Log.w(TAG, "renderOne", e)
            }
        }

        // Programme un réveil au prochain minuit local pour changer le message.
        private fun scheduleDailyRefresh(context: Context) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = PendingIntent.getBroadcast(
                    context, 72,
                    Intent(context, VerumineWidget::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val next = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 1)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
                } else {
                    am.set(AlarmManager.RTC, next, pi)
                }
            } catch (e: Exception) {
                Log.w(TAG, "scheduleDailyRefresh", e)
            }
        }

        /** Propose d'épingler le widget sur l'écran d'accueil (Android 8+). */
        fun requestPin(context: Context): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= 26) {
                    val awm = context.getSystemService(AppWidgetManager::class.java)
                    val provider = ComponentName(context, VerumineWidget::class.java)
                    if (awm != null && awm.isRequestPinAppWidgetSupported) {
                        awm.requestPinAppWidget(provider, null, null)
                        true
                    } else false
                } else false
            } catch (e: Exception) {
                Log.w(TAG, "requestPin", e); false
            }
        }
    }
}
