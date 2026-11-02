package me.magnum.melonds.common.vkbasalt

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * One effect selectable for the vkBasalt chain.
 *
 * [file] is null for the effects built into vkBasalt (cas, dls, fxaa, smaa, deband) and points to a
 * ReShade `.fx` file inside the imported pack otherwise.
 */
data class VkBasaltEffect(val id: String, val displayName: String, val file: File?) {
    val isBuiltIn: Boolean get() = file == null
}

/**
 * A ReShade/vkBasalt shader pack that was imported from a .zip file.
 *
 * Typical layouts that are understood (anywhere inside the zip, with or without a wrapping folder):
 *  - the official reshade-shaders repo: a `Shaders` folder (.fx/.fxh files) plus a `Textures` folder
 *  - a vkBasalt setup: `vkBasalt.conf` next to the shader folders
 */
class VkBasaltPack(
    val root: File,
    val effects: List<VkBasaltEffect>,
    val includeDir: File?,
    val textureDir: File?,
    val userConf: File?,
)

object VkBasaltBuiltIns {
    val effects: List<VkBasaltEffect> = listOf(
        VkBasaltEffect("cas", "CAS (contrast adaptive sharpening)", null),
        VkBasaltEffect("dls", "DLS (denoised luma sharpening)", null),
        VkBasaltEffect("fxaa", "FXAA", null),
        VkBasaltEffect("smaa", "SMAA", null),
        VkBasaltEffect("deband", "Deband", null),
    )
    val ids: Set<String> = effects.map { it.id }.toSet()
}

object VkBasaltZipImporter {
    const val MAX_ENTRIES = 50_000
    const val MAX_TOTAL_BYTES = 768L * 1024 * 1024

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "bmp", "tga", "dds", "cube")

    /**
     * Extracts the zip into [destDir] (which is wiped first). Entries that would escape [destDir]
     * (zip-slip) are rejected and the extraction is capped to protect against zip bombs.
     */
    @Throws(IOException::class)
    fun extract(input: InputStream, destDir: File) {
        if (destDir.exists() && !destDir.deleteRecursively()) {
            throw IOException("Could not clear ${destDir.path}")
        }
        if (!destDir.mkdirs()) {
            throw IOException("Could not create ${destDir.path}")
        }

        val canonicalRoot = destDir.canonicalFile
        var entries = 0
        var total = 0L
        val buffer = ByteArray(64 * 1024)

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IOException("Too many entries in zip")

                val name = entry.name.replace('\\', '/')
                if (name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith("._")) continue

                val target = File(canonicalRoot, name).canonicalFile
                if (target.path != canonicalRoot.path && !target.path.startsWith(canonicalRoot.path + File.separator)) {
                    throw IOException("Blocked unsafe zip entry: ${entry.name}")
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }

                target.parentFile?.mkdirs()
                target.outputStream().use { out ->
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_TOTAL_BYTES) throw IOException("Zip is too large once extracted")
                        out.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    /** Scans an extracted pack and works out where the shaders, textures and optional config are. */
    fun scan(root: File): VkBasaltPack {
        val all = root.walkTopDown().filter { it.isFile }.toList()
        val fxFiles = all.filter { it.extension.equals("fx", ignoreCase = true) }

        // The folder that holds most .fx files is the shader folder. A folder called "Shaders" wins ties.
        val shadersDir = fxFiles
            .groupBy { it.parentFile }
            .maxWithOrNull(compareBy({ it.value.size }, { it.key?.name.equals("Shaders", ignoreCase = true) }))
            ?.key
        // Includes (.fxh) may live in the shader folder or one of its parents/siblings, so use the "Shaders" folder itself
        // when .fx files are nested deeper (Shaders/Sub/Effect.fx).
        val includeDir = fxFiles
            .mapNotNull { f -> generateSequence(f.parentFile) { it.parentFile }.takeWhile { it != root.parentFile }.firstOrNull { it.name.equals("Shaders", ignoreCase = true) } }
            .firstOrNull() ?: shadersDir

        val textureDir = all
            .firstOrNull { it.parentFile?.name.equals("Textures", ignoreCase = true) }?.parentFile
            ?: all.filter { it.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS }
                .groupBy { it.parentFile }.maxByOrNull { it.value.size }?.key

        val userConf = all.firstOrNull { it.extension.equals("conf", ignoreCase = true) && it.name.contains("basalt", ignoreCase = true) }
            ?: all.firstOrNull { it.extension.equals("conf", ignoreCase = true) }

        val effects = fxFiles
            .sortedBy { it.relativeTo(root).path.lowercase(Locale.ROOT) }
            .map { VkBasaltEffect(id = it.relativeTo(root).invariantSeparatorsPath, displayName = it.nameWithoutExtension, file = it) }

        return VkBasaltPack(root, effects, includeDir, textureDir, userConf)
    }
}

object VkBasaltConfigWriter {
    private val KEY_VALUE = Regex("""^\s*([A-Za-z0-9_.\-]+)\s*=\s*(.*?)\s*$""")
    private val PATH_KEYS = setOf("reshadeTexturePath", "reshadeIncludePath", "lutFile")

    /** Config key used for a pack effect (vkBasalt reads `<key> = /path/effect.fx`). Never clashes with built-ins. */
    fun configKey(effect: VkBasaltEffect, taken: MutableSet<String>): String {
        val base = effect.displayName.replace(Regex("[^A-Za-z0-9_]"), "_").ifEmpty { "effect" }
        var key = if (base.lowercase(Locale.ROOT) in VkBasaltBuiltIns.ids || base.equals("effects", true)) "fx_$base" else base
        var n = 2
        while (!taken.add(key.lowercase(Locale.ROOT))) key = "${base}_${n++}"
        return key
    }

    /**
     * Builds a vkBasalt.conf for the chosen effects (in the given order). Unknown ids are skipped.
     */
    fun build(pack: VkBasaltPack?, selectedIds: List<String>, depthCapture: Boolean = false, topScreenOnly: Boolean = false): String {
        val byId = (VkBasaltBuiltIns.effects + (pack?.effects ?: emptyList())).associateBy { it.id }
        val chain = selectedIds.mapNotNull { byId[it] }.distinctBy { it.id }

        val taken = mutableSetOf<String>()
        taken.addAll(VkBasaltBuiltIns.ids)
        val sb = StringBuilder()
        sb.append("# Generated by melonDS (vkBasalt). Edit the effect selection in Settings > Video.\n")

        val keys = chain.map { if (it.isBuiltIn) it.id else configKey(it, taken) }
        sb.append("effects = ").append(keys.joinToString(":")).append('\n')
        chain.forEachIndexed { i, effect ->
            val file = effect.file ?: return@forEachIndexed
            sb.append(keys[i]).append(" = ").append(file.absolutePath).append('\n')
        }
        if (pack?.textureDir != null) sb.append("reshadeTexturePath = ").append(pack.textureDir.absolutePath).append('\n')
        if (pack?.includeDir != null) sb.append("reshadeIncludePath = ").append(pack.includeDir.absolutePath).append('\n')
        sb.append("depthCapture = ").append(if (depthCapture) "on" else "off").append('\n')
        sb.append("toggleKey = Home\n")
        if (topScreenOnly) sb.append("effectRegion = true\n")
        return sb.toString()
    }

    /**
     * Rewrites a vkBasalt.conf that came with the zip so every path points inside the imported pack.
     * Paths are resolved (in this order): as they are if they exist, relative to the config file, relative to the
     * pack root, and finally by file name anywhere in the pack. Unresolvable values are left untouched.
     */
    fun rewriteUserConf(text: String, pack: VkBasaltPack, confDir: File, topScreenOnly: Boolean = false): String {
        val byName: Map<String, File> by lazy {
            pack.root.walkTopDown().filter { it.isFile }.groupBy { it.name }.mapValues { it.value.first() }
        }
        val dirsByName: Map<String, File> by lazy {
            pack.root.walkTopDown().filter { it.isDirectory }.groupBy { it.name }.mapValues { it.value.first() }
        }

        fun resolve(value: String, wantDir: Boolean): String? {
            val unquoted = value.trim().trim('"')
            val direct = File(unquoted)
            if (direct.isAbsolute && (if (wantDir) direct.isDirectory else direct.isFile) && direct.canonicalPath.startsWith(pack.root.canonicalPath)) return direct.absolutePath
            val candidates = listOf(File(confDir, unquoted), File(pack.root, unquoted))
            for (c in candidates) {
                val ok = if (wantDir) c.isDirectory else c.isFile
                if (ok && c.canonicalPath.startsWith(pack.root.canonicalPath)) return c.canonicalPath
            }
            val leaf = unquoted.replace('\\', '/').trimEnd('/').substringAfterLast('/')
            return (if (wantDir) dirsByName[leaf] else byName[leaf])?.absolutePath
        }

        val rewritten = text.lineSequence().joinToString("\n") { line ->
            if (line.trimStart().startsWith("#")) return@joinToString line
            val m = KEY_VALUE.matchEntire(line) ?: return@joinToString line
            val key = m.groupValues[1]
            val value = m.groupValues[2]
            val isFx = value.trim('"').endsWith(".fx", ignoreCase = true)
            val isCube = value.trim('"').endsWith(".cube", ignoreCase = true) || value.trim('"').endsWith(".png", ignoreCase = true)
            when {
                key in setOf("reshadeTexturePath", "reshadeIncludePath") ->
                    resolve(value, wantDir = true)?.let { "$key = $it" } ?: line
                key == "lutFile" || isFx || (isCube && key in PATH_KEYS) ->
                    resolve(value, wantDir = false)?.let { "$key = $it" } ?: line
                else -> line
            }
        }

        // "effectRegion" is controlled by the app setting, not by the zip's config
        val withoutRegion = rewritten.lineSequence().filterNot { KEY_VALUE.matchEntire(it)?.groupValues?.get(1) == "effectRegion" }.joinToString("\n")
        return if (topScreenOnly) withoutRegion.trimEnd('\n') + "\neffectRegion = true\n" else withoutRegion
    }
}
