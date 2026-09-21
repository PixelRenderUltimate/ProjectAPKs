cmake_minimum_required(VERSION 3.22.1)
project("pixelrender")

add_library(pixelrender_native SHARED vulkan_probe.cpp)

find_library(log-lib log)

# Catatan: kita TIDAK link langsung ke libvulkan.
# libvulkan.so di-dlopen saat runtime supaya .so tetap bisa dimuat pada
# perangkat yang tidak punya Vulkan loader sama sekali.
target_link_libraries(pixelrender_native ${log-lib} dl)
