package org.ychat

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Creation du chat controller reel (fichier neuf, seul autorise).
 *
 * PROUVE upstream (clone scratch ychat-spec-probe/simplex-chat @79d91e7) :
 * - le chat_ctrl NE vient NI de `chat_new`, NI de `chat_init`, NI d'un
 *   `chat_controller` (ces 3 symboles sont ABSENTS de simplex-api.c, de
 *   libsimplex.dll.def, de Core.kt/SimpleXAPI.kt et du `nm -D` de libsimplex.so
 *   — voir .worker-report.md) ;
 * - il vient de `chat_migrate_init(path, key, confirm, &ctrl)` :
 *   `src/Simplex/Chat/Mobile.hs:110`
 *   (`foreign export ccall "chat_migrate_init"`),
 *   `apps/.../cpp/android/simplex-api.c:59`
 *   (`extern char *chat_migrate_init(...)`),
 *   `libsimplex.dll.def` (export `chat_migrate_init`),
 *   `Core.kt:25` (`external fun chatMigrateInit(dbPath, dbKey, confirm)`),
 *   `nm -D libsimplex.so` : `T chat_migrate_init` ;
 * - retour Kotlin upstream : `Array<Any> = [String JSON DBMigrationResult,
 *   Long ctrl]`, le ctrl n'etant lu (`migrated[1] as Long`, Core.kt:97-99)
 *   que si le JSON est `DBMigrationResult.OK`.
 *
 * Patterns repris a l'identique :
 * - `dbAbsolutePrefixPath` upstream Android (Files.android.kt) =
 *   `dataDir/files`, soit exactement `context.filesDir` : les fichiers crees
 *   sont donc `<filesDir>_chat.db` + `<filesDir>_agent.db` (SQLite.hs :
 *   `dbFilePrefix <> "_chat.db"`, `<> "_agent.db"`) ;
 * - double passe de migration (Core.kt `initChatController`) : d'abord
 *   confirm=`error`, puis si `errorMigration` relance avec `yesUp` ;
 * - mot de passe : 32 octets SecureRandom en Base64 (upstream
 *   `DatabaseUtils.randomDatabasePassword`), persiste ici en SharedPreferences
 *   privees (ECART : l'upstream le stocke en keystore via `ksDatabasePassword` ;
 *   durcissement keystore = non-fait explicite, voir .worker-report.md).
 *
 * Discriminateur JSON : `{"type":"ok", ...}` (et NON `"ok"` nu ni
 * `responseType`) — prouve par le client python du meme backend
 * (`packages/simplex-chat-python/src/simplex_chat/core.py` :
 * `parsed.get("type") == "ok"`).
 */
object ChatInit {

    /** Premier passage : echoue (sans migrer) si une migration est requise. */
    const val CONFIRM_ERROR = "error"

    /** Second passage : applique les montées de version en attente. */
    const val CONFIRM_YES_UP = "yesUp"

    private const val TAG = "ChatInit"
    private const val PREFS_NAME = "ychat_repo"
    private const val KEY_DB_KEY = "db_key"

    /** Garde l'init (suspend-friendly : `synchronized` est interdit autour de `withContext`). */
    private val mutex = Mutex()

    /** Prefixe DB = filesDir (cf. `dbAbsolutePrefixPath` upstream). */
    fun dbPrefix(context: Context): String = context.filesDir.absolutePath

    /**
     * Mot de passe DB : genere une fois (32 octets SecureRandom, Base64 NO_WRAP),
     * stable ensuite. Retourne "" si le stockage est indisponible (DB non chiffree,
     * comme l'upstream quand `dbKey == ""`).
     */
    fun getOrCreateDbKey(context: Context): String {
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DB_KEY, null)?.takeIf { it.isNotEmpty() }?.let { return it }
        return try {
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val key = Base64.encodeToString(raw, Base64.NO_WRAP)
            prefs.edit().putString(KEY_DB_KEY, key).apply()
            key
        } catch (e: Exception) {
            Log.w(TAG, "mot de passe DB non persistable, DB non chiffree : ${e.message}")
            ""
        }
    }

    /** `true` si le JSON de migration est `{"type":"ok", ...}`. */
    fun isMigrationOk(migrationJson: String?): Boolean {
        if (migrationJson.isNullOrBlank()) return false
        return try {
            JSONObject(migrationJson).optString("type", "") == "ok"
        } catch (e: Exception) {
            false
        }
    }

    /** `true` si le JSON demande une montee de version (`errorMigration`). */
    fun isUpgradeRequired(migrationJson: String?): Boolean {
        if (migrationJson.isNullOrBlank()) return false
        return try {
            JSONObject(migrationJson).optString("type", "") == "errorMigration"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Cree le controller reel si besoin (idempotent, thread-safe) et le stocke
     * dans [repository.controller]. A appeler hors main thread (bloquant JNI).
     *
     * @return le controller (> 0), ou 0L si la migration a echoue (le repli
     *   local du repository s'applique alors — cas d'erreur consigne en log).
     */
    suspend fun ensureBackend(repository: ChatRepository): Long =
        withContext(Dispatchers.IO) {
            val fast = repository.controller
            if (fast != 0L) return@withContext fast
            mutex.withLock {
                val cur = repository.controller
                if (cur != 0L) return@withContext cur
                try {
                    SimplexJni.ensureInit()
                } catch (e: Exception) {
                    Log.w(TAG, "initHS echec : ${e.message}")
                    return@withContext 0L
                }
                val ctx = repository.appCtx()
                val prefix = dbPrefix(ctx)
                val dbKey = getOrCreateDbKey(ctx)
                try {
                    var (migrationJson, ctrl) =
                        SimplexJni.migrateInit(prefix, dbKey, CONFIRM_ERROR)
                    if (!isMigrationOk(migrationJson) &&
                        isUpgradeRequired(migrationJson)
                    ) {
                        Log.i(TAG, "migration requise, second passage yesUp")
                        val rerun = SimplexJni.migrateInit(prefix, dbKey, CONFIRM_YES_UP)
                        migrationJson = rerun.first
                        ctrl = rerun.second
                    }
                    if (isMigrationOk(migrationJson) && ctrl != 0L) {
                        repository.controller = ctrl
                        Log.i(TAG, "controller cree (migration ok)")
                        ctrl
                    } else {
                        Log.w(TAG, "migration echouee : $migrationJson")
                        0L
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "chatMigrateInit echec : ${e.message}")
                    0L
                }
            }
        }
}
