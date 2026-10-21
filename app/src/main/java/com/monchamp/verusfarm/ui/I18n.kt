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

    // Traduit un texte français affiché (clé = le texte français lui-même).
    // Renvoie le français si aucune traduction -> on peut envelopper sans risque.
    fun tr(fr: String): String {
        val l = lang
        if (l == "fr") return fr
        val row = DT[fr] ?: return fr
        val i = ORDER.indexOf(l)
        return if (i >= 0) (row.getOrNull(i) ?: fr) else fr
    }

    private val ORDER = listOf("en", "ar", "pt", "es", "sw")
    // fr -> [en, ar, pt, es, sw]
    private val DT: Map<String, Array<String>> = mapOf(
        // Onglets / en-têtes
        "Minage" to arrayOf("Mining","التعدين","Mineração","Minería","Uchimbaji"),
        "Pleine puissance, en continu" to arrayOf("Full power, non-stop","بكامل الطاقة، بلا توقف","Potência máxima, sem parar","Máxima potencia, sin parar","Nguvu kamili, bila kusimama"),
        "Wallet" to arrayOf("Wallet","المحفظة","Carteira","Cartera","Pochi"),
        "Plus" to arrayOf("More","المزيد","Mais","Más","Zaidi"),
        "Réglages et protections" to arrayOf("Settings and protections","الإعدادات والحماية","Definições e proteções","Ajustes y protecciones","Mipangilio na ulinzi"),
        // Wallet
        "Retire tes gains, partout dans le monde" to arrayOf("Withdraw your earnings, anywhere in the world","اسحب أرباحك من أي مكان في العالم","Levante os seus ganhos, em qualquer lugar do mundo","Retira tus ganancias, en cualquier parte del mundo","Toa mapato yako, popote duniani"),
        "Solde disponible" to arrayOf("Available balance","الرصيد المتاح","Saldo disponível","Saldo disponible","Salio linalopatikana"),
        "Calculs acceptés" to arrayOf("Accepted shares","الحسابات المقبولة","Cálculos aceites","Cálculos aceptados","Hesabu zilizokubaliwa"),
        "Valeur de 10 calculs" to arrayOf("Value of 10 shares","قيمة 10 حسابات","Valor de 10 cálculos","Valor de 10 cálculos","Thamani ya hesabu 10"),
        "Demander un retrait" to arrayOf("Request a withdrawal","طلب سحب","Pedir um levantamento","Solicitar un retiro","Omba kutoa fedha"),
        "Mobile Money" to arrayOf("Mobile Money","Mobile Money","Mobile Money","Mobile Money","Mobile Money"),
        "Crypto" to arrayOf("Crypto","عملة رقمية","Cripto","Cripto","Crypto"),
        "Pays" to arrayOf("Country","البلد","País","País","Nchi"),
        "Choisir un pays" to arrayOf("Choose a country","اختر بلدًا","Escolher um país","Elegir un país","Chagua nchi"),
        "Cryptomonnaie" to arrayOf("Cryptocurrency","عملة رقمية","Criptomoeda","Criptomoneda","Sarafu ya crypto"),
        "Choisir" to arrayOf("Choose","اختر","Escolher","Elegir","Chagua"),
        "En VRSC" to arrayOf("In VRSC","بعملة VRSC","Em VRSC","En VRSC","Kwa VRSC"),
        "Prénom" to arrayOf("First name","الاسم الأول","Nome próprio","Nombre","Jina la kwanza"),
        "Nom" to arrayOf("Last name","اسم العائلة","Apelido","Apellido","Jina la ukoo"),
        "Adresse e-mail" to arrayOf("Email address","البريد الإلكتروني","Endereço de e-mail","Correo electrónico","Anwani ya barua pepe"),
        "Équivalent" to arrayOf("Equivalent","المعادل","Equivalente","Equivalente","Sawa na"),
        "Frais de l'administrateur" to arrayOf("Administrator fee","رسوم المسؤول","Taxa do administrador","Comisión del administrador","Ada ya msimamizi"),
        "Réduction fidélité (niveau)" to arrayOf("Loyalty discount (level)","خصم الولاء (المستوى)","Desconto de fidelidade (nível)","Descuento por fidelidad (nivel)","Punguzo la uaminifu (ngazi)"),
        "Frais appliqués" to arrayOf("Applied fee","الرسوم المطبقة","Taxa aplicada","Comisión aplicada","Ada iliyotumika"),
        "Tu reçois (net)" to arrayOf("You receive (net)","ستتلقى (صافي)","Recebes (líquido)","Recibes (neto)","Unapokea (halisi)"),
        "Envoyer la demande de retrait" to arrayOf("Send withdrawal request","إرسال طلب السحب","Enviar pedido de levantamento","Enviar solicitud de retiro","Tuma ombi la kutoa fedha"),
        "Envoi en cours…" to arrayOf("Sending…","جارٍ الإرسال…","A enviar…","Enviando…","Inatuma…"),
        "Comment sont versés les gains ?" to arrayOf("How are earnings paid?","كيف تُدفع الأرباح؟","Como são pagos os ganhos?","¿Cómo se pagan las ganancias?","Mapato hulipwaje?"),
        "Mes demandes" to arrayOf("My requests","طلباتي","Os meus pedidos","Mis solicitudes","Maombi yangu"),
        // Plus / réglages
        "Réglages et diagnostic" to arrayOf("Settings and diagnostics","الإعدادات والتشخيص","Definições e diagnóstico","Ajustes y diagnóstico","Mipangilio na uchunguzi"),
        "Langue" to arrayOf("Language","اللغة","Idioma","Idioma","Lugha"),
        "À propos" to arrayOf("About","حول","Acerca de","Acerca de","Kuhusu"),
        "Copier" to arrayOf("Copy","نسخ","Copiar","Copiar","Nakili"),
        "Se déconnecter" to arrayOf("Log out","تسجيل الخروج","Terminar sessão","Cerrar sesión","Toka"),
        "Déconnexion" to arrayOf("Log out","تسجيل الخروج","Terminar sessão","Cerrar sesión","Toka"),
        // Profil
        "Profil" to arrayOf("Profile","الملف الشخصي","Perfil","Perfil","Wasifu"),
        "Informations" to arrayOf("Information","المعلومات","Informações","Información","Taarifa"),
        "Email" to arrayOf("Email","البريد الإلكتروني","E-mail","Correo","Barua pepe"),
        "Devise" to arrayOf("Currency","العملة","Moeda","Moneda","Sarafu"),
        "Solde jeux" to arrayOf("Games balance","رصيد الألعاب","Saldo de jogos","Saldo de juegos","Salio la michezo"),
        "Administrateur" to arrayOf("Administrator","المسؤول","Administrador","Administrador","Msimamizi"),
        // Notifications
        "Notifications" to arrayOf("Notifications","الإشعارات","Notificações","Notificaciones","Arifa"),
        "Tes messages et alertes" to arrayOf("Your messages and alerts","رسائلك وتنبيهاتك","As tuas mensagens e alertas","Tus mensajes y alertas","Ujumbe na arifa zako"),
        "Aucune notification pour l'instant" to arrayOf("No notifications yet","لا إشعارات حتى الآن","Ainda sem notificações","Aún no hay notificaciones","Bado hakuna arifa"),
        "Tout effacer" to arrayOf("Clear all","مسح الكل","Limpar tudo","Borrar todo","Futa zote"),
        // Jeux
        "Espace jeux" to arrayOf("Games area","منطقة الألعاب","Área de jogos","Zona de juegos","Eneo la michezo"),
        "Règles" to arrayOf("Rules","القواعد","Regras","Reglas","Kanuni"),
        "Retirer mes gains" to arrayOf("Withdraw my earnings","سحب أرباحي","Levantar os meus ganhos","Retirar mis ganancias","Toa mapato yangu"),
        "Indice" to arrayOf("Hint","تلميح","Pista","Pista","Dokezo"),
        "Question" to arrayOf("Question","سؤال","Pergunta","Pregunta","Swali"),
        "Trouve le nombre suivant" to arrayOf("Find the next number","أوجد الرقم التالي","Encontra o número seguinte","Encuentra el siguiente número","Tafuta namba inayofuata"),
        "Manche gagnée !" to arrayOf("Round won!","ربحت الجولة!","Rodada ganha!","¡Ronda ganada!","Umeshinda raundi!"),
        "Manche perdue" to arrayOf("Round lost","خسرت الجولة","Rodada perdida","Ronda perdida","Umeshindwa raundi"),
        "Retour aux jeux" to arrayOf("Back to games","العودة إلى الألعاب","Voltar aos jogos","Volver a los juegos","Rudi kwenye michezo"),
        // Menu « Plus » (feuille) + divers
        "Accueil" to arrayOf("Home","الرئيسية","Início","Inicio","Mwanzo"),
        "Jeux" to arrayOf("Games","الألعاب","Jogos","Juegos","Michezo"),
        "Parrainage & filleuls" to arrayOf("Referrals","الإحالات والمُحالون","Indicações e indicados","Referidos","Rufaa na walioalikwa"),
        "Réglages & autorisations" to arrayOf("Settings & permissions","الإعدادات والأذونات","Definições e permissões","Ajustes y permisos","Mipangilio na ruhusa"),
        "Classement" to arrayOf("Leaderboard","الترتيب","Classificação","Clasificación","Orodha ya washindi"),
        "Missions" to arrayOf("Missions","المهام","Missões","Misiones","Misheni"),
        "Bientôt" to arrayOf("Soon","قريبًا","Em breve","Pronto","Hivi karibuni"),
        "Activé" to arrayOf("Enabled","مُفعّل","Ativado","Activado","Imewashwa"),
        "Mon champ de minage" to arrayOf("My mining field","حقل التعدين الخاص بي","O meu campo de mineração","Mi campo de minería","Shamba langu la uchimbaji")
    )

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
