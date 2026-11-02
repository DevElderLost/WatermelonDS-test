package me.magnum.melonds

import me.magnum.melonds.common.UriFileHandler
import me.magnum.melonds.domain.model.VulkanDriverConfiguration
import me.magnum.melonds.domain.model.VulkanDriverMode

object MelonDSAndroidInterface {
    const val RENDERER_CAP_OPENGL = 1 shl 0
    const val RENDERER_CAP_VULKAN = 1 shl 1

    private external fun setupNative(
        uriFileHandler: UriFileHandler,
        useCustomVulkanDriver: Boolean,
        vulkanTmpLibDir: String,
        vulkanHookLibDir: String,
        customVulkanDriverDir: String?,
        customVulkanDriverName: String?,
        customVulkanDriverDisplayName: String?,
    )
    private external fun configureVulkanDriverNative(
        useCustomVulkanDriver: Boolean,
        vulkanTmpLibDir: String,
        vulkanHookLibDir: String,
        customVulkanDriverDir: String?,
        customVulkanDriverName: String?,
        customVulkanDriverDisplayName: String?,
    )
    external fun getEmulatorGlContext(): Long
    external fun getRendererCapabilities(): Int
    private external fun canInitializeVulkanRendererNative(): Boolean
    external fun setVulkanCapabilityOverridesNative(
        disableTimelineSemaphores: Boolean,
        disableDynamicTextureIndexing: Boolean,
    )
    private external fun setVkBasaltConfigNative(configPath: String?, logPath: String?, topScreenOnly: Boolean)
    external fun cleanup()

    fun setup(uriFileHandler: UriFileHandler, vulkanDriverConfiguration: VulkanDriverConfiguration) {
        setupNative(
            uriFileHandler = uriFileHandler,
            useCustomVulkanDriver = vulkanDriverConfiguration.mode == VulkanDriverMode.CUSTOM,
            vulkanTmpLibDir = vulkanDriverConfiguration.tmpLibDir,
            vulkanHookLibDir = vulkanDriverConfiguration.hookLibDir,
            customVulkanDriverDir = vulkanDriverConfiguration.customDriverDir,
            customVulkanDriverName = vulkanDriverConfiguration.customDriverName,
            customVulkanDriverDisplayName = vulkanDriverConfiguration.customDriverDisplayName,
        )
    }

    fun configureVulkanDriver(vulkanDriverConfiguration: VulkanDriverConfiguration) {
        configureVulkanDriverNative(
            useCustomVulkanDriver = vulkanDriverConfiguration.mode == VulkanDriverMode.CUSTOM,
            vulkanTmpLibDir = vulkanDriverConfiguration.tmpLibDir,
            vulkanHookLibDir = vulkanDriverConfiguration.hookLibDir,
            customVulkanDriverDir = vulkanDriverConfiguration.customDriverDir,
            customVulkanDriverName = vulkanDriverConfiguration.customDriverName,
            customVulkanDriverDisplayName = vulkanDriverConfiguration.customDriverDisplayName,
        )
    }

    fun isVulkanRendererSupported(): Boolean {
        return runCatching {
            getRendererCapabilities() and RENDERER_CAP_VULKAN != 0
        }.getOrDefault(false)
    }

    fun canInitializeVulkanRenderer(): Boolean {
        return runCatching {
            canInitializeVulkanRendererNative()
        }.getOrDefault(false)
    }

    fun setVulkanCapabilityOverrides(
        disableTimelineSemaphores: Boolean,
        disableDynamicTextureIndexing: Boolean,
    ) {
        runCatching {
            setVulkanCapabilityOverridesNative(
                disableTimelineSemaphores,
                disableDynamicTextureIndexing,
            )
        }
    }

    /**
     * Enables (or disables, when [configPath] is null) the real vkBasalt Vulkan layer for the next Vulkan context.
     * [configPath] is the vkBasalt.conf to use, [logPath] an optional vkBasalt log file and [topScreenOnly] restricts the
     * effects to the top screen of the layout.
     */
    fun setVkBasaltConfig(configPath: String?, logPath: String?, topScreenOnly: Boolean = false) {
        runCatching {
            setVkBasaltConfigNative(configPath, logPath, topScreenOnly)
        }
    }
}
