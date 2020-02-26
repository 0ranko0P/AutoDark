package me.ranko.autodark.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.R

/**
 * Fragment to show about page
 *
 * @author  0ranko0P
 * */
class AboutFragment : PreferenceFragmentCompat() {

    companion object {
        const val PREFERENCE_KEY_FEEDBACK = "pref_feedback"
        const val PREFERENCE_KEY_LICENSE = "pref_license"
        const val PREFERENCE_KEY_SHARE = "pref_share"
        const val PREFERENCE_KEY_VERSION = "pref_ver"

        fun replace(manager: FragmentManager, @IdRes container: Int, name: String?) {
            val fragment = AboutFragment()
            val transaction = manager.beginTransaction()
            transaction.replace(container, fragment)
            if (name != null) transaction.addToBackStack(name)
            transaction.commit()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_about)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        findPreference<Preference>(PREFERENCE_KEY_VERSION)!!.summary = BuildConfig.VERSION_NAME
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            PREFERENCE_KEY_FEEDBACK -> {
                val divider = getString(R.string.feedback_divider)
                val systemInfo = StringBuilder(divider)
                    .append("\nApp version: ${BuildConfig.VERSION_NAME}")
                    .append("\nAndroid SDK: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
                    .append(divider)
                    .toString()

                val intent = Intent(Intent.ACTION_SEND)
                intent.data = Uri.parse("mailto:")
                intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.app_ranko_email)))
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
                intent.putExtra(Intent.EXTRA_TEXT, systemInfo)
                startActivity(Intent.createChooser(intent, getString(R.string.feedback_send)))
            }

            PREFERENCE_KEY_SHARE -> {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.app_github_page))
                startActivity(
                    Intent.createChooser(intent, getString(R.string.adb_share_text))
                )
            }

            PREFERENCE_KEY_LICENSE -> {
                startActivity(Intent(context, OssLicensesMenuActivity::class.java))
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}