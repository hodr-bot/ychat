#!/usr/bin/env bash
# Fetch + verify official SimpleX APK v7.0.3 and extract native libs.
# Idempotent: skips download/extract when cached .so files already match expected sha256.
# Extracts to cache scratch ychat-apk (default: $HOME/.hermes/cache/scratch/ychat-apk).
set -euo pipefail

VERSION="7.0.3"
APK_NAME="simplex-aarch64.apk"
BASE_URL="https://github.com/simplex-chat/simplex-chat/releases/download/v${VERSION}"
APK_URL="${BASE_URL}/${APK_NAME}"

EXPECTED_SIMPLEX="1856cdc7e1dd3cee2dac3d7d013d1e06c3facc12879f5775ea93ba92161dbfb4"
EXPECTED_SUPPORT="acbabf60244517ec227f84afdbb166c7f2d00e31274d229bbba70926ff240f36"

CACHE_DIR="${SCRATCH_DIR:-${HOME}/.hermes/cache/scratch/ychat-apk}"
APK_PATH="${CACHE_DIR}/${APK_NAME}"
LIBS_DIR="${CACHE_DIR}/arm64-v8a"

mkdir -p "${CACHE_DIR}" "${LIBS_DIR}"

sha_of() {
  sha256sum "$1" | awk '{print $1}'
}

apksigner_bin() {
  if [ -n "${ANDROID_HOME:-}" ] && [ -x "${ANDROID_HOME}/build-tools/34.0.0/apksigner" ]; then
    echo "${ANDROID_HOME}/build-tools/34.0.0/apksigner"
  elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "${ANDROID_SDK_ROOT}/build-tools/34.0.0/apksigner" ]; then
    echo "${ANDROID_SDK_ROOT}/build-tools/34.0.0/apksigner"
  elif command -v apksigner >/dev/null 2>&1; then
    echo "apksigner"
  else
    echo "" >&2
    echo "ERROR: apksigner introuvable (ni ANDROID_HOME/build-tools/34.0.0 ni PATH)" >&2
    return 1
  fi
}

# --- Idempotence: si les .so sont deja presents et conformes, on ne fait rien ---
if [ -f "${LIBS_DIR}/libsimplex.so" ] && [ -f "${LIBS_DIR}/libsupport.so" ]; then
  GOT_SIMPLEX="$(sha_of "${LIBS_DIR}/libsimplex.so")"
  GOT_SUPPORT="$(sha_of "${LIBS_DIR}/libsupport.so")"
  if [ "${GOT_SIMPLEX}" = "${EXPECTED_SIMPLEX}" ] && [ "${GOT_SUPPORT}" = "${EXPECTED_SUPPORT}" ]; then
    echo "OK: libs deja en cache et conformes dans ${LIBS_DIR}, rien a faire."
    exit 0
  else
    echo "WARN: cache existant non conforme, re-extraction."
    echo "  libsimplex.so: ${GOT_SIMPLEX} (attendu ${EXPECTED_SIMPLEX})"
    echo "  libsupport.so: ${GOT_SUPPORT} (attendu ${EXPECTED_SUPPORT})"
  fi
fi

# --- Telechargement APK officiel (reprendre si partiel) ---
if [ -f "${APK_PATH}" ]; then
  echo "APK deja present: ${APK_PATH} (re-verification apksigner ci-dessous)"
else
  echo "Telechargement ${APK_URL}"
  echo "  -> ${APK_PATH}"
  curl -fL --retry 3 -C - -o "${APK_PATH}" "${APK_URL}"
fi

# --- Verification signature APK ---
SIGNER="$(apksigner_bin)"
echo "Verification apksigner: ${SIGNER} verify ${APK_PATH}"
"${SIGNER}" verify --verbose "${APK_PATH}"

# --- Localisation des .so dans l'APK (aarch64 -> arm64-v8a) ---
echo "Contenu natif de l'APK:"
unzip -l "${APK_PATH}" | grep -E '\.so$' || {
  echo "ERROR: aucun .so trouve dans ${APK_PATH}" >&2
  exit 1
}

SIMPLEX_ENTRY="$(unzip -l "${APK_PATH}" | awk '{print $4}' | grep -E 'libsimplex\.so$' | grep 'arm64-v8a' | head -n 1)"
SUPPORT_ENTRY="$(unzip -l "${APK_PATH}" | awk '{print $4}' | grep -E 'libsupport\.so$' | grep 'arm64-v8a' | head -n 1)"

if [ -z "${SIMPLEX_ENTRY}" ] || [ -z "${SUPPORT_ENTRY}" ]; then
  echo "ERROR: entrees arm64-v8a libsimplex.so / libsupport.so introuvables" >&2
  exit 1
fi
echo "  libsimplex: ${SIMPLEX_ENTRY}"
echo "  libsupport: ${SUPPORT_ENTRY}"

# --- Extraction vers cache scratch ---
TMPDIR="$(mktemp -d)"
trap 'rm -rf "${TMPDIR}"' EXIT
unzip -p "${APK_PATH}" "${SIMPLEX_ENTRY}" > "${TMPDIR}/libsimplex.so"
unzip -p "${APK_PATH}" "${SUPPORT_ENTRY}" > "${TMPDIR}/libsupport.so"

echo "Verification sha256 des .so extraits:"
echo "  libsimplex.so: $(sha_of "${TMPDIR}/libsimplex.so")"
echo "  libsupport.so: $(sha_of "${TMPDIR}/libsupport.so")"

if [ "$(sha_of "${TMPDIR}/libsimplex.so")" != "${EXPECTED_SIMPLEX}" ]; then
  echo "ERROR: sha256 libsimplex.so non conforme (attendu ${EXPECTED_SIMPLEX})" >&2
  exit 1
fi
if [ "$(sha_of "${TMPDIR}/libsupport.so")" != "${EXPECTED_SUPPORT}" ]; then
  echo "ERROR: sha256 libsupport.so non conforme (attendu ${EXPECTED_SUPPORT})" >&2
  exit 1
fi

cp -f "${TMPDIR}/libsimplex.so" "${LIBS_DIR}/libsimplex.so"
cp -f "${TMPDIR}/libsupport.so" "${LIBS_DIR}/libsupport.so"
cp -f "${APK_PATH}" "${CACHE_DIR}/" 2>/dev/null || true

echo "OK: libs verifiees et extraites vers ${LIBS_DIR}"
ls -l "${LIBS_DIR}/"
