package me.ranko.autodark.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Pair
import android.view.View
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.Receivers.BlockListReceiver
import me.ranko.autodark.core.DARK_JOB_TYPE
import me.ranko.autodark.core.DarkPreferenceSupplier
import me.ranko.autodark.ui.Preference.DarkDisplayPreference
import me.ranko.autodark.ui.Preference.DarkSwitchPreference

class MainFragment : PreferenceFragmentCompat(), DarkPreferenceSupplier {

    private inner class XposedAliveReceiver(val mContext: Context): BroadcastReceiver() {
        init {
            mContext.registerReceiver(this,
                    IntentFilter(BlockListReceiver.ACTION_ALIVE),
                    Constant.PERMISSION_SEND_DARK_BROADCAST, null)
            BlockListReceiver.sendIsAliveBroadcast(mContext)
        }

        override fun onReceive(context: Context?, intent: Intent) {
            isXposed = true
            xposedAliveTimer?.cancel()
            xposedAliveTimer = null
            if (xposedPreference != null) {
                initXposedPreference(true)
            }
            mContext.unregisterReceiver(this)
            xposedAliveReceiver = null
        }
    }

    private lateinit var startPreference: DarkDisplayPreference
    private lateinit var endPreference: DarkDisplayPreference
    private lateinit var autoPreference: SwitchPreference

    private lateinit var forceDarkPreference: DarkSwitchPreference
    private var xposedPreference: Preference? = null

    // may never get clicked
    private val aboutPreference by lazy(LazyThreadSafetyMode.NONE) { findPreference<Preference>(getString(R.string.pref_key_about))!! }

    private var isXposed = false
    private var xposedAliveTimer: Job? = null
    private var xposedAliveReceiver: XposedAliveReceiver? = null

    companion object {
        val PERMISSIONS_LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        const val REQUEST_LOCATION_PERMISSION = 12

        const val DARK_PREFERENCE_AUTO = "dark_mode_auto"
        const val DARK_PREFERENCE_START = "dark_mode_time_start"
        const val DARK_PREFERENCE_END = "dark_mode_time_end"
        const val DARK_PREFERENCE_FORCE_ROOT = "dark_mode_force"
        const val DARK_PREFERENCE_XPOSED = "dark_mode_xposed"
        const val DARK_PREFERENCE_WALLPAPER = "dark_mode_wallpaper"

        private const val XPOSED_ALIVE_TIME_OUT = 500L
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

    private lateinit var viewModel: MainViewModel

    override fun onAttach(context: Context) {
        super.onAttach(context)

        viewModel = ViewModelProvider(
            context as FragmentActivity,
            MainViewModel.Companion.Factory(context.application)
        ).get(MainViewModel::class.java)

        lifecycle.addObserver(viewModel.darkSettings)
        xposedAliveReceiver = XposedAliveReceiver(context)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_main)
        val darkTimeCategory = findPreference<PreferenceCategory>(getString(R.string.pref_key_time))!!
        startPreference = darkTimeCategory.findPreference(DARK_PREFERENCE_START)!!
        endPreference = darkTimeCategory.findPreference(DARK_PREFERENCE_END)!!
        autoPreference = darkTimeCategory.findPreference(DARK_PREFERENCE_AUTO)!!

        forceDarkPreference = findPreference(DARK_PREFERENCE_FORCE_ROOT)!!
        xposedPreference = findPreference(DARK_PREFERENCE_XPOSED)!!

        if (isXposed) {
            initXposedPreference(true)
        } else {
            xposedAliveTimer = lifecycleScope.launch(Dispatchers.Main) {
                delay(XPOSED_ALIVE_TIME_OUT)
                xposedAliveTimer = null
                if (isXposed.not() && xposedPreference != null) {
                    initXposedPreference(isXposed)
                }
            }
        }
    }

    private fun initXposedPreference(isXposed: Boolean) {
        xposedPreference!!.isEnabled = isXposed
        forceDarkPreference.isEnabled = isXposed.not()
        forceDarkPreference.isSwitchable = isXposed.not()

        if (isXposed) {
            xposedPreference!!.title = getString(R.string.pref_block_title, "")
            forceDarkPreference.title = getString(R.string.pref_force_dark, "")
            forceDarkPreference.summary = getString(R.string.pref_force_dark_summary, getString(R.string.pref_force_dark_summary_xposed))
        } else {
            xposedPreference!!.title = getString(R.string.pref_block_title, " (Xposed)")
            forceDarkPreference.title = getString(R.string.pref_force_dark, if (AutoDarkApplication.isSui) " (Sui)" else " (Root)")
            forceDarkPreference.summary = getString(R.string.pref_force_dark_summary, getString(R.string.pref_force_dark_summary_root))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.switch.addOnPropertyChangedCallback(switchObserver)

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
        when (preference.key) {
            DARK_PREFERENCE_START, DARK_PREFERENCE_END -> return false

            DARK_PREFERENCE_WALLPAPER -> startActivity(Intent(requireActivity(), DarkWallpaperPickerActivity::class.java))

            DARK_PREFERENCE_FORCE_ROOT -> viewModel.onForceDarkClicked(preference as SwitchPreference, lifecycleScope)

            DARK_PREFERENCE_AUTO -> onAutoPreferenceClick()

            DARK_PREFERENCE_XPOSED -> {
                val activity = requireActivity() as MainActivity
                val appBarView = activity.findViewById<View>(R.id.appbar)
                val fabView = activity.findViewById<View>(R.id.fab)
                val appBarShared = Pair<View, String>(appBarView, appBarView.transitionName)
                val fabShared = Pair<View, String>(fabView, fabView.transitionName)
                val intent = Intent(activity, BlockListActivity::class.java)
                val options = ActivityOptions.makeSceneTransitionAnimation(activity, appBarShared, fabShared)
                activity.startActivity(intent, options.toBundle())
            }

            aboutPreference.key -> AboutFragment.replace(parentFragmentManager, R.id.container, "about")

            else -> return super.onPreferenceTreeClick(preference)
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                viewModel.summaryText.set(viewModel.newSummary(R.string.permission_failed))
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

    override fun onDestroy() {
        super.onDestroy()
        xposedAliveReceiver?.apply {
            mContext.unregisterReceiver(this)
            xposedAliveReceiver = null
        }
    }

    override fun get(@DARK_JOB_TYPE type: String): DarkDisplayPreference {
        return if (type == DARK_PREFERENCE_START) startPreference else endPreference
    }

    private fun checkLocationPermission(): Boolean {
        return requireActivity().checkSelfPermission(PERMISSIONS_LOCATION[0]) == PackageManager.PERMISSION_GRANTED &&
                requireActivity().checkSelfPermission(PERMISSIONS_LOCATION[1]) == PackageManager.PERMISSION_GRANTED
    }
}
