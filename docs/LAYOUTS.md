# TGX — Inventaire layouts / drawables / strings / styles réutilisables SANS TDLib

> Sonde **lecture seule** du clone Telegram-X dans le scratch `ychat-spec-probe/tgx`.
> **Aucun fichier tgx n'a été copié** dans le worktree (inventaire par `ls`/`cat`/`grep` sur le scratch uniquement).
> Racine source : `/home/hermes/.hermes/cache/scratch/ychat-spec-probe/tgx/app/src/main/res/`
> Licence source : **GPLv3** (Telegram X) — compatibilité juridique avec ychat **à valider par le conducteur** (cf. Non-fait).
> Pas de build, pas de commit.

## 0. Constat structurant (conditionne tout le verdict)

TGX construit son UI **~95 % en code** (`org.thunderdog.challegram.v.*`, `component/chat/*`, `component/dialogs/*`).
Le dossier `res/layout/` ne contient que **9 fichiers** (tous des coquilles `RecyclerView` / `EditText` mono-vue),
**aucun** layout de bulle de message, **aucun** item de liste de chats, **aucun** avatar en XML.
Bulles / avatars / écrans = classes Java programmatiques **couplées à `TdApi`** → effort lourd, voir §5.

## 1. Layouts XML (9 + 1 variante)

| Ressource (chemin source) | Contenu résumé | Dépendance TdApi | Verdict | Effort estimé |
|---|---|---|---|---|
| `res/layout/recycler.xml` (→ `androidx.recyclerview.widget.RecyclerView`, thème `@style/WhiteRecyclerTheme`) | coquille liste générique plein écran | **Non** (XML prouvé §6, cmd L1) | **réutilisable** (pattern 6 lignes) | **S** (< 0,5 j, copie + renommage thème) |
| `res/layout/recycler_custom.xml` (→ `org.thunderdog.challegram.v.CustomRecyclerView`) | coquille liste générique custom | XML **non** ; classe `CustomRecyclerView.java` **sans `TdApi` direct** (imports : android + recyclerview + `me.vkryl`) | **adaptable** (XML + classe portée, dépendance `me.vkryl` à remplacer) | **M** (1–2 j) |
| `res/layout/recycler_sharedmedia.xml` (→ `v.MediaRecyclerView`) | coquille grille médias | XML **non** ; classe `MediaRecyclerView.java` **sans `TdApi` direct** | **adaptable** (idem) | **M** (1–2 j) |
| `res/layout/recycler_chats.xml` (→ `v.ChatsRecyclerView`) | coquille liste de chats | XML **non**, **mais** `ChatsRecyclerView.java` importe `org.drinkless.tdlib.TdApi` + `ChatsAdapter`, `ChatsController`, `ChatView` (preuve §6, cmd J1 : **positif**) | **non réutilisable en l'état / adaptable** (garder l'idée : RecyclerView + preload 15 + `ChatsController` à réécrire sur modèle ychat) | **L** (3–5 j, découplage adapter) |
| `res/layout/recycler_messages.xml` (→ `v.MessagesRecyclerView`) | coquille liste de messages | XML **non** ; classe **sans `TdApi` direct**, **mais** transitif **positif** : `MessagesAdapter.java` = 7 refs `TdApi`, `TGMessage.java` positif (cmd J2) | **non réutilisable en l'état / adaptable** (idée : LinearLayoutManager inversé + animator 140 ms + touch-helper à réécrire) | **L** (5–8 j) |
| `res/layout/input_header.xml` + `input_header_light.xml` (→ `v.HeaderEditText`, 19 dp, hint `#adcae0`) | champ titre header | XML **non** ; `HeaderEditText.java` **sans `TdApi`** (dépendances internes : `Lang`, `Theme`, `Fonts`, `Views`, `RtlCheckListener`) | **adaptable** (thème/couleurs en dur à extraire) | **M** (1 j) |
| `res/layout/input_header_grey.xml` (variante hint `#a1aab3`, texte noir) | idem thème clair | idem | **adaptable** | **M** (1 j) |
| `res/layout/input_password.xml` + `layout-v12/input_password.xml` (→ `v.EditText`, variante v12 `textCursorDrawable=@drawable/cursor_blue`) | champ mot de passe | XML **non**, **mais** `EditText.java` **positif `TdApi`** (cmd J1) | **adaptable** (le pattern variante v12 + `cursor_blue` est la vraie valeur ; classe à réécrire sur EditText stock) | **S–M** (0,5–1 j) |

## 2. Drawables (572 fichiers, 100 % `.xml`, preuve globale §6 cmd D1/D2 : **0 match `tdapi`/`tdlib` → tous sans TdApi**)

| Famille (chemin source) | Exemples vérifiés existants | Dépendance TdApi | Verdict | Effort |
|---|---|---|---|---|
| Icônes Material génériques `res/drawable/baseline_*_24.xml` (~400 fichiers, vecteurs `#FF000000`) | `baseline_check_24`, `baseline_search_24`, `baseline_chat_bubble_24`, `baseline_archive_24`, `baseline_group_24`, `baseline_person_24` ✓ présents | **Non** (couvert par preuve globale) | **réutilisables** (recolorer via tint, pas de copie aveugle : préférer Material symbols upstream quand identique) | **S** (0,5 j pour une sélection ~40) |
| Ticks / statut message `deproko_baseline_check_single_24`, `deproko_baseline_check_double_24`, `deproko_baseline_clock_24` (+ `andrejsharapov_baseline_message_check_*`) | ✓ présents | **Non** | **réutilisables** (leckey : single = envoyé, double = lu, clock = en attente — copier le trio) | **S** (< 0,5 j) |
| Actions chat `deproko_baseline_send_24`, `deproko_baseline_pin_24`, `deproko_baseline_msg_voice_24`, `deproko_baseline_msg_video_24` | ✓ présents | **Non** | **réutilisables** | **S** |
| Avatars / groupes / canaux : `baseline_account_circle_16/24`, `baseline_group_16/24/56/96`, `baseline_person_12/16/24/56`, `dot_baseline_acc_personal_*`, `deproko_baseline_mosaic_group_24`, `dot_baseline_bot/channel/group_circle_18` | ✓ présents | **Non** | **réutilisables comme placeholders** (le vrai rendu avatar TGX est programmé `AvatarView.java`, pas en XML) | **S** |
| Shapes/selectors : `transparent.xml`, `cursor_blue/grey/white.xml`, `bg_btn_header.xml`, `bg_btn_header_light.xml` (→ `@color/header_light_pressed`), `bg_btn_pin.xml` | ✓ lus intégralement, couleurs en dur, aucun ref TdApi | **Non** | **réutilisables** | **S** |
| `bg_edittext.xml` (selector → `@drawable/bg_edittext_active` / `bg_edittext_inactive`) | **références INTROUVABLES** dans `res/drawable` (`ls \| grep edittext` ne retourne que `bg_edittext.xml`) | **Non** (mais cassé) | **non réutilisable en l'état** (références manquantes) | **S** si recréé (2 shapes), sinon écart |
| `giveaway_particle_1/2.xml`, `ic_map_pin_44.xml`, logos `logo_youtube/vimeo/dailymotion` | présents | **Non** | **adaptables** au besoin (non prioritaires) | **S** |
| `res/drawable-xxhdpi/` (46 png), `drawable-hdpi/mdpi/xhdpi`, `mipmap-*`, `raw/` (wav/mp3 voip + `maps_night.json`) | binaires | N/A (pas de code) | **réutilisables** sous réserve licence/origine (sons voip à remplacer de préférence) | **S** |

## 3. Strings (5324 clés dans `res/values/strings.xml` — fichier global **NON réutilisable tel quel**, 11 lignes `tdapi|tdlib` §6 cmd S1)

Sous-ensemble cible **vérifié sans TdApi** (chaque valeur lue, aucune ne mentionne TDLib) :

| Clé | Valeur EN | Verdict / effort |
|---|---|---|
| `ChatContentPhoto` / `ChatContentVideo` / `ChatContentVoice` / `ChatContentFile` / `ChatContentDice` (`Dice: rolling…`) | Photo, Video, Voice Message, File | **réutilisables** (aperçus liste chats) — **S** (extraction + traduction FR) |
| `xChats_other` (`%1$s chats`), `xMessages_other`, `xNewMessages_other`, `NewMessages`, `SelectChat`, `MessageReply` (Reply), `DeleteMessage` | cf. valeurs lues §probe | **réutilisables** — **S** |
| `format_newMessagesFromChats`, `FromXChats_*`, `xOtherChatTitles_*`, `ChatContentVoiceDuration`, `ChatContentRoundVideo`, `MessageSearchFilter*` (All/Text/Photo/Video/Voice/Round/Files/Music/Gif), `ClearSavedMessages`, `DirectMessages` | — | **adaptables** (convention pluriels `_one/_other` à reprendre) — **M** (0,5–1 j tri + FR) |
| 11 lignes `TdlibLog*`, `TdlibLogs*`, `Launch*Tdlib*`, `DebugShareError` + `EmulatorDetectorReport_*` (local_strings) | — | **non réutilisables** (backdoor debug + ref TDLib) — exclure |
| `res/values/local_strings.xml` (hors les 2 clés emulator ci-dessus) : `UseExplicitDice`, `suggested_language_*`, `format_timestampWithTimeZone` | — | **réutilisables** — **S** |

## 4. Styles / colors / values / ids / menu / xml

| Ressource | Verdict (preuve §6) | Effort |
|---|---|---|
| `values/styles.xml` (`AppTheme`, `AppTheme.Dark`, `DialogTheme*`, `WhiteRecyclerTheme`, `TransparentTheme`) + variantes `values-v11/styles.xml`, `values-v13/styles.xml` (Holo, obsolètes) | `styles.xml` **réutilisable comme squelette** (preuve cmd T1, exit 1) ; variantes v11/v13 **non** (Holo mort) | **S** (thème moderne à réécrire, garder l'idée `WhiteRecyclerTheme` + `TransparentTheme`) |
| `values/colors.xml` (7 couleurs : `header_light_pressed`, `splash`, `status_bar`…) | **réutilisable** (preuve T1) mais palette en dur, pas de Material night | **S** |
| `values/values.xml` + `values-night/values.xml` (`isNight`), `values-sw600dp/sw720dp` (`isTablet`) | **réutilisables** (bool pattern jour/nuit/tablette, preuve T1) | **S** |
| `values/ids.xml` (1323 ids) | **NON réutilisable tel quel** : 14 ids `*tdlib*` (`btn_restartTdlib`, `btn_tdlib_*`, `btn_test_recovery_tdlib`…, cmd I1) → extraction sélective (`menu_*`, `btn_*` génériques) **adaptable** | **M** (1 j de tri) |
| `menu/text.xml` (9 items formatage : bold/italic/underline/strike/mono/spoiler/link/quote/clear, ids `@id/btn_*`, icônes `baseline_*`, titres `@string/TextFormat*`) | **réutilisable** (preuve T1) — modèle de barre de formatage | **S–M** (0,5–1 j, vérifier les 9 strings `TextFormat*` + icônes) |
| `xml/` (`provider_paths`, `syncadapter`, `authenticator`, `data_extraction_rules`, `automotive_app_desc`) | standards Android, **adaptables** (paths/sync à réécrire pour ychat) | **S** |

## 5. Ressources demandées mais ABSENTES en XML → verdicts

| Attendu | Réalité TGX | Verdict | Effort |
|---|---|---|---|
| Layout item liste de chats | Programmatique (`component/dialogs/ChatView.java`, `ChatsAdapter.java` + `ChatsRecyclerView` **TdApi+**) | **non réutilisable / adaptable** (spécifier item ychat from scratch en s'inspirant des strings §3) | **L** (1–2 sem, avec avatar programmé) |
| Bulles de messages | Programmatiques (`component/chat/MessageView*`, `TGMessage` **TdApi+**) | **non réutilisable** | **L** (2–3 sem) |
| Avatars | `AvatarView.java` programmé (+ placeholders §2) | placeholders **réutilisables**, vue **non** | **M–L** |
| Écrans simples (login, profil, réglages) | Programmatiques (`ui/*Controller.java`, la plupart **TdApi+**) ; seuls `input_*`/`recycler*` en XML | patterns XML **adaptables**, contrôleurs **non** | **L** |

## 6. Vérification OBLIGATOIRE — commandes, sorties, preuves

Toutes exécutées en lecture seule sur le scratch, **aucune copie vers le worktree**.

- **D1** `grep -ri "tdapi" $SP/app/src/main/res/drawable --include="*.xml" -l` → **aucune sortie, exit=1** (preuve : 572 drawables sans `tdapi`).
- **D2** `grep -ri "tdlib" $SP/app/src/main/res/drawable --include="*.xml" -l` → **aucune sortie, exit=1** (preuve : sans `tdlib`).
- **L1** `grep -ri "tdapi\|tdlib" $SP/app/src/main/res/layout $SP/app/src/main/res/layout-v12 -r` → **aucune sortie, exit=1** (preuve : 10 layouts sans TdApi).
- **T1** `grep -ri "tdapi\|tdlib" colors.xml styles.xml values.xml values-night/values.xml values-sw600dp/values.xml values-sw720dp/values.xml values-v11/styles.xml values-v13/styles.xml menu/text.xml` → **aucune sortie, exit=1** (preuve styles/colors/values/menu sans TdApi). *Écart de probing : première tentative avec chemins `values/values-v11` (au lieu de `values-v11/`) → exit=2, corrigée et relancée → exit=1, consigné.*
- **S1** `grep -c -i "tdapi\|tdlib" values/strings.xml` → **11** (preuve que le fichier global est **non réutilisable tel quel** ; lignes : `TdlibLogClearEmpty/Ok/Fail`, `TdlibLogs*`, `Launch*Tdlib*`, `DebugShareError`).
- **I1** `grep -n -i "tdlib" values/ids.xml` → **14 matchs** (`btn_restartTdlib`, `btn_secret_tdlibDatabaseStats`, `btn_secret_forceTdlibRestarts`, `btn_test_recovery_tdlib`, `btn_tdlib`, `btn_tdlib_verbosity/resetLogSettings/logSize/shareLogs/viewLogs/viewLogsOld/clearLogs/androidLogs/debugDatacenter`) → preuve **non réutilisable tel quel**.
- **J1** `grep -l "TdApi\|Tdlib\|tdlib" v/<Classe>.java` → **positif** : `EditText.java`, `ChatsRecyclerView.java` (import `org.drinkless.tdlib.TdApi` lu) ; **négatif** : `HeaderEditText.java`, `CustomRecyclerView.java`, `MessagesRecyclerView.java`, `MediaRecyclerView.java`.
- **J2** transitif : `MessagesAdapter.java` = **7 refs `TdApi`**, `TGMessage.java` **positif** → `recycler_messages.xml` classé non réutilisable en l'état malgré classe directe négative.

`$SP` = `/home/hermes/.hermes/cache/scratch/ychat-spec-probe/tgx`.

## 7. Non-fait explicite

1. **Pas de build** (interdit par la mission) — aucune validation compilation des extraits.
2. **Audit individuel des 572 drawables non fait** : la preuve d'absence TdApi est globale (D1/D2) + échantillon nommé (~30 fichiers) ; la pertinence 1-par-1 (doublons, variantes `_16/_24/_56/_96`) reste à trier.
3. **Transitif Java limité au niveau 1** (imports directs + `MessagesAdapter`/`TGMessage`) : graphe complet (`ChatView`, `ChatsAdapter`, `Theme`, `Lang`…) non audité.
4. **`bg_edittext_active/inactive` manquants non résolus** : origine (générés ? variante de build ?) non cherchée.
5. **Licence GPLv3** : recopie même partielle (vecteurs, strings EN) soumise au copyleft — décision conducteur en attente ; en attendant, ce document ne contient que des noms/résumés, pas de contenus recopiés.
6. **5324 − ~30 strings non auditées** une par une ; seules les clés citées §3 sont prouvées sans TdApi.
7. **Aucun fichier tgx copié** dans le worktree : vérifiable par `git status` (seuls `docs/LAYOUTS.md` + `.worker-report.md` ajoutés).
