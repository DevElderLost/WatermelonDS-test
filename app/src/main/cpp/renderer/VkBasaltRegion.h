#pragma once

#include <cstdint>

namespace MelonDSAndroid
{
// Restricts the vkBasalt post-processing layer to the top screen of the current layout.
// The layer (libVkLayer_vkBasalt.so, loaded by the Vulkan loader) exports vkBasalt_SetEffectRegion; it is resolved lazily
// because the layer only exists when it was enabled for the Vulkan instance.
void setVkBasaltTopScreenOnly(bool enabled);
bool isVkBasaltTopScreenOnly();

// Rectangle in swapchain pixels for the swapchain with extent (extentWidth x extentHeight). width/height 0 clears it.
void publishVkBasaltRegion(uint32_t extentWidth, uint32_t extentHeight, int x, int y, uint32_t width, uint32_t height);
}
