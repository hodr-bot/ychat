package org.ychat

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Etat en memoire + persistance legere YChat.
 *
 * ECART SPEC (a consigner) : docs/SPEC.md n'a PAS de "section 4 (modeles JSON
 * chatCmd/chatResp)" — la section 4 s'intitule "Liste strip Telegram (incompatible
 * — a ne PAS reprendre)". Les modeles JSON utilises ici viennent donc de la
 * section 1.3 (protocole : commande texte -> JSON `responseType`/`details`,
 * types `CRActiveUser`/`CRApiChats`/`CRChatItems`/`CRNewChatItems`...) et restent
 * volontairement tolerants (optString/optJSONObject, jamais de get* strict).
 *
 * Contenu :
 * - profil local (nom), flag de setup ;
 * - contacts indexes par ID unique (Map), surnoms locaux en SharedPreferences ;
 * - messages par contact (List en memoire) ;
 * - serveurs via [ServerPresets] (load/save SharedPreferences, modifiables) ;
 * - adresse permanente locale (generee une fois, stockee).
 *
 * Backend : [SimplexJni.sendCmd] sur Dispatchers.IO (pattern SimpleXAPI.sendCmd).
 * NOTE : ce worktree expose un SimplexJni SANS `chatMigrateInit`/`chatCloseStore`
 * (sous-ensemble v0 : initHS + send/recv + parse). Faute de controller reel,
 * [controller] vaut 0L : les envois tentent le JNI puis replient en local-echo
 * (message persiste, marque non envoye). Brancher le vrai controller des que le
 * thread JNI exposera migrateInit (non-fait explicite, voir .worker-report.md).
 */
class ChatRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Handle backend. 0L = non connecte (repli local). Voir KDoc classe. */
    @Volatile
    var controller: Long = 0L

    private val lock = Any()
    private val contacts: MutableMap<String, ChatContact> = LinkedHashMap()
    private val messages: MutableMap<String, MutableList<ChatMessage>> = HashMap()
    private val idSeq = AtomicLong(1L)

    // ---------------------------------------------------------------- profil --

    /** Vrai si le setup (nom + serveurs defaut) a ete complete. */
    fun hasProfile(): Boolean = prefs.getBoolean(KEY_PROFILE_DONE, false)

    fun getProfileName(): String = prefs.getString(KEY_PROFILE_NAME, "") ?: ""

    /** Persiste le profil local + les serveurs (defauts ou customises). */
    fun saveProfile(displayName: String, servers: YChatServers = ServerPresets.defaults()) {
        ServerPresets.save(prefs, servers)
        prefs.edit()
            .putString(KEY_PROFILE_NAME, displayName)
            .putBoolean(KEY_PROFILE_DONE, true)
            .apply()
    }

    /** Commande texte de creation d'utilisateur (SPEC 1.3 : `/_create user {...}`). */
    fun buildCreateUserCmd(displayName: String): String {
        val safe = displayName.replace("\"", "'").take(120)
        return "/_create user {\"name\": \"$safe\"}"
    }

    // -------------------------------------------------------------- contacts --

    /** Tous les contacts (ordre d'insertion). */
    fun getContacts(): List<ChatContact> = synchronized(lock) { contacts.values.toList() }

    fun getContact(id: String): ChatContact? = synchronized(lock) { contacts[id] }

    /**
     * Ajoute un contact sous ID unique (jamais de doublon d'ID).
     * @return le contact existant si l'ID est deja connu, sinon le nouveau.
     */
    fun addContact(displayName: String, inviteLink: String? = null, address: String? = null): ChatContact =
        synchronized(lock) {
            val id = UUID.randomUUID().toString()
            val contact = ChatContact(
                id = id,
                displayName = displayName.ifBlank { "Contact" },
                inviteLink = inviteLink,
                address = address,
            )
            contacts[id] = contact
            messages[id] = mutableListOf()
            contact
        }

    /** Surnom local (persiste en SharedPreferences, n'affecte pas le backend). */
    fun setNickname(contactId: String, nickname: String) {
        prefs.edit().putString(KEY_NICK_PREFIX + contactId, nickname).apply()
        synchronized(lock) {
            contacts[contactId]?.let { contacts[contactId] = it.copy(displayName = nickname) }
        }
    }

    /** Nom affiche = surnom local si defini, sinon nom du contact. */
    fun displayNameOf(contact: ChatContact): String =
        prefs.getString(KEY_NICK_PREFIX + contact.id, null)?.takeIf { it.isNotBlank() }
            ?: contact.displayName

    fun removeContact(contactId: String): Boolean = synchronized(lock) {
        messages.remove(contactId)
        contacts.remove(contactId) != null
    }

    // -------------------------------------------------------------- messages --

    fun getMessages(contactId: String): List<ChatMessage> = synchronized(lock) {
        messages[contactId]?.toList() ?: emptyList()
    }

    fun lastMessagePreview(contactId: String): String = synchronized(lock) {
        messages[contactId]?.lastOrNull()?.text ?: ""
    }

    private fun storeLocal(contactId: String, text: String, outgoing: Boolean): ChatMessage {
        val msg = ChatMessage(
            id = idSeq.getAndIncrement(),
            contactId = contactId,
            text = text,
            outgoing = outgoing,
            timestamp = System.currentTimeMillis(),
        )
        synchronized(lock) { messages.getOrPut(contactId) { mutableListOf() }.add(msg) }
        return msg
    }

    /** Message entrant (push backend ou test local). */
    fun onIncomingMessage(contactId: String, text: String): ChatMessage =
        storeLocal(contactId, text, outgoing = false)

    /**
     * Envoi via SimplexJni (NOTE mission : "SimplexJni.send" ; l'API reelle expose
     * [SimplexJni.sendCmd] — ecart de nommage consigne dans .worker-report.md).
     * Le message est TOUJOURS persiste en local ; `sent=true` si le backend a
     * repondu sans erreur.
     */
    suspend fun sendMessage(contactId: String, text: String): SendResult =
        withContext(Dispatchers.IO) {
            val trimmed = text.trim()
            require(trimmed.isNotEmpty()) { "texte vide" }
            var sent = false
            var backendReply: String? = null
            val ctrl = controller
            if (ctrl != 0L) {
                try {
                    SimplexJni.ensureInit()
                    // Format minimal v0 (SPEC 1.3 : chaine de commande texte).
                    // A aligner sur CC.cmdString upstream quand le backend reel est branche.
                    val cmd = "/_send $contactId $trimmed"
                    backendReply = SimplexJni.sendCmd(ctrl, cmd)
                    sent = isOkResponse(backendReply)
                } catch (e: Exception) {
                    Log.w(TAG, "send via backend echec, repli local : ${e.message}")
                }
            } else {
                Log.i(TAG, "pas de controller (0L) : message garde en local")
            }
            val msg = storeLocal(contactId, trimmed, outgoing = true)
            SendResult(message = msg, sent = sent, backendReply = backendReply)
        }

    /** Reponse backend consideree OK si JSON sans marqueur d'erreur explicite. */
    private fun isOkResponse(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        return try {
            val o = JSONObject(json)
            val t = o.optString("responseType", "")
            !(t.contains("error", ignoreCase = true) || t.contains("err", ignoreCase = true) ||
                o.has("error") || o.has("err"))
        } catch (e: Exception) {
            false
        }
    }

    // -------------------------------------------------- adresse permanente --

    /** Adresse permanente locale (generee une fois, stable ensuite). */
    fun getOrCreateStaticAddress(): String {
        prefs.getString(KEY_STATIC_ADDRESS, null)?.let { return it }
        val addr = "ychat:" + UUID.randomUUID().toString()
        prefs.edit().putString(KEY_STATIC_ADDRESS, addr).apply()
        return addr
    }

    /** Lien d'invitation unique pour un contact. */
    fun buildInviteLink(contact: ChatContact): String =
        contact.inviteLink ?: "https://ychat/invite#${contact.id}"

    /**
     * Valide un lien colle via chatParseUri (safe=1), hors main thread.
     * Retourne le lien tel quel si le backend est indisponible (0L).
     */
    suspend fun parseInviteLink(link: String): String? = withContext(Dispatchers.IO) {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) return@withContext null
        if (controller == 0L) return@withContext trimmed
        try {
            SimplexJni.ensureInit()
            SimplexJni.chatParseUri(trimmed, 1)
        } catch (e: Exception) {
            Log.w(TAG, "parseUri echec : ${e.message}")
            null
        }
    }

    fun servers(): YChatServers = ServerPresets.load(prefs)

    fun saveServers(servers: YChatServers) = ServerPresets.save(prefs, servers)

    companion object {
        private const val TAG = "ChatRepository"
        private const val PREFS_NAME = "ychat_repo"
        private const val KEY_PROFILE_DONE = "profile_done"
        private const val KEY_PROFILE_NAME = "profile_name"
        private const val KEY_STATIC_ADDRESS = "static_address"
        private const val KEY_NICK_PREFIX = "nick_"

        @Volatile
        private var instance: ChatRepository? = null

        fun getInstance(context: Context): ChatRepository =
            instance ?: synchronized(this) {
                instance ?: ChatRepository(context).also { instance = it }
            }
    }
}

/** Contact : ID unique stable, nom, lien d'invitation, adresse. */
data class ChatContact(
    val id: String,
    val displayName: String,
    val inviteLink: String? = null,
    val address: String? = null,
)

/** Message : rattache a un contact, sens entrant/sortant, horodatage. */
data class ChatMessage(
    val id: Long,
    val contactId: String,
    val text: String,
    val outgoing: Boolean,
    val timestamp: Long,
)

/** Resultat d'envoi : message persiste + statut backend. */
data class SendResult(
    val message: ChatMessage,
    val sent: Boolean,
    val backendReply: String?,
)
