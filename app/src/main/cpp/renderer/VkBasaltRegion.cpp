#include "VkBasaltRegion.h"

#include <atomic>
#include <dlfcn.h>
#include <mutex>

namespace MelonDSAndroid
{
namespace
{
using SetEffectRegionFn = void (*)(uint32_t, uint32_t, int32_t, int32_t, uint32_t, uint32_t);

std::atomic<bool> gTopScreenOnly{false};
std::mutex gResolveMutex;
SetEffectRegionFn gSetEffectRegion = nullptr;

SetEffectRegionFn resolveSetEffectRegion()
{
    std::lock_guard<std::mutex> lock(gResolveMutex);
    if (gSetEffectRegion != nullptr)
        return gSetEffectRegion;

    // RTLD_NOLOAD: only succeeds when the loader already loaded the layer for this process
    void* handle = dlopen("libVkLayer_vkBasalt.so", RTLD_NOW | RTLD_NOLOAD);
    if (handle == nullptr)
        return nullptr;

    gSetEffectRegion = reinterpret_cast<SetEffectRegionFn>(dlsym(handle, "vkBasalt_SetEffectRegion"));
    dlclose(handle); // the layer stays loaded by the Vulkan loader, this only drops our extra reference
    return gSetEffectRegion;
}
}

void setVkBasaltTopScreenOnly(bool enabled)
{
    gTopScreenOnly.store(enabled, std::memory_order_relaxed);
}

bool isVkBasaltTopScreenOnly()
{
    return gTopScreenOnly.load(std::memory_order_relaxed);
}

void publishVkBasaltRegion(uint32_t extentWidth, uint32_t extentHeight, int x, int y, uint32_t width, uint32_t height)
{
    SetEffectRegionFn fn = resolveSetEffectRegion();
    if (fn != nullptr)
        fn(extentWidth, extentHeight, x, y, width, height);
}
}
