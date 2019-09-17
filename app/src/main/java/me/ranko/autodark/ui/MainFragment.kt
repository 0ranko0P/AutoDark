package me.ranko.autodark.ui

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.core.DARK_PREFERENCE_END
import me.ranko.autodark.core.DARK_PREFERENCE_FORCE
import me.ranko.autodark.core.DARK_PREFERENCE_START
import me.ranko.autodark.core.DarkPreferenceSupplier
import me.ranko.autodark.ui.Preference.DarkDisplayPreference
import timber.log.Timber

class MainFragment : PreferenceFragmentCompat(), DarkPreferenceSupplier {
    private lateinit var startPreference: DarkDisplayPreference
    private lateinit var endPreference: DarkDisplayPreference
    private lateinit var forcePreference: SwitchPreference

    private val viewModel: MainViewModel by lazy {
        val context = requireNotNull(activity) { "Call after activity created!" }
        ViewModelProviders.of(
            context, MainViewModel.Companion.Factory(context.application)
        ).get(MainViewModel::class.java)
    }

    companion object {
        //fun newInstance() = MainFragment()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) =
        addPreferencesFromResource(R.xml.preferences_main)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        startPreference = findPreference(DARK_PREFERENCE_START)!!
        endPreference = findPreference(DARK_PREFERENCE_END)!!
        forcePreference = findPreference(DARK_PREFERENCE_FORCE)!!

        startPreference.onPreferenceChangeListener = viewModel.darkSettings
        endPreference.onPreferenceChangeListener = viewModel.darkSettings

        viewModel.masterSwitch.observe(this, Observer<Boolean> { enabled ->
            startPreference.isEnabled = enabled
            endPreference.isEnabled = enabled
        })

        viewModel.forceDarkStatus.observe(this, Observer<Int> { status ->
            when (status) {
                Constant.JOB_STATUS_SUCCEED -> Timber.v("Set force job successful")

                Constant.JOB_STATUS_FAILED -> {
                    forcePreference.isChecked = !forcePreference.isChecked
                    Toast.makeText(context, R.string.root_check_failed, Toast.LENGTH_SHORT).show()
                    Timber.v("Set force job failed")
                }
            }
            forcePreference.isEnabled = status != Constant.JOB_STATUS_PENDING
        })

        // Set job running on background, reject new value in observer
        forcePreference.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->

            GlobalScope.launch(Dispatchers.Main, CoroutineStart.ATOMIC) {
                viewModel.triggerForceDark(newValue.toString().toBoolean())
            }
            true
        }


        lifecycle.addObserver(viewModel.darkSettings)
    }

    override fun get(type: String): DarkDisplayPreference {
        return if (type == DARK_PREFERENCE_START) startPreference else endPreference
    }
}
