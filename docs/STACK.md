# STACK.md

- App: Kotlin Android, AGP 8.5.2 + Kotlin 1.9.24 + Gradle 8.14.4 system binary + JDK 17, compileSdk/targetSdk 34, minSdk 26.
- Backend: `libsimplex` prebuilt `.so` reuse via JNI (no Haskell cross-compile in v0). Default SMP/XFTP servers = SimpleX defaults, user-modifiable list.
- Infra: zero self-hosted en v0. GitHub: hodr-bot/ychat.
- Licence: SimpleX = AGPLv3 (combinaison via JNI) -> YChat en AGPLv3 ; ressources graphiques Telegram-X (GPLv3) : combinaison autorisee sens GPLv3 -> AGPLv3 (compatibilite FSF, oeuvre combinee sous AGPLv3), recopie avec attribution, zero code Java/TDLib repris.
