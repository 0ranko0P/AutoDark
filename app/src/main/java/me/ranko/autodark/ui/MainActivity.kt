package me.ranko.autodark.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
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

    private companion object val ARG_IS_DIALOG_SHOWED = "arg_dialog"
    private var isDialogShowed = false

    private val summaryTextListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            @Suppress("UNCHECKED_CAST")
            val summary = (sender as ObservableField<MainViewModel.Companion.Summary>).get()!!
            showSummary(summary)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            isDialogShowed = savedInstanceState.getBoolean(ARG_IS_DIALOG_SHOWED, false)
        }

        checkBootReceiver()

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

        if (ViewUtil.isLandscape(this)) {
            val collapsingToolbar =
                binding.appbar.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar)!!
            val transparent = ColorStateList.valueOf(getColor(android.R.color.transparent))
            collapsingToolbar.setExpandedTitleTextColor(transparent)
        } else {
            ViewUtil.setImmersiveNavBar(window)
        }
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
     * Notify user if this happened and disable __do not show again__ button.
     *
     * */
    private fun checkBootReceiver() {
        val restricted = !isComponentEnabled(DarkModeAlarmReceiver::class.java)
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val silence = sp.getBoolean(SP_RESTRICTED_SILENCE, isDialogShowed)
        if (silence && !restricted) return

        if (restrictedDialog == null) {
            restrictedDialog = BottomSheetDialog(this, R.style.AppTheme_BottomSheetDialogDayNight).apply {
                val root = LayoutInflater.from(context).inflate(
                    R.layout.dialog_bottom_resstricted,
                    this.window!!.decorView.rootView as ViewGroup,
                    false
                )
                setContentView(root)

                val mBehavior = BottomSheetBehavior.from(root.parent as ViewGroup)

                setOnShowListener { mBehavior.peekHeight = root.height }

                // button show later
                root.findViewById<MaterialButton>(R.id.btnLater)!!.setOnClickListener { dismiss() }

                // button do not show again
                root.findViewById<MaterialButton>(R.id.btnShutup)!!.run {
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

        isDialogShowed = true
        restrictedDialog!!.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ARG_IS_DIALOG_SHOWED, isDialogShowed)
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