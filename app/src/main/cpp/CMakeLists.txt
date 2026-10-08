cmake_minimum_required(VERSION 3.22.1)
project(pokedaisy C)

# ---------------------------------------------------------------------------
# Vendored mGBA (git submodule, pinned to 0.10.5) — core only.
# No Qt/SDL/GL frontends, no external deps (libzip/sqlite/ffmpeg/png/lua).
# This mirrors how mGBA's own 3DS/Switch/Vita targets build the bare core.
# ---------------------------------------------------------------------------
get_filename_component(MGBA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/mgba" ABSOLUTE)

if(NOT EXISTS "${MGBA_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "mGBA submodule missing at ${MGBA_DIR} — run: git submodule update --init third_party/mgba")
endif()

set(BUILD_STATIC        ON  CACHE BOOL "" FORCE)
set(BUILD_SHARED        OFF CACHE BOOL "" FORCE)
set(DISABLE_FRONTENDS   ON  CACHE BOOL "" FORCE)
set(DISABLE_DEPS        ON  CACHE BOOL "" FORCE)
set(SKIP_LIBRARY        OFF CACHE BOOL "" FORCE)
set(BUILD_GL            OFF CACHE BOOL "" FORCE)
set(BUILD_GLES2         OFF CACHE BOOL "" FORCE)
set(BUILD_GLES3         OFF CACHE BOOL "" FORCE)
set(BUILD_PERF          OFF CACHE BOOL "" FORCE)
set(BUILD_TEST          OFF CACHE BOOL "" FORCE)
set(BUILD_SUITE         OFF CACHE BOOL "" FORCE)
set(BUILD_LIBRETRO      OFF CACHE BOOL "" FORCE)
set(BUILD_PYTHON        OFF CACHE BOOL "" FORCE)
set(USE_DEBUGGERS       OFF CACHE BOOL "" FORCE)
set(USE_GDB_STUB        OFF CACHE BOOL "" FORCE)
set(USE_EDITLINE        OFF CACHE BOOL "" FORCE)
set(USE_FFMPEG          OFF CACHE BOOL "" FORCE)
set(USE_DISCORD_RPC     OFF CACHE BOOL "" FORCE)
set(USE_LZMA            OFF CACHE BOOL "" FORCE)
set(USE_LUA             OFF CACHE BOOL "" FORCE)
set(USE_ELF             OFF CACHE BOOL "" FORCE)
set(USE_SQLITE3         OFF CACHE BOOL "" FORCE)
set(USE_LIBZIP          OFF CACHE BOOL "" FORCE)
set(USE_MINIZIP         OFF CACHE BOOL "" FORCE)
set(USE_PNG             OFF CACHE BOOL "" FORCE)
set(USE_ZLIB            OFF CACHE BOOL "" FORCE)
set(M_CORE_GBA          ON  CACHE BOOL "" FORCE)
set(M_CORE_GB           ON  CACHE BOOL "" FORCE)   # Game Boy / Color (Pokémon Red/Blue/Yellow, G/S/C)
set(BUILD_LTO           OFF CACHE BOOL "" FORCE)
set(SKIP_INSTALL_ALL    ON  CACHE BOOL "" FORCE)

add_subdirectory("${MGBA_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/mgba" EXCLUDE_FROM_ALL)

# ---------------------------------------------------------------------------
# Vendored rcheevos (git submodule, pinned to v12.5.0) — RetroAchievements'
# own client library. GBA ROM hashing only: no disc, encrypted or zip hashing.
# ---------------------------------------------------------------------------
get_filename_component(RCHEEVOS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/rcheevos" ABSOLUTE)

if(NOT EXISTS "${RCHEEVOS_DIR}/include/rc_client.h")
    message(FATAL_ERROR "rcheevos submodule missing at ${RCHEEVOS_DIR} — run: git submodule update --init third_party/rcheevos")
endif()

file(GLOB RCHEEVOS_SOURCES
    "${RCHEEVOS_DIR}/src/*.c"
    "${RCHEEVOS_DIR}/src/rapi/*.c"
    "${RCHEEVOS_DIR}/src/rcheevos/*.c"
    "${RCHEEVOS_DIR}/src/rhash/hash.c"
    "${RCHEEVOS_DIR}/src/rhash/hash_rom.c"
    "${RCHEEVOS_DIR}/src/rhash/md5.c")
list(FILTER RCHEEVOS_SOURCES EXCLUDE REGEX "rc_libretro\\.c$|rc_client_external\\.c$|rc_client_raintegration\\.c$")

add_library(rcheevos STATIC ${RCHEEVOS_SOURCES})
target_include_directories(rcheevos PUBLIC "${RCHEEVOS_DIR}/include")
target_compile_definitions(rcheevos PUBLIC
    RC_CLIENT_SUPPORTS_HASH RC_HASH_NO_DISC RC_HASH_NO_ENCRYPTED RC_HASH_NO_ZIP)

# ---------------------------------------------------------------------------
# Our JNI bridge.
# ---------------------------------------------------------------------------
add_library(pokedaisy SHARED pokedaisy_jni.c pokedaisy_ra.c pk_cheats.c)

target_include_directories(pokedaisy PRIVATE
    "${MGBA_DIR}/include"
    "${MGBA_DIR}/src"
    "${CMAKE_CURRENT_BINARY_DIR}/mgba/include")

target_link_libraries(pokedaisy
    mgba
    rcheevos
    android
    log)
