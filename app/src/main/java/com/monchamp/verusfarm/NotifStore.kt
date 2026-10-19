package com.monchamp.verusfarm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

// Une notification telle qu'affichée dans la page Notifications de l'app.
data class NotifItem(
    val id: Long,
    val title: String,
    val body: String,
    val tsSec: Long,
    val read: Boolean,
    val kind: String = "SYSTEM"
)

// Magasin LOCAL des notifications (persisté). Plus tard, il sera alimenté par
// le serveur (admin, parrainage…) ; pour l'instant il stocke les notifications
// locales et affiche le compteur (badge) de la cloche.
object NotifStore {
    private const val MAX = 100

    private val _items = MutableStateFlow<List<NotifItem>>(emptyList())
    val items: StateFlow<List<NotifItem>> = _items

    fun unreadCount(): Int = _items.value.count { !it.read }

    fun load(ctx: Context) {
        _items.value = parse(Prefs.notificationsJson(ctx))
    }

    // Ajoute une notification (en tête de liste) et la persiste.
    fun add(ctx: Context, title: String, body: String, kind: String = "SYSTEM") {
        val item = NotifItem(
            id = System.currentTimeMillis(),
            title = title, body = body,
            tsSec = System.currentTimeMillis() / 1000L,
            read = false, kind = kind
        )
        val next = (listOf(item) + _items.value).take(MAX)
        _items.value = next
        save(ctx, next)
    }

    // Ajoute une notification venue du SERVEUR (message admin), avec sa vraie date.
    fun addServer(ctx: Context, title: String, body: String, kind: String, tsSec: Long) {
        val item = NotifItem(
            id = System.currentTimeMillis() + (0..999).random(),
            title = title, body = body, tsSec = tsSec, read = false, kind = kind
        )
        val next = (listOf(item) + _items.value).take(MAX)
        _items.value = next
        save(ctx, next)
    }

    fun markAllRead(ctx: Context) {
        val next = _items.value.map { if (it.read) it else it.copy(read = true) }
        _items.value = next
        save(ctx, next)
    }

    fun delete(ctx: Context, id: Long) {
        val next = _items.value.filterNot { it.id == id }
        _items.value = next
        save(ctx, next)
    }

    fun clear(ctx: Context) {
        _items.value = emptyList()
        save(ctx, emptyList())
    }

    // ---------- JSON ----------
    private fun parse(json: String): List<NotifItem> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NotifItem(
                id = o.optLong("id"),
                title = o.optString("title"),
                body = o.optString("body"),
                tsSec = o.optLong("ts"),
                read = o.optBoolean("read", false),
                kind = o.optString("kind", "SYSTEM")
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(ctx: Context, list: List<NotifItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("body", it.body)
                    .put("ts", it.tsSec)
                    .put("read", it.read)
                    .put("kind", it.kind)
            )
        }
        Prefs.setNotificationsJson(ctx, arr.toString())
    }
}
