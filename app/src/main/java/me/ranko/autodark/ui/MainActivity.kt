package me.ranko.autodark.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import me.ranko.autodark.Constant
import me.ranko.autodark.Constant.SP_RESTRICTED_SILENCE
import me.ranko.autodark.R
import me.ranko.autodark.Receivers.DarkModeAlarmReceiver
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.databinding.MainActivityBinding
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: MainActivityBinding

    /**
     * Show this dialog if boot receiver is disabled
     *
     * @see     checkBootReceiver
     * */
    private var restrictedDialog: BottomSheetDialog? = null

    private val summaryTextListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            @Suppress("UNCHECKED_CAST")
            val summary = (sender as ObservableField<MainViewModel.Companion.Summary>).get()!!
            showSummary(summary)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this, MainViewModel.Companion.Factory(application))
            .get(MainViewModel::class.java)

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        viewModel.summaryText.addOnPropertyChangedCallback(summaryTextListener)

        viewModel.requirePermission.observe(this, Observer { required ->
            if (required) {
                PermissionActivity.startWithAnimationForResult(binding.fab, this)
                viewModel.onRequireAdbConsumed()
            }
        })

        if (ViewUtil.isLandscape(window)) {
            val collapsingToolbar =
                binding.appbar.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar)!!
            val transparent = ColorStateList.valueOf(getColor(android.R.color.transparent))
            collapsingToolbar.setExpandedTitleTextColor(transparent)
        } else {
            ViewUtil.setImmersiveNavBar(window)
        }

        checkBootReceiver()
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        viewModel.getDelayedSummary()?.run {
            // delayed summary exists, show summary
            showSummary(this)
        }
    }

    private fun showSummary(summary: MainViewModel.Companion.Summary) {
        val snack = Snackbar.make(binding.coordinatorRoot, summary.message, Snackbar.LENGTH_LONG)
        summary.actionStr?.let { snack.setAction(it, summary.action) }
        snack.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PermissionActivity.REQUEST_CODE_PERMISSION) {
            if (resultCode == Activity.RESULT_OK) {
                viewModel.updateForceDarkTitle()
                Snackbar.make(binding.coordinatorRoot, R.string.permission_granted, Snackbar.LENGTH_SHORT)
                    .show()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * Some optimize app or OEM performance boost function can disable boot receiver
     * Notify user if this happened
     *
     * */
    private fun checkBootReceiver() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val restricted = !isComponentEnabled(DarkModeAlarmReceiver::class.java)
        val silence = sp.getBoolean(SP_RESTRICTED_SILENCE, false)

        if (silence && !restricted) return

        if (restrictedDialog == null) {
            restrictedDialog = BottomSheetDialog(this).apply {
                setContentView(R.layout.dialog_bottom_resstricted)

                // button show later
                findViewById<MaterialButton>(R.id.btnLater)!!.setOnClickListener { dismiss() }

                // button do not show again
                findViewById<MaterialButton>(R.id.btnShutup)!!.run {
                    // add strike font style when restricted
                    paintFlags = if (restricted) {
                        Timber.d("Receiver is disabled!")
                        paintFlags.or(Paint.STRIKE_THRU_TEXT_FLAG)
                    } else {
                        paintFlags.and(Paint.STRIKE_THRU_TEXT_FLAG.inv())
                    }
                    // disable shut up button when restricted
                    isEnabled = !restricted

                    setOnClickListener {
                        sp.edit().putBoolean(Constant.SP_RESTRICTED_SILENCE, true).apply()
                        dismiss()
                    }
                }
            }
        }

        restrictedDialog!!.show()
    }

    override fun onStop() {
        restrictedDialog?.run { if (isShowing) dismiss() }
        super.onStop()
    }

    override fun onDestroy() {
        viewModel.summaryText.removeOnPropertyChangedCallback(summaryTextListener)

        super.onDestroy()
    }

    private fun isComponentEnabled(receiver: Class<*>): Boolean {
        val component = ComponentName(this, receiver)
        val status = packageManager.getComponentEnabledSetting(component)
        return status <= PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
}