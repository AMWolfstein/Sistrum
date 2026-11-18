cmake_minimum_required(VERSION 3.22.1)
project(sistrum_discord LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 20)
option(SISTRUM_DISCORD_SDK "Include the staged Discord Social SDK" ON)
add_library(sistrum_discord SHARED src/main/cpp/presence.cpp)
target_compile_options(sistrum_discord PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
# Keep every JNI entry point while hiding the wrapper's static C++ runtime.
target_link_options(sistrum_discord PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,--version-script,${CMAKE_CURRENT_SOURCE_DIR}/presence.map"
    "-Wl,--icf=safe"
    "-Wl,--pack-dyn-relocs=android")
set_property(TARGET sistrum_discord APPEND PROPERTY LINK_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/presence.map")
set(SDK "${CMAKE_CURRENT_SOURCE_DIR}/../sdk/android")
if(SISTRUM_DISCORD_SDK AND EXISTS "${SDK}/jni/${ANDROID_ABI}/libdiscord_partner_sdk.so")
    add_library(discord_partner_sdk SHARED IMPORTED)
    set_target_properties(discord_partner_sdk PROPERTIES IMPORTED_LOCATION
        "${SDK}/jni/${ANDROID_ABI}/libdiscord_partner_sdk.so")
    target_include_directories(sistrum_discord PRIVATE "${SDK}/prefab/modules/discord_partner_sdk/include")
    target_compile_definitions(sistrum_discord PRIVATE SISTRUM_DISCORD_SDK=1)
    target_link_libraries(sistrum_discord PRIVATE discord_partner_sdk)
endif()
