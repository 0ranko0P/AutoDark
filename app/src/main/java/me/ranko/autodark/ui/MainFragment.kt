package me.ranko.autodark.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.Constant.JOB_STATUS_FAILED
import me.ranko.autodark.Constant.JOB_STATUS_PENDING
import me.ranko.autodark.R
import me.ranko.autodark.core.DarkPreferenceSupplier
import me.ranko.autodark.ui.Preference.DarkDisplayPreference

class MainFragment : PreferenceFragmentCompat(), DarkPreferenceSupplier {

    private lateinit var darkTimeCategory: PreferenceCategory
    private lateinit var startPreference: DarkDisplayPreference
    private lateinit var endPreference: DarkDisplayPreference
    private lateinit var forcePreference: SwitchPreference
    private lateinit var autoPreference: SwitchPreference

    // may never get clicked
    private val aboutPreference by lazy { findPreference<Preference>(getString(R.string.pref_key_about))!! }

    private val rootView by lazy { activity!!.findViewById<View>(R.id.coordinatorRoot) }

    companion object {
        val PERMISSIONS_LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        const val REQUEST_LOCATION_PERMISSION = 12

        const val DARK_PREFERENCE_AUTO = "dark_mode_auto"
        const val DARK_PREFERENCE_START = "dark_mode_time_start"
        const val DARK_PREFERENCE_END = "dark_mode_time_end"
        const val DARK_PREFERENCE_FORCE = "dark_mode_force"
    }

    /**
     * Sync master switch status to preferences
     * */
    private val switchObserver = object : Observable.OnPropertyChangedCallback() {
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

        darkTimeCategory = findPreference(getString(R.string.pref_key_time))!!
        startPreference = darkTimeCategory.findPreference(DARK_PREFERENCE_START)!!
        endPreference = darkTimeCategory.findPreference(DARK_PREFERENCE_END)!!
        autoPreference = darkTimeCategory.findPreference(DARK_PREFERENCE_AUTO)!!
        forcePreference = findPreference(DARK_PREFERENCE_FORCE)!!

        startPreference.onPreferenceChangeListener = viewModel.darkSettings
        endPreference.onPreferenceChangeListener = viewModel.darkSettings

        viewModel.switch.addOnPropertyChangedCallback(switchObserver)

        viewModel.forceDarkTile.observe(viewLifecycleOwner, Observer<Int> { strId ->
            forcePreference.setTitle(strId)
        })

        lifecycle.addObserver(viewModel.darkSettings)

        viewModel.updateForceDarkTitle()

        // observe force-dark job result
        viewModel.forceDarkStatus.observe(viewLifecycleOwner, Observer<Int> { status ->
            forcePreference.isEnabled = status != JOB_STATUS_PENDING
            if (status == JOB_STATUS_FAILED) {
                forcePreference.isChecked = !forcePreference.isChecked
                Snackbar.make(rootView, R.string.root_check_failed, Snackbar.LENGTH_SHORT).show()
            }
        })

        // observe auto mode job result
        // also init darkTimeCategory there
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
    private fun onAutoPreferenceClick() {
        if (checkLocationPermission()) {
            // disable time preference now
            setTimePreferenceEnabled(false)
            viewModel.onAutoModeClicked()
        } else {
            requestPermissions(PERMISSIONS_LOCATION, REQUEST_LOCATION_PERMISSION)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            DARK_PREFERENCE_START -> false

            DARK_PREFERENCE_END -> false

            DARK_PREFERENCE_FORCE -> { // handle result in observer
                viewModel.triggerForceDark((preference as SwitchPreference).isChecked)
                true
            }

            DARK_PREFERENCE_AUTO -> {
                onAutoPreferenceClick()
                true
            }

            aboutPreference.key -> {
                AboutFragment.replace(activity!!.supportFragmentManager, R.id.container, "about")
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(rootView, R.string.permission_failed, Snackbar.LENGTH_SHORT).show()
                autoPreference.isChecked = false
            } else {
                onAutoPreferenceClick()
            }
        }
    }

    /**
     * Set availability to the whole DarkTimeCategory
     *
     * @see     darkTimeCategory
     * @see     Preference.setEnabled
     * */
    private fun setTimePreferenceEnabled(isEnabled: Boolean) {
        if (startPreference.isEnabled.xor(isEnabled)) { // avoid unnecessary notifyDependencyChange
            startPreference.isEnabled = isEnabled
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.switch.removeOnPropertyChangedCallback(switchObserver)
        autoPreference.onPreferenceClickListener = null
    }

    override fun get(type: String): DarkDisplayPreference {
        return if (type == startPreference.key) startPreference else endPreference
    }

    private fun checkLocationPermission(): Boolean {
        return activity!!.checkSelfPermission(PERMISSIONS_LOCATION[0]) == PackageManager.PERMISSION_GRANTED &&
                activity!!.checkSelfPermission(PERMISSIONS_LOCATION[1]) == PackageManager.PERMISSION_GRANTED
    }
}
