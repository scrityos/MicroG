package org.microg.gms.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.platform.MaterialSharedAxis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.microg.gms.auth.AuthConstants
import org.microg.gms.auth.login.LoginActivity
import org.microg.gms.people.DatabaseHelper
import org.microg.gms.people.PeopleManager

class AccountsFragment : PreferenceFragmentCompat() {

    private val tag = AccountsFragment::class.java.simpleName

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_accounts)
        updateSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onResume() {
        super.onResume()
        updateSettings()
    }

    private fun clearAccountPreferences() {
        val preferenceCategory = findPreference<PreferenceCategory>("prefcat_current_accounts")
        preferenceCategory?.removeAll()
    }

    private fun updateSettings() {
        val context = requireContext()

        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE)

        clearAccountPreferences()

        val preferenceCategory = findPreference<PreferenceCategory>("prefcat_current_accounts")

        lifecycleScope.launch(Dispatchers.Main) {
            accounts.forEach { account ->
                val photo = PeopleManager.getOwnerAvatarBitmap(context, account.name, false)
                val newPreference = Preference(requireContext()).apply {
                    title = getDisplayName(account)
                    summary = account.name
                    icon = getCircleBitmapDrawable(photo)
                    key = "account:${account.name}"
                    order = 0

                    setOnPreferenceClickListener {
                        showConfirmationDialog(account.name)
                        true
                    }
                }

                if (photo == null) {
                    withContext(Dispatchers.IO) {
                        PeopleManager.getOwnerAvatarBitmap(context, account.name, true)
                    }?.let { newPreference.icon = getCircleBitmapDrawable(it) }
                }

                preferenceCategory?.addPreference(newPreference)
            }
        }
    }

    private fun showConfirmationDialog(accountName: String) {
        AlertDialog.Builder(requireContext(), R.style.AppTheme_Dialog_Account)
            .setTitle(getString(R.string.dialog_title_remove_account))
            .setMessage(getString(R.string.dialog_message_remove_account))
            .setPositiveButton(getString(R.string.dialog_confirm_button)) { _, _ ->
                removeAccount(accountName)
            }.setNegativeButton(getString(R.string.dialog_cancel_button)) { dialog, _ ->
                dialog.dismiss()
            }.create().show()
    }

    private fun removeAccount(accountName: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            val accountManager = AccountManager.get(requireContext())
            val accounts = accountManager.getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE)

            val accountToRemove = accounts.firstOrNull { it.name == accountName }
            accountToRemove?.let {
                try {
                    val removedSuccessfully = withContext(Dispatchers.IO) {
                        accountManager.removeAccountExplicitly(it)
                    }
                    if (removedSuccessfully) {
                        updateSettings()
                        val toastMessage =
                            getString(R.string.toast_remove_account_success, accountName)
                        showToast(toastMessage)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error removing account: $accountName", e)
                    showToast(getString(R.string.toast_remove_account_success))
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.colorBackground))

        findPreference<Preference>("pref_manage_accounts")?.setOnPreferenceClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
            } catch (activityNotFoundException: ActivityNotFoundException) {
                Log.e(TAG, "Failed to launch sync settings", activityNotFoundException)
            }
            true
        }

        findPreference<Preference>("pref_add_account")?.setOnPreferenceClickListener {
            try {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            } catch (activityNotFoundException: ActivityNotFoundException) {
                Log.e(TAG, "Failed to launch login activity", activityNotFoundException)
            }
            true
        }
    }

    init {
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.accounts_menu_item, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.privacy_settings -> {
                findNavController().navigate(R.id.privacyFragment)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getDisplayName(account: Account): String? {
        val databaseHelper = DatabaseHelper(requireContext())
        val cursor = databaseHelper.getOwner(account.name)
        return try {
            if (cursor.moveToNext()) {
                cursor.getColumnIndex("display_name").takeIf { it >= 0 }
                    ?.let { cursor.getString(it) }?.takeIf { it.isNotBlank() }
            } else null
        } finally {
            cursor.close()
            databaseHelper.close()
        }
    }

    private fun getCircleBitmapDrawable(bitmap: Bitmap?) =
        if (bitmap != null) RoundedBitmapDrawableFactory.create(resources, bitmap)
            .also { it.isCircular = true } else null

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
