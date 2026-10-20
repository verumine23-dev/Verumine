package com.monchamp.verusfarm

import android.content.Context
import android.os.Build
import android.provider.Settings

// Donne à chaque téléphone un nom unique pour que Luckpool les distingue,
// même si tous utilisent la même adresse VRSC.
object DeviceIdentity {

    // Nom automatique UNIQUE par installation : préfixe modèle + identifiant aléatoire persistant.
    // On n'utilise PAS ANDROID_ID (identique sur certains téléphones bon marché → deux appareils
    // finiraient avec le même nom et seraient fusionnés sur Luckpool). L'identifiant aléatoire est
    // créé une seule fois et stocké : il est donc différent sur chaque téléphone et jamais vide.
    fun autoName(ctx: Context): String {
        val model = Build.MODEL.filter { it.isLetterOrDigit() }.lowercase().take(5).ifEmpty { "vrs" }
        val uid = Prefs.randomSuffix(ctx).filter { it.isLetterOrDigit() }.lowercase()
        val name = (model + uid).filter { it.isLetterOrDigit() }.take(12)
        return name.ifEmpty { "miner$uid" }.take(12)
    }

    // Nom réellement utilisé : celui choisi, sinon le nom automatique. Toujours valide et non vide.
    fun workerName(ctx: Context): String {
        val custom = Prefs.customName(ctx).filter { it.isLetterOrDigit() }.lowercase()
        return custom.ifBlank { autoName(ctx) }.take(12).ifEmpty { "miner" }
    }

    // Identifiant envoyé à Luckpool : ADRESSE.nomDuWorker (le worker ne contient jamais de point)
    // Format confirmé par la doc LuckPool : « Add rig name to username as follows: USER.RIG ».
    fun poolUser(ctx: Context): String {
        val w = Prefs.wallet(ctx).trim()
        val worker = workerName(ctx)
        return "$w.$worker"
    }

    // Version affichable du diagnostic : l'adresse est masquée (confidentielle), le worker reste
    // visible pour que l'utilisateur vérifie le nom réellement envoyé à LuckPool.
    fun maskedPoolUser(ctx: Context): String {
        val w = Prefs.wallet(ctx).trim()
        val masked = if (w.length > 8) "${w.take(4)}…${w.takeLast(4)}" else "R…"
        return "$masked.${workerName(ctx)}"
    }

    fun abi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "inconnu"

    fun allAbis(): String = Build.SUPPORTED_ABIS.joinToString(", ").ifEmpty { "inconnu" }
}
