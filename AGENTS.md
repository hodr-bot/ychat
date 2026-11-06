# AGENTS.md — YChat conductor rules

- One worktree per thread under `.worktrees/`, single writer via flock. Workers propose via `pi-wt` (never commit), conductor lands.
- Heavy commands sandboxed: `bwrap + timeout`. Never parallel Gradle runs (OOM).
- Android: `export ANDROID_HOME=$HOME/.android-shared/sdk` + `ANDROID_SDK_ROOT` + `GRADLE_USER_HOME=$HOME/.gradle-shared`. JDK 17 for Kotlin builds. No SDK downloads, no emulator (no KVM).
- Verify APK bytes: `aapt2 dump badging`, `strings` across all `classes*.dex`, `apksigner verify`. Copy shippable APK to `~/.hermes/cache/scratch/ychat-apk/` BEFORE land/fin.
- Zero secrets committed (placeholders + gitignored `.env` only).
