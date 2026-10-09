# PokeDaisy for the 3DS: libmgba (the submodule, built by its own CMake for the
# 3DS) + the portable companion core + main.c, packed into a .3dsx.
# Configure with mGBA's 3DS toolchain file (it sets up devkitARM and the `3DS`
# variable mGBA keys its platform code on) - `make 3ds` in ../ does it in Docker.
cmake_minimum_required(VERSION 3.13)
project(pokedaisy C)

get_filename_component(MGBA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/mgba" ABSOLUTE)
if(NOT EXISTS "${MGBA_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "mGBA submodule missing at ${MGBA_DIR} - run: git submodule update --init third_party/mgba")
endif()

# libmgba only, GBA only - the same switches as the Android build
# (app/src/main/cpp/CMakeLists.txt), minus the Game Boy core for now.
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
set(M_CORE_GB           OFF CACHE BOOL "" FORCE)
set(BUILD_LTO           OFF CACHE BOOL "" FORCE)
set(SKIP_INSTALL_ALL    ON  CACHE BOOL "" FORCE)

add_subdirectory("${MGBA_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/mgba" EXCLUDE_FROM_ALL)

file(GLOB CORE_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/../core/*.c")

add_executable(pokedaisy.elf main.c ${CORE_SOURCES})
target_include_directories(pokedaisy.elf PRIVATE
    "${MGBA_DIR}/include"
    "${MGBA_DIR}/src"
    "${CMAKE_CURRENT_BINARY_DIR}/mgba/include")
# mGBA's 3DS platform defines (src/platform/3ds/CMakeLists.txt): its public
# headers size color_t by them, so ours must match the library's.
target_compile_definitions(pokedaisy.elf PRIVATE COLOR_16_BIT COLOR_5_6_5 M_CORE_GBA BUILD_STATIC)
target_compile_options(pokedaisy.elf PRIVATE -std=gnu11 -O2 -Wall -Wextra)
target_link_libraries(pokedaisy.elf mgba m)

find_program(SMDHTOOL smdhtool HINTS "$ENV{DEVKITPRO}/tools/bin" /opt/devkitpro/tools/bin)
find_program(3DSXTOOL_BIN 3dsxtool HINTS "$ENV{DEVKITPRO}/tools/bin" /opt/devkitpro/tools/bin)

add_custom_command(
    OUTPUT pokedaisy.smdh
    COMMAND ${SMDHTOOL} --create "PokeDaisy" "Gen 3 Pokemon companion" "PokeDaisy"
            "${CMAKE_CURRENT_SOURCE_DIR}/icon.png" pokedaisy.smdh
    DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/icon.png")

add_custom_command(
    OUTPUT pokedaisy.3dsx
    COMMAND ${3DSXTOOL_BIN} pokedaisy.elf pokedaisy.3dsx --smdh=pokedaisy.smdh
    DEPENDS pokedaisy.elf pokedaisy.smdh)

add_custom_target(pokedaisy_3dsx ALL DEPENDS pokedaisy.3dsx)
