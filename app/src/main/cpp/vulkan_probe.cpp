// PixelRender - Vulkan capability probe (PHASE 1)
//
// Tujuan file ini HANYA membaca kemampuan Vulkan dari perangkat.
// Tidak ada hook, tidak ada layer injection, tidak ada interaksi dengan
// proses aplikasi lain. Hasil dikembalikan sebagai JSON string ke Kotlin.
//
// libvulkan.so di-dlopen saat runtime agar library ini tetap bisa dimuat
// pada perangkat yang tidak memiliki Vulkan loader.

#include <jni.h>
#include <dlfcn.h>
#include <string>
#include <vector>
#include <vulkan/vulkan.h>

namespace {

std::string esc(const char *s) {
    std::string o;
    if (!s) return o;
    for (const char *p = s; *p; ++p) {
        unsigned char c = static_cast<unsigned char>(*p);
        if (c == '"' || c == '\\') {
            o.push_back('\\');
            o.push_back(static_cast<char>(c));
        } else if (c >= 0x20) {
            o.push_back(static_cast<char>(c));
        }
    }
    return o;
}

// Dekode versi Vulkan tanpa bergantung pada macro yang berubah antar versi header.
std::string ver(uint32_t v) {
    return std::to_string((v >> 22) & 0x7Fu) + "." +
           std::to_string((v >> 12) & 0x3FFu) + "." +
           std::to_string(v & 0xFFFu);
}

const char *devType(VkPhysicalDeviceType t) {
    switch (t) {
        case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU: return "INTEGRATED_GPU";
        case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU:   return "DISCRETE_GPU";
        case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU:    return "VIRTUAL_GPU";
        case VK_PHYSICAL_DEVICE_TYPE_CPU:            return "CPU";
        default:                                     return "OTHER";
    }
}

// Handle disimpan statis; sengaja tidak di-dlclose agar pointer fungsi
// yang sudah diambil tidak pernah menjadi dangling.
void *gVulkanLib = nullptr;

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_pixelrender_app_graphics_VulkanNative_nativeQueryJson(JNIEnv *env, jobject /*thiz*/) {

    if (!gVulkanLib) {
        gVulkanLib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    }
    if (!gVulkanLib) {
        return env->NewStringUTF(
                R"({"available":false,"reason":"libvulkan.so tidak tersedia pada perangkat ini"})");
    }

    auto gipa = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
            dlsym(gVulkanLib, "vkGetInstanceProcAddr"));
    if (!gipa) {
        return env->NewStringUTF(
                R"({"available":false,"reason":"vkGetInstanceProcAddr tidak ditemukan"})");
    }

    auto enumInstVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
            gipa(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));
    auto enumInstExt = reinterpret_cast<PFN_vkEnumerateInstanceExtensionProperties>(
            gipa(VK_NULL_HANDLE, "vkEnumerateInstanceExtensionProperties"));
    auto createInstance = reinterpret_cast<PFN_vkCreateInstance>(
            gipa(VK_NULL_HANDLE, "vkCreateInstance"));

    uint32_t loaderVersion = VK_API_VERSION_1_0;
    if (enumInstVersion) {
        enumInstVersion(&loaderVersion);
    }

    std::string j = "{\"available\":true,\"loaderApiVersion\":\"" + ver(loaderVersion) + "\"";

    j += ",\"instanceExtensions\":[";
    if (enumInstExt) {
        uint32_t n = 0;
        if (enumInstExt(nullptr, &n, nullptr) == VK_SUCCESS && n > 0) {
            std::vector<VkExtensionProperties> props(n);
            if (enumInstExt(nullptr, &n, props.data()) == VK_SUCCESS) {
                for (uint32_t i = 0; i < n; ++i) {
                    if (i) j += ",";
                    j += "\"" + esc(props[i].extensionName) + "\"";
                }
            }
        }
    }
    j += "]";

    VkApplicationInfo ai{};
    ai.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    ai.pApplicationName = "PixelRender";
    ai.applicationVersion = 1;
    ai.pEngineName = "PixelRenderProbe";
    ai.engineVersion = 1;
    ai.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo ci{};
    ci.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    ci.pApplicationInfo = &ai;

    VkInstance inst = VK_NULL_HANDLE;
    VkResult r = createInstance ? createInstance(&ci, nullptr, &inst)
                                : VK_ERROR_INITIALIZATION_FAILED;

    j += ",\"devices\":[";

    if (r == VK_SUCCESS && inst != VK_NULL_HANDLE) {
        auto enumPd = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(
                gipa(inst, "vkEnumeratePhysicalDevices"));
        auto getProps = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(
                gipa(inst, "vkGetPhysicalDeviceProperties"));
        auto getFeat = reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures>(
                gipa(inst, "vkGetPhysicalDeviceFeatures"));
        auto enumDevExt = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(
                gipa(inst, "vkEnumerateDeviceExtensionProperties"));
        auto destroyInst = reinterpret_cast<PFN_vkDestroyInstance>(
                gipa(inst, "vkDestroyInstance"));

        uint32_t dc = 0;
        if (enumPd && enumPd(inst, &dc, nullptr) == VK_SUCCESS && dc > 0) {
            std::vector<VkPhysicalDevice> pds(dc);
            enumPd(inst, &dc, pds.data());

            for (uint32_t i = 0; i < dc; ++i) {
                if (i) j += ",";

                VkPhysicalDeviceProperties p{};
                if (getProps) getProps(pds[i], &p);
                VkPhysicalDeviceFeatures f{};
                if (getFeat) getFeat(pds[i], &f);

                j += "{\"name\":\"" + esc(p.deviceName) + "\"";
                j += ",\"apiVersion\":\"" + ver(p.apiVersion) + "\"";
                j += ",\"driverVersionRaw\":" + std::to_string(p.driverVersion);
                j += ",\"vendorId\":" + std::to_string(p.vendorID);
                j += ",\"deviceId\":" + std::to_string(p.deviceID);
                j += ",\"type\":\"" + std::string(devType(p.deviceType)) + "\"";
                j += ",\"samplerAnisotropy\":";
                j += (f.samplerAnisotropy ? "true" : "false");
                j += ",\"maxSamplerAnisotropy\":" +
                     std::to_string(static_cast<int>(p.limits.maxSamplerAnisotropy));
                j += ",\"maxImageDimension2D\":" + std::to_string(p.limits.maxImageDimension2D);

                j += ",\"colorSampleCounts\":[";
                {
                    const uint32_t counts[] = {1u, 2u, 4u, 8u, 16u, 32u, 64u};
                    bool first = true;
                    for (uint32_t c : counts) {
                        if (p.limits.framebufferColorSampleCounts & c) {
                            if (!first) j += ",";
                            j += std::to_string(c);
                            first = false;
                        }
                    }
                }
                j += "]";

                j += ",\"deviceExtensions\":[";
                if (enumDevExt) {
                    uint32_t en = 0;
                    if (enumDevExt(pds[i], nullptr, &en, nullptr) == VK_SUCCESS && en > 0) {
                        std::vector<VkExtensionProperties> eps(en);
                        if (enumDevExt(pds[i], nullptr, &en, eps.data()) == VK_SUCCESS) {
                            for (uint32_t k = 0; k < en; ++k) {
                                if (k) j += ",";
                                j += "\"" + esc(eps[k].extensionName) + "\"";
                            }
                        }
                    }
                }
                j += "]}";
            }
        }

        if (destroyInst) destroyInst(inst, nullptr);
    }

    j += "]";
    if (r != VK_SUCCESS) {
        j += ",\"instanceError\":" + std::to_string(static_cast<int>(r));
    }
    j += "}";

    return env->NewStringUTF(j.c_str());
}
