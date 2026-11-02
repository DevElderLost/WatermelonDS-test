package me.magnum.melonds.ui.settings.fragments

import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.R
import me.magnum.melonds.common.vkbasalt.VkBasaltManager

/**
 * Settings logic of the real vkBasalt/ReShade post-processing (Video > vkBasalt / ReShade).
 * Keeps VideoPreferencesFragment small: the fragment only owns the file picker launcher.
 */
class VkBasaltSettingsController(
    private val fragment: PreferenceFragmentCompat,
    private val launchZipPicker: () -> Unit,
) {
    private val manager = VkBasaltManager(fragment.requireContext())

    private val importPreference = fragment.findPreference<Preference>("video_vkbasalt_import")
    private val effectsPreference = fragment.findPreference<Preference>("video_vkbasalt_effects")
    private val removePreference = fragment.findPreference<Preference>("video_vkbasalt_remove")

    fun setup() {
        importPreference?.setOnPreferenceClickListener {
            launchZipPicker()
            true
        }
        effectsPreference?.setOnPreferenceClickListener {
            showEffectsDialog()
            true
        }
        removePreference?.setOnPreferenceClickListener {
            manager.removePack()
            Toast.makeText(fragment.requireContext(), R.string.video_vkbasalt_pack_removed, Toast.LENGTH_SHORT).show()
            refreshState()
            true
        }
        refreshState()
    }

    fun onZipPicked(uri: Uri) {
        val context = fragment.requireContext()
        Toast.makeText(context, R.string.video_vkbasalt_importing, Toast.LENGTH_SHORT).show()
        fragment.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { manager.importZip(uri) }
            result.onSuccess { pack ->
                Toast.makeText(context, context.getString(R.string.video_vkbasalt_import_success, pack.effects.size), Toast.LENGTH_LONG).show()
                refreshState()
                if (pack.effects.isNotEmpty()) showEffectsDialog()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.video_vkbasalt_import_failed, it.message ?: "?"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshState() {
        val context = fragment.requireContext()
        val hasPack = manager.hasPack()
        val selected = manager.selectedEffectIds()
        removePreference?.isEnabled = hasPack
        importPreference?.summary = if (hasPack) context.getString(R.string.video_vkbasalt_import_summary_installed) else context.getString(R.string.video_vkbasalt_import_summary)
        effectsPreference?.summary = if (selected.isEmpty()) {
            context.getString(R.string.video_vkbasalt_effects_summary_none)
        } else {
            selected.joinToString(" → ") { it.substringAfterLast('/').removeSuffix(".fx") }
        }
    }

    private fun showEffectsDialog() {
        val context = fragment.requireContext()
        val effects = manager.availableEffects()
        val chain = manager.selectedEffectIds().toMutableList()

        val labels = effects.map { if (it.isBuiltIn) "${it.displayName} (vkBasalt)" else it.id }.toTypedArray()
        val checked = BooleanArray(effects.size) { effects[it].id in chain }

        AlertDialog.Builder(context)
            .setTitle(R.string.video_vkbasalt_effects_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                // The chain order is the order in which the effects were ticked
                val id = effects[which].id
                if (isChecked) {
                    if (id !in chain) chain.add(id)
                } else {
                    chain.remove(id)
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                manager.setSelectedEffectIds(chain)
                refreshState()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
