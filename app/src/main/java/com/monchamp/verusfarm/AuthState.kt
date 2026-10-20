package com.monchamp.verusfarm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

// État de connexion partagé entre l'écran d'auth et le retour OAuth (Google).
object AuthState {
    val loggedIn = MutableStateFlow(false)

    fun refresh(ctx: Context) {
        loggedIn.value = Prefs.loggedIn(ctx)
    }

    fun setLoggedIn(value: Boolean) {
        loggedIn.value = value
    }
}
