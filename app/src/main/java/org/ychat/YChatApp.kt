package org.ychat

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application YChat : point d'entree processus, init coroutines.
 *
 * - Expose un [CoroutineScope] application ([appScope]) pour les taches de fond
 *   (init JNI, boucle de reception future).
 * - Au demarrage : init RTS Haskell puis creation du controller reel via
 *   [ChatInit.ensureBackend] (chat_migrate_init upstream : DB dans filesDir
 *   + mot de passe), stocke dans [ChatRepository.controller]
 *   (SPEC docs/SPEC.md sections 1.2 et 5 : init unique par processus,
 *   jamais sur le main thread).
 * - Expose le [ChatRepository] partage (etat memoire + SharedPreferences).
 *
 * Declare dans AndroidManifest via android:name=".YChatApp" (modif manifest autorisee).
 */
class YChatApp : Application() {

    private val handler = CoroutineExceptionHandler { _, e ->
        Log.w(TAG, "coroutine application: ${e.message}", e)
    }

    /** Scope application : survit aux activities, cancelable a la mort du processus. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)

    /** Depot partage : profils, contacts par ID unique, messages, serveurs, surnoms. */
    val repository: ChatRepository by lazy { ChatRepository.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        // Init RTS Haskell + controller en tache de fond (bloquant JNI,
        // interdit sur le main thread). Echec tolere (controller 0L) : les
        // commandes supportees echouent explicitement, cf. ChatRepository.
        appScope.launch(Dispatchers.IO) {
            try {
                SimplexJni.ensureInitIo()
                Log.i(TAG, "SimplexJni init OK")
                val ctrl = ChatInit.ensureBackend(repository)
                Log.i(TAG, "ChatInit controller=$ctrl")
            } catch (e: Exception) {
                Log.w(TAG, "ChatInit echec (controller 0L) : ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "YChatApp"
    }
}
