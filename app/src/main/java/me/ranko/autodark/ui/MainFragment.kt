package me.ranko.autodark.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringDef
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.Constant.*
import me.ranko.autodark.R
import me.ranko.autodark.core.DarkPreferenceSupplier
import me.ranko.autodark.ui.MainFragment.Companion.DARK_PREFERENCE_END
import me.ranko.autodark.ui.MainFragment.Companion.DARK_PREFERENCE_START
import me.ranko.autodark.ui.Preference.DarkDisplayPreference
import timber.log.Timber

class MainFragment : PreferenceFragmentCompat(), DarkPreferenceSupplier {

    private lateinit var darkTimeCategory: PreferenceCategory
    private lateinit var startPreference: DarkDisplayPreference
    private lateinit var endPreference: DarkDisplayPreference
    private lateinit var forcePreference: SwitchPreference
    private lateinit var autoPreference: SwitchPreference

    companion object {
        val PERMISSIONS_LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        const val REQUEST_LOCATION_PERMISSION = 12
        const val DARK_PREFERENCE_ABOUT = "pref_about"
        const val DARK_PREFERENCE_AUTO = "dark_mode_auto"
        const val DARK_PREFERENCE_START = "dark_mode_time_start"
        const val DARK_PREFERENCE_END = "dark_mode_time_end"
        const val DARK_PREFERENCE_FORCE = "dark_mode_force"

        private fun checkLocationPermission(context: Context): Boolean {
            return AutoDarkApplication.checkSelfPermission(context, PERMISSIONS_LOCATION[0]) &&
                    AutoDarkApplication.checkSelfPermission(context, PERMISSIONS_LOCATION[1])
        }
    }

    /**
     * Sync master switch status to preferences
     * */
    private val onSwitchChangedCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            when ((sender as ObservableField<*>).get() as DarkSwitch) {
                DarkSwitch.SHARE -> return

                DarkSwitch.ON -> setTimePreferenceEnabled(true)

                DarkSwitch.OFF -> setTimePreferenceEnabled(false)
            }
        }
    }

    private val viewModel: MainViewModel by lazy {
        val context = requireNotNull(activity) { "Call after activity created!" }
        ViewModelProvider(context, MainViewModel.Companion.Factory(context.application)).get(
            MainViewModel::class.java
        )
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) =
        addPreferencesFromResource(R.xml.preferences_main)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        darkTimeCategory = findPreference<PreferenceCategory>("dark_mode_category")!!
        startPreference = darkTimeCategory.findPreference(DARK_PREFERENCE_START)!!
        endPreference = darkTimeCategory.findPreference(DARK_PREFERENCE_END)!!
        autoPreference = darkTimeCategory.findPreference<SwitchPreference>(DARK_PREFERENCE_AUTO)!!
        forcePreference = findPreference(DARK_PREFERENCE_FORCE)!!

        startPreference.onPreferenceChangeListener = viewModel.darkSettings
        endPreference.onPreferenceChangeListener = viewModel.darkSettings

        viewModel.switch.addOnPropertyChangedCallback(onSwitchChangedCallback)
        onSwitchChangedCallback.onPropertyChanged(viewModel.switch, 0)

        viewModel.forceDarkStatus.observe(viewLifecycleOwner, Observer<Int> { status ->
            when (status) {
                JOB_STATUS_SUCCEED -> Timber.v("Set force job successful")

                JOB_STATUS_FAILED -> {
                    forcePreference.isChecked = !forcePreference.isChecked
                    Toast.makeText(context, R.string.root_check_failed, Toast.LENGTH_SHORT).show()
                    Timber.v("Set force job failed")
                }
            }
            forcePreference.isEnabled = status != JOB_STATUS_PENDING
        })

        viewModel.forceDarkTile.observe(viewLifecycleOwner, Observer<Int> { strId ->
            forcePreference.setTitle(strId)
        })

        // Set job running on background, reject new value in observer
        forcePreference.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            viewModel.triggerForceDark(newValue.toString().toBoolean())
            true
        }

        lifecycle.addObserver(viewModel.darkSettings)

        viewModel.updateForceDarkTitle()

        viewModel.autoMode.observe(viewLifecycleOwner, Observer<Boolean> { result ->
            autoPreference.isChecked = result
            // hide custom time preferences when using auto mode
            startPreference.isVisible = !result
            endPreference.isVisible = !result

            // enable time preference status
            setTimePreferenceEnabled(viewModel.switch.get() == DarkSwitch.ON)
        })
    }

    /**
     * Handle auto mode preference click there.
     * Will call viewModel if location permission granted
     *
     * @see     checkLocationPermission
     * @see     MainViewModel.onAutoModeClicked
     * */
    @SuppressLint("MissingPermission")
    private fun onAutoPreferenceClick(): Boolean {
        if (checkLocationPermission(context!!)) {
            // disable time preference now
            setTimePreferenceEnabled(false)
            viewModel.onAutoModeClicked()
        } else {
            requestPermissions(PERMISSIONS_LOCATION, REQUEST_LOCATION_PERMISSION)
        }
        return true
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {

            DARK_PREFERENCE_ABOUT -> {
                AboutFragment.replace(activity!!.supportFragmentManager, R.id.container, "about")
                true
            }

            DARK_PREFERENCE_AUTO -> onAutoPreferenceClick()

            else -> super.onPreferenceTreeClick(preference)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context!!, R.string.permission_failed, Toast.LENGTH_SHORT).show()
                autoPreference.isChecked = false
            } else {
                onPreferenceTreeClick(autoPreference)
            }
        }
    }

    private fun setTimePreferenceEnabled(isEnabled: Boolean) {
        for (index in 0 until darkTimeCategory.preferenceCount)
            darkTimeCategory.getPreference(index).isEnabled = isEnabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.switch.removeOnPropertyChangedCallback(onSwitchChangedCallback)
        autoPreference.onPreferenceClickListener = null
    }

    override fun get(type: String): DarkDisplayPreference {
        return if (type == DARK_PREFERENCE_START) startPreference else endPreference
    }
}
