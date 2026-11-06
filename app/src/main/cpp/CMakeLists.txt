# YChat JNI bridge — app/src/main/cpp/CMakeLists.txt
#
# Reference upstream : simplex-chat (clone scratch ychat-spec-probe),
#   apps/multiplatform/common/src/commonMain/cpp/android/CMakeLists.txt
#   (cible upstream `app-lib` / `simplex-api.c`, cmake_minimum 3.10.2).
# Cible YChat (lue du pont ../ychat1/app/src/main/cpp/ychat-bridge.c,
#   prefixe JNI Java_org_ychat_SimplexJni_) : `ychat-bridge` / `ychat-bridge.c`.

cmake_minimum_required(VERSION 3.22)

project("ychat-bridge")

# Bibliotheque JNI YChat.
add_library(ychat-bridge SHARED ychat-bridge.c)

# Dossier des .so prebuilts Haskell : surchargeable via
#   -DYCHAT_SIMPLEX_LIB_DIR=<dir>
# Defaut : cache scratch ychat-apk/arm64-v8a (verifie present sur disque,
#   voir .worker-report.md).
if(NOT DEFINED YCHAT_SIMPLEX_LIB_DIR)
  if(DEFINED ENV{HOME})
    set(YCHAT_SIMPLEX_LIB_DIR "$ENV{HOME}/.hermes/cache/scratch/ychat-apk/arm64-v8a")
  else()
    set(YCHAT_SIMPLEX_LIB_DIR "/home/hermes/.hermes/cache/scratch/ychat-apk/arm64-v8a")
  endif()
endif()

# Prebuilts IMPORTED (noms de cibles repris de l'upstream : simplex, support).
add_library(simplex SHARED IMPORTED)
set_target_properties(simplex PROPERTIES IMPORTED_LOCATION
  ${YCHAT_SIMPLEX_LIB_DIR}/libsimplex.so)

add_library(support SHARED IMPORTED)
set_target_properties(support PROPERTIES IMPORTED_LOCATION
  ${YCHAT_SIMPLEX_LIB_DIR}/libsupport.so)

# jni.h : find_package d'abord, repli sur ANDROID_NDK porte (variable
# CMake ou variable d'environnement).
find_package(JNI QUIET)
if(JNI_FOUND)
  target_include_directories(ychat-bridge PRIVATE ${JNI_INCLUDE_DIRS})
elseif(DEFINED ANDROID_NDK)
  target_include_directories(ychat-bridge PRIVATE ${ANDROID_NDK}/sysroot/usr/include)
elseif(DEFINED ENV{ANDROID_NDK})
  target_include_directories(ychat-bridge PRIVATE $ENV{ANDROID_NDK}/sysroot/usr/include)
endif()

# NDK log (miroir upstream : find_library puis linkage).
find_library(log-lib log)

# Upstream : -g0 en compile.
target_compile_options(ychat-bridge PRIVATE
  -g0
)

# 16 KiB pages (Android 15+) + build-id none, comme l'upstream.
# https://developer.android.com/guide/practices/page-sizes#cmake
target_link_options(ychat-bridge PRIVATE
  "-Wl,-z,max-page-size=16384"
  "-Wl,--build-id=none"
)

target_link_libraries(ychat-bridge
  simplex support
  ${log-lib}
)

# Strip section .comment post-build, comme l'upstream.
add_custom_command(TARGET ychat-bridge POST_BUILD
  COMMAND ${CMAKE_STRIP} --remove-section=.comment $<TARGET_FILE:ychat-bridge>
  COMMENT "Stripping .comment section from ychat-bridge"
)
