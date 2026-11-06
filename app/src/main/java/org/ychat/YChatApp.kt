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
 * - Initialise le RTS Haskell hors main thread via [SimplexJni.ensureInitIo]
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
        // Init RTS Haskell en tache de fond : bloquant, interdit sur le main thread.
        // Echec tolere (backend indisponible) : l'UI locale reste utilisable.
        appScope.launch(Dispatchers.IO) {
            try {
                SimplexJni.ensureInitIo()
                Log.i(TAG, "SimplexJni init OK")
            } catch (e: Exception) {
                Log.w(TAG, "SimplexJni init echec (mode local seul) : ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "YChatApp"
    }
}
