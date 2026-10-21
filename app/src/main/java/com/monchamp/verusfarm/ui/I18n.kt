package com.monchamp.verusfarm.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import com.monchamp.verusfarm.Prefs

/**
 * Internationalisation légère et RÉACTIVE.
 *  - `lang` est un état Compose : changer la langue recompose toute l'interface.
 *  - `t(clé)` renvoie la chaîne traduite (repli sur le français puis sur la clé).
 *  - Les écrans migrent progressivement vers t("…") ; le mécanisme est en place
 *    pour les 6 langues et il suffit d'ajouter des clés pour étendre.
 */
object I18n {
    data class Lang(val code: String, val name: String, val flag: String)

    // Ensemble « Essentiel (6) ».
    val languages = listOf(
        Lang("fr", "Français", "🇫🇷"),
        Lang("en", "English", "🇬🇧"),
        Lang("ar", "العربية", "🇸🇦"),
        Lang("pt", "Português", "🇵🇹"),
        Lang("es", "Español", "🇪🇸"),
        Lang("sw", "Kiswahili", "🇹🇿")
    )

    var lang by mutableStateOf("fr")

    fun init(ctx: Context) {
        val c = Prefs.language(ctx)
        lang = if (c.isNotBlank()) c else "fr"
    }

    fun set(ctx: Context, code: String) {
        Prefs.setLanguage(ctx, code)
        lang = code
    }

    fun isRtl(): Boolean = lang == "ar"

    fun t(key: String): String {
        val l = lang                            // dépendance d'état -> recomposition
        val row = STR[key] ?: return key
        return row[l] ?: row["fr"] ?: key
    }

    // code -> traduction. fr = référence.
    private val STR: Map<String, Map<String, String>> = mapOf(
        "app_tagline" to mapOf(
            "fr" to "Mine Verus sur ton téléphone", "en" to "Mine Verus on your phone",
            "ar" to "عدّن فيروس على هاتفك", "pt" to "Minere Verus no seu telefone",
            "es" to "Mina Verus en tu teléfono", "sw" to "Chimba Verus kwenye simu yako"),
        "lang_title" to mapOf(
            "fr" to "Choisis ta langue", "en" to "Choose your language",
            "ar" to "اختر لغتك", "pt" to "Escolha o seu idioma",
            "es" to "Elige tu idioma", "sw" to "Chagua lugha yako"),
        "lang_subtitle" to mapOf(
            "fr" to "Tu pourras la changer plus tard dans Plus.", "en" to "You can change it later in More.",
            "ar" to "يمكنك تغييرها لاحقًا من قائمة المزيد.", "pt" to "Pode alterá-lo mais tarde em Mais.",
            "es" to "Puedes cambiarlo más tarde en Más.", "sw" to "Unaweza kuibadilisha baadaye katika Zaidi."),
        "continue" to mapOf(
            "fr" to "Continuer", "en" to "Continue", "ar" to "متابعة",
            "pt" to "Continuar", "es" to "Continuar", "sw" to "Endelea"),
        "save" to mapOf(
            "fr" to "Enregistrer", "en" to "Save", "ar" to "حفظ",
            "pt" to "Guardar", "es" to "Guardar", "sw" to "Hifadhi"),
        "cancel" to mapOf(
            "fr" to "Annuler", "en" to "Cancel", "ar" to "إلغاء",
            "pt" to "Cancelar", "es" to "Cancelar", "sw" to "Ghairi"),
        "close" to mapOf(
            "fr" to "Fermer", "en" to "Close", "ar" to "إغلاق",
            "pt" to "Fechar", "es" to "Cerrar", "sw" to "Funga"),
        "back" to mapOf(
            "fr" to "Retour", "en" to "Back", "ar" to "رجوع",
            "pt" to "Voltar", "es" to "Atrás", "sw" to "Rudi"),
        "nav_home" to mapOf(
            "fr" to "Accueil", "en" to "Home", "ar" to "الرئيسية",
            "pt" to "Início", "es" to "Inicio", "sw" to "Mwanzo"),
        "nav_mine" to mapOf(
            "fr" to "Minage", "en" to "Mining", "ar" to "التعدين",
            "pt" to "Mineração", "es" to "Minería", "sw" to "Uchimbaji"),
        "nav_games" to mapOf(
            "fr" to "Jeux", "en" to "Games", "ar" to "الألعاب",
            "pt" to "Jogos", "es" to "Juegos", "sw" to "Michezo"),
        "nav_wallet" to mapOf(
            "fr" to "Wallet", "en" to "Wallet", "ar" to "المحفظة",
            "pt" to "Carteira", "es" to "Cartera", "sw" to "Pochi"),
        "nav_notifs" to mapOf(
            "fr" to "Notifications", "en" to "Notifications", "ar" to "الإشعارات",
            "pt" to "Notificações", "es" to "Notificaciones", "sw" to "Arifa"),
        "nav_profile" to mapOf(
            "fr" to "Profil", "en" to "Profile", "ar" to "الملف الشخصي",
            "pt" to "Perfil", "es" to "Perfil", "sw" to "Wasifu"),
        "nav_more" to mapOf(
            "fr" to "Plus", "en" to "More", "ar" to "المزيد",
            "pt" to "Mais", "es" to "Más", "sw" to "Zaidi"),
        "more_subtitle" to mapOf(
            "fr" to "Réglages et protections", "en" to "Settings and protections",
            "ar" to "الإعدادات والحماية", "pt" to "Definições e proteções",
            "es" to "Ajustes y protecciones", "sw" to "Mipangilio na ulinzi"),
        "more_language" to mapOf(
            "fr" to "Langue", "en" to "Language", "ar" to "اللغة",
            "pt" to "Idioma", "es" to "Idioma", "sw" to "Lugha"),
        "wallet_subtitle" to mapOf(
            "fr" to "Retire tes gains, partout dans le monde", "en" to "Withdraw your earnings, anywhere in the world",
            "ar" to "اسحب أرباحك من أي مكان في العالم", "pt" to "Levante os seus ganhos, em qualquer lugar do mundo",
            "es" to "Retira tus ganancias, en cualquier parte del mundo", "sw" to "Toa mapato yako, popote duniani"),
        "wallet_balance" to mapOf(
            "fr" to "Solde disponible", "en" to "Available balance", "ar" to "الرصيد المتاح",
            "pt" to "Saldo disponível", "es" to "Saldo disponible", "sw" to "Salio linalopatikana"),
        "wallet_accepted" to mapOf(
            "fr" to "Calculs acceptés", "en" to "Accepted shares", "ar" to "الحسابات المقبولة",
            "pt" to "Cálculos aceites", "es" to "Cálculos aceptados", "sw" to "Hesabu zilizokubaliwa"),
        "games_subtitle" to mapOf(
            "fr" to "Joue, gagne, retire. Connexion obligatoire.", "en" to "Play, win, withdraw. Connection required.",
            "ar" to "العب، اربح، اسحب. الاتصال مطلوب.", "pt" to "Jogue, ganhe, levante. Ligação obrigatória.",
            "es" to "Juega, gana, retira. Conexión obligatoria.", "sw" to "Cheza, shinda, toa. Muunganisho unahitajika.")
    )
}
