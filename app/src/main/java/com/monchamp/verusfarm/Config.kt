package com.monchamp.verusfarm

object Config {

    // ======================================================================
    //  Adresse VRSC de RÉCOMPENSE (celle de l'administrateur).
    //  Elle est inscrite automatiquement à la compilation (variable VRSC_WALLET)
    //  et N'EST JAMAIS affichée à l'utilisateur : tous les téléphones minent
    //  vers cette adexte, et les gains sont reversés par l'administrateur via
    //  la page Wallet (Mobile Money ou crypto).
    // ======================================================================
    const val DEFAULT_WALLET = "RXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"

    // Serveurs Luckpool, du plus proche au plus lointain.
    val POOLS = listOf("eu.luckpool.net", "na.luckpool.net", "ap.luckpool.net")
    const val POOL_PORT = 3956
    const val POOL_PASSWORD = "x"

    // Programmes de minage cherchés dans jniLibs, dans cet ordre.
    val ENGINES = listOf("libccminer.so", "libccminer_compat.so")

    const val MAX_LOG_LINES = 60

    // ---------------------------------------------------------------- Retraits
    // Frais prélevés par l'administrateur sur chaque demande de retrait (%).
    const val ADMIN_FEE_PERCENT = 20.0

    // Montant minimum de retrait, en VRSC.
    const val MIN_WITHDRAW_VRSC = 0.5

    // ---------------------------------------------------------------- EmailJS
    //  L'administrateur crée un compte gratuit sur emailjs.com, puis remplit
    //  ces trois valeurs. Les demandes de retrait lui arrivent alors par e-mail.
    //  (Laisse tel quel pour désactiver l'envoi : l'application affiche alors
    //   un message clair invitant à configurer EmailJS.)
    const val EMAILJS_SERVICE_ID = "service_xxxxxxx"
    const val EMAILJS_TEMPLATE_ID = "template_xxxxxxx"
    const val EMAILJS_PUBLIC_KEY = "xxxxxxxxxxxxxxxxxx"

    fun emailJsReady(): Boolean =
        !EMAILJS_SERVICE_ID.contains("xxxx") &&
            !EMAILJS_TEMPLATE_ID.contains("xxxx") &&
            !EMAILJS_PUBLIC_KEY.contains("xxxx")

    // ------------------------------------------------------------- Conversion
    //  Valeurs de secours si l'application ne peut pas joindre le réseau.
    //  (Mises à jour en direct quand internet est disponible.)
    const val FALLBACK_VRSC_USD = 0.60      // prix approximatif d'1 VRSC en USD

    private val WALLET_REGEX = Regex("^R[1-9A-HJ-NP-Za-km-z]{33}$")

    fun isPlaceholder(wallet: String): Boolean =
        wallet.isBlank() || wallet.contains("XXXXXXXX")

    fun isUsableWallet(wallet: String): Boolean =
        !isPlaceholder(wallet) && WALLET_REGEX.matches(wallet)
}
