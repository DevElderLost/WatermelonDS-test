// Exports needed by the Android Vulkan loader on top of what upstream vkBasalt already exports
// (vkBasalt_GetInstanceProcAddr / vkBasalt_GetDeviceProcAddr).
//
// Layer interface v2 (vkNegotiateLoaderLayerInterfaceVersion) plus the classic
// "<layer name>GetInstanceProcAddr" symbols and the vkEnumerate* entry points, the same set the
// Khronos validation layer exports, so every loader generation can find the layer.
#define VK_NO_PROTOTYPES
#include "vulkan/vulkan.h"
#include "vulkan/vk_layer.h"

#include <cstring>

#define VKBASALT_ANDROID_EXPORT extern "C" __attribute__((visibility("default")))

// Upstream vkBasalt declares these without VKAPI_ATTR. On 32-bit ARM VKAPI_ATTR is a calling convention attribute
// (pcs "aapcs-vfp") that is part of the PFN_vk* function pointer types, so the exported entry points below are thin
// wrappers carrying the right attribute (the arguments are pointers only, so both conventions are identical).
extern "C"
{
    PFN_vkVoidFunction vkBasalt_GetDeviceProcAddr(VkDevice device, const char* pName);
    PFN_vkVoidFunction vkBasalt_GetInstanceProcAddr(VkInstance instance, const char* pName);
}

static VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL basaltGetInstanceProcAddr(VkInstance instance, const char* pName)
{
    return vkBasalt_GetInstanceProcAddr(instance, pName);
}

static VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL basaltGetDeviceProcAddr(VkDevice device, const char* pName)
{
    return vkBasalt_GetDeviceProcAddr(device, pName);
}

VKBASALT_ANDROID_EXPORT VKAPI_ATTR VkResult VKAPI_CALL vkNegotiateLoaderLayerInterfaceVersion(VkNegotiateLayerInterface* pVersionStruct)
{
    if (pVersionStruct == nullptr || pVersionStruct->sType != LAYER_NEGOTIATE_INTERFACE_STRUCT)
        return VK_ERROR_INITIALIZATION_FAILED;

    if (pVersionStruct->loaderLayerInterfaceVersion > 2)
        pVersionStruct->loaderLayerInterfaceVersion = 2;

    if (pVersionStruct->loaderLayerInterfaceVersion >= 2)
    {
        pVersionStruct->pfnGetInstanceProcAddr       = basaltGetInstanceProcAddr;
        pVersionStruct->pfnGetDeviceProcAddr         = basaltGetDeviceProcAddr;
        pVersionStruct->pfnGetPhysicalDeviceProcAddr = nullptr;
    }
    return VK_SUCCESS;
}

VKBASALT_ANDROID_EXPORT VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL VK_LAYER_VKBASALT_post_processingGetInstanceProcAddr(VkInstance instance, const char* pName)
{
    return basaltGetInstanceProcAddr(instance, pName);
}

VKBASALT_ANDROID_EXPORT VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL VK_LAYER_VKBASALT_post_processingGetDeviceProcAddr(VkDevice device, const char* pName)
{
    return basaltGetDeviceProcAddr(device, pName);
}

VKBASALT_ANDROID_EXPORT VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateInstanceLayerProperties(uint32_t* pPropertyCount, VkLayerProperties* pProperties)
{
    auto fn = reinterpret_cast<PFN_vkEnumerateInstanceLayerProperties>(basaltGetInstanceProcAddr(nullptr, "vkEnumerateInstanceLayerProperties"));
    return fn ? fn(pPropertyCount, pProperties) : VK_ERROR_INITIALIZATION_FAILED;
}

VKBASALT_ANDROID_EXPORT VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateInstanceExtensionProperties(const char* pLayerName, uint32_t* pPropertyCount, VkExtensionProperties* pProperties)
{
    auto fn = reinterpret_cast<PFN_vkEnumerateInstanceExtensionProperties>(basaltGetInstanceProcAddr(nullptr, "vkEnumerateInstanceExtensionProperties"));
    return fn ? fn(pLayerName, pPropertyCount, pProperties) : VK_ERROR_INITIALIZATION_FAILED;
}

VKBASALT_ANDROID_EXPORT VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateDeviceLayerProperties(VkPhysicalDevice physicalDevice, uint32_t* pPropertyCount, VkLayerProperties* pProperties)
{
    return vkEnumerateInstanceLayerProperties(pPropertyCount, pProperties);
}

VKBASALT_ANDROID_EXPORT VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateDeviceExtensionProperties(VkPhysicalDevice physicalDevice, const char* pLayerName, uint32_t* pPropertyCount, VkExtensionProperties* pProperties)
{
    return vkEnumerateInstanceExtensionProperties(pLayerName, pPropertyCount, pProperties);
}
