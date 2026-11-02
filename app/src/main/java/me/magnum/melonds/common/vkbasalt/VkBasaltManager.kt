package me.magnum.melonds.common.vkbasalt

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import me.magnum.melonds.MelonDSAndroidInterface
import java.io.File
import java.io.IOException

/**
 * Glue between the settings UI and the real vkBasalt Vulkan layer (libVkLayer_vkBasalt.so).
 *
 * The layer is loaded by the Vulkan loader when VulkanContext creates the VkInstance with the layer enabled
 * (see VulkanContext::SetPostProcessLayerEnabled in melonDS-android-lib). vkBasalt then reads the config pointed
 * to by VKBASALT_CONFIG_FILE, which this class writes right before the emulator starts.
 */
class VkBasaltManager(context: Context) {
    companion object {
        const val KEY_ENABLED = "video_vkbasalt_enabled"
        const val KEY_EFFECTS = "video_vkbasalt_effects"
        private const val EFFECT_SEPARATOR = "\n"
    }

    private val appContext = context.applicationContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)

    private val baseDir = File(appContext.filesDir, "vkbasalt")
    private val packDir = File(baseDir, "pack")
    private val tempPackDir = File(baseDir, "pack.tmp")
    private val configFile = File(baseDir, "vkBasalt.conf")
    private val logFile = File(appContext.cacheDir, "vkbasalt.log")

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun loadPack(): VkBasaltPack? {
        return if (packDir.isDirectory) VkBasaltZipImporter.scan(packDir) else null
    }

    fun hasPack(): Boolean = packDir.isDirectory

    fun availableEffects(): List<VkBasaltEffect> {
        return VkBasaltBuiltIns.effects + (loadPack()?.effects ?: emptyList())
    }

    /** Effect ids in chain order. */
    fun selectedEffectIds(): List<String> {
        return preferences.getString(KEY_EFFECTS, "").orEmpty().split(EFFECT_SEPARATOR).filter { it.isNotBlank() }
    }

    fun setSelectedEffectIds(ids: List<String>) {
        preferences.edit().putString(KEY_EFFECTS, ids.joinToString(EFFECT_SEPARATOR)).apply()
    }

    /**
     * Imports a ReShade/vkBasalt shader pack from a .zip. Replaces any previously imported pack.
     * Blocking: call from a background thread.
     */
    fun importZip(uri: Uri): Result<VkBasaltPack> = runCatching {
        baseDir.mkdirs()
        try {
            val stream = appContext.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the selected file")
            stream.use { VkBasaltZipImporter.extract(it, tempPackDir) }

            val scanned = VkBasaltZipImporter.scan(tempPackDir)
            if (scanned.effects.isEmpty() && scanned.userConf == null) {
                throw IOException("The zip has no ReShade (.fx) effects or vkBasalt.conf")
            }

            if (packDir.exists() && !packDir.deleteRecursively()) throw IOException("Could not replace the previous pack")
            if (!tempPackDir.renameTo(packDir)) throw IOException("Could not install the pack")
        } finally {
            tempPackDir.deleteRecursively()
        }

        // The previous selection refers to effects of the old pack
        setSelectedEffectIds(emptyList())
        VkBasaltZipImporter.scan(packDir)
    }

    fun removePack() {
        packDir.deleteRecursively()
        configFile.delete()
        setSelectedEffectIds(emptyList())
    }

    /**
     * Writes the vkBasalt config and tells the native side whether to enable the layer. Must be called before the
     * emulator creates its Vulkan instance (EmulatorActivity.onCreate).
     */
    fun applyForEmulation() {
        if (!isEnabled()) {
            MelonDSAndroidInterface.setVkBasaltConfig(null, null)
            return
        }

        val pack = loadPack()
        val selected = selectedEffectIds()
        val configText = when {
            selected.isNotEmpty() -> VkBasaltConfigWriter.build(pack, selected)
            pack?.userConf != null -> VkBasaltConfigWriter.rewriteUserConf(pack.userConf.readText(), pack, pack.userConf.parentFile ?: pack.root)
            else -> null
        }

        if (configText == null) {
            // Enabled but nothing selected: do not load the layer
            MelonDSAndroidInterface.setVkBasaltConfig(null, null)
            return
        }

        runCatching {
            baseDir.mkdirs()
            configFile.writeText(configText)
            MelonDSAndroidInterface.setVkBasaltConfig(configFile.absolutePath, logFile.absolutePath)
        }.onFailure {
            MelonDSAndroidInterface.setVkBasaltConfig(null, null)
        }
    }
}
