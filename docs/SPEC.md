# YChat — SPEC v0 (sondage lecture seule)

Date : 2026-11-06. Source : clones superficiels (`--depth 1 --filter=blob:none --sparse`)
dans `/home/hermes/.hermes/cache/scratch/ychat-spec-probe/` — **aucun clone dans le worktree,
aucune modification des repos sondés, aucun commit.**
Référence stack : `docs/STACK.md` (Kotlin Android, AGP 8.5.2, JDK 17, minSdk 26 ;
`libsimplex` précompilé réutilisé via JNI ; serveurs par défaut SimpleX modifiables par
l'utilisateur ; zéro self-hosted en v0).

- SimpleX : `https://github.com/simplex-chat/simplex-chat`, branche `stable`,
  commit `79d91e7` (« desktop: do not delete the whole install folder… (#7789) »).
- SimpleX MQ (dépendance, presets XFTP) : `https://github.com/simplex-chat/simplexmq`.
- Telegram-X : `https://github.com/TGX-Android/Telegram-X` (l'org `Telegram-X` seule n'existe
  plus — `ls-remote` : « Repository not found » ; le repo vivant est `TGX-Android/Telegram-X`),
  commit `08ee6c2` « Version bump to `1825` » (2026-11-06), `version.app=1825`.

---

## 1. Findings SimpleX (`simplex-chat`)

### 1.1 Structure app Android — chemins

```
apps/multiplatform/
  android/                  # coquille Android : 8 fichiers Kotlin seulement
    src/main/java/chat/simplex/app/
      MainActivity.kt  SimplexApp.kt  SimplexService.kt  CallService.kt
      MessagesFetcherWorker.kt  views/call/CallActivity.kt
      model/NtfManager.android.kt  views/helpers/Util.kt
    src/main/AndroidManifest.xml
    build.gradle.kts            # applicationId chat.simplex.app, minSdk 26, targetSdk 35,
                                # flavors google (Play Billing) / foss, CMake externalNativeBuild
  common/src/
    commonMain/kotlin/chat/simplex/common/
      platform/Core.kt          # déclarations JNI `external fun` (voir 1.2)
      model/SimpleXAPI.kt       # sendCmd / recvMsg / CC.cmdString (voir 1.3)
      views/...                 # 100 % de l'UI (Compose multiplateforme)
    commonMain/cpp/android/
      simplex-api.c             # pont JNI C (voir 1.2)
      CMakeLists.txt            # build app-lib + import libsimplex/libsupport précompilés
    androidMain/kotlin/chat/simplex/common/platform/
      AppCommon.android.kt      # initHaskell() : loadLibrary + pipeStdOutToSocket + initHS
      ...                       # ~40 fichiers d'implémentation plateforme (notifs, fichiers, etc.)
  desktop/  ios/  ...
src/Simplex/Chat/
  Controller.hs                 # ADTs ChatCommand (l.327) / ChatResponse (l.765) — le protocole
  Chat.hs                       # câblage presetServers (operators, useSMP/useXFTP)
  Operators.hs                  # types PresetOperator / opérateurs
  Operators/Presets.hs          # liste des serveurs SMP par défaut (voir 1.4)
  Library/Commands.hs           # _defaultNtfServers (l.147)
scripts/android/
  download-libs.sh              # télécharge libsimplex.so + libsupport.so depuis le CI
  lib.txt                       # URLs CI : https://ci.zw3rk.com/build/.../pkg-aarch64-android-libsimplex.zip
```

Taille côté client : 765 fichiers sous `apps/multiplatform/common/src`.

### 1.2 libsimplex JNI — noms de fonctions `chat_*`, protocole

Déclarations Kotlin (`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/platform/Core.kt`,
package `chat.simplex.common.platform`, fichier `Core.kt` → préfixe JNI
`Java_chat_simplex_common_platform_CoreKt_`) :

```kotlin
external fun initHS()
external fun pipeStdOutToSocket(socketName: String): Int
external fun chatMigrateInit(dbPath: String, dbKey: String, confirm: String): Array<Any>
external fun chatCloseStore(ctrl: ChatCtrl): String
external fun chatSendCmdRetry(ctrl: ChatCtrl, msg: String, retryNum: Int): String
external fun chatSendRemoteCmdRetry(ctrl: ChatCtrl, rhId: Int, msg: String, retryNum: Int): String
external fun chatRecvMsg(ctrl: ChatCtrl): String
external fun chatRecvMsgWait(ctrl: ChatCtrl, timeout: Int): String
external fun chatParseMarkdown(str: String): String
external fun chatParseServer(str: String): String
external fun chatParseUri(str: String, safe: Int): String
external fun chatPasswordHash(pwd: String, salt: String): String
external fun chatValidName(name: String): String
external fun chatJsonLength(str: String): Int
external fun chatWriteFile(ctrl: ChatCtrl, path: String, buffer: ByteBuffer): String
external fun chatReadFile(path: String, key: String, nonce: String): Array<Any>
external fun chatEncryptFile(ctrl: ChatCtrl, fromPath: String, toPath: String): String
external fun chatDecryptFile(fromPath: String, key: String, nonce: String, toPath: String): String
```

Pont C (`apps/multiplatform/common/src/commonMain/cpp/android/simplex-api.c`) : chaque
`Java_chat_simplex_common_platform_CoreKt_<nom>` appelle le symbole Haskell `chat_<nom>`
(`chat_migrate_init`, `chat_close_store`, `chat_send_cmd_retry`, `chat_send_remote_cmd_retry`,
`chat_recv_msg`, `chat_recv_msg_wait`, `chat_parse_markdown`, `chat_parse_server`,
`chat_parse_uri`, `chat_password_hash`, `chat_valid_name`, `chat_json_length`,
`chat_write_file`, `chat_read_file`, `chat_encrypt_file`, `chat_decrypt_file`),
plus `initHS()` (RTS : `hs_init_with_rtsopts`, `-A64m -H64m -xn`, `setLineBuffering`) et
`pipeStdOutToSocket` (redirection stdout/stderr Haskell vers logcat via LocalServerSocket).

Liste canonique des symboles exportés (`libsimplex.dll.def` à la racine, à maintenir aussi
dans `flake.nix` quand on ajoute une fonction — commentaire en tête de `simplex-api.c`) :
`chat_migrate_init`, `chat_close_store`, `chat_send_cmd[_retry]`,
`chat_send_remote_cmd[_retry]`, `chat_recv_msg[_wait]`, `chat_parse_markdown`,
`chat_parse_server`, `chat_parse_uri`, `chat_password_hash`, `chat_valid_name`,
`chat_json_length`, `chat_badge_keygen`, `chat_badge_issue`, `chat_encrypt_media`,
`chat_decrypt_media`, `chat_write_file`, `chat_read_file`, `chat_encrypt_file`,
`chat_decrypt_file`.

`CMakeLists.txt` Android (extrait clé n° 2, voir §7) : `add_library(app-lib SHARED
simplex-api.c)`, import de **précompilés** `libs/${ANDROID_ABI}/libsimplex.so` et
`libsupport.so`, link `target_link_libraries(app-lib simplex support ${log-lib})`,
`max-page-size=16384`, strip `.comment`. Côté Kotlin : `System.loadLibrary("app-lib")`
puis `pipeStdOutToSocket(packageName)` puis `initHS()` (`AppCommon.android.kt`,
fonction `initHaskell`).

### 1.3 Protocole JSON (commandes → réponses)

- Requête : **chaîne de commande texte** construite côté Kotlin
  (`SimpleXAPI.kt:3955`, `val cmdString: String get() = when (this)`), ex.
  `/u`, `/users`, `/_create user {...json...}`, `/_start main=...`, `/_delete user …`.
  Envoi synchrone : `chatSendCmdRetry(ctrl, c, retryNum)` → **chaîne JSON réponse**
  décodée en `API` scellé (`SimpleXAPI.kt:841`, `sendCmd`, sur `Dispatchers.IO`).
- Push temps réel : boucle `chatRecvMsgWait(ctrl, MESSAGE_TIMEOUT)` (`SimpleXAPI.kt:866,
  recvMsg`) → JSON décodé (`responseType`, `details`).
- Types miroirs Haskell : `data ChatCommand` (`src/Simplex/Chat/Controller.hs:327`,
  ~100 constructeurs : `ShowActiveUser`, `CreateActiveUser`, `StartChat`, `APIGetChats`,
  `APISendMessages`, `APIUpdateChatItem`, …) et `data ChatResponse`
  (`Controller.hs:765` : `CRActiveUser`, `CRApiChats`, `CRChatItems`, `CRNewChatItems`,
  `CRServerTestResult`, `CRUserServers`, `CRNetworkConfig`, …).
- Fichiers : `chatWriteFile` (ByteBuffer direct), `chatReadFile`, chiffrement
  `chatEncryptFile`/`chatDecryptFile`.

### 1.4 Disponibilité `.so` précompilés (releases / APK)

- `apps/multiplatform/.gitignore` **exclut** `common/src/commonMain/cpp/android/libs/` :
  les `.so` **ne sont pas** dans le repo. Ils sont produits par le CI Haskell
  cross-compilé et récupérés par `scripts/android/download-libs.sh <job_repo> [arch]`
  (`aarch64`→`arm64-v8a`, `armv7a`→`armeabi-v7a`) — `scripts/android/lib.txt` épingle des
  builds `ci.zw3rk.com`. Pas d'artefact `.so` isolé dans les GitHub Releases.
- En revanche les APK officiels **contiennent** les `.so` : release `v7.0.3`
  (dernière via API GitHub), `simplex-aarch64.apk` (87 519 748 o) :
  `lib/arm64-v8a/libsimplex.so` (**197 610 624 o** non compressé),
  `libsupport.so` (14 650 736 o), `libapp-lib.so` (10 096 o).
  → Stratégie v0 : **extraire la paire `.so` par ABI depuis l'APK officiel**
  (vérifié : `aapt2 dump badging`, `strings` sur `classes*.dex`, `apksigner verify`,
  copie à `~/.hermes/cache/scratch/ychat-apk/` AVANT land — cf. AGENTS.md),
  version épinglée, ou rejouer `download-libs.sh` contre le CI.

### 1.5 Serveurs par défaut SMP/XFTP — chemins + valeurs

Source de vérité **backend** (seed en base à la première init, `src/Simplex/Chat.hs:84-110`) :

| Fichier | Valeur |
|---|---|
| `src/Simplex/Chat/Operators/Presets.hs` → `enabledSimplexChatSMPServers` | 11 SMP SimpleX activés : `smp8`→`smp12`, `smp14`→`smp19` (`.simplex.im`, chaque entrée `smp://<fingerprint>@host,<onion>` ; **pas de `smp13`**) ; `useSMP = 4` |
| même fichier → `disabledSimplexChatSMPServers` | `smp4, smp5, smp6.simplex.im` (désactivés, gardés pour compat des short-links) |
| même fichier → `fluxSMPServers_` / `fluxXFTPServers` | opérateur Flux : `smp1..smp6.simplexonflux.com` (`useSMP = 3`, rôles SMP restreints `storage=false`) ; `xftp1..xftp6.simplexonflux.com` (`useXFTP = 3`) |
| `src/Simplex/Chat/Operators/Presets.hs` → `simplexChatRelays` | 3 relais `https://smp5,smp6,smp4.simplex.im/r#…` ; `useChatRelays = 2` |
| `simplexmq:src/Simplex/FileTransfer/Client/Presets.hs` → `defaultXFTPServers` | 6 XFTP SimpleX : `xftp1..xftp6.simplex.im` (`xftp://<fp>@host,<onion>`) ; `useXFTP = 3` (câblé dans `Chat.hs:90`) |
| `src/Simplex/Chat/Library/Commands.hs:147` → `_defaultNtfServers` | `ntf3, ntf4.simplex.im` (`ntf2` commenté) |

Exemple d'entrée (format `ProtoServerWithAuth`, fingerprint + hôte(s) dont `.onion`) :
`smp://0YuTwO05YJWS8rkjn9eLJDjQhFKvIYd8d4xG8X1blIU=@smp8.simplex.im,beccx4…`,
`xftp://da1aH3nOT-9G8lV7bWamhxpDYdJ1xmW7j3JpGaDR5Ug=@xftp1.simplex.im,bliszt…`
(listes complètes : `git show HEAD:src/Simplex/Chat/Operators/Presets.hs` et
`simplexmq:src/Simplex/FileTransfer/Client/Presets.hs`).
Règle amont à respecter : un serveur retiré des presets **doit** rester dans
`allPresetServers` (compat des short-links) ; côté UI les écrans existent déjà :
`views/usersettings/networkAndServers/{ProtocolServersView,NewServerView,OperatorView}.kt`.

---

## 2. Findings Telegram-X (`TGX-Android/Telegram-X`)

### 2.1 Taille

- 2 350 fichiers suivis (hors contenu des ~20 submodules natifs non initialisés).
- `app/src/main/java` : ~20 Mo, **1 076 fichiers Java** ; `+ kotlin/` ≈ **1 099 fichiers**.
- `.../telegram/` (couche TDLib) : 112 fichiers, 2,2 Mo ; `Tdlib.java` seul : **12 404 lignes**.
- Coût de build amont (README) : clone `--recursive` + LFS, **≥ 7,32 Go** disque libre,
  **≥ 8 Go** RAM, JDK 25 + Rust (`version.rust`) + NDK 30 (`version.ndk_primary`),
  `tdlib` + `tgcalls` + `ffmpeg/webrtc/opus/…` en submodules. Incompatible avec
  l'env YChat (JDK 17, pas de NDK lourd, builds sandboxés).

### 2.2 Points de couplage TDLib / MTProto (impossibles à « débrancher » proprement)

- `tdlib/` est un **submodule** (`TGX-Android/tdlib`, branche `main`) : tout le protocole
  MTProto + chiffrement + stockage TDLib vit en natif (`app/jni`, `version.jni=240.0.0`).
- **449 fichiers** importent `org.drinkless.tdlib` / `TdApi` ; **16 467 occurrences** de
  `TdApi.` ; **407 fichiers hors** `telegram/` référencent `org.thunderdog.challegram.telegram`.
- `Tdlib.java` (12 k lignes) = façade d'envoi/réception (`client.send`, ~103 sites
  `.send(new TdApi.…)`), cache, notifications, options ; `TDLib.java` (146 lignes) n'est
  qu'un helper de logs. Le modèle de données UI (`TGMessage`, `data/TD`, `emoji/`, …)
  consomme directement les types `TdApi.*`.
- Auth = numéro de téléphone + code via MTProto ; `telegram.api_id` / `telegram.api_hash`
  injectés au build (`local.properties` → `BuildConfig.TELEGRAM_API_ID/HASH`,
  sample `94575/a3406de8…`) — sans ces secrets Telegram, rien ne se connecte.
- Push : FCM/HMS couplés aux serveurs Telegram (`TdlibNotification*`, Play Integrity,
  Recaptcha, `play-services base/maps/location` dans `app/build.gradle.kts`).

### 2.3 Points de renommage package (si fork)

`app/build.gradle.kts:387` `namespace = "org.thunderdog.challegram"` ; `applicationId`
depuis config `app.id` (défaut sample `org.example.tgx`) ; `AndroidManifest.xml`
(`BaseApplication`, `sync.StubProvider`, `sync.SyncService`, authorities
`${applicationId}.sync.*`) ; `resValue account_type / content_authority` ; ~20
submodules et thèmes générés (`GenerateThemesTask`) ; LFS obligatoire
(`validateGitSetup`). Renommer = trivial mécaniquement, mais ne change rien au §2.2.

### 2.4 Verdict fork-vs-UI-neuve + coût

**Verdict : UI neuve (Compose), zéro code Telegram-X réutilisé — même pas en fork.**
Justification : le « fork » garderait ~1 100 fichiers dont 100 % de la couche données
est Telegram (TDLib natif à recompiler, MTProto, auth par numéro, secrets d'API,
FCM/Play) ; « stripper Telegram » = réécrire le réseau, le stockage, l'auth, les
notifications et la quasi-totalité des 407 fichiers UI couplés à `telegram.*` —
c.-à-d. réécrire l'app. Coût estimé : **fork+strip ≈ 4–8 semaines** pour un résultat
instable et sous licence **GPLv3** contraignante, contre **UI neuve ≈ 2–4 semaines**
pour un MVP (liste chats / conversation / onboarding / réglages serveurs) branché sur
l'adaptateur JNI §5, sans TDLib/MTProto/FCM. Seule réutilisation admise : lecture
d'inspiration (patterns d'écrans), pas de copie (incompatibilité de fait + GPLv3).

---

## 3. Architecture YChat proposée (v0)

```
app-android (Kotlin, AGP 8.5.2 / JDK 17, minSdk 26, targetSdk 34)
├── ui (Compose, Material3) — écrans : onboarding/profil, liste chats,
│   conversation, pièces jointes, réglages (langue, serveurs, chiffrement BDD)
├── domain — modèles + UseCases (coroutines/Flow), zéro dépendance TDLib/MTProto
├── data/simplex
│   ├── ChatBackend (interface) : sendCommand(cmd: String): JSONObject,
│   │   receiveLoop(): Flow<JSONObject>, migrateInit / closeStore / file APIs
│   ├── ServerConfig : presets seedés §1.5, CRUD utilisateur, test via
│   │   chatParseServer + CRServerTestResult (écran type ProtocolServersView)
│   └── db locale : préfs + cache (pas de Room obligatoire v0 ; BDD SimpleX
│       chiffrée gérée par libsimplex via chatMigrateInit)
└── jni (module :app-lib)
    ├── simplex-api-ychat.c  — copie adaptée de simplex-api.c, préfixe JNI
    │   renommé vers notre classe Kotlin (ex. Java_net_ychat_core_SimplexJniKt_…)
    └── src/main/jniLibs/<abi>/libsimplex.so + libsupport.so  (extraits APK officiel
        épinglé, vérifiés — voir §1.4 ; jamais compilés depuis Haskell en v0)
Service premier plan (type SimplexService) + Worker périodique
(type MessagesFetcherWorker) : boucle chatRecvMsgWait + notifs locales,
sans FCM (ni HMS) en v0.
```

Principes : backend = `libsimplex` seul ; serveurs par défaut = SimpleX, **liste
modifiable par l'utilisateur** (ajout/suppression/test, fingerprints affichées) ;
zéro self-hosted ; zéro secret commité (`.env` gitignoré, placeholders).

## 4. Liste strip Telegram (incompatible — à ne PAS reprendre)

Auth par numéro de téléphone / codes MTProto ; `TdApi.*` / `org.drinkless.tdlib` ;
`Tdlib.java` et tout `telegram.*` ; `telegram.api_id/api_hash` ; FCM/HMS/push
Telegram, Play Integrity/Recaptcha/SafetyNet ; sync-adapter + authorities Telegram ;
chaînes/serveurs `*.telegram.org` ; groupes/channels/bots/stickers côté protocole ;
build natif TDLib (`tdlib`, `tgcalls`, `ffmpeg/webrtc`, LFS, Rust, NDK 30) ;
thèmes/outillage de génération Telegram-X.

## 5. Plan adaptateur JNI (v0)

1. Épingler version SimpleX (ex. `v7.0.3`) ; télécharger `simplex-aarch64.apk` (+ `armv7a`) ;
   vérifier `aapt2 dump badging` + `strings` + `apksigner verify` ; extraire
   `libsimplex.so` + `libsupport.so` par ABI → `~/.hermes/cache/scratch/ychat-apk/`
   puis `app/jniLibs/<abi>/` ; consigner SHA-256 dans SPEC.
2. Créer classe Kotlin `net.ychat.core.SimplexJni` avec `external fun` miroirs du §1.2
   (sous-ensemble v0 : `chatMigrateInit`, `chatCloseStore`, `chatSendCmdRetry`,
   `chatRecvMsgWait`, `chatParseServer`, `chatParseUri`, `chatWriteFile`,
   `chatReadFile`, `chatEncryptFile`, `chatDecryptFile`, + `initHS`,
   `pipeStdOutToSocket`).
3. Copier `simplex-api.c` → renommer **tous** les `Java_chat_simplex_common_platform_CoreKt_*`
   vers `Java_net_ychat_core_SimplexJniKt_*` ; CMake : importer les 2 `.so`
   (`IMPORTED_LOCATION …/jniLibs/${ANDROID_ABI}/…`), `max-page-size=16384`,
   garder `hs_init_with_rtsopts -A64m -H64m -xn`.
4. Séquence d'init (processus unique, une fois) : `loadLibrary("app-lib")` →
   `pipeStdOutToSocket(pkg)` → `initHS()` → `chatMigrateInit(dbPath, dbKey, confirm)`
   (`confirm` : `yesUp` par défaut comme SimpleX ; jamais `yesUpDown` sans action
   explicite) → `sendCmd("/_start main=on")` → boucle `chatRecvMsgWait`.
5. Codec JSON : réutiliser les chaînes `cmdString` documentées (§1.3 : `/u`,
   `/users`, `/_create user`, `/get chats`, `/_send …`) en ciblant les constructeurs
   `CC`/`CR` strictement nécessaires au MVP ; parser `responseType`/`details`.
6. Fichiers : `chatWriteFile` (ByteBuffer direct, jamais de `String` pour le binaire),
   test `chatParseServer` avant ajout d'un serveur custom.
7. Garde-fous : appels JNI jamais sur le main thread ; `retryNum` pour envois ;
   `chatCloseStore` à la destruction ; gestion `DBMigrationResult` (corruption →
   ne jamais effacer silencieusement).

## 6. Risques

- **Poids** : `libsimplex.so` ≈ 197 Mo (arm64, non compressé) → APK ~87 Mo/ABI ;
  acceptable mais surveiller la limite Play/F-Droid ; envisager split ABI.
- **Supply chain** : pas de Maven/AAR officiel — dépendance à l'extraction d'APK/CI ;
  épingler + vérifier signature et hash à chaque montée de version ; dérive possible
  du set `chat_*` (surveiller `libsimplex.dll.def` + `flake.nix`).
- **RTS Haskell** : init unique par processus, heap `-H64m`, GC non-déplaçant ;
  risque mémoire sur petits appareils — tester minSdk 26 réel.
- **Licence** : SimpleX = **AGPLv3** (lien dynamique JNI = œuvre combinée probable →
  YChat devra être AGPLv3-compatible) ; Telegram-X = GPLv3 → aucune copie.
- **Background Android 14+** : sans FCM, la synchro temps réel dépend du service
  premier plan + exemptions batterie ; documenter le compromis v0.
- **Migrations BDD** : `chatMigrateInit confirm` — un mauvais `confirm` peut
  détruire l'historique ; UX de confirmation obligatoire (cf. logique
  `YesUp`/`ErrorMigration` de `Core.kt:initChatController`).
- **Serveurs** : presets codés en dur côté Haskell — YChat doit stocker sa propre
  liste modifiable et ne jamais supposer `smp13` ou l'ordre des presets.

## 7. Vérification — extraits clés (verbatim, chemins lus)

Fichiers lus (sondage) :
`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/platform/Core.kt`,
`apps/multiplatform/common/src/commonMain/cpp/android/simplex-api.c`,
`apps/multiplatform/common/src/commonMain/cpp/android/CMakeLists.txt`,
`apps/multiplatform/common/src/androidMain/kotlin/chat/simplex/common/platform/AppCommon.android.kt`,
`apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/model/SimpleXAPI.kt`
(l.841-880, l.3955+), `apps/multiplatform/android/build.gradle.kts`,
`apps/multiplatform/android/src/main/java/chat/simplex/app/MainActivity.kt`,
`apps/multiplatform/.gitignore`, `scripts/android/download-libs.sh`, `scripts/android/lib.txt`,
`libsimplex.dll.def`, `src/Simplex/Chat.hs` (presetServers), `src/Simplex/Chat/Controller.hs`
(ChatCommand/ChatResponse), `src/Simplex/Chat/Operators.hs`, `src/Simplex/Chat/Operators/Presets.hs`,
`src/Simplex/Chat/Library/Commands.hs` (`_defaultNtfServers`),
`simplexmq:src/Simplex/FileTransfer/Client/Presets.hs`, `cabal.project`,
`TGX:app/build.gradle.kts`, `TGX:app/src/main/AndroidManifest.xml`,
`TGX:app/src/main/java/org/thunderdog/challegram/TDLib.java`,
`TGX:.../telegram/Tdlib.java`, `TGX:.../config/Config.java`, `TGX:local.properties.sample`,
`TGX:properties.gradle.kts`, `TGX:buildSrc/.../AppConfigurationSource.kt`,
`TGX:version.properties`, `TGX:README.md`, `TGX:.gitmodules`.

**Extrait 1** — `simplex-api.c` (pont JNI ↔ symboles Haskell) :
```c
extern char *chat_migrate_init(const char *path, const char *key, const char *confirm, chat_ctrl *ctrl);
extern char *chat_send_cmd_retry(chat_ctrl ctrl, const char *cmd, const int retryNum);
extern char *chat_recv_msg_wait(chat_ctrl ctrl, const int wait);
JNIEXPORT jobjectArray JNICALL
Java_chat_simplex_common_platform_CoreKt_chatMigrateInit(JNIEnv *env, __unused jclass clazz, jstring dbPath, jstring dbKey, jstring confirm) {
```

**Extrait 2** — `CMakeLists.txt` Android (import des précompilés, preuve qu'il n'y a
pas de compilation Haskell côté app) :
```cmake
add_library( simplex SHARED IMPORTED )
set_target_properties( simplex PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libsimplex.so)
add_library( support SHARED IMPORTED )
set_target_properties( support PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libsupport.so)
```

**Extrait 3** — `src/Simplex/Chat/Operators/Presets.hs` (serveurs SMP par défaut) :
```haskell
enabledSimplexChatSMPServers =
  [ "smp://0YuTwO05YJWS8rkjn9eLJDjQhFKvIYd8d4xG8X1blIU=@smp8.simplex.im,beccx4yfxxbvyhqypaavemqurytl6hozr47wfc7uuecacjqdvwpw2xid.onion",
    "smp://SkIkI6EPd2D63F4xFKfHk7I1UGZVNn6k1QWZ5rcyr6w=@smp9.simplex.im,jssqzccmrcws6bhmn77vgmhfjmhwlyr3u7puw4erkyoosywgl67slqqd.onion",
    ...
```

**Extrait 4** — `SimpleXAPI.kt:841-870` (protocole : commande texte → JSON, push) :
```kotlin
suspend fun sendCmd(rhId: Long?, cmd: CC, otherCtrl: ChatCtrl? = null, retryNum: Int = 0, log: Boolean = true): API {
    ...
      val c = cmd.cmdString
      ...
      val rStr = if (rhId == null) chatSendCmdRetry(ctrl, c, retryNum) else chatSendRemoteCmdRetry(ctrl, rhId.toInt(), c, retryNum)
      ...
      val r = json.decodeFromString<API>(rStr)
```
```kotlin
fun recvMsg(ctrl: ChatCtrl): API? {
    val rStr = chatRecvMsgWait(ctrl, MESSAGE_TIMEOUT)
```

**Extrait 5** — Telegram-X, preuve du couplage (`Tdlib.java`, 12 404 lignes, 16 467
`TdApi.`, 449 fichiers importateurs) :
```java
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
```
+ `local.properties.sample` : `telegram.api_id=94575` /
`telegram.api_hash=a3406de8d171bb422bb6ddf3bbd800e2` (secrets Telegram obligatoires).
