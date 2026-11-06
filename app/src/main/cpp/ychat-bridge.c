/*
 * YChat JNI bridge — app/src/main/cpp/ychat-bridge.c
 *
 * Reference upstream : simplex-chat (stable, commit 79d91e7),
 *   apps/multiplatform/common/src/commonMain/cpp/android/simplex-api.c
 * Spec : docs/SPEC.md section 1.2 (declarations Core.kt, symboles chat_*,
 *   pattern initHS + gestion memoire des chaines retour).
 *
 * Contrepartie Kotlin : package/classe `org.ychat.SimplexJni` avec `external fun`
 *   initHS / chatMigrateInit / chatSendCmdRetry / chatRecvMsgWait /
 *   chatParseServer / chatParseUri
 *   => prefixe JNI exact `Java_org_ychat_SimplexJni_`.
 * Creation du controller (ChatInit.kt) via `chatMigrateInit`, copie du pattern
 * upstream `Java_chat_simplex_common_platform_CoreKt_chatMigrateInit`
 * (simple-api.c) : retour jobjectArray [jstring JSON DBMigrationResult, Long ctrl].
 *
 * NOTE chat_init_hs : ce symbole N'EXISTE PAS dans l'upstream
 *   (absent de simplex-api.c, de libsimplex.dll.def et du .so verifie par
 *   `nm -D` — voir .worker-report.md). initHS suit donc le pattern upstream
 *   reel : `hs_init_with_rtsopts("-A64m -H64m -xn")` + `setLineBuffering()`,
 *   sans appel a un quelconque `chat_init_hs`.
 *
 * Les 5 autres fonctions appellent les symboles Haskell verifies presents
 * dans libsimplex.so (nm -D, voir .worker-report.md) : chat_migrate_init,
 * chat_send_cmd_retry, chat_recv_msg_wait, chat_parse_server, chat_parse_uri
 * (tous deja dans libsimplex.dll.def, aucune entree .def a ajouter).
 *
 * Pattern memoire (upstream, repris a l'identique) : les `jstring` d'entree
 * sont converties par GetStringUTFChars / liberees par ReleaseStringUTFChars
 * apres l'appel ; la chaine retournee par Haskell est COPIEE vers une
 * `jstring` via NewStringUTF (le pointeur Haskell n'est pas libere cote C,
 * comme dans l'upstream — il appartient au RTS Haskell).
 */

#include <jni.h>

/* from the RTS (libsimplex.so — verifie present par nm -D) */
void hs_init_with_rtsopts(int *argc, char **argv[]);

/* from android-support (libsupport.so — verifie present par nm -D) */
void setLineBuffering(void);

/* from simplex-chat : opaque controller handle, cf. upstream */
typedef long *chat_ctrl;

extern char *chat_migrate_init(const char *path, const char *key, const char *confirm, chat_ctrl *ctrl);
extern char *chat_send_cmd_retry(chat_ctrl ctrl, const char *cmd, const int retryNum);
extern char *chat_recv_msg_wait(chat_ctrl ctrl, const int wait);
extern char *chat_parse_server(const char *str);
extern char *chat_parse_uri(const char *str, const int safe);

JNIEXPORT jobjectArray JNICALL
Java_org_ychat_SimplexJni_chatMigrateInit(JNIEnv *env, __unused jclass clazz,
        jstring dbPath, jstring dbKey, jstring confirm) {
    const char *_dbPath = (*env)->GetStringUTFChars(env, dbPath, JNI_FALSE);
    const char *_dbKey = (*env)->GetStringUTFChars(env, dbKey, JNI_FALSE);
    const char *_confirm = (*env)->GetStringUTFChars(env, confirm, JNI_FALSE);
    jlong _ctrl = (jlong) 0;
    jstring res = (*env)->NewStringUTF(env,
        chat_migrate_init(_dbPath, _dbKey, _confirm, &_ctrl));
    (*env)->ReleaseStringUTFChars(env, dbPath, _dbPath);
    (*env)->ReleaseStringUTFChars(env, dbKey, _dbKey);
    (*env)->ReleaseStringUTFChars(env, confirm, _confirm);

    /* [JSON DBMigrationResult, Long ctrl] — cf. upstream chatMigrateInit. */
    jobjectArray ret = (jobjectArray)(*env)->NewObjectArray(env, 2,
        (*env)->FindClass(env, "java/lang/Object"), NULL);
    (*env)->SetObjectArrayElement(env, ret, 0, res);
    (*env)->SetObjectArrayElement(env, ret, 1,
        (*env)->NewObject(env, (*env)->FindClass(env, "java/lang/Long"),
        (*env)->GetMethodID(env, (*env)->FindClass(env, "java/lang/Long"),
            "<init>", "(J)V"),
        _ctrl));
    return ret;
}

JNIEXPORT void JNICALL
Java_org_ychat_SimplexJni_initHS(__unused JNIEnv *env, __unused jclass clazz) {
    int argc = 5;
    char *argv[] = {
        "ychat",
        "+RTS", /* requires `hs_init_with_rtsopts` */
        "-A64m", /* chunk size for new allocations */
        "-H64m", /* initial heap size */
        "-xn", /* non-moving GC */
        NULL
    };
    char **pargv = argv;
    hs_init_with_rtsopts(&argc, &pargv);
    setLineBuffering();
}

JNIEXPORT jstring JNICALL
Java_org_ychat_SimplexJni_chatSendCmdRetry(JNIEnv *env, __unused jclass clazz,
        jlong controller, jstring msg, jint retryNum) {
    const char *_msg = (*env)->GetStringUTFChars(env, msg, JNI_FALSE);
    jstring res = (*env)->NewStringUTF(env,
        chat_send_cmd_retry((void *) controller, _msg, retryNum));
    (*env)->ReleaseStringUTFChars(env, msg, _msg);
    return res;
}

JNIEXPORT jstring JNICALL
Java_org_ychat_SimplexJni_chatRecvMsgWait(JNIEnv *env, __unused jclass clazz,
        jlong controller, jint wait) {
    return (*env)->NewStringUTF(env,
        chat_recv_msg_wait((void *) controller, wait));
}

JNIEXPORT jstring JNICALL
Java_org_ychat_SimplexJni_chatParseServer(JNIEnv *env, __unused jclass clazz,
        jstring str) {
    const char *_str = (*env)->GetStringUTFChars(env, str, JNI_FALSE);
    jstring res = (*env)->NewStringUTF(env, chat_parse_server(_str));
    (*env)->ReleaseStringUTFChars(env, str, _str);
    return res;
}

JNIEXPORT jstring JNICALL
Java_org_ychat_SimplexJni_chatParseUri(JNIEnv *env, __unused jclass clazz,
        jstring str, jint safe) {
    const char *_str = (*env)->GetStringUTFChars(env, str, JNI_FALSE);
    jstring res = (*env)->NewStringUTF(env, chat_parse_uri(_str, safe));
    (*env)->ReleaseStringUTFChars(env, str, _str);
    return res;
}
