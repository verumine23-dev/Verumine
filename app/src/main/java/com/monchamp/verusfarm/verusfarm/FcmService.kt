package com.monchamp.verusfarm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Reçoit les notifications push (FCM) même quand l'application est fermée.
 * Chaque message est ajouté à la page Notifications + affiché en bannière sonore.
 *
 * Ne compile qu'une fois Firebase ajouté au projet (voir le guide FCM) :
 *    - google-services.json dans app/
 *    - plugin + dépendances firebase-messaging dans build.gradle(.kts)
 */
class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Push.saveToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Verumine"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        try {
            NotifStore.load(applicationContext)
            NotifStore.addServer(applicationContext, title, body, "ADMIN", System.currentTimeMillis() / 1000)
        } catch (_: Exception) {}
        RewardNotifier.toast(applicationContext, title, body)
    }
}
