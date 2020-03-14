package me.ranko.autodark.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
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

    private val viewModel: MainViewModel by lazy {
        val context = requireNotNull(activity) { "Call after activity created!" }
        ViewModelProvider(context, MainViewModel.Companion.Factory(context.application)).get(
            MainViewModel::class.java
        )
    }

    companion object {
        const val PREFERENCE_KEY_FEEDBACK = "pref_feedback"
        const val PREFERENCE_KEY_LICENSE = "pref_license"
        const val PREFERENCE_KEY_SHARE = "pref_share"
        const val PREFERENCE_KEY_VERSION = "pref_ver"

        fun replace(manager: FragmentManager, @IdRes container: Int, name: String?) {
            val fragment = AboutFragment()
            manager.beginTransaction()
                .setCustomAnimations(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                ).replace(container, fragment)
                .addToBackStack(name)
                .commit()
        }

        fun shareApp(context: Context) {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.app_github_page))
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.adb_share_text))
            )
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_about)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel.onAboutPageChanged(true)

        findPreference<Preference>(PREFERENCE_KEY_VERSION)!!.summary = BuildConfig.VERSION_NAME
    }

    override fun onDetach() {
        super.onDetach()
        viewModel.onAboutPageChanged(false)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            PREFERENCE_KEY_FEEDBACK -> {
                val divider = getString(R.string.feedback_divider)
                val systemInfo = StringBuilder(divider)
                    .append("\nApp version: ${BuildConfig.VERSION_NAME}")
                    .append("\nAndroid SDK: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
                    .append("\nMANUFACTURER: ${Build.MANUFACTURER}")
                    .append("\nBrand: ${Build.BRAND}")
                    .append(divider)
                    .toString()

                val intent = Intent(Intent.ACTION_SEND)
                intent.data = Uri.parse("mailto:")
                intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.app_ranko_email)))
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
                intent.putExtra(Intent.EXTRA_TEXT, systemInfo)
                startActivity(Intent.createChooser(intent, getString(R.string.feedback_send)))
            }

            PREFERENCE_KEY_SHARE -> shareApp(context!!)

            PREFERENCE_KEY_LICENSE -> {
                startActivity(Intent(context, OssLicensesMenuActivity::class.java))
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}