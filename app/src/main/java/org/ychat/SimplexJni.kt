package org.ychat

import kotlin.jvm.JvmStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Pont Kotlin vers ychat-bridge (app/src/main/cpp/ychat-bridge.c),
 * lui-meme adapte de l'upstream simplex-chat (stable 79d91e7)
 * apps/multiplatform/common/src/commonMain/cpp/android/simplex-api.c.
 *
 * SPEC : docs/SPEC.md section 1.2 (sous-ensemble v0 : initHS +
 * chatMigrateInit / chatSendCmdRetry / chatRecvMsgWait / chatParseServer /
 * chatParseUri). Creation du controller via [chatMigrateInit], copie du
 * pattern upstream Core.kt (chatMigrateInit(dbPath, dbKey, confirm) :
 * Array<Any> = [JSON DBMigrationResult, Long ctrl]).
 * Pattern d'appel upstream : Core.kt (declarations `external fun`) +
 * SimpleXAPI.kt sendCmd (withContext(Dispatchers.IO) + chatSendCmdRetry) /
 * recvMsg (chatRecvMsgWait + "" => null) + MESSAGE_TIMEOUT.
 *
 * Le choix `object` (et non fonctions top-level du fichier) est volontaire :
 * fonctions top-level donneraient la classe SimplexJniKt et le prefixe
 * Java_org_ychat_SimplexJniKt_, qui NE correspond PAS au pont C.
 * `object SimplexJni` donne la classe org.ychat.SimplexJni et le prefixe
 * Java_org_ychat_SimplexJni_ attendu par ychat-bridge.c.
 * Les `external fun` portent @JvmStatic pour generer des methodes natives
 * `static` (2e param JNI `jclass`), exactement comme le pont C les declare
 * (`JNIEnv*, jclass, ...`) et comme l'upstream (fonctions top-level Core.kt
 * => methodes statiques de CoreKt).
 */
object SimplexJni {
    init {
        System.loadLibrary("ychat-bridge")
    }

    /**
     * Creation/migration DB + controller (upstream Core.kt `chatMigrateInit`,
     * pont C `Java_org_ychat_SimplexJni_chatMigrateInit` ->
     * `chat_migrate_init(path, key, confirm, &ctrl)`).
     * @return Array<Any> = [String JSON DBMigrationResult, Long ctrl]
     *   (ctrl = 0L si la migration a echoue, comme l'upstream qui ne lit
     *   `migrated[1]` que si `res is DBMigrationResult.OK`).
     */
    @JvmStatic
    external fun chatMigrateInit(dbPath: String, dbKey: String, confirm: String): Array<Any>

    /** RTS Haskell : hs_init_with_rtsopts + setLineBuffering, sans argument. */
    @JvmStatic
    external fun initHS()

    /** C: jstring chat_send_cmd_retry(jlong controller, jstring msg, jint retryNum). */
    @JvmStatic
    external fun chatSendCmdRetry(controller: Long, msg: String, retryNum: Int): String

    /** C: jstring chat_recv_msg_wait(jlong controller, jint wait). */
    @JvmStatic
    external fun chatRecvMsgWait(controller: Long, timeout: Int): String

    /** C: jstring chat_parse_server(jstring str). */
    @JvmStatic
    external fun chatParseServer(str: String): String

    /** C: jstring chat_parse_uri(jstring str, jint safe). */
    @JvmStatic
    external fun chatParseUri(str: String, safe: Int): String

    /** Upstream SimpleXAPI.kt : MESSAGE_TIMEOUT = 300_000_000. */
    const val MESSAGE_TIMEOUT: Int = 300_000_000

    @Volatile
    private var hsInitialized: Boolean = false

    /**
     * Init RTS idempotente et thread-safe (double-checked locking).
     * Appeler une fois avant tout appel chat_*. Ne fait rien si deja init.
     * Ne jamais appeler sur le main thread (bloquant) : utiliser ensureInitIo().
     */
    fun ensureInit() {
        if (hsInitialized) return
        synchronized(this) {
            if (!hsInitialized) {
                initHS()
                hsInitialized = true
            }
        }
    }

    /** Variante suspendue sur Dispatchers.IO pour l'appel initial. */
    suspend fun ensureInitIo() {
        if (hsInitialized) return
        withContext(Dispatchers.IO) {
            ensureInit()
        }
    }

    /**
     * Creation du controller sur Dispatchers.IO (pattern upstream
     * Core.kt `initChatController` : ensureInit puis chatMigrateInit).
     * @return Pair(migrationJson, ctrl) ; ctrl = 0L si migration non-OK.
     */
    suspend fun migrateInit(
        dbPath: String,
        dbKey: String,
        confirm: String = ChatInit.CONFIRM_ERROR,
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        ensureInit()
        val migrated = chatMigrateInit(dbPath, dbKey, confirm)
        Pair(migrated[0] as String, migrated[1] as Long)
    }

    /**
     * Envoi synchrone d'une commande texte (pattern SimpleXAPI.sendCmd :
     * withContext(Dispatchers.IO) + chatSendCmdRetry -> reponse JSON brute).
     */
    suspend fun sendCmd(controller: Long, cmd: String, retryNum: Int = 0): String =
        withContext(Dispatchers.IO) {
            chatSendCmdRetry(controller, cmd, retryNum)
        }

    /**
     * Une attente push (pattern SimpleXAPI.recvMsg : chatRecvMsgWait +
     * "" => null, sinon JSON brut).
     */
    suspend fun recvMsg(controller: Long, timeout: Int = MESSAGE_TIMEOUT): String? =
        withContext(Dispatchers.IO) {
            val rStr = chatRecvMsgWait(controller, timeout)
            if (rStr.isEmpty()) null else rStr
        }

    /**
     * Boucle de reception cancelable : attend en boucle via chatRecvMsgWait
     * (Dispatchers.IO) et delivre chaque JSON non-vide a onMessage.
     * Sortie uniquement par annulation de la coroutine parente.
     */
    suspend fun CoroutineScope.receiveLoop(
        controller: Long,
        timeout: Int = MESSAGE_TIMEOUT,
        onMessage: suspend (String) -> Unit,
    ) {
        while (true) {
            ensureActive()
            val msg = recvMsg(controller, timeout)
            if (msg != null) onMessage(msg)
        }
    }
}
